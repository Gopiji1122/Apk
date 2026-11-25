package com.aurapdf.editor.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
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
import com.aurapdf.editor.engine.PdfEngine
import com.aurapdf.editor.engine.PdfLinkInfo
import com.aurapdf.editor.engine.TextFontStyle
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

enum class ExtraTool {
    LINKS, SIGN, MARKUP, WATERMARK, PAGES, SECURITY, OCR, EXPORT
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

    var pageCount by remember { mutableIntStateOf(1) }
    var currentPage by remember { mutableIntStateOf(0) }
    var reloadTrigger by remember { mutableIntStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var nightInvert by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    // Interactive Draft Overlays (movable, resizable, re-editable until Save!)
    var drafts by remember { mutableStateOf(listOf<DraftOverlay>()) }
    var selectedDraftId by remember { mutableStateOf<Long?>(null) }
    var editingTextDraft by remember { mutableStateOf<DraftOverlay?>(null) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var activeExtraTool by remember { mutableStateOf<ExtraTool?>(null) }

    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }
    val undoStack = remember { mutableStateListOf<File>() }
    val scrollState = rememberScrollState()

    BackHandler(enabled = activeExtraTool != null || editingTextDraft != null || showOverflowMenu) {
        when {
            showOverflowMenu -> showOverflowMenu = false
            editingTextDraft != null -> editingTextDraft = null
            activeExtraTool != null -> activeExtraTool = null
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

    fun saveDraftsToPdf(afterSave: (() -> Unit)? = null) {
        if (drafts.isEmpty()) {
            afterSave?.invoke()
            return
        }
        val toCommit = drafts
        drafts = emptyList()
        selectedDraftId = null
        scope.launch {
            isBusy = true
            try {
                withContext(Dispatchers.IO) {
                    val snap = storage.createUndoSnapshot(file)
                    undoStack.add(snap)
                    if (undoStack.size > 12) undoStack.removeAt(0).delete()
                    PdfEngine.commitDraftOverlays(file, currentPage, toCommit)
                }
                reloadTrigger++
                statusBanner = "Saved ${toCommit.size} item(s) permanently to Page ${currentPage + 1}"
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
                    val newOverlay = DraftOverlay(
                        isImage = true,
                        imageBitmap = bmp,
                        xNorm = 0.25f,
                        yNorm = 0.25f,
                        wNorm = wN,
                        hNorm = hN
                    )
                    drafts = drafts + newOverlay
                    selectedDraftId = newOverlay.id
                    statusBanner = "Drag image to move • Drag corner handle to resize • Tap Save when done"
                }
            }
        }
    }

    val invertMatrix = remember {
        ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
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
                        Text(file.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = if (drafts.isNotEmpty()) "Page ${currentPage + 1}/$pageCount • ${drafts.size} unsaved" else "Page ${currentPage + 1} of $pageCount",
                            fontSize = 11.sp,
                            color = if (drafts.isNotEmpty()) Color(0xFFFFB74D) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (drafts.isNotEmpty()) {
                        TextButton(onClick = { saveDraftsToPdf() }) {
                            Icon(Icons.Default.Save, contentDescription = "Save", tint = Color(0xFF66BB6A), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Save", color = Color(0xFF66BB6A), fontWeight = FontWeight.Bold)
                        }
                    } else if (undoStack.isNotEmpty()) {
                        IconButton(onClick = {
                            scope.launch {
                                val snap = undoStack.removeAt(undoStack.lastIndex)
                                withContext(Dispatchers.IO) {
                                    snap.copyTo(file, overwrite = true)
                                    snap.delete()
                                }
                                reloadTrigger++
                                statusBanner = "Undid last edit"
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                        }
                    }

                    // UPPER THREE-DOT MENU WITH ALL OTHER PDF OPTIONS
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "All Edit Options")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Add Blank Page") },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    saveDraftsToPdf {
                                        applyPdfMutation("Added blank page") { PdfEngine.addBlankPage(file, currentPage) }
                                        currentPage++
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Organize / Merge / Split Pages") },
                                leadingIcon = { Icon(Icons.Default.Layers, contentDescription = null) },
                                onClick = { showOverflowMenu = false; activeExtraTool = ExtraTool.PAGES }
                            )
                            DropdownMenuItem(
                                text = { Text("Links (Add / Remove)") },
                                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                                onClick = { showOverflowMenu = false; activeExtraTool = ExtraTool.LINKS }
                            )
                            DropdownMenuItem(
                                text = { Text("Sign (Normal & Digital)") },
                                leadingIcon = { Icon(Icons.Default.Create, contentDescription = null) },
                                onClick = { showOverflowMenu = false; activeExtraTool = ExtraTool.SIGN }
                            )
                            DropdownMenuItem(
                                text = { Text("Highlight & Redact") },
                                leadingIcon = { Icon(Icons.Default.Highlight, contentDescription = null) },
                                onClick = { showOverflowMenu = false; activeExtraTool = ExtraTool.MARKUP }
                            )
                            DropdownMenuItem(
                                text = { Text("Custom Watermark") },
                                leadingIcon = { Icon(Icons.Default.WaterDrop, contentDescription = null) },
                                onClick = { showOverflowMenu = false; activeExtraTool = ExtraTool.WATERMARK }
                            )
                            DropdownMenuItem(
                                text = { Text("Password Lock / Unlock") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                onClick = { showOverflowMenu = false; activeExtraTool = ExtraTool.SECURITY }
                            )
                            DropdownMenuItem(
                                text = { Text("Offline OCR (Extract Text)") },
                                leadingIcon = { Icon(Icons.Default.DocumentScanner, contentDescription = null) },
                                onClick = { showOverflowMenu = false; activeExtraTool = ExtraTool.OCR }
                            )
                            DropdownMenuItem(
                                text = { Text("Compress / Export PNG") },
                                leadingIcon = { Icon(Icons.Default.Compress, contentDescription = null) },
                                onClick = { showOverflowMenu = false; activeExtraTool = ExtraTool.EXPORT }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(if (nightInvert) "Disable Night Mode" else "Night Invert Mode") },
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
                    // Page Switcher Strip
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { saveDraftsToPdf { if (currentPage > 0) currentPage-- } },
                            enabled = currentPage > 0,
                            modifier = Modifier.size(30.dp)
                        ) { Icon(Icons.Default.ChevronLeft, contentDescription = "Prev") }

                        Text("Page ${currentPage + 1} / $pageCount", fontSize = 12.sp, fontWeight = FontWeight.Medium)

                        IconButton(
                            onClick = { saveDraftsToPdf { if (currentPage < pageCount - 1) currentPage++ } },
                            enabled = currentPage < pageCount - 1,
                            modifier = Modifier.size(30.dp)
                        ) { Icon(Icons.Default.ChevronRight, contentDescription = "Next") }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    // Primary Interactive Edit Bar: Add Text, Add Image, Edit Selected, Save
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val blank = DraftOverlay(isImage = false, text = "", xNorm = 0.12f, yNorm = 0.16f)
                                editingTextDraft = blank
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Text", fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = { galleryImgPicker.launch(arrayOf("image/jpeg", "image/png", "image/*")) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Image", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (statusBanner != null) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(statusBanner!!, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Text("OK", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { statusBanner = null })
                    }
                }
            }

            // Scrollable Interactive Page Canvas with Live Touch-Move & Touch-Resize Overlays!
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(if (nightInvert) Color.Black else Color(0xFF181818))
                    .verticalScroll(scrollState)
                    .padding(12.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                if (pageBitmap != null) {
                    val aspect = pageBitmap!!.width.toFloat() / pageBitmap!!.height.coerceAtLeast(1)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspect.coerceIn(0.45f, 1.8f))
                            .onSizeChanged { canvasSize = it }
                            .clickable { selectedDraftId = null }
                    ) {
                        Image(
                            bitmap = pageBitmap!!.asImageBitmap(),
                            contentDescription = "PDF Page",
                            contentScale = ContentScale.FillBounds,
                            colorFilter = if (nightInvert) ColorFilter.colorMatrix(invertMatrix) else null,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Render every interactive DraftOverlay (Drag to Move, Corner Handle to Resize, Tap to Re-Edit!)
                        drafts.forEach { ov ->
                            val isSelected = selectedDraftId == ov.id
                            val pxX = (ov.xNorm * canvasSize.width).roundToInt()
                            val pxY = (ov.yNorm * canvasSize.height).roundToInt()
                            val wDp = with(density) { (ov.wNorm * canvasSize.width).toDp() }
                            val hDp = with(density) { (ov.hNorm * canvasSize.height).toDp() }

                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(pxX, pxY) }
                                    .then(if (ov.isImage) Modifier.size(wDp.coerceAtLeast(48.dp), hDp.coerceAtLeast(48.dp)) else Modifier.wrapContentSize())
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.8.dp,
                                        color = if (isSelected) Color(0xFF2196F3) else Color(0x882196F3),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .pointerInput(ov.id, canvasSize) {
                                        detectDragGestures(
                                            onDragStart = { selectedDraftId = ov.id },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val dxN = dragAmount.x / canvasSize.width.coerceAtLeast(1)
                                                val dyN = dragAmount.y / canvasSize.height.coerceAtLeast(1)
                                                drafts = drafts.map { item ->
                                                    if (item.id == ov.id) {
                                                        item.copy(
                                                            xNorm = (item.xNorm + dxN).coerceIn(0.01f, 0.90f),
                                                            yNorm = (item.yNorm + dyN).coerceIn(0.01f, 0.90f)
                                                        )
                                                    } else item
                                                }
                                            }
                                        )
                                    }
                                    .clickable { selectedDraftId = ov.id }
                            ) {
                                if (ov.isImage && ov.imageBitmap != null) {
                                    Image(
                                        bitmap = ov.imageBitmap.asImageBitmap(),
                                        contentDescription = "Draft Image",
                                        contentScale = ContentScale.FillBounds,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    val ff = when (ov.fontStyle) {
                                        TextFontStyle.SANS -> FontFamily.SansSerif
                                        TextFontStyle.SERIF -> FontFamily.Serif
                                        TextFontStyle.MONO -> FontFamily.Monospace
                                    }
                                    Text(
                                        text = ov.text,
                                        fontSize = ov.fontSizeSp.sp,
                                        fontFamily = ff,
                                        fontWeight = if (ov.isBold) FontWeight.Bold else FontWeight.Normal,
                                        fontStyle = if (ov.isItalic) FontStyle.Italic else FontStyle.Normal,
                                        textDecoration = if (ov.isUnderline || ov.linkUrl.isNotBlank()) TextDecoration.Underline else TextDecoration.None,
                                        color = if (ov.linkUrl.isNotBlank()) Color(0xFF1976D2) else Color(ov.colorArgb),
                                        modifier = Modifier
                                            .background(if (ov.hasBgHighlight) Color(0xFFFFF59D) else Color.Transparent)
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }

                                // Top-right quick buttons when selected: Delete & Re-Edit Text
                                if (isSelected) {
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(y = (-28).dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (!ov.isImage) {
                                            Surface(
                                                shape = CircleShape,
                                                color = Color(0xFF1976D2),
                                                modifier = Modifier.size(24.dp).clickable { editingTextDraft = ov }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.Edit, contentDescription = "Re-edit Text", tint = Color.White, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                        Surface(
                                            shape = CircleShape,
                                            color = Color(0xFFD32F2F),
                                            modifier = Modifier.size(24.dp).clickable {
                                                drafts = drafts.filterNot { it.id == ov.id }
                                                selectedDraftId = null
                                            }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }

                                    // Bottom-right Touch Resize Handle (for Image width/height OR Text font size!)
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
                                                            if (item.isImage) {
                                                                val dw = drag.x / canvasSize.width.coerceAtLeast(1)
                                                                val dh = drag.y / canvasSize.height.coerceAtLeast(1)
                                                                item.copy(
                                                                    wNorm = (item.wNorm + dw).coerceIn(0.10f, 0.94f),
                                                                    hNorm = (item.hNorm + dh).coerceIn(0.08f, 0.90f)
                                                                )
                                                            } else {
                                                                val deltaSp = (drag.x + drag.y) * 0.08f
                                                                item.copy(fontSizeSp = (item.fontSizeSp + deltaSp).coerceIn(8f, 48f))
                                                            }
                                                        } else item
                                                    }
                                                }
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.OpenInFull, contentDescription = "Resize", tint = Color.White, modifier = Modifier.size(13.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (isBusy || pageBitmap == null) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }

    // Re-Editable Rich Text Styling Dialog (Every Color, Font Design, Bold/Italic/Underline/Highlight, Link)
    if (editingTextDraft != null) {
        val target = editingTextDraft!!
        RichTextEditorDialog(
            initial = target,
            onDismiss = { editingTextDraft = null },
            onConfirm = { updated ->
                val exists = drafts.any { it.id == updated.id }
                drafts = if (exists) drafts.map { if (it.id == updated.id) updated else it } else drafts + updated
                selectedDraftId = updated.id
                editingTextDraft = null
                statusBanner = "Drag text to move • Drag blue corner to resize • Tap Edit icon to re-edit • Tap Save"
            }
        )
    }

    // Extra Tools Modal Bottom Sheet (triggered directly from upper 3-dot menu!)
    if (activeExtraTool != null) {
        ModalBottomSheet(
            onDismissRequest = { activeExtraTool = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (activeExtraTool!!) {
                    ExtraTool.LINKS -> ExtraLinksSheet(
                        file = file,
                        onAddLink = { label, url ->
                            activeExtraTool = null
                            val ov = DraftOverlay(isImage = false, text = label.ifBlank { url }, linkUrl = url, isBold = true, colorArgb = 0xFF1976D2, xNorm = 0.12f, yNorm = 0.78f)
                            drafts = drafts + ov
                            selectedDraftId = ov.id
                            statusBanner = "Drag link to exact spot & tap Save"
                        },
                        onRemoveLink = { pIdx, url ->
                            activeExtraTool = null
                            applyPdfMutation("Removed link(s)") { PdfEngine.removeLinks(file, pIdx, url) }
                        }
                    )
                    ExtraTool.SIGN -> ExtraSignSheet(
                        onApplyDrawn = { bmp ->
                            activeExtraTool = null
                            val ov = DraftOverlay(isImage = true, imageBitmap = bmp, xNorm = 0.50f, yNorm = 0.72f, wNorm = 0.36f, hNorm = 0.14f)
                            drafts = drafts + ov
                            selectedDraftId = ov.id
                            statusBanner = "Drag & resize your signature • Tap Save when placed"
                        },
                        onApplyDigital = { name, reason, loc ->
                            activeExtraTool = null
                            saveDraftsToPdf {
                                applyPdfMutation("Applied Digital Signature + SHA-256") {
                                    PdfEngine.stampDigitalSignature(file, currentPage, name, reason, loc, 0.45f, 0.80f)
                                }
                            }
                        }
                    )
                    ExtraTool.MARKUP -> ExtraMarkupSheet { xP, yP, wP, hP, redact ->
                        activeExtraTool = null
                        saveDraftsToPdf {
                            applyPdfMutation(if (redact) "Applied redaction" else "Added highlight") {
                                PdfEngine.addBoxOverlay(file, currentPage, xP, yP, wP, hP, redact)
                            }
                        }
                    }
                    ExtraTool.WATERMARK -> ExtraWatermarkSheet { txt, sz, op, ang, col, all ->
                        activeExtraTool = null
                        saveDraftsToPdf {
                            applyPdfMutation("Applied custom watermark") {
                                PdfEngine.addCustomWatermark(file, currentPage, txt, sz, op, ang, col, all)
                            }
                        }
                    }
                    ExtraTool.PAGES -> ExtraPagesSheet(
                        currentPage = currentPage,
                        pageCount = pageCount,
                        storage = storage,
                        onAddBlank = {
                            activeExtraTool = null
                            saveDraftsToPdf {
                                applyPdfMutation("Added blank page") { PdfEngine.addBlankPage(file, currentPage) }
                                currentPage++
                            }
                        },
                        onRotate = {
                            activeExtraTool = null
                            saveDraftsToPdf { applyPdfMutation("Rotated page +90°") { PdfEngine.rotatePage(file, currentPage, 90, false) } }
                        },
                        onDelete = {
                            activeExtraTool = null
                            drafts = emptyList()
                            applyPdfMutation("Deleted page") { PdfEngine.deletePage(file, currentPage) }
                            if (currentPage > 0) currentPage--
                        },
                        onMoveUp = {
                            if (currentPage > 0) {
                                activeExtraTool = null
                                val target = currentPage - 1
                                saveDraftsToPdf { applyPdfMutation("Moved page up") { PdfEngine.movePage(file, currentPage, target) } }
                                currentPage = target
                            }
                        },
                        onAddNumbers = {
                            activeExtraTool = null
                            saveDraftsToPdf { applyPdfMutation("Added page numbers") { PdfEngine.addPageNumbers(file) } }
                        },
                        onMerge = { extra ->
                            activeExtraTool = null
                            saveDraftsToPdf { applyPdfMutation("Merged PDF pages") { PdfEngine.appendPdfIntoCurrent(file, extra) } }
                        },
                        onSplit = { f1, t1 ->
                            activeExtraTool = null
                            scope.launch {
                                val out = storage.newOutputFile("${file.nameWithoutExtension}_p${f1}_$t1")
                                withContext(Dispatchers.IO) { PdfEngine.extractRangeToNewFile(file, out, f1, t1) }
                                onOpenNewFile(out)
                            }
                        }
                    )
                    ExtraTool.SECURITY -> ExtraSecuritySheet(
                        onLock = { pwd -> activeExtraTool = null; saveDraftsToPdf { applyPdfMutation("PDF locked") { PdfEngine.protectPdf(file, pwd) } } },
                        onUnlock = { pwd -> activeExtraTool = null; saveDraftsToPdf { applyPdfMutation("PDF unlocked") { PdfEngine.unlockPdf(file, pwd) } } }
                    )
                    ExtraTool.OCR -> ExtraOcrSheet(file, currentPage) { txt ->
                        clipboard.setText(AnnotatedString(txt))
                        statusBanner = "Copied OCR text"
                    }
                    ExtraTool.EXPORT -> ExtraExportSheet(
                        onCompress = {
                            activeExtraTool = null
                            scope.launch {
                                isBusy = true
                                val out = storage.newOutputFile("${file.nameWithoutExtension}_compressed")
                                withContext(Dispatchers.IO) { PdfEngine.compressPdf(file, out, 0.65f) }
                                isBusy = false
                                onOpenNewFile(out)
                            }
                        },
                        onPng = {
                            activeExtraTool = null
                            scope.launch {
                                val png = File(context.cacheDir, "${file.nameWithoutExtension}_p${currentPage + 1}.png")
                                withContext(Dispatchers.IO) { PdfEngine.exportPageAsPng(file, currentPage, png) }
                                storage.shareFile(png, "image/png")
                            }
                        }
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun RichTextEditorDialog(
    initial: DraftOverlay,
    onDismiss: () -> Unit,
    onConfirm: (DraftOverlay) -> Unit
) {
    var text by remember { mutableStateOf(initial.text) }
    var fontSize by remember { mutableFloatStateOf(initial.fontSizeSp) }
    var fontStyle by remember { mutableStateOf(initial.fontStyle) }
    var bold by remember { mutableStateOf(initial.isBold) }
    var italic by remember { mutableStateOf(initial.isItalic) }
    var underline by remember { mutableStateOf(initial.isUnderline) }
    var bgHighlight by remember { mutableStateOf(initial.hasBgHighlight) }
    var colorArgb by remember { mutableLongStateOf(initial.colorArgb) }
    var linkUrl by remember { mutableStateOf(initial.linkUrl) }

    val colorPalette = listOf(
        0xFF000000L, 0xFFFFFFFFL, 0xFFD32F2FL, 0xFF1976D2L, 0xFF2E7D32L,
        0xFFF57C00L, 0xFF7B1FA2L, 0xFF00838FL, 0xFFC2185BL, 0xFF5D4037L
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.text.isBlank()) "Add Styled Text" else "Re-Edit Text") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Write text...") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = linkUrl,
                    onValueChange = { linkUrl = it },
                    label = { Text("Link Under Text (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // Font Design Row
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextFontStyle.entries.forEach { st ->
                        FilterChip(selected = fontStyle == st, onClick = { fontStyle = st }, label = { Text(st.label, fontSize = 11.sp) })
                    }
                }
                // Format toggles
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChip(selected = bold, onClick = { bold = !bold }, label = { Text("B", fontWeight = FontWeight.Bold) }) }
                    item { FilterChip(selected = italic, onClick = { italic = !italic }, label = { Text("I", fontStyle = FontStyle.Italic) }) }
                    item { FilterChip(selected = underline, onClick = { underline = !underline }, label = { Text("U", textDecoration = TextDecoration.Underline) }) }
                    item { FilterChip(selected = bgHighlight, onClick = { bgHighlight = !bgHighlight }, label = { Text("Highlight", fontSize = 11.sp) }) }
                }
                // Color Palette
                Text("Text Color", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(colorPalette) { c ->
                        val selected = colorArgb == c
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(if (selected) 2.5.dp else 1.dp, if (selected) Color(0xFF2196F3) else Color.Gray, CircleShape)
                                .clickable { colorArgb = c },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = if (c == 0xFFFFFFFFL) Color.Black else Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                Text("Size: ${fontSize.toInt()} pt", fontSize = 12.sp)
                Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 8f..44f)
            }
        },
        confirmButton = {
            Button(
                enabled = text.isNotBlank(),
                onClick = {
                    onConfirm(
                        initial.copy(
                            text = text,
                            fontSizeSp = fontSize,
                            fontStyle = fontStyle,
                            isBold = bold,
                            isItalic = italic,
                            isUnderline = underline,
                            hasBgHighlight = bgHighlight,
                            colorArgb = colorArgb,
                            linkUrl = linkUrl.trim()
                        )
                    )
                }
            ) { Text("Place on Page") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ExtraLinksSheet(file: File, onAddLink: (String, String) -> Unit, onRemoveLink: (Int?, String?) -> Unit) {
    var links by remember { mutableStateOf<List<PdfLinkInfo>>(emptyList()) }
    var label by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    LaunchedEffect(file) { links = withContext(Dispatchers.IO) { PdfEngine.listLinks(file) } }

    Text("PDF Hyperlinks", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Link Text") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("URL (https://...)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Button(onClick = { onAddLink(label, url.trim()) }, enabled = url.length > 8, modifier = Modifier.fillMaxWidth()) { Text("Place Draggable Link on Page") }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Existing Links (${links.size})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (links.isNotEmpty()) {
            TextButton(onClick = { onRemoveLink(null, null) }) {
                Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Remove All", color = Color(0xFFEF5350))
            }
        }
    }
    LazyColumn(modifier = Modifier.heightIn(max = 140.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(links) { item ->
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.url, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Page ${item.pageIndex + 1}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onRemoveLink(item.pageIndex, item.url) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Remove", tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtraSignSheet(onApplyDrawn: (Bitmap) -> Unit, onApplyDigital: (String, String, String) -> Unit) {
    var mode by remember { mutableIntStateOf(0) }
    var strokes by remember { mutableStateOf(listOf<List<Offset>>()) }
    var curStroke by remember { mutableStateOf(listOf<Offset>()) }
    var signer by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("Approved") }
    var loc by remember { mutableStateOf("India") }

    Text("Sign PDF (Normal & Digital)", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("Hand Sign (Draggable)") })
        FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Digital Signature") })
    }
    if (mode == 0) {
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
                    for (i in 0 until pts.size - 1) drawLine(Color.Black, pts[i], pts[i + 1], strokeWidth = 6f, cap = StrokeCap.Round)
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
                    val p = Paint().apply { color = android.graphics.Color.BLACK; strokeWidth = 6f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }
                    strokes.forEach { pts ->
                        val path = Path()
                        pts.forEachIndexed { idx, pt ->
                            val sx = (pt.x / 1.5f).coerceIn(0f, 599f)
                            val sy = (pt.y / 1.5f).coerceIn(0f, 239f)
                            if (idx == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                        }
                        c.drawPath(path, p)
                    }
                    onApplyDrawn(bmp)
                },
                modifier = Modifier.weight(1f)
            ) { Text("Place Signature") }
        }
    } else {
        OutlinedTextField(value = signer, onValueChange = { signer = it }, label = { Text("Signer Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = loc, onValueChange = { loc = it }, label = { Text("Location") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(enabled = signer.isNotBlank(), onClick = { onApplyDigital(signer.trim(), reason.trim(), loc.trim()) }, modifier = Modifier.fillMaxWidth()) {
            Text("Apply Digital Signature + SHA-256")
        }
    }
}

@Composable
private fun ExtraMarkupSheet(onApply: (Float, Float, Float, Float, Boolean) -> Unit) {
    var redact by remember { mutableStateOf(false) }
    var xP by remember { mutableFloatStateOf(0.08f) }
    var yP by remember { mutableFloatStateOf(0.25f) }
    var wP by remember { mutableFloatStateOf(0.55f) }
    var hP by remember { mutableFloatStateOf(0.05f) }
    Text("Highlight & Permanent Redact", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !redact, onClick = { redact = false }, label = { Text("Yellow Highlight") })
        FilterChip(selected = redact, onClick = { redact = true }, label = { Text("Black Redact") })
    }
    Text("X:${(xP * 100).toInt()}% • Y:${(yP * 100).toInt()}% • W:${(wP * 100).toInt()}%", fontSize = 12.sp)
    Slider(value = xP, onValueChange = { xP = it }, valueRange = 0.02f..0.80f)
    Slider(value = yP, onValueChange = { yP = it }, valueRange = 0.04f..0.92f)
    Slider(value = wP, onValueChange = { wP = it }, valueRange = 0.10f..0.90f)
    Slider(value = hP, onValueChange = { hP = it }, valueRange = 0.02f..0.30f)
    Button(onClick = { onApply(xP, yP, wP, hP, redact) }, modifier = Modifier.fillMaxWidth()) { Text(if (redact) "Apply Redaction" else "Apply Highlight") }
}

@Composable
private fun ExtraWatermarkSheet(onApply: (String, Float, Float, Float, Int, Boolean) -> Unit) {
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
private fun ExtraPagesSheet(
    currentPage: Int, pageCount: Int, storage: PdfStorage,
    onAddBlank: () -> Unit, onRotate: () -> Unit, onDelete: () -> Unit,
    onMoveUp: () -> Unit, onAddNumbers: () -> Unit, onMerge: (File) -> Unit, onSplit: (Int, Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    var fP by remember { mutableStateOf("1") }
    var tP by remember { mutableStateOf(pageCount.toString()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) scope.launch { onMerge(withContext(Dispatchers.IO) { storage.importPdfFromUri(u) }) }
    }
    Text("Organize Pages (Page ${currentPage + 1}/$pageCount)", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = onAddBlank, modifier = Modifier.weight(1f)) { Text("+ Blank Page", fontSize = 12.sp) }
        OutlinedButton(onClick = { picker.launch(arrayOf("application/pdf")) }, modifier = Modifier.weight(1f)) { Text("+ Merge PDF", fontSize = 12.sp) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onRotate, modifier = Modifier.weight(1f)) { Text("Rotate +90°", fontSize = 12.sp) }
        OutlinedButton(onClick = onMoveUp, enabled = currentPage > 0, modifier = Modifier.weight(1f)) { Text("Move Up", fontSize = 12.sp) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onAddNumbers, modifier = Modifier.weight(1f)) { Text("Page Numbers", fontSize = 12.sp) }
        OutlinedButton(onClick = onDelete, enabled = pageCount > 1, modifier = Modifier.weight(1f)) { Text("Delete Page", color = Color(0xFFEF5350), fontSize = 12.sp) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = fP, onValueChange = { fP = it }, label = { Text("From") }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(value = tP, onValueChange = { tP = it }, label = { Text("To") }, singleLine = true, modifier = Modifier.weight(1f))
        Button(onClick = { onSplit(fP.toIntOrNull() ?: 1, tP.toIntOrNull() ?: pageCount) }) { Text("Split") }
    }
}

@Composable
private fun ExtraSecuritySheet(onLock: (String) -> Unit, onUnlock: (String) -> Unit) {
    var pwd by remember { mutableStateOf("") }
    Text("Password Security", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    OutlinedTextField(value = pwd, onValueChange = { pwd = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = { onLock(pwd) }, enabled = pwd.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Lock PDF") }
        OutlinedButton(onClick = { onUnlock(pwd) }, enabled = pwd.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Unlock PDF") }
    }
}

@Composable
private fun ExtraOcrSheet(file: File, pageIndex: Int, onCopy: (String) -> Unit) {
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
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp, max = 220.dp)) {
        LazyColumn(contentPadding = PaddingValues(12.dp)) {
            item { SelectionContainer { Text(text, fontSize = 13.sp, lineHeight = 19.sp) } }
        }
    }
}

@Composable
private fun ExtraExportSheet(onCompress: () -> Unit, onPng: () -> Unit) {
    Text("Compress & Export", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    Button(onClick = onCompress, modifier = Modifier.fillMaxWidth()) { Text("Compress PDF") }
    OutlinedButton(onClick = onPng, modifier = Modifier.fillMaxWidth()) { Text("Export Current Page as PNG") }
}
