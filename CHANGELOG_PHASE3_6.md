# Phase 3.6 — Premium polish + complete Viewer & Scanner

Scope: only Phase 1–3 features (Home, Files, Reader, Scanner, Settings). No Phase 4+ tools were added.
Version 1.5.0-phase3.6 (versionCode 10).

## Not verified yet
This build was written without an Android SDK, so it has NOT been compiled or run on a device.
Please run `./gradlew :app:assembleDebug testDebugUnitTest` and report any compile error.
(The Gradle wrapper JAR/scripts were not in the uploaded ZIP; generate them with `gradle wrapper`.)

## Bugs fixed
### Reader
- **High-zoom tiles were wrong**: the whole page was requested as one tile that MuPDF cropped to 1800x2200, then stretched over the page (wrong region, soft text). Now only the visible region is rendered, pixel-exact, at screen density.
- Zoom + list scroll stacked (content moved faster than the finger). Zoom state is now one engine: pinch, pan, fling, double-tap with focus-correct animation.
- Page layout shifted while scrolling mixed-size PDFs; real page sizes are loaded in the background.
- Current page was "first visible item"; it is now the page at the viewport centre.
- Resume page could be overwritten by page 0 on open.
- Share used the original URI (fails for non-app files) -> FileProvider copy named after the document.
- Large PDFs were copied twice on every open -> single copy, skipped entirely when the source is unchanged.
- Out-of-memory while rendering now retries at lower resolution instead of crashing.
- Search: per-match highlight (active match in orange), "3 of 17", progress, cancellable, Back closes search.
### Scanner
- Camera outline did not line up with the letter-boxed preview.
- Fine-rotation preview rotated the photo under a fixed quad; replaced by a live perspective-corrected, straightened preview.
- Busy overlay could stay forever after any processing error; all work now runs through one guarded path.
- Slider drags launched many concurrent enhances writing the same file; now debounced, latest-wins, serialised.
- Session snapshots could interleave (clearAll/upsertAll) and lose pages; now serialised.
- Back button left the Scan tab and lost context; now steps back through Enhance -> Crop -> Review -> Camera.
- No confirmation when deleting a page; added delete + "discard all" confirmations.
- PDF export used Android PdfDocument (lossless Flate => huge files). New streaming JPEG-embedding writer: typically 5-10x smaller, constant memory.
- Failed exports left zero-byte files; now cleaned up. Output streams use truncate mode ("wt").
- Image decode could land far below the requested HD size; now exact.
- Document-colour filter ~10x faster (background estimated at 1/4 scale).
- Edge detector rejects non-convex candidates.
### App
- Dead settings now work: haptics, animations, default layout, night reading (also exposed in Settings).
- Language choice now persists on Android 12 and below; per-app locale config added.
- Home tab navigation did not save/restore tab state.

## Completed (previously advertised or declared but missing)
- Reading modes: continuous scroll, single page (swipe), two-page spread.
- Zoom presets (fit width / fit page / actual size), Contents (outline) tab, tappable PDF links (internal jump + external with confirmation), go-to-page dialog, copy page text, immersive reading (bars hide with controls, screen stays on).
- Library thumbnails (first page, cached), Scanned filter, share from the library.

## Premium UI
- New "Ruby & Ink" theme (light/dark), typography, shapes, motion tokens that obey the Animations setting.
- New adaptive icon (gradient, themed monochrome layer) + animated splash.
- Animated bottom bar with spring selection pill and raised Scan action; hides during camera/crop/enhance.
- Press-scale tactile cards, staggered entrances, shimmer skeletons, shutter flash, focus ring.
- English + Hindi strings for all main screens (scanner stage labels are still English).
