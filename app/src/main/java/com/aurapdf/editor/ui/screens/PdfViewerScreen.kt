package com.aurapdf.editor.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aurapdf.editor.engine.PdfEngine
import com.aurapdf.editor.ui.components.PremiumToolbarButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    pdfFile: File,
    onBack: () -> Unit,
    onOpenMarkup: (Int) -> Unit,
    onOpenSign: (Int) -> Unit,
    onOpenOrganizer: () -> Unit,
    onRunOcr: (Int?) -> Unit,
    onSplit: (Int) -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onWatermark: () -> Unit,
    onCompress: () -> Unit,
    onExportImages: () -> Unit,
    onSaveDownloads: () -> Unit,
    onShare: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var scale by remember { mutableFloatStateOf(1f) }
    var darkInvertMode by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchMatches by remember { mutableStateOf<List<Int>>(emptyList()) }
    var showToolsSheet by remember { mutableStateOf(false) }

    val rendererResult = remember(pdfFile, pdfFile.lastModified()) {
        runCatching {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                PdfRenderer(pfd)
            } catch (e: Throwable) {
                pfd.close()
                throw e
            }
        }
    }
    val renderer = rendererResult.getOrNull()
    val isPasswordProtected = rendererResult.exceptionOrNull() is SecurityException

    val renderMutex = remember { Mutex() }
    val bitmapCache = remember(pdfFile, pdfFile.lastModified()) {
        object : LruCache<Int, Bitmap>(16) {}
    }

    DisposableEffect(renderer) {
        onDispose {
            bitmapCache.evictAll()
            renderer?.close()
        }
    }

    val pageCount = renderer?.pageCount ?: 0
    val currentPageIndex by remember(pageCount) {
        derivedStateOf { listState.firstVisibleItemIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0)) }
    }

    val invertFilter = remember {
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

    if (showToolsSheet && renderer != null) {
        ModalBottomSheet(onDismissRequest = { showToolsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("PDF Tools", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                SheetToolRow(Icons.Rounded.CallSplit, "Split / Extract Pages") {
                    showToolsSheet = false; onSplit(pageCount)
                }
                SheetToolRow(Icons.Rounded.Lock, "Lock with Password (AES-128)") {
                    showToolsSheet = false; onLock()
                }
                SheetToolRow(Icons.Rounded.LockOpen, "Unlock Password") {
                    showToolsSheet = false; onUnlock()
                }
                SheetToolRow(Icons.Rounded.WaterDrop, "Add Diagonal Watermark") {
                    showToolsSheet = false; onWatermark()
                }
                SheetToolRow(Icons.Rounded.Compress, "Compress File Size") {
                    showToolsSheet = false; onCompress()
                }
                SheetToolRow(Icons.Rounded.TextSnippet, "OCR Entire PDF (Offline)") {
                    showToolsSheet = false; onRunOcr(null)
                }
                SheetToolRow(Icons.Rounded.PhotoLibrary, "Export All Pages as PNG") {
                    showToolsSheet = false; onExportImages()
                }
                SheetToolRow(Icons.Rounded.Download, "Save Copy to Downloads") {
                    showToolsSheet = false; onSaveDownloads()
                }
                SheetToolRow(Icons.Rounded.Share, "Share PDF") {
                    showToolsSheet = false; onShare()
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(pdfFile.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (pageCount > 0) "Page ${currentPageIndex + 1} of $pageCount"
                                else if (isPasswordProtected) "Password Protected" else "Cannot open PDF",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (renderer != null) {
                            IconButton(onClick = { showSearchBar = !showSearchBar }) {
                                Icon(Icons.Rounded.Search, contentDescription = "Search")
                            }
                            IconButton(onClick = { darkInvertMode = !darkInvertMode }) {
                                Icon(
                                    if (darkInvertMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                    contentDescription = "Night Mode"
                                )
                            }
                            IconButton(onClick = { showToolsSheet = true }) {
                                Icon(Icons.Rounded.Tune, contentDescription = "More Tools")
                            }
                        }
                    }
                )

                AnimatedVisibility(visible = showSearchBar && renderer != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Find text in PDF...") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = {
                            scope.launch {
                                val found = runCatching {
                                    PdfEngine.searchTextInPdf(pdfFile, searchQuery)
                                }.getOrDefault(emptyList())
                                searchMatches = found
                                if (found.isNotEmpty()) {
                                    listState.animateScrollToItem(found.first())
                                }
                            }
                        }) {
                            Text("Find")
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (renderer != null) {
                Surface(
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        PremiumToolbarButton("Markup", Icons.Rounded.Draw, { onOpenMarkup(currentPageIndex) })
                        PremiumToolbarButton("Sign", Icons.Rounded.BorderColor, { onOpenSign(currentPageIndex) })
                        PremiumToolbarButton("Pages", Icons.Rounded.GridView, onOpenOrganizer)
                        PremiumToolbarButton("OCR", Icons.Rounded.TextSnippet, { onRunOcr(currentPageIndex) })
                        PremiumToolbarButton("Tools", Icons.Rounded.Tune, { showToolsSheet = true })
                    }
                }
            }
        }
    ) { padding ->
        if (renderer == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(shape = RoundedCornerShape(22.dp)) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            if (isPasswordProtected) Icons.Rounded.Lock else Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            if (isPasswordProtected) "This PDF is password protected"
                            else "Unable to open this PDF file",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (isPasswordProtected) {
                            Button(onClick = onUnlock) {
                                Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Unlock PDF")
                            }
                        } else {
                            OutlinedButton(onClick = onBack) { Text("Go Back") }
                        }
                    }
                }
            }
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 3.5f)
                    }
                }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(pageCount) { pageIndex ->
                    PdfPageCard(
                        renderer = renderer,
                        mutex = renderMutex,
                        cache = bitmapCache,
                        pageIndex = pageIndex,
                        isSearchMatch = searchMatches.contains(pageIndex),
                        colorFilter = if (darkInvertMode) invertFilter else null
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetToolRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun PdfPageCard(
    renderer: PdfRenderer,
    mutex: Mutex,
    cache: LruCache<Int, Bitmap>,
    pageIndex: Int,
    isSearchMatch: Boolean,
    colorFilter: ColorFilter?
) {
    var bitmap by remember(pageIndex, cache) { mutableStateOf(cache.get(pageIndex)) }

    LaunchedEffect(pageIndex, cache) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.IO) {
                cache.get(pageIndex) ?: mutex.withLock {
                    renderer.openPage(pageIndex).use { page ->
                        val targetW = 1080
                        val targetH = (targetW.toFloat() / page.width * page.height).toInt()
                        Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888).also {
                            it.eraseColor(AndroidColor.WHITE)
                            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            cache.put(pageIndex, it)
                        }
                    }
                }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSearchMatch) 6.dp else 2.dp)
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(460.dp)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }
        }
    }
}
