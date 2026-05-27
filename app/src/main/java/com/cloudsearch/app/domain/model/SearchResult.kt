package me.fulltxt.app.domain.model

data class SearchResult(
    val file: CloudFile,
    val snippet: String,
    val score: Double,
    /** Non-empty when the same file (fileName + fileSizeBytes) exists on multiple providers/accounts. */
    val duplicateProviders: List<CloudProvider> = emptyList()
)
