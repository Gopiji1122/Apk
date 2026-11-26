package com.offlinepdf.studio.feature.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinepdf.studio.core.imageprocessing.DocumentQuad
import com.offlinepdf.studio.core.imageprocessing.ExportFormat
import com.offlinepdf.studio.core.imageprocessing.ExportMargin
import com.offlinepdf.studio.core.imageprocessing.ExportOrientation
import com.offlinepdf.studio.core.imageprocessing.ExportPageSize
import com.offlinepdf.studio.core.imageprocessing.NormalizedPoint
import com.offlinepdf.studio.core.imageprocessing.QuadStabilityTracker
import com.offlinepdf.studio.core.imageprocessing.ScanFilterMode
import com.offlinepdf.studio.core.imageprocessing.ScannedPageItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.hypot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onOpenExportedPdf: (String) -> Unit = {},
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importGalleryImages(uris)
    }

    var showExportSheet by remember { mutableStateOf(false) }

    val exportPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> if (uri != null) viewModel.exportToSafUri(uri) }

    val exportJpegLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/jpeg")
    ) { uri -> if (uri != null) viewModel.exportToSafUri(uri) }

    val exportPngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri -> if (uri != null) viewModel.exportToSafUri(uri) }

    LaunchedEffect(state.statusMessage) {
        val msg = state.statusMessage ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        viewModel.clearStatusMessage()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (state.stage) {
            ScannerStage.CAMERA -> {
                if (!hasCameraPermission) {
                    CameraPermissionPrompt(
                        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        onImportGallery = { galleryPicker.launch("image/*") }
                    )
                } else {
                    CameraCaptureStage(
                        state = state,
                        viewModel = viewModel,
                        onImportGallery = { galleryPicker.launch("image/*") }
                    )
                }
            }
            ScannerStage.CROP -> {
                val active = state.activePage
                if (active != null) {
                    CropPerspectiveStage(
                        page = active,
                        viewModel = viewModel
                    )
                }
            }
            ScannerStage.ENHANCE -> {
                val active = state.activePage
                if (active != null) {
                    EnhanceFilterStage(
                        page = active,
                        totalPages = state.pages.size,
                        previewRevision = state.previewRevision,
                        viewModel = viewModel
                    )
                }
            }
            ScannerStage.PAGES_REVIEW -> {
                PagesReviewStage(
                    pages = state.pages,
                    previewRevision = state.previewRevision,
                    viewModel = viewModel,
                    onAddPage = { viewModel.openStage(ScannerStage.CAMERA) },
                    onExportClick = { showExportSheet = true }
                )
            }
        }

        // Non-blocking processing overlay
        if (state.isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x99000000)),
                contentAlignment = Alignment.Center
            ) {
                Card {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = state.processingMessage.ifBlank { "Processing..." },
                            style = MaterialTheme.typography.titleSmall
                        )
                        state.exportProgress?.let { (cur, total) ->
                            if (total > 0) {
                                LinearProgressIndicator(
                                    progress = { cur.toFloat() / total.toFloat() },
                                    modifier = Modifier.width(180.dp)
                                )
                                Text("Page $cur of $total", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }

    // Export Configuration Bottom Sheet
    if (showExportSheet) {
        val opts = state.exportOptions
        ModalBottomSheet(onDismissRequest = { showExportSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Export Scanned Document (${state.pages.size} pages)", style = MaterialTheme.typography.titleLarge)

                OutlinedTextField(
                    value = opts.title,
                    onValueChange = { viewModel.updateExportOptions(opts.copy(title = it)) },
                    label = { Text("Output Filename") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Format", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportFormat.entries.forEach { fmt ->
                        FilterChip(
                            selected = opts.format == fmt,
                            onClick = { viewModel.updateExportOptions(opts.copy(format = fmt)) },
                            label = { Text(fmt.displayName) }
                        )
                    }
                }

                if (opts.format == ExportFormat.PDF) {
                    Text("Page Size", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ExportPageSize.entries) { sz ->
                            FilterChip(
                                selected = opts.pageSize == sz,
                                onClick = { viewModel.updateExportOptions(opts.copy(pageSize = sz)) },
                                label = { Text(sz.displayName) }
                            )
                        }
                    }

                    Text("Orientation", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportOrientation.entries.forEach { ori ->
                            FilterChip(
                                selected = opts.orientation == ori,
                                onClick = { viewModel.updateExportOptions(opts.copy(orientation = ori)) },
                                label = { Text(ori.displayName) }
                            )
                        }
                    }

                    Text("Margins", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportMargin.entries.forEach { m ->
                            FilterChip(
                                selected = opts.margin == m,
                                onClick = { viewModel.updateExportOptions(opts.copy(margin = m)) },
                                label = { Text(m.displayName) }
                            )
                        }
                    }
                }

                if (opts.format != ExportFormat.PNG) {
                    Text("Image Quality: ${opts.jpegQuality}%", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = opts.jpegQuality.toFloat(),
                        onValueChange = { viewModel.updateExportOptions(opts.copy(jpegQuality = it.toInt())) },
                        valueRange = 40f..100f
                    )
                }

                Button(
                    onClick = {
                        showExportSheet = false
                        val base = opts.title.trim().ifBlank { "Scanned_Document" }
                        when (opts.format) {
                            ExportFormat.PDF -> exportPdfLauncher.launch("$base.pdf")
                            ExportFormat.JPEG -> exportJpegLauncher.launch("$base.jpg")
                            ExportFormat.PNG -> exportPngLauncher.launch("$base.png")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Choose Folder & Save ${opts.format.extension.uppercase()}")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Exported PDF Ready Dialog
    state.lastExportedPdfUri?.let { exportedUri ->
        AlertDialog(
            onDismissRequest = viewModel::clearLastExportedPdfUri,
            title = { Text("Scan Saved as PDF") },
            text = { Text("Your scanned PDF has been saved locally and added to Recently Scanned.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearLastExportedPdfUri()
                    onOpenExportedPdf(exportedUri)
                }) { Text("Open PDF") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::clearLastExportedPdfUri) { Text("Done") }
            }
        )
    }
}

@Composable
private fun CameraPermissionPrompt(
    onRequestPermission: () -> Unit,
    onImportGallery: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))
        Text("Camera Permission Needed", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Offline PDF Studio uses your camera locally to detect document edges and scan pages. Nothing is ever uploaded.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRequestPermission) { Text("Grant Camera Permission") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onImportGallery) {
            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Or Import Photos from Gallery")
        }
    }
}

@Composable
private fun CameraCaptureStage(
    state: ScannerUiState,
    viewModel: ScannerViewModel,
    onImportGallery: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val stabilityTracker = remember { QuadStabilityTracker() }

    var detectedQuad by remember { mutableStateOf<DocumentQuad?>(null) }
    var stabilityProgress by remember { mutableFloatStateOf(0f) }
    var zoomRatio by remember { mutableFloatStateOf(0f) }
    var countdown by remember { mutableIntStateOf(0) }
    var isCapturing by remember { mutableStateOf(false) }

    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    fun triggerCapture(quadSnapshot: DocumentQuad?) {
        val capture = imageCaptureRef ?: return
        if (isCapturing) return
        isCapturing = true
        val outFile = viewModel.createCaptureTempFile()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outFile).build()
        capture.flashMode = if (state.flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    isCapturing = false
                    stabilityTracker.reset()
                    viewModel.onRawPhotoCaptured(outFile, quadSnapshot)
                }

                override fun onError(exception: ImageCaptureException) {
                    isCapturing = false
                    Toast.makeText(context, "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    fun triggerCaptureWithTimer(quadSnapshot: DocumentQuad?) {
        if (state.timerSeconds <= 0) {
            triggerCapture(quadSnapshot)
        } else {
            scope.launch {
                for (sec in state.timerSeconds downTo 1) {
                    countdown = sec
                    delay(1000)
                }
                countdown = 0
                triggerCapture(quadSnapshot)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    LaunchedEffect(state.flashEnabled, cameraRef) {
        cameraRef?.cameraControl?.enableTorch(state.flashEnabled)
    }

    LaunchedEffect(zoomRatio, cameraRef) {
        cameraRef?.cameraControl?.setLinearZoom(zoomRatio.coerceIn(0f, 1f))
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Live CameraX Preview
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        val pv = previewViewRef ?: return@detectTapGestures
                        val cam = cameraRef ?: return@detectTapGestures
                        val point = pv.meteringPointFactory.createPoint(tapOffset.x, tapOffset.y)
                        val action = FocusMeteringAction.Builder(point).build()
                        cam.cameraControl.startFocusAndMetering(action)
                    }
                },
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewViewRef = this
                }
            },
            update = { previewView ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val provider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build()
                    imageCaptureRef = capture

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        try {
                            val bmp = imageProxy.toBitmap()
                            val quad = viewModel.edgeDetector.detectDocumentQuad(bmp)
                            bmp.recycle()
                            val stability = stabilityTracker.onFrame(quad)
                            ContextCompat.getMainExecutor(context).execute {
                                detectedQuad = stability.detectedQuad
                                stabilityProgress = stability.progress
                                if (state.autoCaptureEnabled && stability.shouldAutoCapture && !isCapturing) {
                                    triggerCapture(stability.detectedQuad)
                                }
                            }
                        } catch (_: Throwable) {
                            // Ignore transient frame errors so camera preview never freezes
                        } finally {
                            imageProxy.close()
                        }
                    }

                    val selector = if (state.useFrontCamera) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }

                    runCatching {
                        provider.unbindAll()
                        cameraRef = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture, analysis)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        )

        // Live Document Boundary Detection Polygon Overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val q = detectedQuad ?: return@Canvas
            val path = Path().apply {
                moveTo(q.topLeft.x * size.width, q.topLeft.y * size.height)
                lineTo(q.topRight.x * size.width, q.topRight.y * size.height)
                lineTo(q.bottomRight.x * size.width, q.bottomRight.y * size.height)
                lineTo(q.bottomLeft.x * size.width, q.bottomLeft.y * size.height)
                close()
            }
            val color = if (stabilityProgress >= 0.6f) Color(0xFF22C55E) else Color(0xFFF59E0B)
            drawPath(path, color.copy(alpha = 0.22f))
            drawPath(path, color, style = Stroke(width = 6f))
        }

        // Top Camera Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x88000000))
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = state.autoCaptureEnabled,
                onClick = viewModel::toggleAutoCapture,
                label = { Text(if (state.autoCaptureEnabled) "Auto" else "Manual") }
            )
            Row {
                IconButton(onClick = viewModel::toggleFlash) {
                    Icon(
                        if (state.flashEnabled) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                        contentDescription = "Flash",
                        tint = Color.White
                    )
                }
                IconButton(onClick = viewModel::cycleTimer) {
                    Icon(Icons.Outlined.Timer, contentDescription = "Timer", tint = if (state.timerSeconds > 0) Color(0xFF93C5FD) else Color.White)
                }
                IconButton(onClick = viewModel::toggleCameraLens) {
                    Icon(Icons.Outlined.Cameraswitch, contentDescription = "Switch camera", tint = Color.White)
                }
                IconButton(onClick = onImportGallery) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Import from gallery", tint = Color.White)
                }
            }
        }

        // Alignment guidance badge
        Surface(
            color = Color(0xAA111827),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 68.dp)
        ) {
            val guidance = when {
                countdown > 0 -> "Capturing in $countdown..."
                detectedQuad != null && state.autoCaptureEnabled -> "Document detected — hold steady (${(stabilityProgress * 100).toInt()}%)"
                detectedQuad != null -> "Edges detected — tap shutter to capture"
                else -> "Align document inside frame"
            }
            Text(
                text = guidance,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // Bottom Controls: Zoom + Shutter + Page Queue Badge
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xAA000000))
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.BottomCenter)
        ) {
            Slider(
                value = zoomRatio,
                onValueChange = { zoomRatio = it },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.pages.isNotEmpty()) {
                    FilledTonalButton(onClick = { viewModel.openStage(ScannerStage.PAGES_REVIEW) }) {
                        Text("${state.pages.size} Pages")
                    }
                } else {
                    Spacer(Modifier.width(88.dp))
                }

                // Shutter Button
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    modifier = Modifier
                        .size(68.dp)
                        .border(4.dp, Color(0xFF93C5FD), CircleShape)
                        .clickable { triggerCaptureWithTimer(detectedQuad) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.CameraAlt,
                            contentDescription = "Capture",
                            tint = Color.Black,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                if (state.pages.isNotEmpty()) {
                    Button(onClick = { viewModel.openStage(ScannerStage.PAGES_REVIEW) }) {
                        Text("Done")
                    }
                } else {
                    Spacer(Modifier.width(88.dp))
                }
            }
        }
    }
}

