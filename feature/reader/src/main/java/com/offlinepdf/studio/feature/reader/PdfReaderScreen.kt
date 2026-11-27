package com.offlinepdf.studio.feature.reader

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinepdf.studio.core.common.Formatters
import com.offlinepdf.studio.core.pdf.PdfSearchBox
import com.offlinepdf.studio.core.pdf.ReadingCanvasTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    uriString: String,
    onBack: () -> Unit,
    onOpenEditor: (String) -> Unit,
    viewModel: PdfReaderViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var isSearchMode by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showThumbnailsSheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }

    // Smooth pinch-zoom & pan state (increased max zoom to 7.0x)
    val scaleAnim = remember { Animatable(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    // Google Drive-style right-side scrubber visibility (auto-hides after 2.4s)
    var isDraggingScrollbar by remember { mutableStateOf(false) }
    var scrollbarVisible by remember { mutableStateOf(false) }

    val isScrolling by remember { derivedStateOf { listState.isScrollInProgress } }

    // SAF Save / Download launcher
    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        if (destUri != null) {
            viewModel.exportToDestination(destUri)
        }
    }

    LaunchedEffect(state.statusToast) {
        val msg = state.statusToast ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        viewModel.clearStatusToast()
    }

    LaunchedEffect(isScrolling, isDraggingScrollbar) {
        if (isScrolling || isDraggingScrollbar) {
            scrollbarVisible = true
        } else {
            delay(2400)
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
        scope.launch { listState.scrollToItem(target) }
    }

    LaunchedEffect(uriString) {
        viewModel.openUri(uriString)
    }

    LaunchedEffect(state.pageCount, state.initialResumePage) {
        if (state.pageCount > 0 && state.initialResumePage > 0) {
            jumpToPage(state.initialResumePage)
        }
    }

    val prefetchWidthPx = with(LocalDensity.current) {
        (LocalConfiguration.current.screenWidthDp.dp.toPx() * 1.45f).toInt().coerceIn(800, 1600)
    }
    LaunchedEffect(listState.firstVisibleItemIndex, state.pageCount) {
        if (state.pageCount > 0) {
            val idx = listState.firstVisibleItemIndex
            viewModel.onPageChanged(idx)
            viewModel.schedulePredictivePrefetch(idx, prefetchWidthPx)
        }
    }

    val canvasBackground = when (state.canvasTheme) {
        ReadingCanvasTheme.LIGHT -> Color(0xFFE5E7EB)
        ReadingCanvasTheme.DARK_CANVAS -> Color(0xFF101216)
        ReadingCanvasTheme.NIGHT_INVERTED -> Color(0xFF07080A)
    }

    val headerBg = Color(0xF214161C)
    val headerFg = Color(0xFFF3F4F6)
    val isCurrentBookmarked = state.bookmarkedPages.contains(state.currentPage)

    // Root full-screen Box: PDF viewport NEVER resizes when header hides/shows -> zero layout lag!
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(canvasBackground)
            .onSizeChanged { viewportSize = it }
    ) {
        // 1. Full-Screen Edge-to-Edge PDF Canvas
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (!isSearchMode) {
                                viewModel.toggleControlsVisibility()
                            }
                        },
                        onDoubleTap = { tapOffset ->
                            scope.launch {
                                val startScale = scaleAnim.value
                                val startX = offsetX
                                val startY = offsetY
                                val target = if (startScale > 1.35f) 1.0f else 1.85f
                                val (endX, endY) = if (target <= 1.01f) {
                                    0f to 0f
                                } else {
                                    val cx = (viewportSize.width / 2f - tapOffset.x) * (target - 1f)
                                    val cy = (viewportSize.height / 2f - tapOffset.y) * (target - 1f)
                                    clampOffsets(target, cx, cy)
                                }
                                scaleAnim.animateTo(target, tween(240, easing = FastOutSlowInEasing)) {
                                    val frac = if (kotlin.math.abs(target - startScale) > 0.001f) {
                                        ((value - startScale) / (target - startScale)).coerceIn(0f, 1f)
                                    } else 1f
                                    offsetX = startX + (endX - startX) * frac
                                    offsetY = startY + (endY - startY) * frac
                                }
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val activePointers = event.changes.count { it.pressed }
                                if (activePointers >= 2) {
                                    val rawZoom = event.calculateZoom()
                                    // Smoothly damp noisy single-frame finger spikes so pinch never jumps suddenly
                                    val zoomChange = if (rawZoom.isFinite()) rawZoom.coerceIn(0.90f, 1.12f) else 1f
                                    val panChange = event.calculatePan()
                                    val centroid = event.calculateCentroid(useCurrent = true)
                                    val oldScale = scaleAnim.value
                                    val newScale = (oldScale * zoomChange).coerceIn(1f, 5.0f)
                                    val factor = if (oldScale > 0f) newScale / oldScale else 1f

                                    val focalX = centroid.x - viewportSize.width / 2f
                                    val focalY = centroid.y - viewportSize.height / 2f
                                    val rawX = (offsetX - focalX) * factor + focalX + panChange.x
                                    val rawY = (offsetY - focalY) * factor + focalY + panChange.y
                                    val (cx, cy) = clampOffsets(newScale, rawX, rawY)

                                    scope.launch { scaleAnim.snapTo(newScale) }
                                    offsetX = cx
                                    offsetY = cy

                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                } else if (activePointers == 1 && scaleAnim.value > 1.03f) {
                                    val panChange = event.calculatePan()
                                    val maxX = (viewportSize.width * (scaleAnim.value - 1f)) / 2f
                                    val maxY = (viewportSize.height * (scaleAnim.value - 1f)) / 2f
                                    val nextX = (offsetX + panChange.x).coerceIn(-maxX, maxX)
                                    val nextY = (offsetY + panChange.y).coerceIn(-maxY, maxY)
                                    val movedX = abs(nextX - offsetX) > 0.5f
                                    val movedY = abs(nextY - offsetY) > 0.5f
                                    offsetX = nextX
                                    offsetY = nextY
                                    if (movedX || movedY) {
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
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
                    // Smoothness-first two-stage resolution with slightly sharper crisp cap for 7x zoom
                    val crispWidthPx = (screenWidthPx * 1.5f).toInt().coerceIn(800, 1680)
                    val fastPreviewWidthPx = (screenWidthPx * 0.55f).toInt().coerceIn(320, 540)
                    val activeMotion = isScrolling || isDraggingScrollbar

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scaleAnim.value,
                                scaleY = scaleAnim.value,
                                translationX = offsetX,
                                translationY = offsetY
                            ),
                        // Fixed top padding keeps page 1 cleanly below floating header with zero reflow on toggle
                        contentPadding = PaddingValues(top = 84.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(state.pageCount, key = { it }) { pageIndex ->
                            SmoothPdfPageItem(
                                pageIndex = pageIndex,
                                fastWidthPx = fastPreviewWidthPx,
                                crispWidthPx = crispWidthPx,
                                currentZoomScale = scaleAnim.value,
                                isActivelyMoving = activeMotion,
                                canvasTheme = state.canvasTheme,
                                highlightBoxes = state.searchBoxesByPage[pageIndex].orEmpty(),
                                aspectRatioProvider = { viewModel.getPageAspectRatio(pageIndex) },
                                renderProvider = { width -> viewModel.renderPage(pageIndex, width) },
                                tileProvider = { l, t, r, b, fullW ->
                                    viewModel.renderHighZoomTile(pageIndex, l, t, r, b, fullW)
                                }
                            )
                        }
                    }
                }
            }
        }

        // 2. Floating Premium Single-Row Header (slides & fades smoothly over PDF with zero layout jump)
        androidx.compose.animation.AnimatedVisibility(
            visible = state.controlsVisible || isSearchMode,
            enter = slideInVertically(tween(240, easing = FastOutSlowInEasing)) { -it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(210, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(170)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = headerBg,
                contentColor = headerFg,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSearchMode) {
                            IconButton(
                                onClick = {
                                    isSearchMode = false
                                    viewModel.clearSearch()
                                },
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = "Close search", tint = headerFg, modifier = Modifier.size(22.dp))
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0x1FFFFFFF),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .padding(end = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (state.searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search in PDF...",
                                            color = Color(0xFF9CA3AF),
                                            fontSize = 14.sp
                                        )
                                    }
                                    BasicTextField(
                                        value = state.searchQuery,
                                        onValueChange = { q ->
                                            viewModel.searchText(q) { firstPage -> jumpToPage(firstPage) }
                                        },
                                        singleLine = true,
                                        textStyle = TextStyle(color = headerFg, fontSize = 14.sp),
                                        cursorBrush = SolidColor(Color(0xFFA5B4FC)),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            if (state.searchQuery.isNotBlank()) {
                                val matchText = if (state.searchHits.isNotEmpty()) {
                                    "${state.activeHitIndex + 1}/${state.searchHits.size} (${state.totalMatchCount})"
                                } else if (state.isSearching) {
                                    "..."
                                } else {
                                    "0"
                                }
                                Text(
                                    text = matchText,
                                    color = Color(0xFFE5E7EB),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.selectPreviousHit()?.let(::jumpToPage) },
                                enabled = state.searchHits.isNotEmpty(),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.KeyboardArrowUp,
                                    contentDescription = "Previous match",
                                    tint = if (state.searchHits.isNotEmpty()) headerFg else Color(0xFF6B7280),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.selectNextHit()?.let(::jumpToPage) },
                                enabled = state.searchHits.isNotEmpty(),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = "Next match",
                                    tint = if (state.searchHits.isNotEmpty()) headerFg else Color(0xFF6B7280),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        } else {
                            // Normal Header: Back + Filename + [Search, Thumbnails, Bookmark, 3-dot Menu]
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "Back",
                                    tint = headerFg,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Text(
                                text = state.documentTitle.ifBlank { "Document.pdf" },
                                color = headerFg,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp)
                            )

                            IconButton(
                                onClick = { isSearchMode = true },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Outlined.Search, contentDescription = "Search", tint = headerFg, modifier = Modifier.size(21.dp))
                            }

                            IconButton(
                                onClick = { showThumbnailsSheet = true },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Outlined.GridView, contentDescription = "Thumbnails", tint = headerFg, modifier = Modifier.size(21.dp))
                            }

                            // Bookmark icon directly between Thumbnails and Three-dot menu
                            IconButton(
                                onClick = viewModel::toggleCurrentPageBookmark,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCurrentBookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = "Bookmark page",
                                    tint = if (isCurrentBookmarked) Color(0xFFC4B5FD) else headerFg,
                                    modifier = Modifier.size(21.dp)
                                )
                            }

                            Box {
                                IconButton(
                                    onClick = { showOverflowMenu = true },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Outlined.MoreVert, contentDescription = "Menu", tint = headerFg, modifier = Modifier.size(21.dp))
                                }
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Download / Save to Storage") },
                                        leadingIcon = { Icon(Icons.Outlined.Download, null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            val suggested = state.documentTitle.ifBlank { "Document.pdf" }
                                            saveDocumentLauncher.launch(suggested)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            val label = when (state.canvasTheme) {
                                                ReadingCanvasTheme.DARK_CANVAS -> "Theme: Dark → Light"
                                                ReadingCanvasTheme.LIGHT -> "Theme: Light → Night Invert"
                                                ReadingCanvasTheme.NIGHT_INVERTED -> "Theme: Night → Dark"
                                            }
                                            Text(label)
                                        },
                                        leadingIcon = { Icon(Icons.Outlined.DarkMode, null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            viewModel.cycleCanvasTheme()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Saved Bookmarks (${state.bookmarks.size})") },
                                        leadingIcon = { Icon(Icons.Outlined.Bookmarks, null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            showBookmarksSheet = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("PDF Info") },
                                        leadingIcon = { Icon(Icons.Outlined.Info, null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            showInfoDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share") },
                                        leadingIcon = { Icon(Icons.Outlined.Share, null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            sharePdfUri(context, uriString)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = Color(0x26FFFFFF))
                }
            }
        }

        // 3. Slim Premium Right-Side Vertical Scrubber (stays at exact page on re-touch!)
        if (state.pageCount > 1) {
            GoogleDriveVerticalScrubber(
                visible = scrollbarVisible || isDraggingScrollbar,
                currentPage = state.currentPage,
                pageCount = state.pageCount,
                isDragging = isDraggingScrollbar,
                onDragStateChanged = { isDraggingScrollbar = it },
                onScrubToPage = { jumpToPage(it) },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(top = 64.dp, bottom = 84.dp)
            )
        }

        // 4. Bottom-Right Floating Edit Button (hides/reappears smoothly with reading controls)
        androidx.compose.animation.AnimatedVisibility(
            visible = state.controlsVisible && !isSearchMode && state.pageCount > 0,
            enter = scaleIn(tween(200, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
            exit = scaleOut(tween(170, easing = FastOutSlowInEasing)) + fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 20.dp)
        ) {
            FloatingActionButton(
                onClick = { onOpenEditor(uriString) },
                shape = RoundedCornerShape(16.dp),
                containerColor = Color(0xFFE8DEF8),
                contentColor = Color(0xFF1D192B),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                modifier = Modifier.size(52.dp)
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = "Edit PDF", modifier = Modifier.size(22.dp))
            }
        }
    }

    // Thumbnails Sheet
    if (showThumbnailsSheet && state.pageCount > 0) {
        ModalBottomSheet(onDismissRequest = { showThumbnailsSheet = false }) {
            Text(
                text = "Pages (${state.pageCount})",
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

    // Bookmarks Sheet
    if (showBookmarksSheet) {
        ModalBottomSheet(onDismissRequest = { showBookmarksSheet = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Bookmarks", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (state.bookmarks.isEmpty()) {
                    Text(
                        "No bookmarks saved yet. Tap the bookmark icon in the header to save a page.",
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

    // PDF Info Dialog
    if (showInfoDialog) {
        val m = state.metadata
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("PDF Information") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Name: ${state.documentTitle.ifBlank { m.title }}")
                    Text("Size: ${Formatters.formatFileSize(state.fileSizeBytes)}")
                    Text("Pages: ${m.pageCount}")
                    Text("Format: ${m.format}")
                    Text("Encryption: ${m.encryption}")
                    if (m.author.isNotBlank()) Text("Author: ${m.author}")
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) { Text("Close") }
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
}

/**
 * Premium slim right-edge vertical fast-scroll thumb + dark glass page pill.
 * Uses rememberUpdatedState so subsequent drags ALWAYS start from the exact current page
 * under the user's thumb—never jumping back to page 1.
 */
@Composable
private fun GoogleDriveVerticalScrubber(
    visible: Boolean,
    currentPage: Int,
    pageCount: Int,
    isDragging: Boolean,
    onDragStateChanged: (Boolean) -> Unit,
    onScrubToPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(140)),
        exit = fadeOut(tween(280)),
        modifier = modifier
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxHeight()
                .width(108.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            val density = LocalDensity.current
            val thumbHeightDp = 44.dp
            val thumbHeightPx = with(density) { thumbHeightDp.toPx() }
            val trackHeightPx = (constraints.maxHeight.toFloat() - thumbHeightPx).coerceAtLeast(1f)

            val fraction = if (pageCount > 1) {
                (currentPage.toFloat() / (pageCount - 1).toFloat()).coerceIn(0f, 1f)
            } else 0f

            // Always read the latest fraction and callbacks inside pointerInput!
            val latestFraction by rememberUpdatedState(fraction)
            val latestOnScrub by rememberUpdatedState(onScrubToPage)
            val latestOnDragState by rememberUpdatedState(onDragStateChanged)

            var dragFraction by remember { mutableFloatStateOf(fraction) }
            LaunchedEffect(fraction, isDragging) {
                if (!isDragging) dragFraction = fraction
            }

            val effectiveFraction = if (isDragging) dragFraction else fraction
            val thumbOffsetYPx = (effectiveFraction * trackHeightPx).roundToInt()

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.offset { IntOffset(0, thumbOffsetYPx) }
            ) {
                // Sleek dark glass page indicator pill ("30/60")
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xEB1D1F26),
                    contentColor = Color(0xFFF9FAFB),
                    shadowElevation = 5.dp,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .border(0.8.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
                ) {
                    Text(
                        text = "${currentPage + 1}/$pageCount",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }

                // Slim dark glass right-edge half-pill drag thumb
                val thumbShape = RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp)
                Surface(
                    shape = thumbShape,
                    color = Color(0xF522252E),
                    contentColor = Color(0xFFF3F4F6),
                    modifier = Modifier
                        .width(26.dp)
                        .height(thumbHeightDp)
                        .shadow(5.dp, thumbShape)
                        .border(0.8.dp, Color(0x38FFFFFF), thumbShape)
                        .pointerInput(pageCount, trackHeightPx) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragFraction = latestFraction
                                    latestOnDragState(true)
                                },
                                onDragEnd = { latestOnDragState(false) },
                                onDragCancel = { latestOnDragState(false) },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    val next = (dragFraction + (dragAmount / trackHeightPx)).coerceIn(0f, 1f)
                                    dragFraction = next
                                    val targetPage = (next * (pageCount - 1)).roundToInt()
                                    latestOnScrub(targetPage)
                                }
                            )
                        }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.KeyboardArrowUp,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Icon(
                            imageVector = Icons.Outlined.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Coordinated single-state-machine PDF page renderer:
 * NotLoaded -> PreviewReady -> SharpReady -> HighZoomTileReady
 * Strict monotonic resolution guard ensures a slower low-res pass can NEVER replace a sharper bitmap.
 */
@Composable
private fun SmoothPdfPageItem(
    pageIndex: Int,
    fastWidthPx: Int,
    crispWidthPx: Int,
    currentZoomScale: Float,
    isActivelyMoving: Boolean,
    canvasTheme: ReadingCanvasTheme,
    highlightBoxes: List<PdfSearchBox>,
    aspectRatioProvider: suspend () -> Float,
    renderProvider: suspend (Int) -> Bitmap?,
    tileProvider: suspend (Float, Float, Float, Float, Int) -> Bitmap?
) {
    var aspectRatio by remember(pageIndex) { mutableFloatStateOf(0.707f) }
    var bitmap by remember(pageIndex, canvasTheme) { mutableStateOf<Bitmap?>(null) }
    var loadedWidthPx by remember(pageIndex, canvasTheme) { mutableStateOf(0) }
    var zoomedTile by remember(pageIndex, canvasTheme) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex) {
        aspectRatio = aspectRatioProvider()
    }

    // Single coordinated render state machine (zero concurrent race conditions)
    LaunchedEffect(pageIndex, canvasTheme, isActivelyMoving, currentZoomScale) {
        if (loadedWidthPx < fastWidthPx) {
            val preview = renderProvider(fastWidthPx)
            if (preview != null && fastWidthPx > loadedWidthPx) {
                bitmap = preview
                loadedWidthPx = fastWidthPx
            }
        }
        if (!isActivelyMoving && loadedWidthPx < crispWidthPx) {
            val crisp = renderProvider(crispWidthPx)
            if (crisp != null && crispWidthPx > loadedWidthPx) {
                bitmap = crisp
                loadedWidthPx = crispWidthPx
            }
        }
        // High-zoom crisp upgrade when user zooms past 2.2x (up to 7x) and pauses
        if (!isActivelyMoving && currentZoomScale >= 2.2f) {
            delay(180)
            val targetZoomedFullW = (crispWidthPx * currentZoomScale.coerceAtMost(4.5f)).toInt().coerceAtMost(6400)
            val tile = tileProvider(0f, 0f, 1f, 1f, targetZoomedFullW)
            if (tile != null) {
                zoomedTile = tile
            }
        } else if (currentZoomScale < 1.8f) {
            zoomedTile = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .background(if (canvasTheme == ReadingCanvasTheme.NIGHT_INVERTED) Color(0xFF14161A) else Color.White)
    ) {
        val displayBmp = zoomedTile ?: bitmap
        if (displayBmp != null && !displayBmp.isRecycled) {
            Image(
                bitmap = displayBmp.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
            if (highlightBoxes.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    highlightBoxes.forEach { box ->
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
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Page ${pageIndex + 1}", style = MaterialTheme.typography.labelSmall)
    }
}

private fun sharePdfUri(context: android.content.Context, uriString: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, Uri.parse(uriString))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share PDF"))
}
