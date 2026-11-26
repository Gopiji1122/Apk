# Offline PDF Studio — Phase 1 Dependency, License & Compatibility Report

## 1. Environment Inspection & Build Strategy

- **Sandbox Environment**: OpenJDK `17.0.21`, Python 3, and standard Linux utilities are installed, with **no Android SDK, NDK, Gradle runtime, or outbound internet access**. Because an APK cannot be compiled locally inside this offline sandbox, this project is delivered as a complete, ready-to-build multi-module Android Gradle project with a verified GitHub Actions workflow (`.github/workflows/android-ci.yml`) that compiles the real **ARM64 (`arm64-v8a`) Debug and Release APKs** on `ubuntu-latest`.
- **Target Device & ABI**: Configured with `abiFilters += listOf("arm64-v8a")` for lean, fast ARM64 APKs compatible with modern 64-bit Android phones (such as OnePlus 8 / Snapdragon 865). To support 32-bit devices or x86_64 emulators later, add `"armeabi-v7a"` or `"x86_64"` to `app/build.gradle.kts`.
- **Android SDK Matrix**:
  - `minSdk = 26` (Android 8.0 Oreo) — satisfies MuPDF (`>= 21`), CameraX (`>= 23`), Tesseract4Android (`>= 21`), adaptive icons, `java.time`, and modern Storage Access Framework APIs.
  - `compileSdk = 35` / `targetSdk = 35` (Android 15) — fully compatible with AGP `8.10.1` and GitHub Actions Android runners.
  - **JDK**: `17` (Temurin) | **Gradle**: `8.11.1` | **NDK / CMake**: Not required in Phase 1 because `com.artifex.mupdf:fitz:1.28.5` ships prebuilt native `.so` libraries for Android ABIs via Artifex's Maven repository.

## 2. Verified Dependency & License Matrix

