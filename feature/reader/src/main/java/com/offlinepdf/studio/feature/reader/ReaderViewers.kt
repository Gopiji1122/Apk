package com.offlinepdf.studio.feature.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.min

/** Cover-alone spread maths: spread 0 = [0], spread k>=1 = [2k-1, 2k]. */
object SpreadMath {
    fun spreadCount(pageCount: Int): Int = if (pageCount <= 0) 0 else 1 + (pageCount) / 2
    fun spreadOf(page: Int): Int = if (page <= 0) 0 else (page + 1) / 2
    fun firstPageOf(spread: Int): Int = if (spread <= 0) 0 else 2 * spread - 1
    fun pagesOf(spread: Int, pageCount: Int): List<Int> =
        if (spread <= 0) listOf(0) else listOf(2 * spread - 1, 2 * spread).filter { it < pageCount }
}

/** Lets the screen (scrubber, search, sheets) drive whichever viewer is currently on screen. */
class ReaderController {
    /** Pending programmatic pager jump (-1 = none); consumed by [PagedViewer]. */
    var pendingJump by mutableIntStateOf(-1)
    var jumpTo: (page: Int, animate: Boolean) -> Unit = { _, _ -> }
    /** Scroll offset signal used to re-trigger sharp tiles. */
    var scrollSignal: () -> Int = { 0 }
    var isScrollInProgress: () -> Boolean = { false }
    /** Vertical scroller used while zoomed in continuous mode (content px, returns consumed). */
    var scroller: ((Float) -> Float)? = null
}

@Composable
internal fun ContinuousViewer(
    pageCount: Int,
    initialPage: Int,
    aspectOf: (Int) -> Float,
    pageWidthFraction: Float,
    contentPadding: PaddingValues,
    controller: ReaderController,
    userScrollEnabled: Boolean,
    onCurrentPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
    pageContent: @Composable (pageIndex: Int, modifier: Modifier) -> Unit
) {
    val listState: LazyListState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)

    DisposableEffect(listState) {
        controller.jumpTo = { page, _ -> listState.requestScrollToItem(page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))) }
        controller.scrollSignal = { listState.firstVisibleItemIndex * 100_000 + listState.firstVisibleItemScrollOffset }
        controller.isScrollInProgress = { listState.isScrollInProgress }
        controller.scroller = { delta -> listState.dispatchRawDelta(delta) }
        onDispose {
            controller.scroller = null
            controller.isScrollInProgress = { false }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.firstOrNull { center >= it.offset && center < it.offset + it.size }?.index
                ?: listState.firstVisibleItemIndex
        }.distinctUntilChanged().collect { onCurrentPage(it) }
    }

    LazyColumn(
        state = listState,
        userScrollEnabled = userScrollEnabled,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(pageCount, key = { it }) { index ->
            pageContent(
                index,
                Modifier
                    .fillMaxWidth(pageWidthFraction)
                    .aspectRatio(aspectOf(index).coerceAtLeast(0.05f))
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PagedViewer(
    pageCount: Int,
    initialPage: Int,
    spread: Boolean,
    aspectOf: (Int) -> Float,
    controller: ReaderController,
    userScrollEnabled: Boolean,
    onCurrentPage: (Int) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    pageContent: @Composable (pageIndex: Int, modifier: Modifier) -> Unit
) {
    val total = if (spread) SpreadMath.spreadCount(pageCount) else pageCount
    val startIndex = if (spread) SpreadMath.spreadOf(initialPage) else initialPage
    val pagerState: PagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, (total - 1).coerceAtLeast(0))
    ) { total }

    DisposableEffect(pagerState, spread) {
        controller.jumpTo = { page, _ ->
            val target = (if (spread) SpreadMath.spreadOf(page) else page).coerceIn(0, (total - 1).coerceAtLeast(0))
            controller.pendingJump = target
        }
        controller.scrollSignal = { pagerState.currentPage * 1000 + (pagerState.currentPageOffsetFraction * 100).toInt() }
        controller.isScrollInProgress = { pagerState.isScrollInProgress }
        controller.scroller = null
        onDispose { controller.isScrollInProgress = { false } }
    }
    // Perform requested jumps from a coroutine (scrollToPage suspends).
    val jump = controller.pendingJump
    LaunchedEffect(jump, pagerState) {
        if (jump >= 0) {
            pagerState.scrollToPage(jump)
            controller.pendingJump = -1
        }
    }

    LaunchedEffect(pagerState, spread) {
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect {
            onCurrentPage(if (spread) SpreadMath.firstPageOf(it) else it)
        }
    }

    HorizontalPager(
        state = pagerState,
        userScrollEnabled = userScrollEnabled,
        beyondViewportPageCount = 1,
        pageSpacing = 10.dp,
        key = { it },
        modifier = modifier
    ) { p ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!spread) {
                val ar = aspectOf(p).coerceAtLeast(0.05f)
                val w = fitWidth(maxWidth, maxHeight, ar)
                pageContent(p, Modifier.width(w).aspectRatio(ar))
            } else {
                val pages = SpreadMath.pagesOf(p, pageCount)
                val a0 = aspectOf(pages.first()).coerceAtLeast(0.05f)
                val a1 = if (pages.size > 1) aspectOf(pages[1]).coerceAtLeast(0.05f) else a0
                // Common height for the pair; a lone page is sized as if its sibling existed.
                val h = min(maxHeight.value, maxWidth.value / (a0 + a1)).dp
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    pages.forEach { idx ->
                        val ar = aspectOf(idx).coerceAtLeast(0.05f)
                        pageContent(idx, Modifier.width(h * ar).aspectRatio(ar))
                    }
                }
            }
        }
    }
}

private fun fitWidth(maxW: Dp, maxH: Dp, aspect: Float): Dp = min(maxW.value, maxH.value * aspect).dp
