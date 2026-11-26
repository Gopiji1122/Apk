package com.aurapdf.editor.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurapdf.editor.data.PdfStorage
import com.aurapdf.editor.engine.DraftOverlay
import com.aurapdf.editor.engine.InkStroke
import com.aurapdf.editor.engine.OverlayKind
import com.aurapdf.editor.engine.PdfEngine
import com.aurapdf.editor.engine.PdfLinkInfo
import com.aurapdf.editor.engine.TextFontStyle
import com.aurapdf.editor.theme.AcrobatColors
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class AcrobatMode(val label: String, val icon: ImageVector, val color: Color) {
    EDIT("Edit PDF", Icons.Default.Edit, AcrobatColors.EditPink),
    COMMENT("Comment", Icons.Default.Comment, AcrobatColors.CommentBlue),
    FILL_SIGN("Fill & Sign", Icons.Default.Create, AcrobatColors.SignPurple),
    ORGANIZE("Organize", Icons.Default.Layers, AcrobatColors.OrganizeTeal)
}

enum class ExtraSheet {
    LINKS, SIGN, WATERMARK, PAGES, SECURITY, OCR, EXPORT, PROPERTIES, SEARCH
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPdfScreen(
    file: File,
    storage: PdfStorage,
    onBack: () -> Unit,
    onOpenNewFile: (File) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Offline Read Aloud (TTS)
    var ttsReady by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    val tts = remember {
        var eng: TextToSpeech? = null
        eng = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                eng?.language = Locale.getDefault()
                ttsReady = true
            }
        }
        eng
    }
    DisposableEffect(Unit) {
        onDispose { tts.stop(); tts.shutdown() }
    }

    var pageCount by remember { mutableIntStateOf(1) }
    var currentPage by remember { mutableIntStateOf(0) }
    var reloadTrigger by remember { mutableIntStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var nightInvert by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    var activeMode by remember { mutableStateOf(AcrobatMode.EDIT) }
    var drafts by remember { mutableStateOf(listOf<DraftOverlay>()) }
    var inkStrokes by remember { mutableStateOf(listOf<InkStroke>()) }
    var isDrawMode by remember { mutableStateOf(false) }
    var drawColor by remember { mutableLongStateOf(0xFFD32F2FL) }
    var currentInkPts by remember { mutableStateOf(listOf<Pair<Float, Float>>()) }

    var selectedDraftId by remember { mutableStateOf<Long?>(null) }
    var editingTextDraft by remember { mutableStateOf<DraftOverlay?>(null) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var activeSheet by remember { mutableStateOf<ExtraSheet?>(null) }

    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }
    val undoStack = remember { mutableStateListOf<File>() }
    val scrollState = rememberScrollState()

    val hasUnsaved = drafts.isNotEmpty() || inkStrokes.isNotEmpty()

    BackHandler(enabled = activeSheet != null || editingTextDraft != null || showOverflowMenu || isDrawMode) {
        when {
            showOverflowMenu -> showOverflowMenu = false
            editingTextDraft != null -> editingTextDraft = null
            activeSheet != null -> activeSheet = null
            isDrawMode -> isDrawMode = false
        }
    }

    LaunchedEffect(file, currentPage, reloadTrigger) {
        withContext(Dispatchers.IO) {
            try {
                val count = PdfEngine.getPageCount(file).coerceAtLeast(1)
                pageCount = count
                if (currentPage >= count) currentPage = count - 1
                pageBitmap = PdfEngine.renderPageBitmap(file, currentPage, 1150)
            } catch (e: Exception) {
                statusBanner = "PDF load error: ${e.message}"
            }
        }
    }

    fun applyPdfMutation(msg: String, block: () -> Unit) {
        scope.launch {
            isBusy = true
            try {
                withContext(Dispatchers.IO) {
                    val snap = storage.createUndoSnapshot(file)
                    undoStack.add(snap)
                    if (undoStack.size > 12) undoStack.removeAt(0).delete()
                    block()
                }
                reloadTrigger++
                statusBanner = msg
            } catch (e: Exception) {
                statusBanner = "Error: ${e.message}"
            } finally {
                isBusy = false
            }
        }
    }

    fun saveAllDrafts(afterSave: (() -> Unit)? = null) {
        if (!hasUnsaved) {
            afterSave?.invoke()
            return
        }
        val dCommit = drafts
        val iCommit = inkStrokes
        drafts = emptyList()
        inkStrokes = emptyList()
        selectedDraftId = null
        isDrawMode = false
        scope.launch {
            isBusy = true
            try {
                withContext(Dispatchers.IO) {
                    val snap = storage.createUndoSnapshot(file)
                    undoStack.add(snap)
                    if (undoStack.size > 12) undoStack.removeAt(0).delete()
                    PdfEngine.commitDraftsAndInk(file, currentPage, dCommit, iCommit)
                }
                reloadTrigger++
                statusBanner = "Saved edits to Page ${currentPage + 1}"
                afterSave?.invoke()
            } catch (e: Exception) {
                statusBanner = "Save error: ${e.message}"
            } finally {
                isBusy = false
            }
        }
    }

    val galleryImgPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                }
                if (bmp != null) {
                    val aspect = bmp.height.toFloat() / bmp.width.coerceAtLeast(1)
                    val wN = 0.42f
                    val hN = (wN * aspect * 0.72f).coerceIn(0.10f, 0.55f)
                    val ov = DraftOverlay(kind = OverlayKind.IMAGE, imageBitmap = bmp, xNorm = 0.25f, yNorm = 0.25f, wNorm = wN, hNorm = hN)
                    drafts = drafts + ov
                    selectedDraftId = ov.id
                    statusBanner = "Drag image to move • Drag blue corner to resize • Tap Save"
                }
            }
        }
    }

    val invertMatrix = remember {
        ColorMatrix(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f))
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
                        Text(file.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = if (hasUnsaved) "Page ${currentPage + 1}/$pageCount • Unsaved edits" else "Page ${currentPage + 1} of $pageCount",
                            fontSize = 11.sp,
                            color = if (hasUnsaved) Color(0xFFFFB74D) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (hasUnsaved) {
                        TextButton(onClick = { saveAllDrafts() }) {
                            Icon(Icons.Default.Save, contentDescription = "Save", tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Save", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                        }
                    } else {
                        IconButton(onClick = { activeSheet = ExtraSheet.SEARCH }) {
                            Icon(Icons.Default.Search, contentDescription = "Search in PDF", modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = {
                            if (isSpeaking) {
                                tts.stop(); isSpeaking = false
                            } else if (ttsReady) {
                                scope.launch {
                                    val txt = withContext(Dispatchers.IO) { PdfEngine.extractPageText(file, currentPage) }
                                    if (txt.isNotBlank()) {
                                        isSpeaking = true
                                        tts.speak(txt, TextToSpeech.QUEUE_FLUSH, null, "pdf_tts")
                                    } else {
                                        statusBanner = "No selectable text on this page. Use OCR from the ⋮ menu."
                                    }
                                }
                            }
                        }) {
                            Icon(
                                imageVector = if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                                contentDescription = "Read Aloud",
                                tint = if (isSpeaking) AcrobatColors.ReadCyan else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (undoStack.isNotEmpty() && !hasUnsaved) {
                        IconButton(onClick = {
                            scope.launch {
                                val snap = undoStack.removeAt(undoStack.lastIndex)
                                withContext(Dispatchers.IO) { snap.copyTo(file, overwrite = true); snap.delete() }
                                reloadTrigger++
                                statusBanner = "Undid last saved edit"
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", modifier = Modifier.size(20.dp))
                        }
                    }

                    // UPPER THREE-DOT MENU WITH ALL ACROBAT PRO TOOLS
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "All Acrobat Options")
                        }
                        DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Links (Add / Remove)") },
                                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = AcrobatColors.CommentBlue) },
                                onClick = { showOverflowMenu = false; activeSheet = ExtraSheet.LINKS }
                            )
                            DropdownMenuItem(
                                text = { Text("Custom Watermark") },
                                leadingIcon = { Icon(Icons.Default.WaterDrop, contentDescription = null, tint = AcrobatColors.ReadCyan) },
                                onClick = { showOverflowMenu = false; activeSheet = ExtraSheet.WATERMARK }
                            )
                            DropdownMenuItem(
                                text = { Text("Password Lock / Unlock") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = AcrobatColors.ProtectRed) },
                                onClick = { showOverflowMenu = false; activeSheet = ExtraSheet.SECURITY }
                            )
                            DropdownMenuItem(
                                text = { Text("Offline OCR (Scan Text)") },
                                leadingIcon = { Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = AcrobatColors.OrganizeTeal) },
                                onClick = { showOverflowMenu = false; activeSheet = ExtraSheet.OCR }
                            )
                            DropdownMenuItem(
                                text = { Text("Compress / Export PNG & TXT") },
                                leadingIcon = { Icon(Icons.Default.Compress, contentDescription = null, tint = AcrobatColors.CompressGreen) },
                                onClick = { showOverflowMenu = false; activeSheet = ExtraSheet.EXPORT }
                            )
                            DropdownMenuItem(
                                text = { Text("Document Properties") },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = AcrobatColors.ExportIndigo) },
                                onClick = { showOverflowMenu = false; activeSheet = ExtraSheet.PROPERTIES }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(if (nightInvert) "Disable Night Mode" else "Night Invert Reading") },
                                leadingIcon = { Icon(Icons.Default.DarkMode, contentDescription = null) },
                                onClick = { showOverflowMenu = false; nightInvert = !nightInvert }
                            )
                            DropdownMenuItem(
                                text = { Text("Share PDF") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = { showOverflowMenu = false; storage.shareFile(file) }
                            )
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
                Column {
                    // Contextual Sub-Toolbar for the selected Acrobat Mode!
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (activeMode) {
                            AcrobatMode.EDIT -> {
                                item {
                                    AcrobatActionPill("Add Text", Icons.Default.TextFields, AcrobatColors.EditPink) {
                                        isDrawMode = false
                                        editingTextDraft = DraftOverlay(kind = OverlayKind.TEXT, xNorm = 0.12f, yNorm = 0.16f)
                                    }
                                }
                                item {
                                    AcrobatActionPill("Add Image", Icons.Default.Image, AcrobatColors.CompressGreen) {
                                        isDrawMode = false
                                        galleryImgPicker.launch(arrayOf("image/jpeg", "image/png", "image/*"))
                                    }
                                }
                                item {
                                    AcrobatActionPill("Whiteout", Icons.Default.CropSquare, Color(0xFF78909C)) {
                                        isDrawMode = false
                                        val ov = DraftOverlay(kind = OverlayKind.WHITEOUT, xNorm = 0.15f, yNorm = 0.25f, wNorm = 0.45f, hNorm = 0.06f)
                                        drafts = drafts + ov; selectedDraftId = ov.id
                                        statusBanner = "Drag Whiteout box over text to erase it, then add new text on top"
                                    }
                                }
                                item {
                                    AcrobatActionPill("Redact", Icons.Default.Lock, AcrobatColors.ProtectRed) {
                                        isDrawMode = false
                                        val ov = DraftOverlay(kind = OverlayKind.REDACT, xNorm = 0.15f, yNorm = 0.35f, wNorm = 0.45f, hNorm = 0.06f)
                                        drafts = drafts + ov; selectedDraftId = ov.id
                                        statusBanner = "Drag black Redaction box & resize corner handle"
                                    }
                                }
                                item {
                                    AcrobatActionPill("Links", Icons.Default.Link, AcrobatColors.CommentBlue) {
                                        isDrawMode = false; activeSheet = ExtraSheet.LINKS
                                    }
                                }
                            }
                            AcrobatMode.COMMENT -> {
                                item {
                                    AcrobatActionPill(
                                        label = if (isDrawMode) "Drawing ON" else "Draw Pen",
                                        icon = Icons.Default.Brush,
                                        color = if (isDrawMode) AcrobatColors.Red else AcrobatColors.CommentBlue
                                    ) {
                                        isDrawMode = !isDrawMode
                                        if (isDrawMode) statusBanner = "Draw directly on the PDF page with your finger. Tap Save when done."
                                    }
                                }
                                if (isDrawMode) {
                                    item {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            listOf(0xFFD32F2FL, 0xFF1976D2L, 0xFF000000L, 0xFF2E7D32L).forEach { c ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(c))
                                                        .border(if (drawColor == c) 2.5.dp else 1.dp, Color.White, CircleShape)
                                                        .clickable { drawColor = c }
                                                )
                                            }
                                            if (inkStrokes.isNotEmpty()) {
                                                TextButton(onClick = { inkStrokes = inkStrokes.dropLast(1) }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                                                    Text("Undo Ink", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                                item {
                                    AcrobatActionPill("Highlight", Icons.Default.Highlight, Color(0xFFF9A825)) {
                                        isDrawMode = false
                                        val ov = DraftOverlay(kind = OverlayKind.HIGHLIGHT, colorArgb = 0xFFFFEB3BL, xNorm = 0.12f, yNorm = 0.25f, wNorm = 0.55f, hNorm = 0.045f)
                                        drafts = drafts + ov; selectedDraftId = ov.id
                                    }
                                }
                                item {
                                    AcrobatActionPill("Sticky Note", Icons.Default.StickyNote2, Color(0xFFFFA000)) {
                                        isDrawMode = false
                                        editingTextDraft = DraftOverlay(kind = OverlayKind.STICKY_NOTE, text = "", xNorm = 0.16f, yNorm = 0.22f)
                                    }
                                }
                                item {
                                    AcrobatActionPill("Box Shape", Icons.Default.CropSquare, AcrobatColors.CommentBlue) {
                                        isDrawMode = false
                                        val ov = DraftOverlay(kind = OverlayKind.SHAPE_RECT, colorArgb = 0xFF1976D2L, xNorm = 0.20f, yNorm = 0.30f, wNorm = 0.38f, hNorm = 0.16f)
                                        drafts = drafts + ov; selectedDraftId = ov.id
                                    }
                                }
                                item {
                                    AcrobatActionPill("Line", Icons.Default.Edit, AcrobatColors.ProtectRed) {
                                        isDrawMode = false
                                        val ov = DraftOverlay(kind = OverlayKind.SHAPE_LINE, colorArgb = 0xFFD32F2FL, xNorm = 0.15f, yNorm = 0.45f, wNorm = 0.50f, hNorm = 0.03f)
                                        drafts = drafts + ov; selectedDraftId = ov.id
                                    }
                                }
                            }
                            AcrobatMode.FILL_SIGN -> {
                                item {
                                    AcrobatActionPill("Sign (Hand/Digital)", Icons.Default.Create, AcrobatColors.SignPurple) {
                                        isDrawMode = false; activeSheet = ExtraSheet.SIGN
                                    }
                                }
                                item {
                                    AcrobatActionPill("Check ✓", Icons.Default.Check, AcrobatColors.CompressGreen) {
                                        isDrawMode = false
                                        val ov = DraftOverlay(kind = OverlayKind.TEXT, text = "X", fontSizeSp = 18f, isBold = true, colorArgb = 0xFF111111L, xNorm = 0.25f, yNorm = 0.35f)
                                        drafts = drafts + ov; selectedDraftId = ov.id
                                    }
                                }
                                item {
                                    AcrobatActionPill("Today Date", Icons.Default.TextFields, AcrobatColors.CommentBlue) {
                                        isDrawMode = false
                                        val d = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date())
                                        val ov = DraftOverlay(kind = OverlayKind.TEXT, text = d, fontSizeSp = 13f, isBold = true, colorArgb = 0xFF111111L, xNorm = 0.25f, yNorm = 0.45f)
                                        drafts = drafts + ov; selectedDraftId = ov.id
                                    }
                                }
                                item {
                                    AcrobatActionPill("Fill Text", Icons.Default.Edit, AcrobatColors.EditPink) {
                                        isDrawMode = false
                                        editingTextDraft = DraftOverlay(kind = OverlayKind.TEXT, fontSizeSp = 13f, xNorm = 0.20f, yNorm = 0.30f)
                                    }
                                }
                            }
                            AcrobatMode.ORGANIZE -> {
                                item {
                                    AcrobatActionPill("+ Blank Page", Icons.Default.Add, AcrobatColors.OrganizeTeal) {
                                        saveAllDrafts {
                                            applyPdfMutation("Inserted blank page") { PdfEngine.addBlankPage(file, currentPage) }
                                            currentPage++
                                        }
                                    }
                                }
                                item {
                                    AcrobatActionPill("All Page Tools", Icons.Default.Layers, AcrobatColors.MergeOrange) {
                                        activeSheet = ExtraSheet.PAGES
                                    }
                                }
                                item {
                                    AcrobatActionPill("Watermark", Icons.Default.WaterDrop, AcrobatColors.ReadCyan) {
                                        activeSheet = ExtraSheet.WATERMARK
                                    }
                                }
                                item {
                                    AcrobatActionPill("Compress", Icons.Default.Compress, AcrobatColors.CompressGreen) {
                                        activeSheet = ExtraSheet.EXPORT
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    // Acrobat Bottom Mode Navigation + Page Switcher
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { saveAllDrafts { if (currentPage > 0) currentPage-- } },
                            enabled = currentPage > 0,
                            modifier = Modifier.size(32.dp)
                        ) { Icon(Icons.Default.ChevronLeft, contentDescription = "Prev") }

                        AcrobatMode.entries.forEach { mode ->
                            val selected = activeMode == mode
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        activeMode = mode
                                        if (mode != AcrobatMode.COMMENT) isDrawMode = false
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = mode.icon,
                                    contentDescription = mode.label,
                                    tint = if (selected) mode.color else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = mode.label,
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) mode.color else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = { saveAllDrafts { if (currentPage < pageCount - 1) currentPage++ } },
                            enabled = currentPage < pageCount - 1,
                            modifier = Modifier.size(32.dp)
                        ) { Icon(Icons.Default.ChevronRight, contentDescription = "Next") }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (statusBanner != null) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(statusBanner!!, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Text("OK", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { statusBanner = null })
                    }
                }
            }

            // Interactive PDF Canvas (Scrollable, Freehand Ink Drawing, Draggable/Resizable/Re-Editable Overlays!)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(if (nightInvert) Color.Black else Color(0xFF1A1B20))
                    .then(if (!isDrawMode) Modifier.verticalScroll(scrollState) else Modifier)
                    .padding(10.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                if (pageBitmap != null) {
                    val aspect = pageBitmap!!.width.toFloat() / pageBitmap!!.height.coerceAtLeast(1)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspect.coerceIn(0.45f, 1.8f))
                            .onSizeChanged { canvasSize = it }
                            .clickable { if (!isDrawMode) selectedDraftId = null }
                    ) {
                        Image(
                            bitmap = pageBitmap!!.asImageBitmap(),
                            contentDescription = "PDF Page",
                            contentScale = ContentScale.FillBounds,
                            colorFilter = if (nightInvert) ColorFilter.colorMatrix(invertMatrix) else null,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Freehand Ink Drawing Layer
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (isDrawMode) {
                                        Modifier.pointerInput(drawColor, canvasSize) {
                                            detectDragGestures(
                                                onDragStart = { pos ->
                                                    val nx = (pos.x / canvasSize.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                                    val ny = (pos.y / canvasSize.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                                    currentInkPts = listOf(nx to ny)
                                                },
                                                onDrag = { change, _ ->
                                                    change.consume()
                                                    val nx = (change.position.x / canvasSize.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                                    val ny = (change.position.y / canvasSize.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                                    currentInkPts = currentInkPts + (nx to ny)
                                                },
                                                onDragEnd = {
                                                    if (currentInkPts.size >= 2) {
                                                        inkStrokes = inkStrokes + InkStroke(currentInkPts, drawColor)
                                                    }
                                                    currentInkPts = emptyList()
                                                }
                                            )
                                        }
                                    } else Modifier
                                )
                        ) {
                            val allStrokes = if (currentInkPts.size >= 2) inkStrokes + InkStroke(currentInkPts, drawColor) else inkStrokes
                            allStrokes.forEach { st ->
                                for (i in 0 until st.points.size - 1) {
                                    drawLine(
                                        color = Color(st.colorArgb),
                                        start = Offset(st.points[i].first * size.width, st.points[i].second * size.height),
                                        end = Offset(st.points[i + 1].first * size.width, st.points[i + 1].second * size.height),
                                        strokeWidth = 6f,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }

                        // Interactive Draft Overlays (Touch Drag Move + Corner Handle Resize + Tap Edit)
                        drafts.forEach { ov ->
                            val isSelected = selectedDraftId == ov.id && !isDrawMode
                            val pxX = (ov.xNorm * canvasSize.width).roundToInt()
                            val pxY = (ov.yNorm * canvasSize.height).roundToInt()
                            val wDp = with(density) { (ov.wNorm * canvasSize.width).toDp() }
                            val hDp = with(density) { (ov.hNorm * canvasSize.height).toDp() }
                            val isBoxType = ov.kind != OverlayKind.TEXT && ov.kind != OverlayKind.STICKY_NOTE

                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(pxX, pxY) }
                                    .then(if (isBoxType) Modifier.size(wDp.coerceAtLeast(36.dp), hDp.coerceAtLeast(16.dp)) else Modifier.wrapContentSize())
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.7.dp,
                                        color = if (isSelected) Color(0xFF2196F3) else Color(0x662196F3),
                                        shape = RoundedCornerShape(3.dp)
                                    )
                                    .then(
                                        if (!isDrawMode) {
                                            Modifier.pointerInput(ov.id, canvasSize) {
                                                detectDragGestures(
                                                    onDragStart = { selectedDraftId = ov.id },
                                                    onDrag = { change, drag ->
                                                        change.consume()
                                                        val dxN = drag.x / canvasSize.width.coerceAtLeast(1)
                                                        val dyN = drag.y / canvasSize.height.coerceAtLeast(1)
                                                        drafts = drafts.map { item ->
                                                            if (item.id == ov.id) {
                                                                item.copy(
                                                                    xNorm = (item.xNorm + dxN).coerceIn(0.01f, 0.92f),
                                                                    yNorm = (item.yNorm + dyN).coerceIn(0.01f, 0.92f)
                                                                )
                                                            } else item
                                                        }
                                                    }
                                                )
                                            }.clickable { selectedDraftId = ov.id }
                                        } else Modifier
                                    )
                            ) {
                                when (ov.kind) {
                                    OverlayKind.IMAGE -> if (ov.imageBitmap != null) {
                                        Image(bitmap = ov.imageBitmap.asImageBitmap(), contentDescription = "Img", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                                    }
                                    OverlayKind.WHITEOUT -> Box(Modifier.fillMaxSize().background(Color.White))
                                    OverlayKind.REDACT -> Box(Modifier.fillMaxSize().background(Color.Black))
                                    OverlayKind.HIGHLIGHT -> Box(Modifier.fillMaxSize().background(Color(ov.colorArgb).copy(alpha = 0.40f)))
                                    OverlayKind.SHAPE_RECT -> Box(Modifier.fillMaxSize().border(2.dp, Color(ov.colorArgb)))
                                    OverlayKind.SHAPE_LINE -> Box(Modifier.fillMaxWidth().height(3.dp).align(Alignment.Center).background(Color(ov.colorArgb)))
                                    OverlayKind.TEXT, OverlayKind.STICKY_NOTE -> {
                                        val ff = when (ov.fontStyle) {
                                            TextFontStyle.SANS -> FontFamily.SansSerif
                                            TextFontStyle.SERIF -> FontFamily.Serif
                                            TextFontStyle.MONO -> FontFamily.Monospace
                                        }
                                        val deco = when {
                                            ov.isStrikethrough -> TextDecoration.LineThrough
                                            ov.isUnderline || ov.linkUrl.isNotBlank() -> TextDecoration.Underline
                                            else -> TextDecoration.None
                                        }
                                        Text(
                                            text = ov.text,
                                            fontSize = ov.fontSizeSp.sp,
                                            fontFamily = ff,
                                            fontWeight = if (ov.isBold) FontWeight.Bold else FontWeight.Normal,
                                            fontStyle = if (ov.isItalic) FontStyle.Italic else FontStyle.Normal,
                                            textDecoration = deco,
                                            color = if (ov.linkUrl.isNotBlank()) Color(0xFF1976D2) else Color(ov.colorArgb),
                                            modifier = Modifier
                                                .background(if (ov.kind == OverlayKind.STICKY_NOTE || ov.hasBgHighlight) Color(0xFFFFF59D) else Color.Transparent)
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Row(
                                        modifier = Modifier.align(Alignment.TopEnd).offset(y = (-28).dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (ov.kind == OverlayKind.TEXT || ov.kind == OverlayKind.STICKY_NOTE) {
                                            Surface(shape = CircleShape, color = Color(0xFF1976D2), modifier = Modifier.size(24.dp).clickable { editingTextDraft = ov }) {
                                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.White, modifier = Modifier.size(14.dp)) }
                                            }
                                        }
                                        Surface(shape = CircleShape, color = Color(0xFFD32F2F), modifier = Modifier.size(24.dp).clickable { drafts = drafts.filterNot { it.id == ov.id }; selectedDraftId = null }) {
                                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(14.dp)) }
                                        }
                                    }

                                    // Corner Resize Handle
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF2196F3),
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .offset(x = 10.dp, y = 10.dp)
                                            .size(24.dp)
                                            .pointerInput(ov.id, canvasSize) {
                                                detectDragGestures { change, drag ->
                                                    change.consume()
                                                    drafts = drafts.map { item ->
                                                        if (item.id == ov.id) {
                                                            if (isBoxType) {
                                                                val dw = drag.x / canvasSize.width.coerceAtLeast(1)
                                                                val dh = drag.y / canvasSize.height.coerceAtLeast(1)
                                                                item.copy(
                                                                    wNorm = (item.wNorm + dw).coerceIn(0.06f, 0.94f),
                                                                    hNorm = (item.hNorm + dh).coerceIn(0.02f, 0.90f)
                                                                )
                                                            } else {
                                                                val dSp = (drag.x + drag.y) * 0.08f
                                                                item.copy(fontSizeSp = (item.fontSizeSp + dSp).coerceIn(8f, 48f))
                                                            }
                                                        } else item
                                                    }
                                                }
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.OpenInFull, contentDescription = "Resize", tint = Color.White, modifier = Modifier.size(13.dp)) }
                                    }
                                }
                            }
                        }
                    }
                }
                if (isBusy || pageBitmap == null) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    // Re-Editable Rich Text / Sticky Note Dialog
    if (editingTextDraft != null) {
        RichTextEditorDialog(
            initial = editingTextDraft!!,
            onDismiss = { editingTextDraft = null },
            onConfirm = { updated ->
                val exists = drafts.any { it.id == updated.id }
                drafts = if (exists) drafts.map { if (it.id == updated.id) updated else it } else drafts + updated
                selectedDraftId = updated.id
                editingTextDraft = null
            }
        )
    }

    // Extra Acrobat Tool Bottom Sheets
    if (activeSheet != null) {
        ModalBottomSheet(onDismissRequest = { activeSheet = null }, containerColor = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (activeSheet!!) {
                    ExtraSheet.SEARCH -> SearchInPdfSheet(file) { targetPage ->
                        activeSheet = null
                        saveAllDrafts { currentPage = targetPage }
                    }
                    ExtraSheet.LINKS -> AcrobatLinksSheet(
                        file = file,
                        onAdd = { lbl, url ->
                            activeSheet = null
                            val ov = DraftOverlay(kind = OverlayKind.TEXT, text = lbl.ifBlank { url }, linkUrl = url, isBold = true, colorArgb = 0xFF1976D2L, xNorm = 0.12f, yNorm = 0.78f)
                            drafts = drafts + ov; selectedDraftId = ov.id
                            statusBanner = "Drag link to exact spot & tap Save"
                        },
                        onRemove = { pIdx, url ->
                            activeSheet = null
                            applyPdfMutation("Removed link(s)") { PdfEngine.removeLinks(file, pIdx, url) }
                        }
                    )
                    ExtraSheet.SIGN -> AcrobatSignSheet(
                        onDrawn = { bmp ->
                            activeSheet = null
                            val ov = DraftOverlay(kind = OverlayKind.IMAGE, imageBitmap = bmp, xNorm = 0.48f, yNorm = 0.72f, wNorm = 0.36f, hNorm = 0.14f)
                            drafts = drafts + ov; selectedDraftId = ov.id
                            statusBanner = "Drag & resize signature • Tap Save when placed"
                        },
                        onDigital = { name, reason, loc ->
                            activeSheet = null
                            saveAllDrafts {
                                applyPdfMutation("Stamped Digital Signature + SHA-256") {
                                    PdfEngine.stampDigitalSignature(file, currentPage, name, reason, loc, 0.45f, 0.80f)
                                }
                            }
                        }
                    )
                    ExtraSheet.WATERMARK -> AcrobatWatermarkSheet { txt, sz, op, ang, col, all ->
                        activeSheet = null
                        saveAllDrafts { applyPdfMutation("Applied custom watermark") { PdfEngine.addCustomWatermark(file, currentPage, txt, sz, op, ang, col, all) } }
                    }
                    ExtraSheet.PAGES -> AcrobatPagesSheet(
                        currentPage = currentPage, pageCount = pageCount, storage = storage,
                        onAddBlank = { activeSheet = null; saveAllDrafts { applyPdfMutation("Added blank page") { PdfEngine.addBlankPage(file, currentPage) }; currentPage++ } },
                        onRotate = { activeSheet = null; saveAllDrafts { applyPdfMutation("Rotated page +90°") { PdfEngine.rotatePage(file, currentPage, 90, false) } } },
                        onDelete = { activeSheet = null; drafts = emptyList(); applyPdfMutation("Deleted page") { PdfEngine.deletePage(file, currentPage) }; if (currentPage > 0) currentPage-- },
                        onMoveUp = { if (currentPage > 0) { activeSheet = null; val t = currentPage - 1; saveAllDrafts { applyPdfMutation("Moved page up") { PdfEngine.movePage(file, currentPage, t) } }; currentPage = t } },
                        onHeaderFooter = { h, f, nums -> activeSheet = null; saveAllDrafts { applyPdfMutation("Applied Header/Footer/Numbers") { PdfEngine.addHeaderFooterNumbers(file, h, f, nums) } } },
                        onMerge = { extra -> activeSheet = null; saveAllDrafts { applyPdfMutation("Merged PDF pages") { PdfEngine.appendPdfIntoCurrent(file, extra) } } },
                        onSplit = { f1, t1 ->
                            activeSheet = null
                            scope.launch {
                                val out = storage.newOutputFile("${file.nameWithoutExtension}_p${f1}_$t1")
                                withContext(Dispatchers.IO) { PdfEngine.extractRangeToNewFile(file, out, f1, t1) }
                                onOpenNewFile(out)
                            }
                        }
                    )
                    ExtraSheet.SECURITY -> AcrobatSecuritySheet(
                        onLock = { pwd -> activeSheet = null; saveAllDrafts { applyPdfMutation("PDF locked") { PdfEngine.protectPdf(file, pwd) } } },
                        onUnlock = { pwd -> activeSheet = null; saveAllDrafts { applyPdfMutation("PDF unlocked") { PdfEngine.unlockPdf(file, pwd) } } }
                    )
                    ExtraSheet.OCR -> AcrobatOcrSheet(file, currentPage) { txt ->
                        clipboard.setText(AnnotatedString(txt))
                        statusBanner = "Copied OCR text"
                    }
                    ExtraSheet.EXPORT -> AcrobatExportSheet(
                        onCompress = { qual, w ->
                            activeSheet = null
                            scope.launch {
                                isBusy = true
                                val out = storage.newOutputFile("${file.nameWithoutExtension}_compressed")
                                withContext(Dispatchers.IO) { PdfEngine.compressPdf(file, out, qual, w) }
                                isBusy = false
                                onOpenNewFile(out)
                            }
                        },
                        onPng = {
                            activeSheet = null
                            scope.launch {
                                val png = File(context.cacheDir, "${file.nameWithoutExtension}_p${currentPage + 1}.png")
                                withContext(Dispatchers.IO) { PdfEngine.exportPageAsPng(file, currentPage, png) }
                                storage.shareFile(png, "image/png")
                            }
                        },
                        onTxt = {
                            activeSheet = null
                            scope.launch {
                                val txt = withContext(Dispatchers.IO) { PdfEngine.extractPageText(file, currentPage) }
                                val fTxt = File(context.cacheDir, "${file.nameWithoutExtension}_p${currentPage + 1}.txt")
                                withContext(Dispatchers.IO) { fTxt.writeText(txt.ifBlank { "(No selectable text)" }) }
                                storage.shareFile(fTxt, "text/plain")
                            }
                        }
                    )
                    ExtraSheet.PROPERTIES -> AcrobatPropertiesSheet(file) { t, a, s ->
                        activeSheet = null
                        applyPdfMutation("Updated document properties") { PdfEngine.updateMetadata(file, t, a, s) }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun AcrobatActionPill(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun RichTextEditorDialog(initial: DraftOverlay, onDismiss: () -> Unit, onConfirm: (DraftOverlay) -> Unit) {
    var text by remember { mutableStateOf(initial.text) }
    var fontSize by remember { mutableFloatStateOf(initial.fontSizeSp) }
    var fontStyle by remember { mutableStateOf(initial.fontStyle) }
    var bold by remember { mutableStateOf(initial.isBold) }
    var italic by remember { mutableStateOf(initial.isItalic) }
    var underline by remember { mutableStateOf(initial.isUnderline) }
    var strike by remember { mutableStateOf(initial.isStrikethrough) }
    var bgHighlight by remember { mutableStateOf(initial.hasBgHighlight) }
    var colorArgb by remember { mutableLongStateOf(initial.colorArgb) }
    var linkUrl by remember { mutableStateOf(initial.linkUrl) }

    val colors = listOf(0xFF000000L, 0xFFFFFFFFL, 0xFFD32F2FL, 0xFF1976D2L, 0xFF2E7D32L, 0xFFF57C00L, 0xFF7B1FA2L, 0xFF00838FL, 0xFFC2185BL, 0xFF5D4037L)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.kind == OverlayKind.STICKY_NOTE) "Sticky Note Comment" else if (initial.text.isBlank()) "Add Styled Text" else "Re-Edit Text") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Write text...") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
                if (initial.kind != OverlayKind.STICKY_NOTE) {
                    OutlinedTextField(value = linkUrl, onValueChange = { linkUrl = it }, label = { Text("Link Under Text (Optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextFontStyle.entries.forEach { st -> FilterChip(selected = fontStyle == st, onClick = { fontStyle = st }, label = { Text(st.label, fontSize = 11.sp) }) }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChip(selected = bold, onClick = { bold = !bold }, label = { Text("B", fontWeight = FontWeight.Bold) }) }
                    item { FilterChip(selected = italic, onClick = { italic = !italic }, label = { Text("I", fontStyle = FontStyle.Italic) }) }
                    item { FilterChip(selected = underline, onClick = { underline = !underline }, label = { Text("U", textDecoration = TextDecoration.Underline) }) }
                    item { FilterChip(selected = strike, onClick = { strike = !strike }, label = { Text("S", textDecoration = TextDecoration.LineThrough) }) }
                    item { FilterChip(selected = bgHighlight, onClick = { bgHighlight = !bgHighlight }, label = { Text("Highlight", fontSize = 11.sp) }) }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(colors) { c ->
                        val sel = colorArgb == c
                        Box(
                            modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(c)).border(if (sel) 2.5.dp else 1.dp, if (sel) Color(0xFF2196F3) else Color.Gray, CircleShape).clickable { colorArgb = c },
                            contentAlignment = Alignment.Center
                        ) {
                            if (sel) Icon(Icons.Default.Check, contentDescription = null, tint = if (c == 0xFFFFFFFFL) Color.Black else Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Text("Size: ${fontSize.toInt()} pt", fontSize = 12.sp)
                Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 8f..44f)
            }
        },
        confirmButton = {
            Button(enabled = text.isNotBlank(), onClick = {
                onConfirm(initial.copy(text = text, fontSizeSp = fontSize, fontStyle = fontStyle, isBold = bold, isItalic = italic, isUnderline = underline, isStrikethrough = strike, hasBgHighlight = bgHighlight, colorArgb = colorArgb, linkUrl = linkUrl.trim()))
            }) { Text("Place on Page") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SearchInPdfSheet(file: File, onJumpToPage: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<Int>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }

    Text("Search Text Inside PDF", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Keyword...") }, singleLine = true, modifier = Modifier.weight(1f))
        Button(onClick = {
            scope.launch {
                matches = withContext(Dispatchers.IO) { PdfEngine.searchPagesContaining(file, query) }
                searched = true
            }
        }) { Text("Find") }
    }
    if (searched) {
        if (matches.isEmpty()) Text("No matches found for \"$query\".", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(matches) { p -> FilterChip(selected = false, onClick = { onJumpToPage(p) }, label = { Text("Go to Page ${p + 1}") }) }
        }
    }
}

@Composable
private fun AcrobatLinksSheet(file: File, onAdd: (String, String) -> Unit, onRemove: (Int?, String?) -> Unit) {
    var links by remember { mutableStateOf<List<PdfLinkInfo>>(emptyList()) }
    var label by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    LaunchedEffect(file) { links = withContext(Dispatchers.IO) { PdfEngine.listLinks(file) } }

    Text("PDF Hyperlinks", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Link Text") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("URL (https://...)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Button(onClick = { onAdd(label, url.trim()) }, enabled = url.length > 8, modifier = Modifier.fillMaxWidth()) { Text("Place Draggable Link") }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Existing Links (${links.size})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (links.isNotEmpty()) {
            TextButton(onClick = { onRemove(null, null) }) {
                Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Remove All", color = Color(0xFFEF5350))
            }
        }
    }
    LazyColumn(modifier = Modifier.heightIn(max = 130.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(links) { item ->
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.url, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Page ${item.pageIndex + 1}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onRemove(item.pageIndex, item.url) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Remove", tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AcrobatSignSheet(onDrawn: (Bitmap) -> Unit, onDigital: (String, String, String) -> Unit) {
    var mode by remember { mutableIntStateOf(0) }
    var inkColor by remember { mutableIntStateOf(android.graphics.Color.BLACK) }
    var strokes by remember { mutableStateOf(listOf<List<Offset>>()) }
    var curStroke by remember { mutableStateOf(listOf<Offset>()) }
    var signer by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("Approved") }
    var loc by remember { mutableStateOf("India") }

    Text("Fill & Sign (Hand & Digital)", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("Draw Signature") })
        FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Digital Certificate") })
    }
    if (mode == 0) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Black" to android.graphics.Color.BLACK, "Blue" to android.graphics.Color.rgb(21, 101, 192), "Red" to android.graphics.Color.rgb(198, 40, 40)).forEach { (n, c) ->
                FilterChip(selected = inkColor == c, onClick = { inkColor = c }, label = { Text(n, fontSize = 11.sp) })
            }
        }
        Surface(shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth().height(150.dp)) {
            Canvas(
                modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { p -> curStroke = listOf(p) },
                        onDrag = { ch, _ -> curStroke = curStroke + ch.position },
                        onDragEnd = { if (curStroke.isNotEmpty()) { strokes = strokes + listOf(curStroke); curStroke = emptyList() } }
                    )
                }
            ) {
                (strokes + listOf(curStroke)).forEach { pts ->
                    for (i in 0 until pts.size - 1) drawLine(Color(inkColor), pts[i], pts[i + 1], strokeWidth = 6f, cap = StrokeCap.Round)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { strokes = emptyList(); curStroke = emptyList() }, modifier = Modifier.weight(1f)) { Text("Clear") }
            Button(
                enabled = strokes.isNotEmpty(),
                onClick = {
                    val bmp = Bitmap.createBitmap(600, 240, Bitmap.Config.ARGB_8888)
                    val c = Canvas(bmp)
                    val p = Paint().apply { color = inkColor; strokeWidth = 6f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }
                    strokes.forEach { pts ->
                        val path = Path()
                        pts.forEachIndexed { idx, pt ->
                            val sx = (pt.x / 1.5f).coerceIn(0f, 599f)
                            val sy = (pt.y / 1.5f).coerceIn(0f, 239f)
                            if (idx == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                        }
                        c.drawPath(path, p)
                    }
                    onDrawn(bmp)
                },
                modifier = Modifier.weight(1f)
            ) { Text("Place Signature") }
        }
    } else {
        OutlinedTextField(value = signer, onValueChange = { signer = it }, label = { Text("Signer Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = loc, onValueChange = { loc = it }, label = { Text("Location") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(enabled = signer.isNotBlank(), onClick = { onDigital(signer.trim(), reason.trim(), loc.trim()) }, modifier = Modifier.fillMaxWidth()) { Text("Apply Digital Signature + SHA-256") }
    }
}

@Composable
private fun AcrobatWatermarkSheet(onApply: (String, Float, Float, Float, Int, Boolean) -> Unit) {
    var txt by remember { mutableStateOf("CONFIDENTIAL") }
    var sz by remember { mutableFloatStateOf(42f) }
    var op by remember { mutableFloatStateOf(0.28f) }
    var ang by remember { mutableFloatStateOf(45f) }
    var col by remember { mutableIntStateOf(0x888888) }
    var all by remember { mutableStateOf(true) }
    Text("Custom Watermark", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    OutlinedTextField(value = txt, onValueChange = { txt = it }, label = { Text("Watermark Text") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Gray" to 0x888888, "Red" to 0xD32F2F, "Blue" to 0x1976D2, "Black" to 0x111111).forEach { (n, h) ->
            FilterChip(selected = col == h, onClick = { col = h }, label = { Text(n, fontSize = 11.sp) })
        }
    }
    Text("Size:${sz.toInt()} • Opacity:${(op * 100).toInt()}% • Angle:${ang.toInt()}°", fontSize = 12.sp)
    Slider(value = sz, onValueChange = { sz = it }, valueRange = 16f..76f)
    Slider(value = op, onValueChange = { op = it }, valueRange = 0.10f..0.85f)
    Slider(value = ang, onValueChange = { ang = it }, valueRange = 0f..90f)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("All Pages", fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(checked = all, onCheckedChange = { all = it })
    }
    Button(onClick = { onApply(txt, sz, op, ang, col, all) }, modifier = Modifier.fillMaxWidth()) { Text("Apply Watermark") }
}

@Composable
private fun AcrobatPagesSheet(
    currentPage: Int, pageCount: Int, storage: PdfStorage,
    onAddBlank: () -> Unit, onRotate: () -> Unit, onDelete: () -> Unit,
    onMoveUp: () -> Unit, onHeaderFooter: (String, String, Boolean) -> Unit, onMerge: (File) -> Unit, onSplit: (Int, Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    var fP by remember { mutableStateOf("1") }
    var tP by remember { mutableStateOf(pageCount.toString()) }
    var hdr by remember { mutableStateOf("") }
    var ftr by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) scope.launch { onMerge(withContext(Dispatchers.IO) { storage.importPdfFromUri(u) }) }
    }
    Text("Organize Pages (Page ${currentPage + 1}/$pageCount)", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = onAddBlank, modifier = Modifier.weight(1f)) { Text("+ Blank", fontSize = 12.sp) }
        OutlinedButton(onClick = { picker.launch(arrayOf("application/pdf")) }, modifier = Modifier.weight(1f)) { Text("+ Merge PDF", fontSize = 12.sp) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onRotate, modifier = Modifier.weight(1f)) { Text("Rotate +90°", fontSize = 12.sp) }
        OutlinedButton(onClick = onMoveUp, enabled = currentPage > 0, modifier = Modifier.weight(1f)) { Text("Move Up", fontSize = 12.sp) }
        OutlinedButton(onClick = onDelete, enabled = pageCount > 1, modifier = Modifier.weight(1f)) { Text("Delete", color = Color(0xFFEF5350), fontSize = 12.sp) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = hdr, onValueChange = { hdr = it }, label = { Text("Header") }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(value = ftr, onValueChange = { ftr = it }, label = { Text("Footer") }, singleLine = true, modifier = Modifier.weight(1f))
        Button(onClick = { onHeaderFooter(hdr, ftr, true) }) { Text("Apply #", fontSize = 11.sp) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = fP, onValueChange = { fP = it }, label = { Text("From") }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(value = tP, onValueChange = { tP = it }, label = { Text("To") }, singleLine = true, modifier = Modifier.weight(1f))
        Button(onClick = { onSplit(fP.toIntOrNull() ?: 1, tP.toIntOrNull() ?: pageCount) }) { Text("Split") }
    }
}

@Composable
private fun AcrobatSecuritySheet(onLock: (String) -> Unit, onUnlock: (String) -> Unit) {
    var pwd by remember { mutableStateOf("") }
    Text("Password Protect / Unlock", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    OutlinedTextField(value = pwd, onValueChange = { pwd = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = { onLock(pwd) }, enabled = pwd.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Lock PDF") }
        OutlinedButton(onClick = { onUnlock(pwd) }, enabled = pwd.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Unlock PDF") }
    }
}

@Composable
private fun AcrobatOcrSheet(file: File, pageIndex: Int, onCopy: (String) -> Unit) {
    var text by remember { mutableStateOf("Scanning Page ${pageIndex + 1} offline...") }
    LaunchedEffect(file, pageIndex) {
        try {
            val bmp = withContext(Dispatchers.IO) { PdfEngine.renderPageBitmap(file, pageIndex, 1400) }
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener { res -> text = res.text.ifBlank { "No text found." }; bmp.recycle() }
                .addOnFailureListener { e -> text = "OCR failed: ${e.message}"; bmp.recycle() }
        } catch (e: Exception) { text = "Error: ${e.message}" }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Offline OCR (Page ${pageIndex + 1})", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = { onCopy(text) }) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Copy")
        }
    }
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp, max = 210.dp)) {
        LazyColumn(contentPadding = PaddingValues(12.dp)) {
            item { SelectionContainer { Text(text, fontSize = 13.sp, lineHeight = 19.sp) } }
        }
    }
}

@Composable
private fun AcrobatExportSheet(onCompress: (Float, Int) -> Unit, onPng: () -> Unit, onTxt: () -> Unit) {
    Text("Compress & Export PDF", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = { onCompress(0.45f, 800) }, modifier = Modifier.weight(1f)) { Text("High Compress", fontSize = 11.sp) }
        OutlinedButton(onClick = { onCompress(0.70f, 1050) }, modifier = Modifier.weight(1f)) { Text("Balanced", fontSize = 11.sp) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onPng, modifier = Modifier.weight(1f)) { Text("Export Page PNG", fontSize = 12.sp) }
        OutlinedButton(onClick = onTxt, modifier = Modifier.weight(1f)) { Text("Export Text (.txt)", fontSize = 12.sp) }
    }
}

@Composable
private fun AcrobatPropertiesSheet(file: File, onSave: (String, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    LaunchedEffect(file) {
        val m = withContext(Dispatchers.IO) { PdfEngine.getMetadata(file) }
        title = m.title; author = m.author; subject = m.subject
    }
    Text("Document Properties", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = author, onValueChange = { author = it }, label = { Text("Author") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Button(onClick = { onSave(title, author, subject) }, modifier = Modifier.fillMaxWidth()) { Text("Save Properties") }
}
