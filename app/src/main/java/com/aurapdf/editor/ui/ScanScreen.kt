package com.aurapdf.editor.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurapdf.editor.data.PdfStorage
import com.aurapdf.editor.engine.PdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ScanFilter(val label: String) {
    ORIGINAL("Original"),
    ENHANCE("Magic Color"),
    GRAY("Grayscale"),
    BW("B&W Doc")
}

data class ScannedPage(
    val id: Long = System.nanoTime(),
    val original: Bitmap,
    val rotationDeg: Int = 0,
    val filter: ScanFilter = ScanFilter.ENHANCE,
    val trimPercent: Int = 0
)

private fun processScanBitmap(page: ScannedPage): Bitmap {
    var src = page.original
    if (page.rotationDeg % 360 != 0) {
        val m = Matrix().apply { postRotate(page.rotationDeg.toFloat()) }
        src = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }
    if (page.trimPercent > 0) {
        val dx = (src.width * page.trimPercent / 100f).toInt().coerceAtMost(src.width / 4)
        val dy = (src.height * page.trimPercent / 100f).toInt().coerceAtMost(src.height / 4)
        src = Bitmap.createBitmap(src, dx, dy, (src.width - dx * 2).coerceAtLeast(10), (src.height - dy * 2).coerceAtLeast(10))
    }
    if (page.filter == ScanFilter.ORIGINAL) return src

    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val paint = Paint()
    val cm = ColorMatrix()
    when (page.filter) {
        ScanFilter.ENHANCE -> {
            val contrast = 1.32f
            val shift = (-0.5f * contrast + 0.5f) * 255f + 12f
            cm.set(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, shift,
                    0f, contrast, 0f, 0f, shift,
                    0f, 0f, contrast, 0f, shift,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }
        ScanFilter.GRAY -> cm.setSaturation(0f)
        ScanFilter.BW -> {
            cm.setSaturation(0f)
            val high = ColorMatrix(
                floatArrayOf(
                    2.2f, 0f, 0f, 0f, -155f,
                    0f, 2.2f, 0f, 0f, -155f,
                    0f, 0f, 2.2f, 0f, -155f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            cm.postConcat(high)
        }
        ScanFilter.ORIGINAL -> {}
    }
    paint.colorFilter = ColorMatrixColorFilter(cm)
    c.drawBitmap(src, 0f, 0f, paint)
    return out
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    storage: PdfStorage,
    onBack: () -> Unit,
    onSavedOnly: (File) -> Unit,
    onOpenInEditor: (File) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pages by remember { mutableStateOf(listOf<ScannedPage>()) }
    var selectedIdx by remember { mutableIntStateOf(0) }
    var pendingCamFile by remember { mutableStateOf<File?>(null) }
    var isBuilding by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingCamFile
        if (ok && file != null && file.exists()) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeFile(file.absolutePath, opts)
                }
                if (bmp != null) {
                    pages = pages + ScannedPage(original = bmp)
                    selectedIdx = pages.lastIndex
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val added = withContext(Dispatchers.IO) {
                    uris.mapNotNull { u ->
                        context.contentResolver.openInputStream(u)?.use { input ->
                            BitmapFactory.decodeStream(input)?.let { ScannedPage(original = it) }
                        }
                    }
                }
                if (added.isNotEmpty()) {
                    pages = pages + added
                    selectedIdx = pages.lastIndex
                }
            }
        }
    }

    fun launchCamera() {
        val (file, uri) = storage.createCameraCaptureUri()
        pendingCamFile = file
        cameraLauncher.launch(uri)
    }

    fun buildAndRoute(openEditor: Boolean) {
        if (pages.isEmpty() || isBuilding) return
        scope.launch {
            isBuilding = true
            val out = storage.newOutputFile("Scan_${System.currentTimeMillis() % 100000}")
            withContext(Dispatchers.IO) {
                val processed = pages.map { processScanBitmap(it) }
                PdfEngine.bitmapsToPdf(processed, out)
            }
            isBuilding = false
            if (openEditor) onOpenInEditor(out) else onSavedOnly(out)
        }
    }

    val currentPage = pages.getOrNull(selectedIdx)
    val previewBmp = remember(currentPage) {
        currentPage?.let { processScanBitmap(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text("Document Scanner", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Text("${pages.size} scanned page(s)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    if (pages.isNotEmpty()) {
                        TextButton(onClick = { buildAndRoute(openEditor = false) }, enabled = !isBuilding) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save")
                        }
                        Button(
                            onClick = { buildAndRoute(openEditor = true) },
                            enabled = !isBuilding,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Edit PDF", fontSize = 12.sp)
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().navigationBarsPadding()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (currentPage != null) {
                        // Filter chips
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            itemsIndexed(ScanFilter.entries) { _, f ->
                                FilterChip(
                                    selected = currentPage.filter == f,
                                    onClick = {
                                        pages = pages.mapIndexed { idx, p ->
                                            if (idx == selectedIdx) p.copy(filter = f) else p
                                        }
                                    },
                                    label = { Text(f.label, fontSize = 11.sp) }
                                )
                            }
                        }

                        // Rotate, Crop Trim, Delete row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    pages = pages.mapIndexed { idx, p ->
                                        if (idx == selectedIdx) p.copy(rotationDeg = (p.rotationDeg + 90) % 360) else p
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Rotate", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    pages = pages.mapIndexed { idx, p ->
                                        if (idx == selectedIdx) p.copy(trimPercent = (p.trimPercent + 5) % 20) else p
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Trim ${currentPage.trimPercent}%", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    pages = pages.filterIndexed { idx, _ -> idx != selectedIdx }
                                    selectedIdx = (selectedIdx - 1).coerceAtLeast(0)
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Delete", color = Color(0xFFEF5350), fontSize = 12.sp)
                            }
                        }
                    }

                    // Camera & Gallery Capture buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { launchCamera() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (pages.isEmpty()) "Scan with Camera" else "Add Camera Page")
                        }
                        OutlinedButton(
                            onClick = { galleryLauncher.launch(arrayOf("image/*")) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("From Gallery")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (pages.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(pages) { idx, _ ->
                        FilterChip(
                            selected = idx == selectedIdx,
                            onClick = { selectedIdx = idx },
                            label = { Text("Page ${idx + 1}", fontSize = 12.sp) }
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF141414)),
                contentAlignment = Alignment.Center
            ) {
                if (previewBmp != null) {
                    Image(
                        bitmap = previewBmp.asImageBitmap(),
                        contentDescription = "Scanned Page",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(12.dp)
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color(0xFF888888), modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Ready to Scan Documents", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Color.White)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Capture pages with your camera or pick photos, apply Magic Color / B&W filters, then tap Edit PDF to add text, images, or signatures.",
                            fontSize = 13.sp,
                            color = Color(0xFF9E9E9E)
                        )
                    }
                }
                if (isBuilding) CircularProgressIndicator()
            }
        }
    }
}