@Composable
private fun CropPerspectiveStage(
    page: ScannedPageItem,
    viewModel: ScannerViewModel
) {
    var previewBitmap by remember(page.rawImagePath) { mutableStateOf<Bitmap?>(null) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var activeHandle by remember { mutableIntStateOf(-1) }

    LaunchedEffect(page.rawImagePath) {
        previewBitmap = viewModel.loadPreviewBitmap(page.rawImagePath, maxSide = 1280)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E1015))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Crop & Perspective", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Row {
                TextButton(onClick = viewModel::autoDetectActiveQuad) { Text("Auto") }
                TextButton(onClick = viewModel::resetActiveQuad) { Text("Reset") }
            }
        }

        // Interactive 4-corner quad editor
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { boxSize = it },
            contentAlignment = Alignment.Center
        ) {
            val bmp = previewBitmap
            if (bmp != null && !bmp.isRecycled) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Captured document",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(page.quad, boxSize) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    if (boxSize.width == 0 || boxSize.height == 0) return@detectDragGestures
                                    val nx = start.x / boxSize.width
                                    val ny = start.y / boxSize.height
                                    val corners = page.quad.asList()
                                    activeHandle = corners.indices.minByOrNull { idx ->
                                        hypot((corners[idx].x - nx).toDouble(), (corners[idx].y - ny).toDouble())
                                    } ?: -1
                                },
                                onDragEnd = { activeHandle = -1 },
                                onDragCancel = { activeHandle = -1 },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (activeHandle !in 0..3 || boxSize.width == 0 || boxSize.height == 0) return@detectDragGestures
                                    val dx = dragAmount.x / boxSize.width
                                    val dy = dragAmount.y / boxSize.height
                                    val q = page.quad
                                    val updated = when (activeHandle) {
                                        0 -> q.copy(topLeft = NormalizedPoint(q.topLeft.x + dx, q.topLeft.y + dy).clamped())
                                        1 -> q.copy(topRight = NormalizedPoint(q.topRight.x + dx, q.topRight.y + dy).clamped())
                                        2 -> q.copy(bottomRight = NormalizedPoint(q.bottomRight.x + dx, q.bottomRight.y + dy).clamped())
                                        else -> q.copy(bottomLeft = NormalizedPoint(q.bottomLeft.x + dx, q.bottomLeft.y + dy).clamped())
                                    }
                                    viewModel.updateActiveQuad(updated)
                                }
                            )
                        }
                ) {
                    val q = page.quad
                    val pts = listOf(
                        Offset(q.topLeft.x * size.width, q.topLeft.y * size.height),
                        Offset(q.topRight.x * size.width, q.topRight.y * size.height),
                        Offset(q.bottomRight.x * size.width, q.bottomRight.y * size.height),
                        Offset(q.bottomLeft.x * size.width, q.bottomLeft.y * size.height)
                    )
                    val path = Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        lineTo(pts[1].x, pts[1].y)
                        lineTo(pts[2].x, pts[2].y)
                        lineTo(pts[3].x, pts[3].y)
                        close()
                    }
                    drawPath(path, Color(0xFF38BDF8), style = Stroke(width = 5f))
                    pts.forEach { pt ->
                        drawCircle(Color.White, radius = 22f, center = pt)
                        drawCircle(Color(0xFF0284C7), radius = 14f, center = pt)
                    }
                }
            } else {
                CircularProgressIndicator()
            }
        }

        // Fine rotation & 90° rotation
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Fine: ${page.fineRotationDegrees.toInt()}°",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium
            )
            Slider(
                value = page.fineRotationDegrees,
                onValueChange = viewModel::setActiveFineRotation,
                valueRange = -15f..15f,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = viewModel::rotateActivePage90) {
                Icon(Icons.Outlined.RotateRight, contentDescription = "Rotate 90", tint = Color.White)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = viewModel::retakeActivePage,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.Refresh, null)
                Spacer(Modifier.width(6.dp))
                Text("Retake")
            }
            Button(
                onClick = viewModel::applyCropAndProceedToEnhance,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.Check, null)
                Spacer(Modifier.width(6.dp))
                Text("Apply & Enhance")
            }
        }
    }
}

