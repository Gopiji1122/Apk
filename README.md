# Offline PDF Studio (Phase 2 — Professional PDF Reader)

A private, 100% offline, modular Android PDF Editor & Document Scanner built with **Kotlin**, **Jetpack Compose (Material 3)**, **Clean Architecture + MVVM**, **Hilt**, **Room**, **DataStore**, **Android Storage Access Framework**, and **MuPDF (`com.artifex.mupdf:fitz:1.28.5`)**.

## Phase 2 Completed Features
- **Phase 1 Foundation**: 12-module architecture, Material 3 themes, English + Hindi localization, SAF File Manager, Room/DataStore persistence, MuPDF JNI engine.
- **Phase 2 Professional PDF Reader**:
  - Continuous Vertical, Single-Page Horizontal (`HorizontalPager`), and Two-Page Spread layouts
  - Fit Width, Fit Page, and Actual Size (100%) zoom presets + pinch-to-zoom + double-tap zoom
  - Tap-to-toggle full-screen immersive reading controls
  - Real search result highlighting drawn on PDF pages (rotation-aware normalized `Quad` boxes) + Previous/Next match navigation
  - Lazily rendered Page Thumbnails grid sheet (separate thumbnail `LruCache`)
  - Bookmarks manager sheet + Table of Contents (PDF Outline) sheet + Go-to-Page dialog
  - Selectable & copyable page text via MuPDF `StructuredText`
  - Light, Dark Canvas, and Night Inverted reading modes + 90° rotation + auto-resume last reading position

## Upcoming Phases (Honestly Labeled in UI)
- **Phase 3**: CameraX + OpenCV Document Scanner (auto edge detection, stability capture, 4-point crop, 10 filters, unlimited pages)
- **Phase 4**: Tesseract4Android Offline OCR (English + Hindi Devanagari, searchable PDF, TXT export)
- **Phases 5–9**: Annotations, Content Editor, Page Organizer, Forms/Signatures, Compression, Security & True Redaction
