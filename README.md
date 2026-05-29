# FullTXT

**Full-text search across all your cloud storage — completely private, entirely on-device.**

FullTXT indexes files from connected cloud accounts and lets you search their content in seconds. The index lives exclusively on your phone. No data ever leaves your device.

> 📄 [Deutsche Dokumentation → README_DE.md](README_DE.md)

---

## Features

- **Full-text search** across all connected cloud providers simultaneously
- **Local SQLite FTS5 index** — searches run offline, no round-trips to the cloud
- **Open files** directly from search results in the respective cloud or viewer app
- **Duplicate detection** — files that exist on multiple providers are marked automatically
- **Delta sync** — subsequent index runs only fetch changes, not all files again
- **Daily automatic sync** — optional per-account background update once every 24 hours
- **Privacy by design** — no telemetry, no tracking, no third-party data transfer

---

## Supported Cloud Providers

| Provider | Protocol / API | Authentication |
|---|---|---|
| Google Drive | Google Drive API v3 | OAuth 2.0 |
| Microsoft OneDrive | Microsoft Graph API (delta) | OAuth 2.0 (MSAL) |
| Dropbox | Dropbox API v2 | OAuth 2.0 + PKCE |
| Nextcloud | WebDAV (PROPFIND / GET) | App password |
| ownCloud | WebDAV (PROPFIND / GET) | App password |
| MagentaCloud (Telekom) | WebDAV (Nextcloud backend) | App password |
| Strato HiDrive | WebDAV (PROPFIND / GET) | Username + password |

Multiple accounts of the same provider are supported.

---

## Supported File Formats

| Format | Extensions | Notes |
|---|---|---|
| Plain text | `.txt` `.md` `.csv` `.log` | Directly readable, no library needed |
| PDF | `.pdf` | Searchable PDFs only — no OCR for scanned pages |
| Word | `.docx` | Apache POI |
| Excel | `.xlsx` | Apache POI |
| PowerPoint | `.pptx` | Apache POI |

Files larger than **50 MB** are skipped to prevent out-of-memory errors.

---

## Getting Started

### 1 — Connect an account

Open **Settings** (gear icon, top right) and tap the provider you want to connect:

