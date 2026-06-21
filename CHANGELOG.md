# Changelog

Alle nennenswerten Änderungen an FullTXT werden in dieser Datei dokumentiert.

Das Format orientiert sich an [Keep a Changelog](https://keepachangelog.com/de/1.1.0/),
und das Projekt folgt [Semantic Versioning](https://semver.org/lang/de/).

## [1.3.0] – 2026-06-21

### Sicherheit
- **Der Suchindex auf dem Gerät wird jetzt verschlüsselt gespeichert (SQLCipher, AES-256).** Die Index-Datenbank enthält den extrahierten Klartext aller indexierten Dokumente; sie ist nun auch bei Root-Zugriff oder forensischem Auslesen des Geräts nicht mehr lesbar. Der Schlüssel wird einmalig zufällig erzeugt und im hardware-gestützten Android Keystore (via `EncryptedSharedPreferences`) verwahrt – er verlässt das Gerät nie.
  - **Hinweis:** Ein bereits vorhandener (unverschlüsselter) Index wird beim ersten Start dieser Version verworfen; die verbundenen Konten müssen **einmal neu indexiert** werden.
- **Index-Backups werden jetzt verschlüsselt.** Beim Export wird ein Passwort abgefragt; die Backup-Datei (`.ftxt`) wird damit per AES-256-GCM verschlüsselt (Schlüsselableitung via PBKDF2). Der exportierte Index ist damit auch außerhalb der App (Downloads-Ordner, Cloud) nicht mehr lesbar. Zum Import wird dasselbe Passwort benötigt. Ein falsches Passwort lässt den vorhandenen Index unangetastet, da zunächst in eine temporäre Datei entschlüsselt wird. Backups bleiben geräteübergreifend wiederherstellbar (eigenes Passwort statt des geräte­gebundenen Index-Schlüssels).
- **Hinweis:** Unverschlüsselte `.db`-Backups früherer Versionen können nicht mehr importiert werden.

### Build
- SQLCipher nutzt jetzt das Artefakt `net.zetetic:sqlcipher-android` (4.6.1) mit **16-KB-Page-Size-Unterstützung**, wie von Google Play für neue Apps auf Android 15+ gefordert. Die nativen Bibliotheken sind auf 16 KB ausgerichtet (verifiziert: alle `LOAD`-Segmente `p_align = 0x4000`).

## [1.2.9] – 2026-06-20

### Build
- Release-Bundles betten jetzt native Debug-Symbole ein (`ndk.debugSymbolLevel = "FULL"`), damit die Play Console Abstürze/ANRs aus nativem Code symbolisieren kann. Hinweis: Der enthaltene native Code stammt ausschließlich aus vorgefertigten, bereits gestrippten Drittanbieter-Bibliotheken (ML Kit OCR, androidx.graphics.path); für diese liegen keine Symboltabellen vor, sodass Google weiterhin die Symbol-Warnung anzeigt. Die Einstellung greift automatisch, falls künftig eigener nativer Code hinzukommt.

## [1.2.8] – 2026-06-14

### Berechtigungen
- Die Berechtigung `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` wurde entfernt (von Google Play nur für eng definierte App-Kategorien erlaubt). Der Akku-Optimierungs-Dialog öffnet jetzt die System-Einstellungsseite, in der FullTXT ausgenommen werden kann.

## [1.2.7] – 2026-06-11

### Stabilität
- Behebt einen Absturz (Out-of-Memory) beim Indexieren großer Dateien: Die App nutzt jetzt einen größeren Arbeitsspeicher-Heap, sodass die Indexierung großer Cloud-Bestände nicht mehr abbricht.

## [1.2.6] – 2026-06-11

### Indexierung
- Fehlgeschlagene Indexierungen zeigen jetzt eine **Klartext-Fehlermeldung** auf der Konto-Karte (z. B. falsche Anmeldedaten oder Server-URL) statt still abzubrechen.
- Bei **Anmeldefehlern** wird nicht mehr endlos wiederholt – das verhindert eine Dauerschleife aus Fehlversuchen.

## [1.2.5] – 2026-06-11

### Indexierung
- Texterkennung (OCR) läuft jetzt als eigener, fortsetzbarer Hintergrund-Lauf. Die normale Indexierung bleibt dadurch schnell und bricht nicht mehr ab; gescannte PDFs werden anschließend nach und nach nachgezogen – auch wenn das System die Verarbeitung zwischendurch unterbricht.

## [1.2.4] – 2026-06-10

### Indexierung
- Optionale Texterkennung (OCR) für gescannte PDFs – vollständig offline (ML Kit), standardmäßig deaktiviert. Einschaltbar unter Einstellungen → Indexierung; greift nur bei PDFs ohne Textebene und ist deutlich langsamer und akkuintensiver.

## [1.2.3] – 2026-06-09

### Preismodell
- Alle Funktionen sind jetzt kostenlos. Das frühere FullTXT Pro (Einmalkauf) und sämtliche In-App-Käufe wurden entfernt.
- OneDrive und Dropbox lassen sich ohne Freischaltung verbinden.

## [1.2.2] – 2026-06-01

### Suche
- Einstellbare maximale Trefferanzahl (50 / 100 / 200 / 500) unter Einstellungen → Suche; die Suche wird bei Änderung automatisch neu ausgeführt.
- Treffer-Vorschau (Snippet) wird auf die Fundstelle zentriert, damit der hervorgehobene Begriff in den sichtbaren Zeilen bleibt.
- Der nichtssagende Google-Drive-Pfad (interne Ordner-IDs) wird in den Ergebnissen nicht mehr angezeigt.

## [1.2.1] – 2026-06-01

### Suche
- Farbige Dateityp-Symbole in den Suchergebnissen (PDF, Word, Excel, PowerPoint, Text) – abschaltbar unter Einstellungen → Suche.
- Anzeige der Trefferanzahl über der Ergebnisliste.

## [1.2.0] – 2026-05-31

### Neuer Cloud-Anbieter
- Yandex Disk (WebDAV) – Verbindung mit Benutzername + Passwort (App-Passwort bei aktivierter Zwei-Faktor-Authentifizierung).

### Darstellung
- Heller/dunkler Design-Umschalter: System, Hell oder Dunkel (Einstellungen → Darstellung).
- Wirkt sofort app-weit; auf Android 12+ zusätzlich Material You (Dynamic Color).
- Behebt das helle Aufblitzen beim App-Start im Dunkelmodus.

### Suche
- Anzahl der gespeicherten letzten Suchanfragen konfigurierbar (0–10, Standard 5).

### Einstellungen
- Cloud-Speicher & Konten auf eine eigene Seite ausgelagert → kompaktere Hauptseite.

## [1.1.6] – 2026-05-30

### Index-Backup
- Suchindex exportieren und importieren (Einstellungen → Speicher).
- Export als `.db`-Datei mit automatischem Dateinamen (`fulltxt_backup_YYYY-MM-DD.db`).
- Validierung der Backup-Datei vor dem Import.
- App startet nach erfolgreichem Import automatisch neu.
- Zuverlässige WAL-Behandlung: alle Daten werden vollständig gesichert.

## [1.1.5] – 2026-05-30

### App-Icon
- Finales App-Icon: Dokumente, Wolke und Lupe.
- Adaptive Icon mit Foreground/Background-Ebenen.
- Alle Mipmap-Dichten (mdpi bis xxxhdpi).

## [1.1.4] – 2026-05-30

### App-Icon
- Neues App-Icon: grüne Lupe auf dunklem Hintergrund (Interim-Design).

### OpenDocument-Formate
- Unterstützung für `.odt`, `.ods`, `.odp` (kein zusätzliches Dependency).

### Website
- Homepage unter fulltxt.me live.
- Datenschutzerklärung und Nutzungsbedingungen (DE + EN).

## [1.1.3] – 2026-05-30

### OpenDocument-Formate
- Unterstützung für `.odt` (Writer), `.ods` (Calc) und `.odp` (Impress).
- Kein zusätzliches Library-Dependency – integrierter ZIP/XML-Parser.

### AAB-Build
- Android App Bundle für den Play Store vorbereitet.

## [1.1.2] – 2026-05-29

### Lokale Ordner indexieren
- Dateien auf dem Gerät können nun indexiert werden (via Storage Access Framework).
- Rekursive Indexierung von Unterordnern.
- Delta-Sync: nur geänderte Dateien werden beim nächsten Durchlauf neu indexiert.
- Lokale Ordner erscheinen als eigene Account-Karte in den Einstellungen.

### Speicherinfo
- Index-Datenbankgröße und Gesamtanzahl indexierter Dateien in den Einstellungen sichtbar.

## [1.1.1] – 2026-05-29

- Index-Datenbankgröße und Anzahl indexierter Dateien in den Einstellungen anzeigen (Sektion Speicher).
- Akku-Optimierungs-Hinweis beim ersten App-Start.

## [1.1.0] – 2026-05-29

### Suchfilter
- Filter-Icon neben dem Suchfeld öffnet ein Bottom Sheet.
- **Dateityp**: PDF, Word, Excel, PowerPoint, Text (Mehrfachauswahl).
- **Geändert**: Letzte 7 Tage / Letzter Monat / Letztes Jahr.
- **Cloud-Anbieter**: wird angezeigt, wenn mehr als ein Anbieter verknüpft ist.
- Aktive Filter erscheinen als Chips unter dem Suchfeld und sind einzeln entfernbar.

### Suchverlauf
- Die letzten 3 Suchbegriffe werden gespeichert.
- Erscheinen als Vorschläge, wenn das Suchfeld leer ist.

### Sonstiges
- Hinweis beim ersten Start zur Akku-Optimierungs-Ausnahme.

[1.2.2]: https://github.com/strYchni0x/fulltxt/releases/tag/v1.2.2
[1.2.1]: https://github.com/strYchni0x/fulltxt/releases/tag/v1.2.1
[1.2.0]: https://github.com/strYchni0x/fulltxt/releases/tag/v1.2.0
[1.1.6]: https://github.com/strYchni0x/fulltxt/releases/tag/v1.1.6
[1.1.5]: https://github.com/strYchni0x/fulltxt/releases/tag/v1.1.5
[1.1.4]: https://github.com/strYchni0x/fulltxt/releases/tag/v1.1.4
[1.1.3]: https://github.com/strYchni0x/fulltxt/releases/tag/v1.1.3
[1.1.2]: https://github.com/strYchni0x/fulltxt/releases/tag/v1.1.2
[1.1.1]: https://github.com/strYchni0x/fulltxt/releases/tag/v1.1.1
[1.1.0]: https://github.com/strYchni0x/fulltxt/releases/tag/v1.1.0
