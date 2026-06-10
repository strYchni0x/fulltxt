# FullTXT

**Volltextsuche über alle Cloud-Speicher — vollständig privat, ausschließlich auf dem Gerät.**

FullTXT indexiert Dateien aus verbundenen Cloud-Accounts und ermöglicht die Suche in ihren Inhalten innerhalb von Sekunden. Der Index liegt ausschließlich auf dem eigenen Smartphone. Es werden keinerlei Daten das Gerät verlassen.

> 📄 [English documentation → README.md](README.md)

---

## Funktionen

- **Volltextsuche** über alle verbundenen Cloud-Anbieter gleichzeitig
- **Lokaler SQLite-FTS5-Index** — Suchen laufen offline, kein Datenabruf aus der Cloud
- **Dateien öffnen** direkt aus dem Suchergebnis heraus, in der jeweiligen Cloud-App oder im Browser
- **Duplikatserkennung** — Dateien, die bei mehreren Anbietern vorhanden sind, werden automatisch markiert
- **Delta-Sync** — Folgeindexierungen laden nur Änderungen, nicht den gesamten Bestand
- **Täglicher automatischer Sync** — optionaler Account-spezifischer Hintergrund-Update einmal täglich
- **Helles / dunkles Design** — dem System folgen oder in der App fest hell/dunkel wählen
- **Privacy by Design** — kein Tracking, keine Telemetrie, keine Weitergabe an Dritte

---

## Unterstützte Cloud-Anbieter

| Anbieter | Protokoll / API | Authentifizierung |
|---|---|---|
| Google Drive | Google Drive API v3 | OAuth 2.0 |
| Microsoft OneDrive | Microsoft Graph API (Delta) | OAuth 2.0 (MSAL) |
| Dropbox | Dropbox API v2 | OAuth 2.0 + PKCE |
| Nextcloud | WebDAV (PROPFIND / GET) | App-Passwort |
| ownCloud | WebDAV (PROPFIND / GET) | App-Passwort |
| MagentaCloud (Telekom) | WebDAV (Nextcloud-Backend) | App-Passwort |
| Strato HiDrive | WebDAV (PROPFIND / GET) | Benutzername + Passwort |
| Yandex Disk | WebDAV (PROPFIND / GET) | Benutzername + Passwort / App-Passwort |

Mehrere Accounts desselben Anbieters werden unterstützt.

---

## Unterstützte Dateiformate

| Format | Erweiterungen | Hinweis |
|---|---|---|
| Nur-Text | `.txt` `.md` `.csv` `.log` | Direkt lesbar, keine Bibliothek nötig |
| PDF | `.pdf` | Durchsuchbare PDFs via PDFBox; gescannte PDFs optional per Offline-OCR (ML Kit, in Einstellungen aktivierbar, standardmäßig aus) |
| Word | `.docx` | Apache POI |
| Excel | `.xlsx` | Apache POI |
| PowerPoint | `.pptx` | Apache POI |
| OpenDocument Text | `.odt` | Integrierter ZIP/XML-Parser |
| OpenDocument Tabelle | `.ods` | Integrierter ZIP/XML-Parser |
| OpenDocument Präsentation | `.odp` | Integrierter ZIP/XML-Parser |

Dateien über **50 MB** werden übersprungen, um Speicherüberlauf zu verhindern.

---

## Erste Schritte

### 1 — Account verbinden

Öffne **Einstellungen** (Zahnrad-Symbol oben rechts), tippe auf **Cloud-Speicher & Konten** und dann auf den gewünschten Anbieter:

- **Google Drive / OneDrive / Dropbox** — Ein Browser-OAuth-Flow öffnet sich; anmelden und Lesezugriff erteilen.
- **Nextcloud / ownCloud / MagentaCloud** — Server-URL und ein *App-Passwort* eingeben (in den Sicherheitseinstellungen der jeweiligen Cloud erstellen, um das Hauptpasswort nicht zu verwenden).
- **Strato HiDrive** — Strato-Benutzername und Passwort eingeben.
- **Yandex Disk** — Yandex-Benutzername und Passwort eingeben (bei aktivierter Zwei-Faktor-Authentifizierung ein App-Passwort verwenden).

