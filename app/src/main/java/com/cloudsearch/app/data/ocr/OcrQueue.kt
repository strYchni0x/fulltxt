package me.fulltxt.app.data.ocr

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent, restart-safe queue of file IDs that still need OCR. Scanned PDFs detected during
 * the fast indexing pass are enqueued here; [me.fulltxt.app.worker.OcrWorker] drains the queue
 * separately and removes each ID as soon as its OCR result is stored. Because progress is banked
 * per file, an interrupted OCR run simply continues from the remaining IDs instead of restarting.
 *
 * Backed by SharedPreferences (a String set) to avoid a Room schema migration.
 */
@Singleton
class OcrQueue @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences("fulltxt_ocr", Context.MODE_PRIVATE)

    @Synchronized
    fun add(fileId: String) {
        val set = read()
        if (set.add(fileId)) write(set)
    }

    @Synchronized
    fun remove(fileId: String) {
        val set = read()
        if (set.remove(fileId)) write(set)
    }

    @Synchronized
    fun snapshot(): List<String> = read().toList()

    @Synchronized
    fun size(): Int = read().size

    @Synchronized
    fun clear() = prefs.edit().remove(KEY).apply()

    // getStringSet returns a shared instance that must not be mutated, so copy into a new set.
    private fun read(): MutableSet<String> = HashSet(prefs.getStringSet(KEY, emptySet()) ?: emptySet())

    private fun write(set: Set<String>) = prefs.edit().putStringSet(KEY, set).apply()

    companion object {
        private const val KEY = "pending"
    }
}
