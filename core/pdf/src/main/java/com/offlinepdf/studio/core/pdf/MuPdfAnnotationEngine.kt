package com.offlinepdf.studio.core.pdf

import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.PDFAnnotation
import com.artifex.mupdf.fitz.PDFDocument
import com.artifex.mupdf.fitz.PDFPage
import com.artifex.mupdf.fitz.Point
import com.artifex.mupdf.fitz.Quad
import com.artifex.mupdf.fitz.Rect
import com.offlinepdf.studio.core.common.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Writes real, standards-compliant PDF annotations into a new PDF copy using MuPDF 1.28.5
 * (PDFPage.createAnnotation + PDFAnnotation.update + PDFDocument.save).
 * Never overwrites the user's original source PDF.
 */
@Singleton
class MuPdfAnnotationEngine @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun saveAnnotatedCopy(
        sourcePdfPath: String,
        outputPdfPath: String,
        annotations: List<PdfEditAnnotation>,
        password: String? = null
    ): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            val doc = Document.openDocument(sourcePdfPath)
            val pdfDoc = doc as? PDFDocument ?: run {
                doc.destroy()
                throw IllegalStateException("Selected file is not a writable PDF document.")
            }
            try {
                if (pdfDoc.needsPassword()) {
                    val ok = !password.isNullOrEmpty() && pdfDoc.authenticatePassword(password)
                    if (!ok) throw IllegalStateException("Password required to save this PDF.")
                }

                val byPage = annotations.groupBy { it.pageIndex }
                var writtenCount = 0

                for ((pageIndex, pageAnnotations) in byPage) {
                    if (pageIndex < 0 || pageIndex >= pdfDoc.countPages()) continue
                    val page = pdfDoc.loadPage(pageIndex)
                    val pdfPage = page as? PDFPage
                    try {
                        if (pdfPage == null) continue
                        val bounds = pdfPage.bounds
                        val x0 = bounds.x0
                        val y0 = bounds.y0
                        val w = max(1f, bounds.x1 - bounds.x0)
                        val h = max(1f, bounds.y1 - bounds.y0)

                        for (item in pageAnnotations) {
                            writeAnnotationToPage(pdfPage, item, x0, y0, w, h)
                            writtenCount++
                        }
                    } finally {
                        page.destroy()
                    }
                }

                val outFile = File(outputPdfPath)
                outFile.parentFile?.mkdirs()
                pdfDoc.save(outFile.absolutePath, "garbage,compress")
                writtenCount
            } finally {
                pdfDoc.destroy()
            }
        }
    }

    private fun writeAnnotationToPage(
        page: PDFPage,
        item: PdfEditAnnotation,
        x0: Float,
        y0: Float,
        w: Float,
        h: Float
    ) {
        val rgb = PdfAnnotationCoordinateMath.argbToRgbFloats(item.colorArgb)
        when (item) {
            is PdfEditAnnotation.Markup -> {
                val type = when (item.kind) {
                    PdfMarkupKind.HIGHLIGHT -> PDFAnnotation.TYPE_HIGHLIGHT
                    PdfMarkupKind.UNDERLINE -> PDFAnnotation.TYPE_UNDERLINE
                    PdfMarkupKind.STRIKETHROUGH -> PDFAnnotation.TYPE_STRIKE_OUT
                }
                val l = PdfAnnotationCoordinateMath.toPdfX(min(item.leftNorm, item.rightNorm), x0, w)
                val r = PdfAnnotationCoordinateMath.toPdfX(max(item.leftNorm, item.rightNorm), x0, w)
                val t = PdfAnnotationCoordinateMath.toPdfY(min(item.topNorm, item.bottomNorm), y0, h)
                val b = PdfAnnotationCoordinateMath.toPdfY(max(item.topNorm, item.bottomNorm), y0, h)
                val annot = page.createAnnotation(type)
                try {
                    annot.setColor(rgb)
                    annot.setOpacity(if (item.kind == PdfMarkupKind.HIGHLIGHT) 0.42f else 0.95f)
                    annot.setQuadPoints(arrayOf(Quad(l, t, r, t, l, b, r, b)))
                    annot.setAuthor("Offline PDF Studio")
                    annot.update()
                } finally {
                    annot.destroy()
                }
            }

            is PdfEditAnnotation.Ink -> {
                val validStrokes = item.strokes.filter { it.size >= 2 }
                if (validStrokes.isEmpty()) return
                val inkArray = validStrokes.map { stroke ->
                    stroke.map { pt ->
                        Point(
                            PdfAnnotationCoordinateMath.toPdfX(pt.x, x0, w),
                            PdfAnnotationCoordinateMath.toPdfY(pt.y, y0, h)
                        )
                    }.toTypedArray()
                }.toTypedArray()

                val annot = page.createAnnotation(PDFAnnotation.TYPE_INK)
                try {
                    annot.setColor(rgb)
                    annot.setOpacity(item.opacity.coerceIn(0.1f, 1f))
                    annot.setBorderWidth(item.strokeWidthPt.coerceIn(0.8f, 18f))
                    annot.setInkList(inkArray)
                    annot.setAuthor("Offline PDF Studio")
                    annot.update()
                } finally {
                    annot.destroy()
                }
            }

            is PdfEditAnnotation.StickyNote -> {
                val px = PdfAnnotationCoordinateMath.toPdfX(item.xNorm, x0, w)
                val py = PdfAnnotationCoordinateMath.toPdfY(item.yNorm, y0, h)
                val annot = page.createAnnotation(PDFAnnotation.TYPE_TEXT)
                try {
                    annot.setRect(Rect(px - 10f, py - 10f, px + 14f, py + 14f))
                    annot.setColor(rgb)
                    annot.setIcon("Comment")
                    annot.setContents(item.text)
                    annot.setAuthor("Offline PDF Studio")
                    annot.update()
                } finally {
                    annot.destroy()
                }
            }

            is PdfEditAnnotation.FreeText -> {
                val l = PdfAnnotationCoordinateMath.toPdfX(item.xNorm, x0, w)
                val t = PdfAnnotationCoordinateMath.toPdfY(item.yNorm, y0, h)
                val r = PdfAnnotationCoordinateMath.toPdfX((item.xNorm + item.widthNorm).coerceAtMost(0.98f), x0, w)
                val b = PdfAnnotationCoordinateMath.toPdfY((item.yNorm + item.heightNorm).coerceAtMost(0.98f), y0, h)
                val annot = page.createAnnotation(PDFAnnotation.TYPE_FREE_TEXT)
                try {
                    annot.setRect(Rect(l, t, max(r, l + 40f), max(b, t + 20f)))
                    annot.setDefaultAppearance("Helv", item.fontSizePt.coerceIn(8f, 36f), rgb)
                    annot.setContents(item.text)
                    annot.setAuthor("Offline PDF Studio")
                    annot.update()
                } finally {
                    annot.destroy()
                }
            }

            is PdfEditAnnotation.Shape -> {
                val sx = PdfAnnotationCoordinateMath.toPdfX(item.startXNorm, x0, w)
                val sy = PdfAnnotationCoordinateMath.toPdfY(item.startYNorm, y0, h)
                val ex = PdfAnnotationCoordinateMath.toPdfX(item.endXNorm, x0, w)
                val ey = PdfAnnotationCoordinateMath.toPdfY(item.endYNorm, y0, h)
                val type = when (item.kind) {
                    PdfShapeKind.RECTANGLE -> PDFAnnotation.TYPE_SQUARE
                    PdfShapeKind.CIRCLE -> PDFAnnotation.TYPE_CIRCLE
                    PdfShapeKind.LINE, PdfShapeKind.ARROW -> PDFAnnotation.TYPE_LINE
                }
                val annot = page.createAnnotation(type)
                try {
                    annot.setColor(rgb)
                    annot.setBorderWidth(item.strokeWidthPt.coerceIn(1f, 12f))
                    if (item.kind == PdfShapeKind.LINE || item.kind == PdfShapeKind.ARROW) {
                        annot.setLine(Point(sx, sy), Point(ex, ey))
                        if (item.kind == PdfShapeKind.ARROW) {
                            annot.setLineEndingStyles(
                                PDFAnnotation.LINE_ENDING_NONE,
                                PDFAnnotation.LINE_ENDING_CLOSED_ARROW
                            )
                        }
                    } else {
                        annot.setRect(Rect(min(sx, ex), min(sy, ey), max(sx, ex), max(sy, ey)))
                    }
                    annot.setAuthor("Offline PDF Studio")
                    annot.update()
                } finally {
                    annot.destroy()
                }
            }
        }
    }
}