### 2 — Dateien indexieren

Nach dem Verbinden auf **Indexieren** in der Account-Karte tippen. Eine dauerhafte Benachrichtigung zeigt den Fortschritt. Der erste Durchlauf lädt alle unterstützten Dateien herunter und extrahiert deren Text; Folgedurchläufe holen nur Änderungen (Delta-Sync).

> **Tipp:** Die Indexierung eines großen Accounts (10.000+ Dateien) kann mehrere Minuten dauern. Das Display kann ausgeschaltet bleiben — der Foreground-Service verhindert, dass Android den Prozess abbricht.

### 3 — Suchen

Zurück auf den Hauptbildschirm: Suchbegriff eingeben. Ergebnisse erscheinen ab zwei Zeichen, nach Relevanz sortiert, mit hervorgehobenen Treffern im Snippet.

Auf ein Ergebnis tippen öffnet die Datei in der zugehörigen Cloud-App oder im Browser.

---

## Suchergebnisse

### Snippets

Übereinstimmende Suchbegriffe werden im zweiزeiligen Textvorschau **fett** hervorgehoben.

### Duplikatserkennung

Wenn dieselbe Datei (gleicher Name *und* gleiche Dateigröße) bei mehr als einem Anbieter oder Account vorhanden ist, wird in jedem betroffenen Suchergebnis eine zusätzliche Zeile angezeigt:

```
OneDrive
Auch: Dropbox          ← erscheint sowohl beim OneDrive- als auch beim Dropbox-Treffer
```

---

## Einstellungen

### Netzwerk — Mobile Daten

Standardmäßig läuft die Indexierung nur über **WLAN**, um unerwartete Datenkosten zu vermeiden.

Unter **Einstellungen → Indexierung → Mobilfunk erlauben** kann die Indexierung auch über Mobilfunknetze (5G / LTE) erlaubt werden. Ein Bestätigungsdialog weist auf den möglichen Datenverbrauch hin.

> Die Änderung dieser Einstellung wirkt sich beim nächsten Klick auf **Indexieren** oder beim nächsten täglichen Sync aus. Ein bereits laufender Job, der mit der WLAN-Einschränkung gestartet wurde, wird nicht nachträglich angepasst.

### Täglicher automatischer Delta-Sync

In jeder Account-Karte wird nach Abschluss der ersten vollständigen Indexierung ein **Tägl. Delta-Sync**-Schalter angezeigt.

| Zustand | Verhalten |
|---|---|
| Aus (Standard) | Index wird nur beim manuellen Tippen auf „Neu indexieren" aktualisiert |
| Ein | WorkManager plant einmal täglich einen Delta-Sync |

Der periodische Job verwendet dieselbe Netzwerkbeschränkung wie eine manuelle Indexierung (nur WLAN, außer Mobilfunk ist aktiviert). Android wählt den genauen Ausführungszeitpunkt innerhalb des 24-Stunden-Fensters anhand des Gerätezustands (Laden, Inaktiv, Netzwerk).

Beim Trennen eines Accounts wird der tägliche Sync automatisch abgebrochen.

### Darstellung — helles / dunkles Design

Unter **Einstellungen → Darstellung** lässt sich **System**, **Hell** oder **Dunkel** wählen. Die Änderung wirkt sofort app-weit. **System** (Standard) folgt der Hell-/Dunkel-Einstellung des Geräts; auf Android 12+ passt sich die Farbpalette zusätzlich per Material You (Dynamic Color) an.

### Cloud-Speicher & Konten

Verbundene Konten und die Verbinden-Buttons je Anbieter liegen auf einer eigenen Seite, erreichbar über **Einstellungen → Cloud-Speicher & Konten** — so bleibt die Hauptseite der Einstellungen kompakt.

