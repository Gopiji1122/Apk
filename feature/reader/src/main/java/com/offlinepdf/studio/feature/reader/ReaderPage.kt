package com.offlinepdf.studio.feature.reader

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.offlinepdf.studio.core.pdf.PdfLink
import com.offlinepdf.studio.core.pdf.PdfRegionTile
import com.offlinepdf.studio.core.pdf.ReadingCanvasTheme
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/** Resolution used for the "base" bitmap of a page of the given on-screen width. */
internal fun baseRenderWidth(displayWidthPx: Int): Int =
    (displayWidthPx * 1.35f).roundToInt().coerceIn(640, 2000)

@Immutable
class PageEnv(
    val zoom: ReaderZoomState,
    val canvasTheme: ReadingCanvasTheme,
    val renderPage: suspend (Int, Int) -> Bitmap?,
    val renderTile: suspend (Int, Float, Float, Float, Float, Int) -> PdfRegionTile?,
    val loadLinks: suspend (Int) -> List<PdfLink>,
    val viewportBounds: () -> Rect,
    /** Changes whenever the underlying list / pager moves. */
    val scrollSignal: () -> Int,
    val isMoving: () -> Boolean,
    val onLink: (PdfLink) -> Unit
)

private val ActiveHighlight = Color(0x99FF8A00)
private val PassiveHighlight = Color(0x66FFD60A)

/**
 * Single page renderer.
 *
 *  1. fast low-res preview → 2. crisp base once scrolling settles → 3. exact-region tile at the
 *  current zoom (only what is visible), drawn at its precise normalized rectangle.
 *
 * A slower, lower-resolution pass can never replace a sharper bitmap.
 */
@OptIn(FlowPreview::class)
@Composable
internal fun PdfPageView(
    pageIndex: Int,
    env: PageEnv,
    highlights: List<HighlightBox>,
    activeMatch: Int,
    modifier: Modifier = Modifier
) {
    val theme = env.canvasTheme
    var base by remember(pageIndex, theme) { mutableStateOf<Bitmap?>(null) }
    var baseWidth by remember(pageIndex, theme) { mutableIntStateOf(0) }
    var tile by remember(pageIndex, theme) { mutableStateOf<PdfRegionTile?>(null) }
    var links by remember(pageIndex) { mutableStateOf<List<PdfLink>>(emptyList()) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    LaunchedEffect(pageIndex, theme, size.width) {
        if (size.width <= 0) return@LaunchedEffect
        val target = baseRenderWidth(size.width)
        if (base == null) {
            val fast = (target * 0.4f).roundToInt().coerceAtLeast(320)
            env.renderPage(pageIndex, fast)?.let { base = it; baseWidth = fast }
        }
        snapshotFlow { env.isMoving() }.first { !it }
        if (baseWidth < target) {
            env.renderPage(pageIndex, target)?.let { base = it; baseWidth = target }
        }
    }

    LaunchedEffect(pageIndex, base != null) {
        if (base != null && links.isEmpty()) links = env.loadLinks(pageIndex)
    }

    // Sharp region tile while zoomed in; waits for motion to settle.
    LaunchedEffect(pageIndex, theme) {
        snapshotFlow {
            val z = env.zoom
            Triple(z.scale, z.offsetX, z.offsetY).hashCode() * 31 + env.scrollSignal()
        }.debounce(150).collectLatest {
            if (env.zoom.scale < 1.35f) {
                tile = null
                return@collectLatest
            }
            snapshotFlow { env.isMoving() }.first { !it }
            val c = coords ?: return@collectLatest
            if (!c.isAttached) return@collectLatest
            val page = c.boundsInRoot()
            val vp = env.viewportBounds()
            val vis = page.intersect(vp)
            if (vis.width < 2f || vis.height < 2f || page.width < 2f || page.height < 2f) return@collectLatest
            val l = ((vis.left - page.left) / page.width).coerceIn(0f, 1f)
            val t = ((vis.top - page.top) / page.height).coerceIn(0f, 1f)
            val r = ((vis.right - page.left) / page.width).coerceIn(0f, 1f)
            val b = ((vis.bottom - page.top) / page.height).coerceIn(0f, 1f)
            env.renderTile(pageIndex, l, t, r, b, page.width.roundToInt())?.let { tile = it }
        }
    }

    val imageAlpha by animateFloatAsState(
        targetValue = if (base != null) 1f else 0f,
        animationSpec = tween(180),
        label = "page-fade"
    )

    Box(
        modifier = modifier
            .onSizeChanged { size = it }
            .onGloballyPositioned { coords = it }
            .background(if (theme == ReadingCanvasTheme.NIGHT_INVERTED) Color(0xFF14161A) else Color.White)
            .pointerInput(links) {
                if (links.isEmpty()) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                    if ((up.position - down.position).getDistance() > viewConfiguration.touchSlop * 2) return@awaitEachGesture
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    val nx = up.position.x / w
                    val ny = up.position.y / h
                    val hit = links.firstOrNull {
                        nx in it.box.leftNorm..it.box.rightNorm && ny in it.box.topNorm..it.box.bottomNorm
                    }
                    if (hit != null) {
                        up.consume()
                        env.onLink(hit)
                    }
                }
            }
    ) {
        val bmp = base
        if (bmp != null && !bmp.isRecycled) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = imageAlpha }
            )
        }
        val t = tile
        if (t != null && !t.bitmap.isRecycled) {
            val image = remember(t) { t.bitmap.asImageBitmap() }
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (env.zoom.scale >= 1.2f) {
                    val l = t.leftNorm * size.width
                    val top = t.topNorm * size.height
                    val w = (t.rightNorm - t.leftNorm) * size.width
                    val h = (t.bottomNorm - t.topNorm) * size.height
                    drawImage(
                        image = image,
                        dstOffset = IntOffset(l.roundToInt(), top.roundToInt()),
                        dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1))
                    )
                }
            }
        }
        if (highlights.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                highlights.forEach { hb ->
                    val box = hb.box
                    drawRect(
                        color = if (hb.matchIndex == activeMatch) ActiveHighlight else PassiveHighlight,
                        topLeft = Offset(box.leftNorm * size.width, box.topNorm * size.height),
                        size = Size(
                            ((box.rightNorm - box.leftNorm) * size.width).coerceAtLeast(6f),
                            ((box.bottomNorm - box.topNorm) * size.height).coerceAtLeast(6f)
                        )
                    )
                }
            }
        }
    }
}
