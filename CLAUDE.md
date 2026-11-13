# CLAUDE.md – Raster

Anleitung für Claude Code (und andere), die an diesem Projekt weiterarbeiten.

## Worum es geht

**Raster** ist ein UI-Design- und Prototyping-Programm für **Android-Tablets mit Stift**, angelehnt an Adobe XD. Besitzer ist David (GitHub `davscha`). Zielgerät ist sein **Samsung Galaxy Tab S9+** mit S Pen. Das Handy ist ausdrücklich nicht das Ziel.

Raster gehört zur Kreativ-Suite:
- **Lasur** (`davscha/Lasur-Android-App-Claude`): Photoshop-Klon, Pixel
- **Kontur** (`davscha/Vec-Ill-apk-app`): Illustrator-Klon, Vektor
- **Raster** (dieses Repo): XD-Klon, Screens, UI-Bausteine, Komponenten, Prototyp
- Später: ein Animate/Flash-Nachfolger

Der Code ist **neu geschrieben** (Davids Wunsch), Aussehen und Bedienung sind aber von Lasur und Kontur übernommen: Farben, Strich-Icons, Studio-Layout, Stift zeichnet, Finger navigiert. Neue UI soll dazu passen. Im Zweifel nachsehen, wie Kontur es löst.

## Repository

- GitHub: `davscha/raster-xd-clone` (privat), Standard-Branch `main`
- Paket und Application-ID: `de.davscha.raster`
- **Neueste APK (fester Link):** https://github.com/davscha/raster-xd-clone/releases/latest/download/Raster.apk
  - `.github/workflows/build.yml` baut bei jedem Push eine Debug-APK. Auf `main` entsteht zusätzlich ein Release `build-<Nummer>` mit `Raster.apk`, markiert als „latest“.
  - **David möchte nach jedem Build diesen direkten Download-Link bekommen.**

## Bauen und Testen

- Android Studio oder `./gradlew assembleDebug`. Gradle 8.11.1, AGP 8.7.3, Kotlin 2.0.21, JDK 17, `minSdk 29`, `targetSdk 35`, Compose BOM 2024.12.01, Material 3. Keine weiteren Abhängigkeiten.
- **Emulator:** Tablet-Profil im Querformat (z. B. Pixel Tablet). Ohne Stift zeichnet die Maus nur mit **Einstellungen → „Auch mit dem Finger zeichnen“**. Zwei-Finger-Zoom mit Strg + Maus.
- Keine Unit- oder UI-Tests. Bisher nur über GitHub Actions kompiliert.

## Arbeitsweise

- **Alles auf Deutsch:** UI-Texte, Kommentare, Commit-Nachrichten. Bezeichner Englisch.
- **YAGNI, kleine Schritte.** Kein Overengineering.
- David schreibt oft auf dem Tablet (Tippfehler überlesen) und meldet Bugs per Bildschirmaufnahme. Unklar? Nachfragen statt raten.
- Er kommt aus der Webentwicklung (JS/PHP) und Python, Kotlin ist neuer für ihn. Erklärungen in Worten, ohne Formeln.
- Projektformat: lesbares, verschachteltes JSON (mag er).
- Commits enden mit dem Co-Authored-By-Trailer der jeweiligen Sitzung.

## Aufbau (`app/src/main/java/de/davscha/raster/`)

| Datei | Inhalt |
|---|---|
| `EditorState.kt` | Herzstück: Werkzeuge, Modus (Design/Prototyp), Auswahl, Treffertests, Verschieben/Skalieren mit Einrasten, Zeichnen, Bausteine einfügen, Bearbeiten, Screens, Komponenten, Verbindungen, Rückgängig, Dateien |
| `model/Model.kt` | `Document` (Screens, Komponenten, Start-Screen), `Board` (Screen oder Komponente), `Element` (alle Arten in einer Klasse), `Link`, `Transition`, `ScreenPreset` |
| `model/Widgets.kt` | Startwerte für jeden Baustein |
| `render/Renderer.kt` | Zeichnet alles für Arbeitsfläche, Vorschau und Export; Textumbruch mit `StaticLayout` (zwischengespeichert) |
| `render/ViewState.kt` | Zoom und Verschieben der Ansicht |
| `io/RasterJson.kt` | Projektformat `.raster` (Version 1) |
| `io/ProjectStore.kt` | Speichern/Öffnen über die Dateiauswahl, Autosave `filesDir/autosave.raster` |
| `io/Exporter.kt` | PNG/JPG nach `Bilder/Raster` |
| `ui/RasterApp.kt` | Dateiauswahl, Autosave (20 s und bei `ON_STOP`), Dialoge, Vorschau |
| `ui/StudioLayout.kt` | Kopfzeile, Werkzeugleiste links, Arbeitsfläche, Fenster rechts |
| `ui/CanvasView.kt` | Arbeitsfläche, Overlays (Auswahl, Anfasser, Hilfslinien, Verbindungspfeile) und **alle Gesten** |
| `ui/Panels.kt` | Reiter Ebenen/Bausteine/Komponenten, Eigenschaften, Prototyp-Fenster |
| `ui/Preview.kt` | Vollbild-Vorschau mit Übergängen (`AnimatedContent`) |
| `ui/Dialogs.kt` | Farbwähler, Einstellungen, Neues Projekt, Export |
| `ui/Components.kt`, `ui/Fields.kt`, `ui/RasterIcons.kt`, `ui/Theme.kt` | Bausteine (aus Kontur), Zahlen- und Textfelder, Strich-Icons, Farben |

