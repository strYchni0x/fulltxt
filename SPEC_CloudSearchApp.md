# CloudSearch Android App – Projektspezifikation

**Version:** 0.1 (Initial)
**Stand:** Mai 2026
**Status:** In Planung

---

## 1. Projektüberblick

Eine Android-App, die es ermöglicht, Dateien in verknüpften Cloud-Accounts zu durchsuchen. Die Suche basiert auf einem lokal gespeicherten Volltextindex. Alle erhobenen Daten verbleiben ausschließlich auf dem Gerät des Nutzers.

---

## 2. Ziele & Nicht-Ziele

### Ziele
- Volltext- und Metadatensuche über mehrere Cloud-Anbieter hinweg
- Lokale Indexierung für schnelle, offline-fähige Suche
- Datenschutzkonformer Betrieb: keinerlei Datenübertragung an Dritte
- API-Limits der Cloud-Anbieter werden zu jederzeit eingehalten
- Dateien sollen geöffnet werden können (Verlinkung zur jeweiligen Cloud App)
- Duplikate sollen erkannt werden

### Nicht-Ziele (MVP)
- Keine Datei-Verwaltungsfunktionen (Upload, Umbenennen, Löschen)
- Kein OCR für gescannte Dokumente
- Kein Cloud-Sync des Index
- Keine Web- oder Desktop-Variante

---

## 3. Unterstützte Cloud-Anbieter

### MVP (Version 1.0)
| Anbieter | Protokoll / API | Authentifizierung |
|---|---|---|
| Google Drive | Google Drive API v3 | OAuth 2.0 |
| Microsoft OneDrive | Microsoft Graph API | OAuth 2.0 (MSAL) |

### Geplant (Post-MVP)
- Nextcloud (WebDAV + Nextcloud API)
- Strato HiDrive (REST API / WebDAV)
- Weitere WebDAV-kompatible Dienste

---

## 4. Unterstützte Dateiformate

### MVP (Version 1.0)
| Format | Erweiterungen | Bibliothek |
|---|---|---|
| Nur-Text | `.txt`, `.md`, `.csv`, `.log` | Keine (direkt lesbar) |
| PDF (durchsuchbar) | `.pdf` | PdfBox-Android |
| Word-Dokumente | `.docx` | Apache POI |
| Excel-Tabellen | `.xlsx` | Apache POI |
| PowerPoint | `.pptx` | Apache POI |

> **Hinweis:** OCR für gescannte PDFs ist explizit **nicht** Teil des MVP. Nur digital erstellte, durchsuchbare PDFs werden unterstützt.

### Geplant (Post-MVP)
- OpenDocument-Formate (`.odt`, `.ods`, `.odp`)
- E-Books (`.epub`)

---

## 5. Indexierung

### Strategie
- Dateien werden **temporär** in den App-internen Cache-Ordner (`getCacheDir()`) heruntergeladen
- Nach erfolgter Textextraktion wird die heruntergeladene temporäre Datei **sofort und vollständig** vom Gerät (NICHT aus der Cloud) gelöscht
- Die Dateien werden zu keinem Zeitpunkt dauerhaft gespeichert

### Gespeicherte Index-Daten (lokal)
Für jede indexierte Datei werden folgende Daten im lokalen Index abgelegt:

**Metadaten:**
- Dateiname
- Dateipfad in der Cloud
- Cloud-Anbieter / Account-ID
- Dateigröße
- Erstelldatum / letztes Änderungsdatum
- MIME-Type
- Cloud-eigene File-ID

**Inhalt:**
- Vollständiger extrahierter Volltext

### Index-Technologie
- **SQLite FTS5** (Full-Text Search, in Android integriert)
- Kein externer Datenbankserver erforderlich
- Index-Datei verbleibt ausschließlich im privaten App-Speicher

### Erstindexierung
- Läuft als Hintergrunddienst (WorkManager)
- Wird bevorzugt ausgeführt: WLAN-Verbindung aktiv, Gerät wird geladen, optional deaktivierbar
- Fortschritt wird dem Nutzer angezeigt
- Kann pausiert und fortgesetzt werden

### Delta-Synchronisation
- Regelmäßiger Abgleich auf Änderungen in der Cloud
- Google Drive & OneDrive: Nutzung der Change-Token / Delta-API
- Nur geänderte oder neue Dateien werden neu heruntergeladen und indexiert

---

## 6. API-Nutzung & Rate Limits

Die App hält die Rate Limits der jeweiligen Cloud-Anbieter **strikt ein**, auch wenn dies zu einer langsameren Indexierung führt.

