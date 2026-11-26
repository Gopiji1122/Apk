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
)

data class PdfSearchHit(
    val pageIndex: Int,
    val quadCount: Int
)

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
    fun renderPage(
        pageIndex: Int,
        targetWidthPx: Int,
        rotationDegrees: Int = 0,
        invertColors: Boolean = false
    ): Bitmap
    fun searchPage(pageIndex: Int, query: String): PdfSearchHit?
}

interface PdfEngine {
    suspend fun openDocument(localFilePath: String): PdfOpenResult
}
