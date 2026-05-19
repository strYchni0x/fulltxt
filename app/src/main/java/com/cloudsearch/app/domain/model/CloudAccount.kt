package me.fulltxt.app.domain.model

data class CloudAccount(
    val accountId: String,
    val provider: CloudProvider,
    val displayName: String,
    val email: String
)
