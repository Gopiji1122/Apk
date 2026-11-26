# Offline PDF Studio (Phase 1 — MuPDF Foundation)

A private, 100% offline, modular Android PDF Editor & Document Scanner built with **Kotlin**, **Jetpack Compose (Material 3)**, **Clean Architecture + MVVM**, **Hilt**, **Room**, **DataStore**, **Android Storage Access Framework**, and **MuPDF (`com.artifex.mupdf:fitz:1.28.5`)**.

## Phase 1 Status

### Completed (Working Code)
- Modular 12-module Gradle architecture (`app`, `core:*`, `feature:*`)
- Material 3 Light / Dark / System theme + English & Hindi (Devanagari) localization
- Animated 5-tab bottom navigation (`Home`, `Files`, `Scan`, `Tools`, `Settings`)
- SAF Document Picker & File Manager (Search, Sort by Date/Name/Size, Grid/List, Rename, Share, Favorites, Remove)
- MuPDF JNI Reader (`core:pdf`): lazy page rendering on `Dispatchers.IO`, `LruCache`, pinch-to-zoom, double-tap zoom, page jump slider, 90° rotation, night reading inversion, password-protected PDF unlock, in-document text search, bookmarks, metadata inspector, and reading position resume
- Settings: Theme, Language (`en`/`hi`), Haptic/Animation toggles, Cache size & Clear Temporary Files, Open-Source Licenses & AGPL Notice
- GitHub Actions CI workflow (`.github/workflows/android-ci.yml`) for ARM64 Debug & Release APK builds

### Pending (Honestly Labeled in UI — Zero Fake Buttons)
- **Phase 2**: Two-page view, thumbnail drawer, text selection/copy, search highlight overlays
- **Phase 3**: CameraX + OpenCV Document Scanner (auto edge detection, stability capture, 4-point perspective crop, 10 enhancement filters, unlimited disk-backed multi-page queue)
- **Phase 4**: Tesseract4Android Offline OCR (English + Hindi Devanagari, searchable PDF, TXT export)
- **Phases 5–9**: Annotations, Content Editor, Page Organizer, Forms/Signatures, Compression, Watermark, Passwords, True Redaction

## Building the ARM64 APK via GitHub Actions

Push these files to the root of your GitHub repository (each command short for mobile terminals):

```bash
git init
git add .
git commit -m "Phase 1: Offline PDF Studio MuPDF foundation"
git branch -M main
git remote add origin https://github.com/<YOUR_USER>/<YOUR_REPO>.git
git push -u origin main
```

Then open the **Actions** tab in GitHub and download `OfflinePdfStudio-Phase1-arm64-debug-apk` (or configure `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD` secrets for signed Release APKs).
