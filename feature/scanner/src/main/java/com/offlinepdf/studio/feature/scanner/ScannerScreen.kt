package com.offlinepdf.studio.feature.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaActionSound
import android.util.Size as AndroidSize
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Filter
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Hd
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Reorder
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinepdf.studio.core.imageprocessing.CleanupBrushMode
import com.offlinepdf.studio.core.imageprocessing.CustomAdjustments
import com.offlinepdf.studio.core.imageprocessing.DocumentQuad
import com.offlinepdf.studio.core.imageprocessing.ExportFormat
import com.offlinepdf.studio.core.imageprocessing.ExportOrientation
import com.offlinepdf.studio.core.imageprocessing.ExportPageSize
import com.offlinepdf.studio.core.imageprocessing.NormalizedPoint
import com.offlinepdf.studio.core.imageprocessing.QuadStabilityTracker
import com.offlinepdf.studio.core.imageprocessing.ScanFilterMode
import com.offlinepdf.studio.core.imageprocessing.ScanQualityMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

private val AccentBlue = Color(0xFF2B84F6)
private val StageCharcoal = Color(0xFF232325)
private val SurfaceDark = Color(0xFF121214)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onOpenExportedPdf: (String) -> Unit,
    onNavigateHome: () -> Unit = {},
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(25)
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importImagesFromGallery(uris)
    }

    var pendingExportFormat by remember { mutableStateOf(ExportFormat.PDF) }
    val exportPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> if (uri != null) viewModel.exportToUri(uri, ExportFormat.PDF) }

    val exportZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (uri != null) viewModel.exportToUri(uri, pendingExportFormat) }

    val exportJpgLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/jpeg")
    ) { uri -> if (uri != null) viewModel.exportToUri(uri, ExportFormat.JPEG) }

    val exportPngLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri -> if (uri != null) viewModel.exportToUri(uri, ExportFormat.PNG) }

    fun launchExport(format: ExportFormat) {
        pendingExportFormat = format
        val name = viewModel.suggestedExportFileName(format)
        val mime = viewModel.suggestedExportMimeType(format)
        when (mime) {
            "application/pdf" -> exportPdfLauncher.launch(name)
            "application/zip" -> exportZipLauncher.launch(name)
            "image/jpeg" -> exportJpgLauncher.launch(name)
            else -> exportPngLauncher.launch(name)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(state.statusMessage, state.errorMessage) {
        val msg = state.errorMessage ?: state.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearMessages()
    }

    BackHandler {
        when {
            state.stage == ScannerStage.REVIEW && state.activeTool != ReviewTool.NONE -> {
                viewModel.cancelActiveTool()
            }
            state.stage == ScannerStage.REVIEW -> {
                viewModel.requestExitScanner(onNavigateHome)
            }
            else -> viewModel.requestExitScanner(onNavigateHome)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (state.stage) {
            ScannerStage.CAMERA -> {
                if (hasCameraPermission) {
                    LiveCameraScannerView(
                        state = state,
                        viewModel = viewModel,
                        onRequestExit = { viewModel.requestExitScanner(onNavigateHome) },
                        onOpenGallery = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                } else {
                    CameraPermissionPrompt(
                        onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        onHome = onNavigateHome
                    )
                }
            }

            ScannerStage.REVIEW -> {
                ReviewAndEditScreen(
                    state = state,
                    viewModel = viewModel,
                    onRequestExit = { viewModel.requestExitScanner(onNavigateHome) },
                    onImportGallery = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onQuickSavePdf = { launchExport(ExportFormat.PDF) },
                    onSharePdf = {
                        viewModel.buildSharePdfIntent { intent ->
                            if (intent != null) {
                                context.startActivity(android.content.Intent.createChooser(intent, "Share PDF"))
                            }
                        }
                    }
                )
            }
        }

        if (state.isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(shape = RoundedCornerShape(16.dp), color = SurfaceDark) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = AccentBlue)
                        Text(state.processingMessage ?: "Processing...", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        if (state.lastExportedPdfUri != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 56.dp, start = 16.dp, end = 16.dp),
                shape = RoundedCornerShape(24.dp),
                color = AccentBlue
            ) {
                Row(
                    modifier = Modifier
                        .clickable {
                            val uri = state.lastExportedPdfUri ?: return@clickable
                            onOpenExportedPdf(uri)
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("PDF Saved — Tap to Open in Reader", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 84.dp)
        )
    }

    // Unsaved Scan Discard Warning Dialog
    if (state.showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDiscardDialog() },
            title = { Text("Discard unsaved scan?") },
            text = {
                Text("You have ${state.pages.size} unsaved page(s). Leaving now will clear all scanned pages.")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDiscardAndExit(onNavigateHome) }) {
                    Text("Discard & Clear", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDiscardDialog() }) {
                    Text("Keep Editing")
                }
            }
        )
    }

    if (state.showRenameDialog) {
        var draft by remember(state.documentTitle) { mutableStateOf(state.documentTitle) }
        AlertDialog(
            onDismissRequest = { viewModel.openRenameDialog(false) },
            title = { Text("Rename Scan") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    label = { Text("Document name") }
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.updateDocumentTitle(draft) }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { viewModel.openRenameDialog(false) }) { Text("Cancel") } }
        )
    }

    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.confirmDeleteActivePage(false) },
            title = { Text("Delete Page ${state.activePageIndex + 1}?") },
            text = { Text("This removes the page from your current scan session.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteActivePage() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { viewModel.confirmDeleteActivePage(false) }) { Text("Cancel") } }
        )
    }

    if (state.showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.openSettingsSheet(false) },
            containerColor = SurfaceDark,
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Scanner Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (viewModel.edgeDetector.isNeuralActive)
                        "Neural 4-Corner Detection: Active (DocAligner ONNX)"
                    else
                        "Detection Engine: Color-Aware OpenCV",
                    color = AccentBlue,
                    fontSize = 12.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Shutter Sound")
                    Switch(checked = state.captureSoundEnabled, onCheckedChange = viewModel::toggleCaptureSound)
                }
                Text("Auto-Capture Sensitivity", fontSize = 13.sp, color = Color.LightGray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("LOW" to "Strict", "NORMAL" to "Balanced", "HIGH" to "Fast").forEach { (code, label) ->
                        FilterChip(
                            selected = state.detectionSensitivity == code,
                            onClick = { viewModel.setDetectionSensitivity(code) },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (state.showExportSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.openExportSheet(false) },
            containerColor = SurfaceDark,
            contentColor = Color.White
        ) {
            val opts = state.exportOptions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Export Document (${state.pages.size} page(s))", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = opts.format == ExportFormat.PDF,
                        onClick = { viewModel.updateExportOptions(opts.copy(format = ExportFormat.PDF)) },
                        label = { Text("PDF") }
                    )
                    FilterChip(
                        selected = opts.format == ExportFormat.JPEG,
                        onClick = { viewModel.updateExportOptions(opts.copy(format = ExportFormat.JPEG)) },
                        label = { Text(if (state.pages.size > 1) "JPEG (ZIP)" else "JPEG") }
                    )
                    FilterChip(
                        selected = opts.format == ExportFormat.PNG,
                        onClick = { viewModel.updateExportOptions(opts.copy(format = ExportFormat.PNG)) },
                        label = { Text(if (state.pages.size > 1) "PNG (ZIP)" else "PNG") }
                    )
                }
                Text("Compression Quality: ${opts.jpegQuality}%", fontSize = 12.sp, color = Color.LightGray)
                Slider(
                    value = opts.jpegQuality.toFloat(),
                    onValueChange = { viewModel.updateExportOptions(opts.copy(jpegQuality = it.roundToInt())) },
                    valueRange = 60f..96f
                )
                Button(
                    onClick = { launchExport(opts.format) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Save ${opts.format.displayName}", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun CameraPermissionPrompt(onGrant: () -> Unit, onHome: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.DocumentScanner, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("Camera Permission Required", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onHome) { Text("Home", color = Color.White) }
            Button(onClick = onGrant, colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) { Text("Allow Camera") }
        }
    }
}

