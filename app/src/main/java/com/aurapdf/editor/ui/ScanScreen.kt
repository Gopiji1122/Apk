package com.aurapdf.editor.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurapdf.editor.data.PdfStorage
import com.aurapdf.editor.engine.PdfEngine
import com.aurapdf.editor.theme.AcrobatColors
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ScanFilter(val label: String) {
    ORIGINAL("Original"),
    AUTO_COLOR("Auto Color"),
    GRAY("Grayscale"),
    BW("B&W Crisp"),
    WHITEBOARD("Lighten")
}

data class ScannedPage(
    val id: Long = System.nanoTime(),
    val original: Bitmap,
    val rotationDeg: Int = 0,
    val filter: ScanFilter = ScanFilter.AUTO_COLOR,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f
)

private fun processScanBitmap(page: ScannedPage): Bitmap {
    var src = page.original
    if (page.rotationDeg % 360 != 0) {
        val m = Matrix().apply { postRotate(page.rotationDeg.toFloat()) }
        src = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }
    val x0 = (src.width * page.cropLeft).toInt().coerceIn(0, src.width - 10)
    val y0 = (src.height * page.cropTop).toInt().coerceIn(0, src.height - 10)
    val cw = (src.width * (page.cropRight - page.cropLeft)).toInt().coerceIn(10, src.width - x0)
    val ch = (src.height * (page.cropBottom - page.cropTop)).toInt().coerceIn(10, src.height - y0)
    if (x0 > 0 || y0 > 0 || cw < src.width || ch < src.height) {
        src = Bitmap.createBitmap(src, x0, y0, cw, ch)
    }
    if (page.filter == ScanFilter.ORIGINAL) return src

    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val paint = Paint()
    val cm = ColorMatrix()
    when (page.filter) {
        ScanFilter.AUTO_COLOR -> {
            val k = 1.30f
            val s = (-0.5f * k + 0.5f) * 255f + 14f
            cm.set(floatArrayOf(k, 0f, 0f, 0f, s, 0f, k, 0f, 0f, s, 0f, 0f, k, 0f, s, 0f, 0f, 0f, 1f, 0f))
        }
        ScanFilter.GRAY -> cm.setSaturation(0f)
        ScanFilter.BW -> {
            cm.setSaturation(0f)
            cm.postConcat(ColorMatrix(floatArrayOf(2.3f, 0f, 0f, 0f, -165f, 0f, 2.3f, 0f, 0f, -165f, 0f, 0f, 2.3f, 0f, -165f, 0f, 0f, 0f, 1f, 0f)))
        }
        ScanFilter.WHITEBOARD -> {
            val k = 1.15f
            val s = 28f
            cm.set(floatArrayOf(k, 0f, 0f, 0f, s, 0f, k, 0f, 0f, s, 0f, 0f, k, 0f, s, 0f, 0f, 0f, 1f, 0f))
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
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var pages by remember { mutableStateOf(listOf<ScannedPage>()) }
    var selectedIdx by remember { mutableIntStateOf(0) }
    var pendingCamFile by remember { mutableStateOf<File?>(null) }
    var isBuilding by remember { mutableStateOf(false) }
    var isCropMode by remember { mutableStateOf(false) }
    var scanStatus by remember { mutableStateOf<String?>(null) }

    // Interactive Crop Rect state
    var cLeft by remember { mutableFloatStateOf(0.05f) }
    var cTop by remember { mutableFloatStateOf(0.05f) }
    var cRight by remember { mutableFloatStateOf(0.95f) }
    var cBottom by remember { mutableFloatStateOf(0.95f) }
    var imgBoxSize by remember { mutableStateOf(IntSize(1, 1)) }

    // Google ML Kit Pro Document Scanner (Auto Scan + Manual Scan + 4-Corner Perspective Crop + Filters + ML Clean!)
    val mlKitScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanRes = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val scannedPages = scanRes?.pages
            if (!scannedPages.isNullOrEmpty()) {
                scope.launch {
                    val loaded = withContext(Dispatchers.IO) {
                        scannedPages.mapNotNull { p ->
                            context.contentResolver.openInputStream(p.imageUri)?.use { input ->
                                BitmapFactory.decodeStream(input)?.let { ScannedPage(original = it, filter = ScanFilter.ORIGINAL) }
                            }
                        }
                    }
                    if (loaded.isNotEmpty()) {
                        pages = pages + loaded
                        selectedIdx = pages.lastIndex
                        scanStatus = "Imported ${loaded.size} pro-scanned page(s). Tap Edit PDF or Save."
                    }
                }
            } else {
                val pdfUri = scanRes?.pdf?.uri
                if (pdfUri != null) {
                    scope.launch {
                        val imported = withContext(Dispatchers.IO) {
                            storage.importPdfFromUri(pdfUri, "Scan_${System.currentTimeMillis() % 100000}")
                        }
                        onOpenInEditor(imported)
                    }
                }
            }
        }
    }

    fun launchAutoManualMlScanner() {
        if (activity == null) return
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(50)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        val client = GmsDocumentScanning.getClient(options)
        client.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                mlKitScannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e ->
                scanStatus = "Auto Scanner unavailable on this device (${e.message}). Use Manual Camera below."
            }
    }

    val manualCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingCamFile
        if (ok && f != null && f.exists()) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeFile(f.absolutePath, opts)
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

    fun launchManualCamera() {
        val (file, uri) = storage.createCameraCaptureUri()
        pendingCamFile = file
        manualCameraLauncher.launch(uri)
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
    val previewBmp = remember(currentPage, isCropMode) {
        if (currentPage == null) null
        else if (isCropMode) processScanBitmap(currentPage.copy(cropLeft = 0f, cropTop = 0f, cropRight = 1f, cropBottom = 1f))
        else processScanBitmap(currentPage)
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
                        Text("Adobe-Style Scanner", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Text("${pages.size} page(s) • Auto & Manual", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    if (pages.isNotEmpty() && !isCropMode) {
                        TextButton(onClick = { buildAndRoute(openEditor = false) }, enabled = !isBuilding) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Save")
                        }
                        Button(
                            onClick = { buildAndRoute(openEditor = true) },
                            enabled = !isBuilding,
                            colors = ButtonDefaults.buttonColors(containerColor = AcrobatColors.EditPink),
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
                    if (isCropMode && currentPage != null) {
                        Text("Drag any corner circle on the document to crop", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { isCropMode = false }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                            Button(
                                onClick = {
                                    pages = pages.mapIndexed { idx, p ->
                                        if (idx == selectedIdx) p.copy(cropLeft = cLeft, cropTop = cTop, cropRight = cRight, cropBottom = cBottom)
                                        else p
                                    }
                                    isCropMode = false
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Apply Crop")
                            }
                        }
                    } else {
                        if (currentPage != null) {
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
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = {
                                        cLeft = currentPage.cropLeft.coerceIn(0f, 0.4f)
                                        cTop = currentPage.cropTop.coerceIn(0f, 0.4f)
                                        cRight = currentPage.cropRight.coerceIn(0.6f, 1f)
                                        cBottom = currentPage.cropBottom.coerceIn(0.6f, 1f)
                                        isCropMode = true
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Crop", fontSize = 12.sp)
                                }
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

                        // Primary Auto/Manual Scanner + Manual Camera + Gallery
                        Button(
                            onClick = { launchAutoManualMlScanner() },
                            colors = ButtonDefaults.buttonColors(containerColor = AcrobatColors.Red),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Auto / Manual Pro Scan (Edge Detect + Crop + Clean)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { launchManualCamera() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Manual Shot", fontSize = 12.sp)
                            }
                            OutlinedButton(onClick = { galleryLauncher.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Gallery", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (scanStatus != null) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Text(scanStatus!!, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
            if (pages.size > 1) {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(pages) { idx, _ ->
                        FilterChip(selected = idx == selectedIdx, onClick = { selectedIdx = idx }, label = { Text("Page ${idx + 1}", fontSize = 12.sp) })
                    }
                }
            }

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF121214)).padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (previewBmp != null) {
                    val aspect = previewBmp.width.toFloat() / previewBmp.height.coerceAtLeast(1)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspect.coerceIn(0.45f, 1.8f))
                            .onSizeChanged { imgBoxSize = it }
                    ) {
                        Image(
                            bitmap = previewBmp.asImageBitmap(),
                            contentDescription = "Scanned Page",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (isCropMode) {
                            Canvas(
                                modifier = Modifier.fillMaxSize().pointerInput(imgBoxSize) {
                                    var activeCorner = -1
                                    detectDragGestures(
                                        onDragStart = { pos ->
                                            val w = imgBoxSize.width.toFloat().coerceAtLeast(1f)
                                            val h = imgBoxSize.height.toFloat().coerceAtLeast(1f)
                                            val corners = listOf(
                                                Offset(cLeft * w, cTop * h),
                                                Offset(cRight * w, cTop * h),
                                                Offset(cLeft * w, cBottom * h),
                                                Offset(cRight * w, cBottom * h)
                                            )
                                            activeCorner = corners.indices.minByOrNull { (corners[it] - pos).getDistance() } ?: -1
                                        },
                                        onDrag = { change, drag ->
                                            change.consume()
                                            val dx = drag.x / imgBoxSize.width.toFloat().coerceAtLeast(1f)
                                            val dy = drag.y / imgBoxSize.height.toFloat().coerceAtLeast(1f)
                                            when (activeCorner) {
                                                0 -> { cLeft = (cLeft + dx).coerceIn(0f, cRight - 0.15f); cTop = (cTop + dy).coerceIn(0f, cBottom - 0.15f) }
                                                1 -> { cRight = (cRight + dx).coerceIn(cLeft + 0.15f, 1f); cTop = (cTop + dy).coerceIn(0f, cBottom - 0.15f) }
                                                2 -> { cLeft = (cLeft + dx).coerceIn(0f, cRight - 0.15f); cBottom = (cBottom + dy).coerceIn(cTop + 0.15f, 1f) }
                                                3 -> { cRight = (cRight + dx).coerceIn(cLeft + 0.15f, 1f); cBottom = (cBottom + dy).coerceIn(cTop + 0.15f, 1f) }
                                            }
                                        }
                                    )
                                }
                            ) {
                                val lx = cLeft * size.width
                                val ty = cTop * size.height
                                val rx = cRight * size.width
                                val by = cBottom * size.height
                                drawRect(
                                    color = Color(0xFF2196F3),
                                    topLeft = Offset(lx, ty),
                                    size = Size(rx - lx, by - ty),
                                    style = Stroke(width = 4f)
                                )
                                listOf(Offset(lx, ty), Offset(rx, ty), Offset(lx, by), Offset(rx, by)).forEach { pt ->
                                    drawCircle(Color(0xFF2196F3), radius = 18f, center = pt)
                                    drawCircle(Color.White, radius = 9f, center = pt)
                                }
                            }
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = AcrobatColors.Red, modifier = Modifier.size(60.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Pro Document Scanner", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Tap Auto / Manual Pro Scan for automatic edge detection, 4-corner perspective crop, filters, and stain cleanup—or use Manual Shot & Gallery below.",
                            fontSize = 13.sp,
                            color = Color(0xFFA0A4AE)
                        )
                    }
                }
                if (isBuilding) CircularProgressIndicator()
            }
        }
    }
}