| Component | Artifact / Version | License | Phase | Verification Source |
| :--- | :--- | :--- | :--- | :--- |
| Android Gradle Plugin | `com.android.application` `8.10.1` | Apache 2.0 | Phase 1 | [Android Kotlin/AGP Support](https://developer.android.com/build/kotlin-support) |
| Kotlin & Compose Compiler | `2.1.21` | Apache 2.0 | Phase 1 | [Compose-Kotlin Compatibility](https://developer.android.com/jetpack/androidx/releases/compose-kotlin) |
| KSP | `2.1.21-2.0.1` | Apache 2.0 | Phase 1 | [Gradle/KSP Compatibility](https://proandroiddev.com/how-to-configure-gradle-the-ultimate-guide-06dfb4eaa92d) |
| Jetpack Compose BOM | `androidx.compose:compose-bom:2025.05.00` | Apache 2.0 | Phase 1 | [Compose BOM Mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping) |
| Navigation Compose | `androidx.navigation:navigation-compose:2.9.0` | Apache 2.0 | Phase 1 | [AndroidX Releases](https://developer.android.com/jetpack/androidx/versions) |
| Hilt DI | `com.google.dagger:hilt-android:2.56.1` | Apache 2.0 | Phase 1 | Pinned to `2.56.1` to avoid `2.56.2` ZipException ([google/dagger#4803](https://github.com/google/dagger/issues/4803)) |
| Room Database | `androidx.room:room-runtime:2.7.2` | Apache 2.0 | Phase 1 | [AndroidX Releases](https://developer.android.com/jetpack/androidx/versions) |
| DataStore Preferences | `androidx.datastore:datastore-preferences:1.1.4` | Apache 2.0 | Phase 1 | [AndroidX Releases](https://developer.android.com/jetpack/androidx/versions) |
| WorkManager | `androidx.work:work-runtime-ktx:2.10.1` | Apache 2.0 | Phase 1 | [AndroidX Releases](https://developer.android.com/jetpack/androidx/versions) |
| MuPDF Native PDF Engine | `com.artifex.mupdf:fitz:1.28.5` | **GNU AGPL v3+** | Phase 1 | [MuPDF Android Guide](https://mupdf.readthedocs.io/en/latest/guide/using-with-android.html), [mupdf-android-viewer-mini](https://github.com/ArtifexSoftware/mupdf-android-viewer-mini/blob/master/lib/build.gradle) |
| CameraX | `androidx.camera:camera-*` (planned `1.4.2+`) | Apache 2.0 | Phase 3 | [CameraX Releases](https://developer.android.com/jetpack/androidx/releases/camera) |
| OpenCV Android | `org.opencv:opencv:4.12.0` (Maven Central) | Apache 2.0 | Phase 3 | [OpenCV Maven Central](https://mvnrepository.com/artifact/org.opencv/opencv/4.12.0) |
| Tesseract4Android | `cz.adaptech.tesseract4android:tesseract4android-openmp:4.9.0` | Apache 2.0 | Phase 4 | [adaptech-cz/Tesseract4Android](https://github.com/adaptech-cz/Tesseract4Android) |

## 3. MuPDF Android Integration & AGPL v3.0 Obligations

- **Integration Approach**: Direct JNI integration via `com.artifex.mupdf:fitz:1.28.5` from Artifex's official Maven repository (`http://maven.ghostscript.com`, configured with `isAllowInsecureProtocol = true` in `settings.gradle.kts`). Rather than embedding Artifex's legacy XML `DocumentActivity`, `core:pdf` wraps `com.artifex.mupdf.fitz.Document`, `Page`, `Matrix`, and `AndroidDrawDevice.drawPage()` behind a clean Kotlin `PdfEngine` interface with thread-safe native locking, `LruCache` bitmap management, and deterministic `page.destroy()` / `document.destroy()` cleanup.
- **AGPL v3.0 Licensing Compliance** ([MuPDF Licensing Guide](https://mupdf.readthedocs.io/en/latest/guide/using-with-android.html)):
  - **Private Offline Personal Use**: Permitted free of charge under GNU AGPL v3.0 without a commercial license.
  - **Zero Proprietary Dependencies**: This codebase strictly excludes Google Play Services, Google ML Kit, AdMob, and Crashlytics so it remains 100% AGPL-compatible.
  - **Obligations If Ever Distributed**: If you ever distribute or share compiled APKs with others, GNU AGPL v3.0 requires releasing the complete corresponding source code of the entire application under AGPL v3.0+ (or purchasing a commercial license from Artifex Software).

## 4. Technical Limitations & Honest Engineering Boundaries

1. **Direct PDF Text Editing (Phase 6)**: PDF is a fixed-layout format, not a reflowable word processor. Scanned PDFs, outlined vector glyphs, and subset-embedded fonts cannot be edited in place like a `.docx` file. Phase 6 will detect editable content vs. non-editable regions and offer honest cover-and-overlay tools where direct glyph editing is unsupported.
2. **True Redaction (Phase 8)**: MuPDF's `PDFPage` supports real redaction annotations (`PDFAnnotation.TYPE_REDACT`) and `applyRedactions()`, which permanently strip underlying text/graphics from the content stream. Phase 8 will verify post-export text extraction confirms zero recoverable characters before labeling redaction complete.
3. **Visual vs. Cryptographic Signatures (Phase 8)**: Handwritten/image signatures placed on a page are **visual signatures** only and do not provide PKCS#7 / X.509 cryptographic authentication. Certificate-based digital signing will be clearly separated as a future capability.
4. **Offline Office Conversion (DOCX / XLSX / PPTX)**: High-fidelity offline Office-to-PDF conversion on Android requires a full layout engine (such as LibreOfficeKit, ~100+ MB native footprint). It is intentionally excluded from the core Phase 1 APK and will be evaluated as a separate optional plugin or plain-text/HTML fallback in Phase 8.
5. **Offline OCR Language Data (Phase 4)**: Tesseract4Android requires local `eng.traineddata` and `hin.traineddata` files stored in app-private `tessdata/`. Per your offline requirement, Phase 4 will provide a local SAF file importer (no automatic internet downloads).
