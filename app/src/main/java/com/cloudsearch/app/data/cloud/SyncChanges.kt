package me.fulltxt.app.data.cloud

import me.fulltxt.app.domain.model.CloudFile

/**
 * Ergebnis eines inkrementellen Syncs (oder eines ersten vollständigen Scans, wenn changeToken null war).
 *
 * @param changed      Dateien, die seit dem letzten Sync hinzugefügt oder geändert wurden.
 * @param deletedIds   Datei-IDs, die seit dem letzten Sync gelöscht wurden.
 *                     Leer bei Anbietern ohne serverseitiges Löschprotokoll (WebDAV, Dropbox).
 * @param newChangeToken Token / Delta-Link / Cursor für den nächsten [CloudConnector.getChanges]-Aufruf.
 */
data class SyncChanges(
    val changed: List<CloudFile>,
    val deletedIds: List<String>,
    val newChangeToken: String
)
