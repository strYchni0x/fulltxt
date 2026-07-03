package me.fulltxt.app.data.backup

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Wird geworfen, wenn eine Backup-Datei nicht entschlüsselt werden kann (falsches Passwort oder beschädigt/abgeschnitten). */
class BackupDecryptException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Streaming-basierte authentifizierte Verschlüsselung für FullTXT-Index-Backups.
 *
 * Der Index ist das sensibelste Artefakt der App — er enthält den extrahierten Klartext jedes
 * indexierten Dokuments. Ein roher `.db`-Export würde diesen Text überall ungeschützt lassen, wo die
 * Backup-Datei landet (Downloads-Ordner, Cloud-Speicher, ...). Dies umschließt den Export mit
 * AES-256-GCM und einem aus einer Benutzer-Passphrase abgeleiteten Schlüssel (PBKDF2), sodass das
 * Backup portabel bleibt (auf jedem Gerät wiederherstellbar, anders als ein Hardware-Keystore-
 * Schlüssel) und im Ruhezustand unlesbar ist.
 *
 * Format (alle Ganzzahlen big-endian):
 *
 *   Header: "FTXTBK01" (8 B) | Iterationen (4 B) | Salt (16 B) | Nonce-Präfix (8 B)
 *   Chunk*: Ciphertext-Länge (4 B) | Ciphertext + GCM-Tag
 *
 * Jeder Klartext-Chunk (max. [CHUNK_SIZE]) wird unter seiner eigenen 12-Byte-Nonce verschlüsselt
 * (8-Byte-Zufallspräfix ‖ 4-Byte-Zähler). Das höchstwertige Zählerbit markiert den letzten Chunk,
 * sodass das Weglassen oder Anhängen von Chunks die Authentifizierung fehlschlagen lässt — d. h. ein
 * Abschneiden wird erkannt.
 */
object BackupCrypto {

    private const val MAGIC = "FTXTBK01"
    private const val MAGIC_LEN = 8
    private const val KDF_ITERATIONS = 210_000
    private const val SALT_LEN = 16
    private const val NONCE_PREFIX_LEN = 8
    private const val NONCE_LEN = 12
    private const val GCM_TAG_BITS = 128
    private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
    private const val CHUNK_SIZE = 64 * 1024
    private const val HEADER_LEN = MAGIC_LEN + 4 + SALT_LEN + NONCE_PREFIX_LEN
    private const val FINAL_FLAG = 0x8000_0000.toInt()

    fun encrypt(input: InputStream, output: OutputStream, passphrase: CharArray) {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { rnd.nextBytes(it) }
        val noncePrefix = ByteArray(NONCE_PREFIX_LEN).also { rnd.nextBytes(it) }
        val key = deriveKey(passphrase, salt, KDF_ITERATIONS)

        val header = ByteBuffer.allocate(HEADER_LEN)
            .put(MAGIC.toByteArray(Charsets.US_ASCII))
            .putInt(KDF_ITERATIONS)
            .put(salt)
            .put(noncePrefix)
        output.write(header.array())

        // Einen Chunk vorausschauend lesen, damit wir wissen, welcher Chunk der letzte ist.
        val current = ByteArray(CHUNK_SIZE)
        var currentLen = readChunk(input, current).coerceAtLeast(0)
        var counter = 0
        while (true) {
            val next = ByteArray(CHUNK_SIZE)
            val nextLen = readChunk(input, next)
            val isFinal = nextLen < 0

            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce(noncePrefix, counter, isFinal)))
            }
            val ct = cipher.doFinal(current, 0, currentLen)
            writeInt(output, ct.size)
            output.write(ct)
            counter++

            if (isFinal) break
            System.arraycopy(next, 0, current, 0, nextLen)
            currentLen = nextLen
        }
        output.flush()
    }

    fun decrypt(input: InputStream, output: OutputStream, passphrase: CharArray) {
        val header = readExactly(input, HEADER_LEN)
            ?: throw BackupDecryptException("Keine gültige FullTXT-Backup-Datei.")
        val magic = String(header, 0, MAGIC_LEN, Charsets.US_ASCII)
        if (magic != MAGIC) throw BackupDecryptException("Keine gültige FullTXT-Backup-Datei.")

        val hb = ByteBuffer.wrap(header).position(MAGIC_LEN) as ByteBuffer
        val iterations = hb.int
        val salt = ByteArray(SALT_LEN).also { hb.get(it) }
        val noncePrefix = ByteArray(NONCE_PREFIX_LEN).also { hb.get(it) }
        val key = deriveKey(passphrase, salt, iterations)

        var lenBytes = readExactly(input, 4)
            ?: throw BackupDecryptException("Backup-Datei ist beschädigt.")
        var counter = 0
        while (true) {
            val ctLen = ByteBuffer.wrap(lenBytes).int
            if (ctLen < GCM_TAG_BYTES || ctLen > CHUNK_SIZE + GCM_TAG_BYTES) {
                throw BackupDecryptException("Backup-Datei ist beschädigt.")
            }
            val ct = readExactly(input, ctLen)
                ?: throw BackupDecryptException("Backup-Datei ist unvollständig.")

            // Ein weiterer Längen-Header bedeutet, dass weitere Chunks folgen; EOF bedeutet, dies ist der letzte Chunk.
            val nextLenBytes = readExactly(input, 4)
            val isFinal = nextLenBytes == null

            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce(noncePrefix, counter, isFinal)))
            }
            val pt = try {
                cipher.doFinal(ct)
            } catch (e: AEADBadTagException) {
                throw BackupDecryptException("Falsches Passwort oder beschädigte Backup-Datei.", e)
            }
            output.write(pt)
            counter++

            if (isFinal) break
            lenBytes = nextLenBytes!!
        }
        output.flush()
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(passphrase, salt, iterations, 256)
        try {
            val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            return SecretKeySpec(encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun nonce(prefix: ByteArray, counter: Int, isFinal: Boolean): ByteArray {
        val c = if (isFinal) counter or FINAL_FLAG else counter
        return ByteArray(NONCE_LEN).also {
            System.arraycopy(prefix, 0, it, 0, NONCE_PREFIX_LEN)
            it[8] = (c ushr 24).toByte()
            it[9] = (c ushr 16).toByte()
            it[10] = (c ushr 8).toByte()
            it[11] = c.toByte()
        }
    }

    private fun writeInt(output: OutputStream, value: Int) {
        output.write(ByteBuffer.allocate(4).putInt(value).array())
    }

    /** Füllt [buf] so weit wie möglich. Gibt die Byte-Anzahl zurück oder -1 bei sofortigem EOF. */
    private fun readChunk(input: InputStream, buf: ByteArray): Int {
        var total = 0
        while (total < buf.size) {
            val r = input.read(buf, total, buf.size - total)
            if (r < 0) break
            total += r
        }
        return if (total == 0) -1 else total
    }

    /** Liest genau [n] Bytes. Gibt null bei sauberem EOF zurück; wirft bei einem partiellen (beschädigten) Lesen. */
    private fun readExactly(input: InputStream, n: Int): ByteArray? {
        val buf = ByteArray(n)
        var total = 0
        while (total < n) {
            val r = input.read(buf, total, n - total)
            if (r < 0) break
            total += r
        }
        if (total == 0) return null
        if (total < n) throw BackupDecryptException("Backup-Datei ist unvollständig.")
        return buf
    }
}
