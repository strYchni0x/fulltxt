package me.fulltxt.app.data.extractor

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File

object TextExtractor {

    fun extract(file: File, mimeType: String): String = when {
        isPlainText(mimeType) -> file.readText(Charsets.UTF_8)
        isPdf(mimeType) -> extractPdf(file)
        isDocx(mimeType) -> extractDocx(file)
        isXlsx(mimeType) -> extractXlsx(file)
        isPptx(mimeType) -> extractPptx(file)
        else -> ""
    }

    private fun isPlainText(mimeType: String) =
        mimeType.startsWith("text/") || mimeType == "application/csv"

    private fun isPdf(mimeType: String) = mimeType == "application/pdf"

    private fun isDocx(mimeType: String) =
        mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    private fun isXlsx(mimeType: String) =
        mimeType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    private fun isPptx(mimeType: String) =
        mimeType == "application/vnd.openxmlformats-officedocument.presentationml.presentation"

    private fun extractPdf(file: File): String =
        PDDocument.load(file).use { PDFTextStripper().getText(it) }

    private fun extractDocx(file: File): String =
        XWPFDocument(file.inputStream()).use { doc ->
            doc.paragraphs.joinToString("\n") { it.text }
        }

    private fun extractXlsx(file: File): String =
        XSSFWorkbook(file.inputStream()).use { wb ->
            buildString {
                for (sheet in wb) {
                    for (row in sheet) {
                        appendLine(row.joinToString("\t") { cell -> cell.toString() })
                    }
                }
            }
        }

    private fun extractPptx(file: File): String =
        XMLSlideShow(file.inputStream()).use { ppt ->
            ppt.slides.joinToString("\n") { slide ->
                slide.shapes
                    .filterIsInstance<XSLFTextShape>()
                    .joinToString("\n") { it.text }
            }
        }
}