| Anbieter | Bekanntes Limit | Strategie |
|---|---|---|
| Google Drive API | 1.000 Anfragen / 100 Sek. (pro Nutzer) | Request-Queue + Exponential Backoff bei HTTP 429 |
| Microsoft Graph API | 10.000 Anfragen / 10 Min. | Request-Queue + Exponential Backoff bei HTTP 429 |

- Alle API-Aufrufe laufen über eine zentrale **Rate-Limit-Middleware**
- Bei Überschreitung wird automatisch gewartet (kein Abbruch)

---

## 7. Datenschutz & Sicherheit

- Alle Index-Daten werden **ausschließlich lokal** auf dem Gerät gespeichert
- Der App-interne Speicher ist für andere Apps nicht zugänglich (Android-Sandbox)
- Temporär heruntergeladene Dateien werden nach der Indexierung **sofort gelöscht**
- OAuth-Token werden sicher im Android `EncryptedSharedPreferences` gespeichert
- Es findet **keine Telemetrie, kein Tracking und keine Datenübertragung** an Dritte statt
- Der Index wird im Standard nicht in der Cloud gesichert. Ein Backup soll aber möglich sein.

---

## 8. Architektur (Übersicht)

```
┌─────────────────────────────────────────────────────┐
│                    Android App                      │
│                                                     │
│  ┌─────────────┐    ┌──────────────────────────┐   │
│  │  UI Layer   │    │    WorkManager / Jobs    │   │
│  │  (Compose)  │    │   (Hintergrundindex)     │   │
│  └──────┬──────┘    └───────────┬──────────────┘   │
│         │                       │                   │
│  ┌──────▼───────────────────────▼──────────────┐   │
│  │              Repository Layer               │   │
│  │  SearchRepository │ IndexRepository         │   │
│  └──────────────┬──────────────────────────────┘   │
│                 │                                   │
│  ┌──────────────▼──────────────────────────────┐   │
│  │            Lokale Datenhaltung              │   │
│  │         SQLite (FTS5) via Room              │   │
│  └─────────────────────────────────────────────┘   │
│                                                     │
│  ┌──────────────────────────────────────────────┐  │
│  │           Cloud Connector Layer             │  │
│  │  GoogleDriveConnector │ OneDriveConnector   │  │
│  │  (Rate Limit Middleware, OAuth, Delta Sync) │  │
│  └──────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
         │                        │
         ▼                        ▼
   Google Drive API       Microsoft Graph API
```

### Technologie-Stack
| Bereich | Technologie |
|---|---|
| Sprache | Kotlin |
| UI | Jetpack Compose |
| Architektur | MVVM + Repository Pattern |
| Datenbank | Room (SQLite FTS5) |
| Hintergrundarbeit | WorkManager |
| HTTP | OkHttp + Retrofit |
| Google Auth | Google Identity Services (OAuth 2.0) |
| Microsoft Auth | MSAL (Microsoft Authentication Library) |
| PDF-Parsing | PdfBox-Android |
| Office-Parsing | Apache POI |
| Dependency Injection | Hilt |

---

## 9. Geplante Features (Roadmap)

### Version 1.0 (MVP)
- [x] Google Drive Integration
- [x] OneDrive Integration
- [x] Lokale SQLite FTS5 Indexierung
- [x] Volltext + Metadaten im Index
- [x] Temporäres Herunterladen, sofortiges Löschen
- [x] Durchsuchbare PDF-Unterstützung
- [x] Office-Dokumente (docx, xlsx, pptx)
- [x] Erstindexierung im Hintergrund (WorkManager)
- [x] Rate-Limit-konforme API-Nutzung
- [x] Einfache Suchoberfläche

### Version 1.x
- [ ] Delta-Synchronisation (Änderungen in der Cloud erkennen)
- [ ] Filterung nach Anbieter, Dateityp, Datum
- [ ] Suchergebnisse mit Textvorschau und Hervorhebung
- [ ] Mehrere Accounts pro Anbieter

### Version 2.0
- [ ] Nextcloud / WebDAV-Unterstützung
- [ ] Strato HiDrive
- [ ] OpenDocument-Formate
- [ ] Indexierungsstatistiken

---

## 10. Offene Fragen

| # | Frage | Status |
|---|---|---|
| 1 | App-Name? | Offen |
| 2 | Mindest-Android-Version (API Level)? | Offen |
| 3 | Soll der Index exportierbar/sicherbar sein? | Offen |
| 4 | Mehrsprachigkeit (DE/EN)? | Offen |
| 5 | Soll die App im Play Store veröffentlicht werden? | Offen |

---

*Dieses Dokument wird während der Entwicklung kontinuierlich aktualisiert.*