---

## Datenschutz & Sicherheit

| Aspekt | Detail |
|---|---|
| Index-Speicherung | Ausschließlich im privaten internen App-Speicher — für andere Apps nicht zugänglich |
| Heruntergeladene Dateien | Werden sofort nach der Textextraktion gelöscht; keine dauerhafte Speicherung |
| OAuth-Token | Gespeichert in Android `EncryptedSharedPreferences` |
| Telemetrie | Keine — kein Analytics, kein Crash-Reporting, keine Drittanbieter-SDKs |
| Cloud-Sync des Index | Nicht implementiert; der Index verbleibt auf dem Gerät |

---

## Architektur

```
┌──────────────────────────────────────────────────────┐
│                    FullTXT App                       │
│                                                      │
│  ┌─────────────────┐   ┌────────────────────────┐   │
│  │   UI-Schicht    │   │  WorkManager / Jobs    │   │
│  │  (Compose/MVVM) │   │  IndexingWorker        │   │
│  │  SearchScreen   │   │  (Foreground-Service,  │   │
│  │  SettingsScreen │   │   Delta- & Tages-Sync) │   │
│  └────────┬────────┘   └───────────┬────────────┘   │
│           │                        │                 │
│  ┌────────▼────────────────────────▼────────────┐   │
│  │              Repository-Schicht              │   │
│  │   SearchRepository   │   IndexRepository    │   │
│  └────────────────┬─────────────────────────────┘   │
│                   │                                  │
│  ┌────────────────▼─────────────────────────────┐   │
│  │      Lokale Datenbank (Room / FTS5)          │   │
│  │   file_metadata  │  file_content_fts         │   │
│  └──────────────────────────────────────────────┘   │
│                                                      │
│  ┌──────────────────────────────────────────────┐   │
│  │           Cloud-Connector-Schicht            │   │
│  │  Google Drive · OneDrive · Dropbox           │   │
│  │  Nextcloud · ownCloud · MagentaCloud ·       │   │
│  │  Strato · Yandex                             │   │
│  │  (Rate-Limit-Middleware, OAuth, Delta-Token) │   │
│  └──────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────┘
```

### Technologie-Stack

| Bereich | Technologie |
|---|---|
| Sprache | Kotlin |
| UI | Jetpack Compose |
| Architektur | MVVM + Repository-Pattern |
| Datenbank | Room (SQLite FTS5) |
| Hintergrundarbeit | WorkManager (Foreground-Service + PeriodicWork) |
| HTTP-Client | OkHttp + Retrofit |
| Google-Authentifizierung | Google Identity Services (OAuth 2.0) |
| Microsoft-Authentifizierung | MSAL (Microsoft Authentication Library) |
| Dropbox-Authentifizierung | PKCE OAuth 2.0 |
| PDF-Parsing | PdfBox-Android |
| Office-Parsing | Apache POI |
| Dependency Injection | Hilt |

### Zentrale Design-Entscheidungen

**FTS5-Index** — SQLite FTS5 ist in Android integriert und benötigt keine zusätzlichen Abhängigkeiten. Die `snippet()`-Hilfsfunktion extrahiert direkt aus der Datenbank hervorgehobene Vorschauen der gefundenen Begriffe.

**Delta-Sync** — Alle Connectoren implementieren `getChanges(accountId, changeToken?)`. Beim ersten Aufruf ist `changeToken` null, was eine vollständige Dateiliste auslöst. Bei Folgeaufrufen werden nur seit dem letzten Token geänderte Einträge zurückgegeben. Google Drive und OneDrive liefern echte Lösch-IDs; WebDAV-basierte Anbieter fallen auf eine vollständige Neuliste mit eTag-basierter Deduplizierung zurück.

**Foreground-Service** — `IndexingWorker` ruft direkt nach dem Start `setForeground(ForegroundInfo(..., DATA_SYNC))` auf. Dies verhindert, dass Android den Worker mitten in der Ausführung beendet — wichtig bei OneDrive-Accounts mit 10.000+ Dateien.

