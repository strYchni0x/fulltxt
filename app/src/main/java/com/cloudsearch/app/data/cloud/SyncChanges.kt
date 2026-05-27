package me.fulltxt.app.data.cloud

import me.fulltxt.app.domain.model.CloudFile

/**
 * Result of an incremental sync (or a first full scan when changeToken was null).
 *
 * @param changed      Files that were added or modified since the last sync.
 * @param deletedIds   File IDs that were deleted since the last sync.
 *                     Empty for providers without a server-side deletion log (WebDAV, Dropbox).
 * @param newChangeToken Token / delta-link / cursor to pass into the next [CloudConnector.getChanges] call.
 */
data class SyncChanges(
    val changed: List<CloudFile>,
    val deletedIds: List<String>,
    val newChangeToken: String
)
