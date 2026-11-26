package com.offlinepdf.studio.core.pdf

object PdfGeometryHelper {

    fun normalizeBox(
        minX: Float,
        minY: Float,
        maxX: Float,
        maxY: Float,
        pageLeft: Float,
        pageTop: Float,
        pageWidth: Float,
        pageHeight: Float
    ): PdfSearchBox {
        val safeW = pageWidth.coerceAtLeast(1f)
        val safeH = pageHeight.coerceAtLeast(1f)
        return PdfSearchBox(
            leftNorm = ((minX - pageLeft) / safeW).coerceIn(0f, 1f),
            topNorm = ((minY - pageTop) / safeH).coerceIn(0f, 1f),
            rightNorm = ((maxX - pageLeft) / safeW).coerceIn(0f, 1f),
            bottomNorm = ((maxY - pageTop) / safeH).coerceIn(0f, 1f)
        )
    }

    /**
     * Rotates a normalized [0..1] box clockwise by 0, 90, 180, or 270 degrees
     * so overlays align with rotated rendered bitmaps.
     */
    fun rotateNormalizedBox(box: PdfSearchBox, rotationDegrees: Int): PdfSearchBox {
        return when (((rotationDegrees % 360) + 360) % 360) {
            90 -> PdfSearchBox(
                leftNorm = 1f - box.bottomNorm,
                topNorm = box.leftNorm,
                rightNorm = 1f - box.topNorm,
                bottomNorm = box.rightNorm
            )
            180 -> PdfSearchBox(
                leftNorm = 1f - box.rightNorm,
                topNorm = 1f - box.bottomNorm,
                rightNorm = 1f - box.leftNorm,
                bottomNorm = 1f - box.topNorm
            )
            270 -> PdfSearchBox(
                leftNorm = box.topNorm,
                topNorm = 1f - box.rightNorm,
                rightNorm = box.bottomNorm,
                bottomNorm = 1f - box.leftNorm
            )
            else -> box
        }
    }

    fun nextHitIndex(currentIndex: Int, totalHits: Int): Int {
        if (totalHits <= 0) return -1
        return (currentIndex + 1) % totalHits
    }

    fun previousHitIndex(currentIndex: Int, totalHits: Int): Int {
        if (totalHits <= 0) return -1
        return if (currentIndex <= 0) totalHits - 1 else currentIndex - 1
    }
}
