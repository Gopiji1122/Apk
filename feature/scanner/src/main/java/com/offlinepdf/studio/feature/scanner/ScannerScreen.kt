package com.offlinepdf.studio.feature.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Size
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
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Hd
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
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
import androidx.compose.ui.geometry.Size as GeoSize
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onOpenExportedPdf: (String) -> Unit = {},
    onFullscreenChange: (Boolean) -> Unit = {},
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Camera / crop / enhance use the whole screen; review keeps the bottom bar.
    LaunchedEffect(state.stage) { onFullscreenChange(state.stage != ScannerStage.PAGES_REVIEW) }
    DisposableEffect(Unit) { onDispose { onFullscreenChange(false) } }

    // Back steps through the scan flow instead of leaving the tab and losing context.
    androidx.activity.compose.BackHandler(enabled = state.stage != ScannerStage.CAMERA || state.isProcessing) {
        viewModel.navigateBack()
    }

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

    val exportZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (uri != null) viewModel.exportToSafUri(uri) }

    LaunchedEffect(state.statusMessage) {
        val msg = state.statusMessage ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
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

        if (state.isProcessing && state.processingMessage == "Enhancing...") {
            // Filter / slider tweaks must not block the UI; show a slim progress line instead.
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                color = Color(0xFF22C7D6),
                trackColor = Color.Transparent
            )
        } else if (state.isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x99000000)),
                contentAlignment = Alignment.Center
            ) {
                Card(shape = RoundedCornerShape(20.dp)) {
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
        val isMultiImageZip = opts.format != ExportFormat.PDF && state.pages.size > 1
        ModalBottomSheet(onDismissRequest = { showExportSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Export Document (${state.pages.size} pages)", style = MaterialTheme.typography.titleLarge)

                OutlinedTextField(
                    value = opts.title,
                    onValueChange = { viewModel.updateExportOptions(opts.copy(title = it)) },
                    label = { Text("Filename") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
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

                if (isMultiImageZip) {
                    Text(
                        "All ${state.pages.size} pages will be packaged cleanly into a single ZIP archive.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
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
                    PremiumLabeledSlider(
                        label = "Quality",
                        valueText = "${opts.jpegQuality}%",
                        value = opts.jpegQuality.toFloat(),
                        valueRange = 40f..100f,
                        onValueChange = { viewModel.updateExportOptions(opts.copy(jpegQuality = it.toInt())) }
                    )
                }

                Button(
                    onClick = {
                        showExportSheet = false
                        val base = opts.title.trim().ifBlank { "Scanned_Document" }
                        when {
                            opts.format == ExportFormat.PDF -> exportPdfLauncher.launch("$base.pdf")
                            isMultiImageZip -> exportZipLauncher.launch("${base}_images.zip")
                            opts.format == ExportFormat.JPEG -> exportJpegLauncher.launch("$base.jpg")
                            else -> exportPngLauncher.launch("$base.png")
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    val btnLabel = when {
                        opts.format == ExportFormat.PDF -> "Save PDF"
                        isMultiImageZip -> "Save All ${state.pages.size} Images (ZIP)"
                        else -> "Save ${opts.format.extension.uppercase()}"
                    }
                    Text(btnLabel, fontWeight = FontWeight.SemiBold)
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
                Row {
                    TextButton(onClick = {
                        scope.launch {
                            runCatching {
                                val shareUri = viewModel.prepareShareUri(exportedUri)
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(android.content.Intent.EXTRA_STREAM, shareUri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(send, null))
                            }
                        }
                    }) { Text("Share") }
                    TextButton(onClick = viewModel::clearLastExportedPdfUri) { Text("Done") }
                }
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
        Button(
            onClick = onRequestPermission,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.height(46.dp)
        ) { Text("Grant Camera Permission") }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onImportGallery,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.height(46.dp)
        ) {
            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Import from Gallery")
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

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val stabilityTracker = remember { QuadStabilityTracker() }
    val lastAnalysisMs = remember { AtomicLong(0L) }

    var detectedQuad by remember { mutableStateOf<DocumentQuad?>(null) }
    var stabilityProgress by remember { mutableFloatStateOf(0f) }
    var qualityHint by remember { mutableStateOf<String?>(null) }
    var currentZoomRatio by remember { mutableFloatStateOf(1f) }
    var isCapturing by remember { mutableStateOf(false) }
    var shutterFlash by remember { mutableStateOf(false) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    val haptics = com.offlinepdf.studio.core.designsystem.components.rememberStudioHaptics()
    val flashAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (shutterFlash) 0.85f else 0f,
        animationSpec = androidx.compose.animation.core.tween(if (shutterFlash) 40 else 220),
        label = "shutter"
    )
    LaunchedEffect(shutterFlash) { if (shutterFlash) { kotlinx.coroutines.delay(70); shutterFlash = false } }
    LaunchedEffect(focusPoint) { if (focusPoint != null) { kotlinx.coroutines.delay(900); focusPoint = null } }

    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    val latestAutoEnabled by rememberUpdatedState(state.autoCaptureEnabled)
    val latestFlashEnabled by rememberUpdatedState(state.flashEnabled)

    fun triggerCapture(quadSnapshot: DocumentQuad?) {
        val capture = imageCaptureRef ?: return
        if (isCapturing) return
        isCapturing = true
        shutterFlash = true
        haptics.confirm()
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

                // Lightweight 640x480 analysis resolution for lower CPU/heat/battery usage
                val resSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(640, 480),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()

                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resSelector)
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
                        val frameResult = viewModel.edgeDetector.analyzeFrame(bmp, rotation)
                        bmp.recycle()

                        val stability = stabilityTracker.onFrameEvaluated(frameResult)
                        ContextCompat.getMainExecutor(context).execute {
                            detectedQuad = stability.detectedQuad
                            stabilityProgress = stability.progress
                            qualityHint = stability.qualityHint
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF101216))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = state.autoCaptureEnabled,
                onClick = viewModel::toggleAutoCapture,
                label = { Text(if (state.autoCaptureEnabled) "Auto" else "Manual") }
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        val pv = previewViewRef ?: return@detectTapGestures
                        val cam = cameraRef ?: return@detectTapGestures
                        focusPoint = tapOffset
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

            Canvas(modifier = Modifier.fillMaxSize()) {
                val q = detectedQuad ?: return@Canvas
                // The preview is letter-boxed (FIT_CENTER, 4:3 sensor): map the normalized quad into
                // the *visible picture* rectangle, not the whole box, or the outline drifts.
                val portrait = size.width <= size.height
                val fit = computeFitRect(
                    if (portrait) 3 else 4, if (portrait) 4 else 3,
                    IntSize(size.width.toInt(), size.height.toInt())
                )
                fun mapPt(p: NormalizedPoint) = Offset(fit.left + p.x * fit.width, fit.top + p.y * fit.height)
                val pts = listOf(mapPt(q.topLeft), mapPt(q.topRight), mapPt(q.bottomRight), mapPt(q.bottomLeft))
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

            focusPoint?.let { fp ->
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(Color.White, radius = 34f, center = fp, style = Stroke(width = 3f))
                    drawCircle(Color.White.copy(alpha = 0.5f), radius = 6f, center = fp)
                }
            }
            if (flashAlpha > 0.01f) {
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAlpha)))
            }

            Surface(
                color = Color(0xBF111827),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                val guidance = when {
                    qualityHint != null -> qualityHint!!
                    detectedQuad != null && state.autoCaptureEnabled && stabilityProgress >= 0.5f -> "Hold steady..."
                    detectedQuad != null && state.autoCaptureEnabled -> "Document detected"
                    detectedQuad != null -> "Ready — tap shutter"
                    else -> "Point at document (pinch to zoom)"
                }
                Text(
                    text = guidance,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            if (currentZoomRatio > 1.05f) {
                Surface(
                    color = Color(0xBF111827),
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF101216))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.pages.isNotEmpty()) {
                FilledTonalButton(
                    onClick = { viewModel.openStage(ScannerStage.PAGES_REVIEW) },
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("${state.pages.size} Pages", fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
                Button(
                    onClick = { viewModel.openStage(ScannerStage.PAGES_REVIEW) },
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text("Done", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Spacer(Modifier.width(92.dp))
            }
        }
    }
}

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

/**
 * Premium Curved Protractor / Arc Rotation Dial (Apple Photos / Lightroom style):
 * Replaces the old-fashioned linear tilt slider with a sleek curved tick-arc,
 * smooth horizontal drag scrubbing, and magnetic snap-to-zero.
 */
@Composable
private fun CurvedRotationDial(
    angleDegrees: Float,
    onAngleChange: (Float) -> Unit,
    onRotate90: () -> Unit,
    modifier: Modifier = Modifier
) {
    val latestAngle by rememberUpdatedState(angleDegrees)
    val latestOnChange by rememberUpdatedState(onAngleChange)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Live degree pill (tap to reset to 0°)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (abs(angleDegrees) > 0.2f) Color(0xFF1E3A5F) else Color(0xFF1F232B),
            modifier = Modifier
                .width(56.dp)
                .clickable { onAngleChange(0f) }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                Text(
                    text = String.format("%+.1f°", angleDegrees),
                    color = if (abs(angleDegrees) > 0.2f) Color(0xFF7DD3FC) else Color(0xFFD1D5DB),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Smooth Curved Arc Tick Dial
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaDeg = -dragAmount * 0.08f
                        var next = (latestAngle + deltaDeg).coerceIn(-15f, 15f)
                        if (abs(next) < 0.35f) next = 0f
                        latestOnChange(next)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                val arcRadius = size.width * 1.15f
                val arcCenterY = size.height * 0.22f + arcRadius

                // Draw tick marks from -15° to +15° along a smooth upward curve
                for (deg in -15..15) {
                    val relDeg = deg - angleDegrees
                    val rad = Math.toRadians(relDeg.toDouble() * 1.35)
                    if (abs(relDeg) > 16f) continue

                    val isMajor = deg % 5 == 0
                    val isZero = deg == 0
                    val tickLen = when {
                        isZero -> 18.dp.toPx()
                        isMajor -> 13.dp.toPx()
                        else -> 7.dp.toPx()
                    }

                    val outerR = arcRadius
                    val innerR = arcRadius - tickLen
                    val startX = centerX + (outerR * sin(rad)).toFloat()
                    val startY = arcCenterY - (outerR * cos(rad)).toFloat()
                    val endX = centerX + (innerR * sin(rad)).toFloat()
                    val endY = arcCenterY - (innerR * cos(rad)).toFloat()

                    val alpha = (1f - (abs(relDeg) / 17f)).coerceIn(0.15f, 1f)
                    val tickColor = when {
                        isZero -> Color(0xFF38BDF8).copy(alpha = alpha)
                        isMajor -> Color.White.copy(alpha = alpha * 0.85f)
                        else -> Color(0xFF9CA3AF).copy(alpha = alpha * 0.6f)
                    }
                    drawLine(
                        color = tickColor,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = if (isMajor) 2.2f else 1.4f,
                        cap = StrokeCap.Round
                    )
                }

                // Glowing stationary center indicator needle
                drawLine(
                    color = Color(0xFF38BDF8),
                    start = Offset(centerX, 2.dp.toPx()),
                    end = Offset(centerX, 26.dp.toPx()),
                    strokeWidth = 3.2f,
                    cap = StrokeCap.Round
                )
            }
        }

        // Clean 90° Rotate Pill Button
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1F232B),
            modifier = Modifier
                .size(40.dp)
                .clickable(onClick = onRotate90)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.RotateRight,
                    contentDescription = "Rotate 90",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun PremiumLabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    labelColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = labelColor, style = MaterialTheme.typography.labelMedium)
            Text(
                valueText,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.height(32.dp)
        )
    }
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
    var livePreview by remember { mutableStateOf(false) }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.navigateBack() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text("Crop & Perspective", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = { livePreview = !livePreview }) {
                    Text(if (livePreview) "Edit corners" else "Preview")
                }
                if (!livePreview) {
                    TextButton(onClick = viewModel::autoDetectActiveQuad) { Text("Auto") }
                    TextButton(onClick = viewModel::resetActiveQuad) { Text("Reset") }
                }
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

                if (livePreview) {
                    StraightenPreview(
                        bitmap = bmp,
                        quad = page.quad,
                        angleDegrees = page.fineRotationDegrees,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Captured document",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
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
                }
            } else {
                CircularProgressIndicator()
            }
        }

        // Sleek Curved Protractor Rotation Dial
        CurvedRotationDial(
            angleDegrees = page.fineRotationDegrees,
            onAngleChange = viewModel::setActiveFineRotation,
            onRotate90 = viewModel::rotateActivePage90
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = viewModel::retakeActivePage,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Retake")
            }
            Button(
                onClick = viewModel::applyCropAndProceedToEnhance,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Icon(Icons.Outlined.Check, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Apply", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Live "what you will get" preview: perspective-corrects the quad with a 4-point matrix and applies
 * the fine rotation exactly like the real pipeline (white wedges at the corners), with a thirds grid.
 */
@Composable
private fun StraightenPreview(
    bitmap: Bitmap,
    quad: DocumentQuad,
    angleDegrees: Float,
    modifier: Modifier = Modifier
) {
    val paint = remember { android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG) }
    Canvas(modifier = modifier) {
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        fun px(p: NormalizedPoint) = Offset(p.x * bw, p.y * bh)
        val tl = px(quad.topLeft); val tr = px(quad.topRight)
        val br = px(quad.bottomRight); val bl = px(quad.bottomLeft)
        val docW = maxOf(1f, (hypot((tr.x - tl.x).toDouble(), (tr.y - tl.y).toDouble()) +
            hypot((br.x - bl.x).toDouble(), (br.y - bl.y).toDouble())).toFloat() / 2f)
        val docH = maxOf(1f, (hypot((bl.x - tl.x).toDouble(), (bl.y - tl.y).toDouble()) +
            hypot((br.x - tr.x).toDouble(), (br.y - tr.y).toDouble())).toFloat() / 2f)
        val scale = min(size.width / docW, size.height / docH) * 0.94f
        val dw = docW * scale
        val dh = docH * scale
        val left = (size.width - dw) / 2f
        val top = (size.height - dh) / 2f

        val src = floatArrayOf(tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y)
        val dst = floatArrayOf(left, top, left + dw, top, left + dw, top + dh, left, top + dh)
        val matrix = android.graphics.Matrix()
        if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) return@Canvas

        drawIntoCanvas { canvas ->
            val rect = Rect(left, top, left + dw, top + dh)
            canvas.save()
            canvas.clipRect(rect.left, rect.top, rect.right, rect.bottom)
            canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, androidx.compose.ui.graphics.Paint().apply { color = Color.White })
            canvas.save()
            canvas.rotate(angleDegrees, rect.center.x, rect.center.y)
            canvas.clipRect(rect.left, rect.top, rect.right, rect.bottom)
            canvas.nativeCanvas.drawBitmap(bitmap, matrix, paint)
            canvas.restore()
            canvas.restore()
        }
        val grid = Color.White.copy(alpha = 0.35f)
        for (i in 1..2) {
            drawLine(grid, Offset(left + dw * i / 3f, top), Offset(left + dw * i / 3f, top + dh), strokeWidth = 1.5f)
            drawLine(grid, Offset(left, top + dh * i / 3f), Offset(left + dw, top + dh * i / 3f), strokeWidth = 1.5f)
        }
        drawRect(Color.White.copy(alpha = 0.6f), Offset(left, top), GeoSize(dw, dh), style = Stroke(width = 2f))
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.navigateBack() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to crop", tint = Color.White)
                }
                Text("Enhance Document", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PremiumLabeledSlider(
                    label = "Brightness",
                    valueText = "${c.brightness.toInt()}",
                    value = c.brightness,
                    valueRange = -60f..60f,
                    onValueChange = { viewModel.setActiveCustomAdjustments(c.copy(brightness = it)) },
                    labelColor = Color.White
                )
                PremiumLabeledSlider(
                    label = "Contrast",
                    valueText = String.format("%.2f", c.contrast),
                    value = c.contrast,
                    valueRange = 0.5f..2.0f,
                    onValueChange = { viewModel.setActiveCustomAdjustments(c.copy(contrast = it)) },
                    labelColor = Color.White
                )
                PremiumLabeledSlider(
                    label = "Saturation",
                    valueText = String.format("%.2f", c.saturation),
                    value = c.saturation,
                    valueRange = 0f..2.0f,
                    onValueChange = { viewModel.setActiveCustomAdjustments(c.copy(saturation = it)) },
                    labelColor = Color.White
                )
                PremiumLabeledSlider(
                    label = "Sharpness",
                    valueText = String.format("%.2f", c.sharpness),
                    value = c.sharpness,
                    valueRange = 0f..1.5f,
                    onValueChange = { viewModel.setActiveCustomAdjustments(c.copy(sharpness = it)) },
                    labelColor = Color.White
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
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) { Text("Apply All", fontSize = 13.sp) }
            }
            OutlinedButton(
                onClick = { viewModel.openStage(ScannerStage.CAMERA) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Page", fontSize = 13.sp)
            }
            Button(
                onClick = { viewModel.openStage(ScannerStage.PAGES_REVIEW) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) { Text("Pages ($totalPages)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
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
    var deleteIndex by remember { mutableStateOf<Int?>(null) }
    var confirmDiscardAll by remember { mutableStateOf(false) }

    deleteIndex?.let { idx ->
        AlertDialog(
            onDismissRequest = { deleteIndex = null },
            title = { Text("Delete page ${idx + 1}?") },
            text = { Text("This scanned page will be permanently removed. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deletePage(idx); deleteIndex = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteIndex = null }) { Text("Cancel") } }
        )
    }
    if (confirmDiscardAll) {
        AlertDialog(
            onDismissRequest = { confirmDiscardAll = false },
            title = { Text("Discard all ${pages.size} pages?") },
            text = { Text("Every unsaved scanned page will be deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.discardAllPages(); confirmDiscardAll = false }) {
                    Text("Discard all", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDiscardAll = false }) { Text("Cancel") } }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Minimalist, balanced header with sleek compact Export pill button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Scanned Pages (${pages.size})", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Auto-saved locally • Tap a page to edit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { confirmDiscardAll = true }, enabled = pages.isNotEmpty()) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Discard all pages")
            }
            FilledTonalButton(
                onClick = onExportClick,
                enabled = pages.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.height(38.dp)
            ) {
                Icon(Icons.Outlined.Download, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Export", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                    onDelete = { deleteIndex = index }
                )
            }
        }

        // Clean, uniform bottom action bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onAddPage,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
            ) {
                Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Page", fontWeight = FontWeight.Medium)
            }
            Button(
                onClick = onExportClick,
                enabled = pages.isNotEmpty(),
                shape = RoundedCornerShape(14.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
            ) {
                Icon(Icons.Outlined.Download, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save PDF", fontWeight = FontWeight.SemiBold)
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

    com.offlinepdf.studio.core.designsystem.components.StudioCard(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .clip(RoundedCornerShape(10.dp))
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
                        .padding(6.dp)
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
                IconButton(onClick = onMoveLeft, enabled = index > 0, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Move left", modifier = Modifier.size(15.dp))
                }
                IconButton(onClick = onCrop, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Outlined.Crop, contentDescription = "Crop", modifier = Modifier.size(15.dp))
                }
                IconButton(onClick = onEnhance, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Outlined.AutoFixHigh, contentDescription = "Filter", modifier = Modifier.size(15.dp))
                }
                IconButton(onClick = onRotate, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Outlined.RotateRight, contentDescription = "Rotate", modifier = Modifier.size(15.dp))
                }
                IconButton(onClick = onDuplicate, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Duplicate", modifier = Modifier.size(15.dp))
                }
                IconButton(onClick = onMoveRight, enabled = index < total - 1, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Move right", modifier = Modifier.size(15.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}
