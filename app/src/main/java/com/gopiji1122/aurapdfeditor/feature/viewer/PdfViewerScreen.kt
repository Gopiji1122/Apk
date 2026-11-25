package com.gopiji1122.aurapdfeditor.feature.viewer

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.speech.tts.TextToSpeech
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gopiji1122.aurapdfeditor.core.pdfengine.PdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

private val InvertColorMatrix = ColorMatrix(
    floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    pdfUri: Uri,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var pageCount by remember { mutableIntStateOf(0) }
    var invertColors by remember { mutableStateOf(false) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchMatches by remember { mutableStateOf<List<Int>>(emptyList()) }
    var currentMatchIdx by remember { mutableIntStateOf(0) }
    var isSpeaking by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }

    val bitmapCache = remember {
        object : LruCache<Int, Bitmap>(6) {}
    }

    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var tempFile by remember { mutableStateOf<File?>(null) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(pdfUri) {
        val ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }
        tts = ttsInstance

        scope.launch(Dispatchers.IO) {
            try {
                val file = File.createTempFile("view_", ".pdf", context.cacheDir)
                context.contentResolver.openInputStream(pdfUri)?.use { input ->
                    FileOutputStream(file).use { out -> input.copyTo(out) }
                }
                val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val pdfRenderer = PdfRenderer(descriptor)
                withContext(Dispatchers.Main) {
                    tempFile = file
                    pfd = descriptor
                    renderer = pdfRenderer
                    pageCount = pdfRenderer.pageCount
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText = "Unable to open PDF: ${e.message}"
                }
            }
        }

        onDispose {
            ttsInstance.stop()
            ttsInstance.shutdown()
            renderer?.close()
            pfd?.close()
            tempFile?.delete()
            bitmapCache.evictAll()
        }
    }

    val currentPage by remember {
        derivedStateOf { (listState.firstVisibleItemIndex + 1).coerceAtMost(pageCount.coerceAtLeast(1)) }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = if (pageCount > 0) "Page $currentPage / $pageCount" else "Aura Viewer",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = { invertColors = !invertColors }) {
                            Icon(
                                if (invertColors) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Invert"
                            )
                        }
                        IconButton(onClick = {
                            if (isSpeaking) {
                                tts?.stop()
                                isSpeaking = false
                            } else {
                                scope.launch {
                                    val text = PdfEngine.extractPageText(context, pdfUri, currentPage - 1)
                                    if (text.isNotBlank()) {
                                        isSpeaking = true
                                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "page_$currentPage")
                                    } else {
                                        statusText = "No selectable text on page $currentPage"
                                    }
                                }
                            }
                        }) {
                            Icon(
                                if (isSpeaking) Icons.Default.StopCircle else Icons.Default.VolumeUp,
                                contentDescription = "Read Aloud"
                            )
                        }
                    }
                )
                if (showSearch) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search text in PDF...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            scope.launch {
                                val found = PdfEngine.searchInPdf(context, pdfUri, searchQuery)
                                searchMatches = found
                                currentMatchIdx = 0
                                if (found.isNotEmpty()) {
                                    listState.animateScrollToItem(found[0])
                                    statusText = "Found on ${found.size} page(s)"
                                } else {
                                    statusText = "No matches found"
                                }
                            }
                        }) {
                            Text("Go")
                        }
                        if (searchMatches.size > 1) {
                            IconButton(onClick = {
                                currentMatchIdx = (currentMatchIdx + 1) % searchMatches.size
                                scope.launch { listState.animateScrollToItem(searchMatches[currentMatchIdx]) }
                            }) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next Match")
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(if (invertColors) Color.Black else MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        zoomScale = (zoomScale * zoom).coerceIn(1f, 3.5f)
                    }
                }
        ) {
            if (pageCount == 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(statusText ?: "Loading PDF...")
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(scaleX = zoomScale, scaleY = zoomScale),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    items(pageCount) { index ->
                        PdfPageItem(
                            renderer = renderer,
                            pageIndex = index,
                            cache = bitmapCache,
                            invertColors = invertColors
                        )
                    }
                }
            }

            statusText?.let { msg ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { statusText = null }) { Text("OK") }
                    }
                ) {
                    Text(msg)
                }
            }
        }
    }
}

@Composable
private fun PdfPageItem(
    renderer: PdfRenderer?,
    pageIndex: Int,
    cache: LruCache<Int, Bitmap>,
    invertColors: Boolean
) {
    var bitmap by remember(pageIndex, renderer) { mutableStateOf(cache.get(pageIndex)) }

    LaunchedEffect(pageIndex, renderer) {
        if (bitmap == null && renderer != null) {
            val rendered = withContext(Dispatchers.IO) {
                synchronized(renderer) {
                    val page = renderer.openPage(pageIndex)
                    val width = (page.width * 2).coerceAtMost(1800)
                    val height = (page.height * 2).coerceAtMost(2600)
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(AndroidColor.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    cache.put(pageIndex, bmp)
                    bmp
                }
            }
            bitmap = rendered
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                contentScale = ContentScale.FillWidth,
                colorFilter = if (invertColors) ColorFilter.colorMatrix(InvertColorMatrix) else null,
                modifier = Modifier.fillMaxWidth()
            )
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
