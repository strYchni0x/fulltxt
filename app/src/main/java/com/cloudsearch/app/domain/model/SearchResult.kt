package me.fulltxt.app.domain.model

data class SearchResult(
    val file: CloudFile,
    val snippet: String,
    val score: Double
)
