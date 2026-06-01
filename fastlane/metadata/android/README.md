# Play-Store-Metadaten

Texte für den Google-Play-Eintrag, in der Verzeichnisstruktur von
[Fastlane `supply`](https://docs.fastlane.tools/actions/supply/). Auch ohne
Fastlane direkt zum Copy-&-Paste in die Play Console nutzbar.

## Struktur

```
fastlane/metadata/android/<locale>/
  title.txt              App-Name            (max. 30 Zeichen)
  short_description.txt  Kurzbeschreibung    (max. 80 Zeichen)
  full_description.txt   Beschreibung        (max. 4000 Zeichen)
  changelogs/<vc>.txt    Was ist neu         (max. 500 Zeichen, <vc> = versionCode)
```

Aktuelle Sprachen: `de-DE`, `en-US`. Changelog liegt unter `changelogs/12.txt`
(versionCode 12 = App-Version 1.2.2). Für jedes neue Release eine neue Datei
`changelogs/<versionCode>.txt` anlegen.

## Noch manuell beizusteuern (Grafik-Assets, nicht im Repo)

In der Play Console unter „Store-Eintrag":

- [ ] **App-Icon** 512×512 PNG (32-bit) — Quelle: `app/.../mipmap` / `docs/assets/icon-512.png`
- [ ] **Feature-Grafik** 1024×500 PNG/JPG (Pflicht)
- [ ] **Phone-Screenshots** mind. 2, 16:9 oder 9:16, 320–3840 px Kante
- [ ] (optional) **7"- und 10"-Tablet-Screenshots**

## Weitere Pflichtangaben (Formulare, kein Text hier)

- [ ] Datenschutz-URL: https://fulltxt.me/privacy.html
- [ ] Data-Safety-Formular (keine Datenerhebung/-weitergabe, kein Tracking)
- [ ] Content-Rating-Fragebogen
- [ ] Zielgruppe & Inhalte, Werbung: nein
- [ ] App-Zugriff: Testzugangsdaten für die Cloud-Anmeldung bereitstellen
- [ ] In-App-Produkt „FullTXT Pro" anlegen und aktivieren

## Upload-Artefakt

Das AAB wird mit `./gradlew bundleRelease` erzeugt:
`app/build/outputs/bundle/release/app-release.aab`
