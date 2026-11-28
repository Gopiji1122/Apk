package com.offlinepdf.studio.core.pdf

import android.graphics.Bitmap

data class PdfMetadata(
    val title: String = "",
    val author: String = "",
    val subject: String = "",
    val creator: String = "",
    val producer: String = "",
    val format: String = "PDF",
    val encryption: String = "None",
    val pageCount: Int = 0
)

data class PdfPageSize(
    val widthPt: Float,
    val heightPt: Float
) {
    val aspectRatio: Float
        get() = if (heightPt > 0f) widthPt / heightPt else 0.707f
}

/**
 * Normalized [0f..1f] rectangle in unrotated page space so search highlights
 * remain accurate across zoom levels, screen sizes, and densities.
 */
data class PdfSearchBox(
    val leftNorm: Float,
    val topNorm: Float,
    val rightNorm: Float,
    val bottomNorm: Float
)

data class PdfSearchHit(
    val pageIndex: Int,
    val boxes: List<PdfSearchBox>
) {
    val quadCount: Int get() = boxes.size
}

data class PdfOutlineEntry(
    val title: String,
    val pageIndex: Int,
    val depth: Int
)

enum class ReadingLayoutMode {
    CONTINUOUS_VERTICAL,
    SINGLE_PAGE_HORIZONTAL,
    TWO_PAGE_SPREAD
}

enum class ZoomPreset(val scaleMultiplier: Float) {
    FIT_WIDTH(1.0f),
    FIT_PAGE(0.85f),
    ACTUAL_SIZE(1.6f)
}

enum class ReadingCanvasTheme {
    LIGHT,
    DARK_CANVAS,
    NIGHT_INVERTED
}

sealed interface PdfOpenResult {
    data class Opened(val handle: PdfDocumentHandle, val metadata: PdfMetadata) : PdfOpenResult
    data class PasswordRequired(val handle: PdfDocumentHandle) : PdfOpenResult
    data class Error(val message: String, val cause: Throwable? = null) : PdfOpenResult
}

interface PdfDocumentHandle : AutoCloseable {
    val pageCount: Int
    val isPasswordProtected: Boolean
    fun authenticate(password: String): Boolean
    fun getMetadata(): PdfMetadata
    fun getPageSize(pageIndex: Int): PdfPageSize
    fun getOutline(): List<PdfOutlineEntry>
    fun renderPage(
        pageIndex: Int,
        targetWidthPx: Int,
        rotationDegrees: Int = 0,
        invertColors: Boolean = false
    ): Bitmap
    fun renderThumbnail(pageIndex: Int, targetWidthPx: Int = 220): Bitmap
    fun renderRegion(
        pageIndex: Int,
        leftNorm: Float,
        topNorm: Float,
        rightNorm: Float,
        bottomNorm: Float,
        zoomedFullPageWidthPx: Int,
        invertColors: Boolean = false
    ): Bitmap
    fun searchPage(pageIndex: Int, query: String): PdfSearchHit?
    fun extractPageText(pageIndex: Int): String
}

interface PdfEngine {
    suspend fun openDocument(localFilePath: String): PdfOpenResult
}
