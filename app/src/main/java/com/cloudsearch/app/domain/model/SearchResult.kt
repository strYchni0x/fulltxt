package me.fulltxt.app.domain.model

data class SearchResult(
    val file: CloudFile,
    val snippet: String,
    val score: Double,
    /** Nicht leer, wenn dieselbe Datei (fileName + fileSizeBytes) bei mehreren Anbietern/Konten existiert. */
    val duplicateProviders: List<CloudProvider> = emptyList()
)
