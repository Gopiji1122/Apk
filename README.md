> **Phase 3.6** — see CHANGELOG_PHASE3_6.md for the premium redesign, reader/scanner completion and bug fixes.

# Offline PDF Studio (Phase 3.5 — Hardened Reader & Offline Scanner)

A private, 100% offline, modular Android PDF Editor & Document Scanner built with **Kotlin**, **Jetpack Compose (Material 3)**, **Clean Architecture + MVVM**, **Hilt**, **Room**, **DataStore**, **CameraX 1.4.2**, **OpenCV 4.12.0**, and **MuPDF 1.28.5**.

## Completed Through Phase 3.5
- **Phase 1 Foundation:** 13-module Gradle architecture, Light/Dark/System themes, English + Hindi UI, SAF File Manager, custom adaptive vector app icon, zero internet/tracking permissions.
- **Phase 2 & 3.5 Smooth Tiled PDF Viewer:** Google Drive–style minimalist full-screen reader, floating frosted header with inline search & match navigation, slim right-edge vertical scrubber, coordinated single-state-machine page renderer, directional predictive pre-rendering (next 2 + prev 1), and high-zoom MuPDF tile rendering (`renderRegion`) for crisp 7× Hindi & diagram zoom.
- **Phase 3 & 3.5 Hardened Document Scanner:** CameraX `640×480` low-CPU analysis + OpenCV Laplacian blur & exposure checks, smooth Adobe Scan–style Auto-Capture, direct 2-finger pinch-to-zoom, Fast/HD quality toggle, EXIF upright normalization, interactive 4-corner crop with live magnifier loupe, custom **Curved Protractor Rotation Dial**, all 10 OpenCV enhancement modes, persistent Room + `filesDir` crash-safe session recovery, abort-on-corrupt-page PDF export, and multi-page JPEG/PNG ZIP export.

## Upcoming Phases
- **Phase 4:** Tesseract4Android Offline English & Hindi (Devanagari) OCR + Searchable PDF + TXT export.
- **Phases 5–9:** Annotations, Content Editor, Page Organizer (Merge/Split), Forms & Signatures, Compression, Watermark, Passwords & True Redaction.
