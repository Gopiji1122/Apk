package com.aurapdf.editor.ui.screens

import android.app.Activity
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.aurapdf.editor.data.PdfRepository
import com.aurapdf.editor.model.NormPoint
import com.aurapdf.editor.model.ScanFilter
import com.aurapdf.editor.model.ScannedPageDraft
import com.aurapdf.editor.ui.theme.AcrobatRed
import com.aurapdf.editor.ui.theme.ToolCompressGreen
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    repository: PdfRepository,
    onBack: () -> Unit,
    onSavedToRecent: () -> Unit,
    onOpenInEditor: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val pages = remember { mutableStateListOf<ScannedPageDraft>() }
    var activeIndex by remember { mutableIntStateOf(0) }
    var docTitle by remember { mutableStateOf("Scan_${System.currentTimeMillis() % 100000}") }
    var isSaving by remember { mutableStateOf(false) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    BackHandler { onBack() }

    val mlKitScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val scanDir = File(context.cacheDir, "scans").apply { mkdirs() }
            scanResult?.pages?.forEachIndexed { idx, page ->
                val targetImg = File(scanDir, "mlkit_${System.currentTimeMillis()}_$idx.jpg")
                context.contentResolver.openInputStream(page.imageUri)?.use { input ->
                    FileOutputStream(targetImg).use { out -> input.copyTo(out) }
                }
                if (targetImg.exists()) {
                    pages.add(
                        ScannedPageDraft(
                            imagePath = targetImg.absolutePath,
                            filter = ScanFilter.ORIGINAL,
                            topLeft = NormPoint(0f, 0f),
                            topRight = NormPoint(1f, 0f),
                            bottomRight = NormPoint(1f, 1f),
                            bottomLeft = NormPoint(0f, 1f)
                        )
                    )
                }
            }
            if (pages.isNotEmpty()) activeIndex = pages.lastIndex
        }
    }

    fun launchProMlKitScanner() {
        if (activity == null) return
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(50)
            .setResultFormats(RESULT_FORMAT_JPEG, RESULT_FORMAT_PDF)
            .setScannerMode(SCANNER_MODE_FULL)
            .build()
        val scanner = GmsDocumentScanning.getClient(options)
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                mlKitScannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener {
                Toast.makeText(
                    context,
                    "ML Kit Scanner unavailable on this device — use Manual Camera / Gallery below.",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCameraFile
        if (success && file != null && file.exists() && file.length() > 0L) {
            pages.add(ScannedPageDraft(imagePath = file.absolutePath))
            activeIndex = pages.lastIndex
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val scanDir = File(context.cacheDir, "scans").apply { mkdirs() }
            uris.forEachIndexed { idx, uri ->
                val out = File(scanDir, "gal_${System.currentTimeMillis()}_$idx.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(out).use { output -> input.copyTo(output) }
                }
                if (out.exists()) {
                    pages.add(ScannedPageDraft(imagePath = out.absolutePath))
                }
            }
            if (pages.isNotEmpty()) activeIndex = pages.lastIndex
        }
    }

    fun buildAndFinish(openInEditor: Boolean) {
        if (pages.isEmpty()) {
            Toast.makeText(context, "Scan or add at least one page first", Toast.LENGTH_SHORT).show()
            return
        }
        isSaving = true
        scope.launch {
            runCatching {
                repository.scannedPagesToPdf(pages.toList(), docTitle)
            }.onSuccess { pdfFile ->
                isSaving = false
                if (openInEditor) {
                    onOpenInEditor(pdfFile.absolutePath)
                } else {
                    Toast.makeText(context, "Saved ${pdfFile.name}", Toast.LENGTH_SHORT).show()
                    onSavedToRecent()
                }
            }.onFailure {
                isSaving = false
                Toast.makeText(context, "Scan export error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Pro Document Scanner", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(
                        onClick = { buildAndFinish(openInEditor = false) },
                        enabled = pages.isNotEmpty() && !isSaving
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold, color = ToolCompressGreen)
                    }
                    Button(
                        onClick = { buildAndFinish(openInEditor = true) },
                        enabled = pages.isNotEmpty() && !isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = AcrobatRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Edit PDF", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                onClick = { launchProMlKitScanner() },
                colors = CardDefaults.cardColors(containerColor = AcrobatRed),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.20f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.DocumentScanner,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.padding(start = 14.dp)) {
                        Text(
                            "Launch Pro Auto / Manual Scanner",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "Auto edge detection, auto capture, 4-corner warp & stain cleanup",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.90f)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val camDir = File(context.cacheDir, "camera").apply { mkdirs() }
                        val photoFile = File(camDir, "cap_${System.currentTimeMillis()}.jpg")
                        pendingCameraFile = photoFile
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            photoFile
                        )
                        cameraLauncher.launch(uri)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Manual Camera")
                }
                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Gallery Multi-Add")
                }
            }

            if (pages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.DocumentScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            "Tap Pro Scanner above or capture pages with Manual Camera / Gallery",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            } else {
                val safeIdx = activeIndex.coerceIn(0, pages.lastIndex)
                val currentDraft = pages[safeIdx]
                val liveColorFilter = remember(currentDraft.filter) {
                    buildComposeScanColorFilter(currentDraft.filter)
                }
                val animatedRotation by animateFloatAsState(
                    targetValue = currentDraft.rotationDeg.toFloat(),
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                    label = "scan_rot"
                )

                OutlinedTextField(
                    value = docTitle,
                    onValueChange = { docTitle = it },
                    label = { Text("Output PDF Title") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Interactive 4-Corner Draggable Crop Studio with Live Filter & Rotation Preview
                Surface(
                    color = Color(0xFF0A0B0D),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val bmp = remember(currentDraft.imagePath) {
                            BitmapFactory.decodeFile(currentDraft.imagePath)?.asImageBitmap()
                        }
                        if (bmp != null) {
                            val aspect = bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat()
                            Box(
                                modifier = Modifier
                                    .aspectRatio(aspect.coerceIn(0.45f, 1.8f))
                                    .graphicsLayer(rotationZ = animatedRotation)
                            ) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = "Scanned page",
                                    contentScale = ContentScale.FillBounds,
                                    colorFilter = liveColorFilter,
                                    modifier = Modifier.fillMaxSize()
                                )

                                var activeCorner by remember { mutableIntStateOf(-1) }
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(safeIdx) {
                                            detectDragGestures(
                                                onDragStart = { offset ->
                                                    val w = size.width.toFloat()
                                                    val h = size.height.toFloat()
                                                    val d = pages[safeIdx]
                                                    val corners = listOf(
                                                        Offset(d.topLeft.x * w, d.topLeft.y * h),
                                                        Offset(d.topRight.x * w, d.topRight.y * h),
                                                        Offset(d.bottomRight.x * w, d.bottomRight.y * h),
                                                        Offset(d.bottomLeft.x * w, d.bottomLeft.y * h)
                                                    )
                                                    activeCorner = corners.indices.minByOrNull { idx ->
                                                        hypot(
                                                            offset.x - corners[idx].x,
                                                            offset.y - corners[idx].y
                                                        )
                                                    } ?: -1
                                                },
                                                onDrag = { change, _ ->
                                                    change.consume()
                                                    if (activeCorner in 0..3) {
                                                        val nx = (change.position.x / size.width).coerceIn(0f, 1f)
                                                        val ny = (change.position.y / size.height).coerceIn(0f, 1f)
                                                        val d = pages[safeIdx]
                                                        pages[safeIdx] = when (activeCorner) {
                                                            0 -> d.copy(topLeft = NormPoint(nx, ny))
                                                            1 -> d.copy(topRight = NormPoint(nx, ny))
                                                            2 -> d.copy(bottomRight = NormPoint(nx, ny))
                                                            else -> d.copy(bottomLeft = NormPoint(nx, ny))
                                                        }
                                                    }
                                                },
                                                onDragEnd = { activeCorner = -1 }
                                            )
                                        }
                                ) {
                                    val d = pages[safeIdx]
                                    val p0 = Offset(d.topLeft.x * size.width, d.topLeft.y * size.height)
                                    val p1 = Offset(d.topRight.x * size.width, d.topRight.y * size.height)
                                    val p2 = Offset(d.bottomRight.x * size.width, d.bottomRight.y * size.height)
                                    val p3 = Offset(d.bottomLeft.x * size.width, d.bottomLeft.y * size.height)

                                    val quad = Path().apply {
                                        moveTo(p0.x, p0.y)
                                        lineTo(p1.x, p1.y)
                                        lineTo(p2.x, p2.y)
                                        lineTo(p3.x, p3.y)
                                        close()
                                    }
                                    val fullRect = Path().apply {
                                        moveTo(0f, 0f)
                                        lineTo(size.width, 0f)
                                        lineTo(size.width, size.height)
                                        lineTo(0f, size.height)
                                        close()
                                    }
                                    val outerMask = Path().apply {
                                        op(fullRect, quad, PathOperation.Difference)
                                    }
                                    drawPath(outerMask, color = Color.Black.copy(alpha = 0.42f))
                                    drawPath(quad, color = AcrobatRed, style = Stroke(width = 5f))
                                    listOf(p0, p1, p2, p3).forEach { corner ->
                                        drawCircle(color = Color.White, radius = 17f, center = corner)
                                        drawCircle(color = AcrobatRed, radius = 12f, center = corner)
                                    }
                                }
                            }
                        }

                        // Live Filter & Rotation Badge
                        Surface(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                        ) {
                            Text(
                                text = "${currentDraft.filter.label} • ${currentDraft.rotationDeg}°",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Filters Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScanFilter.entries.forEach { f ->
                        FilterChip(
                            selected = currentDraft.filter == f,
                            onClick = { pages[safeIdx] = currentDraft.copy(filter = f) },
                            label = { Text(f.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AcrobatRed,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                // Bottom Page Switcher + Rotate + Delete Page
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        pages.forEachIndexed { idx, _ ->
                            FilterChip(
                                selected = idx == safeIdx,
                                onClick = { activeIndex = idx },
                                label = { Text("P${idx + 1}") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AcrobatRed,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                    Row {
                        IconButton(onClick = {
                            pages[safeIdx] = currentDraft.copy(rotationDeg = (currentDraft.rotationDeg + 90) % 360)
                        }) {
                            Icon(Icons.Default.RotateRight, contentDescription = "Rotate +90°")
                        }
                        IconButton(onClick = {
                            pages.removeAt(safeIdx)
                            activeIndex = (safeIdx - 1).coerceAtLeast(0)
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Page", tint = AcrobatRed)
                        }
                    }
                }
            }
        }
    }
}

private fun buildComposeScanColorFilter(filter: ScanFilter): ColorFilter? {
    return when (filter) {
        ScanFilter.ORIGINAL -> null
        ScanFilter.AUTO_COLOR -> ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    1.25f, 0f, 0f, 0f, -18f,
                    0f, 1.25f, 0f, 0f, -18f,
                    0f, 0f, 1.25f, 0f, -18f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        ScanFilter.GRAYSCALE -> ColorFilter.colorMatrix(
            ColorMatrix().apply { setToSaturation(0f) }
        )
        ScanFilter.BW_CRISP -> {
            val mat = ColorMatrix().apply { setToSaturation(0f) }
            val contrast = ColorMatrix(
                floatArrayOf(
                    2.1f, 0f, 0f, 0f, -110f,
                    0f, 2.1f, 0f, 0f, -110f,
                    0f, 0f, 2.1f, 0f, -110f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            mat.timesAssign(contrast)
            ColorFilter.colorMatrix(mat)
        }
        ScanFilter.LIGHTEN -> ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    1.15f, 0f, 0f, 0f, 28f,
                    0f, 1.15f, 0f, 0f, 28f,
                    0f, 0f, 1.15f, 0f, 28f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }
}
