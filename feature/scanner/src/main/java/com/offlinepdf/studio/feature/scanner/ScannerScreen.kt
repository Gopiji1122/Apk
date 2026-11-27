package com.offlinepdf.studio.feature.scanner

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Hd
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
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
import com.offlinepdf.studio.core.common.Formatters
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
    ) { uris -> if (uris.isNotEmpty()) viewModel.importGalleryImages(uris) }

    var showExportSheet by remember { mutableStateOf(false) }
    var showOcrSheet by remember { mutableStateOf(false) }
    var showScannerSettingsSheet by remember { mutableStateOf(false) }

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

    val exportTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> if (uri != null) viewModel.exportOcrTxtToUri(uri) }

    val importEngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.importTrainedData(uri, "eng") }

    val importHinLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.importTrainedData(uri, "hin") }

    LaunchedEffect(state.statusMessage) {
        val msg = state.statusMessage ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        viewModel.clearStatusMessage()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (state.stage) {
            ScannerStage.HUB -> {
                ScannerHubStage(
                    state = state,
                    onStartScan = { viewModel.openStage(ScannerStage.CAMERA) },
                    onImportGallery = { galleryPicker.launch("image/*") },
                    onOpenRecentPdf = onOpenExportedPdf,
                    onResumeRecovered = viewModel::resumeRecoveredSession,
                    onDiscardRecovered = viewModel::discardRecoveredSession,
                    onOpenSettings = { showScannerSettingsSheet = true },
                    onOpenCurrentPages = { viewModel.openStage(ScannerStage.PAGES_REVIEW) }
                )
            }
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
                        onBackToHub = { viewModel.openStage(ScannerStage.HUB) },
                        onImportGallery = { galleryPicker.launch("image/*") }
                    )
                }
            }
            ScannerStage.CROP -> {
                state.activePage?.let { active ->
                    CropPerspectiveStage(
                        page = active,
                        previewRevision = state.previewRevision,
                        viewModel = viewModel
                    )
                }
            }
            ScannerStage.ENHANCE -> {
                state.activePage?.let { active ->
                    EnhanceFilterStage(
                        page = active,
                        state = state,
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
                    onImportGallery = { galleryPicker.launch("image/*") },
                    onOcrClick = { showOcrSheet = true },
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
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(state.processingMessage.ifBlank { "Processing..." }, style = MaterialTheme.typography.titleSmall)
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

    // Verified Export Bottom Sheet
    if (showExportSheet) {
        val opts = state.exportOptions
        val isMultiImageZip = (opts.format == ExportFormat.JPEG || opts.format == ExportFormat.PNG) && state.pages.size > 1
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
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ExportFormat.entries) { fmt ->
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

                if (opts.format == ExportFormat.PDF || opts.format == ExportFormat.SEARCHABLE_PDF) {
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
                            opts.format == ExportFormat.PDF || opts.format == ExportFormat.SEARCHABLE_PDF ->
                                exportPdfLauncher.launch("$base.pdf")
                            isMultiImageZip -> exportZipLauncher.launch("${base}_images.zip")
                            opts.format == ExportFormat.JPEG -> exportJpegLauncher.launch("$base.jpg")
                            else -> exportPngLauncher.launch("$base.png")
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    val btnLabel = when {
                        opts.format == ExportFormat.SEARCHABLE_PDF -> "Save Searchable PDF (OCR)"
                        opts.format == ExportFormat.PDF -> "Save Verified PDF"
                        isMultiImageZip -> "Save All ${state.pages.size} Images (ZIP)"
                        else -> "Save ${opts.format.extension.uppercase()}"
                    }
                    Text(btnLabel, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Offline OCR Sheet (English + Hindi Devanagari via Tesseract4Android)
    if (showOcrSheet) {
        ModalBottomSheet(onDismissRequest = { showOcrSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Offline OCR (English & Hindi)", style = MaterialTheme.typography.titleLarge)

                // Model Availability Status
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "English (eng): ${if (state.ocrLangStatus.engAvailable) "Ready offline" else "Not installed"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Hindi / Devanagari (hin): ${if (state.ocrLangStatus.hinAvailable) "Ready offline" else "Not installed"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { importEngLauncher.launch(arrayOf("*/*")) },
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("Import eng.traineddata", fontSize = 12.sp) }
                            OutlinedButton(
                                onClick = { importHinLauncher.launch(arrayOf("*/*")) },
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("Import hin.traineddata", fontSize = 12.sp) }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("eng" to "English", "hin" to "हिन्दी", "eng+hin" to "EN + हिन्दी").forEach { (code, label) ->
                        FilterChip(
                            selected = state.ocrLanguageCode == code,
                            onClick = { viewModel.setOcrLanguage(code) },
                            label = { Text(label) }
                        )
                    }
                }

                if (state.isRunningOcr) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val p = state.ocrProgress
                        Text("Recognizing page ${p?.first ?: 0} of ${p?.second ?: state.pages.size}...")
                        TextButton(onClick = viewModel::cancelOcr) { Text("Cancel") }
                    }
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    Button(
                        onClick = viewModel::runOfflineOcr,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Icon(Icons.Outlined.TextFields, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Extract Text from ${state.pages.size} Page(s)")
                    }
                }

                if (state.ocrResults.isNotEmpty()) {
                    OutlinedTextField(
                        value = state.ocrSearchQuery,
                        onValueChange = viewModel::updateOcrSearchQuery,
                        label = { Text("Filter recognized text...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val filteredText = if (state.ocrSearchQuery.isBlank()) {
                        state.combinedOcrText
                    } else {
                        state.combinedOcrText.lines()
                            .filter { it.contains(state.ocrSearchQuery, ignoreCase = true) }
                            .joinToString("\n")
                            .ifBlank { "No matching lines." }
                    }
                    Card(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                        SelectionContainer {
                            Text(
                                text = filteredText,
                                modifier = Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("OCR Text", state.combinedOcrText))
                                Toast.makeText(context, "Copied OCR text", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy")
                        }
                        Button(
                            onClick = {
                                val base = state.exportOptions.title.ifBlank { "Scan_OCR" }
                                exportTxtLauncher.launch("$base.txt")
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Download, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export TXT")
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Scanner Settings Sheet (DataStore persisted)
    if (showScannerSettingsSheet) {
        ModalBottomSheet(onDismissRequest = { showScannerSettingsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Scanner Settings", style = MaterialTheme.typography.titleLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Automatic Capture")
                    Switch(checked = state.autoCaptureEnabled, onCheckedChange = { viewModel.toggleAutoCapture() })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Capture Shutter Sound")
                    Switch(checked = state.captureSoundEnabled, onCheckedChange = { viewModel.toggleCaptureSound() })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Batch Continuous Scan Mode")
                    Switch(checked = state.batchModeEnabled, onCheckedChange = { viewModel.toggleBatchMode() })
                }
                Text("Edge Detection Sensitivity", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("LOW", "NORMAL", "HIGH").forEach { level ->
                        FilterChip(
                            selected = state.detectionSensitivity == level,
                            onClick = { viewModel.setDetectionSensitivity(level) },
                            label = { Text(level) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    state.lastExportedPdfUri?.let { exportedUri ->
        AlertDialog(
            onDismissRequest = viewModel::clearLastExportedPdfUri,
            title = { Text("Verified PDF Saved") },
            text = { Text("Your scanned PDF passed page-count verification and was added to Recently Scanned.") },
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
private fun ScannerHubStage(
    state: ScannerUiState,
    onStartScan: () -> Unit,
    onImportGallery: () -> Unit,
    onOpenRecentPdf: (String) -> Unit,
    onResumeRecovered: () -> Unit,
    onDiscardRecovered: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCurrentPages: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Document Scanner", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "100% offline • OpenCV + Tesseract OCR",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Scanner settings")
                }
            }
        }

        // Session Recovery Banner
        if (state.recoveredSessionCount > 0) {
            item {
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Unsaved scan session recovered (${state.recoveredSessionCount} pages)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onResumeRecovered, shape = RoundedCornerShape(12.dp)) {
                                Text("Resume Session")
                            }
                            OutlinedButton(onClick = onDiscardRecovered, shape = RoundedCornerShape(12.dp)) {
                                Text("Discard")
                            }
                        }
                    }
                }
            }
        }

        // Large Primary Scan Document Card
        item {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onStartScan)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.DocumentScanner, contentDescription = null, modifier = Modifier.size(28.dp))
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Scan Document",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Auto edge detection, 10 filters, unlimited pages & offline OCR",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = onImportGallery,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(46.dp)
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Import Photos")
                }
                if (state.pages.isNotEmpty()) {
                    Button(
                        onClick = onOpenCurrentPages,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Text("Active (${state.pages.size})")
                    }
                }
            }
        }

        item {
            Text("Recent Scans", style = MaterialTheme.typography.titleMedium)
        }
        if (state.recentScans.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No saved scans yet. Tap 'Scan Document' to capture your first multi-page PDF.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(state.recentScans, key = { it.uriString }) { doc ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenRecentPdf(doc.uriString) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.PictureAsPdf, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(doc.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${doc.pageCount} pages • ${Formatters.formatFileSize(doc.sizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionPrompt(
    onRequestPermission: () -> Unit,
    onImportGallery: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Outlined.CameraAlt, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Camera Permission Needed", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Offline PDF Studio uses your rear camera locally to detect document edges and scan pages. Nothing is ever uploaded.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRequestPermission, shape = RoundedCornerShape(14.dp), modifier = Modifier.height(46.dp)) {
            Text("Grant Camera Permission")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onImportGallery, shape = RoundedCornerShape(14.dp), modifier = Modifier.height(46.dp)) {
            Icon(Icons.Outlined.PhotoLibrary, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Import from Gallery")
        }
    }
}

@Composable
private fun CameraCaptureStage(
    state: ScannerUiState,
    viewModel: ScannerViewModel,
    onBackToHub: () -> Unit,
    onImportGallery: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val stabilityTracker = remember { QuadStabilityTracker() }
    val lastAnalysisMs = remember { AtomicLong(0L) }

    var detectedQuad by remember { mutableStateOf<DocumentQuad?>(null) }
    var confidence by remember { mutableFloatStateOf(0f) }
    var stabilityProgress by remember { mutableFloatStateOf(0f) }
    var qualityHint by remember { mutableStateOf<String?>(null) }
    var currentZoomRatio by remember { mutableFloatStateOf(1f) }
    var exposureIndex by remember { mutableIntStateOf(0) }
    var exposureRange by remember { mutableStateOf(-4..4) }
    var isCapturing by remember { mutableStateOf(false) }

    var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    val latestAutoEnabled by rememberUpdatedState(state.autoCaptureEnabled)
    val latestFlashEnabled by rememberUpdatedState(state.flashEnabled)
    val latestSensitivity by rememberUpdatedState(state.detectionSensitivity)

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
            runCatching { cameraProviderRef?.unbindAll() }
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(previewViewRef, state.qualityMode) {
        val pv = previewViewRef ?: return@LaunchedEffect
        pv.post {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val provider = runCatching { cameraProviderFuture.get() }.getOrNull() ?: return@addListener
                cameraProviderRef = provider

                val preview = Preview.Builder().build().also { it.surfaceProvider = pv.surfaceProvider }
                val captureMode = if (state.qualityMode == ScanQualityMode.HD) {
                    ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                } else {
                    ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                }
                val capture = ImageCapture.Builder().setCaptureMode(captureMode).build()
                imageCaptureRef = capture

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

                // Zero-Bitmap Y-plane luminance analysis!
                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    try {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastAnalysisMs.get() < 100L) return@setAnalyzer
                        lastAnalysisMs.set(now)

                        val yPlane = imageProxy.planes[0]
                        val frameResult = viewModel.edgeDetector.analyzeYPlane(
                            yBuffer = yPlane.buffer,
                            width = imageProxy.width,
                            height = imageProxy.height,
                            rowStride = yPlane.rowStride,
                            rotationDegrees = imageProxy.imageInfo.rotationDegrees
                        )

                        val stability = stabilityTracker.onFrameEvaluated(frameResult, latestSensitivity)
                        ContextCompat.getMainExecutor(context).execute {
                            detectedQuad = stability.detectedQuad
                            confidence = stability.confidence
                            stabilityProgress = stability.progress
                            qualityHint = stability.qualityHint
                            if (latestAutoEnabled && stability.shouldAutoCapture && !isCapturing) {
                                triggerCapture(stability.detectedQuad)
                            }
                        }
                    } catch (_: Throwable) {
                        // Never stall live camera preview
                    } finally {
                        imageProxy.close()
                    }
                }

                runCatching {
                    provider.unbindAll()
                    val vp = pv.viewPort
                    val boundCamera = if (vp != null) {
                        val group = UseCaseGroup.Builder()
                            .addUseCase(preview).addUseCase(capture).addUseCase(analysis)
                            .setViewPort(vp).build()
                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, group)
                    } else {
                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture, analysis)
                    }
                    cameraRef = boundCamera
                    boundCamera.cameraControl.enableTorch(latestFlashEnabled)
                    val expState = boundCamera.cameraInfo.exposureState
                    if (expState.isExposureCompensationSupported) {
                        val r = expState.exposureCompensationRange
                        exposureRange = r.lower.coerceAtLeast(-6)..r.upper.coerceAtMost(6)
                    }
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    LaunchedEffect(state.flashEnabled, cameraRef) {
        cameraRef?.cameraControl?.enableTorch(state.flashEnabled)
    }

    LaunchedEffect(exposureIndex, cameraRef) {
        val cam = cameraRef ?: return@LaunchedEffect
        if (cam.cameraInfo.exposureState.isExposureCompensationSupported) {
            cam.cameraControl.setExposureCompensationIndex(exposureIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Minimalist Camera Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF101216))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onBackToHub, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                FilterChip(
                    selected = state.autoCaptureEnabled,
                    onClick = viewModel::toggleAutoCapture,
                    label = { Text(if (state.autoCaptureEnabled) "Auto" else "Manual", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = state.batchModeEnabled,
                    onClick = viewModel::toggleBatchMode,
                    label = { Text(if (state.batchModeEnabled) "Batch" else "Single", fontSize = 12.sp) }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = state.qualityMode == ScanQualityMode.HD,
                    onClick = viewModel::toggleQualityMode,
                    label = { Text(state.qualityMode.displayName, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Outlined.Hd, null, modifier = Modifier.size(15.dp)) }
                )
                IconButton(onClick = viewModel::toggleFlash, modifier = Modifier.size(38.dp)) {
                    Icon(
                        if (state.flashEnabled) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                        contentDescription = "Flash",
                        tint = if (state.flashEnabled) Color(0xFFFDE047) else Color.White
                    )
                }
                IconButton(onClick = onImportGallery, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Gallery", tint = Color.White)
                }
            }
        }

        // Full-screen Camera Preview + Pinch-Zoom + Tap-to-Focus + Smooth Quad Overlay
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        val pv = previewViewRef ?: return@detectTapGestures
                        val cam = cameraRef ?: return@detectTapGestures
                        val pt = pv.meteringPointFactory.createPoint(tapOffset.x, tapOffset.y)
                        cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(pt).build())
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
                                        val zs = cam.cameraInfo.zoomState.value
                                        val minZ = zs?.minZoomRatio ?: 1f
                                        val maxZ = (zs?.maxZoomRatio ?: 6f).coerceAtMost(8f)
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
                val pts = listOf(
                    Offset(q.topLeft.x * size.width, q.topLeft.y * size.height),
                    Offset(q.topRight.x * size.width, q.topRight.y * size.height),
                    Offset(q.bottomRight.x * size.width, q.bottomRight.y * size.height),
                    Offset(q.bottomLeft.x * size.width, q.bottomLeft.y * size.height)
                )
                val path = Path().apply {
                    moveTo(pts[0].x, pts[0].y); lineTo(pts[1].x, pts[1].y)
                    lineTo(pts[2].x, pts[2].y); lineTo(pts[3].x, pts[3].y); close()
                }
                val activeColor = if (stabilityProgress >= 0.5f) Color(0xFF22C55E) else Color(0xFF38BDF8)
                drawPath(path, activeColor.copy(alpha = 0.16f))
                drawPath(path, activeColor, style = Stroke(width = 5f))
                pts.forEach { pt ->
                    drawCircle(Color.White, radius = 10f, center = pt)
                    drawCircle(activeColor, radius = 6f, center = pt)
                }
            }

            // Status & Detection Confidence Pill
            Surface(
                color = Color(0xBF111827),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
            ) {
                val confPct = (confidence * 100).roundToInt()
                val text = when {
                    state.replacingPageIndex != null -> "Retaking Page #${state.replacingPageIndex + 1}"
                    qualityHint != null -> qualityHint!!
                    detectedQuad != null && state.autoCaptureEnabled && stabilityProgress >= 0.5f -> "Capturing... ($confPct%)"
                    detectedQuad != null -> "Document detected ($confPct%)"
                    else -> "Align document • Pinch to zoom"
                }
                Text(text, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }

            if (currentZoomRatio > 1.05f) {
                Surface(
                    color = Color(0xBF111827),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
                ) {
                    Text(
                        String.format("%.1fx", currentZoomRatio),
                        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Compact Exposure Strip + Clean Shutter Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF101216))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (exposureRange.last > exposureRange.first) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("EV", color = Color(0xFF9CA3AF), fontSize = 11.sp, modifier = Modifier.width(24.dp))
                    Slider(
                        value = exposureIndex.toFloat(),
                        onValueChange = { exposureIndex = it.roundToInt() },
                        valueRange = exposureRange.first.toFloat()..exposureRange.last.toFloat(),
                        modifier = Modifier.weight(1f).height(26.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.pages.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = { viewModel.openStage(ScannerStage.PAGES_REVIEW) },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) { Text("${state.pages.size} Pages", fontSize = 13.sp) }
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
                            Icon(Icons.Outlined.CameraAlt, contentDescription = "Capture", tint = Color.Black, modifier = Modifier.size(28.dp))
                        }
                    }
                }

                if (state.pages.isNotEmpty()) {
                    Button(
                        onClick = { viewModel.openStage(ScannerStage.PAGES_REVIEW) },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                    ) { Text("Done", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                } else {
                    Spacer(Modifier.width(92.dp))
                }
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

@Composable
private fun CurvedRotationDial(
    angleDegrees: Float,
    onAngleChange: (Float) -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    modifier: Modifier = Modifier
) {
    val latestAngle by rememberUpdatedState(angleDegrees)
    val latestOnChange by rememberUpdatedState(onAngleChange)

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1F232B),
            modifier = Modifier.size(38.dp).clickable(onClick = onRotateLeft)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.RotateLeft, contentDescription = "Rotate Left", tint = Color.White, modifier = Modifier.size(19.dp))
            }
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (abs(angleDegrees) > 0.2f) Color(0xFF1E3A5F) else Color(0xFF1F232B),
            modifier = Modifier.width(50.dp).clickable { onAngleChange(0f) }
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                Text(
                    String.format("%+.1f°", angleDegrees),
                    color = if (abs(angleDegrees) > 0.2f) Color(0xFF7DD3FC) else Color(0xFFD1D5DB),
                    fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(46.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        var next = (latestAngle - dragAmount * 0.08f).coerceIn(-15f, 15f)
                        if (abs(next) < 0.35f) next = 0f
                        latestOnChange(next)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                val arcRadius = size.width * 1.15f
                val arcCenterY = size.height * 0.22f + arcRadius

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
                    drawLine(tickColor, Offset(startX, startY), Offset(endX, endY), if (isMajor) 2.2f else 1.4f, StrokeCap.Round)
                }

                drawLine(
                    Color(0xFF38BDF8),
                    Offset(centerX, 2.dp.toPx()),
                    Offset(centerX, 24.dp.toPx()),
                    3.2f,
                    StrokeCap.Round
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1F232B),
            modifier = Modifier.size(38.dp).clickable(onClick = onRotateRight)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.RotateRight, contentDescription = "Rotate Right", tint = Color.White, modifier = Modifier.size(19.dp))
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
            Text(valueText, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
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
            modifier = Modifier.height(30.dp)
        )
    }
}

/**
 * Pro Crop & Perspective Editor:
 * - 4 independently draggable corner handles (0..3)
 * - 4 draggable edge midpoint handles (4..7) to shift entire edges cleanly
 * - Live circular corner magnifier loupe
 * - Rotate Left / Right + Curved Protractor Rotation Dial
 */
@Composable
private fun CropPerspectiveStage(
    page: ScannedPageItem,
    previewRevision: Int,
    viewModel: ScannerViewModel
) {
    var previewBitmap by remember(page.rawImagePath, previewRevision) { mutableStateOf<Bitmap?>(null) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var activeHandle by remember { mutableIntStateOf(-1) } // 0..3 corners, 4..7 edge midpoints

    val latestQuad by rememberUpdatedState(page.quad)

    LaunchedEffect(page.rawImagePath, previewRevision) {
        previewBitmap = viewModel.loadPreviewBitmap(page.rawImagePath, maxSide = 1400)
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0E1015)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Crop & Perspective", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = viewModel::autoDetectActiveQuad) { Text("Auto") }
                TextButton(onClick = viewModel::resetActiveQuad) { Text("Reset") }
            }
        }

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().onSizeChanged { boxSize = it },
            contentAlignment = Alignment.Center
        ) {
            val bmp = previewBitmap
            if (bmp != null && !bmp.isRecycled) {
                val fitRect = remember(bmp.width, bmp.height, boxSize) {
                    computeFitRect(bmp.width, bmp.height, boxSize)
                }

                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Captured document",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer(rotationZ = page.fineRotationDegrees)
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
                                    val allHandles = latestQuad.asList() + latestQuad.edgeMidpoints()
                                    activeHandle = allHandles.indices.minByOrNull { idx ->
                                        hypot((allHandles[idx].x - nx).toDouble(), (allHandles[idx].y - ny).toDouble())
                                    } ?: -1
                                },
                                onDragEnd = { activeHandle = -1 },
                                onDragCancel = { activeHandle = -1 },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (activeHandle !in 0..7 || fitRect.width <= 1f || fitRect.height <= 1f) return@detectDragGestures
                                    val dx = dragAmount.x / fitRect.width
                                    val dy = dragAmount.y / fitRect.height
                                    val q = latestQuad
                                    val updated = when (activeHandle) {
                                        0 -> q.copy(topLeft = NormalizedPoint(q.topLeft.x + dx, q.topLeft.y + dy).clamped())
                                        1 -> q.copy(topRight = NormalizedPoint(q.topRight.x + dx, q.topRight.y + dy).clamped())
                                        2 -> q.copy(bottomRight = NormalizedPoint(q.bottomRight.x + dx, q.bottomRight.y + dy).clamped())
                                        3 -> q.copy(bottomLeft = NormalizedPoint(q.bottomLeft.x + dx, q.bottomLeft.y + dy).clamped())
                                        // Edge midpoints move both adjacent corners cleanly!
                                        4 -> q.copy(
                                            topLeft = NormalizedPoint(q.topLeft.x, q.topLeft.y + dy).clamped(),
                                            topRight = NormalizedPoint(q.topRight.x, q.topRight.y + dy).clamped()
                                        )
                                        5 -> q.copy(
                                            topRight = NormalizedPoint(q.topRight.x + dx, q.topRight.y).clamped(),
                                            bottomRight = NormalizedPoint(q.bottomRight.x + dx, q.bottomRight.y).clamped()
                                        )
                                        6 -> q.copy(
                                            bottomRight = NormalizedPoint(q.bottomRight.x, q.bottomRight.y + dy).clamped(),
                                            bottomLeft = NormalizedPoint(q.bottomLeft.x, q.bottomLeft.y + dy).clamped()
                                        )
                                        else -> q.copy(
                                            bottomLeft = NormalizedPoint(q.bottomLeft.x + dx, q.bottomLeft.y).clamped(),
                                            topLeft = NormalizedPoint(q.topLeft.x + dx, q.topLeft.y).clamped()
                                        )
                                    }
                                    viewModel.updateActiveQuad(updated)
                                }
                            )
                        }
                ) {
                    val q = page.quad
                    val corners = listOf(
                        Offset(fitRect.left + q.topLeft.x * fitRect.width, fitRect.top + q.topLeft.y * fitRect.height),
                        Offset(fitRect.left + q.topRight.x * fitRect.width, fitRect.top + q.topRight.y * fitRect.height),
                        Offset(fitRect.left + q.bottomRight.x * fitRect.width, fitRect.top + q.bottomRight.y * fitRect.height),
                        Offset(fitRect.left + q.bottomLeft.x * fitRect.width, fitRect.top + q.bottomLeft.y * fitRect.height)
                    )
                    val path = Path().apply {
                        moveTo(corners[0].x, corners[0].y); lineTo(corners[1].x, corners[1].y)
                        lineTo(corners[2].x, corners[2].y); lineTo(corners[3].x, corners[3].y); close()
                    }
                    drawPath(path, Color(0x2E38BDF8))
                    drawPath(path, Color(0xFF38BDF8), style = Stroke(width = 5f))

                    // Draw 4 corner handles
                    corners.forEachIndexed { idx, pt ->
                        val sel = idx == activeHandle
                        drawCircle(Color.White, radius = if (sel) 26f else 20f, center = pt)
                        drawCircle(if (sel) Color(0xFF22C55E) else Color(0xFF0284C7), radius = if (sel) 16f else 12f, center = pt)
                    }
                    // Draw 4 subtle edge midpoint pills
                    q.edgeMidpoints().forEachIndexed { idx, mp ->
                        val pt = Offset(fitRect.left + mp.x * fitRect.width, fitRect.top + mp.y * fitRect.height)
                        val sel = (idx + 4) == activeHandle
                        drawCircle(Color.White, radius = if (sel) 15f else 11f, center = pt)
                        drawCircle(if (sel) Color(0xFF22C55E) else Color(0xFF38BDF8), radius = if (sel) 9f else 6f, center = pt)
                    }
                }

                if (activeHandle in 0..3) {
                    val activePt = page.quad.asList()[activeHandle]
                    val loupeAlign = if (activePt.x < 0.5f) Alignment.TopEnd else Alignment.TopStart
                    CornerMagnifierLoupe(
                        source = bmp, normX = activePt.x, normY = activePt.y,
                        modifier = Modifier.align(loupeAlign).padding(10.dp)
                    )
                }
            } else {
                CircularProgressIndicator()
            }
        }

        CurvedRotationDial(
            angleDegrees = page.fineRotationDegrees,
            onAngleChange = viewModel::setActiveFineRotation,
            onRotateLeft = { viewModel.rotateActivePage90(clockwise = false) },
            onRotateRight = { viewModel.rotateActivePage90(clockwise = true) }
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = viewModel::retakeActivePage,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(44.dp)
            ) {
                Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Retake")
            }
            Button(
                onClick = viewModel::applyCropAndProceedToEnhance,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(44.dp)
            ) {
                Icon(Icons.Outlined.Check, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Confirm", fontWeight = FontWeight.SemiBold)
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
        modifier = modifier.size(104.dp).clip(CircleShape).border(2.5.dp, Color(0xFF38BDF8), CircleShape).background(Color.Black)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawImage(
                image = source.asImageBitmap(),
                srcOffset = IntOffset(srcLeft, srcTop),
                srcSize = IntSize(actualW, actualH),
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
            )
            val midX = size.width / 2f; val midY = size.height / 2f
            drawLine(Color(0xFF22C55E), Offset(midX - 18f, midY), Offset(midX + 18f, midY), 3f)
            drawLine(Color(0xFF22C55E), Offset(midX, midY - 18f), Offset(midX, midY + 18f), 3f)
        }
    }
}

@Composable
private fun EnhanceFilterStage(
    page: ScannedPageItem,
    state: ScannerUiState,
    viewModel: ScannerViewModel
) {
    var showOriginal by remember { mutableStateOf(false) }
    var showAdjustSliders by remember { mutableStateOf(false) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val targetPath = if (showOriginal) page.warpedImagePath else page.enhancedImagePath

    LaunchedEffect(targetPath, state.previewRevision) {
        previewBitmap = viewModel.loadPreviewBitmap(targetPath, maxSide = 1400)
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0E1015)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Page Editor", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::undoPageEdit, enabled = state.canUndo, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Undo", tint = if (state.canUndo) Color.White else Color.Gray)
                }
                IconButton(onClick = viewModel::redoPageEdit, enabled = state.canRedo, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "Redo", tint = if (state.canRedo) Color.White else Color.Gray)
                }
                TextButton(onClick = viewModel::resetPageEnhancement) { Text("Reset", fontSize = 12.sp) }
                FilterChip(
                    selected = showOriginal,
                    onClick = { showOriginal = !showOriginal },
                    label = { Text(if (showOriginal) "Orig" else "Compare", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Outlined.Compare, null, modifier = Modifier.size(14.dp)) }
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val bmp = previewBitmap
            if (bmp != null && !bmp.isRecycled) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Enhanced preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CircularProgressIndicator()
            }
        }

        // All 10 distinct professional filters
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ScanFilterMode.entries) { mode ->
                FilterChip(
                    selected = page.filterMode == mode,
                    onClick = { showOriginal = false; viewModel.setActiveFilter(mode) },
                    label = { Text(mode.displayName, fontSize = 12.sp) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { showAdjustSliders = !showAdjustSliders }) {
                Text(if (showAdjustSliders) "Hide Fine Adjustments" else "Strength, Brightness, Contrast & Sharpness")
            }
        }

        if (showAdjustSliders) {
            val c = page.customAdjustments
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PremiumLabeledSlider(
                    label = "Filter Strength",
                    valueText = "${(c.filterStrength * 100).toInt()}%",
                    value = c.filterStrength, valueRange = 0f..1f,
                    onValueChange = { viewModel.setActiveCustomAdjustments(c.copy(filterStrength = it)) },
                    labelColor = Color.White
                )
                PremiumLabeledSlider(
                    label = "Brightness",
                    valueText = "${c.brightness.toInt()}",
                    value = c.brightness, valueRange = -60f..60f,
                    onValueChange = { viewModel.setActiveCustomAdjustments(c.copy(brightness = it)) },
                    labelColor = Color.White
                )
                PremiumLabeledSlider(
                    label = "Contrast",
                    valueText = String.format("%.2f", c.contrast),
                    value = c.contrast, valueRange = 0.6f..1.8f,
                    onValueChange = { viewModel.setActiveCustomAdjustments(c.copy(contrast = it)) },
                    labelColor = Color.White
                )
                PremiumLabeledSlider(
                    label = "Sharpness",
                    valueText = String.format("%.2f", c.sharpness),
                    value = c.sharpness, valueRange = 0f..1.5f,
                    onValueChange = { viewModel.setActiveCustomAdjustments(c.copy(sharpness = it)) },
                    labelColor = Color.White
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.pages.size > 1) {
                OutlinedButton(
                    onClick = viewModel::applyActiveFilterToAllPages,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) { Text("Apply All", fontSize = 12.sp) }
            }
            OutlinedButton(
                onClick = { viewModel.openStage(ScannerStage.CAMERA) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(44.dp)
            ) {
                Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Page", fontSize = 12.sp)
            }
            Button(
                onClick = { viewModel.openStage(ScannerStage.PAGES_REVIEW) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(44.dp)
            ) { Text("Pages (${state.pages.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun PagesReviewStage(
    pages: List<ScannedPageItem>,
    previewRevision: Int,
    viewModel: ScannerViewModel,
    onAddPage: () -> Unit,
    onImportGallery: () -> Unit,
    onOcrClick: () -> Unit,
    onExportClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Pages (${pages.size})", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Auto-saved locally • Tap any page to edit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(
                    onClick = onOcrClick,
                    enabled = pages.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Outlined.TextFields, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("OCR", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onExportClick,
                    enabled = pages.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Outlined.Download, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Export", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
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
                    onReplace = { viewModel.startReplacePage(index) },
                    onRotate = { viewModel.rotatePageInQueue(index, clockwise = true) },
                    onDuplicate = { viewModel.duplicatePage(index) },
                    onMoveLeft = { viewModel.movePage(index, index - 1) },
                    onMoveRight = { viewModel.movePage(index, index + 1) },
                    onDelete = { viewModel.deletePage(index) }
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onAddPage,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(44.dp)
            ) {
                Icon(Icons.Outlined.CameraAlt, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Scan More", fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = onImportGallery,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(44.dp)
            ) {
                Icon(Icons.Outlined.PhotoLibrary, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Import", fontSize = 13.sp)
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
    onReplace: () -> Unit,
    onRotate: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onDelete: () -> Unit
) {
    var thumb by remember(item.id, previewRevision) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.id, previewRevision) { thumb = loadThumb() }

    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
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
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                }
                Surface(
                    color = Color(0xCC111827),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                ) {
                    Text(
                        "#${index + 1}",
                        color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Surface(
                    color = Color(0xCC111827),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clickable(onClick = onReplace)
                ) {
                    Icon(
                        Icons.Outlined.Refresh, contentDescription = "Replace page",
                        tint = Color.White, modifier = Modifier.padding(4.dp).size(14.dp)
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onMoveLeft, enabled = index > 0, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Move left", modifier = Modifier.size(14.dp))
                }
                IconButton(onClick = onCrop, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.Crop, contentDescription = "Crop", modifier = Modifier.size(14.dp))
                }
                IconButton(onClick = onEnhance, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.AutoFixHigh, contentDescription = "Filter", modifier = Modifier.size(14.dp))
                }
                IconButton(onClick = onRotate, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.RotateRight, contentDescription = "Rotate", modifier = Modifier.size(14.dp))
                }
                IconButton(onClick = onDuplicate, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Duplicate", modifier = Modifier.size(14.dp))
                }
                IconButton(onClick = onMoveRight, enabled = index < total - 1, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Move right", modifier = Modifier.size(14.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}
