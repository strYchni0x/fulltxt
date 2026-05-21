package me.fulltxt.app.data.extractor

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
import java.io.File

object TextExtractor {

    fun extract(file: File, mimeType: String): String = runCatching {
        when {
            isPlainText(mimeType) -> extractPlainText(file)
            isPdf(mimeType)       -> extractPdf(file)
            isDocx(mimeType)      -> extractDocx(file)
            isXlsx(mimeType)      -> extractXlsx(file)
            isPptx(mimeType)      -> extractPptx(file)
            else                  -> ""
        }
    }.getOrDefault("")

    private fun isPlainText(mimeType: String) = mimeType.startsWith("text/")
    private fun isPdf(mimeType: String)  = mimeType == "application/pdf"
    private fun isDocx(mimeType: String) = mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    private fun isXlsx(mimeType: String) = mimeType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private fun isPptx(mimeType: String) = mimeType == "application/vnd.openxmlformats-officedocument.presentationml.presentation"

    private fun extractPlainText(file: File): String =
        runCatching { file.readText(Charsets.UTF_8) }
            .getOrElse { file.readText(Charsets.ISO_8859_1) }

    private fun extractPdf(file: File): String =
        PDDocument.load(file).use { PDFTextStripper().getText(it) }

    private fun extractDocx(file: File): String =
        XWPFDocument(file.inputStream()).use { doc ->
            buildString {
                // Iterate body elements in document order to preserve paragraph/table sequence
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
