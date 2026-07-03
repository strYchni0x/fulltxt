package me.fulltxt.app.data.extractor

import android.util.Xml
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTable
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader
import java.util.zip.ZipFile

object TextExtractor {

    /** Ein PDF mit weniger als so vielen Nicht-Leerraum-Zeichen gilt als ohne Textebene. */
    private const val OCR_TEXT_LAYER_THRESHOLD = 16

    fun extract(file: File, mimeType: String): String = runCatching {
        when {
            isPlainText(mimeType) -> extractPlainText(file)
            isPdf(mimeType)       -> extractPdf(file)
            isDocx(mimeType)      -> extractDocx(file)
            isXlsx(mimeType)      -> extractXlsx(file)
            isPptx(mimeType)      -> extractPptx(file)
            isOdf(mimeType)       -> extractOdf(file)
            else                  -> ""
        }
    }.getOrDefault("")

    private fun isPlainText(mimeType: String) = mimeType.startsWith("text/")
    private fun isPdf(mimeType: String)  = mimeType == "application/pdf"
    private fun isDocx(mimeType: String) = mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    private fun isXlsx(mimeType: String) = mimeType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private fun isPptx(mimeType: String) = mimeType == "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    private fun isOdf(mimeType: String)  = mimeType.startsWith("application/vnd.oasis.opendocument.")

    private fun extractPlainText(file: File): String =
        runCatching { file.readText(Charsets.UTF_8) }
            .getOrElse { file.readText(Charsets.ISO_8859_1) }

    private fun extractPdf(file: File): String =
        PDDocument.load(file).use { PDFTextStripper().getText(it) }

    /**
     * True für ein gescanntes/reines Bild-PDF: Die Datei ist ein PDF, aber [extractedText] (aus
     * [extract]) hat praktisch keine eingebettete Textebene. Solche Dateien werden für OCR
     * eingereiht, statt inline OCR-verarbeitet zu werden, damit der Haupt-Indexlauf schnell bleibt
     * und der OCR-Durchlauf separat laufen kann.
     */
    fun pdfNeedsOcr(mimeType: String, extractedText: String): Boolean =
        isPdf(mimeType) && extractedText.count { !it.isWhitespace() } < OCR_TEXT_LAYER_THRESHOLD

    private fun extractDocx(file: File): String =
        XWPFDocument(file.inputStream()).use { doc ->
            buildString {
                // Body-Elemente in Dokumentreihenfolge durchlaufen, um die Absatz-/Tabellen-Reihenfolge zu erhalten
                doc.bodyElements.forEach { element ->
                    when (element) {
                        is XWPFParagraph -> {
                            val text = element.text.trim()
                            if (text.isNotEmpty()) appendLine(text)
                        }
                        is XWPFTable -> element.rows.forEach { row ->
                            val text = row.tableCells.joinToString("\t") { cell ->
                                cell.paragraphs.joinToString(" ") { it.text.trim() }
                            }.trim()
                            if (text.isNotEmpty()) appendLine(text)
                        }
                    }
                }
            }
        }

    private fun extractXlsx(file: File): String {
        val formatter = DataFormatter()
        return XSSFWorkbook(file.inputStream()).use { wb ->
            buildString {
                for (sheet in wb) {
                    for (row in sheet) {
                        val first = row.firstCellNum.toInt()
                        val last = row.lastCellNum.toInt()
                        if (first < 0) continue
                        val cells = (first until last).map { col ->
                            val cell = row.getCell(col)
                            if (cell != null) formatter.formatCellValue(cell) else ""
                        }
                        if (cells.any { it.isNotBlank() }) appendLine(cells.joinToString("\t"))
                    }
                }
            }
        }
    }

    /**
     * Extrahiert Text aus OpenDocument-Formaten (.odt, .ods, .odp).
     * ODF-Dateien sind ZIP-Archive; der Text steckt in <text:p>- / <text:h>-Elementen in content.xml.
     */
    private fun extractOdf(file: File): String {
        val xml = ZipFile(file).use { zip ->
            zip.getEntry("content.xml")?.let { entry ->
                zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
            }
        } ?: return ""

        val parser: XmlPullParser = Xml.newPullParser()
        parser.setInput(StringReader(xml))

        return buildString {
            var paragraphDepth = 0
            val currentParagraph = StringBuilder()

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "text:p" || parser.name == "text:h") {
                            paragraphDepth++
                            if (paragraphDepth == 1) currentParagraph.clear()
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (paragraphDepth > 0) currentParagraph.append(parser.text)
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "text:p" || parser.name == "text:h") {
                            paragraphDepth--
                            if (paragraphDepth == 0) {
                                val text = currentParagraph.toString().trim()
                                if (text.isNotEmpty()) appendLine(text)
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        }.trim()
    }

    private fun extractPptx(file: File): String =
        XMLSlideShow(file.inputStream()).use { ppt ->
            ppt.slides.joinToString("\n") { slide ->
                buildString {
                    slide.shapes.forEach { shape ->
                        when (shape) {
                            is XSLFTable -> shape.rows.forEach { row ->
                                val text = row.cells.joinToString("\t") { it.text?.trim() ?: "" }.trim()
                                if (text.isNotEmpty()) appendLine(text)
                            }
                            is XSLFTextShape -> {
                                val text = shape.text?.trim() ?: ""
                                if (text.isNotEmpty()) appendLine(text)
                            }
                        }
                    }
                }
            }
        }
}
