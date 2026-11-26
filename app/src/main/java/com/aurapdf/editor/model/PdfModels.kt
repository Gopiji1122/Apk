package com.aurapdf.editor.model

import java.util.UUID

enum class AppThemeMode(val displayName: String, val description: String) {
    ACROBAT_DARK("Acrobat Dark", "Signature dark studio workspace"),
    AMOLED_BLACK("AMOLED Pure Black", "True #000000 OLED battery saver"),
    ACROBAT_LIGHT("Acrobat Light", "Crisp daylight document workspace")
}

enum class EditorLaunchAction {
    NONE,
    ADD_TEXT,
    ORGANIZE,
    CROP
}

sealed class Screen {
    data object Home : Screen()
    data object Scanner : Screen()
    data class ViewPdf(val pdfPath: String) : Screen()
    data class EditPdf(
        val pdfPath: String,
        val initialAction: EditorLaunchAction = EditorLaunchAction.NONE
    ) : Screen()
}

data class PendingFolderExport(
    val rangeMode: PageRangeMode,
    val customRange: String,
    val format: ExportFormat,
    val jpegQuality: Int,
    val dpi: Int
)

data class RecentPdfItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val filePath: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val isStarred: Boolean = false,
    val pageCount: Int = 1,
    val isEncrypted: Boolean = false
)

enum class PageSizeOption(val label: String, val widthPt: Float, val heightPt: Float) {
    A4("A4 (210 × 297 mm)", 595f, 842f),
    LETTER("Letter (8.5 × 11 in)", 612f, 792f),
    LEGAL("Legal (8.5 × 14 in)", 612f, 1008f)
}

enum class PageOrientation(val label: String) {
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

enum class PdfFontFamily(val displayName: String) {
    SANS("Sans (Helvetica)"),
    SERIF("Serif (Times)"),
    MONO("Mono (Courier)")
}

enum class ShapeKind(val label: String) {
    RECTANGLE("Rectangle"),
    LINE("Line")
}

data class NormPoint(val x: Float, val y: Float)

data class FreehandStroke(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int,
    val points: List<NormPoint>,
    val colorArgb: Long,
    val strokeWidthNorm: Float = 0.005f
)

sealed class OverlayItem {
    abstract val id: String
    abstract val pageIndex: Int
    abstract val xNorm: Float
    abstract val yNorm: Float
    abstract val wNorm: Float
    abstract val hNorm: Float

    abstract fun withBounds(x: Float, y: Float, w: Float, h: Float): OverlayItem

    data class TextOverlay(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override val xNorm: Float = 0.15f,
        override val yNorm: Float = 0.20f,
        override val wNorm: Float = 0.50f,
        override val hNorm: Float = 0.08f,
        val text: String,
        val fontSizeSp: Float = 16f,
        val colorArgb: Long = 0xFF111111,
        val fontFamily: PdfFontFamily = PdfFontFamily.SANS,
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val isUnderline: Boolean = false,
        val isStrikethrough: Boolean = false,
        val highlightColorArgb: Long? = null,
        val linkUrl: String? = null
    ) : OverlayItem() {
        override fun withBounds(x: Float, y: Float, w: Float, h: Float): OverlayItem {
            val scaleRatio = if (wNorm > 0.01f) (w / wNorm).coerceIn(0.5f, 2.0f) else 1f
            val updatedFontSize = (fontSizeSp * scaleRatio).coerceIn(8f, 72f)
            return copy(xNorm = x, yNorm = y, wNorm = w, hNorm = h, fontSizeSp = updatedFontSize)
        }
    }

    data class ImageOverlay(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override val xNorm: Float = 0.20f,
        override val yNorm: Float = 0.25f,
        override val wNorm: Float = 0.40f,
        override val hNorm: Float = 0.25f,
        val imagePath: String
    ) : OverlayItem() {
        override fun withBounds(x: Float, y: Float, w: Float, h: Float) =
            copy(xNorm = x, yNorm = y, wNorm = w, hNorm = h)
    }

    data class HighlightOverlay(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override val xNorm: Float = 0.12f,
        override val yNorm: Float = 0.30f,
        override val wNorm: Float = 0.65f,
        override val hNorm: Float = 0.04f,
        val colorArgb: Long = 0xFFFFEB3B,
        val opacity: Float = 0.42f
    ) : OverlayItem() {
        override fun withBounds(x: Float, y: Float, w: Float, h: Float) =
            copy(xNorm = x, yNorm = y, wNorm = w, hNorm = h)
    }

    data class WhiteoutOverlay(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override val xNorm: Float = 0.15f,
        override val yNorm: Float = 0.35f,
        override val wNorm: Float = 0.45f,
        override val hNorm: Float = 0.06f
    ) : OverlayItem() {
        override fun withBounds(x: Float, y: Float, w: Float, h: Float) =
            copy(xNorm = x, yNorm = y, wNorm = w, hNorm = h)
    }