### Wichtige Konzepte

- **Screens und Komponenten sind beide `Board`s.** Screens liegen frei auf der Arbeitsfläche (`x`, `y` in Dokumentkoordinaten). Komponenten liegen bei (0, 0) und werden im Bearbeitungsmodus (`editingComponent`) allein gezeigt; `boards()` liefert je nach Modus das eine oder das andere.
- **Elemente** liegen flach in `board.children` (keine Gruppen, dafür gibt es Komponenten), Koordinaten relativ zur linken oberen Ecke des Boards. Linien gehen von (x, y) nach (x + w, y + h), `w`/`h` dürfen dort negativ sein; `bounds()` normalisiert.
- **Bausteine** werden nur aus Parametern gezeichnet (`Renderer.drawContent`). Neuer Baustein: `Kind` ergänzen, Startwerte in `Widgets`, Zeichnen im `Renderer`, Icon in `kindIcon`, ggf. eigene Felder in `ElementProps`.
- **Text-Elemente** sind immer so hoch wie ihr Text (`fitText`), nur die Breite ist frei.
- **Instanzen** (`Kind.INSTANCE`) zeichnen die Komponente skaliert auf ihre Größe. Überschreibungen in `overrides` mit Schlüsseln `<Element-ID>.text` und `<Element-ID>.fill`. Rekursion ist auf Tiefe 4 begrenzt; eine Komponente kann sich nicht selbst enthalten.
- **Verbindungen** (`Element.link`) zeigen auf eine Screen-ID oder `Link.BACK`. Beim Löschen eines Screens werden Verbindungen dorthin entfernt.
- **Rückgängig:** Das ganze Dokument als JSON-Text (max. 100 Schritte). `checkpoint(key)` mit gleichem Schlüssel innerhalb von 1,5 s fasst Schieberegler- und Tipp-Änderungen zusammen. Selektion wird nach dem Wiederherstellen bereinigt.
- **Veraltete Anzeige vermeiden:** Das Modell ist kein Compose-State. **Jede Composable, die Modellwerte anzeigt, muss `state.revision` lesen.** Jede Änderung ruft `touch()`.
- **Eingabe** wie in Kontur: Stift = zeichnen und Auswahlrechteck, Finger = navigieren. Antippen wählt aus. Verschieben nur mit Ausgewähltem; innerhalb des Auswahlrahmens verschiebt Ziehen immer die Auswahl. Ein zweiter Finger bricht ab und navigiert. Handballen: nur der Zeiger, der die Geste begonnen hat, zählt.
- **Screen auswählen:** Titel über dem Screen antippen (oder in der Ebenenliste). Dann verschiebt Ziehen den Screen und die Anfasser ändern seine Größe.

### Muster aus Kontur, auf die man achten sollte

- `VerticalDivider` in Leisten immer mit fester Höhe.
- Eigene Kurven nie mit `addOval`/`addRoundRect` bauen, wenn sie je zurückgelesen werden (Conic-Fehler auf Android 14+). Raster zeichnet nur, liest nichts zurück, daher hier unkritisch.
- Dateiauswahl mit MIME `application/octet-stream`, sonst hängt Android `.json` an.

## Stand (Version 0.1.0)

Alles aus der README ist geschrieben, aber **noch nie live geprüft**. Bekannte Lücken und Verdächtiges:

- Nur das **Studio-Layout**. Ein Schwebend-Layout wie in Lasur/Kontur fehlt noch. Die Kopfzeile ist im Hochformat eng.
- Keine Drehung von Elementen (für UI selten nötig), keine Gruppen.
- Instanzen werden beim Skalieren verzerrt (einfaches Strecken, kein Responsive Resize).
- Bausteine sind in der Vorschau nicht bedienbar (Schalter umlegen o. Ä.), nur Verbindungen.
- Ziehen aus der Bibliothek auf die Arbeitsfläche gibt es nicht, Antippen setzt in die Mitte.
- Prüfen: Einrasten beim Skalieren mit mehreren Elementen, Screen-Titel-Treffer bei starkem Zoom, Rückgängig während Komponenten-Bearbeitung, Export mit sehr großen Screens (Tab S9+ in 3×), Autosave/Wiederherstellen.

## Ideen für später (grob nach Priorität)

1. Schwebend-Layout
2. Wiederholungsraster (Repeat Grid) und Abstände zwischen Elementen anzeigen
3. Bilder einfügen (Galerie/Kamera), Icons-Bibliothek
4. Zustände für Komponenten (z. B. Knopf normal/gedrückt), Hover/Tippen in der Vorschau
5. Export als SVG/PDF, Übernahme von Elementen aus Kontur
