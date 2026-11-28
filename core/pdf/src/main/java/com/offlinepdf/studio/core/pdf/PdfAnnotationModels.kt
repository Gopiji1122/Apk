package com.offlinepdf.studio.core.pdf

import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class PdfEditorMode(val label: String) {
    COMMENT("Comment"),
    FILL_AND_SIGN("Fill & Sign")
}

enum class PdfAnnotationTool(val label: String) {
    PAN("Scroll"),
    HIGHLIGHT("Highlight"),
    UNDERLINE("Underline"),
    STRIKETHROUGH("Strike"),
    PEN("Draw"),
    NOTE("Note"),
    TEXT("Text"),
    RECTANGLE("Box"),
    CIRCLE("Circle"),
    ARROW("Arrow"),
    LINE("Line"),
    ERASER("Erase"),
    // Fill & Sign tools
    FILL_TEXT("Add Text"),
    CHECKMARK("Check ✓"),
    CROSS("Cross ✕"),
    SIGNATURE("Sign")
}

data class PdfNormPoint(
    val x: Float,
    val y: Float
) {
    fun clamped(): PdfNormPoint = PdfNormPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
}

enum class PdfMarkupKind {
    HIGHLIGHT,
    UNDERLINE,
    STRIKETHROUGH
}

enum class PdfShapeKind {
    RECTANGLE,
    CIRCLE,
    LINE,
    ARROW
}

sealed interface PdfEditAnnotation {
    val id: String
    val pageIndex: Int
    val colorArgb: Int

    data class Markup(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override val colorArgb: Int,
        val kind: PdfMarkupKind,
        val leftNorm: Float,
        val topNorm: Float,
        val rightNorm: Float,
        val bottomNorm: Float
    ) : PdfEditAnnotation

    data class Ink(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override val colorArgb: Int,
        val strokeWidthPt: Float = 2.5f,
        val opacity: Float = 1.0f,
        val strokes: List<List<PdfNormPoint>>,
        val isSignature: Boolean = false
    ) : PdfEditAnnotation

    data class StickyNote(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override val colorArgb: Int,
        val xNorm: Float,
        val yNorm: Float,
        val text: String
    ) : PdfEditAnnotation

    data class FreeText(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override val colorArgb: Int,
        val xNorm: Float,
        val yNorm: Float,
        val widthNorm: Float = 0.36f,
        val heightNorm: Float = 0.055f,
        val text: String,
        val fontSizePt: Float = 14f
    ) : PdfEditAnnotation

    data class Shape(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override val colorArgb: Int,
        val kind: PdfShapeKind,
        val startXNorm: Float,
        val startYNorm: Float,
        val endXNorm: Float,
        val endYNorm: Float,
        val strokeWidthPt: Float = 2.2f
    ) : PdfEditAnnotation
}

/**
 * Pure Kotlin PDF-space coordinate math & hit-testing helpers (100% unit-testable).
 */
object PdfAnnotationCoordinateMath {

    fun argbToRgbFloats(argb: Int): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return floatArrayOf(r, g, b)
    }

    fun toPdfX(normX: Float, pageX0: Float, pageWidth: Float): Float =
        pageX0 + normX.coerceIn(0f, 1f) * pageWidth

    fun toPdfY(normY: Float, pageY0: Float, pageHeight: Float): Float =
        pageY0 + normY.coerceIn(0f, 1f) * pageHeight

    /**
     * Normalized hit-test for the Eraser tool so tapping any annotation deletes it cleanly.
     */
    fun hitTest(annotation: PdfEditAnnotation, tapX: Float, tapY: Float, tolerance: Float = 0.045f): Boolean {
        return when (annotation) {
            is PdfEditAnnotation.Markup -> {
                val l = min(annotation.leftNorm, annotation.rightNorm) - tolerance
                val r = max(annotation.leftNorm, annotation.rightNorm) + tolerance
                val t = min(annotation.topNorm, annotation.bottomNorm) - tolerance
                val b = max(annotation.topNorm, annotation.bottomNorm) + tolerance
                tapX in l..r && tapY in t..b
            }
            is PdfEditAnnotation.StickyNote -> {
                hypot((tapX - annotation.xNorm).toDouble(), (tapY - annotation.yNorm).toDouble()) <= tolerance * 1.4
            }
            is PdfEditAnnotation.FreeText -> {
                val l = annotation.xNorm - tolerance
                val r = annotation.xNorm + annotation.widthNorm + tolerance
                val t = annotation.yNorm - tolerance
                val b = annotation.yNorm + annotation.heightNorm + tolerance
                tapX in l..r && tapY in t..b
            }
            is PdfEditAnnotation.Shape -> {
                val l = min(annotation.startXNorm, annotation.endXNorm) - tolerance
                val r = max(annotation.startXNorm, annotation.endXNorm) + tolerance
                val t = min(annotation.startYNorm, annotation.endYNorm) - tolerance
                val b = max(annotation.startYNorm, annotation.endYNorm) + tolerance
                tapX in l..r && tapY in t..b
            }
            is PdfEditAnnotation.Ink -> {
                annotation.strokes.any { stroke ->
                    stroke.any { pt ->
                        abs(pt.x - tapX) <= tolerance && abs(pt.y - tapY) <= tolerance
                    }
                }
            }
        }
    }
}