    data class RedactOverlay(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override val xNorm: Float = 0.15f,
        override val yNorm: Float = 0.40f,
        override val wNorm: Float = 0.45f,
        override val hNorm: Float = 0.06f,
        val label: String = "REDACTED"
    ) : OverlayItem() {
        override fun withBounds(x: Float, y: Float, w: Float, h: Float) =
            copy(xNorm = x, yNorm = y, wNorm = w, hNorm = h)
    }

    data class StickyNoteOverlay(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override val xNorm: Float = 0.72f,
        override val yNorm: Float = 0.18f,
        override val wNorm: Float = 0.12f,
        override val hNorm: Float = 0.09f,
        val noteText: String,
        val author: String = "Aura User",
        val colorArgb: Long = 0xFFFFD54F
    ) : OverlayItem() {
        override fun withBounds(x: Float, y: Float, w: Float, h: Float) =
            copy(xNorm = x, yNorm = y, wNorm = w, hNorm = h)
    }

    data class ShapeOverlay(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override val xNorm: Float = 0.20f,
        override val yNorm: Float = 0.30f,
        override val wNorm: Float = 0.45f,
        override val hNorm: Float = 0.15f,
        val shapeKind: ShapeKind = ShapeKind.RECTANGLE,
        val colorArgb: Long = 0xFFE52521,
        val strokeWidthPt: Float = 3f
    ) : OverlayItem() {
        override fun withBounds(x: Float, y: Float, w: Float, h: Float) =
            copy(xNorm = x, yNorm = y, wNorm = w, hNorm = h)
    }

    data class SignatureOverlay(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override val xNorm: Float = 0.50f,
        override val yNorm: Float = 0.75f,
        override val wNorm: Float = 0.36f,
        override val hNorm: Float = 0.12f,
        val strokes: List<List<NormPoint>>,
        val inkColorArgb: Long = 0xFF111111,
        val digitalCertInfo: DigitalCertInfo? = null
    ) : OverlayItem() {
        override fun withBounds(x: Float, y: Float, w: Float, h: Float) =
            copy(xNorm = x, yNorm = y, wNorm = w, hNorm = h)
    }
}

data class DigitalCertInfo(
    val signerName: String,
    val reason: String,
    val location: String,
    val timestamp: String,
    val sha256Hash: String
)

data class EditorSnapshot(
    val overlays: List<OverlayItem>,
    val strokes: List<FreehandStroke>
)

data class SearchMatch(
    val pageIndex: Int,
    val matchIndexOnPage: Int,
    val snippet: String
)

data class BookmarkItem(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int,
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class PdfLinkInfo(
    val pageIndex: Int,
    val annotationIndex: Int,
    val uri: String,
    val rectSummary: String
)

enum class CompressPreset(
    val label: String,
    val description: String,
    val quality: Int,
    val dpi: Int
) {
    EXTREME("Extreme", "Smallest file size (30% quality, 96 DPI)", 30, 96),
    HIGH("High", "Strong compression (50% quality, 120 DPI)", 50, 120),
    BALANCED("Balanced", "Recommended clarity & size (72% quality, 150 DPI)", 72, 150),
    LOW("Low", "High print fidelity (88% quality, 220 DPI)", 88, 220),
    CUSTOM("Custom", "User-configured sliders", 70, 150)
}

data class CompressConfig(
    val preset: CompressPreset = CompressPreset.BALANCED,
    val jpegQuality: Int = 72,
    val targetDpi: Int = 150,
    val grayscale: Boolean = false,
    val stripMetadata: Boolean = false
)

data class CompressionResult(
    val outputFilePath: String,
    val originalBytes: Long,
    val compressedBytes: Long,
    val reductionPercent: Float
)

enum class ExportFormat(val label: String, val extension: String, val mimeType: String) {
    PNG("PNG Images", "png", "image/png"),
    JPEG("JPEG Images", "jpg", "image/jpeg"),
    TXT("Plain Text (.txt)", "txt", "text/plain"),
    DOCX("Word Document (.docx)", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    HTML("Web Page (.html)", "html", "text/html"),
    PDF_COPY("Save a Copy (.pdf)", "pdf", "application/pdf")
}

enum class PageRangeMode(val label: String) {
    CURRENT("Current Page"),
    CUSTOM("Custom Range"),
    ALL("All Pages")
}

enum class ScanFilter(val label: String) {
    ORIGINAL("Original"),
    AUTO_COLOR("Auto Color"),
    GRAYSCALE("Grayscale"),
    BW_CRISP("B&W Crisp"),
    LIGHTEN("Lighten")
}

data class ScannedPageDraft(
    val id: String = UUID.randomUUID().toString(),
    val imagePath: String,
    val filter: ScanFilter = ScanFilter.AUTO_COLOR,
    val rotationDeg: Int = 0,
    val topLeft: NormPoint = NormPoint(0.05f, 0.05f),
    val topRight: NormPoint = NormPoint(0.95f, 0.05f),
    val bottomRight: NormPoint = NormPoint(0.95f, 0.95f),
    val bottomLeft: NormPoint = NormPoint(0.05f, 0.95f)
)
