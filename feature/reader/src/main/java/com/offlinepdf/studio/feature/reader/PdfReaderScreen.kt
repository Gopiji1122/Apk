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

    // Smooth pinch-zoom & pan state
    val scaleAnim = remember { Animatable(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    // Google Drive-style right-side scrubber visibility (auto-hides after 2.5s)
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

    LaunchedEffect(listState.firstVisibleItemIndex, state.pageCount) {
        if (state.pageCount > 0) {
            viewModel.onPageChanged(listState.firstVisibleItemIndex)
        }
    }

    val canvasBackground = when (state.canvasTheme) {
        ReadingCanvasTheme.LIGHT -> Color(0xFFE5E7EB)
        ReadingCanvasTheme.DARK_CANVAS -> Color(0xFF121316)
        ReadingCanvasTheme.NIGHT_INVERTED -> Color(0xFF090A0C)
    }

    val headerBg = Color(0xFF141518)
    val headerFg = Color(0xFFF3F4F6)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(canvasBackground)
    ) {
        // 1. Compact Google Drive-Style Single-Row Header (~52dp, thumb-friendly)
        AnimatedVisibility(
            visible = state.controlsVisible || isSearchMode,
            enter = slideInVertically { -it } + fadeIn(tween(160)),
            exit = slideOutVertically { -it } + fadeOut(tween(160))
        ) {
            Surface(
                color = headerBg,
                contentColor = headerFg,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSearchMode) {
                        // Inline Search Mode directly inside the header bar
                        IconButton(onClick = {
                            isSearchMode = false
                            viewModel.clearSearch()
                        }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close search", tint = headerFg)
                        }

                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (state.searchQuery.isEmpty()) {
                                Text(
                                    text = "Search in PDF...",
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 15.sp
                                )
                            }
                            BasicTextField(
                                value = state.searchQuery,
                                onValueChange = { q ->
                                    viewModel.searchText(q) { firstPage -> jumpToPage(firstPage) }
                                },
                                singleLine = true,
                                textStyle = TextStyle(color = headerFg, fontSize = 15.sp),
                                cursorBrush = SolidColor(Color(0xFF93C5FD)),
                                modifier = Modifier.fillMaxWidth()
                            )
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
                                color = Color(0xFFD1D5DB),
                                fontSize = 12.sp,
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
                                tint = if (state.searchHits.isNotEmpty()) headerFg else Color(0xFF6B7280)
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
                                tint = if (state.searchHits.isNotEmpty()) headerFg else Color(0xFF6B7280)
                            )
                        }
                    } else {
                        // Minimal Normal Header: Back + File Name + [Search, Thumbnails, 3-dot Menu]
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = headerFg)
                        }

                        Text(
                            text = state.documentTitle.ifBlank { "Document.pdf" },
                            color = headerFg,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 4.dp)
                        )

                        IconButton(onClick = { isSearchMode = true }) {
                            Icon(Icons.Outlined.Search, contentDescription = "Search", tint = headerFg)
                        }

                        IconButton(onClick = { showThumbnailsSheet = true }) {
                            Icon(Icons.Outlined.GridView, contentDescription = "Thumbnails", tint = headerFg)
                        }

                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "Menu", tint = headerFg)
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
                                    text = {
                                        val isSaved = state.bookmarkedPages.contains(state.currentPage)
                                        Text(if (isSaved) "Remove Page Bookmark" else "Bookmark Page ${state.currentPage + 1}")
                                    },
                                    leadingIcon = {
                                        val isSaved = state.bookmarkedPages.contains(state.currentPage)
                                        Icon(
                                            if (isSaved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                                            null
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.toggleCurrentPageBookmark()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Bookmarks (${state.bookmarks.size})") },
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
            }
        }

        // 2. Direct Edge-to-Edge PDF Viewport (Zero margins, Zero bottom bar)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { viewportSize = it }
        ) {
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
                                    if (scaleAnim.value > 1.2f) {
                                        offsetX = 0f
                                        offsetY = 0f
                                        scaleAnim.animateTo(1f, tween(200, easing = FastOutSlowInEasing))
                                    } else {
                                        val target = 2.3f
                                        val cx = (viewportSize.width / 2f - tapOffset.x) * (target - 1f)
                                        val cy = (viewportSize.height / 2f - tapOffset.y) * (target - 1f)
                                        val (clampedX, clampedY) = clampOffsets(target, cx, cy)
                                        offsetX = clampedX
                                        offsetY = clampedY
                                        scaleAnim.animateTo(target, tween(200, easing = FastOutSlowInEasing))
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
                                        val newScale = (oldScale * zoomChange).coerceIn(1f, 4.5f)
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
                        // Smoothness-first resolution: fast preview while moving, crisp immediately on stop
                        val crispWidthPx = (screenWidthPx * 1.35f).toInt().coerceIn(720, 1440)
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
                            contentPadding = PaddingValues(vertical = 0.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(state.pageCount, key = { it }) { pageIndex ->
                                SmoothPdfPageItem(
                                    pageIndex = pageIndex,
                                    fastWidthPx = fastPreviewWidthPx,
                                    crispWidthPx = crispWidthPx,
                                    isActivelyMoving = activeMotion,
                                    canvasTheme = state.canvasTheme,
                                    highlightBoxes = state.searchBoxesByPage[pageIndex].orEmpty(),
                                    aspectRatioProvider = { viewModel.getPageAspectRatio(pageIndex) },
                                    renderProvider = { width -> viewModel.renderPage(pageIndex, width) }
                                )
                            }
                        }
                    }
                }
            }

            // 3. Slim Google Drive-Style Right-Side Vertical Slider + Page Pill (auto-hides after ~2.5s)
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
                        .navigationBarsPadding()
                        .padding(top = 12.dp, bottom = 76.dp)
                )
            }

            // 4. Bottom-Right Floating Edit Button (hides/reappears with user intent, opens separate Editor)
            AnimatedVisibility(
                visible = state.controlsVisible && !isSearchMode && state.pageCount > 0,
                enter = scaleIn(tween(180)) + fadeIn(tween(180)),
                exit = scaleOut(tween(160)) + fadeOut(tween(160)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 18.dp)
            ) {
                FloatingActionButton(
                    onClick = { onOpenEditor(uriString) },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = Color(0xFFE6DCFA),
                    contentColor = Color(0xFF1F1638),
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit PDF")
                }
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
                        "No bookmarks saved yet. Open the three-dot menu to bookmark the current page.",
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
 * Slim Google Drive-style right-edge vertical fast-scroll handle + dark page pill ("1/70").
 * Appears only while scrolling or dragging, and fades away smoothly after ~2.5s.
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
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(140)),
        exit = fadeOut(tween(300)),
        modifier = modifier
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxHeight()
                .width(108.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            val density = LocalDensity.current
            val thumbHeightDp = 42.dp
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
                modifier = Modifier.offset { IntOffset(0, thumbOffsetYPx) }
            ) {
                // Compact dark page pill ("1/70")
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xE626282E),
                    contentColor = Color.White,
                    shadowElevation = 4.dp,
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        text = "${currentPage + 1}/$pageCount",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                // Slim dark right-edge circular/half-pill drag thumb with up/down chevrons
                Surface(
                    shape = RoundedCornerShape(topStart = 21.dp, bottomStart = 21.dp),
                    color = Color(0xF02B2D33),
                    contentColor = Color.White,
                    modifier = Modifier
                        .width(28.dp)
                        .height(thumbHeightDp)
                        .shadow(4.dp, RoundedCornerShape(topStart = 21.dp, bottomStart = 21.dp))
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.KeyboardArrowUp,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Icon(
                            imageVector = Icons.Outlined.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Two-stage MuPDF page renderer:
 * 1) Reserves exact page aspect ratio immediately so scrolling never jumps.
 * 2) Renders fast low-res preview while scrolling, then sharpens immediately when stopped.
 */
