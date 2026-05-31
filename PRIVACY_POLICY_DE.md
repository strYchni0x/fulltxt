# Datenschutzerklärung – FULLTXT

**Stand:** Mai 2026  
**Verantwortlicher:** Florian Willnat · florian@willnat.org

---

## 1. Überblick

FULLTXT ist eine Android-App zur Volltext-Suche in Cloud-Speicherdiensten. Diese Datenschutzerklärung erläutert, welche Daten die App verarbeitet, wie sie gespeichert werden und welche Rechte dir als Nutzer zustehen.

**Kernprinzip:** FULLTXT verarbeitet alle Daten ausschließlich lokal auf deinem Gerät. Es gibt keinen eigenen Backend-Server; es werden keine Nutzungsdaten, Analysedaten oder Dateiinhalte an den App-Entwickler übertragen.

---

## 2. Verarbeitete Daten

### 2.1 Kontodaten (Cloud-Anbieter)

Beim Verbinden eines Cloud-Accounts werden folgende Informationen lokal gespeichert:

| Datum | Zweck | Speicherort |
|-------|-------|-------------|
| E-Mail-Adresse / Benutzername | Anzeige in der App, eindeutige Kontokennung | Verschlüsselte Gerätedatenbank |
| OAuth-Token / App-Passwort | Zugriff auf die Cloud-API des jeweiligen Anbieters | Android `EncryptedSharedPreferences` (AES-256) |
| Anzeigename | Anzeige in der App | Verschlüsselte Gerätedatenbank |

Unterstützte Anbieter: Google Drive, Microsoft OneDrive, Nextcloud, ownCloud, Dropbox, MagentaCloud, Strato HiDrive, Yandex Disk.

### 2.2 Datei-Index

Zur Volltextsuche werden von deinen Cloud-Dateien folgende Metadaten lokal indexiert:

- Dateiname, Dateigröße, Änderungsdatum, Dateipfad
- Textinhalt der Datei (für Office-Dokumente und PDFs)

Der Index wird in einer SQLite-Datenbank auf dem Gerät gespeichert und **verlässt das Gerät nicht**.

### 2.3 Temporäre Dateien

Zur Textextraktion werden Dateien vorübergehend in den internen Cache des Geräts heruntergeladen (`cacheDir`). Die temporären Dateien werden unmittelbar nach der Textextraktion gelöscht.

### 2.4 Benachrichtigungen

Die App zeigt Fortschrittsbenachrichtigungen während laufender Indexierungsvorgänge an. Dafür wird die Android-Berechtigung `POST_NOTIFICATIONS` benötigt (Android 13+).

---

## 3. Datenübertragung an Dritte

FULLTXT selbst übermittelt keine Daten an externe Server. Beim Indexieren kommuniziert die App direkt (ohne Umweg über einen eigenen Server) mit den APIs der jeweiligen Cloud-Anbieter. Es gelten dabei deren Datenschutzbestimmungen:

- **Google Drive:** [policies.google.com/privacy](https://policies.google.com/privacy)
- **Microsoft OneDrive:** [privacy.microsoft.com](https://privacy.microsoft.com/de-de/privacystatement)
- **Dropbox:** [www.dropbox.com/privacy](https://www.dropbox.com/privacy)
- **Yandex Disk:** [yandex.com/legal/confidential](https://yandex.com/legal/confidential)
- **Nextcloud / ownCloud / MagentaCloud / Strato HiDrive:** Richtlinien des jeweiligen Server-Betreibers

---

## 4. Berechtigungen

| Berechtigung | Zweck |
|---|---|
| `INTERNET` | Verbindung zu Cloud-APIs |
| `ACCESS_NETWORK_STATE` | Prüfung des Netzwerktyps (WLAN vs. Mobilfunk) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | Indexierung im Vordergrund-Dienst (Datei-Download) |
| `POST_NOTIFICATIONS` | Fortschrittsanzeige während der Indexierung |

Die App fordert **keine** Berechtigung für den Zugriff auf lokale Gerätedateien, den Standort, die Kamera oder Kontakte.

---

## 5. Datenspeicherung und Löschung

Alle von FULLTXT gespeicherten Daten befinden sich ausschließlich auf dem Gerät:

- **Löschen eines einzelnen Accounts:** Tippe in Einstellungen auf „Trennen". Alle Indexdaten, Zugangsdaten und OAuth-Token des Accounts werden sofort und vollständig gelöscht.
- **Deinstallation der App:** Android löscht automatisch alle App-Daten (Datenbank, SharedPreferences, Cache), sobald die App deinstalliert wird.
- **Datensicherung:** Die App ist mit `android:allowBackup="false"` konfiguriert. App-Daten werden nicht über die Android-Backup-Funktion in die Cloud gesichert.

---

## 6. Datensicherheit

- OAuth-Token und Passwörter werden mit `EncryptedSharedPreferences` (AES-256-GCM, Android Keystore) gespeichert.
- Die Gerätedatenbank enthält keine Klartext-Passwörter.
- Netzwerkverbindungen in Release-Builds sind auf HTTPS beschränkt (Network Security Config).
- HTTP-Debug-Logging ist ausschließlich in Debug-Builds aktiv.

---

## 7. Rechtsgrundlage (DSGVO)

Die Verarbeitung erfolgt auf Grundlage von **Art. 6 Abs. 1 lit. b DSGVO** (Vertragserfüllung / Nutzung der App-Funktionen). Du erteilst die Einwilligung implizit durch das Verbinden eines Cloud-Accounts. Du kannst die Verarbeitung jederzeit durch Trennen des Accounts oder Deinstallieren der App beenden.

---

## 8. Deine Rechte

Als betroffene Person hast du folgende Rechte:

- **Auskunft** (Art. 15 DSGVO): Welche Daten sind gespeichert? → Alle Daten befinden sich lokal auf deinem Gerät und sind in der App einsehbar.
- **Berichtigung** (Art. 16 DSGVO): Nicht zutreffend — die App speichert nur automatisch aus der Cloud abgeleitete Daten.
- **Löschung** (Art. 17 DSGVO): Trenne den Account oder deinstalliere die App.
- **Datenübertragbarkeit** (Art. 20 DSGVO): Daten liegen lokal; ein Export ist über Android-Dateimanager möglich.
- **Beschwerde** (Art. 77 DSGVO): Du hast das Recht, dich bei einer Datenschutzaufsichtsbehörde zu beschweren.

---

## 9. Kinder

Die App richtet sich nicht an Kinder unter 16 Jahren. Es werden wissentlich keine Daten von Minderjährigen verarbeitet.

---

## 10. Änderungen dieser Erklärung

Bei wesentlichen Änderungen wird die Versionsnummer der App erhöht und im Changelog dokumentiert. Die aktuelle Version ist stets in der App-Repository unter `PRIVACY_POLICY_DE.md` verfügbar.

---

## 11. Kontakt

Bei Fragen zum Datenschutz:

**Florian Willnat**  
E-Mail: florian@willnat.org
