# Google-Picker-Setup (playstore-Edition, drive.file)

Die öffentliche `playstore`-Variante nutzt den Scope `drive.file`. Damit sieht die App nur
Dateien, die der Nutzer über den **Google Picker** freigibt. Der Picker ist auf fulltxt.me
gehostet (`docs/drive-picker.html`) und gibt die Auswahl per Deep-Link
`fulltxt://drive-picker` an die App zurück.

Damit der Flow live funktioniert, sind einmalig diese Schritte in **Google Cloud Projekt A**
nötig (das Projekt der öffentlichen App; das `dev`-Projekt mit `drive.readonly` bleibt getrennt):

## 1. APIs aktivieren
- **Google Drive API**
- **Google Picker API**

## 2. OAuth-Client (Android)
- OAuth-Client-ID Typ **Android** anlegen:
  - Paketname: `me.fulltxt.app`
  - SHA-1: Fingerprint des **Play-App-Signing-Schlüssels** (Play Console → App-Integrität).
  - Für lokale Tests zusätzlich den Debug-/Upload-SHA-1 eintragen.
- OAuth-Consent-Screen: Scope `…/auth/drive.file` (non-sensitive, keine CASA/Verifizierung).

## 3. Browser-API-Key (für den Picker)
- API-Key erstellen, **Picker API** erlauben.
- Auf **HTTP-Referrer** `https://fulltxt.me/*` einschränken.

## 4. Werte eintragen
`app/src/playstore/res/values/drive.xml` ist **gitignored** (enthält den API-Key, Repo ist
öffentlich). Lokal aus der Vorlage anlegen:
1. `app/src/playstore/drive.xml.example` → nach `app/src/playstore/res/values/drive.xml` kopieren.
2. Platzhalter ersetzen:
   - `picker_app_id`  → **Projektnummer** (= Zahl vor dem Bindestrich der OAuth-Client-ID).
   - `picker_api_key` → der Browser-API-Key aus Schritt 3.
   - `picker_base_url` → bleibt `https://fulltxt.me/drive-picker.html`.

Ohne diese Datei baut der `playstore`-Flavor nicht (analog zu `keystore.properties`). Der
`dev`-Flavor ist davon unberührt.

## 5. Hosting
`docs/drive-picker.html` wird über dieselbe GitHub-Pages-Quelle wie `docs/index.html` unter
`https://fulltxt.me/drive-picker.html` ausgeliefert. Sicherstellen, dass die Seite erreichbar ist.

## Verifikation
- `.\gradlew assemblePlaystoreDebug` und auf einem Gerät mit eingetragenem Test-Google-Konto:
  Einstellungen → Cloud-Speicher & Konten → Google Drive verbinden → Picker öffnet sich auf
  fulltxt.me → Ordner/Dateien wählen → App indexiert nur die Auswahl.
- Deep-Link-Smoke-Test ohne Picker:
  `adb shell am start -a android.intent.action.VIEW -d "fulltxt://drive-picker?folders=ID1&files=ID2"`
  → `DrivePickerCallbackActivity` parst die IDs (Logtag `DrivePicker`).

Die `dev`-Variante (`me.fulltxt.app.dev`, `drive.readonly`) ist davon unberührt und listet
weiterhin den gesamten Drive.
