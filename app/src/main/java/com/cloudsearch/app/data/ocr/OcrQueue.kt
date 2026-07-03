package me.fulltxt.app.data.ocr

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistente, neustartsichere Warteschlange von Datei-IDs, die noch OCR benötigen. Gescannte PDFs,
 * die während des schnellen Indexlaufs erkannt werden, werden hier eingereiht;
 * [me.fulltxt.app.worker.OcrWorker] arbeitet die Warteschlange separat ab und entfernt jede ID,
 * sobald ihr OCR-Ergebnis gespeichert ist. Da der Fortschritt pro Datei gesichert wird, setzt ein
 * unterbrochener OCR-Lauf einfach bei den verbleibenden IDs fort, statt neu zu starten.
 *
 * Gestützt auf SharedPreferences (ein String-Set), um eine Room-Schema-Migration zu vermeiden.
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

    // getStringSet gibt eine geteilte Instanz zurück, die nicht verändert werden darf, daher in ein neues Set kopieren.
    private fun read(): MutableSet<String> = HashSet(prefs.getStringSet(KEY, emptySet()) ?: emptySet())

    private fun write(set: Set<String>) = prefs.edit().putStringSet(KEY, set).apply()

    companion object {
        private const val KEY = "pending"
    }
}
