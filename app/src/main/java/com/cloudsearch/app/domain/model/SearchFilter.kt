package me.fulltxt.app.domain.model

data class SearchFilter(
    val providers: Set<CloudProvider> = emptySet(),
    val fileTypes: Set<FileTypeFilter> = emptySet(),
    val dateFilter: DateFilter? = null
) {
    val isActive get() = providers.isNotEmpty() || fileTypes.isNotEmpty() || dateFilter != null
}

enum class FileTypeFilter(val label: String, val extensions: Set<String>) {
    PDF("PDF", setOf("pdf")),
    WORD("Word", setOf("docx")),
    EXCEL("Excel", setOf("xlsx")),
    POWERPOINT("PowerPoint", setOf("pptx")),
    TEXT("Text", setOf("txt", "md", "csv", "log"))
}

enum class DateFilter(val label: String, private val days: Long) {
    LAST_WEEK("Letzte 7 Tage", 7),
    LAST_MONTH("Letzter Monat", 30),
    LAST_YEAR("Letztes Jahr", 365);

    val cutoffMillis: Long get() = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
}