@Composable
private fun LiveCameraScannerView(
    state: ScannerUiState,
    viewModel: ScannerViewModel,
    onRequestExit: () -> Unit,
    onOpenGallery: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var focusIndicator by remember { mutableStateOf<Offset?>(null) }
    val stabilityTracker = remember { QuadStabilityTracker() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val shutterSound = remember { runCatching { MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) } }.getOrNull() }

    val latestAutoCapture by rememberUpdatedState(state.autoCaptureEnabled)
    val latestCapturing by rememberUpdatedState(state.isCapturing)
    val latestProcessing by rememberUpdatedState(state.isProcessing)
    val latestSensitivity by rememberUpdatedState(state.detectionSensitivity)
    val latestSound by rememberUpdatedState(state.captureSoundEnabled)

    fun triggerCapture() {
        val capture = imageCaptureRef ?: return
        if (latestCapturing || latestProcessing) return
        viewModel.setCapturing(true)
        if (latestSound) runCatching { shutterSound?.play(MediaActionSound.SHUTTER_CLICK) }
        val outFile = viewModel.createCaptureOutputFile()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outFile).build()
        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    stabilityTracker.reset()
                    viewModel.onPhotoCaptured(outFile)
                }
                override fun onError(exception: ImageCaptureException) {
                    viewModel.setCapturing(false)
                }
            }
        )
    }

    LaunchedEffect(state.flashEnabled, cameraRef) {
        cameraRef?.cameraControl?.enableTorch(state.flashEnabled)
    }

    LaunchedEffect(previewViewRef, state.qualityMode) {
        val pv = previewViewRef ?: return@LaunchedEffect
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = runCatching { providerFuture.get() }.getOrNull() ?: return@addListener
            pv.post {
                val rotation = pv.display?.rotation ?: android.view.Surface.ROTATION_0
                val preview = Preview.Builder()
                    .setTargetRotation(rotation)
                    .build()
                    .also { it.surfaceProvider = pv.surfaceProvider }

                val captureMode = if (state.qualityMode == ScanQualityMode.HD)
                    ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                else
                    ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY

                val capture = ImageCapture.Builder()
                    .setCaptureMode(captureMode)
                    .setJpegQuality(if (state.qualityMode == ScanQualityMode.HD) 96 else 90)
                    .setTargetRotation(rotation)
                    .build()

                val resSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            AndroidSize(480, 640),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()

                val analyzer = ImageAnalysis.Builder()
                    .setResolutionSelector(resSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                var lastAnalyzedMs = 0L
                analyzer.setAnalyzer(analysisExecutor) { imageProxy ->
                    try {
                        val now = System.currentTimeMillis()
                        if (!latestAutoCapture || now - lastAnalyzedMs < 95L || latestCapturing || latestProcessing) {
                            return@setAnalyzer
                        }
                        lastAnalyzedMs = now
                        val rawBmp = imageProxy.toBitmap()
                        val rotDeg = imageProxy.imageInfo.rotationDegrees
                        val uprightBmp = if (rotDeg != 0) {
                            val m = Matrix().apply { postRotate(rotDeg.toFloat()) }
                            Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, m, false).also {
                                if (it !== rawBmp) rawBmp.recycle()
                            }
                        } else {
                            rawBmp
                        }
                        val frameResult = try {
                            viewModel.edgeDetector.analyzeCameraBitmap(uprightBmp)
                        } finally {
                            uprightBmp.recycle()
                        }
                        val stability = stabilityTracker.onFrameEvaluated(frameResult, latestSensitivity, now)
                        ContextCompat.getMainExecutor(context).execute {
                            viewModel.updateLiveDetection(
                                quad = stability.detectedQuad,
                                confidence = stability.confidence,
                                progress = stability.progress,
                                hint = stability.qualityHint
                            )
                            if (latestAutoCapture && stability.shouldAutoCapture) {
                                triggerCapture()
                            }
                        }
                    } finally {
                        imageProxy.close()
                    }
                }

                val useCaseGroup = UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(capture)
                    .addUseCase(analyzer)
                    .apply { pv.viewPort?.let { setViewPort(it) } }
                    .build()

                runCatching {
                    provider.unbindAll()
                    val cam = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, useCaseGroup)
                    cameraRef = cam
                    imageCaptureRef = capture
                    cam.cameraControl.enableTorch(state.flashEnabled)
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { cameraRef?.cameraControl?.enableTorch(false) }
            viewModel.turnOffFlash()
            runCatching { analysisExecutor.shutdown() }
            runCatching { shutterSound?.release() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1. Minimal pure-black Top Bar (Home | Fast/HD | Settings)
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onRequestExit) {
                Icon(Icons.Outlined.Home, contentDescription = "Home", tint = Color.White, modifier = Modifier.size(26.dp))
            }

            Surface(
                onClick = { viewModel.toggleQualityMode() },
                shape = RoundedCornerShape(20.dp),
                color = if (state.qualityMode == ScanQualityMode.HD) AccentBlue.copy(alpha = 0.22f) else Color(0xFF1E1E22),
                border = BorderStroke(1.dp, if (state.qualityMode == ScanQualityMode.HD) AccentBlue else Color.White.copy(alpha = 0.16f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (state.qualityMode == ScanQualityMode.HD) Icons.Outlined.Hd else Icons.Outlined.Speed,
                        contentDescription = null,
                        tint = if (state.qualityMode == ScanQualityMode.HD) AccentBlue else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (state.qualityMode == ScanQualityMode.HD) "HD" else "Fast",
                        color = if (state.qualityMode == ScanQualityMode.HD) AccentBlue else Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }

            IconButton(onClick = { viewModel.openSettingsSheet(true) }) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }

        // 2. Full-bleed Live Camera Preview + Mode-Specific Visual Guides
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(cameraRef) {
                    detectTransformGestures { _, _, zoom, _ ->
                        val cam = cameraRef ?: return@detectTransformGestures
                        val cur = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                        val minZ = cam.cameraInfo.zoomState.value?.minZoomRatio ?: 1f
                        val maxZ = cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 6f
                        cam.cameraControl.setZoomRatio((cur * zoom).coerceIn(minZ, maxZ))
                    }
                }
                .pointerInput(cameraRef, previewViewRef) {
                    detectTapGestures { offset ->
                        val pv = previewViewRef ?: return@detectTapGestures
                        val cam = cameraRef ?: return@detectTapGestures
                        focusIndicator = offset
                        val pt = pv.meteringPointFactory.createPoint(offset.x, offset.y)
                        cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(pt).build())
                    }
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FIT_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        previewViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Mode-specific framing guides + Auto-Capture corner overlay (HIDDEN in Manual mode!)
            Canvas(modifier = Modifier.fillMaxSize()) {
                when (state.capturePreset) {
                    CapturePresetMode.BOOK -> {
                        // Dashed center spine divider for 2-page Book Split
                        drawLine(
                            color = AccentBlue.copy(alpha = 0.9f),
                            start = Offset(size.width / 2f, size.height * 0.08f),
                            end = Offset(size.width / 2f, size.height * 0.92f),
                            strokeWidth = 2.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f), 0f)
                        )
                    }
                    CapturePresetMode.ID_CARD, CapturePresetMode.BUSINESS_CARD -> {
                        val cardW = size.width * 0.84f
                        val ratio = if (state.capturePreset == CapturePresetMode.ID_CARD) 1.586f else 1.75f
                        val cardH = (cardW / ratio).coerceAtMost(size.height * 0.55f)
                        val left = (size.width - cardW) / 2f
                        val top = (size.height - cardH) / 2f
                        drawRoundRect(
                            color = AccentBlue.copy(alpha = 0.9f),
                            topLeft = Offset(left, top),
                            size = Size(cardW, cardH),
                            cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    }
                    else -> Unit
                }

                // Only draw live auto-capture corner box when Auto-Capture is ON!
                if (state.autoCaptureEnabled) {
                    state.liveDetectedQuad?.let { q ->
                        val tl = Offset(q.topLeft.x * size.width, q.topLeft.y * size.height)
                        val tr = Offset(q.topRight.x * size.width, q.topRight.y * size.height)
                        val br = Offset(q.bottomRight.x * size.width, q.bottomRight.y * size.height)
                        val bl = Offset(q.bottomLeft.x * size.width, q.bottomLeft.y * size.height)

                        val path = Path().apply {
                            moveTo(tl.x, tl.y); lineTo(tr.x, tr.y); lineTo(br.x, br.y); lineTo(bl.x, bl.y); close()
                        }
                        drawPath(path, AccentBlue.copy(alpha = 0.14f))
                        drawPath(path, AccentBlue, style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round))

                        listOf(tl, tr, br, bl).forEach { pt ->
                            drawCircle(Color.White, radius = 11.dp.toPx(), center = pt)
                            drawCircle(AccentBlue, radius = 8.5.dp.toPx(), center = pt)
                        }
                    }
                }

                focusIndicator?.let { pt ->
                    drawCircle(Color.White.copy(alpha = 0.85f), radius = 26.dp.toPx(), center = pt, style = Stroke(2.dp.toPx()))
                }
            }

            // Clear Auto / Manual mode badge at top of viewfinder
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.58f)
            ) {
                Text(
                    text = if (state.autoCaptureEnabled) "Auto Capture" else "Manual Capture",
                    color = if (state.autoCaptureEnabled) AccentBlue else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }

            // Centered mode-aware status / instruction pill
            val pillText = when {
                state.idCardFrontPendingPath != null -> "Step 2/2: Flip & scan Back of ID card"
                !state.autoCaptureEnabled -> when (state.capturePreset) {
                    CapturePresetMode.BOOK -> "Align spine with center line — tap shutter"
                    CapturePresetMode.ID_CARD -> "Step 1/2: Align Front of ID — tap shutter"
                    CapturePresetMode.BUSINESS_CARD -> "Align card in frame — tap shutter"
                    else -> "Manual mode — tap shutter to capture"
                }
                state.cameraQualityHint != null -> state.cameraQualityHint
                state.liveDetectedQuad != null && state.autoCaptureProgress > 0.25f -> "Capturing... hold steady"
                state.liveDetectedQuad != null -> "Document detected"
                state.capturePreset == CapturePresetMode.BOOK -> "Align spine with center line"
                state.capturePreset == CapturePresetMode.ID_CARD -> "Step 1/2: Place Front of ID in frame"
                state.capturePreset == CapturePresetMode.BUSINESS_CARD -> "Center business card in frame"
                else -> "Looking for document"
            }
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(22.dp),
                color = Color.Black.copy(alpha = 0.62f)
            ) {
                Text(
                    text = pillText,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Bottom-center floating Single | Batch pill
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.65f)
            ) {
                Row(modifier = Modifier.padding(3.dp)) {
                    SegmentedModePill("Single", !state.batchMode) { viewModel.setBatchMode(false) }
                    SegmentedModePill("Batch", state.batchMode) { viewModel.setBatchMode(true) }
                }
            }
        }

        // 3. Horizontal Mode Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CapturePresetMode.entries.forEach { preset ->
                val selected = state.capturePreset == preset
                Text(
                    text = preset.label,
                    color = if (selected) AccentBlue else Color(0xFFB8B8C0),
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable { viewModel.selectCapturePreset(preset) }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                )
            }
        }

        // 4. Clean 5-Slot Bottom Control Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenGallery) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Gallery", tint = Color.White, modifier = Modifier.size(26.dp))
            }

            // Auto / Manual toggle with clear label underneath
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { viewModel.toggleAutoCapture() }.padding(horizontal = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.DocumentScanner,
                    contentDescription = "Auto Capture",
                    tint = if (state.autoCaptureEnabled) AccentBlue else Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = if (state.autoCaptureEnabled) "Auto" else "Manual",
                    color = if (state.autoCaptureEnabled) AccentBlue else Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Center Shutter Ring (progress ring ONLY shown in Auto mode!)
            Box(
                modifier = Modifier.size(72.dp).clickable { triggerCapture() },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 4.dp.toPx()
                    drawCircle(color = Color.White, style = Stroke(width = stroke))
                    if (state.autoCaptureEnabled && state.autoCaptureProgress > 0.02f) {
                        drawArc(
                            color = AccentBlue,
                            startAngle = -90f,
                            sweepAngle = 360f * state.autoCaptureProgress.coerceIn(0f, 1f),
                            useCenter = false,
                            style = Stroke(width = stroke + 1.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(if (state.isCapturing) AccentBlue else Color.White.copy(alpha = 0.14f))
                )
            }

            IconButton(onClick = { viewModel.toggleFlash() }) {
                Icon(
                    imageVector = if (state.flashEnabled) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                    contentDescription = "Flash",
                    tint = if (state.flashEnabled) Color(0xFFFFD54F) else Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            Box(
                modifier = Modifier.size(48.dp).clickable(enabled = state.pages.isNotEmpty()) { viewModel.openReviewScreen() },
                contentAlignment = Alignment.Center
            ) {
                val lastPage = state.pages.lastOrNull()
                if (lastPage != null) {
                    PageThumbnailImage(
                        path = lastPage.enhancedImagePath,
                        revision = state.previewRevision,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    )
                    Box(
                        modifier = Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape).background(AccentBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${state.pages.size}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF26262A)))
                }
            }
        }
    }
}

