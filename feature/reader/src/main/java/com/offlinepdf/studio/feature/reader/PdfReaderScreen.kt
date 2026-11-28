package com.offlinepdf.studio.feature.reader

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinepdf.studio.core.designsystem.R
import com.offlinepdf.studio.core.designsystem.theme.extras
import com.offlinepdf.studio.core.pdf.PdfLink
import com.offlinepdf.studio.core.pdf.ReadingCanvasTheme
import com.offlinepdf.studio.core.pdf.ReadingLayoutMode
import com.offlinepdf.studio.core.pdf.ZoomPreset
import kotlinx.coroutines.launch
import kotlin.math.min

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun PdfReaderScreen(
    uriString: String,
    onBack: () -> Unit,
    onOpenEditor: (String) -> Unit,
    viewModel: PdfReaderViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val zoom = remember { ReaderZoomState() }
    val controller = remember { ReaderController() }

    var searchMode by remember { mutableStateOf(false) }
    var showNavigate by remember { mutableStateOf<NavTab?>(null) }
    var showOptions by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showGoTo by remember { mutableStateOf(false) }
    var pendingLink by remember { mutableStateOf<String?>(null) }
    var scrubbing by remember { mutableStateOf(false) }
    var viewerBounds by remember { mutableStateOf(Rect.Zero) }

    LaunchedEffect(uriString) { if (uriString.isNotBlank()) viewModel.openUri(uriString) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> if (uri != null) viewModel.exportToDestination(uri) }

    // ── Toasts ──
    LaunchedEffect(state.toast) {
        val t = state.toast ?: return@LaunchedEffect
        val text = when (t.message) {
            ReaderMessage.BOOKMARK_ADDED -> context.getString(R.string.toast_bookmark_added, t.page)
            ReaderMessage.BOOKMARK_REMOVED -> context.getString(R.string.toast_bookmark_removed, t.page)
            ReaderMessage.SAVED -> context.getString(R.string.toast_saved)
            ReaderMessage.SAVE_FAILED -> context.getString(R.string.toast_save_failed)
            ReaderMessage.TEXT_COPIED -> context.getString(R.string.toast_text_copied, t.page)
            ReaderMessage.NO_TEXT -> context.getString(R.string.toast_no_text)
            ReaderMessage.SHARE_FAILED -> context.getString(R.string.toast_share_failed)
        }
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        viewModel.clearToast()
    }

    // ── Chrome visibility / immersive reading ──
    val chromeVisible = state.controlsVisible || searchMode
    val canvasColor = when (state.canvasTheme) {
        ReadingCanvasTheme.LIGHT -> MaterialTheme.extras.readerCanvasLight
        ReadingCanvasTheme.DARK_CANVAS -> MaterialTheme.extras.readerCanvasDark
        ReadingCanvasTheme.NIGHT_INVERTED -> MaterialTheme.extras.readerCanvasNight
    }
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    DisposableEffect(view, chromeVisible, state.canvasTheme) {
        val window = context.findActivity()?.window
        val controllerCompat = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousLight = controllerCompat?.isAppearanceLightStatusBars
        controllerCompat?.isAppearanceLightStatusBars = state.canvasTheme == ReadingCanvasTheme.LIGHT
        controllerCompat?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (chromeVisible) controllerCompat?.show(WindowInsetsCompat.Type.systemBars())
        else controllerCompat?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controllerCompat?.show(WindowInsetsCompat.Type.systemBars())
            if (previousLight != null) controllerCompat?.isAppearanceLightStatusBars = previousLight
        }
    }

    BackHandler(enabled = searchMode) {
        searchMode = false
        viewModel.clearSearch()
    }

    // ── Geometry ──
    val statusTop = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
    val contentPadding = PaddingValues(top = statusTop + 68.dp, bottom = navBottom + 76.dp)

    val density = androidx.compose.ui.platform.LocalDensity.current
    val vw = zoom.viewport.width.toFloat()
    val vh = zoom.viewport.height.toFloat()
    val padVerticalPx = with(density) { (contentPadding.calculateTopPadding() + contentPadding.calculateBottomPadding()).toPx() }
    val currentAspect = state.aspectOf(state.currentPage).coerceAtLeast(0.05f)
    val pageWidthFraction = if (state.layoutMode == ReadingLayoutMode.CONTINUOUS_VERTICAL &&
        state.zoomPreset == ZoomPreset.FIT_PAGE && vw > 0f
    ) {
        min(1f, ((vh - padVerticalPx) * currentAspect / vw)).coerceAtLeast(0.3f)
    } else 1f

    fun jumpToPage(page: Int) {
        zoom.reset()
        controller.jumpTo(page.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0)), true)
    }

    fun applyZoomPreset(preset: ZoomPreset) {
        viewModel.setZoomPreset(preset)
        when (preset) {
            ZoomPreset.FIT_PAGE -> zoom.animateTo(scope, 1f, null)
            ZoomPreset.ACTUAL_SIZE -> zoom.animateTo(scope, ZoomPreset.ACTUAL_SIZE.scaleMultiplier, null)
            ZoomPreset.FIT_WIDTH -> {
                if (state.layoutMode == ReadingLayoutMode.CONTINUOUS_VERTICAL || vw <= 0f) {
                    zoom.animateTo(scope, 1f, null)
                } else {
                    val contained = min(vw, (vh - padVerticalPx) * currentAspect)
                    zoom.animateTo(scope, (vw / contained).coerceAtLeast(1f), null, targetOffsetY = Float.MAX_VALUE)
                }
            }
        }
    }

    // Pre-render neighbours at the exact width pages will be drawn with.
    LaunchedEffect(state.currentPage, state.pageCount, state.canvasTheme, vw) {
        if (state.pageCount > 0 && vw > 0f) {
            viewModel.prefetch(state.currentPage, baseRenderWidth((vw * pageWidthFraction).toInt()))
        }
    }

    val env = remember(state.canvasTheme, viewModel) {
        PageEnv(
            zoom = zoom,
            canvasTheme = state.canvasTheme,
            renderPage = { page, w -> viewModel.renderPage(page, w) },
            renderTile = { page, l, t, r, b, w -> viewModel.renderTile(page, l, t, r, b, w) },
            loadLinks = { page -> viewModel.loadLinks(page) },
            viewportBounds = { viewerBounds },
            scrollSignal = { controller.scrollSignal() },
            isMoving = { zoom.isInteracting || controller.isScrollInProgress() },
            onLink = { link: PdfLink ->
                val target = link.targetPage
                if (target != null) {
                    zoom.reset()
                    controller.jumpTo(target, true)
                } else if (link.uri != null) {
                    pendingLink = link.uri
                }
            }
        )
    }

    val zoomed by remember { derivedStateOf { zoom.isZoomed } }
    val scrolling by remember { derivedStateOf { controller.isScrollInProgress() } }
    val isContinuous = state.layoutMode == ReadingLayoutMode.CONTINUOUS_VERTICAL
    val continuousScroller: (Float) -> Float = remember(controller) {
        { delta: Float -> controller.scroller?.invoke(delta) ?: 0f }
    }
    val scroller: ((Float) -> Float)? = if (isContinuous) continuousScroller else null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(canvasColor)
            .onSizeChanged { zoom.viewport = it }
    ) {
        if (state.pageCount > 0 && !state.needsPassword) {
            key(state.layoutMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .onGloballyPositioned { viewerBounds = it.boundsInRoot() }
                        .readerZoomGestures(
                            zoom = zoom,
                            scope = scope,
                            scroller = scroller,
                            onTap = { viewModel.toggleControlsVisibility() }
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoom.scale
                                scaleY = zoom.scale
                                translationX = zoom.offsetX
                                translationY = zoom.offsetY
                            }
                    ) {
                        val pageContent: @Composable (Int, Modifier) -> Unit = { idx, mod ->
                            PdfPageView(
                                pageIndex = idx,
                                env = env,
                                highlights = state.matchesByPage[idx].orEmpty(),
                                activeMatch = state.activeMatchIndex,
                                modifier = mod
                            )
                        }
                        when (state.layoutMode) {
                            ReadingLayoutMode.CONTINUOUS_VERTICAL -> ContinuousViewer(
                                pageCount = state.pageCount,
                                initialPage = state.currentPage,
                                aspectOf = { state.aspectOf(it) },
                                pageWidthFraction = pageWidthFraction,
                                contentPadding = contentPadding,
                                controller = controller,
                                userScrollEnabled = !zoomed,
                                onCurrentPage = viewModel::onPageChanged,
                                modifier = Modifier.fillMaxSize(),
                                pageContent = pageContent
                            )
                            else -> PagedViewer(
                                pageCount = state.pageCount,
                                initialPage = state.currentPage,
                                spread = state.layoutMode == ReadingLayoutMode.TWO_PAGE_SPREAD,
                                aspectOf = { state.aspectOf(it) },
                                controller = controller,
                                userScrollEnabled = !zoomed,
                                onCurrentPage = viewModel::onPageChanged,
                                contentPadding = contentPadding,
                                modifier = Modifier.fillMaxSize(),
                                pageContent = pageContent
                            )
                        }
                    }
                }
            }
        }

        if (state.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.reader_opening), color = Color(0xFFB6BDCF))
            }
        }

        state.errorMessage?.let { message ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.height(48.dp))
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.error_title), color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(message, color = Color(0xFFB6BDCF), textAlign = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                Button(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            }
        }

        // ── Chrome ──
        ReaderHeader(
            visible = chromeVisible && !state.isLoading && state.errorMessage == null,
            searchMode = searchMode,
            title = state.documentTitle,
            bookmarked = state.bookmarkedPages.contains(state.currentPage),
            query = state.searchQuery,
            matchCount = state.matches.size,
            activeMatch = state.activeMatchIndex,
            isSearching = state.isSearching,
            searchProgress = state.searchProgress,
            onBack = onBack,
            onOpenSearch = { searchMode = true; viewModel.setControlsVisible(true) },
            onCloseSearch = { searchMode = false; viewModel.clearSearch() },
            onQueryChange = { q -> viewModel.searchText(q) { page -> jumpToPage(page) } },
            onPrevMatch = { viewModel.selectPreviousMatch()?.let { jumpToPage(it) } },
            onNextMatch = { viewModel.selectNextMatch()?.let { jumpToPage(it) } },
            onNavigate = { showNavigate = NavTab.PAGES },
            onBookmark = viewModel::toggleCurrentPageBookmark,
            onSave = {
                val name = state.documentTitle.ifBlank { "Document" }
                saveLauncher.launch(if (name.endsWith(".pdf", true)) name else "$name.pdf")
            },
            onShare = {
                scope.launch {
                    viewModel.prepareShareUri()?.let { shareUri ->
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, shareUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, null))
                    }
                }
            },
            onViewOptions = { showOptions = true },
            onCopyText = {
                scope.launch {
                    val text = viewModel.extractPageText(state.currentPage)
                    if (text.isBlank()) {
                        viewModel.postToast(ReaderMessage.NO_TEXT)
                    } else {
                        val cm = context.getSystemService(ClipboardManager::class.java)
                        cm?.setPrimaryClip(ClipData.newPlainText("PDF", text))
                        viewModel.postToast(ReaderMessage.TEXT_COPIED, state.currentPage + 1)
                    }
                }
            },
            onInfo = { showInfo = true },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        if (isContinuous && state.pageCount > 3 && !zoomed) {
            ReaderScrubber(
                visible = scrubbing || scrolling || (chromeVisible && !searchMode),
                currentPage = state.currentPage,
                pageCount = state.pageCount,
                isDragging = scrubbing,
                onDragStateChanged = { scrubbing = it },
                onScrubToPage = { controller.jumpTo(it, false) },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(top = statusTop + 76.dp, bottom = navBottom + 84.dp)
            )
        }

        if (state.pageCount > 0) {
            PageChip(
                visible = chromeVisible && !searchMode && !scrubbing,
                current = state.currentPage,
                total = state.pageCount,
                onClick = { showGoTo = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            )
            EditFab(
                visible = chromeVisible && !searchMode,
                onClick = { onOpenEditor(uriString) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 12.dp)
            )
        }
    }

    // ── Sheets & dialogs ──
    showNavigate?.let { tab ->
        NavigateSheet(
            initialTab = tab,
            pageCount = state.pageCount,
            currentPage = state.currentPage,
            outline = state.outline,
            bookmarks = state.bookmarks,
            renderThumbnail = { viewModel.renderThumbnail(it) },
            onJump = { showNavigate = null; jumpToPage(it) },
            onDeleteBookmark = viewModel::deleteBookmark,
            onDismiss = { showNavigate = null }
        )
    }
    if (showOptions) {
        ViewOptionsSheet(
            layout = state.layoutMode,
            preset = state.zoomPreset,
            theme = state.canvasTheme,
            onLayout = { zoom.reset(); viewModel.setLayoutMode(it) },
            onPreset = ::applyZoomPreset,
            onTheme = viewModel::setCanvasTheme,
            onDismiss = { showOptions = false }
        )
    }
    if (showInfo) {
        InfoDialog(state.documentTitle, state.fileSizeBytes, state.metadata) { showInfo = false }
    }
    if (showGoTo) {
        GoToPageDialog(
            pageCount = state.pageCount,
            onGo = { showGoTo = false; jumpToPage(it) },
            onDismiss = { showGoTo = false }
        )
    }
    if (state.needsPassword) {
        PasswordDialog(
            error = state.passwordError,
            onSubmit = viewModel::submitPassword,
            onCancel = onBack
        )
    }
    pendingLink?.let { link ->
        ExternalLinkDialog(
            uri = link,
            onOpen = {
                pendingLink = null
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
            onDismiss = { pendingLink = null }
        )
    }
}
