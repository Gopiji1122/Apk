package com.offlinepdf.studio.core.imageprocessing

import kotlin.math.hypot
import kotlin.math.max

data class NormalizedPoint(
    val x: Float,
    val y: Float
) {
    fun clamped(): NormalizedPoint = NormalizedPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
}

/**
 * Four document corners in normalized [0f..1f] image coordinates:
 * topLeft -> topRight -> bottomRight -> bottomLeft.
 */
data class DocumentQuad(
    val topLeft: NormalizedPoint,
    val topRight: NormalizedPoint,
    val bottomRight: NormalizedPoint,
    val bottomLeft: NormalizedPoint
) {
    fun asList(): List<NormalizedPoint> = listOf(topLeft, topRight, bottomRight, bottomLeft)

    fun maxCornerDelta(other: DocumentQuad): Float {
        return max(
            max(
                hypot((topLeft.x - other.topLeft.x).toDouble(), (topLeft.y - other.topLeft.y).toDouble()).toFloat(),
                hypot((topRight.x - other.topRight.x).toDouble(), (topRight.y - other.topRight.y).toDouble()).toFloat()
            ),
            max(
                hypot((bottomRight.x - other.bottomRight.x).toDouble(), (bottomRight.y - other.bottomRight.y).toDouble()).toFloat(),
                hypot((bottomLeft.x - other.bottomLeft.x).toDouble(), (bottomLeft.y - other.bottomLeft.y).toDouble()).toFloat()
            )
        )
    }

    companion object {
        fun fullFrame(inset: Float = 0.04f): DocumentQuad = DocumentQuad(
            topLeft = NormalizedPoint(inset, inset),
            topRight = NormalizedPoint(1f - inset, inset),
            bottomRight = NormalizedPoint(1f - inset, 1f - inset),
            bottomLeft = NormalizedPoint(inset, 1f - inset)
        )

        /**
         * Orders four arbitrary points into canonical [topLeft, topRight, bottomRight, bottomLeft].
         */
        fun fromUnordered(points: List<NormalizedPoint>): DocumentQuad {
            require(points.size == 4) { "DocumentQuad requires exactly 4 points" }
            val bySum = points.sortedBy { it.x + it.y }
            val topLeft = bySum.first().clamped()
            val bottomRight = bySum.last().clamped()
            val remaining = points.filter { it != bySum.first() && it != bySum.last() }
            val byDiff = if (remaining.size == 2) {
                remaining.sortedBy { it.y - it.x }
            } else {
                points.sortedBy { it.y - it.x }
            }
            val topRight = byDiff.first().clamped()
            val bottomLeft = byDiff.last().clamped()
            return DocumentQuad(topLeft, topRight, bottomRight, bottomLeft)
        }
    }
}

enum class ScanFilterMode(val displayName: String) {
    ORIGINAL_COLOR("Original"),
    AUTO_COLOR("Auto Color"),
    DOCUMENT_COLOR("Doc Color"),
    ENHANCED_COLOR("Enhanced"),
    BLACK_AND_WHITE("B&W"),
    GRAYSCALE("Grayscale"),
    HIGH_CONTRAST("High Contrast"),
    PHOTO_MODE("Photo"),
    CLEAN_DOCUMENT("Clean Doc"),
    CUSTOM("Custom")
}

data class CustomAdjustments(
    val brightness: Float = 0f,      // -60f .. +60f
    val contrast: Float = 1.0f,      // 0.5f .. 2.0f
    val saturation: Float = 1.0f,    // 0.0f .. 2.0f
    val sharpness: Float = 0.0f      // 0.0f .. 1.5f
)

enum class ExportFormat(val displayName: String, val mimeType: String, val extension: String) {
    PDF("PDF Document", "application/pdf", "pdf"),
    JPEG("JPEG Image", "image/jpeg", "jpg"),
    PNG("PNG Image", "image/png", "png")
}

enum class ExportPageSize(val displayName: String, val widthPt: Int, val heightPt: Int) {
    A4("A4", 595, 842),
    A5("A5", 420, 595),
    LETTER("Letter", 612, 792),
    LEGAL("Legal", 612, 1008),
    ORIGINAL("Original", 0, 0)
}

enum class ExportOrientation(val displayName: String) {
    AUTO("Auto"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

enum class ExportMargin(val displayName: String, val points: Int) {
    NONE("None", 0),
    SMALL("Small", 18),
    MEDIUM("Medium", 36)
}

data class ExportOptions(
    val title: String = "Scanned_Document",
    val format: ExportFormat = ExportFormat.PDF,
    val pageSize: ExportPageSize = ExportPageSize.A4,
    val orientation: ExportOrientation = ExportOrientation.AUTO,
    val margin: ExportMargin = ExportMargin.NONE,
    val jpegQuality: Int = 85
)

/**
 * Disk-backed scanned page descriptor. Full-resolution bitmaps are never kept in RAM;
 * only lightweight local file paths are stored so users can scan unlimited pages safely.
 */
data class ScannedPageItem(
    val id: String,
    val rawImagePath: String,
    val warpedImagePath: String,
    val enhancedImagePath: String,
    val quad: DocumentQuad = DocumentQuad.fullFrame(),
    val rotationDegrees: Int = 0,
    val fineRotationDegrees: Float = 0f,
    val filterMode: ScanFilterMode = ScanFilterMode.AUTO_COLOR,
    val customAdjustments: CustomAdjustments = CustomAdjustments()
)