- **Google Drive / OneDrive / Dropbox** — a browser-based OAuth flow opens; sign in and grant read-only access.
- **Nextcloud / ownCloud / MagentaCloud** — enter your server URL and an *app password* (create one in your cloud's security settings to avoid using your main password).
- **Strato HiDrive** — enter your Strato username and password.

### 2 — Index files

After connecting, tap **Indexieren** on the account card. A persistent notification shows progress. The first run downloads and extracts text from every supported file; subsequent runs only fetch changes (delta sync).

> **Tip:** Indexing a large account (10 000+ files) can take several minutes. Keep the screen on or leave the app — the Foreground Service prevents Android from interrupting the process.

### 3 — Search

Return to the main screen and start typing. Results appear after two characters, ranked by relevance, with matched terms highlighted in the snippet.

Tap a result to open the file in the associated cloud app or browser.

---

## Search Results

### Snippets

Matched search terms are shown **bold** in a two-line preview extracted from the file content.

### Duplicate detection

If the same file (matching name *and* size) exists on more than one provider or account, each result shows an additional line:

```
OneDrive
Auch: Dropbox          ← visible on both the OneDrive and the Dropbox result
```

---

## Settings

### Network — mobile data

By default indexing only runs over **Wi-Fi** to avoid unexpected data charges.

Go to **Settings → Indexierung → Mobilfunk erlauben** to allow indexing over mobile networks (5G / LTE). A confirmation dialog reminds you of the potential data usage.

> Changing this setting takes effect the next time you tap **Indexieren** or when a daily sync fires. A running job that was started with the Wi-Fi constraint is not affected retroactively.

### Daily automatic delta sync

Each account card shows a **Tägl. Delta-Sync** toggle after the first full index has completed.

| State | Behaviour |
|---|---|
| Off (default) | Index is only updated when you tap "Neu indexieren" manually |
| On | WorkManager schedules a delta sync every 24 hours |

The periodic job uses the same network constraint as a manual sync (Wi-Fi only, unless mobile data is enabled). Android decides the exact execution time within the 24-hour window based on device state (charging, idle, network).

Disconnecting an account automatically cancels any scheduled daily sync.

---

## Privacy & Security

| Aspect | Detail |
|---|---|
| Index storage | Exclusively in the app's private internal storage — inaccessible to other apps |
| Downloaded files | Deleted immediately after text extraction; never stored permanently |
| OAuth tokens | Stored in Android `EncryptedSharedPreferences` |
| Telemetry | None — no analytics, no crash reporting, no third-party SDKs |
| Cloud sync of index | Not implemented; the index stays on-device |

---

## Architecture

```
┌──────────────────────────────────────────────────────┐
│                    FullTXT App                       │
│                                                      │
│  ┌─────────────────┐   ┌────────────────────────┐   │
│  │   UI Layer      │   │  WorkManager / Jobs    │   │
│  │  (Compose/MVVM) │   │  IndexingWorker        │   │
│  │  SearchScreen   │   │  (Foreground Service,  │   │
│  │  SettingsScreen │   │   Delta + Daily Sync)  │   │
│  └────────┬────────┘   └───────────┬────────────┘   │
│           │                        │                 │
│  ┌────────▼────────────────────────▼────────────┐   │
│  │              Repository Layer                │   │
│  │   SearchRepository   │   IndexRepository    │   │
│  └────────────────┬─────────────────────────────┘   │
│                   │                                  │
│  ┌────────────────▼─────────────────────────────┐   │
│  │          Local Database (Room / FTS5)        │   │
│  │   file_metadata  │  file_content_fts         │   │
│  └──────────────────────────────────────────────┘   │
│                                                      │
│  ┌──────────────────────────────────────────────┐   │
│  │           Cloud Connector Layer              │   │
│  │  Google Drive · OneDrive · Dropbox           │   │
│  │  Nextcloud · ownCloud · MagentaCloud · Strato│   │
│  │  (Rate-limit middleware, OAuth, delta tokens)│   │
│  └──────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────┘
```

### Tech stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Architecture | MVVM + Repository pattern |
| Database | Room (SQLite FTS5) |
| Background work | WorkManager (Foreground Service + PeriodicWork) |
| HTTP client | OkHttp + Retrofit |
| Google auth | Google Identity Services (OAuth 2.0) |
| Microsoft auth | MSAL (Microsoft Authentication Library) |
| Dropbox auth | PKCE OAuth 2.0 |
| PDF parsing | PdfBox-Android |
| Office parsing | Apache POI |
| Dependency injection | Hilt |

### Key design decisions

**FTS5 index** — SQLite FTS5 is built into Android and requires no additional dependencies. The `snippet()` auxiliary function is used to extract highlighted previews of matched terms directly from the database.

**Delta sync** — all connectors implement `getChanges(accountId, changeToken?)`. On first run `changeToken` is null, triggering a full file list. On subsequent runs, only items changed since the last token are returned. Google Drive and OneDrive return actual deletion IDs; WebDAV-based providers fall back to a full re-list with eTag-based deduplication.

**Foreground Service** — `IndexingWorker` calls `setForeground(ForegroundInfo(..., DATA_SYNC))` immediately on start. This prevents Android from killing the worker mid-run (critical for OneDrive accounts with 10 000+ files).

**50 MB file limit** — files exceeding 50 MB are skipped silently. This prevents OOM crashes on devices with limited heap.

**Duplicate detection** — after each search, `FileIndexDao.getByFileNames()` retrieves all indexed entries matching the result file names. Groups with more than one distinct `accountId` and the same `fileSizeBytes` are considered duplicates.

---

## Build Instructions

### Prerequisites

- Android Studio Hedgehog or newer
- JDK 17 (bundled with Android Studio)
- A physical or virtual Android device running API 26 or higher

### Google Drive setup

1. Create a project in [Google Cloud Console](https://console.cloud.google.com/)
2. Enable the **Google Drive API**
3. Create an **OAuth 2.0 client ID** (Android app), enter the package name `me.fulltxt.app` and the SHA-1 fingerprint of your debug keystore
4. Add your test account(s) to the OAuth consent screen → Test users

### OneDrive / MSAL setup

1. Register an application in [Azure Portal](https://portal.azure.com/) → App registrations
2. Add an Android redirect URI: `msauth://me.fulltxt.app/<base64-sha1-of-your-keystore>` (Azure stores it URL-encoded automatically)
3. Copy the **Application (client) ID** into `app/src/main/res/raw/msal_config.json`

### Dropbox setup

1. Create an app in [Dropbox App Console](https://www.dropbox.com/developers/apps)
2. Enable scopes: `files.metadata.read`, `files.content.read`, `account_info.read`
3. Add the redirect URI: `fulltxt://dropbox-auth`
4. Copy the **App key** and **App secret** into the Dropbox auth manager

### Run

```bash
# Install on connected device (Windows)
.\gradlew.bat installDebug

# Install on connected device (macOS / Linux)
./gradlew installDebug
```

> **Note:** If Gradle cannot find the JDK, set `JAVA_HOME` to the JDK bundled with Android Studio (e.g. `$env:JAVA_HOME = "path\to\AndroidStudio\jbr"` on Windows or `export JAVA_HOME="path/to/AndroidStudio/jbr"` on macOS/Linux).

---

## Roadmap

| Status | Feature |
|---|---|
| ✅ | Google Drive, OneDrive, Dropbox, Nextcloud, ownCloud, MagentaCloud, Strato HiDrive |
| ✅ | Local FTS5 full-text index |
| ✅ | PDF, DOCX, XLSX, PPTX support |
| ✅ | Foreground Service (prevents background kill) |
| ✅ | Delta sync (change tokens) |
| ✅ | Daily automatic sync (PeriodicWork) |
| ✅ | Duplicate file detection |
| ✅ | Mobile data toggle (per-device) |
| ✅ | Filter by provider, file type, date |
| 🔜 | Multiple accounts per provider |
| 🔜 | OpenDocument format support (`.odt`, `.ods`, `.odp`) |
| 🔜 | Index export / backup |
| 🔜 | Play Store release |

---

## License

TBD
