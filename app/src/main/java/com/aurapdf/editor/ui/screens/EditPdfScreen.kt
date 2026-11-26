package com.aurapdf.editor.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurapdf.editor.data.PdfRepository
import com.aurapdf.editor.engine.PdfBoxEngine
import com.aurapdf.editor.model.CompressionResult
import com.aurapdf.editor.model.EditorLaunchAction
import com.aurapdf.editor.model.ExportFormat
import com.aurapdf.editor.model.PageRangeMode
import com.aurapdf.editor.model.PendingFolderExport
import com.aurapdf.editor.model.EditorSnapshot
import com.aurapdf.editor.model.FreehandStroke
import com.aurapdf.editor.model.NormPoint
import com.aurapdf.editor.model.OverlayItem
import com.aurapdf.editor.model.PdfFontFamily
import com.aurapdf.editor.model.PdfLinkInfo
import com.aurapdf.editor.model.ShapeKind
import com.aurapdf.editor.ui.components.CompressPdfSheet
import com.aurapdf.editor.ui.components.DocumentPropertiesDialog
import com.aurapdf.editor.ui.components.ExportPdfSheet
import com.aurapdf.editor.ui.components.LinksManagerSheet
import com.aurapdf.editor.ui.components.OcrResultDialog
import com.aurapdf.editor.ui.components.PasswordProtectionDialog
import com.aurapdf.editor.ui.components.RichTextColors
import com.aurapdf.editor.ui.components.RichTextEditorDialog
import com.aurapdf.editor.ui.components.ShareUtils
import com.aurapdf.editor.ui.components.SignaturePadDialog
import com.aurapdf.editor.ui.components.WatermarkDialog
import com.aurapdf.editor.ui.theme.AcrobatRed
import com.aurapdf.editor.ui.theme.ToolCommentBlue
import com.aurapdf.editor.ui.theme.ToolCompressGreen
import com.aurapdf.editor.ui.theme.ToolEditPink
import com.aurapdf.editor.ui.theme.ToolOrganizeTeal
import com.aurapdf.editor.ui.theme.ToolSignPurple
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private enum class EditorTab(val label: String, val color: Color) {
    EDIT("Edit PDF", ToolEditPink),
    COMMENT("Comment", ToolCommentBlue),
    SIGN("Fill & Sign", ToolSignPurple),
    ORGANIZE("Organize", ToolOrganizeTeal)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPdfScreen(
    pdfPath: String,
    initialAction: EditorLaunchAction = EditorLaunchAction.NONE,
    repository: PdfRepository,
    engine: PdfBoxEngine,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val file = remember(pdfPath) { File(pdfPath) }

    var password by remember { mutableStateOf<String?>(null) }
    var isLocked by remember { mutableStateOf(false) }
    var unlockInput by remember { mutableStateOf("") }
    var pageCount by remember { mutableIntStateOf(1) }
    var currentPage by remember { mutableIntStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var reloadNonce by remember { mutableIntStateOf(0) }
    var nightInvert by remember { mutableStateOf(false) }

    // Active Acrobat Bottom Mode Tab
    var activeTab by remember { mutableStateOf(EditorTab.EDIT) }

    // Live Unsaved Overlays & Freehand Pen Strokes
    val overlays = remember { mutableStateListOf<OverlayItem>() }
    val strokes = remember { mutableStateListOf<FreehandStroke>() }
    var selectedOverlayId by remember { mutableStateOf<String?>(null) }

    // Live Undo / Redo Stacks + Post-Save Snapshot Undo
    val undoStack = remember { mutableStateListOf<EditorSnapshot>() }
    val redoStack = remember { mutableStateListOf<EditorSnapshot>() }
    var lastSavedSnapshot by remember { mutableStateOf<File?>(null) }

    // Freehand Draw Mode state
    var drawModeActive by remember { mutableStateOf(false) }
    var penColorArgb by remember { mutableLongStateOf(0xFFE52521) }
    var penWidthNorm by remember { mutableFloatStateOf(0.005f) }
    var activeStrokePoints by remember { mutableStateOf<List<NormPoint>>(emptyList()) }

    // Dialogs & Sheets
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var editingTextItem by remember { mutableStateOf<OverlayItem.TextOverlay?>(null) }
    var showAddTextDialog by remember { mutableStateOf(false) }
    var showStickyNoteDialog by remember { mutableStateOf(false) }
    var showSignatureDialog by remember { mutableStateOf(false) }
    var showLinksSheet by remember { mutableStateOf(false) }
    var embeddedLinks by remember { mutableStateOf<List<PdfLinkInfo>>(emptyList()) }
    var showWatermarkDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showPropertiesDialog by remember { mutableStateOf(false) }
    var docProperties by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showCompressSheet by remember { mutableStateOf(false) }
    var lastCompressionResult by remember { mutableStateOf<CompressionResult?>(null) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showSplitDialog by remember { mutableStateOf(false) }
    var showCropDialog by remember { mutableStateOf(false) }
    var showHeaderFooterDialog by remember { mutableStateOf(false) }
    var ocrText by remember { mutableStateOf<String?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    var pendingExportShareFile by remember { mutableStateOf<File?>(null) }
    var pendingFolderExport by remember { mutableStateOf<PendingFolderExport?>(null) }

    val hasUnsavedEdits = overlays.isNotEmpty() || strokes.isNotEmpty()

    fun pushUndoCheckpoint() {
        undoStack.add(EditorSnapshot(overlays = overlays.toList(), strokes = strokes.toList()))
        redoStack.clear()
    }

    fun performUndo() {
        if (undoStack.isNotEmpty()) {
            redoStack.add(EditorSnapshot(overlays = overlays.toList(), strokes = strokes.toList()))
            val prev = undoStack.removeAt(undoStack.lastIndex)
            overlays.clear()
            overlays.addAll(prev.overlays)
            strokes.clear()
            strokes.addAll(prev.strokes)
            selectedOverlayId = null
        }
    }

    fun performRedo() {
        if (redoStack.isNotEmpty()) {
            undoStack.add(EditorSnapshot(overlays = overlays.toList(), strokes = strokes.toList()))
            val next = redoStack.removeAt(redoStack.lastIndex)
            overlays.clear()
            overlays.addAll(next.overlays)
            strokes.clear()
            strokes.addAll(next.strokes)
            selectedOverlayId = null
        }
    }

    val pickOverlayImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val imgDir = File(context.cacheDir, "overlay_imgs").apply { mkdirs() }
            val dst = File(imgDir, "ov_${System.currentTimeMillis()}.png")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dst).use { out -> input.copyTo(out) }
            }
            if (dst.exists()) {
                pushUndoCheckpoint()
                val newImg = OverlayItem.ImageOverlay(
                    pageIndex = currentPage,
                    imagePath = dst.absolutePath
                )
                overlays.add(newImg)
                selectedOverlayId = newImg.id
            }
        }
    }

    val mergePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isWorking = true
            scope.launch {
                runCatching { engine.organizeMergePdf(file, uri, password) }
                    .onSuccess {
                        isWorking = false
                        reloadNonce++
                        Toast.makeText(context, "Merged PDF pages", Toast.LENGTH_SHORT).show()
                    }
                    .onFailure {
                        isWorking = false
                        Toast.makeText(context, "Merge failed: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    val saveCopySafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri: Uri? ->
        if (destUri != null && file.exists()) {
            runCatching {
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
                Toast.makeText(context, "Saved a copy to device", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val folderExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        val req = pendingFolderExport
        if (treeUri != null && req != null) {
            isWorking = true
            scope.launch {
                runCatching {
                    engine.exportPdfImagesToFolder(
                        file = file,
                        pageRangeMode = req.rangeMode,
                        customRange = req.customRange,
                        currentPage = currentPage,
                        format = req.format,
                        jpegQuality = req.jpegQuality,
                        dpi = req.dpi,
                        treeUri = treeUri,
                        password = password
                    )
                }.onSuccess { count ->
                    isWorking = false
                    Toast.makeText(context, "Saved $count individual ${req.format.extension.uppercase()} files to folder", Toast.LENGTH_LONG).show()
                }.onFailure {
                    isWorking = false
                    Toast.makeText(context, "Folder export failed: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        pendingFolderExport = null
    }

    val exportSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destUri: Uri? ->
        val src = pendingExportShareFile
        if (destUri != null && src != null && src.exists()) {
            runCatching {
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    src.inputStream().use { it.copyTo(out) }
                }
                Toast.makeText(context, "Exported ${src.name}", Toast.LENGTH_SHORT).show()
            }
        }
        pendingExportShareFile = null
    }

    BackHandler {
        when {
            drawModeActive -> drawModeActive = false
            selectedOverlayId != null -> selectedOverlayId = null
            hasUnsavedEdits -> showDiscardDialog = true
            else -> onBack()
        }
    }

    LaunchedEffect(initialAction) {
        when (initialAction) {
            EditorLaunchAction.ADD_TEXT -> {
                activeTab = EditorTab.EDIT
                showAddTextDialog = true
            }
            EditorLaunchAction.ORGANIZE -> {
                activeTab = EditorTab.ORGANIZE
            }
            EditorLaunchAction.CROP -> {
                activeTab = EditorTab.ORGANIZE
                showCropDialog = true
            }
            EditorLaunchAction.NONE -> Unit
        }
    }

    LaunchedEffect(pdfPath, password, reloadNonce) {
        val encrypted = engine.checkIsPasswordLocked(file)
        if (encrypted && password.isNullOrEmpty()) {
            isLocked = true
            return@LaunchedEffect
        }
        isLocked = false
        pageCount = engine.getPageCount(file, password)
        currentPage = currentPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        pageBitmap = engine.renderPageBitmap(file, currentPage, 1400, password)
    }

    LaunchedEffect(currentPage, reloadNonce, isLocked) {
        if (!isLocked) {
            pageBitmap = engine.renderPageBitmap(file, currentPage, 1400, password)
        }
    }

    val nightInvertFilter = remember {
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

    if (isLocked) {
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text("Unlock PDF to Edit", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = unlockInput,
                    onValueChange = { unlockInput = it },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            if (engine.verifyPassword(file, unlockInput)) {
                                password = unlockInput
                                isLocked = false
                            } else {
                                Toast.makeText(context, "Incorrect password", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AcrobatRed)
                ) {
                    Text("Unlock", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = onBack) { Text("Back") }
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasUnsavedEdits) showDiscardDialog = true else onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { performUndo() },
                            enabled = undoStack.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Undo, contentDescription = "Undo")
                        }
                        IconButton(
                            onClick = { performRedo() },
                            enabled = redoStack.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Redo, contentDescription = "Redo")
                        }
                        if (!hasUnsavedEdits && lastSavedSnapshot != null) {
                            TextButton(onClick = {
                                val snap = lastSavedSnapshot ?: return@TextButton
                                scope.launch {
                                    engine.restoreSnapshot(file, snap)
                                    lastSavedSnapshot = null
                                    reloadNonce++
                                    Toast.makeText(context, "Reverted to pre-save snapshot", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Text("Revert Save", style = MaterialTheme.typography.labelSmall, color = AcrobatRed)
                            }
                        }
                    }
                },
                actions = {
                    AnimatedVisibility(
                        visible = hasUnsavedEdits,
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        Button(
                            onClick = {
                                isWorking = true
                                scope.launch {
                                    runCatching {
                                        engine.saveEditsToPdf(file, overlays.toList(), strokes.toList(), password)
                                    }.onSuccess { snapshot ->
                                        isWorking = false
                                        lastSavedSnapshot = snapshot
                                        overlays.clear()
                                        strokes.clear()
                                        undoStack.clear()
                                        redoStack.clear()
                                        selectedOverlayId = null
                                        drawModeActive = false
                                        reloadNonce++
                                        Toast.makeText(context, "All edits saved to PDF", Toast.LENGTH_SHORT).show()
                                    }.onFailure {
                                        isWorking = false
                                        Toast.makeText(context, "Save error: ${it.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !isWorking,
                            colors = ButtonDefaults.buttonColors(containerColor = ToolCompressGreen),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save (${overlays.size + strokes.size})", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Advanced Tools")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Links Manager") },
                                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    scope.launch {
                                        embeddedLinks = engine.getLinks(file, password)
                                        showLinksSheet = true
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Custom Watermark") },
                                leadingIcon = { Icon(Icons.Default.BrandingWatermark, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    showWatermarkDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Password Lock / Unlock") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    showPasswordDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Offline ML Kit OCR") },
                                leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    isWorking = true
                                    scope.launch {
                                        runCatching { engine.runOfflineOcrOnPage(file, currentPage, password) }
                                            .onSuccess {
                                                isWorking = false
                                                ocrText = it
                                            }
                                            .onFailure {
                                                isWorking = false
                                                Toast.makeText(context, "OCR error: ${it.message}", Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Document Properties") },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    scope.launch {
                                        docProperties = engine.getDocumentProperties(file, password)
                                        showPropertiesDialog = true
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Compress PDF") },
                                leadingIcon = { Icon(Icons.Default.Compress, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    showCompressSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export PDF") },
                                leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    showExportSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Save a Copy") },
                                leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    saveCopySafLauncher.launch(file.name)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Print") },
                                leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    engine.printPdf(context, file, password)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (nightInvert) "Disable Night Invert" else "Night Invert Mode") },
                                leadingIcon = { Icon(Icons.Default.DarkMode, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    nightInvert = !nightInvert
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    ShareUtils.shareFile(context, file)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                // Contextual Tool Strip for Selected Acrobat Mode Tab (smooth AnimatedContent)
                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                    label = "editor_tab_strip"
                ) { tabState ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (tabState) {
                        EditorTab.EDIT -> {
                            ToolActionChip("Add Text", Icons.Default.TextFields, ToolEditPink) {
                                drawModeActive = false
                                showAddTextDialog = true
                            }
                            ToolActionChip("Add Image", Icons.Default.Image, ToolEditPink) {
                                drawModeActive = false
                                pickOverlayImageLauncher.launch("image/*")
                            }
                            ToolActionChip("Whiteout", Icons.Default.Create, ToolEditPink) {
                                drawModeActive = false
                                pushUndoCheckpoint()
                                val item = OverlayItem.WhiteoutOverlay(pageIndex = currentPage)
                                overlays.add(item)
                                selectedOverlayId = item.id
                            }
                            ToolActionChip("Redact", Icons.Default.Lock, AcrobatRed) {
                                drawModeActive = false
                                pushUndoCheckpoint()
                                val item = OverlayItem.RedactOverlay(pageIndex = currentPage)
                                overlays.add(item)
                                selectedOverlayId = item.id
                            }
                            ToolActionChip("Links", Icons.Default.Link, ToolEditPink) {
                                drawModeActive = false
                                scope.launch {
                                    embeddedLinks = engine.getLinks(file, password)
                                    showLinksSheet = true
                                }
                            }
                        }

                        EditorTab.COMMENT -> {
                            ToolActionChip(
                                label = if (drawModeActive) "Pen: ON" else "Draw Pen",
                                icon = Icons.Default.Draw,
                                color = if (drawModeActive) AcrobatRed else ToolCommentBlue
                            ) {
                                drawModeActive = !drawModeActive
                                if (drawModeActive) selectedOverlayId = null
                            }
                            ToolActionChip("Highlight", Icons.Default.Create, Color(0xFFFBC02D)) {
                                drawModeActive = false
                                pushUndoCheckpoint()
                                val item = OverlayItem.HighlightOverlay(pageIndex = currentPage)
                                overlays.add(item)
                                selectedOverlayId = item.id
                            }
                            ToolActionChip("Sticky Note", Icons.Default.Comment, ToolCommentBlue) {
                                drawModeActive = false
                                showStickyNoteDialog = true
                            }
                            ToolActionChip("Rectangle", Icons.Default.GridView, ToolCommentBlue) {
                                drawModeActive = false
                                pushUndoCheckpoint()
                                val item = OverlayItem.ShapeOverlay(pageIndex = currentPage, shapeKind = ShapeKind.RECTANGLE)
                                overlays.add(item)
                                selectedOverlayId = item.id
                            }
                            ToolActionChip("Line", Icons.Default.Create, ToolCommentBlue) {
                                drawModeActive = false
                                pushUndoCheckpoint()
                                val item = OverlayItem.ShapeOverlay(pageIndex = currentPage, shapeKind = ShapeKind.LINE)
                                overlays.add(item)
                                selectedOverlayId = item.id
                            }
                        }

                        EditorTab.SIGN -> {
                            ToolActionChip("Signature", Icons.Default.Gesture, ToolSignPurple) {
                                drawModeActive = false
                                showSignatureDialog = true
                            }
                            ToolActionChip("Checkmark ✓", Icons.Default.Check, ToolCompressGreen) {
                                drawModeActive = false
                                pushUndoCheckpoint()
                                val item = OverlayItem.TextOverlay(
                                    pageIndex = currentPage,
                                    xNorm = 0.28f,
                                    yNorm = 0.42f,
                                    wNorm = 0.12f,
                                    hNorm = 0.06f,
                                    text = "✓",
                                    fontSizeSp = 24f,
                                    isBold = true,
                                    colorArgb = 0xFF2E7D32
                                )
                                overlays.add(item)
                                selectedOverlayId = item.id
                            }
                            ToolActionChip("Today's Date", Icons.Default.DateRange, ToolSignPurple) {
                                drawModeActive = false
                                pushUndoCheckpoint()
                                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                val item = OverlayItem.TextOverlay(
                                    pageIndex = currentPage,
                                    xNorm = 0.56f,
                                    yNorm = 0.78f,
                                    wNorm = 0.30f,
                                    hNorm = 0.05f,
                                    text = today,
                                    fontSizeSp = 15f
                                )
                                overlays.add(item)
                                selectedOverlayId = item.id
                            }
                            ToolActionChip("Form Text", Icons.Default.TextFields, ToolSignPurple) {
                                drawModeActive = false
                                showAddTextDialog = true
                            }
                        }

                        EditorTab.ORGANIZE -> {
                            ToolActionChip("+ Blank Page", Icons.Default.NoteAdd, ToolOrganizeTeal) {
                                scope.launch {
                                    engine.organizeAddBlankPage(file, currentPage, password)
                                    reloadNonce++
                                    currentPage++
                                    Toast.makeText(context, "Inserted blank page", Toast.LENGTH_SHORT).show()
                                }
                            }
                            ToolActionChip("Merge PDF", Icons.Default.MergeType, ToolOrganizeTeal) {
                                mergePdfLauncher.launch(arrayOf("application/pdf"))
                            }
                            ToolActionChip("Rotate +90°", Icons.Default.RotateRight, ToolOrganizeTeal) {
                                scope.launch {
                                    engine.organizeRotatePage(file, currentPage, 90, password)
                                    reloadNonce++
                                }
                            }
                            ToolActionChip("Move Up", Icons.Default.ArrowUpward, ToolOrganizeTeal) {
                                if (currentPage > 0) {
                                    scope.launch {
                                        engine.organizeMovePage(file, currentPage, currentPage - 1, password)
                                        currentPage--
                                        reloadNonce++
                                    }
                                }
                            }
                            ToolActionChip("Move Down", Icons.Default.ArrowDownward, ToolOrganizeTeal) {
                                if (currentPage < pageCount - 1) {
                                    scope.launch {
                                        engine.organizeMovePage(file, currentPage, currentPage + 1, password)
                                        currentPage++
                                        reloadNonce++
                                    }
                                }
                            }
                            ToolActionChip("Delete Page", Icons.Default.Delete, AcrobatRed) {
                                if (pageCount > 1) {
                                    scope.launch {
                                        engine.organizeDeletePage(file, currentPage, password)
                                        reloadNonce++
                                        Toast.makeText(context, "Deleted page", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Cannot delete only page", Toast.LENGTH_SHORT).show()
                                }
                            }
                            ToolActionChip("Split Range", Icons.Default.CallSplit, ToolOrganizeTeal) {
                                showSplitDialog = true
                            }
                            ToolActionChip("Crop Margins", Icons.Default.Crop, ToolOrganizeTeal) {
                                showCropDialog = true
                            }
                            ToolActionChip("Header/Footer", Icons.Default.Title, ToolOrganizeTeal) {
                                showHeaderFooterDialog = true
                            }
                        }
                    }
                }
                }

                HorizontalDivider()

                // 4 Acrobat Mode Tabs
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    EditorTab.entries.forEach { tab ->
                        val icon = when (tab) {
                            EditorTab.EDIT -> Icons.Default.Edit
                            EditorTab.COMMENT -> Icons.Default.Comment
                            EditorTab.SIGN -> Icons.Default.Gesture
                            EditorTab.ORGANIZE -> Icons.Default.GridView
                        }
                        NavigationBarItem(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            icon = { Icon(icon, contentDescription = tab.label, tint = tab.color) },
                            label = { Text(tab.label, maxLines = 1) }
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
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Freehand Pen Controls Bar when Draw Mode is active
            if (drawModeActive) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        RichTextColors.take(5).forEach { (_, argb) ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb))
                                    .border(
                                        width = if (penColorArgb == argb) 2.5.dp else 1.dp,
                                        color = if (penColorArgb == argb) AcrobatRed else Color.Gray,
                                        shape = CircleShape
                                    )
                                    .clickable { penColorArgb = argb }
                            )
                        }
                        Slider(
                            value = penWidthNorm,
                            onValueChange = { penWidthNorm = it },
                            valueRange = 0.002f..0.016f,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { drawModeActive = false }) {
                            Text("Done")
                        }
                    }
                }
            }

            // Live Interactive Page Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val bmp = pageBitmap
                val aspect = if (bmp != null && bmp.height > 0) {
                    bmp.width.toFloat() / bmp.height.toFloat()
                } else 0.707f

                BoxWithConstraints(
                    modifier = Modifier
                        .aspectRatio(aspect.coerceIn(0.4f, 1.85f))
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White)
                        .clickable { selectedOverlayId = null }
                ) {
                    val canvasW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                    val canvasH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                    val density = LocalDensity.current

                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Editable PDF Page ${currentPage + 1}",
                            contentScale = ContentScale.FillBounds,
                            colorFilter = if (nightInvert) nightInvertFilter else null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Committed + Active Freehand Strokes Layer
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (drawModeActive) {
                                    Modifier.pointerInput(currentPage, penColorArgb, penWidthNorm) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                activeStrokePoints = listOf(
                                                    NormPoint(
                                                        (offset.x / size.width).coerceIn(0f, 1f),
                                                        (offset.y / size.height).coerceIn(0f, 1f)
                                                    )
                                                )
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                activeStrokePoints = activeStrokePoints + NormPoint(
                                                    (change.position.x / size.width).coerceIn(0f, 1f),
                                                    (change.position.y / size.height).coerceIn(0f, 1f)
                                                )
                                            },
                                            onDragEnd = {
                                                if (activeStrokePoints.size >= 2) {
                                                    pushUndoCheckpoint()
                                                    strokes.add(
                                                        FreehandStroke(
                                                            pageIndex = currentPage,
                                                            points = activeStrokePoints,
                                                            colorArgb = penColorArgb,
                                                            strokeWidthNorm = penWidthNorm
                                                        )
                                                    )
                                                }
                                                activeStrokePoints = emptyList()
                                            }
                                        )
                                    }
                                } else Modifier
                            )
                    ) {
                        val pageStrokes = strokes.filter { it.pageIndex == currentPage }
                        for (st in pageStrokes) {
                            if (st.points.size < 2) continue
                            val path = Path()
                            path.moveTo(st.points[0].x * size.width, st.points[0].y * size.height)
                            for (pt in st.points.drop(1)) {
                                path.lineTo(pt.x * size.width, pt.y * size.height)
                            }
                            drawPath(
                                path = path,
                                color = Color(st.colorArgb),
                                style = Stroke(
                                    width = (st.strokeWidthNorm * size.width).coerceIn(2f, 36f),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                        if (activeStrokePoints.size >= 2) {
                            val path = Path()
                            path.moveTo(activeStrokePoints[0].x * size.width, activeStrokePoints[0].y * size.height)
                            for (pt in activeStrokePoints.drop(1)) {
                                path.lineTo(pt.x * size.width, pt.y * size.height)
                            }
                            drawPath(
                                path = path,
                                color = Color(penColorArgb),
                                style = Stroke(
                                    width = (penWidthNorm * size.width).coerceIn(2f, 36f),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    // Live Draggable & Corner-Resizable Overlays Layer
                    val pageOverlays = overlays.filter { it.pageIndex == currentPage }
                    for (item in pageOverlays) {
                        val isSelected = item.id == selectedOverlayId
                        val leftPx = (item.xNorm * canvasW).roundToInt()
                        val topPx = (item.yNorm * canvasH).roundToInt()
                        val widthDp = with(density) { (item.wNorm * canvasW).toDp() }.coerceAtLeast(32.dp)
                        val heightDp = with(density) { (item.hNorm * canvasH).toDp() }.coerceAtLeast(24.dp)

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(leftPx, topPx) }
                                .size(widthDp, heightDp)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) AcrobatRed else Color.Gray.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .pointerInput(item.id) {
                                    detectDragGestures(
                                        onDragStart = {
                                            selectedOverlayId = item.id
                                            pushUndoCheckpoint()
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val idx = overlays.indexOfFirst { it.id == item.id }
                                            if (idx >= 0) {
                                                val cur = overlays[idx]
                                                val nx = (cur.xNorm + dragAmount.x / canvasW)
                                                    .coerceIn(0f, (1f - cur.wNorm).coerceAtLeast(0f))
                                                val ny = (cur.yNorm + dragAmount.y / canvasH)
                                                    .coerceIn(0f, (1f - cur.hNorm).coerceAtLeast(0f))
                                                overlays[idx] = cur.withBounds(nx, ny, cur.wNorm, cur.hNorm)
                                            }
                                        }
                                    )
                                }
                                .clickable { selectedOverlayId = item.id }
                        ) {
                            // Overlay Content Renderer
                            when (item) {
                                is OverlayItem.TextOverlay -> {
                                    val bg = item.highlightColorArgb?.let { Color(it).copy(alpha = 0.42f) }
                                        ?: Color.Transparent
                                    val ff = when (item.fontFamily) {
                                        PdfFontFamily.SANS -> FontFamily.SansSerif
                                        PdfFontFamily.SERIF -> FontFamily.Serif
                                        PdfFontFamily.MONO -> FontFamily.Monospace
                                    }
                                    val dec = when {
                                        item.isUnderline && item.isStrikethrough ->
                                            TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
                                        item.isUnderline -> TextDecoration.Underline
                                        item.isStrikethrough -> TextDecoration.LineThrough
                                        else -> TextDecoration.None
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(bg)
                                            .padding(3.dp)
                                    ) {
                                        Text(
                                            text = item.text,
                                            color = Color(item.colorArgb),
                                            style = TextStyle(
                                                fontSize = item.fontSizeSp.sp,
                                                fontFamily = ff,
                                                fontWeight = if (item.isBold) FontWeight.Bold else FontWeight.Normal,
                                                fontStyle = if (item.isItalic) FontStyle.Italic else FontStyle.Normal,
                                                textDecoration = dec
                                            )
                                        )
                                    }
                                }

                                is OverlayItem.ImageOverlay -> {
                                    val ovBmp = remember(item.imagePath) {
                                        BitmapFactory.decodeFile(item.imagePath)?.asImageBitmap()
                                    }
                                    if (ovBmp != null) {
                                        Image(
                                            bitmap = ovBmp,
                                            contentDescription = "Added Image",
                                            contentScale = ContentScale.FillBounds,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                                is OverlayItem.HighlightOverlay -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(item.colorArgb).copy(alpha = item.opacity))
                                    )
                                }

                                is OverlayItem.WhiteoutOverlay -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.White)
                                    )
                                }

                                is OverlayItem.RedactOverlay -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(item.label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                is OverlayItem.StickyNoteOverlay -> {
                                    Surface(
                                        color = Color(item.colorArgb),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Comment, contentDescription = item.noteText, tint = Color.Black)
                                        }
                                    }
                                }

                                is OverlayItem.ShapeOverlay -> {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val c = Color(item.colorArgb)
                                        if (item.shapeKind == ShapeKind.RECTANGLE) {
                                            drawRect(color = c, style = Stroke(width = item.strokeWidthPt * 2f))
                                        } else {
                                            drawLine(
                                                color = c,
                                                start = Offset(0f, size.height),
                                                end = Offset(size.width, 0f),
                                                strokeWidth = item.strokeWidthPt * 2f
                                            )
                                        }
                                    }
                                }

                                is OverlayItem.SignatureOverlay -> {
                                    val sigPreview = remember(item) {
                                        engine.renderSignatureBitmap(item, 600, 220).asImageBitmap()
                                    }
                                    Image(
                                        bitmap = sigPreview,
                                        contentDescription = "Signature Overlay",
                                        contentScale = ContentScale.FillBounds,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            // Interactive Selection Controls: Delete Badge + Re-Edit Text Badge + Corner Resize Handle
                            if (isSelected) {
                                // Top-Left Delete Badge
                                Surface(
                                    color = AcrobatRed,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .align(Alignment.TopStart)
                                        .clickable {
                                            pushUndoCheckpoint()
                                            overlays.removeAll { it.id == item.id }
                                            selectedOverlayId = null
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                }

                                // Top-Right Re-Edit Badge for Text Overlays (100% re-editable until Save)
                                if (item is OverlayItem.TextOverlay) {
                                    Surface(
                                        color = ToolCommentBlue,
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .align(Alignment.TopEnd)
                                            .clickable { editingTextItem = item }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Text", tint = Color.White, modifier = Modifier.size(13.dp))
                                        }
                                    }
                                }

                                // Bottom-Right Draggable Corner Resize Handle
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .align(Alignment.BottomEnd)
                                        .clip(CircleShape)
                                        .background(ToolCompressGreen)
                                        .border(1.5.dp, Color.White, CircleShape)
                                        .pointerInput(item.id) {
                                            detectDragGestures(
                                                onDragStart = { pushUndoCheckpoint() },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    val idx = overlays.indexOfFirst { it.id == item.id }
                                                    if (idx >= 0) {
                                                        val cur = overlays[idx]
                                                        val nw = (cur.wNorm + dragAmount.x / canvasW)
                                                            .coerceIn(0.06f, (1f - cur.xNorm).coerceAtLeast(0.06f))
                                                        val nh = (cur.hNorm + dragAmount.y / canvasH)
                                                            .coerceIn(0.03f, (1f - cur.yNorm).coerceAtLeast(0.03f))
                                                        overlays[idx] = cur.withBounds(cur.xNorm, cur.yNorm, nw, nh)
                                                    }
                                                }
                                            )
                                        }
                                )
                            }
                        }
                    }
                }
            }

            // Page Switcher Strip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                OutlinedButton(
                    onClick = { currentPage = (currentPage - 1).coerceAtLeast(0) },
                    enabled = currentPage > 0,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    Text("Prev")
                }
                Text(
                    "Page ${currentPage + 1} of $pageCount",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = { currentPage = (currentPage + 1).coerceAtMost(pageCount - 1) },
                    enabled = currentPage < pageCount - 1,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    Text("Next")
                }
            }
        }
    }

    // === DIALOGS & SHEETS ===

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard Unsaved Edits?", fontWeight = FontWeight.Bold) },
            text = { Text("You have unsaved overlays or ink strokes on this PDF.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AcrobatRed)
                ) {
                    Text("Discard", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Keep Editing") }
            }
        )
    }

    if (showAddTextDialog) {
        RichTextEditorDialog(
            initial = null,
            pageIndex = currentPage,
            onConfirm = { newText ->
                showAddTextDialog = false
                pushUndoCheckpoint()
                overlays.add(newText)
                selectedOverlayId = newText.id
            },
            onDismiss = { showAddTextDialog = false }
        )
    }

    editingTextItem?.let { existing ->
        RichTextEditorDialog(
            initial = existing,
            pageIndex = currentPage,
            onConfirm = { updated ->
                editingTextItem = null
                val idx = overlays.indexOfFirst { it.id == existing.id }
                if (idx >= 0) {
                    pushUndoCheckpoint()
                    overlays[idx] = updated
                }
            },
            onDismiss = { editingTextItem = null }
        )
    }

    if (showStickyNoteDialog) {
        var noteText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showStickyNoteDialog = false },
            title = { Text("Add Sticky Note", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Note Content") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showStickyNoteDialog = false
                        pushUndoCheckpoint()
                        val item = OverlayItem.StickyNoteOverlay(pageIndex = currentPage, noteText = noteText)
                        overlays.add(item)
                        selectedOverlayId = item.id
                    },
                    enabled = noteText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = ToolCommentBlue)
                ) {
                    Text("Place Note", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStickyNoteDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showSignatureDialog) {
        SignaturePadDialog(
            pageIndex = currentPage,
            onConfirm = { sig ->
                showSignatureDialog = false
                pushUndoCheckpoint()
                overlays.add(sig)
                selectedOverlayId = sig.id
            },
            onDismiss = { showSignatureDialog = false }
        )
    }

    if (showSplitDialog) {
        var rangeSpec by remember { mutableStateOf("1-$pageCount") }
        var outTitle by remember { mutableStateOf("${file.nameWithoutExtension}_Split") }
        AlertDialog(
            onDismissRequest = { showSplitDialog = false },
            title = { Text("Split / Extract Page Range", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = rangeSpec,
                        onValueChange = { rangeSpec = it },
                        label = { Text("Pages (e.g. 1-3, 5)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = outTitle,
                        onValueChange = { outTitle = it },
                        label = { Text("New PDF Filename") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSplitDialog = false
                        scope.launch {
                            runCatching { engine.organizeSplitRange(file, rangeSpec, outTitle, password) }
                                .onSuccess { created ->
                                    Toast.makeText(context, "Saved ${created.name} to Recent Files", Toast.LENGTH_SHORT).show()
                                }
                                .onFailure {
                                    Toast.makeText(context, "Split failed: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ToolOrganizeTeal)
                ) {
                    Text("Extract PDF", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSplitDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showCropDialog) {
        var leftPct by remember { mutableFloatStateOf(5f) }
        var topPct by remember { mutableFloatStateOf(5f) }
        var rightPct by remember { mutableFloatStateOf(5f) }
        var bottomPct by remember { mutableFloatStateOf(5f) }
        var applyAll by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCropDialog = false },
            title = { Text("Crop PDF Page Margins", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Left Margin: ${leftPct.roundToInt()}%")
                    Slider(value = leftPct, onValueChange = { leftPct = it }, valueRange = 0f..30f)
                    Text("Top Margin: ${topPct.roundToInt()}%")
                    Slider(value = topPct, onValueChange = { topPct = it }, valueRange = 0f..30f)
                    Text("Right Margin: ${rightPct.roundToInt()}%")
                    Slider(value = rightPct, onValueChange = { rightPct = it }, valueRange = 0f..30f)
                    Text("Bottom Margin: ${bottomPct.roundToInt()}%")
                    Slider(value = bottomPct, onValueChange = { bottomPct = it }, valueRange = 0f..30f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Apply to All Pages")
                        Switch(checked = applyAll, onCheckedChange = { applyAll = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCropDialog = false
                        scope.launch {
                            engine.organizeCropMargins(file, currentPage, leftPct, topPct, rightPct, bottomPct, applyAll, password)
                            reloadNonce++
                            Toast.makeText(context, "Page margins cropped", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ToolOrganizeTeal)
                ) {
                    Text("Crop", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCropDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showHeaderFooterDialog) {
        var headerText by remember { mutableStateOf("") }
        var footerText by remember { mutableStateOf("") }
        var autoPageNums by remember { mutableStateOf(true) }
        var fontSize by remember { mutableFloatStateOf(10f) }

        AlertDialog(
            onDismissRequest = { showHeaderFooterDialog = false },
            title = { Text("Header, Footer & Page Numbers", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = headerText, onValueChange = { headerText = it }, label = { Text("Header Text (optional)") })
                    OutlinedTextField(value = footerText, onValueChange = { footerText = it }, label = { Text("Footer Text (optional)") })
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto Page Numbers (Page X of Y)")
                        Switch(checked = autoPageNums, onCheckedChange = { autoPageNums = it })
                    }
                    Text("Font Size: ${fontSize.roundToInt()} pt")
                    Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 8f..18f)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showHeaderFooterDialog = false
                        scope.launch {
                            engine.organizeAddHeaderFooter(file, headerText, footerText, autoPageNums, fontSize, password)
                            reloadNonce++
                            Toast.makeText(context, "Added Header/Footer", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ToolOrganizeTeal)
                ) {
                    Text("Apply", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showHeaderFooterDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showLinksSheet) {
        LinksManagerSheet(
            links = embeddedLinks,
            currentPage = currentPage,
            onAddLink = { pIdx, uri ->
                scope.launch {
                    engine.addLink(file, pIdx, 0.15f, 0.85f, 0.45f, 0.05f, uri, password)
                    embeddedLinks = engine.getLinks(file, password)
                    Toast.makeText(context, "Hyperlink added", Toast.LENGTH_SHORT).show()
                }
            },
            onRemoveLink = { link ->
                scope.launch {
                    engine.removeLink(file, link.pageIndex, link.annotationIndex, password)
                    embeddedLinks = engine.getLinks(file, password)
                }
            },
            onRemoveAllLinks = {
                scope.launch {
                    engine.removeAllLinks(file, password)
                    embeddedLinks = emptyList()
                    Toast.makeText(context, "All hyperlinks removed", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showLinksSheet = false }
        )
    }

    if (showWatermarkDialog) {
        WatermarkDialog(
            currentPage = currentPage,
            onApply = { text, size, opacity, angle, colorArgb, allPages ->
                showWatermarkDialog = false
                scope.launch {
                    engine.applyWatermark(file, text, size, opacity, angle, colorArgb, allPages, currentPage, password)
                    reloadNonce++
                    Toast.makeText(context, "Watermark applied", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showWatermarkDialog = false }
        )
    }

    if (showPasswordDialog) {
        PasswordProtectionDialog(
            isCurrentlyEncrypted = !password.isNullOrEmpty(),
            onSetPassword = { newPwd ->
                showPasswordDialog = false
                scope.launch {
                    engine.setPasswordProtection(file, newPwd, password)
                    password = newPwd
                    Toast.makeText(context, "128-bit password applied", Toast.LENGTH_SHORT).show()
                }
            },
            onRemovePassword = { curPwd ->
                showPasswordDialog = false
                scope.launch {
                    runCatching { engine.removePasswordProtection(file, curPwd) }
                        .onSuccess {
                            password = null
                            reloadNonce++
                            Toast.makeText(context, "Password removed", Toast.LENGTH_SHORT).show()
                        }
                        .onFailure {
                            Toast.makeText(context, "Incorrect password", Toast.LENGTH_SHORT).show()
                        }
                }
            },
            onDismiss = { showPasswordDialog = false }
        )
    }

    if (showPropertiesDialog) {
        DocumentPropertiesDialog(
            initialProps = docProperties,
            onSave = { title, author, subject, keywords ->
                showPropertiesDialog = false
                scope.launch {
                    engine.updateDocumentProperties(file, title, author, subject, keywords, password)
                    Toast.makeText(context, "Document properties updated", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showPropertiesDialog = false }
        )
    }

    if (showCompressSheet) {
        CompressPdfSheet(
            file = file,
            lastResult = lastCompressionResult,
            isWorking = isWorking,
            onRunCompress = { config, replace ->
                isWorking = true
                scope.launch {
                    runCatching { engine.compressPdf(file, config, replace, password) }
                        .onSuccess { res ->
                            isWorking = false
                            lastCompressionResult = res
                            reloadNonce++
                        }
                        .onFailure {
                            isWorking = false
                            Toast.makeText(context, "Compress failed: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            },
            onDismiss = { showCompressSheet = false }
        )
    }

    if (showExportSheet) {
        ExportPdfSheet(
            totalPages = pageCount,
            currentPage = currentPage,
            isWorking = isWorking,
            onExport = { rangeMode, customRange, fmt, quality, dpi, zip, saveSaf ->
                val isMultiImageFolder = saveSaf && !zip &&
                    (fmt == ExportFormat.PNG || fmt == ExportFormat.JPEG) &&
                    (rangeMode != PageRangeMode.CURRENT && pageCount > 1)
                if (isMultiImageFolder) {
                    showExportSheet = false
                    pendingFolderExport = PendingFolderExport(rangeMode, customRange, fmt, quality, dpi)
                    folderExportLauncher.launch(null)
                } else {
                    isWorking = true
                    scope.launch {
                        runCatching {
                            engine.exportPdf(file, rangeMode, customRange, currentPage, fmt, quality, dpi, zip, password)
                        }.onSuccess { exportedFile ->
                            isWorking = false
                            showExportSheet = false
                            if (saveSaf) {
                                pendingExportShareFile = exportedFile
                                exportSafLauncher.launch(exportedFile.name)
                            } else {
                                ShareUtils.shareFile(context, exportedFile, fmt.mimeType)
                            }
                        }.onFailure {
                            isWorking = false
                            Toast.makeText(context, "Export failed: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onDismiss = { showExportSheet = false }
        )
    }

    ocrText?.let { recognized ->
        OcrResultDialog(
            pageIndex = currentPage,
            recognizedText = recognized,
            onInsertAsTextOverlay = { text ->
                ocrText = null
                pushUndoCheckpoint()
                val item = OverlayItem.TextOverlay(pageIndex = currentPage, text = text, fontSizeSp = 12f)
                overlays.add(item)
                selectedOverlayId = item.id
            },
            onDismiss = { ocrText = null }
        )
    }
}

@Composable
private fun ToolActionChip(
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}
