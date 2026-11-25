package com.aurapdf.editor.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class EditablePageItem(
    val originalIndex: Int,
    val rotation: Int = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageOrganizerScreen(
    pdfFile: File,
    onBack: () -> Unit,
    onSaveChanges: (List<Pair<Int, Int>>) -> Unit
) {
    val rendererResult = remember(pdfFile) {
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
    val renderMutex = remember { Mutex() }

    DisposableEffect(renderer) {
        onDispose { runCatching { renderer?.close() } }
    }

    val pages = remember(pdfFile, renderer) {
        mutableStateListOf<EditablePageItem>().apply {
            val count = renderer?.pageCount ?: 0
            for (i in 0 until count) add(EditablePageItem(i, 0))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Organize Pages (${pages.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        enabled = pages.isNotEmpty(),
                        onClick = { onSaveChanges(pages.map { it.originalIndex to it.rotation }) },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        if (renderer == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Unable to load pages for this PDF.")
            }
            return@Scaffold
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            itemsIndexed(pages, key = { _, item -> item.originalIndex }) { idx, item ->
                var thumb by remember(item.originalIndex) { mutableStateOf<Bitmap?>(null) }

                LaunchedEffect(item.originalIndex) {
                    thumb = withContext(Dispatchers.IO) {
                        renderMutex.withLock {
                            runCatching {
                                renderer.openPage(item.originalIndex).use { p ->
                                    val w = 360
                                    val h = (w.toFloat() / p.width.coerceAtLeast(1) * p.height)
                                        .toInt()
                                        .coerceAtLeast(1)
                                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                                        it.eraseColor(AndroidColor.WHITE)
                                        p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    }
                                }
                            }.getOrNull()
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(18.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(170.dp)
                                .background(Color.White, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            val bmp = thumb
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Page ${idx + 1}",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .rotate(item.rotation.toFloat())
                                )
                            } else {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Page ${idx + 1}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            IconButton(
                                onClick = {
                                    if (idx > 0) {
                                        val moved = pages.removeAt(idx)
                                        pages.add(idx - 1, moved)
                                    }
                                },
                                enabled = idx > 0
                            ) {
                                Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = {
                                    pages[idx] = item.copy(rotation = (item.rotation + 90) % 360)
                                }
                            ) {
                                Icon(Icons.Rounded.RotateRight, contentDescription = "Rotate", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = {
                                    if (pages.size > 1) pages.removeAt(idx)
                                },
                                enabled = pages.size > 1
                            ) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
