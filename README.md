# FULLTXT

Android app for full-text search across connected cloud storage accounts. The search index is stored locally on the device — no data ever leaves it.

## Features

- Full-text and metadata search across multiple cloud providers
- Local SQLite FTS5 index for fast, offline-capable search
- Open files directly in the respective cloud app
- Duplicate detection
- Privacy by design: no telemetry, no tracking, no third-party data transfer

## Supported Cloud Providers

| Provider | API | Auth |
|---|---|---|
| Google Drive | Drive API v3 | OAuth 2.0 |
| Microsoft OneDrive | Microsoft Graph API | OAuth 2.0 (MSAL) |

Nextcloud, Strato HiDrive and generic WebDAV planned for v2.0.

## Supported File Formats

| Format | Extensions |
|---|---|
| Plain text | `.txt` `.md` `.csv` `.log` |
| PDF (searchable) | `.pdf` |
| Word | `.docx` |
| Excel | `.xlsx` |
| PowerPoint | `.pptx` |

> OCR for scanned documents is not supported.

## Architecture

```
UI Layer (Jetpack Compose)
        │
Repository Layer (SearchRepository, IndexRepository)
        │
Local: SQLite FTS5 via Room       Cloud: GoogleDriveConnector, OneDriveConnector
                                         (Rate Limit Middleware, OAuth, Delta Sync)
```

**Stack:** Kotlin · Jetpack Compose · MVVM · Room · WorkManager · Hilt · OkHttp · Retrofit

## Privacy

- All index data is stored exclusively in the app's private local storage
- Downloaded files are deleted immediately after text extraction
- OAuth tokens are stored in Android `EncryptedSharedPreferences`
- No cloud sync of the index (manual export/backup planned)

## Project Status

Version 1.0 (MVP) — in development.

## Setup

1. Clone the repository
2. Open the project in Android Studio
3. Create a Google Cloud project, enable the Drive API, and add the OAuth 2.0 client ID
4. Register an app in Azure Portal for MSAL and add `app/src/main/res/raw/msal_config.json`
5. Build and run on a device or emulator (API 26+)

## License

TBD
