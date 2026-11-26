package com.offlinepdf.studio.feature.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    uriString: String,
    onBack: () -> Unit,
    viewModel: PdfReaderViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var showInfoDialog by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        offset = if (scale > 1f) offset + panChange else Offset.Zero
    }

    LaunchedEffect(uriString) {
        viewModel.openUri(uriString)
    }

    LaunchedEffect(listState.firstVisibleItemIndex, state.pageCount) {
        if (state.pageCount > 0) {
            viewModel.onPageChanged(listState.firstVisibleItemIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.documentTitle.ifBlank { "PDF Reader" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
                    IconButton(onClick = viewModel::rotateClockwise) {
                        Icon(Icons.Outlined.RotateRight, contentDescription = "Rotate")
                    }
                    IconButton(onClick = viewModel::toggleNightMode) {
                        Icon(Icons.Outlined.DarkMode, contentDescription = "Night reading mode")
                    }
                    IconButton(onClick = viewModel::toggleCurrentPageBookmark) {
                        val bookmarked = state.bookmarkedPages.contains(state.currentPage)
                        Icon(
                            imageVector = if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = "Bookmark page"
                        )
                    }
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = "Document Info")
                    }
                }
            )
        },
        bottomBar = {
            if (state.pageCount > 1) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${state.currentPage + 1} / ${state.pageCount}",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Slider(
                            value = state.currentPage.toFloat(),
                            onValueChange = { target ->
                                val idx = target.toInt().coerceIn(0, state.pageCount - 1)
                                scope.launch { listState.scrollToItem(idx) }
                            },
                            valueRange = 0f..(state.pageCount - 1).toFloat(),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (showSearchBar) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = viewModel::searchText,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("Search text in PDF...") }
                    )
                    if (state.searchHits.isNotEmpty()) {
                        TextButton(onClick = {
                            val firstHit = state.searchHits.first().pageIndex
                            scope.launch { listState.scrollToItem(firstHit) }
                        }) {
                            Text("${state.searchHits.size} pages")
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (state.nightMode) Color(0xFF0E1117) else MaterialTheme.colorScheme.surfaceVariant)
                    .transformable(state = transformState)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1.2f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.2f
                                }
                            }
                        )
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
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                ),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.pageCount, key = { it }) { pageIndex ->
                                PdfPageTile(
                                    pageIndex = pageIndex,
                                    renderWidthPx = (screenWidthPx * 1.35f).toInt(),
                                    rotationDegrees = state.rotationDegrees,
                                    nightMode = state.nightMode,
                                    renderProvider = { viewModel.renderPage(pageIndex, it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

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
                    if (m.producer.isNotBlank()) Text("Producer: ${m.producer}")
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun PdfPageTile(
    pageIndex: Int,
    renderWidthPx: Int,
    rotationDegrees: Int,
    nightMode: Boolean,
    renderProvider: suspend (Int) -> Bitmap?
) {
    var bitmap by remember(pageIndex, rotationDegrees, nightMode) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex, renderWidthPx, rotationDegrees, nightMode) {
        bitmap = renderProvider(renderWidthPx)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        val bmp = bitmap
        if (bmp != null && !bmp.isRecycled) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "PDF Page ${pageIndex + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat())
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
