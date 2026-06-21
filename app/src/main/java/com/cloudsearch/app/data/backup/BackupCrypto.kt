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

/** Thrown when a backup file cannot be decrypted (wrong password or corrupted/truncated). */
class BackupDecryptException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Streaming authenticated encryption for FullTXT index backups.
 *
 * The index is the most sensitive artefact in the app — it holds the extracted plain text of
 * every indexed document. A raw `.db` export would leave that text unprotected wherever the
 * backup file lands (Downloads folder, cloud storage, ...). This wraps the export in
 * AES-256-GCM with a key derived from a user passphrase (PBKDF2), so the backup stays portable
 * (restorable on any device, unlike a hardware-Keystore key) while being unreadable at rest.
 *
 * Format (all integers big-endian):
 *
 *   header: "FTXTBK01" (8 B) | iterations (4 B) | salt (16 B) | nonce prefix (8 B)
 *   chunk*: ciphertext length (4 B) | ciphertext + GCM tag
 *
 * Each plaintext chunk (max [CHUNK_SIZE]) is encrypted under its own 12-byte nonce
 * (8-byte random prefix ‖ 4-byte counter). The most significant counter bit flags the final
 * chunk, so dropping or appending chunks fails authentication — i.e. truncation is detected.
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

        // Read one chunk ahead so we know which chunk is the final one.
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

            // Another length header means more chunks follow; EOF means this is the final chunk.
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

    /** Fills [buf] as far as possible. Returns the byte count, or -1 at immediate EOF. */
    private fun readChunk(input: InputStream, buf: ByteArray): Int {
        var total = 0
        while (total < buf.size) {
            val r = input.read(buf, total, buf.size - total)
            if (r < 0) break
            total += r
        }
        return if (total == 0) -1 else total
    }

    /** Reads exactly [n] bytes. Returns null at a clean EOF; throws on a partial (corrupt) read. */
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
