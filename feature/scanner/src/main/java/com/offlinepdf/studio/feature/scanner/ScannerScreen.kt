package com.offlinepdf.studio.feature.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.SystemClock
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
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Hd
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RotateRight
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.offlinepdf.studio.core.imageprocessing.ScanQualityMode
import com.offlinepdf.studio.core.imageprocessing.ScannedPageItem
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

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
                        previewRevision = state.previewRevision,
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
            "Offline PDF Studio uses your rear camera locally to detect document edges and scan pages. Nothing is ever uploaded.",
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

/**
 * Rock-solid 60 FPS CameraX Stage:
 * - Binds CameraX ONCE via LaunchedEffect (zero blinking!)
 * - Matches Preview + ImageCapture + ImageAnalysis with FIT_CENTER + UseCaseGroup ViewPort (WYSIWYG!)
 * - Direct two-finger pinch-to-zoom + tap-to-focus on preview (no zoom slider, no timer, no flip)
 * - HD / Fast quality toggle + smooth Adobe Scan-style Auto-Capture
 */
@Composable
private fun CameraCaptureStage(
    state: ScannerUiState,
    viewModel: ScannerViewModel,
    onImportGallery: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val stabilityTracker = remember { QuadStabilityTracker() }
    val lastAnalysisMs = remember { AtomicLong(0L) }

    var detectedQuad by remember { mutableStateOf<DocumentQuad?>(null) }
    var stabilityProgress by remember { mutableFloatStateOf(0f) }
    var currentZoomRatio by remember { mutableFloatStateOf(1f) }
    var isCapturing by remember { mutableStateOf(false) }

    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    val latestAutoEnabled by rememberUpdatedState(state.autoCaptureEnabled)
    val latestFlashEnabled by rememberUpdatedState(state.flashEnabled)

    fun triggerCapture(quadSnapshot: DocumentQuad?) {
        val capture = imageCaptureRef ?: return
        if (isCapturing) return
        isCapturing = true
        val outFile = viewModel.createCaptureTempFile()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outFile).build()
        capture.flashMode = if (latestFlashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF

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

    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            analysisExecutor.shutdown()
        }
    }

    // Bind CameraX ONCE when PreviewView is ready or HD/Fast mode toggles (NEVER inside AndroidView.update!)
    LaunchedEffect(previewViewRef, state.qualityMode) {
        val pv = previewViewRef ?: return@LaunchedEffect
        pv.post {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val provider = runCatching { cameraProviderFuture.get() }.getOrNull() ?: return@addListener

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = pv.surfaceProvider
            }

            val captureMode = if (state.qualityMode == ScanQualityMode.HD) {
                ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
            } else {
                ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(captureMode)
                .build()
            imageCaptureRef = capture

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                try {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastAnalysisMs.get() < 110L) {
                        return@setAnalyzer
                    }
                    lastAnalysisMs.set(now)

                    val rotation = imageProxy.imageInfo.rotationDegrees
                    val bmp = imageProxy.toBitmap()
                    val quad = viewModel.edgeDetector.detectDocumentQuad(bmp, rotation)
                    bmp.recycle()

                    val stability = stabilityTracker.onFrame(quad)
                    ContextCompat.getMainExecutor(context).execute {
                        detectedQuad = stability.detectedQuad
                        stabilityProgress = stability.progress
                        if (latestAutoEnabled && stability.shouldAutoCapture && !isCapturing) {
                            triggerCapture(stability.detectedQuad)
                        }
                    }
                } catch (_: Throwable) {
                    // Ignore transient analysis errors
                } finally {
                    imageProxy.close()
                }
            }

            runCatching {
                provider.unbindAll()
                val vp = pv.viewPort
                val boundCamera = if (vp != null) {
                    val group = UseCaseGroup.Builder()
                        .addUseCase(preview)
                        .addUseCase(capture)
                        .addUseCase(analysis)
                        .setViewPort(vp)
                        .build()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, group)
                } else {
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture,
                        analysis
                    )
                }
                cameraRef = boundCamera
                boundCamera.cameraControl.enableTorch(latestFlashEnabled)
            }
        }, ContextCompat.getMainExecutor(context))
        }
    }

    LaunchedEffect(state.flashEnabled, cameraRef) {
        cameraRef?.cameraControl?.enableTorch(state.flashEnabled)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Minimal Top Toolbar: Auto/Manual | Flash | HD/Fast | Gallery
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF101216))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = state.autoCaptureEnabled,
                onClick = viewModel::toggleAutoCapture,
                label = { Text(if (state.autoCaptureEnabled) "Auto" else "Manual") }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = state.qualityMode == ScanQualityMode.HD,
                    onClick = viewModel::toggleQualityMode,
                    label = { Text(state.qualityMode.displayName) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Hd, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
                IconButton(onClick = viewModel::toggleFlash) {
                    Icon(
                        if (state.flashEnabled) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                        contentDescription = "Flash",
                        tint = if (state.flashEnabled) Color(0xFFFDE047) else Color.White
                    )
                }
                IconButton(onClick = onImportGallery) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Import from gallery", tint = Color.White)
                }
            }
        }

        // Camera Preview Area with direct 2-finger pinch-to-zoom, tap-to-focus, and smooth quad overlay
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        val pv = previewViewRef ?: return@detectTapGestures
                        val cam = cameraRef ?: return@detectTapGestures
                        val point = pv.meteringPointFactory.createPoint(tapOffset.x, tapOffset.y)
                        cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                    }
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } >= 2) {
                                    val zoomChange = event.calculateZoom()
                                    val cam = cameraRef
                                    if (cam != null && zoomChange != 1f) {
                                        val zoomState = cam.cameraInfo.zoomState.value
                                        val minZ = zoomState?.minZoomRatio ?: 1f
                                        val maxZ = (zoomState?.maxZoomRatio ?: 6f).coerceAtMost(8f)
                                        val nextZ = (currentZoomRatio * zoomChange).coerceIn(minZ, maxZ)
                                        currentZoomRatio = nextZ
                                        cam.cameraControl.setZoomRatio(nextZ)
                                    }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                }
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FIT_CENTER
                        previewViewRef = this
                    }
                }
            )

            // Smooth Adobe Scan-style Document Boundary Overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                val q = detectedQuad ?: return@Canvas
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
                val activeColor = if (stabilityProgress >= 0.55f) Color(0xFF22C55E) else Color(0xFF38BDF8)
                drawPath(path, activeColor.copy(alpha = 0.16f))
                drawPath(path, activeColor, style = Stroke(width = 5f))
                pts.forEach { pt ->
                    drawCircle(Color.White, radius = 10f, center = pt)
                    drawCircle(activeColor, radius = 6f, center = pt)
                }
            }

            // Clean top status pill
            Surface(
                color = Color(0xB8111827),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                val guidance = when {
                    detectedQuad != null && state.autoCaptureEnabled && stabilityProgress >= 0.5f ->
                        "Hold steady..."
                    detectedQuad != null && state.autoCaptureEnabled ->
                        "Document detected"
                    detectedQuad != null ->
                        "Ready — tap shutter"
                    else ->
                        "Point at document (pinch to zoom)"
                }
                Text(
                    text = guidance,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            // Zoom indicator pill (only shown when zoomed > 1.05x)
            if (currentZoomRatio > 1.05f) {
                Surface(
                    color = Color(0xB8111827),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = String.format("%.1fx", currentZoomRatio),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Clean Bottom Shutter Bar (no cluttered zoom slider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF101216))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.pages.isNotEmpty()) {
                FilledTonalButton(onClick = { viewModel.openStage(ScannerStage.PAGES_REVIEW) }) {
                    Text("${state.pages.size} Pages")
                }
            } else {
                Spacer(Modifier.width(92.dp))
            }

            Box(contentAlignment = Alignment.Center) {
                if (state.autoCaptureEnabled && stabilityProgress > 0.15f) {
                    CircularProgressIndicator(
                        progress = { stabilityProgress },
                        color = Color(0xFF22C55E),
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(76.dp)
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    modifier = Modifier
                        .size(64.dp)
                        .border(3.dp, Color(0xFF93C5FD), CircleShape)
                        .clickable { triggerCapture(detectedQuad) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.CameraAlt,
                            contentDescription = "Capture",
                            tint = Color.Black,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            if (state.pages.isNotEmpty()) {
                Button(onClick = { viewModel.openStage(ScannerStage.PAGES_REVIEW) }) {
                    Text("Done")
                }
            } else {
                Spacer(Modifier.width(92.dp))
            }
        }
    }
}

/**
 * Computes exact rendered rectangle for ContentScale.Fit inside a Box of size [boxSize].
 */
private fun computeFitRect(imageW: Int, imageH: Int, boxSize: IntSize): Rect {
    if (imageW <= 0 || imageH <= 0 || boxSize.width <= 0 || boxSize.height <= 0) {
        return Rect(0f, 0f, boxSize.width.toFloat(), boxSize.height.toFloat())
    }
    val scale = min(boxSize.width.toFloat() / imageW, boxSize.height.toFloat() / imageH)
    val drawnW = imageW * scale
    val drawnH = imageH * scale
    val left = (boxSize.width - drawnW) / 2f
    val top = (boxSize.height - drawnH) / 2f
    return Rect(left, top, left + drawnW, top + drawnH)
}

@Composable
private fun CropPerspectiveStage(
    page: ScannedPageItem,
    previewRevision: Int,
    viewModel: ScannerViewModel
) {
    var previewBitmap by remember(page.rawImagePath, previewRevision) { mutableStateOf<Bitmap?>(null) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var activeHandle by remember { mutableIntStateOf(-1) }

    // Stable reference so pointerInput NEVER resets mid-drag!
    val latestQuad by rememberUpdatedState(page.quad)

    LaunchedEffect(page.rawImagePath, previewRevision) {
        previewBitmap = viewModel.loadPreviewBitmap(page.rawImagePath, maxSide = 1400)
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

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { boxSize = it },
            contentAlignment = Alignment.Center
        ) {
            val bmp = previewBitmap
            if (bmp != null && !bmp.isRecycled) {
                val fitRect = remember(bmp.width, bmp.height, boxSize) {
                    computeFitRect(bmp.width, bmp.height, boxSize)
                }

                // ContentScale.Fit preserves exact portrait aspect ratio + live fineRotation preview!
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Captured document",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(rotationZ = page.fineRotationDegrees)
                )

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(boxSize, bmp.width, bmp.height) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    if (fitRect.width <= 1f || fitRect.height <= 1f) return@detectDragGestures
                                    val nx = ((start.x - fitRect.left) / fitRect.width).coerceIn(0f, 1f)
                                    val ny = ((start.y - fitRect.top) / fitRect.height).coerceIn(0f, 1f)
                                    val corners = latestQuad.asList()
                                    activeHandle = corners.indices.minByOrNull { idx ->
                                        hypot((corners[idx].x - nx).toDouble(), (corners[idx].y - ny).toDouble())
                                    } ?: -1
                                },
                                onDragEnd = { activeHandle = -1 },
                                onDragCancel = { activeHandle = -1 },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (activeHandle !in 0..3 || fitRect.width <= 1f || fitRect.height <= 1f) {
                                        return@detectDragGestures
                                    }
                                    val dx = dragAmount.x / fitRect.width
                                    val dy = dragAmount.y / fitRect.height
                                    val q = latestQuad
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
                        Offset(fitRect.left + q.topLeft.x * fitRect.width, fitRect.top + q.topLeft.y * fitRect.height),
                        Offset(fitRect.left + q.topRight.x * fitRect.width, fitRect.top + q.topRight.y * fitRect.height),
                        Offset(fitRect.left + q.bottomRight.x * fitRect.width, fitRect.top + q.bottomRight.y * fitRect.height),
                        Offset(fitRect.left + q.bottomLeft.x * fitRect.width, fitRect.top + q.bottomLeft.y * fitRect.height)
                    )
                    val path = Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        lineTo(pts[1].x, pts[1].y)
                        lineTo(pts[2].x, pts[2].y)
                        lineTo(pts[3].x, pts[3].y)
                        close()
                    }
                    drawPath(path, Color(0x3338BDF8))
                    drawPath(path, Color(0xFF38BDF8), style = Stroke(width = 5f))
                    pts.forEachIndexed { idx, pt ->
                        val isSelected = idx == activeHandle
                        drawCircle(Color.White, radius = if (isSelected) 28f else 22f, center = pt)
                        drawCircle(
                            if (isSelected) Color(0xFF22C55E) else Color(0xFF0284C7),
                            radius = if (isSelected) 18f else 14f,
                            center = pt
                        )
                    }
                }

                // Live circular Corner Magnifier Loupe while dragging any corner!
                if (activeHandle in 0..3) {
                    val activePt = page.quad.asList()[activeHandle]
                    val loupeAlign = if (activePt.x < 0.5f) Alignment.TopEnd else Alignment.TopStart
                    CornerMagnifierLoupe(
                        source = bmp,
                        normX = activePt.x,
                        normY = activePt.y,
                        modifier = Modifier
                            .align(loupeAlign)
                            .padding(10.dp)
                    )
                }
            } else {
                CircularProgressIndicator()
            }
        }

        // Live Fine Rotation Slider & 90° Rotate Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Tilt: ${page.fineRotationDegrees.toInt()}°",
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
private fun CornerMagnifierLoupe(
    source: Bitmap,
    normX: Float,
    normY: Float,
    modifier: Modifier = Modifier
) {
    val cropSize = (min(source.width, source.height) * 0.22f).roundToInt().coerceIn(80, 260)
    val cx = (normX * source.width).roundToInt()
    val cy = (normY * source.height).roundToInt()
    val srcLeft = (cx - cropSize / 2).coerceIn(0, (source.width - cropSize).coerceAtLeast(0))
    val srcTop = (cy - cropSize / 2).coerceIn(0, (source.height - cropSize).coerceAtLeast(0))
    val actualW = min(cropSize, source.width - srcLeft).coerceAtLeast(1)
    val actualH = min(cropSize, source.height - srcTop).coerceAtLeast(1)

    Box(
        modifier = modifier
            .size(104.dp)
            .clip(CircleShape)
            .border(2.5.dp, Color(0xFF38BDF8), CircleShape)
            .background(Color.Black)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawImage(
                image = source.asImageBitmap(),
                srcOffset = IntOffset(srcLeft, srcTop),
                srcSize = IntSize(actualW, actualH),
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
            )
            // Crosshair at exact corner
            val midX = size.width / 2f
            val midY = size.height / 2f
            drawLine(Color(0xFF22C55E), Offset(midX - 18f, midY), Offset(midX + 18f, midY), strokeWidth = 3f)
            drawLine(Color(0xFF22C55E), Offset(midX, midY - 18f), Offset(midX, midY + 18f), strokeWidth = 3f)
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
        previewBitmap = viewModel.loadPreviewBitmap(targetPath, maxSide = 1400)
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
            FilterChip(
                selected = showOriginal,
                onClick = { showOriginal = !showOriginal },
                label = { Text(if (showOriginal) "Original" else "Compare") },
                leadingIcon = { Icon(Icons.Outlined.Compare, null, modifier = Modifier.size(16.dp)) }
            )
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
                ) { Text("Apply All") }
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
            ) { Text("Review ($totalPages)") }
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
