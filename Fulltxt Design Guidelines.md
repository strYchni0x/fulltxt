# FullTXT — Design Guidelines

## Markenkern
Ein Fadenkreuz aus Lupe und Cursor — Suche und Text in einem Zeichen. Technisch-präzises, Dark-Mode-Design mit Terminal-/Neon-Akzent.

## Farben
| Rolle | Wert | Verwendung |
|---|---|---|
| Hintergrund (Basis) | `oklch(0.15 0.012 250)` | Seiten-/App-Hintergrund |
| Hintergrund (Karten/Icon-Fläche) | `oklch(0.14 0.01 250)` / `oklch(0.19 0.015 250)` | Cards, Icon-Flächen, Phone-Mock |
| Rahmen/Trenner | `oklch(0.24–0.32 0.02 250)` | Borders, Dividers |
| Text primär | `oklch(0.96 0.005 250)` | Überschriften, Fließtext |
| Text sekundär | `oklch(0.55–0.68 0.01 250)` | Subtext, Labels |
| Akzent (Neon-Grün) | `oklch(0.85 0.19 150)` | CTA, Icon, Highlights, Cursor-Blink |
| Akzent auf Hellgrund | `oklch(0.4 0.14 150)` | Icon-Variante für weiße Flächen |
| Icon-Hintergrund hell | `oklch(0.96 0.005 250)` | Light-Variante App Icon |

Maximal ein Akzentton (Neon-Grün) auf dunklem Grund; kein Verlauf, keine Zweitfarbe.

## Schrift
- **JetBrains Mono** (400/500/600/700) — Wortmarke, Code/Terminal-Elemente, Labels, Eyebrows, Buttons, Suchfeld
- **Manrope** (400–800) — Fließtext, Überschriften (H1/H2), Body

Wortmarke immer in JetBrains Mono, Weight 700, `letter-spacing: -0.02em`, Schreibweise **FullTXT**.

## Logo / App Icon
- Motiv: Kreis (Lupe) + diagonaler Griff + vertikaler Cursor-Strich innerhalb des Kreises, alle in Akzentgrün
- Cursor blinkt (Terminal-Metapher) in Kontext mit Live-UI, statisch in Icon-Exporten
- Icon-Fläche: abgerundetes Quadrat (Legacy, corner-radius ~18% der Breite), Kreis (Circle-Mask), oder transparent (Adaptive Foreground)
- Sicherheitszone Adaptive Icon: Motiv max. ~61% der Canvas-Breite zentriert
- Nie verzerren, nie zweifarbig, nie ohne umgebenden Weißraum in den Icon-Größen

## Layout-Prinzipien
- Max. Content-Breite: `1180px`, zentriert, Seiten-Padding `32px`
- Grid/Flex mit `gap` statt Margins zwischen Geschwister-Elementen
- Nav: Logo links, Links + Sprachumschalter + primärer CTA rechts
- Hero: 2-spaltig (Text ~55% / Produkt-Mock ~45%), Badge → H1 → Subline → CTA-Paar
- Feature-Grid: 3 Spalten, Icon (36×36) → Titel (Bold, 15.5px) → Beschreibung (13.5px, sekundärfarbe)
- Cards: `border-radius: 14px`, 1px Border, kein Schatten
- Buttons primär: Akzentgrün-Fläche, dunkler Text, `border-radius: 7–8px`
- Buttons sekundär: transparent, 1px Border
- Sprache: zweisprachig DE/EN über Umschalter, kein separates Routing nötig

## Asset-Formate (Referenz)
- App Icon Legacy/Play Store: 512×512, corner-radius 92px
- Adaptive Icon: 432×432, Background- + transparenter Foreground-Layer getrennt
- Play Store Feature Graphic: 1024×500
- Wordmark-Banner: 1600×400
- Icon-Densities: 192/144/96/72/48px