@Composable
private fun SegmentedModePill(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) AccentBlue else Color.Transparent
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.8f),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
    }
}

// =====================================================================
// REVIEW & EDIT SCREEN (Inline Multi-Page Crop/Rotate + Live Cleanup/Adjust/Rotate + Working Resize)
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewAndEditScreen(
    state: ScannerUiState,
    viewModel: ScannerViewModel,
    onRequestExit: () -> Unit,
    onImportGallery: () -> Unit,
    onQuickSavePdf: () -> Unit,
    onSharePdf: () -> Unit
) {
    val activePage = state.activePage
    val isInlineCrop = state.activeTool == ReviewTool.CROP

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1. Pure-Black Top Bar
        Row(
            modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onRequestExit) {
                Icon(Icons.Outlined.Home, contentDescription = "Home", tint = Color.White, modifier = Modifier.size(26.dp))
            }

            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp).clickable { viewModel.openRenameDialog(true) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = state.documentTitle,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Canvas(modifier = Modifier.fillMaxWidth(0.75f).height(2.dp).padding(top = 1.dp)) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.55f),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                }
            }

            IconButton(onClick = { viewModel.quickAutoCleanActivePage() }) {
                Icon(Icons.Outlined.AutoFixHigh, contentDescription = "Auto Clean", tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }

        // 2. Dark Charcoal Page Stage + Inline Auto-Detect/Full Pills (when Crop active) + Page Nav Bar
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).background(StageCharcoal)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (activePage != null) {
                    when (state.activeTool) {
                        ReviewTool.CROP -> {
                            InteractiveCropCanvas(
                                imagePath = activePage.rawImagePath,
                                revision = state.previewRevision,
                                quad = state.pendingCropQuad,
                                onQuadChange = viewModel::updatePendingCropQuad
                            )
                        }
                        ReviewTool.CLEANUP -> {
                            InteractiveLiveCleanupCanvas(
                                imagePath = activePage.enhancedImagePath,
                                revision = state.previewRevision,
                                currentMode = state.cleanupMode,
                                customColorArgb = state.cleanupCustomColorArgb,
                                brushRadiusNorm = state.cleanupBrushRadiusNorm,
                                eyedropperActive = state.cleanupEyedropperActive,
                                onEyedropperTap = viewModel::sampleCleanupColorFromPage,
                                onStrokeComplete = viewModel::applySingleCleanupStrokeLive
                            )
                        }
                        ReviewTool.MARKUP -> {
                            InteractiveMarkupCanvas(
                                imagePath = activePage.enhancedImagePath,
                                revision = state.previewRevision,
                                strokes = state.pendingMarkupStrokes,
                                colorArgb = state.markupColorArgb,
                                isHighlighter = state.markupIsHighlighter,
                                strokeWidthNorm = state.markupStrokeWidthNorm,
                                onStrokeComplete = viewModel::addMarkupStroke
                            )
                        }
                        else -> {
                            val showPath = if (state.compareOriginal) activePage.warpedImagePath else activePage.enhancedImagePath
                            val liveRot = if (state.activeTool == ReviewTool.ROTATE) state.pendingRotation90.toFloat() + state.pendingFineRotation else 0f
                            val liveFilter = if (state.activeTool == ReviewTool.ADJUST) {
                                buildLiveColorFilter(state.liveAdjustBrightness, state.liveAdjustContrast)
                            } else {
                                null
                            }
                            PagePreviewImage(
                                path = showPath,
                                revision = state.previewRevision,
                                liveRotationDeg = liveRot,
                                colorFilter = liveFilter,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            // Inline Crop Quick Pills (Auto-detect & Full Frame) right above page nav bar (like 1000430154.jpg!)
            if (isInlineCrop) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
                ) {
                    Surface(
                        onClick = { viewModel.autoDetectActivePageCrop() },
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White
                    ) {
                        Text(
                            text = "Auto-detect",
                            color = Color.Black,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    Surface(
                        onClick = { viewModel.resetCropToFull() },
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White
                    ) {
                        Text(
                            text = "Full Frame",
                            color = Color.Black,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // Page Navigation Bar STAYS VISIBLE in NONE and CROP modes (slide pages while cropping!)
            if (state.activeTool == ReviewTool.NONE || isInlineCrop) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        onClick = { viewModel.previousPage() },
                        enabled = state.activePageIndex > 0,
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = if (state.activePageIndex > 0) 0.55f else 0.25f)
                    ) {
                        Icon(
                            Icons.Outlined.KeyboardArrowLeft,
                            contentDescription = "Previous",
                            tint = if (state.activePageIndex > 0) Color.White else Color.Gray,
                            modifier = Modifier.padding(8.dp).size(20.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            onClick = onImportGallery,
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.65f)
                        ) {
                            Icon(
                                Icons.Outlined.LibraryAdd,
                                contentDescription = "Insert Page",
                                tint = Color.White,
                                modifier = Modifier.padding(9.dp).size(19.dp)
                            )
                        }

                        Surface(
                            onClick = { viewModel.toggleFilmstrip() },
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.72f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Page ${state.activePageIndex + 1} of ${state.pages.size}",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = if (state.filmstripExpanded) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowUp,
                                    contentDescription = "Toggle Thumbnails",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = { viewModel.nextPage() },
                        enabled = state.activePageIndex < state.pages.lastIndex,
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = if (state.activePageIndex < state.pages.lastIndex) 0.55f else 0.25f)
                    ) {
                        Icon(
                            Icons.Outlined.KeyboardArrowRight,
                            contentDescription = "Next",
                            tint = if (state.activePageIndex < state.pages.lastIndex) Color.White else Color.Gray,
                            modifier = Modifier.padding(8.dp).size(20.dp)
                        )
                    }
                }
            }
        }

        // 3. Collapsible Horizontal Thumbnail Filmstrip (stays usable even in Crop mode!)
        AnimatedVisibility(
            visible = state.filmstripExpanded && (state.activeTool == ReviewTool.NONE || isInlineCrop),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().background(Color.Black).padding(vertical = 10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(state.pages, key = { _, p -> p.id }) { idx, page ->
                    val selected = idx == state.activePageIndex
                    Box(
                        modifier = Modifier
                            .size(width = 86.dp, height = 104.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF1E1E22))
                            .border(
                                width = if (selected) 2.5.dp else 1.dp,
                                color = if (selected) AccentBlue else Color.White.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable { viewModel.selectPage(idx) }
                    ) {
                        PageThumbnailImage(
                            path = page.enhancedImagePath,
                            revision = state.previewRevision,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(alpha = 0.72f)
                        ) {
                            Text("${idx + 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }

        // 4. Contextual Tool Panels vs Default/Inline-Crop Tool Strip + Bottom Action Bar
        when (state.activeTool) {
            ReviewTool.ROTATE -> RotateToolPanel(state, viewModel)
            ReviewTool.FILTERS -> FiltersToolPanel(state, viewModel)
            ReviewTool.ADJUST -> AdjustToolPanel(state, viewModel)
            ReviewTool.CLEANUP -> CleanupToolPanel(state, viewModel)
            ReviewTool.MARKUP -> MarkupToolPanel(state, viewModel)
            ReviewTool.RESIZE -> ResizeToolPanel(state, viewModel)
            else -> {
                // Tool strip STAYS visible in both NONE and CROP (with Crop highlighted blue & Rotate right next to it!)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolStripItem(Icons.Outlined.CameraAlt, "Retake", false) { viewModel.startRetakeActivePage() }
                    ToolStripItem(Icons.Outlined.Crop, "Crop", isInlineCrop) { viewModel.selectReviewTool(ReviewTool.CROP) }
                    ToolStripItem(Icons.Outlined.Refresh, "Rotate", false) {
                        if (isInlineCrop) {
                            viewModel.rotateActivePage90(clockwise = true)
                        } else {
                            viewModel.selectReviewTool(ReviewTool.ROTATE)
                        }
                    }
                    ToolStripItem(Icons.Outlined.Filter, "Filters", false) { viewModel.selectReviewTool(ReviewTool.FILTERS) }
                    ToolStripItem(Icons.Outlined.Tune, "Adjust", false) { viewModel.selectReviewTool(ReviewTool.ADJUST) }
                    ToolStripItem(Icons.Outlined.CleaningServices, "Cleanup", false) { viewModel.selectReviewTool(ReviewTool.CLEANUP) }
                    ToolStripItem(Icons.Outlined.Draw, "Markup", false) { viewModel.selectReviewTool(ReviewTool.MARKUP) }
                    ToolStripItem(Icons.Outlined.AspectRatio, "Resize", false) { viewModel.selectReviewTool(ReviewTool.RESIZE) }
                    ToolStripItem(Icons.Outlined.Reorder, "Reorder", false) { viewModel.selectReviewTool(ReviewTool.REORDER) }
                    ToolStripItem(Icons.Outlined.DeleteOutline, "Delete", false) { viewModel.confirmDeleteActivePage(true) }
                }

                // Bottom Action Row: Camera+ | Share | Save PDF | ^
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        onClick = { viewModel.openCameraToAddPage() },
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.75f))
                    ) {
                        Icon(Icons.Outlined.AddAPhoto, contentDescription = "Add Page", tint = Color.White, modifier = Modifier.padding(10.dp).size(22.dp))
                    }

                    Surface(
                        onClick = onSharePdf,
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.75f))
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.padding(10.dp).size(22.dp))
                    }

                    Spacer(Modifier.weight(1f))

                    Surface(shape = RoundedCornerShape(26.dp), color = AccentBlue) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Save PDF",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                modifier = Modifier
                                    .clickable {
                                        if (isInlineCrop) viewModel.applyCropAndPerspective()
                                        onQuickSavePdf()
                                    }
                                    .padding(start = 22.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)
                            )
                            Box(modifier = Modifier.width(1.dp).height(22.dp).background(Color.White.copy(alpha = 0.35f)))
                            Box(
                                modifier = Modifier
                                    .clickable {
                                        if (isInlineCrop) viewModel.applyCropAndPerspective()
                                        viewModel.openExportSheet(true)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "More Export Options", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.activeTool == ReviewTool.REORDER) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.selectReviewTool(ReviewTool.NONE) },
            containerColor = SurfaceDark,
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Reorder & Manage Pages (${state.pages.size})", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    TextButton(onClick = { viewModel.selectReviewTool(ReviewTool.NONE) }) { Text("Done", color = AccentBlue) }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.height(380.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(state.pages, key = { _, p -> p.id }) { idx, page ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF1D1D22),
                            border = BorderStroke(1.dp, if (idx == state.activePageIndex) AccentBlue else Color.White.copy(alpha = 0.12f))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                PageThumbnailImage(
                                    path = page.enhancedImagePath,
                                    revision = state.previewRevision,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.75f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            viewModel.selectPage(idx)
                                            viewModel.selectReviewTool(ReviewTool.NONE)
                                        }
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("P${idx + 1}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Row {
                                        IconButton(onClick = { viewModel.movePage(idx, idx - 1) }, enabled = idx > 0, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Move Left", modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(onClick = { viewModel.movePage(idx, idx + 1) }, enabled = idx < state.pages.lastIndex, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Move Right", modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(onClick = { viewModel.duplicatePage(idx) }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Duplicate", modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(onClick = { viewModel.deletePageAt(idx) }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFFF6B6B), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolStripItem(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (active) AccentBlue else Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
            Text(label, color = if (active) Color.White else Color(0xFFD0D0D8), fontSize = 11.sp, maxLines = 1)
        }
    }
}

// =====================================================================
// TOOL PANELS (Live Rotate Dial, Filters, Live Adjust, Live Cleanup + Eyedropper, Markup, Working Resize)
// =====================================================================
@Composable
private fun RotateToolPanel(state: ScannerUiState, viewModel: ScannerViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { viewModel.rotateActivePage90(clockwise = false) }) {
                Icon(Icons.Outlined.RotateLeft, contentDescription = "Rotate Left 90", tint = Color.White)
            }
            Text(String.format("%+.1f°", state.pendingRotation90.toFloat() + state.pendingFineRotation), color = AccentBlue, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            IconButton(onClick = { viewModel.rotateActivePage90(clockwise = true) }) {
                Icon(Icons.Outlined.RotateRight, contentDescription = "Rotate Right 90", tint = Color.White)
            }
        }
        CurvedRotationDial(
            degrees = state.pendingFineRotation,
            onDegreesChange = viewModel::updatePendingFineRotation
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { viewModel.cancelActiveTool() }) { Text("Cancel", color = Color.LightGray) }
            TextButton(onClick = { viewModel.resetPendingRotation() }) { Text("Reset 0°", color = Color.White) }
            Button(onClick = { viewModel.applyFineRotation() }, colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun CurvedRotationDial(degrees: Float, onDegreesChange: (Float) -> Unit) {
    val latestDeg by rememberUpdatedState(degrees)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDegreesChange((latestDeg + dragAmount.x * 0.22f).coerceIn(-45f, 45f))
                }
            }
    ) {
        val cx = size.width / 2f
        val baseY = size.height * 0.78f
        for (tick in -45..45 step 3) {
            val rel = tick - degrees
            if (abs(rel) > 36f) continue
            val norm = rel / 36f
            val x = cx + norm * (size.width * 0.44f)
            val curveDrop = norm * norm * 16.dp.toPx()
            val isMajor = tick % 15 == 0
            val len = if (isMajor) 16.dp.toPx() else 9.dp.toPx()
            val alpha = (1f - abs(norm) * 0.78f).coerceIn(0.15f, 1f)
            drawLine(
                color = Color.White.copy(alpha = alpha),
                start = Offset(x, baseY + curveDrop),
                end = Offset(x, baseY + curveDrop - len),
                strokeWidth = if (isMajor) 2.dp.toPx() else 1.2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        drawLine(
            color = AccentBlue,
            start = Offset(cx, baseY + 4.dp.toPx()),
            end = Offset(cx, baseY - 22.dp.toPx()),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun FiltersToolPanel(state: ScannerUiState, viewModel: ScannerViewModel) {
    val current = state.activePage?.filterMode ?: ScanFilterMode.AUTO_COLOR
    val proFilters = listOf(
        ScanFilterMode.ORIGINAL,
        ScanFilterMode.AUTO_COLOR,
        ScanFilterMode.DOCUMENT,
        ScanFilterMode.GRAYSCALE,
        ScanFilterMode.ENHANCED_DOCUMENT,
        ScanFilterMode.COLOR,
        ScanFilterMode.BLACK_AND_WHITE,
        ScanFilterMode.HIGH_CONTRAST,
        ScanFilterMode.PHOTO,
        ScanFilterMode.SOFT_COLOR
    )
    Column(
        modifier = Modifier.fillMaxWidth().background(Color.Black).padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            proFilters.forEach { mode ->
                val label = if (mode == ScanFilterMode.ENHANCED_DOCUMENT) "Whiteboard" else mode.displayName
                FilterChip(
                    selected = current == mode,
                    onClick = { viewModel.applyFilterToActivePage(mode) },
                    label = { Text(label, fontSize = 12.sp) }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { viewModel.cancelActiveTool() }) { Text("Cancel", color = Color.LightGray) }
                TextButton(onClick = { viewModel.applyFilterToAllPages(current) }) { Text("Apply All", color = AccentBlue) }
            }
            Button(
                onClick = { viewModel.commitActiveTool() },
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) { Text("Done") }
        }
    }
}

@Composable
private fun AdjustToolPanel(state: ScannerUiState, viewModel: ScannerViewModel) {
    val adj = state.activePage?.customAdjustments ?: CustomAdjustments()
    var brightness by remember(adj) { mutableFloatStateOf(adj.brightness) }
    var contrast by remember(adj) { mutableFloatStateOf(adj.contrast) }
    var sharpness by remember(adj) { mutableFloatStateOf(adj.sharpness) }
    var strength by remember(adj) { mutableFloatStateOf(adj.filterStrength) }

    Column(
        modifier = Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row {
                IconButton(onClick = { viewModel.undoActivePageEdit() }, enabled = state.canUndo) {
                    Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Undo", tint = if (state.canUndo) Color.White else Color.Gray)
                }
                IconButton(onClick = { viewModel.redoActivePageEdit() }, enabled = state.canRedo) {
                    Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "Redo", tint = if (state.canRedo) Color.White else Color.Gray)
                }
                TextButton(onClick = { viewModel.cancelActiveTool() }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Cancel", color = Color.LightGray, fontSize = 12.sp) }
                TextButton(onClick = { viewModel.resetActivePageEdits() }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Reset", color = Color.White, fontSize = 12.sp) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { viewModel.toggleCompareOriginal(!state.compareOriginal) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Outlined.Compare, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (state.compareOriginal) "Original" else "Compare", color = Color.White, fontSize = 12.sp)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { viewModel.commitActiveTool() },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                ) { Text("Done", fontSize = 13.sp) }
            }
        }
        CompactSliderRow("Strength", strength, 0f..1f, { strength = it }) {
            viewModel.updateCustomAdjustments(adj.copy(filterStrength = strength))
        }
        CompactSliderRow("Brightness", brightness, -0.35f..0.35f, {
            brightness = it
            viewModel.setLiveAdjustPreview(brightness, contrast)
        }) {
            viewModel.updateCustomAdjustments(adj.copy(brightness = brightness))
        }
        CompactSliderRow("Contrast", contrast, 0.7f..1.6f, {
            contrast = it
            viewModel.setLiveAdjustPreview(brightness, contrast)
        }) {
            viewModel.updateCustomAdjustments(adj.copy(contrast = contrast))
        }
        CompactSliderRow("Sharpness", sharpness, 0f..1f, { sharpness = it }) {
            viewModel.updateCustomAdjustments(adj.copy(sharpness = sharpness))
        }
    }
}

@Composable
private fun CompactSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.width(74.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onFinished,
            valueRange = range,
            modifier = Modifier.weight(1f).height(30.dp)
        )
    }
}

@Composable
private fun CleanupToolPanel(state: ScannerUiState, viewModel: ScannerViewModel) {
    // Rich Custom Color palette (neutral papers + vivid colors) + Eyedropper!
    val swatches = listOf(
        android.graphics.Color.WHITE,
        android.graphics.Color.parseColor("#FAF6EC"),
        android.graphics.Color.parseColor("#EFE9DA"),
        android.graphics.Color.parseColor("#DCDCDC"),
        android.graphics.Color.BLACK,
        android.graphics.Color.parseColor("#E53935"),
        android.graphics.Color.parseColor("#1E88E5"),
        android.graphics.Color.parseColor("#43A047"),
        android.graphics.Color.parseColor("#FDD835")
    )
    Column(
        modifier = Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = state.cleanupMode == CleanupBrushMode.SURROUNDING_COLOR,
                onClick = { viewModel.setCleanupMode(CleanupBrushMode.SURROUNDING_COLOR) },
                label = { Text("Surrounding Color", fontSize = 12.sp) }
            )
            FilterChip(
                selected = state.cleanupMode == CleanupBrushMode.CUSTOM_COLOR,
                onClick = { viewModel.setCleanupMode(CleanupBrushMode.CUSTOM_COLOR) },
                label = { Text("Custom Color", fontSize = 12.sp) }
            )
        }

        if (state.cleanupMode == CleanupBrushMode.CUSTOM_COLOR) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Eyedropper button (pick exact color straight from page!)
                Surface(
                    onClick = { viewModel.toggleCleanupEyedropper() },
                    shape = CircleShape,
                    color = if (state.cleanupEyedropperActive) AccentBlue else Color(0xFF28282E),
                    border = BorderStroke(1.dp, if (state.cleanupEyedropperActive) Color.White else Color.Gray)
                ) {
                    Icon(
                        Icons.Outlined.Colorize,
                        contentDescription = "Pick from page",
                        tint = Color.White,
                        modifier = Modifier.padding(6.dp).size(18.dp)
                    )
                }
                swatches.forEach { argb ->
                    val selected = !state.cleanupEyedropperActive && state.cleanupCustomColorArgb == argb
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .border(if (selected) 2.5.dp else 1.dp, if (selected) AccentBlue else Color.Gray, CircleShape)
                            .clickable { viewModel.setCleanupCustomColor(argb) }
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Brush", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.width(48.dp))
            Slider(
                value = state.cleanupBrushRadiusNorm,
                onValueChange = viewModel::setCleanupBrushRadius,
                valueRange = 0.014f..0.080f,
                modifier = Modifier.weight(1f).height(28.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { viewModel.cancelActiveTool() }) { Text("Cancel", color = Color.LightGray) }
                TextButton(
                    onClick = { viewModel.undoLiveCleanup() },
                    enabled = state.cleanupUndoCount > 0
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Undo (${state.cleanupUndoCount})")
                }
            }
            Button(
                onClick = { viewModel.commitActiveTool() },
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) { Text("Done") }
        }
    }
}

@Composable
private fun MarkupToolPanel(state: ScannerUiState, viewModel: ScannerViewModel) {
    val colors = listOf(
        android.graphics.Color.parseColor("#E53935"),
        android.graphics.Color.parseColor("#1E88E5"),
        android.graphics.Color.parseColor("#43A047"),
        android.graphics.Color.parseColor("#FDD835"),
        android.graphics.Color.BLACK
    )
    Column(
        modifier = Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !state.markupIsHighlighter, onClick = { viewModel.setMarkupHighlighter(false) }, label = { Text("Pen", fontSize = 12.sp) })
                FilterChip(selected = state.markupIsHighlighter, onClick = { viewModel.setMarkupHighlighter(true) }, label = { Text("Highlighter", fontSize = 12.sp) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                colors.forEach { argb ->
                    val selected = state.markupColorArgb == argb
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .border(if (selected) 2.5.dp else 1.dp, if (selected) Color.White else Color.Gray, CircleShape)
                            .clickable { viewModel.setMarkupColor(argb) }
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row {
                TextButton(onClick = { viewModel.cancelActiveTool() }) { Text("Cancel", color = Color.LightGray) }
                TextButton(onClick = { viewModel.undoMarkupStroke() }, enabled = state.pendingMarkupStrokes.isNotEmpty()) {
                    Text("Undo", color = Color.White)
                }
            }
            Button(onClick = { viewModel.applyMarkupStrokes() }, colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) {
                Text("Save Markup")
            }
        }
    }
}

@Composable
private fun ResizeToolPanel(state: ScannerUiState, viewModel: ScannerViewModel) {
    val opts = state.exportOptions
    Column(
        modifier = Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            ExportPageSize.entries.forEach { sz ->
                FilterChip(
                    selected = opts.pageSize == sz,
                    onClick = { viewModel.applyResize(sz, opts.orientation, applyToAll = false) },
                    label = { Text(sz.displayName, fontSize = 12.sp) }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ExportOrientation.entries.forEach { ori ->
                    FilterChip(
                        selected = opts.orientation == ori,
                        onClick = { viewModel.applyResize(opts.pageSize, ori, applyToAll = false) },
                        label = { Text(ori.displayName, fontSize = 12.sp) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { viewModel.applyResize(opts.pageSize, opts.orientation, applyToAll = true) }) {
                    Text("All", color = AccentBlue, fontSize = 12.sp)
                }
                Button(
                    onClick = { viewModel.commitActiveTool() },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                ) { Text("Done", fontSize = 13.sp) }
            }
        }
    }
}

// =====================================================================
// INTERACTIVE CANVASES (See-Through Crop Rings + Live Cleanup + Markup)
// =====================================================================
@Composable
private fun InteractiveCropCanvas(
    imagePath: String,
    revision: Int,
    quad: DocumentQuad,
    onQuadChange: (DocumentQuad) -> Unit
) {
    val bmp by produceState<Bitmap?>(initialValue = null, imagePath, revision) {
        value = withContext(Dispatchers.IO) { decodeForUi(imagePath, 1400) }
    }
    val latestQuad by rememberUpdatedState(quad)
    var activeHandle by remember { mutableIntStateOf(-1) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val loaded = bmp
        if (loaded == null) {
            CircularProgressIndicator(color = AccentBlue)
            return@BoxWithConstraints
        }
        val imgRect = computeFitRect(loaded.width.toFloat(), loaded.height.toFloat(), constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())

        Image(
            bitmap = loaded.asImageBitmap(),
            contentDescription = "Crop Preview",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(imgRect) {
                    detectDragGestures(
                        onDragStart = { start ->
                            val q = latestQuad
                            val handles = listOf(
                                Offset(imgRect.left + q.topLeft.x * imgRect.width, imgRect.top + q.topLeft.y * imgRect.height),
                                Offset(imgRect.left + q.topRight.x * imgRect.width, imgRect.top + q.topRight.y * imgRect.height),
                                Offset(imgRect.left + q.bottomRight.x * imgRect.width, imgRect.top + q.bottomRight.y * imgRect.height),
                                Offset(imgRect.left + q.bottomLeft.x * imgRect.width, imgRect.top + q.bottomLeft.y * imgRect.height),
                                Offset(imgRect.left + (q.topLeft.x + q.topRight.x) * 0.5f * imgRect.width, imgRect.top + (q.topLeft.y + q.topRight.y) * 0.5f * imgRect.height),
                                Offset(imgRect.left + (q.topRight.x + q.bottomRight.x) * 0.5f * imgRect.width, imgRect.top + (q.topRight.y + q.bottomRight.y) * 0.5f * imgRect.height),
                                Offset(imgRect.left + (q.bottomLeft.x + q.bottomRight.x) * 0.5f * imgRect.width, imgRect.top + (q.bottomLeft.y + q.bottomRight.y) * 0.5f * imgRect.height),
                                Offset(imgRect.left + (q.topLeft.x + q.bottomLeft.x) * 0.5f * imgRect.width, imgRect.top + (q.topLeft.y + q.bottomLeft.y) * 0.5f * imgRect.height)
                            )
                            var best = -1
                            var bestDist = 115f
                            handles.forEachIndexed { idx, pt ->
                                val d = hypot(pt.x - start.x, pt.y - start.y)
                                if (d < bestDist) { bestDist = d; best = idx }
                            }
                            activeHandle = best
                        },
                        onDragEnd = { activeHandle = -1 },
                        onDragCancel = { activeHandle = -1 },
                        onDrag = { change, drag ->
                            if (activeHandle < 0) return@detectDragGestures
                            change.consume()
                            val dx = drag.x / imgRect.width
                            val dy = drag.y / imgRect.height
                            val q = latestQuad
                            fun shift(p: NormalizedPoint) = NormalizedPoint((p.x + dx).coerceIn(0f, 1f), (p.y + dy).coerceIn(0f, 1f))
                            val next = when (activeHandle) {
                                0 -> q.copy(topLeft = shift(q.topLeft))
                                1 -> q.copy(topRight = shift(q.topRight))
                                2 -> q.copy(bottomRight = shift(q.bottomRight))
                                3 -> q.copy(bottomLeft = shift(q.bottomLeft))
                                4 -> q.copy(topLeft = shift(q.topLeft), topRight = shift(q.topRight))
                                5 -> q.copy(topRight = shift(q.topRight), bottomRight = shift(q.bottomRight))
                                6 -> q.copy(bottomLeft = shift(q.bottomLeft), bottomRight = shift(q.bottomRight))
                                7 -> q.copy(topLeft = shift(q.topLeft), bottomLeft = shift(q.bottomLeft))
                                else -> q
                            }
                            onQuadChange(next)
                        }
                    )
                }
        ) {
            val tl = Offset(imgRect.left + quad.topLeft.x * imgRect.width, imgRect.top + quad.topLeft.y * imgRect.height)
            val tr = Offset(imgRect.left + quad.topRight.x * imgRect.width, imgRect.top + quad.topRight.y * imgRect.height)
            val br = Offset(imgRect.left + quad.bottomRight.x * imgRect.width, imgRect.top + quad.bottomRight.y * imgRect.height)
            val bl = Offset(imgRect.left + quad.bottomLeft.x * imgRect.width, imgRect.top + quad.bottomLeft.y * imgRect.height)

            val path = Path().apply { moveTo(tl.x, tl.y); lineTo(tr.x, tr.y); lineTo(br.x, br.y); lineTo(bl.x, bl.y); close() }
            drawPath(path, AccentBlue, style = Stroke(2.5.dp.toPx()))

            // See-through circular corner rings (so you see the exact paper corner inside!)
            listOf(tl, tr, br, bl).forEachIndexed { i, pt ->
                val r = if (activeHandle == i) 15.dp.toPx() else 12.dp.toPx()
                drawCircle(AccentBlue.copy(alpha = 0.22f), radius = r, center = pt)
                drawCircle(AccentBlue, radius = r, center = pt, style = Stroke(2.8.dp.toPx()))
            }

            // Rectangular midpoint handles
            val topMid = Offset((tl.x + tr.x) / 2f, (tl.y + tr.y) / 2f)
            val botMid = Offset((bl.x + br.x) / 2f, (bl.y + br.y) / 2f)
            val leftMid = Offset((tl.x + bl.x) / 2f, (tl.y + bl.y) / 2f)
            val rightMid = Offset((tr.x + br.x) / 2f, (tr.y + br.y) / 2f)
            val pillLong = 24.dp.toPx()
            val pillShort = 7.dp.toPx()
            listOf(topMid, botMid).forEach { pt ->
                drawRoundRect(AccentBlue, Offset(pt.x - pillLong / 2f, pt.y - pillShort / 2f), Size(pillLong, pillShort), CornerRadius(3.dp.toPx()))
            }
            listOf(leftMid, rightMid).forEach { pt ->
                drawRoundRect(AccentBlue, Offset(pt.x - pillShort / 2f, pt.y - pillLong / 2f), Size(pillShort, pillLong), CornerRadius(3.dp.toPx()))
            }

            // Live Corner Magnifier Loupe
            if (activeHandle in 0..3) {
                val activeNorm = when (activeHandle) {
                    0 -> quad.topLeft; 1 -> quad.topRight; 2 -> quad.bottomRight; else -> quad.bottomLeft
                }
                val loupeSize = 110.dp.toPx().roundToInt()
                val loupeX = if (activeNorm.x > 0.5f) 16.dp.toPx().roundToInt() else (size.width - loupeSize - 16.dp.toPx()).roundToInt()
                val loupeY = 16.dp.toPx().roundToInt()
                val cropRad = (min(loaded.width, loaded.height) * 0.08f).roundToInt().coerceAtLeast(32)
                val cx = (activeNorm.x * loaded.width).roundToInt().coerceIn(cropRad, (loaded.width - cropRad).coerceAtLeast(cropRad))
                val cy = (activeNorm.y * loaded.height).roundToInt().coerceIn(cropRad, (loaded.height - cropRad).coerceAtLeast(cropRad))
                drawImage(
                    image = loaded.asImageBitmap(),
                    srcOffset = IntOffset((cx - cropRad).coerceAtLeast(0), (cy - cropRad).coerceAtLeast(0)),
                    srcSize = IntSize((cropRad * 2).coerceAtMost(loaded.width), (cropRad * 2).coerceAtMost(loaded.height)),
                    dstOffset = IntOffset(loupeX, loupeY),
                    dstSize = IntSize(loupeSize, loupeSize)
                )
                drawRect(AccentBlue, topLeft = Offset(loupeX.toFloat(), loupeY.toFloat()), size = Size(loupeSize.toFloat(), loupeSize.toFloat()), style = Stroke(2.dp.toPx()))
                val lc = Offset(loupeX + loupeSize / 2f, loupeY + loupeSize / 2f)
                drawLine(AccentBlue, Offset(lc.x - 14f, lc.y), Offset(lc.x + 14f, lc.y), 2f)
                drawLine(AccentBlue, Offset(lc.x, lc.y - 14f), Offset(lc.x, lc.y + 14f), 2f)
            }
        }
    }
}

@Composable
private fun InteractiveLiveCleanupCanvas(
    imagePath: String,
    revision: Int,
    currentMode: CleanupBrushMode,
    customColorArgb: Int,
    brushRadiusNorm: Float,
    eyedropperActive: Boolean,
    onEyedropperTap: (Float, Float) -> Unit,
    onStrokeComplete: (List<NormalizedPoint>) -> Unit
) {
    val bmp by produceState<Bitmap?>(initialValue = null, imagePath, revision) {
        value = withContext(Dispatchers.IO) { decodeForUi(imagePath, 1400) }
    }
    var livePoints by remember { mutableStateOf<List<NormalizedPoint>>(emptyList()) }
    val latestOnComplete by rememberUpdatedState(onStrokeComplete)
    val latestOnEyedropper by rememberUpdatedState(onEyedropperTap)
    val latestEyedropperActive by rememberUpdatedState(eyedropperActive)

    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val loaded = bmp ?: return@BoxWithConstraints
        val imgRect = computeFitRect(loaded.width.toFloat(), loaded.height.toFloat(), constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())

        Image(
            bitmap = loaded.asImageBitmap(),
            contentDescription = "Live Cleanup Preview",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(imgRect) {
                    detectTapGestures { offset ->
                        if (latestEyedropperActive) {
                            val nx = ((offset.x - imgRect.left) / imgRect.width).coerceIn(0f, 1f)
                            val ny = ((offset.y - imgRect.top) / imgRect.height).coerceIn(0f, 1f)
                            latestOnEyedropper(nx, ny)
                        }
                    }
                }
                .pointerInput(imgRect) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (latestEyedropperActive) return@detectDragGestures
                            val nx = ((offset.x - imgRect.left) / imgRect.width).coerceIn(0f, 1f)
                            val ny = ((offset.y - imgRect.top) / imgRect.height).coerceIn(0f, 1f)
                            livePoints = listOf(NormalizedPoint(nx, ny))
                        },
                        onDragEnd = {
                            if (livePoints.isNotEmpty()) latestOnComplete(livePoints)
                            livePoints = emptyList()
                        },
                        onDragCancel = { livePoints = emptyList() },
                        onDrag = { change, _ ->
                            if (latestEyedropperActive) return@detectDragGestures
                            change.consume()
                            val p = change.position
                            val nx = ((p.x - imgRect.left) / imgRect.width).coerceIn(0f, 1f)
                            val ny = ((p.y - imgRect.top) / imgRect.height).coerceIn(0f, 1f)
                            livePoints = livePoints + NormalizedPoint(nx, ny)
                        }
                    )
                }
        ) {
            if (livePoints.isNotEmpty()) {
                val baseDim = min(imgRect.width, imgRect.height)
                val strokeW = (brushRadiusNorm * 2f * baseDim).coerceAtLeast(10f)
                val color = if (currentMode == CleanupBrushMode.SURROUNDING_COLOR) AccentBlue.copy(alpha = 0.45f) else Color(customColorArgb)
                if (livePoints.size == 1) {
                    val p = livePoints.first()
                    drawCircle(color, radius = strokeW / 2f, center = Offset(imgRect.left + p.x * imgRect.width, imgRect.top + p.y * imgRect.height))
                } else {
                    val path = Path().apply {
                        moveTo(imgRect.left + livePoints.first().x * imgRect.width, imgRect.top + livePoints.first().y * imgRect.height)
                        for (i in 1 until livePoints.size) {
                            lineTo(imgRect.left + livePoints[i].x * imgRect.width, imgRect.top + livePoints[i].y * imgRect.height)
                        }
                    }
                    drawPath(path, color, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}

@Composable
private fun InteractiveMarkupCanvas(
    imagePath: String,
    revision: Int,
    strokes: List<com.offlinepdf.studio.core.imageprocessing.MarkupStroke>,
    colorArgb: Int,
    isHighlighter: Boolean,
    strokeWidthNorm: Float,
    onStrokeComplete: (List<NormalizedPoint>) -> Unit
) {
    val bmp by produceState<Bitmap?>(initialValue = null, imagePath, revision) {
        value = withContext(Dispatchers.IO) { decodeForUi(imagePath, 1400) }
    }
    var livePoints by remember { mutableStateOf<List<NormalizedPoint>>(emptyList()) }
    val latestOnComplete by rememberUpdatedState(onStrokeComplete)

    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val loaded = bmp ?: return@BoxWithConstraints
        val imgRect = computeFitRect(loaded.width.toFloat(), loaded.height.toFloat(), constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())

        Image(
            bitmap = loaded.asImageBitmap(),
            contentDescription = "Markup Preview",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(imgRect) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val nx = ((offset.x - imgRect.left) / imgRect.width).coerceIn(0f, 1f)
                            val ny = ((offset.y - imgRect.top) / imgRect.height).coerceIn(0f, 1f)
                            livePoints = listOf(NormalizedPoint(nx, ny))
                        },
                        onDragEnd = {
                            if (livePoints.size >= 2) latestOnComplete(livePoints)
                            livePoints = emptyList()
                        },
                        onDragCancel = { livePoints = emptyList() },
                        onDrag = { change, _ ->
                            change.consume()
                            val p = change.position
                            val nx = ((p.x - imgRect.left) / imgRect.width).coerceIn(0f, 1f)
                            val ny = ((p.y - imgRect.top) / imgRect.height).coerceIn(0f, 1f)
                            livePoints = livePoints + NormalizedPoint(nx, ny)
                        }
                    )
                }
        ) {
            val baseDim = min(imgRect.width, imgRect.height)
            fun drawMarkup(pts: List<NormalizedPoint>, widthNorm: Float, argb: Int, highlighter: Boolean) {
                if (pts.size < 2) return
                val color = Color(argb).copy(alpha = if (highlighter) 0.36f else 0.96f)
                val path = Path().apply {
                    moveTo(imgRect.left + pts.first().x * imgRect.width, imgRect.top + pts.first().y * imgRect.height)
                    for (i in 1 until pts.size) {
                        lineTo(imgRect.left + pts[i].x * imgRect.width, imgRect.top + pts[i].y * imgRect.height)
                    }
                }
                drawPath(
                    path,
                    color,
                    style = Stroke(
                        width = (widthNorm * baseDim).coerceAtLeast(4f),
                        cap = if (highlighter) StrokeCap.Square else StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
            strokes.forEach { drawMarkup(it.points, it.strokeWidthNorm, it.colorArgb, it.isHighlighter) }
            if (livePoints.size >= 2) drawMarkup(livePoints, strokeWidthNorm, colorArgb, isHighlighter)
        }
    }
}

private fun buildLiveColorFilter(brightness: Float, contrast: Float): ColorFilter {
    val c = contrast.coerceIn(0.6f, 1.8f)
    val b = brightness.coerceIn(-0.4f, 0.4f) * 255f
    val translate = (-0.5f * c + 0.5f) * 255f + b
    val matrix = ColorMatrix(
        floatArrayOf(
            c, 0f, 0f, 0f, translate,
            0f, c, 0f, 0f, translate,
            0f, 0f, c, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
    )
    return ColorFilter.colorMatrix(matrix)
}

private fun computeFitRect(imgW: Float, imgH: Float, boxW: Float, boxH: Float): Rect {
    if (imgW <= 0f || imgH <= 0f || boxW <= 0f || boxH <= 0f) return Rect(0f, 0f, boxW, boxH)
    val scale = min(boxW / imgW, boxH / imgH)
    val w = imgW * scale
    val h = imgH * scale
    val left = (boxW - w) / 2f
    val top = (boxH - h) / 2f
    return Rect(left, top, left + w, top + h)
}

@Composable
private fun PagePreviewImage(
    path: String,
    revision: Int,
    liveRotationDeg: Float = 0f,
    colorFilter: ColorFilter? = null,
    modifier: Modifier = Modifier
) {
    val bmp by produceState<Bitmap?>(initialValue = null, path, revision) {
        value = withContext(Dispatchers.IO) { decodeForUi(path, 1600) }
    }
    if (bmp != null) {
        Image(
            bitmap = bmp!!.asImageBitmap(),
            contentDescription = "Scanned Page",
            contentScale = ContentScale.Fit,
            colorFilter = colorFilter,
            modifier = modifier.graphicsLayer {
                rotationZ = liveRotationDeg
            }
        )
    } else {
        CircularProgressIndicator(color = AccentBlue, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun PageThumbnailImage(path: String, revision: Int, modifier: Modifier = Modifier) {
    val bmp by produceState<Bitmap?>(initialValue = null, path, revision) {
        value = withContext(Dispatchers.IO) { decodeForUi(path, 320) }
    }
    if (bmp != null) {
        Image(
            bitmap = bmp!!.asImageBitmap(),
            contentDescription = "Thumbnail",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}

private fun decodeForUi(path: String, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val maxDim = kotlin.math.max(bounds.outWidth, bounds.outHeight)
    if (maxDim <= 0) return null
    var sample = 1
    while (maxDim / sample > maxSide) sample *= 2
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeFile(path, opts)
}