@Composable
private fun EnhanceFilterStage(
    page: ScannedPageItem,
    totalPages: Int,
    previewRevision: Int,
    viewModel: ScannerViewModel
) {
    var showOriginal by remember { mutableStateOf(false) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val targetPath = if (showOriginal) page.warpedImagePath else page.enhancedImagePath

    LaunchedEffect(targetPath, previewRevision) {
        previewBitmap = viewModel.loadPreviewBitmap(targetPath, maxSide = 1280)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E1015))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Enhance Document", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Row {
                FilterChip(
                    selected = showOriginal,
                    onClick = { showOriginal = !showOriginal },
                    label = { Text(if (showOriginal) "Original" else "Compare") },
                    leadingIcon = { Icon(Icons.Outlined.Compare, null, modifier = Modifier.size(16.dp)) }
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val bmp = previewBitmap
            if (bmp != null && !bmp.isRecycled) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Enhanced document preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CircularProgressIndicator()
            }
        }

        // All 10 Professional Enhancement Modes
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ScanFilterMode.entries) { mode ->
                FilterChip(
                    selected = page.filterMode == mode,
                    onClick = {
                        showOriginal = false
                        viewModel.setActiveFilter(mode)
                    },
                    label = { Text(mode.displayName) }
                )
            }
        }

        // Custom Adjustment Sliders
        if (page.filterMode == ScanFilterMode.CUSTOM) {
            val c = page.customAdjustments
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Brightness (${c.brightness.toInt()})", color = Color.White, style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = c.brightness,
                    onValueChange = { viewModel.setActiveCustomAdjustments(c.copy(brightness = it)) },
                    valueRange = -60f..60f
                )
                Text("Contrast (${String.format("%.2f", c.contrast)})", color = Color.White, style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = c.contrast,
                    onValueChange = { viewModel.setActiveCustomAdjustments(c.copy(contrast = it)) },
                    valueRange = 0.5f..2.0f
                )
                Text("Saturation (${String.format("%.2f", c.saturation)})", color = Color.White, style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = c.saturation,
                    onValueChange = { viewModel.setActiveCustomAdjustments(c.copy(saturation = it)) },
                    valueRange = 0f..2.0f
                )
                Text("Sharpness (${String.format("%.2f", c.sharpness)})", color = Color.White, style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = c.sharpness,
                    onValueChange = { viewModel.setActiveCustomAdjustments(c.copy(sharpness = it)) },
                    valueRange = 0f..1.5f
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (totalPages > 1) {
                OutlinedButton(
                    onClick = viewModel::applyActiveFilterToAllPages,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Apply All")
                }
            }
            OutlinedButton(
                onClick = { viewModel.openStage(ScannerStage.CAMERA) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.Add, null)
                Spacer(Modifier.width(4.dp))
                Text("Add Page")
            }
            Button(
                onClick = { viewModel.openStage(ScannerStage.PAGES_REVIEW) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Review ($totalPages)")
            }
        }
    }
}

