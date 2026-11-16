# Aura Audio Pro — Premium Offline Android Music Player

Aura Audio Pro is a complete, 100% offline Android music player built with **Kotlin**, **Jetpack Compose (Material 3)**, **AndroidX Media3 (ExoPlayer + MediaSessionService)**, and **Room SQLite Database**.

## 1. Architecture & Package Structure
- `com.aura.audiopro.model` — Immutable domain models (`Song`, `LrcLine`, `PlaylistModel`, `DuplicateGroup`, `AppSettings`, `SortOption`, `ThemeMode`).
- `com.aura.audiopro.core` — Size-keyed `LruCache` album artwork loader, dominant color extractor, synchronized `.lrc` parser, `MediaMetadataRetriever` technical inspector, and duplicate detector.
- `com.aura.audiopro.data.db` — Room SQLite database (`AuraDatabase`, `AuraDao`, and 7 entity tables for favorites, play history, playlists, playlist track ordering, excluded folders, metadata overrides, and custom equalizer presets).
- `com.aura.audiopro.data.scanner` — `MediaStoreScanner` with `ContentObserver` for automatic library updates and folder exclusion filtering.
- `com.aura.audiopro.data.repository` — `MusicRepository` coordinating Room Flows, queue state persistence, settings, and JSON playlist export.
- `com.aura.audiopro.audio` — `AuraPlaybackService` (`MediaSessionService`) and `EqualizerController` (`Equalizer`, `BassBoost`, `Virtualizer`, Stereo Balance, Sleep Timer, and corrupted-file recovery).
- `com.aura.audiopro.ui` — Modular Compose UI across `theme/`, `viewmodel/`, `components/`, and `screens/` (`HomeScreen`, `LibraryScreen`, `PlaylistsScreen`, `EqualizerScreen`, `FoldersScreen`, `SettingsScreen`, `NowPlayingScreen`).

## 2. How to Build APK via GitHub Actions (Zero Local Setup)
1. Upload or push this project to your GitHub repository (`main` or `master` branch).
2. Open the **Actions** tab on GitHub and select **Build Aura Audio Pro APK**.
3. GitHub Actions automatically runs unit tests (`gradle testDebugUnitTest`) and builds the APKs (`gradle assembleDebug assembleRelease`).
4. Scroll down to **Artifacts** and download **`Aura-Audio-Pro-APK`**.

## 3. Production Release Signing & Play Store AAB Instructions
To sign with your own production keystore (never commit `.jks` files to Git):
```bash
keytool -genkey -v -keystore aura-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias aura
./gradlew bundleRelease
```
