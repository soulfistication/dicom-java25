# DICOM Viewer (JDK 25 / Swing)

A dependency-free desktop DICOM viewer written in plain Java (JDK 25) with Swing/AWT.
It is a port of the browser app in [`../`](../README.md): a from-scratch DICOM
parser, a pixel decoder with window/level, and an interactive viewer. It lives
**alongside** the web app and shares none of its code — the browser-only pieces
(IndexedDB, `<canvas>`/`ImageData`, `createImageBitmap`, DOM/pointer gestures)
are re-implemented on the Java desktop stack:

| Web (browser)                     | Java desktop                            |
| --------------------------------- | --------------------------------------- |
| `ArrayBuffer` / `DataView`        | `byte[]` + `ByteReader`                 |
| `<canvas>` + `ImageData`          | `BufferedImage` + a custom `ImageView`  |
| `createImageBitmap` (JPEG)        | `javax.imageio.ImageIO`                 |
| IndexedDB persistence             | folder paths remembered via `Preferences`, re-scanned on launch |
| pointer / wheel gestures          | `MouseListener` / `MouseWheelListener`  |

## Requirements

- **JDK 25** to build (`javac --release 25`).
- **Java 25+** runtime to run (`java`).

## Build & run

```bash
./build.sh     # compiles to build/ and packages dist/DicomViewer.jar
./run.sh       # builds if needed, then launches
```

Or manually:

```bash
javac --release 25 -d build/classes $(find src -name '*.java')
java -cp build/classes dicomviewer.Main
```

## Using it

- **Open Folder…** (or drag a folder onto the window) imports a folder as one
  **series**. Files are recognised by content, so **any extension (or none)**
  works — including files without the `DICM` preamble; `DICOMDIR` and non-DICOM
  files are skipped. The folder is re-scanned from disk on the next launch.
- **Scrub** slices with the bottom slider, the **mouse wheel**, or the arrow
  keys. A single multi-frame file scrubs by **frame** instead.
- **Window/Level** tool: drag to change contrast/brightness (shared across the
  series). **Pan** tool: drag to move. **Ctrl/⌘/Alt + wheel** to **zoom**.
- **Double-click** to reset the view. **Reset** button does the same.
- **Metadata** opens a searchable inspector of every parsed element, including
  nested sequences.

## Source layout (`src/dicomviewer/`)

| File | Responsibility |
| --- | --- |
| `ByteReader.java` | Endian-aware cursor over the file bytes |
| `DicomTag.java` | Tag (group, element) + well-known tag keys |
| `Vr.java` | Value Representation encoding rules |
| `Dictionary.java` | Tag names + VRs for Implicit VR parsing |
| `DicomElement.java` | A parsed element with typed value accessors |
| `DicomDataset.java` | Ordered element collection with lookups |
| `DicomParser.java` | DICOM Part 10 parser (Explicit/Implicit VR, LE/BE, sequences, encapsulated pixel data) |
| `DicomImage.java` | Pixel decoder → `BufferedImage`, window/level, rescale, RGB, JPEG via ImageIO |
| `NaturalOrder.java` | Numeric-aware filename ordering |
| `Library.java` / `Series.java` | Folder import, DICOM detection, persisted library |
| `ui/ImageView.java` | Interactive canvas (zoom / pan / window-level / overlays) |
| `ui/MetadataDialog.java` | Searchable metadata inspector |
| `ui/MainFrame.java` | Application window / controller |
| `Main.java` | Entry point |

## Supported encodings

- Uncompressed: Explicit/Implicit VR, little- and big-endian; 8/16-bit signed
  and unsigned grayscale (MONOCHROME1/2) and RGB (interleaved and planar);
  multi-frame; modality rescale (slope/intercept) and window/level.
- Encapsulated: Baseline / Extended **JPEG** (decoded via `ImageIO`).

## Limitations

- JPEG Lossless, JPEG 2000 and RLE encapsulated pixel data are not decoded (no
  built-in codec). Such files still open in the metadata inspector and show a
  clear "unsupported" message in the viewer.
- Linear window/level only (no VOI LUT sequence support).
