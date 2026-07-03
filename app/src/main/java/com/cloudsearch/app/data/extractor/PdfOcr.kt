package me.fulltxt.app.data.extractor

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlin.math.max

/**
 * On-Device-OCR für gescannte (reine Bild-)PDFs. Jede Seite wird mit dem Plattform-[PdfRenderer]
 * zu einer Bitmap gerendert und durch ML Kits mitgelieferten lateinischen Texterkenner geschickt —
 * ohne Netzwerk, ohne Google-Play-Services-Download. Das ist bewusst teuer (CPU/Akku/Zeit) und wird
 * nur aufgerufen, wenn OCR in den Einstellungen aktiviert ist UND das PDF keine nutzbare eingebettete
 * Textebene hat.
 *
 * Absichtlich nur für Android (PdfRenderer + ML Kit). Eine künftige Desktop-Portierung würde diese
 * Datei ersetzen.
 */
object PdfOcr {

    /** Ziel-Renderauflösung. ~200 DPI geben ML Kit genug Details ohne riesige Bitmaps. */
    private const val TARGET_DPI = 200f
    private const val POINTS_PER_INCH = 72f

    /** Harte Obergrenze für die längere Bitmap-Kante, um den Speicher bei großen/posterartigen Seiten zu begrenzen. */
    private const val MAX_EDGE_PX = 2400

    fun extract(file: File): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    return buildString {
                        for (i in 0 until renderer.pageCount) {
                            // Eine einzelne beschädigte/übergroße Seite darf nicht das ganze Dokument abbrechen.
                            val pageText = runCatching { ocrPage(renderer, i, recognizer) }
                                .getOrDefault("")
                            if (pageText.isNotEmpty()) appendLine(pageText)
                        }
                    }.trim()
                }
            }
        } finally {
            recognizer.close()
        }
    }

    private fun ocrPage(
        renderer: PdfRenderer,
        index: Int,
        recognizer: com.google.mlkit.vision.text.TextRecognizer
    ): String {
        renderer.openPage(index).use { page ->
            val scale = TARGET_DPI / POINTS_PER_INCH
            var width = (page.width * scale).toInt().coerceAtLeast(1)
            var height = (page.height * scale).toInt().coerceAtLeast(1)
            val longest = max(width, height)
            if (longest > MAX_EDGE_PX) {
                val r = MAX_EDGE_PX.toFloat() / longest
                width = (width * r).toInt().coerceAtLeast(1)
                height = (height * r).toInt().coerceAtLeast(1)
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                // PDFs sind dort transparent, wo nichts gezeichnet ist; weiß füllen, damit Text schwarz-auf-weiß bleibt.
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val image = InputImage.fromBitmap(bitmap, 0)
                // Blockierendes await ist in Ordnung: Die Extraktion läuft bereits auf einem Hintergrund-Index-Thread.
                return Tasks.await(recognizer.process(image)).text.trim()
            } finally {
                bitmap.recycle()
            }
        }
    }
}
