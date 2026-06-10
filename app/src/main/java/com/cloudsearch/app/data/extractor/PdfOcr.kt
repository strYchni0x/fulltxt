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
 * On-device OCR for scanned (image-only) PDFs. Each page is rendered to a bitmap with the
 * platform [PdfRenderer] and run through ML Kit's bundled Latin text recognizer — no network,
 * no Google Play Services download. This is deliberately expensive (CPU/battery/time) and is
 * only invoked when OCR is enabled in settings AND the PDF has no usable embedded text layer.
 *
 * Android-only by design (PdfRenderer + ML Kit). A future desktop port would replace this file.
 */
object PdfOcr {

    /** Render target resolution. ~200 DPI gives ML Kit enough detail without huge bitmaps. */
    private const val TARGET_DPI = 200f
    private const val POINTS_PER_INCH = 72f

    /** Hard cap on the longer bitmap edge to bound memory on large/poster-sized pages. */
    private const val MAX_EDGE_PX = 2400

    fun extract(file: File): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    return buildString {
                        for (i in 0 until renderer.pageCount) {
                            // A single corrupt/oversized page must not abort the whole document.
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
                // PDFs are transparent where nothing is drawn; paint white so text stays black-on-white.
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val image = InputImage.fromBitmap(bitmap, 0)
                // Blocking await is fine: extraction already runs on a background indexing thread.
                return Tasks.await(recognizer.process(image)).text.trim()
            } finally {
                bitmap.recycle()
            }
        }
    }
}
