package com.offlinepdf.studio.feature.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachPointerEventScope
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinepdf.studio.core.pdf.PdfGeometryHelper
import com.offlinepdf.studio.core.pdf.PdfSearchBox
import com.offlinepdf.studio.core.pdf.ReadingCanvasTheme
import com.offlinepdf.studio.core.pdf.ReadingLayoutMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    uriString: String,
    onBack: () -> Unit,
    viewModel: PdfReaderViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val verticalListState = rememberLazyListState()
    val spreadListState = rememberLazyListState()
    val pagerState = rememberPagerState(
        initialPage = state.currentPage.coerceAtLeast(0),
        pageCount = { state.pageCount.coerceAtLeast(1) }
    )

    var showInfoDialog by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showThumbnailsSheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showOutlineSheet by remember { mutableStateOf(false) }
    var showGoToPageDialog by remember { mutableStateOf(false) }
    var goToPageInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }

    // Smooth pinch-to-zoom & pan state
    val scaleAnim = remember { Animatable(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    // Premium auto-hiding vertical scrollbar visibility
    var isDraggingScrollbar by remember { mutableStateOf(false) }
    var scrollbarVisible by remember { mutableStateOf(false) }

    val isScrolling by remember {
        derivedStateOf {
            verticalListState.isScrollInProgress ||
                spreadListState.isScrollInProgress ||
                pagerState.isScrollInProgress
        }
    }

    LaunchedEffect(isScrolling, isDraggingScrollbar) {
        if (isScrolling || isDraggingScrollbar) {
            scrollbarVisible = true
        } else {
            delay(1600)
            scrollbarVisible = false
        }
    }

    fun clampOffsets(newScale: Float, rawX: Float, rawY: Float): Pair<Float, Float> {
        if (newScale <= 1.01f || viewportSize.width == 0 || viewportSize.height == 0) {
            return 0f to 0f
        }
        val maxX = (viewportSize.width * (newScale - 1f)) / 2f
        val maxY = (viewportSize.height * (newScale - 1f)) / 2f
        return rawX.coerceIn(-maxX, maxX) to rawY.coerceIn(-maxY, maxY)
    }

    fun jumpToPage(pageIndex: Int) {
        val target = pageIndex.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
        viewModel.onPageChanged(target)
        scope.launch {
            when (state.layoutMode) {
                ReadingLayoutMode.CONTINUOUS_VERTICAL -> verticalListState.scrollToItem(target)
                ReadingLayoutMode.SINGLE_PAGE_HORIZONTAL -> pagerState.scrollToPage(target)
                ReadingLayoutMode.TWO_PAGE_SPREAD -> spreadListState.scrollToItem(target / 2)
            }
        }
    }

    LaunchedEffect(uriString) {
        viewModel.openUri(uriString)
    }

    LaunchedEffect(state.pageCount, state.initialResumePage) {
        if (state.pageCount > 0 && state.initialResumePage > 0) {
            jumpToPage(state.initialResumePage)
        }
    }

    LaunchedEffect(verticalListState.firstVisibleItemIndex, state.layoutMode, state.pageCount) {
        if (state.layoutMode == ReadingLayoutMode.CONTINUOUS_VERTICAL && state.pageCount > 0) {
            viewModel.onPageChanged(verticalListState.firstVisibleItemIndex)
        }
    }
    LaunchedEffect(pagerState.currentPage, state.layoutMode, state.pageCount) {
        if (state.layoutMode == ReadingLayoutMode.SINGLE_PAGE_HORIZONTAL && state.pageCount > 0) {
            viewModel.onPageChanged(pagerState.currentPage)
        }
    }
    LaunchedEffect(spreadListState.firstVisibleItemIndex, state.layoutMode, state.pageCount) {
        if (state.layoutMode == ReadingLayoutMode.TWO_PAGE_SPREAD && state.pageCount > 0) {
            viewModel.onPageChanged((spreadListState.firstVisibleItemIndex * 2).coerceAtMost(state.pageCount - 1))
        }
    }

    val canvasBackground = when (state.canvasTheme) {
        ReadingCanvasTheme.LIGHT -> MaterialTheme.colorScheme.surfaceVariant
        ReadingCanvasTheme.DARK_CANVAS -> Color(0xFF141820)
        ReadingCanvasTheme.NIGHT_INVERTED -> Color(0xFF07090D)
    }

    // Full-screen edge-to-edge Box: PDF occupies 100% of screen; TopBar floats cleanly above and auto-hides on tap
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(canvasBackground)
            .onSizeChanged { viewportSize = it }
    ) {
        // 1. Main Full-Screen PDF Canvas with buttery-smooth custom pinch-zoom & pan
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { viewModel.toggleControlsVisibility() },
                        onDoubleTap = { tapOffset ->
                            scope.launch {
                                if (scaleAnim.value > 1.25f) {
                                    offsetX = 0f
                                    offsetY = 0f
                                    scaleAnim.animateTo(
                                        1f,
                                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                                    )
                                } else {
                                    val targetScale = 2.4f
                                    val centerX = viewportSize.width / 2f
                                    val centerY = viewportSize.height / 2f
                                    val desiredX = (centerX - tapOffset.x) * (targetScale - 1f)
                                    val desiredY = (centerY - tapOffset.y) * (targetScale - 1f)
                                    val (cx, cy) = clampOffsets(targetScale, desiredX, desiredY)
                                    offsetX = cx
                                    offsetY = cy
                                    scaleAnim.animateTo(
                                        targetScale,
                                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                                    )
                                }
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    awaitEachPointerEventScope {
                        while (true) {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val activePointers = event.changes.count { it.pressed }
                                if (activePointers >= 2) {
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    val centroid = event.calculateCentroid(useCurrent = true)
                                    val oldScale = scaleAnim.value
                                    val newScale = (oldScale * zoomChange).coerceIn(1f, 5f)
                                    val scaleFactor = if (oldScale > 0f) newScale / oldScale else 1f

                                    val centerX = viewportSize.width / 2f
                                    val centerY = viewportSize.height / 2f
                                    val focalX = centroid.x - centerX
                                    val focalY = centroid.y - centerY

                                    val rawX = (offsetX - focalX) * scaleFactor + focalX + panChange.x
                                    val rawY = (offsetY - focalY) * scaleFactor + focalY + panChange.y
                                    val (cx, cy) = clampOffsets(newScale, rawX, rawY)

                                    scope.launch { scaleAnim.snapTo(newScale) }
                                    offsetX = cx
                                    offsetY = cy

                                    event.changes.forEach { change ->
                                        if (change.positionChanged()) change.consume()
                                    }
                                } else if (activePointers == 1 && scaleAnim.value > 1.03f) {
                                    // Smooth horizontal + vertical pan when zoomed in, while still
                                    // allowing vertical LazyColumn scrolling if user reaches top/bottom edge
                                    val panChange = event.calculatePan()
                                    val maxX = (viewportSize.width * (scaleAnim.value - 1f)) / 2f
                                    val maxY = (viewportSize.height * (scaleAnim.value - 1f)) / 2f
                                    val nextX = (offsetX + panChange.x).coerceIn(-maxX, maxX)
                                    val nextY = (offsetY + panChange.y).coerceIn(-maxY, maxY)
                                    val consumedHoriz = abs(nextX - offsetX) > 0.5f
                                    val consumedVert = abs(nextY - offsetY) > 0.5f
                                    offsetX = nextX
                                    offsetY = nextY
                                    if (consumedHoriz || consumedVert) {
                                        event.changes.forEach { change ->
                                            if (change.positionChanged()) change.consume()
                                        }
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            when {
                state.isLoading -> CircularProgressIndicator()
                state.errorMessage != null -> {
                    Text(
                        text = state.errorMessage ?: "Error loading PDF",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(24.dp)
                    )
                }
                state.pageCount > 0 -> {
                    val screenWidthPx = with(LocalDensity.current) {
                        LocalConfiguration.current.screenWidthDp.dp.toPx().toInt()
                    }
                    // Sharper high-DPI rendering for crisp pinch-to-zoom text
                    val renderWidth = (screenWidthPx * 1.8f).toInt().coerceAtMost(2200)
                    val currentScale = scaleAnim.value
                    val zoomModifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = currentScale,
                            scaleY = currentScale,
                            translationX = offsetX,
                            translationY = offsetY
                        )

                    when (state.layoutMode) {
                        ReadingLayoutMode.CONTINUOUS_VERTICAL -> {
                            LazyColumn(
                                state = verticalListState,
                                modifier = zoomModifier,
                                contentPadding = PaddingValues(
                                    top = if (state.controlsVisible) 88.dp else 8.dp,
                                    bottom = 24.dp,
                                    start = 4.dp,
                                    end = 4.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(state.pageCount, key = { it }) { pageIndex ->
                                    PdfPageTile(
                                        pageIndex = pageIndex,
                                        renderWidthPx = renderWidth,
                                        rotationDegrees = state.rotationDegrees,
                                        canvasTheme = state.canvasTheme,
                                        highlightBoxes = state.searchBoxesByPage[pageIndex].orEmpty(),
                                        renderProvider = { viewModel.renderPage(pageIndex, it) }
                                    )
                                }
                            }
                        }
                        ReadingLayoutMode.SINGLE_PAGE_HORIZONTAL -> {
                            HorizontalPager(
                                state = pagerState,
                                modifier = zoomModifier,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                pageSpacing = 8.dp
                            ) { pageIndex ->
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    PdfPageTile(
                                        pageIndex = pageIndex,
                                        renderWidthPx = renderWidth,
                                        rotationDegrees = state.rotationDegrees,
                                        canvasTheme = state.canvasTheme,
                                        highlightBoxes = state.searchBoxesByPage[pageIndex].orEmpty(),
                                        renderProvider = { viewModel.renderPage(pageIndex, it) }
                                    )
                                }
                            }
                        }
                        ReadingLayoutMode.TWO_PAGE_SPREAD -> {
                            val spreadCount = (state.pageCount + 1) / 2
                            LazyColumn(
                                state = spreadListState,
                                modifier = zoomModifier,
                                contentPadding = PaddingValues(
                                    top = if (state.controlsVisible) 88.dp else 8.dp,
                                    bottom = 24.dp,
                                    start = 4.dp,
                                    end = 4.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(spreadCount, key = { it }) { spreadIdx ->
                                    val leftPage = spreadIdx * 2
                                    val rightPage = leftPage + 1
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            PdfPageTile(
                                                pageIndex = leftPage,
                                                renderWidthPx = renderWidth / 2,
                                                rotationDegrees = state.rotationDegrees,
                                                canvasTheme = state.canvasTheme,
                                                highlightBoxes = state.searchBoxesByPage[leftPage].orEmpty(),
                                                renderProvider = { viewModel.renderPage(leftPage, it) }
                                            )
                                        }
                                        Box(modifier = Modifier.weight(1f)) {
                                            if (rightPage < state.pageCount) {
                                                PdfPageTile(
                                                    pageIndex = rightPage,
                                                    renderWidthPx = renderWidth / 2,
                                                    rotationDegrees = state.rotationDegrees,
                                                    canvasTheme = state.canvasTheme,
                                                    highlightBoxes = state.searchBoxesByPage[rightPage].orEmpty(),
                                                    renderProvider = { viewModel.renderPage(rightPage, it) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Premium Google Drive–Style Vertical Fast-Scroll Handle (appears ONLY while scrolling/dragging)
        if (state.pageCount > 1) {
            PremiumVerticalPageScrubber(
                visible = scrollbarVisible || isDraggingScrollbar,
                currentPage = state.currentPage,
                pageCount = state.pageCount,
                isDragging = isDraggingScrollbar,
                onDragStateChanged = { dragging -> isDraggingScrollbar = dragging },
                onScrubToPage = { target -> jumpToPage(target) },
                onTapPageBubble = {
                    goToPageInput = (state.currentPage + 1).toString()
                    showGoToPageDialog = true
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(top = 80.dp, bottom = 28.dp)
            )
        }

        // 3. Floating Top App Bar + Search Bar (toggles smoothly on single tap for true full-screen reading)
        AnimatedVisibility(
            visible = state.controlsVisible,
            enter = slideInVertically { -it } + fadeIn(tween(180)),
            exit = slideOutVertically { -it } + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            ) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    title = {
                        Column {
                            Text(
                                text = state.documentTitle.ifBlank { "PDF Reader" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (state.pageCount > 0) {
                                Text(
                                    text = "Page ${state.currentPage + 1} of ${state.pageCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSearchBar = !showSearchBar }) {
                            Icon(Icons.Outlined.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = { showThumbnailsSheet = true }) {
                            Icon(Icons.Outlined.GridView, contentDescription = "Page thumbnails")
                        }
                        IconButton(onClick = viewModel::toggleCurrentPageBookmark) {
                            val bookmarked = state.bookmarkedPages.contains(state.currentPage)
                            Icon(
                                imageVector = if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Bookmark page"
                            )
                        }
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Reader options")
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Go to Page...") },
                                    leadingIcon = { Icon(Icons.Outlined.Tag, null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        goToPageInput = (state.currentPage + 1).toString()
                                        showGoToPageDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Continuous Vertical") },
                                    leadingIcon = { Icon(Icons.Outlined.ViewDay, null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.setLayoutMode(ReadingLayoutMode.CONTINUOUS_VERTICAL)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Single Page (Horizontal)") },
                                    leadingIcon = { Icon(Icons.Outlined.ViewCarousel, null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.setLayoutMode(ReadingLayoutMode.SINGLE_PAGE_HORIZONTAL)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Two-Page Spread") },
                                    leadingIcon = { Icon(Icons.Outlined.ViewColumn, null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.setLayoutMode(ReadingLayoutMode.TWO_PAGE_SPREAD)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Select / Copy Page Text") },
                                    leadingIcon = { Icon(Icons.Outlined.TextFields, null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.loadCurrentPageText()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Bookmarks (${state.bookmarks.size})") },
                                    leadingIcon = { Icon(Icons.Outlined.Bookmarks, null) },
                                    onClick = { showOverflowMenu = false; showBookmarksSheet = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Table of Contents (${state.outline.size})") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.List, null) },
                                    onClick = { showOverflowMenu = false; showOutlineSheet = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Rotate 90°") },
                                    leadingIcon = { Icon(Icons.Outlined.RotateRight, null) },
                                    onClick = { showOverflowMenu = false; viewModel.rotateClockwise() }
                                )
                                DropdownMenuItem(
                                    text = {
                                        val label = when (state.canvasTheme) {
                                            ReadingCanvasTheme.LIGHT -> "Theme: Light → Dark Canvas"
                                            ReadingCanvasTheme.DARK_CANVAS -> "Theme: Dark Canvas → Night Invert"
                                            ReadingCanvasTheme.NIGHT_INVERTED -> "Theme: Night Invert → Light"
                                        }
                                        Text(label)
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.DarkMode, null) },
                                    onClick = { showOverflowMenu = false; viewModel.cycleCanvasTheme() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Document Info") },
                                    leadingIcon = { Icon(Icons.Outlined.Info, null) },
                                    onClick = { showOverflowMenu = false; showInfoDialog = true }
                                )
                            }
                        }
                    }
                )

                AnimatedVisibility(visible = showSearchBar) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = viewModel::searchText,
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("Search text in PDF...") }
                        )
                        if (state.searchHits.isNotEmpty()) {
                            Text(
                                text = "${state.activeHitIndex + 1}/${state.searchHits.size}",
                                style = MaterialTheme.typography.labelMedium
                            )
                            IconButton(onClick = { viewModel.selectPreviousHit()?.let(::jumpToPage) }) {
                                Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Previous match")
                            }
                            IconButton(onClick = { viewModel.selectNextHit()?.let(::jumpToPage) }) {
                                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Next match")
                            }
                        }
                    }
                }
            }
        }
    }

    // Thumbnails Bottom Sheet
    if (showThumbnailsSheet && state.pageCount > 0) {
        ModalBottomSheet(onDismissRequest = { showThumbnailsSheet = false }) {
            Text(
                text = "Page Thumbnails (${state.pageCount})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.height(420.dp)
            ) {
                items(state.pageCount, key = { "thumb_$it" }) { idx ->
                    ThumbnailGridItem(
                        pageIndex = idx,
                        isSelected = idx == state.currentPage,
                        renderThumbnail = { viewModel.renderThumbnail(idx) },
                        onClick = {
                            showThumbnailsSheet = false
                            jumpToPage(idx)
                        }
                    )
                }
            }
        }
    }

    // Bookmarks Bottom Sheet
    if (showBookmarksSheet) {
        ModalBottomSheet(onDismissRequest = { showBookmarksSheet = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Bookmarks", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (state.bookmarks.isEmpty()) {
                    Text(
                        "No bookmarks yet. Tap the bookmark icon in the top bar to save a page.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.height(300.dp)) {
                        items(state.bookmarks, key = { it.pageIndex }) { bm ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showBookmarksSheet = false
                                        jumpToPage(bm.pageIndex)
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(bm.label, modifier = Modifier.weight(1f))
                                IconButton(onClick = { viewModel.deleteBookmark(bm.pageIndex) }) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete bookmark")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Table of Contents Bottom Sheet
    if (showOutlineSheet) {
        ModalBottomSheet(onDismissRequest = { showOutlineSheet = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Table of Contents", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (state.outline.isEmpty()) {
                    Text(
                        "This PDF does not contain an embedded table of contents.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.height(340.dp)) {
                        items(state.outline) { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showOutlineSheet = false
                                        jumpToPage(entry.pageIndex)
                                    }
                                    .padding(start = (entry.depth * 14).dp, top = 8.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = entry.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "p. ${entry.pageIndex + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Selectable / Copy Page Text Dialog
    if (state.isExtractingText || state.extractedPageText != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissExtractedText,
            title = { Text("Page ${state.currentPage + 1} Text") },
            text = {
                if (state.isExtractingText) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    SelectionContainer {
                        Text(
                            text = state.extractedPageText.orEmpty(),
                            modifier = Modifier
                                .height(300.dp)
                                .verticalScroll(rememberScrollState()),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val txt = state.extractedPageText.orEmpty()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("PDF Page Text", txt))
                        viewModel.dismissExtractedText()
                    }
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Copy All")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissExtractedText) { Text("Close") }
            }
        )
    }

    // Go To Page Dialog
    if (showGoToPageDialog) {
        AlertDialog(
            onDismissRequest = { showGoToPageDialog = false },
            title = { Text("Go to Page (1–${state.pageCount})") },
            text = {
                OutlinedTextField(
                    value = goToPageInput,
                    onValueChange = { goToPageInput = it.filter(Char::isDigit) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("Page number") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val num = goToPageInput.toIntOrNull()
                    if (num != null && num in 1..state.pageCount) {
                        jumpToPage(num - 1)
                    }
                    showGoToPageDialog = false
                }) { Text("Go") }
            },
            dismissButton = {
                TextButton(onClick = { showGoToPageDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Password Dialog
    if (state.needsPassword) {
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text("Password Protected PDF") },
            text = {
                Column {
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text("Document Password") }
                    )
                    state.passwordError?.let { err ->
                        Spacer(Modifier.height(6.dp))
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.submitPassword(passwordInput) }) { Text("Unlock") }
            },
            dismissButton = {
                TextButton(onClick = onBack) { Text("Cancel") }
            }
        )
    }

    // Document Metadata Dialog
    if (showInfoDialog) {
        val m = state.metadata
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("Document Information") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Title: ${m.title.ifBlank { state.documentTitle }}")
                    Text("Pages: ${m.pageCount}")
                    Text("Format: ${m.format}")
                    Text("Encryption: ${m.encryption}")
                    if (m.author.isNotBlank()) Text("Author: ${m.author}")
                    if (m.creator.isNotBlank()) Text("Creator: ${m.creator}")
                    if (m.producer.isNotBlank()) Text("Producer: ${m.producer}")
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) { Text("Close") }
            }
        )
    }
}

/**
 * Premium Google Drive–style vertical fast-scroll handle with floating page indicator pill.
 * Only appears while scrolling or dragging, and smoothly fades out when idle.
 */
@Composable
private fun PremiumVerticalPageScrubber(
    visible: Boolean,
    currentPage: Int,
    pageCount: Int,
    isDragging: Boolean,
    onDragStateChanged: (Boolean) -> Unit,
    onScrubToPage: (Int) -> Unit,
    onTapPageBubble: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(320)),
        modifier = modifier
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxHeight()
                .width(124.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            val density = LocalDensity.current
            val thumbHeightDp = 46.dp
            val thumbHeightPx = with(density) { thumbHeightDp.toPx() }
            val trackHeightPx = (constraints.maxHeight.toFloat() - thumbHeightPx).coerceAtLeast(1f)

            val fraction = if (pageCount > 1) {
                currentPage.toFloat() / (pageCount - 1).toFloat()
            } else 0f

            var dragFraction by remember { mutableFloatStateOf(fraction) }
            LaunchedEffect(fraction, isDragging) {
                if (!isDragging) dragFraction = fraction
            }

            val effectiveFraction = if (isDragging) dragFraction else fraction
            val thumbOffsetYPx = (effectiveFraction * trackHeightPx).roundToInt()

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .offset { IntOffset(0, thumbOffsetYPx) }
                    .padding(end = 2.dp)
            ) {
                // Floating page indicator bubble (e.g. "3 / 6")
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clickable(onClick = onTapPageBubble)
                ) {
                    Text(
                        text = "${currentPage + 1} / $pageCount",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                // Sleek right-edge vertical drag handle
                Surface(
                    shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .width(34.dp)
                        .height(thumbHeightDp)
                        .shadow(6.dp, RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                        .pointerInput(pageCount, trackHeightPx) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    onDragStateChanged(true)
                                    dragFraction = fraction
                                },
                                onDragEnd = { onDragStateChanged(false) },
                                onDragCancel = { onDragStateChanged(false) },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    val next = (dragFraction + (dragAmount / trackHeightPx)).coerceIn(0f, 1f)
                                    dragFraction = next
                                    val targetPage = (next * (pageCount - 1)).roundToInt()
                                    onScrubToPage(targetPage)
                                }
                            )
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.UnfoldMore,
                            contentDescription = "Scroll pages"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPageTile(
    pageIndex: Int,
    renderWidthPx: Int,
    rotationDegrees: Int,
    canvasTheme: ReadingCanvasTheme,
    highlightBoxes: List<PdfSearchBox>,
    renderProvider: suspend (Int) -> Bitmap?
) {
    var bitmap by remember(pageIndex, rotationDegrees, canvasTheme) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex, renderWidthPx, rotationDegrees, canvasTheme) {
        bitmap = renderProvider(renderWidthPx)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shadowElevation = 2.dp
    ) {
        val bmp = bitmap
        if (bmp != null && !bmp.isRecycled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat())
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "PDF Page ${pageIndex + 1}",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
                if (highlightBoxes.isNotEmpty()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        highlightBoxes.forEach { rawBox ->
                            val box = PdfGeometryHelper.rotateNormalizedBox(rawBox, rotationDegrees)
                            val left = box.leftNorm * size.width
                            val top = box.topNorm * size.height
                            val width = ((box.rightNorm - box.leftNorm) * size.width).coerceAtLeast(6f)
                            val height = ((box.bottomNorm - box.topNorm) * size.height).coerceAtLeast(6f)
                            drawRect(
                                color = Color(0x66FFCA28),
                                topLeft = Offset(left, top),
                                size = Size(width, height)
                            )
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun ThumbnailGridItem(
    pageIndex: Int,
    isSelected: Boolean,
    renderThumbnail: suspend () -> Bitmap?,
    onClick: () -> Unit
) {
    var thumb by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(pageIndex) { thumb = renderThumbnail() }

    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.small
            )
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val bmp = thumb
        if (bmp != null && !bmp.isRecycled) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Thumbnail page ${pageIndex + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Page ${pageIndex + 1}", style = MaterialTheme.typography.labelSmall)
    }
}