**50-MB-Dateigrößenlimit** — Dateien über 50 MB werden stillschweigend übersprungen. Dies verhindert Out-of-Memory-Abstürze auf Geräten mit begrenztem Heap-Speicher.

**Duplikatserkennung** — Nach jeder Suchanfrage ruft `FileIndexDao.getByFileNames()` alle indizierten Einträge ab, deren Dateinamen zu den Ergebnissen passen. Gruppen mit mehr als einer eindeutigen `accountId` und identischer `fileSizeBytes` gelten als Duplikate.

---

## Build-Anleitung

### Voraussetzungen

- Android Studio Hedgehog oder neuer
- JDK 17 (im Android Studio enthalten)
- Ein physisches oder virtuelles Android-Gerät mit API 26 oder höher

### Google Drive einrichten

1. Projekt in der [Google Cloud Console](https://console.cloud.google.com/) anlegen
2. **Google Drive API** aktivieren
3. **OAuth-2.0-Client-ID** (Android-App) erstellen: Paketname `me.fulltxt.app` und SHA-1-Fingerprint des Debug-Keystores eintragen
4. Test-Accounts unter OAuth-Zustimmungsbildschirm → Testnutzer hinzufügen

### OneDrive / MSAL einrichten

1. Anwendung im [Azure-Portal](https://portal.azure.com/) → App-Registrierungen anlegen
2. Android-Redirect-URI hinzufügen: `msauth://me.fulltxt.app/<base64-sha1-des-keystores>` (Azure speichert sie URL-kodiert)
3. Die **Anwendungs-ID (Client-ID)** in `app/src/main/res/raw/msal_config.json` eintragen

### Dropbox einrichten

1. App in der [Dropbox App Console](https://www.dropbox.com/developers/apps) anlegen
2. Scopes aktivieren: `files.metadata.read`, `files.content.read`, `account_info.read`
3. Redirect-URI eintragen: `fulltxt://dropbox-auth`
4. **App-Key** und **App-Secret** im Dropbox-Auth-Manager eintragen

### Starten

```bash
# Auf verbundenem Gerät installieren (Windows)
.\gradlew.bat installDebug

# Auf verbundenem Gerät installieren (macOS / Linux)
./gradlew installDebug
```

> **Hinweis:** Falls Gradle das JDK nicht findet, `JAVA_HOME` auf das in Android Studio enthaltene JDK setzen (z. B. `$env:JAVA_HOME = "Pfad\zu\AndroidStudio\jbr"` unter Windows bzw. `export JAVA_HOME="Pfad/zu/AndroidStudio/jbr"` unter macOS/Linux).

---

## Roadmap

| Status | Feature |
|---|---|
| ✅ | Google Drive, OneDrive, Dropbox, Nextcloud, ownCloud, MagentaCloud, Strato HiDrive, Yandex Disk |
| ✅ | Lokaler FTS5-Volltextindex |
| ✅ | PDF-, DOCX-, XLSX-, PPTX-Unterstützung |
| ✅ | Foreground-Service (verhindert Hintergrundabbruch) |
| ✅ | Delta-Sync (Change-Token) |
| ✅ | Täglicher automatischer Sync (PeriodicWork) |
| ✅ | Duplikatserkennung |
| ✅ | Mobilfunk-Schalter (geräteweit) |
| ✅ | Index-Export / Backup |
| ✅ | Filter nach Anbieter, Dateityp, Datum |
| ✅ | Heller / dunkler Design-Umschalter (System / Hell / Dunkel) |
| ✅ | Lokale Ordner indexieren (via Storage Access Framework, rekursiv, Delta-Sync) |
| 🔜 | Mehrere Accounts pro Anbieter |
| ✅ | OpenDocument-Formate (`.odt`, `.ods`, `.odp`) |
| ✅ | Index-Export / Backup |
| 🔜 | Play-Store-Veröffentlichung |

---

## Lizenz

Noch festzulegen