@Composable
private fun SmoothPdfPageItem(
    pageIndex: Int,
    fastWidthPx: Int,
    crispWidthPx: Int,
    isActivelyMoving: Boolean,
    canvasTheme: ReadingCanvasTheme,
    highlightBoxes: List<PdfSearchBox>,
    aspectRatioProvider: suspend () -> Float,
    renderProvider: suspend (Int) -> Bitmap?
) {
    var aspectRatio by remember(pageIndex) { mutableFloatStateOf(0.707f) }
    var bitmap by remember(pageIndex, canvasTheme) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex) {
        aspectRatio = aspectRatioProvider()
    }

    // Fast pass immediately on visibility
    LaunchedEffect(pageIndex, canvasTheme) {
        if (bitmap == null) {
            bitmap = renderProvider(fastWidthPx)
        }
    }

    // Crisp pass immediately when user stops scrolling
    LaunchedEffect(pageIndex, canvasTheme, isActivelyMoving) {
        if (!isActivelyMoving) {
            val crisp = renderProvider(crispWidthPx)
            if (crisp != null) bitmap = crisp
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .background(if (canvasTheme == ReadingCanvasTheme.NIGHT_INVERTED) Color(0xFF14161A) else Color.White)
    ) {
        val bmp = bitmap
        if (bmp != null && !bmp.isRecycled) {
            Image(
                bitmap = bmp.asImageBitmap(),
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
