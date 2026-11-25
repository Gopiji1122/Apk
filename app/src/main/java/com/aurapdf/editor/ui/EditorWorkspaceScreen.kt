package com.aurapdf.editor.ui

import android.graphics.Bitmap
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurapdf.editor.data.PdfStorage
import com.aurapdf.editor.engine.PdfEngine
import com.aurapdf.editor.engine.PdfLinkInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class WorkspaceTool(val label: String, val icon: ImageVector) {
    TEXT("Text", Icons.Default.TextFields),
    IMAGE("Image", Icons.Default.Image),
    LINK("Links", Icons.Default.Link),
    SIGN("Sign", Icons.Default.Create),
    MARKUP("Highlight/Redact", Icons.Default.Highlight),
    WATERMARK("Watermark", Icons.Default.WaterDrop),
    PAGES("Pages", Icons.Default.Layers),
    SECURITY("Password", Icons.Default.Lock),
    OCR("OCR", Icons.Default.DocumentScanner),
    EXPORT("Compress/PNG", Icons.Default.Compress)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorWorkspaceScreen(
    file: File,
    storage: PdfStorage,
    onBack: () -> Unit,
    onOpenNewFile: (File) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var pageCount by remember { mutableIntStateOf(1) }
    var currentPage by remember { mutableIntStateOf(0) }
    var reloadTrigger by remember { mutableIntStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var nightInvert by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var statusBanner by remember { mutableStateOf<String?>(null) }
    var activeTool by remember { mutableStateOf<WorkspaceTool?>(null) }

    // Undo history stack (in-session snapshots)
    val undoStack = remember { mutableStateListOf<File>() }

    // Pinch-to-zoom & pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        offset = if (scale > 1f) offset + panChange else Offset.Zero
    }

    // Pressing Back closes active tool sheet first, then navigates back to last screen
    BackHandler(enabled = activeTool != null) {
        activeTool = null
    }

    LaunchedEffect(file, currentPage, reloadTrigger) {
        scale = 1f
        offset = Offset.Zero
        withContext(Dispatchers.IO) {
            try {
                val count = PdfEngine.getPageCount(file).coerceAtLeast(1)
                pageCount = count
                if (currentPage >= count) currentPage = count - 1
                pageBitmap = PdfEngine.renderPageBitmap(file, currentPage, 1200)
            } catch (e: Exception) {
                statusBanner = "Protected or unreadable PDF: ${e.message}"
            }
        }
    }

    fun applyEdit(successMsg: String, block: () -> Unit) {
        scope.launch {
            isBusy = true
            try {
                withContext(Dispatchers.IO) {
                    val snap = storage.createUndoSnapshot(file)
                    undoStack.add(snap)
                    if (undoStack.size > 12) {
                        undoStack.removeAt(0).delete()
                    }
                    block()
                }
                reloadTrigger++
                statusBanner = successMsg
            } catch (e: Exception) {
                statusBanner = "Error: ${e.message}"
            } finally {
                isBusy = false
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
                        Text("Page ${currentPage + 1} of $pageCount • Offline Studio", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    if (undoStack.isNotEmpty()) {
                        IconButton(onClick = {
                            scope.launch {
                                val lastSnap = undoStack.removeAt(undoStack.lastIndex)
                                withContext(Dispatchers.IO) {
                                    lastSnap.copyTo(file, overwrite = true)
                                    lastSnap.delete()
                                }
                                reloadTrigger++
                                statusBanner = "Undid last edit."
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                        }
                    }
                    IconButton(onClick = { nightInvert = !nightInvert }) {
                        Icon(
                            Icons.Default.DarkMode,
                            contentDescription = "Night Mode",
                            tint = if (nightInvert) Color(0xFF66BB6A) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { storage.shareFile(file) }) {
                        Icon(Icons.Default.Share, contentDescription = "Share PDF")
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
                    // Compact Page Switcher Strip
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { if (currentPage > 0) currentPage-- }, enabled = currentPage > 0, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Prev", modifier = Modifier.size(20.dp))
                        }
                        Text("Page ${currentPage + 1} / $pageCount", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    applyEdit("Added blank page") {
                                        PdfEngine.addBlankPage(file, currentPage)
                                    }
                                    currentPage++
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("Page", fontSize = 11.sp)
                            }
                            IconButton(onClick = { if (currentPage < pageCount - 1) currentPage++ }, enabled = currentPage < pageCount - 1, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Next", modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    // Full Acrobat-Style Horizontal Editor Toolbar (Apply ANY tool right on this PDF!)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(WorkspaceTool.entries) { tool ->
                            val selected = activeTool == tool
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.clickable { activeTool = tool }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = tool.icon,
                                        contentDescription = tool.label,
                                        tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = tool.label,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
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
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(statusBanner!!, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text(
                            "OK",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { statusBanner = null }
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(if (nightInvert) Color.Black else Color(0xFF1A1A1A))
                    .transformable(state = transformState),
                contentAlignment = Alignment.Center
            ) {
                if (pageBitmap != null) {
                    Image(
                        bitmap = pageBitmap!!.asImageBitmap(),
                        contentDescription = "PDF Page",
                        contentScale = ContentScale.Fit,
                        colorFilter = if (nightInvert) ColorFilter.colorMatrix(invertMatrix) else null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    )
                }
                if (isBusy || pageBitmap == null) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    // Active Tool Modal Bottom Sheet (Every feature applies live to the open PDF!)
    if (activeTool != null) {
        ModalBottomSheet(
            onDismissRequest = { activeTool = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (activeTool!!) {
                    WorkspaceTool.TEXT -> TextToolSheet(
                        onApply = { txt, size, bold, italic, color, xP, yP, sticky, url ->
                            activeTool = null
                            applyEdit("Added text to Page ${currentPage + 1}") {
                                PdfEngine.addTextToPage(file, currentPage, txt, size, bold, italic, color, xP, yP, sticky, url)
                            }
                        }
                    )

                    WorkspaceTool.IMAGE -> ImageToolSheet(
                        onPickAndInsert = { uri, xP, yP, wP ->
                            activeTool = null
                            applyEdit("Inserted image onto Page ${currentPage + 1}") {
                                PdfEngine.addImageToPage(context, file, currentPage, uri, xP, yP, wP)
                            }
                        }
                    )

                    WorkspaceTool.LINK -> LinkToolSheet(
                        file = file,
                        currentPage = currentPage,
                        onAddLink = { label, url, xP, yP ->
                            activeTool = null
                            applyEdit("Added clickable link to Page ${currentPage + 1}") {
                                PdfEngine.addTextToPage(file, currentPage, label.ifBlank { url }, 13f, true, false, 0x1976D2, xP, yP, false, url)
                            }
                        },
                        onRemoveLink = { pIdx, url ->
                            activeTool = null
                            applyEdit("Removed link(s) from PDF") {
                                PdfEngine.removeLinks(file, pIdx, url)
                            }
                        }
                    )

                    WorkspaceTool.SIGN -> SignToolSheet(
                        onApplyDrawn = { bmp, xP, yP ->
                            activeTool = null
                            applyEdit("Stamped signature on Page ${currentPage + 1}") {
                                PdfEngine.stampDrawnSignature(file, currentPage, bmp, xP, yP)
                                bmp.recycle()
                            }
                        },
                        onApplyDigital = { name, reason, loc, xP, yP ->
                            activeTool = null
                            applyEdit("Applied Digital Signature Certificate on Page ${currentPage + 1}") {
                                PdfEngine.stampDigitalSignature(file, currentPage, name, reason, loc, xP, yP)
                            }
                        }
                    )

                    WorkspaceTool.MARKUP -> MarkupToolSheet(
                        onApplyBox = { xP, yP, wP, hP, redact ->
                            activeTool = null
                            applyEdit(if (redact) "Applied permanent redaction" else "Added yellow highlight") {
                                PdfEngine.addBoxOverlay(file, currentPage, xP, yP, wP, hP, redact)
                            }
                        }
                    )

                    WorkspaceTool.WATERMARK -> WatermarkToolSheet(
                        onApply = { txt, size, opacity, angle, color, all ->
                            activeTool = null
                            applyEdit("Applied custom watermark") {
                                PdfEngine.addCustomWatermark(file, currentPage, txt, size, opacity, angle, color, all)
                            }
                        }
                    )

                    WorkspaceTool.PAGES -> PagesToolSheet(
                        currentPage = currentPage,
                        pageCount = pageCount,
                        storage = storage,
                        onAddBlank = { sz, land ->
                            activeTool = null
                            applyEdit("Inserted blank $sz page") {
                                PdfEngine.addBlankPage(file, currentPage, sz, land)
                            }
                            currentPage++
                        },
                        onRotate = { all ->
                            activeTool = null
                            applyEdit("Rotated page(s) +90°") {
                                PdfEngine.rotatePage(file, currentPage, 90, all)
                            }
                        },
                        onDelete = {
                            activeTool = null
                            applyEdit("Deleted page ${currentPage + 1}") {
                                PdfEngine.deletePage(file, currentPage)
                            }
                            if (currentPage > 0) currentPage--
                        },
                        onMoveUp = {
                            if (currentPage > 0) {
                                activeTool = null
                                val target = currentPage - 1
                                applyEdit("Moved page up") { PdfEngine.movePage(file, currentPage, target) }
                                currentPage = target
                            }
                        },
                        onAddNumbers = {
                            activeTool = null
                            applyEdit("Added page numbers to all pages") { PdfEngine.addPageNumbers(file) }
                        },
                        onAppendPdf = { extra ->
                            activeTool = null
                            applyEdit("Merged pages into this PDF") { PdfEngine.appendPdfIntoCurrent(file, extra) }
                        },
                        onSplitRange = { f1, t1 ->
                            activeTool = null
                            scope.launch {
                                val out = storage.newOutputFile("${file.nameWithoutExtension}_p${f1}_to_${t1}")
                                withContext(Dispatchers.IO) { PdfEngine.extractRangeToNewFile(file, out, f1, t1) }
                                onOpenNewFile(out)
                            }
                        }
                    )

                    WorkspaceTool.SECURITY -> SecurityToolSheet(
                        onLock = { pwd ->
                            activeTool = null
                            applyEdit("PDF encrypted with password") { PdfEngine.protectPdf(file, pwd) }
                        },
                        onUnlock = { pwd ->
                            activeTool = null
                            applyEdit("PDF password removed") { PdfEngine.unlockPdf(file, pwd) }
                        }
                    )

                    WorkspaceTool.OCR -> OcrToolSheet(
                        file = file,
                        currentPage = currentPage,
                        onCopy = { txt ->
                            clipboard.setText(AnnotatedString(txt))
                            statusBanner = "Copied OCR text!"
                        }
                    )

                    WorkspaceTool.EXPORT -> ExportToolSheet(
                        onCompress = {
                            activeTool = null
                            scope.launch {
                                isBusy = true
                                val out = storage.newOutputFile("${file.nameWithoutExtension}_compressed")
                                withContext(Dispatchers.IO) { PdfEngine.compressPdf(file, out, 0.65f) }
                                isBusy = false
                                onOpenNewFile(out)
                            }
                        },
                        onExportPng = {
                            activeTool = null
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
private fun TextToolSheet(
    onApply: (String, Float, Boolean, Boolean, Int, Float, Float, Boolean, String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var linkUrl by remember { mutableStateOf("") }
    var fontSize by remember { mutableFloatStateOf(14f) }
    var bold by remember { mutableStateOf(false) }
    var italic by remember { mutableStateOf(false) }
    var sticky by remember { mutableStateOf(false) }
    var colorHex by remember { mutableIntStateOf(0x000000) }
    var xPct by remember { mutableFloatStateOf(0.08f) }
    var yPct by remember { mutableFloatStateOf(0.15f) }

    Text("Write Text / Sticky Note / Link", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Text Content") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = linkUrl, onValueChange = { linkUrl = it }, label = { Text("Optional Link Under Text (https://...)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = bold, onClick = { bold = !bold }, label = { Text("Bold", fontSize = 11.sp) })
        FilterChip(selected = italic, onClick = { italic = !italic }, label = { Text("Italic", fontSize = 11.sp) })
        FilterChip(selected = sticky, onClick = { sticky = !sticky }, label = { Text("Sticky Note", fontSize = 11.sp) })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Black" to 0x000000, "Blue" to 0x1565C0, "Red" to 0xC62828, "Green" to 0x2E7D32).forEach { (n, h) ->
            FilterChip(selected = colorHex == h, onClick = { colorHex = h }, label = { Text(n, fontSize = 11.sp) })
        }
    }
    Text("Size: ${fontSize.toInt()}pt • X: ${(xPct * 100).toInt()}% • Y: ${(yPct * 100).toInt()}%", fontSize = 12.sp)
    Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 8f..36f)
    Slider(value = xPct, onValueChange = { xPct = it }, valueRange = 0.04f..0.80f)
    Slider(value = yPct, onValueChange = { yPct = it }, valueRange = 0.05f..0.92f)
    Button(
        onClick = { onApply(text, fontSize, bold, italic, colorHex, xPct, yPct, sticky, linkUrl.trim()) },
        enabled = text.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) { Text("Add to Page") }
}

@Composable
private fun ImageToolSheet(onPickAndInsert: (Uri, Float, Float, Float) -> Unit) {
    var xPct by remember { mutableFloatStateOf(0.10f) }
    var yPct by remember { mutableFloatStateOf(0.15f) }
    var wPct by remember { mutableFloatStateOf(0.65f) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPickAndInsert(uri, xPct, yPct, wPct)
    }

    Text("Insert JPG / PNG Image", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    Text("Width: ${(wPct * 100).toInt()}% • X: ${(xPct * 100).toInt()}% • Y: ${(yPct * 100).toInt()}%", fontSize = 12.sp)
    Slider(value = wPct, onValueChange = { wPct = it }, valueRange = 0.15f..0.92f)
    Slider(value = xPct, onValueChange = { xPct = it }, valueRange = 0.03f..0.75f)
    Slider(value = yPct, onValueChange = { yPct = it }, valueRange = 0.05f..0.85f)
    Button(
        onClick = { picker.launch(arrayOf("image/jpeg", "image/png", "image/*")) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) { Text("Pick JPG / PNG & Place on Page") }
}

@Composable
private fun LinkToolSheet(
    file: File,
    currentPage: Int,
    onAddLink: (String, String, Float, Float) -> Unit,
    onRemoveLink: (Int?, String?) -> Unit
) {
    var links by remember { mutableStateOf<List<PdfLinkInfo>>(emptyList()) }
    var label by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    var yPct by remember { mutableFloatStateOf(0.85f) }

    LaunchedEffect(file) {
        links = withContext(Dispatchers.IO) { PdfEngine.listLinks(file) }
    }

    Text("PDF Hyperlinks (Add / Remove)", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Link Display Text") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Target URL (https://...)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Text("Vertical Position on Page ${currentPage + 1}: ${(yPct * 100).toInt()}%", fontSize = 12.sp)
    Slider(value = yPct, onValueChange = { yPct = it }, valueRange = 0.06f..0.92f)
    Button(
        onClick = { onAddLink(label, url.trim(), 0.08f, yPct) },
        enabled = url.length > 8,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) { Text("Add Link Under Text") }

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
    LazyColumn(modifier = Modifier.heightIn(max = 150.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(links) { item ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
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
private fun SignToolSheet(
    onApplyDrawn: (Bitmap, Float, Float) -> Unit,
    onApplyDigital: (String, String, String, Float, Float) -> Unit
) {
    var mode by remember { mutableIntStateOf(0) } // 0 = Normal Draw, 1 = Digital Certificate
    var strokes by remember { mutableStateOf(listOf<List<Offset>>()) }
    var curStroke by remember { mutableStateOf(listOf<Offset>()) }
    var signerName by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("Approved") }
    var location by remember { mutableStateOf("India") }
    var yPct by remember { mutableFloatStateOf(0.82f) }

    Text("Sign PDF (Normal & Digital)", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("Normal Hand Sign") })
        FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Digital Signature") })
    }

    if (mode == 0) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth().height(160.dp)
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { pos -> curStroke = listOf(pos) },
                        onDrag = { change, _ -> curStroke = curStroke + change.position },
                        onDragEnd = {
                            if (curStroke.isNotEmpty()) {
                                strokes = strokes + listOf(curStroke)
                                curStroke = emptyList()
                            }
                        }
                    )
                }
            ) {
                (strokes + listOf(curStroke)).forEach { pts ->
                    for (i in 0 until pts.size - 1) {
                        drawLine(Color.Black, pts[i], pts[i + 1], strokeWidth = 6f, cap = StrokeCap.Round)
                    }
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
                    val p = Paint().apply {
                        color = android.graphics.Color.BLACK
                        strokeWidth = 6f
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        isAntiAlias = true
                    }
                    strokes.forEach { pts ->
                        val path = Path()
                        pts.forEachIndexed { idx, pt ->
                            val sx = (pt.x / 1.5f).coerceIn(0f, 599f)
                            val sy = (pt.y / 1.5f).coerceIn(0f, 239f)
                            if (idx == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                        }
                        c.drawPath(path, p)
                    }
                    onApplyDrawn(bmp, 0.56f, yPct)
                },
                modifier = Modifier.weight(1f)
            ) { Text("Stamp Signature") }
        }
    } else {
        OutlinedTextField(value = signerName, onValueChange = { signerName = it }, label = { Text("Signer Full Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason (e.g., Verified / Approved)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Location") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Vertical Position: ${(yPct * 100).toInt()}%", fontSize = 12.sp)
        Slider(value = yPct, onValueChange = { yPct = it }, valueRange = 0.10f..0.90f)
        Button(
            enabled = signerName.isNotBlank(),
            onClick = { onApplyDigital(signerName.trim(), reason.trim(), location.trim(), 0.45f, yPct) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) { Text("Apply Digital Signature + SHA-256") }
    }
}

@Composable
private fun MarkupToolSheet(onApplyBox: (Float, Float, Float, Float, Boolean) -> Unit) {
    var isRedact by remember { mutableStateOf(false) }
    var xPct by remember { mutableFloatStateOf(0.08f) }
    var yPct by remember { mutableFloatStateOf(0.25f) }
    var wPct by remember { mutableFloatStateOf(0.55f) }
    var hPct by remember { mutableFloatStateOf(0.05f) }

    Text("Highlight & Permanent Redact", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !isRedact, onClick = { isRedact = false }, label = { Text("Yellow Highlight") })
        FilterChip(selected = isRedact, onClick = { isRedact = true }, label = { Text("Black Redact") })
    }
    Text("X: ${(xPct * 100).toInt()}% • Y: ${(yPct * 100).toInt()}% • W: ${(wPct * 100).toInt()}%", fontSize = 12.sp)
    Slider(value = xPct, onValueChange = { xPct = it }, valueRange = 0.02f..0.80f)
    Slider(value = yPct, onValueChange = { yPct = it }, valueRange = 0.04f..0.92f)
    Slider(value = wPct, onValueChange = { wPct = it }, valueRange = 0.10f..0.90f)
    Slider(value = hPct, onValueChange = { hPct = it }, valueRange = 0.02f..0.30f)
    Button(
        onClick = { onApplyBox(xPct, yPct, wPct, hPct, isRedact) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) { Text(if (isRedact) "Apply Permanent Black Redaction" else "Apply Highlight") }
}

@Composable
private fun WatermarkToolSheet(onApply: (String, Float, Float, Float, Int, Boolean) -> Unit) {
    var text by remember { mutableStateOf("CONFIDENTIAL") }
    var size by remember { mutableFloatStateOf(42f) }
    var opacity by remember { mutableFloatStateOf(0.28f) }
    var angle by remember { mutableFloatStateOf(45f) }
    var colorHex by remember { mutableIntStateOf(0x888888) }
    var allPages by remember { mutableStateOf(true) }

    Text("Custom Watermark", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Watermark Text") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Gray" to 0x888888, "Red" to 0xD32F2F, "Blue" to 0x1976D2, "Black" to 0x111111).forEach { (n, h) ->
            FilterChip(selected = colorHex == h, onClick = { colorHex = h }, label = { Text(n, fontSize = 11.sp) })
        }
    }
    Text("Size: ${size.toInt()}pt • Opacity: ${(opacity * 100).toInt()}% • Angle: ${angle.toInt()}°", fontSize = 12.sp)
    Slider(value = size, onValueChange = { size = it }, valueRange = 16f..76f)
    Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0.10f..0.85f)
    Slider(value = angle, onValueChange = { angle = it }, valueRange = 0f..90f)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Apply to All Pages", fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(checked = allPages, onCheckedChange = { allPages = it })
    }
    Button(
        onClick = { onApply(text, size, opacity, angle, colorHex, allPages) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) { Text("Stamp Watermark") }
}

@Composable
private fun PagesToolSheet(
    currentPage: Int,
    pageCount: Int,
    storage: PdfStorage,
    onAddBlank: (String, Boolean) -> Unit,
    onRotate: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onAddNumbers: () -> Unit,
    onAppendPdf: (File) -> Unit,
    onSplitRange: (Int, Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    var fromP by remember { mutableStateOf("1") }
    var toP by remember { mutableStateOf(pageCount.toString()) }

    val appendPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val f = withContext(Dispatchers.IO) { storage.importPdfFromUri(uri) }
                onAppendPdf(f)
            }
        }
    }

    Text("Organize Pages (Page ${currentPage + 1} of $pageCount)", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = { onAddBlank("A4", false) }, modifier = Modifier.weight(1f)) { Text("+ Blank A4", fontSize = 12.sp) }
        OutlinedButton(onClick = { appendPicker.launch(arrayOf("application/pdf")) }, modifier = Modifier.weight(1f)) { Text("+ Merge PDF", fontSize = 12.sp) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { onRotate(false) }, modifier = Modifier.weight(1f)) { Text("Rotate Page", fontSize = 12.sp) }
        OutlinedButton(onClick = onMoveUp, enabled = currentPage > 0, modifier = Modifier.weight(1f)) { Text("Move Up", fontSize = 12.sp) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onAddNumbers, modifier = Modifier.weight(1f)) { Text("Page Numbers", fontSize = 12.sp) }
        OutlinedButton(onClick = onDelete, enabled = pageCount > 1, modifier = Modifier.weight(1f)) { Text("Delete Page", color = Color(0xFFEF5350), fontSize = 12.sp) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = fromP, onValueChange = { fromP = it }, label = { Text("From") }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(value = toP, onValueChange = { toP = it }, label = { Text("To") }, singleLine = true, modifier = Modifier.weight(1f))
        Button(onClick = { onSplitRange(fromP.toIntOrNull() ?: 1, toP.toIntOrNull() ?: pageCount) }) { Text("Split") }
    }
}

@Composable
private fun SecurityToolSheet(onLock: (String) -> Unit, onUnlock: (String) -> Unit) {
    var pwd by remember { mutableStateOf("") }
    Text("Password Protect / Unlock PDF", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    OutlinedTextField(value = pwd, onValueChange = { pwd = it }, label = { Text("Enter Password") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = { onLock(pwd) }, enabled = pwd.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Lock PDF") }
        OutlinedButton(onClick = { onUnlock(pwd) }, enabled = pwd.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Unlock PDF") }
    }
}

@Composable
private fun OcrToolSheet(file: File, currentPage: Int, onCopy: (String) -> Unit) {
    var text by remember { mutableStateOf("Scanning Page ${currentPage + 1} offline...") }

    LaunchedEffect(file, currentPage) {
        try {
            val bmp = withContext(Dispatchers.IO) { PdfEngine.renderPageBitmap(file, currentPage, 1400) }
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener { res ->
                    text = res.text.ifBlank { "No readable text found on this page." }
                    bmp.recycle()
                }
                .addOnFailureListener { e ->
                    text = "OCR failed: ${e.message}"
                    bmp.recycle()
                }
        } catch (e: Exception) {
            text = "Error: ${e.message}"
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Offline OCR (Page ${currentPage + 1})", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = { onCopy(text) }) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Copy")
        }
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 240.dp)
    ) {
        LazyColumn(contentPadding = PaddingValues(12.dp)) {
            item { SelectionContainer { Text(text, fontSize = 13.sp, lineHeight = 19.sp) } }
        }
    }
}

@Composable
private fun ExportToolSheet(onCompress: () -> Unit, onExportPng: () -> Unit) {
    Text("Compress & Export", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    Button(onClick = onCompress, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Text("Compress PDF (Reduce File Size)")
    }
    OutlinedButton(onClick = onExportPng, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Text("Export Current Page as PNG Image")
    }
}