@Composable
private fun PagesReviewStage(
    pages: List<ScannedPageItem>,
    previewRevision: Int,
    viewModel: ScannerViewModel,
    onAddPage: () -> Unit,
    onExportClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Scanned Pages (${pages.size})", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Unlimited disk-backed pages • Tap any page to edit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = onExportClick, enabled = pages.isNotEmpty()) {
                Icon(Icons.Outlined.PictureAsPdf, null)
                Spacer(Modifier.width(6.dp))
                Text("Export")
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 156.dp),
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            itemsIndexed(pages, key = { _, item -> item.id }) { index, item ->
                ScannedPageReviewCard(
                    index = index,
                    total = pages.size,
                    item = item,
                    previewRevision = previewRevision,
                    loadThumb = { viewModel.loadPreviewBitmap(item.enhancedImagePath, maxSide = 420) },
                    onCrop = { viewModel.selectPage(index, ScannerStage.CROP) },
                    onEnhance = { viewModel.selectPage(index, ScannerStage.ENHANCE) },
                    onRotate = { viewModel.rotatePageInQueue(index) },
                    onDuplicate = { viewModel.duplicatePage(index) },
                    onMoveLeft = { viewModel.movePage(index, index - 1) },
                    onMoveRight = { viewModel.movePage(index, index + 1) },
                    onDelete = { viewModel.deletePage(index) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(onClick = onAddPage, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Scan More Pages")
            }
            Button(onClick = onExportClick, enabled = pages.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Text("Finish & Save PDF")
            }
        }
    }
}

@Composable
private fun ScannedPageReviewCard(
    index: Int,
    total: Int,
    item: ScannedPageItem,
    previewRevision: Int,
    loadThumb: suspend () -> Bitmap?,
    onCrop: () -> Unit,
    onEnhance: () -> Unit,
    onRotate: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onDelete: () -> Unit
) {
    var thumb by remember(item.id, previewRevision) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.id, previewRevision) { thumb = loadThumb() }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .clickable(onClick = onEnhance),
                contentAlignment = Alignment.Center
            ) {
                val bmp = thumb
                if (bmp != null && !bmp.isRecycled) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Scanned page ${index + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
                Surface(
                    color = Color(0xCC111827),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                ) {
                    Text(
                        text = "#${index + 1}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onMoveLeft, enabled = index > 0, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Move left", modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onCrop, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Crop, contentDescription = "Crop", modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onEnhance, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.AutoFixHigh, contentDescription = "Filter", modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onRotate, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.RotateRight, contentDescription = "Rotate", modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDuplicate, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Duplicate", modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onMoveRight, enabled = index < total - 1, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Move right", modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
