package com.offlinepdf.studio.feature.reader

import android.graphics.Paint as AndroidPaint
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AutoFixOff
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.StrikethroughS
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinepdf.studio.core.pdf.PdfAnnotationTool
import com.offlinepdf.studio.core.pdf.PdfEditAnnotation
import com.offlinepdf.studio.core.pdf.PdfEditorMode
import com.offlinepdf.studio.core.pdf.PdfMarkupKind
import com.offlinepdf.studio.core.pdf.PdfNormPoint
import com.offlinepdf.studio.core.pdf.PdfShapeKind
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val EditorAccent = Color(0xFF2B84F6)
private val EditorStageBg = Color(0xFF1E1F23)
private val EditorBarBg = Color(0xFF0E0F12)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfEditorScreen(
    uriString: String,
    onBackToViewer: () -> Unit,
    viewModel: PdfEditorViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val savePdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> if (uri != null) viewModel.saveAnnotatedCopyToUri(uri) }

    LaunchedEffect(uriString) {
        if (uriString.isNotBlank()) viewModel.openDocument(uriString)
    }

    LaunchedEffect(state.statusMessage, state.errorMessage) {
        val msg = state.errorMessage ?: state.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearMessages()
    }

    BackHandler {
        viewModel.requestExit(onBackToViewer)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 1. Adobe-Style Compact Dark Top Header: Close | [Comment | Fill & Sign] | Undo/Redo/Comments/Save
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .background(EditorBarBg)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { viewModel.requestExit(onBackToViewer) }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close Editor", tint = Color.White)
                }

                // Segmented Mode Pill: Comment | Fill & Sign
                Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF22242A)) {
                    Row(modifier = Modifier.padding(2.dp)) {
                        PdfEditorMode.entries.forEach { mode ->
                            val selected = state.editorMode == mode
                            Surface(
                                onClick = { viewModel.selectEditorMode(mode) },
                                shape = RoundedCornerShape(18.dp),
                                color = if (selected) EditorAccent else Color.Transparent
                            ) {
                                Text(
                                    text = mode.label,
                                    color = if (selected) Color.White else Color(0xFFB8BCC6),
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.undo() }, enabled = state.canUndo, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Undo,
                            contentDescription = "Undo",
                            tint = if (state.canUndo) Color.White else Color.DarkGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = { viewModel.redo() }, enabled = state.canRedo, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Redo,
                            contentDescription = "Redo",
                            tint = if (state.canRedo) Color.White else Color.DarkGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Box {
                        IconButton(onClick = { viewModel.openCommentsSheet(true) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Annotations List", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        if (state.annotations.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(EditorAccent),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${state.annotations.size}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Surface(
                        onClick = { savePdfLauncher.launch(viewModel.suggestedSaveFileName()) },
                        shape = RoundedCornerShape(18.dp),
                        color = EditorAccent,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Outlined.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                            Text("Save", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // 2. Main Interactive PDF Page Stage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(EditorStageBg),
                contentAlignment = Alignment.Center
            ) {
                when {
                    state.isLoading -> CircularProgressIndicator(color = EditorAccent)
                    state.currentPageBitmap != null -> {
                        InteractivePdfAnnotationCanvas(
                            bitmap = state.currentPageBitmap!!,
                            annotations = state.currentPageAnnotations,
                            activeTool = state.activeTool,
                            activeColorArgb = state.activeColorArgb,
                            strokeWidthPt = state.strokeWidthPt,
                            onTap = viewModel::onCanvasTap,
                            onMarkupDrag = viewModel::addMarkupDrag,
                            onInkStroke = viewModel::addInkStroke,
                            onShapeDrag = viewModel::addShapeDrag
                        )
                    }
                    else -> Text(state.errorMessage ?: "Unable to render PDF", color = Color.LightGray)
                }

                // Floating Page Switcher Pill (< Page X / N >)
                if (state.pageCount > 1) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.72f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            IconButton(
                                onClick = { viewModel.previousPage() },
                                enabled = state.currentPage > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.KeyboardArrowLeft,
                                    contentDescription = "Previous Page",
                                    tint = if (state.currentPage > 0) Color.White else Color.Gray
                                )
                            }
                            Text(
                                text = "Page ${state.currentPage + 1} / ${state.pageCount}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(
                                onClick = { viewModel.nextPage() },
                                enabled = state.currentPage < state.pageCount - 1,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.KeyboardArrowRight,
                                    contentDescription = "Next Page",
                                    tint = if (state.currentPage < state.pageCount - 1) Color.White else Color.Gray
                                )
                            }
                        }
                    }
                }
            }

            // 3. Contextual Property Strip (Colors + Stroke/Font Size + Re-draw Signature)
            val showColorBar = state.activeTool != PdfAnnotationTool.PAN && state.activeTool != PdfAnnotationTool.ERASER
            if (showColorBar) {
                val palette = listOf(
                    android.graphics.Color.parseColor("#FFEB3B"),
                    android.graphics.Color.parseColor("#FF5252"),
                    android.graphics.Color.parseColor("#2B84F6"),
                    android.graphics.Color.parseColor("#4CAF50"),
                    android.graphics.Color.parseColor("#FF9800"),
                    android.graphics.Color.parseColor("#9C27B0"),
                    android.graphics.Color.parseColor("#111827")
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF16171C))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    palette.forEach { argb ->
                        val sel = state.activeColorArgb == argb
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(if (sel) 2.5.dp else 1.dp, if (sel) Color.White else Color.Gray, CircleShape)
                                .clickable { viewModel.selectColor(argb) }
                        )
                    }

                    if (state.activeTool in setOf(PdfAnnotationTool.PEN, PdfAnnotationTool.RECTANGLE, PdfAnnotationTool.CIRCLE, PdfAnnotationTool.LINE, PdfAnnotationTool.ARROW)) {
                        Spacer(Modifier.width(6.dp))
                        listOf("S" to 1.8f, "M" to 3.0f, "L" to 5.2f).forEach { (lbl, w) ->
                            val sel = kotlin.math.abs(state.strokeWidthPt - w) < 0.5f
                            Surface(
                                onClick = { viewModel.setStrokeWidth(w) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (sel) EditorAccent else Color(0xFF282A32)
                            ) {
                                Text(lbl, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                    }

                    if (state.activeTool in setOf(PdfAnnotationTool.TEXT, PdfAnnotationTool.FILL_TEXT)) {
                        Spacer(Modifier.width(6.dp))
                        listOf("A-" to 11f, "A" to 14f, "A+" to 20f).forEach { (lbl, sz) ->
                            val sel = kotlin.math.abs(state.fontSizePt - sz) < 1f
                            Surface(
                                onClick = { viewModel.setFontSize(sz) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (sel) EditorAccent else Color(0xFF282A32)
                            ) {
                                Text(lbl, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                    }

                    if (state.activeTool == PdfAnnotationTool.SIGNATURE) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            onClick = { viewModel.openSignaturePad(true) },
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF282A32)
                        ) {
                            Text(
                                text = if (state.hasSavedSignature) "Change Signature" else "Draw Signature",
                                color = EditorAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // 4. Adobe-Style Bottom Tool Strip (Comment Tools vs Fill & Sign Tools)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EditorBarBg)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tools = if (state.editorMode == PdfEditorMode.COMMENT) {
                    listOf(
                        PdfAnnotationTool.PAN to Icons.Outlined.OpenWith,
                        PdfAnnotationTool.HIGHLIGHT to Icons.Outlined.BorderColor,
                        PdfAnnotationTool.UNDERLINE to Icons.Outlined.FormatUnderlined,
                        PdfAnnotationTool.STRIKETHROUGH to Icons.Outlined.StrikethroughS,
                        PdfAnnotationTool.PEN to Icons.Outlined.Draw,
                        PdfAnnotationTool.NOTE to Icons.Outlined.StickyNote2,
                        PdfAnnotationTool.TEXT to Icons.Outlined.TextFields,
                        PdfAnnotationTool.RECTANGLE to Icons.Outlined.CropSquare,
                        PdfAnnotationTool.CIRCLE to Icons.Outlined.Circle,
                        PdfAnnotationTool.ARROW to Icons.AutoMirrored.Outlined.ArrowForward,
                        PdfAnnotationTool.LINE to Icons.Outlined.HorizontalRule,
                        PdfAnnotationTool.ERASER to Icons.Outlined.AutoFixOff
                    )
                } else {
                    listOf(
                        PdfAnnotationTool.PAN to Icons.Outlined.OpenWith,
                        PdfAnnotationTool.FILL_TEXT to Icons.Outlined.Title,
                        PdfAnnotationTool.CHECKMARK to Icons.Outlined.Check,
                        PdfAnnotationTool.CROSS to Icons.Outlined.Close,
                        PdfAnnotationTool.SIGNATURE to Icons.Outlined.Gesture,
                        PdfAnnotationTool.ERASER to Icons.Outlined.AutoFixOff
                    )
                }
                tools.forEach { (tool, icon) ->
                    EditorToolChip(
                        icon = icon,
                        label = tool.label,
                        selected = state.activeTool == tool,
                        onClick = { viewModel.selectTool(tool) }
                    )
                }
            }
        }

        if (state.isSaving) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(shape = RoundedCornerShape(16.dp), color = EditorBarBg) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = EditorAccent)
                        Text("Saving annotated PDF copy...", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp)
        )
    }

    // Add Text / Fill Form Field Dialog
    if (state.pendingTextTap != null) {
        var textInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.dismissTextDialog() },
            title = { Text(if (state.editorMode == PdfEditorMode.FILL_AND_SIGN) "Fill Form Field" else "Add Text") },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text("Enter text") },
                    singleLine = false,
                    maxLines = 3
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.confirmAddText(textInput) }) { Text("Place") } },
            dismissButton = { TextButton(onClick = { viewModel.dismissTextDialog() }) { Text("Cancel") } }
        )
    }

    // Add Sticky Note Dialog
    if (state.pendingNoteTap != null) {
        var noteInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.dismissNoteDialog() },
            title = { Text("Add Sticky Note") },
            text = {
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Comment text") },
                    maxLines = 4
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.confirmAddNote(noteInput) }) { Text("Add Note") } },
            dismissButton = { TextButton(onClick = { viewModel.dismissNoteDialog() }) { Text("Cancel") } }
        )
    }

    // View Existing Sticky Note Dialog
    state.activeStickyNote?.let { note ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissNoteDialog() },
            title = { Text("Sticky Note (Page ${note.pageIndex + 1})") },
            text = { Text(note.text) },
            confirmButton = { TextButton(onClick = { viewModel.dismissNoteDialog() }) { Text("Close") } },
            dismissButton = {
                TextButton(onClick = { viewModel.deleteAnnotationById(note.id) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    // Reusable Signature Drawing Pad Dialog
    if (state.showSignaturePad) {
        SignaturePadDialog(
            onDismiss = { viewModel.openSignaturePad(false) },
            onSave = viewModel::saveReusableSignature
        )
    }

    // Comments & Annotations List Bottom Sheet
    if (state.showCommentsSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.openCommentsSheet(false) },
            containerColor = EditorBarBg,
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).navigationBarsPadding()
            ) {
                Text("Annotations (${state.annotations.size})", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(10.dp))
                if (state.annotations.isEmpty()) {
                    Text("No annotations yet. Pick a tool below to start marking up this PDF.", color = Color.Gray, fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                } else {
                    LazyColumn(
                        modifier = Modifier.height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.annotations, key = { it.id }) { item ->
                            val desc = when (item) {
                                is PdfEditAnnotation.Markup -> "${item.kind.name.lowercase().replaceFirstChar { it.uppercase() }} markup"
                                is PdfEditAnnotation.Ink -> if (item.isSignature) "Signature" else "Freehand drawing"
                                is PdfEditAnnotation.StickyNote -> "Note: ${item.text}"
                                is PdfEditAnnotation.FreeText -> "Text: ${item.text}"
                                is PdfEditAnnotation.Shape -> "${item.kind.name.lowercase().replaceFirstChar { it.uppercase() }} shape"
                            }
                            Surface(
                                onClick = {
                                    viewModel.goToPage(item.pageIndex)
                                    viewModel.openCommentsSheet(false)
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF1C1E24)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Page ${item.pageIndex + 1}", color = EditorAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        Text(desc, color = Color.White, fontSize = 13.sp, maxLines = 2)
                                    }
                                    IconButton(onClick = { viewModel.deleteAnnotationById(item.id) }) {
                                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFFF6B6B))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Unsaved Annotations Discard Warning
    if (state.showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDiscardDialog() },
            title = { Text("Discard unsaved annotations?") },
            text = { Text("You have ${state.annotations.size} unsaved annotation(s). Save a copy first or discard them.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissDiscardDialog()
                    onBackToViewer()
                }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDiscardDialog() }) { Text("Keep Editing") }
            }
        )
    }
}

@Composable
private fun EditorToolChip(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) EditorAccent else Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(label, color = if (selected) Color.White else Color(0xFFC4C7D0), fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun InteractivePdfAnnotationCanvas(
    bitmap: android.graphics.Bitmap,
    annotations: List<PdfEditAnnotation>,
    activeTool: PdfAnnotationTool,
    activeColorArgb: Int,
    strokeWidthPt: Float,
    onTap: (PdfNormPoint) -> Unit,
    onMarkupDrag: (PdfNormPoint, PdfNormPoint) -> Unit,
    onInkStroke: (List<PdfNormPoint>) -> Unit,
    onShapeDrag: (PdfNormPoint, PdfNormPoint) -> Unit
) {
    val latestTool by rememberUpdatedState(activeTool)
    val latestOnTap by rememberUpdatedState(onTap)
    val latestOnMarkup by rememberUpdatedState(onMarkupDrag)
    val latestOnInk by rememberUpdatedState(onInkStroke)
    val latestOnShape by rememberUpdatedState(onShapeDrag)

    var dragStart by remember { mutableStateOf<PdfNormPoint?>(null) }
    var dragCurrent by remember { mutableStateOf<PdfNormPoint?>(null) }
    var liveInkPoints by remember { mutableStateOf<List<PdfNormPoint>>(emptyList()) }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val scale = min(boxW / bitmap.width.toFloat(), boxH / bitmap.height.toFloat())
        val imgW = bitmap.width * scale
        val imgH = bitmap.height * scale
        val left = (boxW - imgW) / 2f
        val top = (boxH - imgH) / 2f
        val imgRect = Rect(left, top, left + imgW, top + imgH)

        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "PDF Page",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(imgRect) {
                    detectTapGestures { offset ->
                        if (offset.x in imgRect.left..imgRect.right && offset.y in imgRect.top..imgRect.bottom) {
                            val pt = PdfNormPoint(
                                (offset.x - imgRect.left) / imgRect.width,
                                (offset.y - imgRect.top) / imgRect.height
                            ).clamped()
                            latestOnTap(pt)
                        }
                    }
                }
                .pointerInput(imgRect) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val tool = latestTool
                            if (tool == PdfAnnotationTool.PAN || tool == PdfAnnotationTool.ERASER) return@detectDragGestures
                            val pt = PdfNormPoint(
                                (offset.x - imgRect.left) / imgRect.width,
                                (offset.y - imgRect.top) / imgRect.height
                            ).clamped()
                            if (tool == PdfAnnotationTool.PEN) {
                                liveInkPoints = listOf(pt)
                            } else if (tool in setOf(
                                    PdfAnnotationTool.HIGHLIGHT, PdfAnnotationTool.UNDERLINE, PdfAnnotationTool.STRIKETHROUGH,
                                    PdfAnnotationTool.RECTANGLE, PdfAnnotationTool.CIRCLE, PdfAnnotationTool.LINE, PdfAnnotationTool.ARROW
                                )
                            ) {
                                dragStart = pt
                                dragCurrent = pt
                            }
                        },
                        onDragEnd = {
                            val tool = latestTool
                            if (tool == PdfAnnotationTool.PEN && liveInkPoints.size >= 2) {
                                latestOnInk(liveInkPoints)
                            } else if (dragStart != null && dragCurrent != null) {
                                if (tool in setOf(PdfAnnotationTool.HIGHLIGHT, PdfAnnotationTool.UNDERLINE, PdfAnnotationTool.STRIKETHROUGH)) {
                                    latestOnMarkup(dragStart!!, dragCurrent!!)
                                } else {
                                    latestOnShape(dragStart!!, dragCurrent!!)
                                }
                            }
                            liveInkPoints = emptyList()
                            dragStart = null
                            dragCurrent = null
                        },
                        onDragCancel = {
                            liveInkPoints = emptyList()
                            dragStart = null
                            dragCurrent = null
                        },
                        onDrag = { change, _ ->
                            val tool = latestTool
                            if (tool == PdfAnnotationTool.PAN || tool == PdfAnnotationTool.ERASER) return@detectDragGestures
                            change.consume()
                            val p = change.position
                            val pt = PdfNormPoint(
                                (p.x - imgRect.left) / imgRect.width,
                                (p.y - imgRect.top) / imgRect.height
                            ).clamped()
                            if (tool == PdfAnnotationTool.PEN) {
                                liveInkPoints = liveInkPoints + pt
                            } else if (dragStart != null) {
                                dragCurrent = pt
                            }
                        }
                    )
                }
        ) {
            fun px(nx: Float) = imgRect.left + nx * imgRect.width
            fun py(ny: Float) = imgRect.top + ny * imgRect.height

            // Draw committed annotations on this page
            annotations.forEach { annot ->
                val color = Color(annot.colorArgb)
                when (annot) {
                    is PdfEditAnnotation.Markup -> {
                        val l = px(annot.leftNorm); val r = px(annot.rightNorm)
                        val t = py(annot.topNorm); val b = py(annot.bottomNorm)
                        when (annot.kind) {
                            PdfMarkupKind.HIGHLIGHT -> drawRect(color.copy(alpha = 0.36f), Offset(l, t), Size(r - l, b - t))
                            PdfMarkupKind.UNDERLINE -> drawLine(color, Offset(l, b), Offset(r, b), strokeWidth = 2.6.dp.toPx(), cap = StrokeCap.Round)
                            PdfMarkupKind.STRIKETHROUGH -> {
                                val midY = (t + b) / 2f
                                drawLine(color, Offset(l, midY), Offset(r, midY), strokeWidth = 2.4.dp.toPx(), cap = StrokeCap.Round)
                            }
                        }
                    }
                    is PdfEditAnnotation.Ink -> {
                        val sw = (annot.strokeWidthPt * (imgRect.width / 595f)).coerceAtLeast(2.2f)
                        annot.strokes.forEach { s ->
                            if (s.size >= 2) {
                                val path = Path().apply {
                                    moveTo(px(s.first().x), py(s.first().y))
                                    for (i in 1 until s.size) lineTo(px(s[i].x), py(s[i].y))
                                }
                                drawPath(path, color.copy(alpha = annot.opacity), style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                        }
                    }
                    is PdfEditAnnotation.StickyNote -> {
                        val c = Offset(px(annot.xNorm), py(annot.yNorm))
                        drawCircle(color, radius = 12.dp.toPx(), center = c)
                        drawCircle(Color.Black.copy(alpha = 0.55f), radius = 12.dp.toPx(), center = c, style = Stroke(1.5.dp.toPx()))
                    }
                    is PdfEditAnnotation.FreeText -> {
                        val textPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                            this.color = annot.colorArgb
                            textSize = (annot.fontSizePt * (imgRect.width / 595f) * 1.15f).coerceIn(18f, 64f)
                            isFakeBoldText = annot.text == "✓" || annot.text == "✕"
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            annot.text,
                            px(annot.xNorm),
                            py(annot.yNorm) + textPaint.textSize,
                            textPaint
                        )
                    }
                    is PdfEditAnnotation.Shape -> {
                        val sx = px(annot.startXNorm); val sy = py(annot.startYNorm)
                        val ex = px(annot.endXNorm); val ey = py(annot.endYNorm)
                        val sw = (annot.strokeWidthPt * (imgRect.width / 595f)).coerceAtLeast(2.2f)
                        when (annot.kind) {
                            PdfShapeKind.RECTANGLE -> drawRect(color, Offset(min(sx, ex), min(sy, ey)), Size(max(2f, kotlin.math.abs(ex - sx)), max(2f, kotlin.math.abs(ey - sy))), style = Stroke(sw))
                            PdfShapeKind.CIRCLE -> drawOval(color, Offset(min(sx, ex), min(sy, ey)), Size(max(2f, kotlin.math.abs(ex - sx)), max(2f, kotlin.math.abs(ey - sy))), style = Stroke(sw))
                            PdfShapeKind.LINE -> drawLine(color, Offset(sx, sy), Offset(ex, ey), strokeWidth = sw, cap = StrokeCap.Round)
                            PdfShapeKind.ARROW -> {
                                drawLine(color, Offset(sx, sy), Offset(ex, ey), strokeWidth = sw, cap = StrokeCap.Round)
                                val angle = atan2((ey - sy).toDouble(), (ex - sx).toDouble())
                                val headLen = 14.dp.toPx()
                                val p1 = Offset((ex - headLen * cos(angle - 0.45)).toFloat(), (ey - headLen * sin(angle - 0.45)).toFloat())
                                val p2 = Offset((ex - headLen * cos(angle + 0.45)).toFloat(), (ey - headLen * sin(angle + 0.45)).toFloat())
                                drawLine(color, Offset(ex, ey), p1, strokeWidth = sw, cap = StrokeCap.Round)
                                drawLine(color, Offset(ex, ey), p2, strokeWidth = sw, cap = StrokeCap.Round)
                            }
                        }
                    }
                }
            }

            // Live rubber-band preview while dragging
            val previewColor = Color(activeColorArgb)
            if (liveInkPoints.size >= 2) {
                val path = Path().apply {
                    moveTo(px(liveInkPoints.first().x), py(liveInkPoints.first().y))
                    for (i in 1 until liveInkPoints.size) lineTo(px(liveInkPoints[i].x), py(liveInkPoints[i].y))
                }
                drawPath(path, previewColor, style = Stroke((strokeWidthPt * (imgRect.width / 595f)).coerceAtLeast(2.5f), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (dragStart != null && dragCurrent != null) {
                val sx = px(dragStart!!.x); val sy = py(dragStart!!.y)
                val ex = px(dragCurrent!!.x); val ey = py(dragCurrent!!.y)
                when (activeTool) {
                    PdfAnnotationTool.HIGHLIGHT -> drawRect(previewColor.copy(alpha = 0.36f), Offset(min(sx, ex), min(sy, ey)), Size(max(4f, kotlin.math.abs(ex - sx)), max(16f, kotlin.math.abs(ey - sy))))
                    PdfAnnotationTool.UNDERLINE, PdfAnnotationTool.STRIKETHROUGH, PdfAnnotationTool.LINE, PdfAnnotationTool.ARROW -> drawLine(previewColor, Offset(sx, sy), Offset(ex, ey), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                    PdfAnnotationTool.RECTANGLE -> drawRect(previewColor, Offset(min(sx, ex), min(sy, ey)), Size(max(4f, kotlin.math.abs(ex - sx)), max(4f, kotlin.math.abs(ey - sy))), style = Stroke(2.5.dp.toPx()))
                    PdfAnnotationTool.CIRCLE -> drawOval(previewColor, Offset(min(sx, ex), min(sy, ey)), Size(max(4f, kotlin.math.abs(ex - sx)), max(4f, kotlin.math.abs(ey - sy))), style = Stroke(2.5.dp.toPx()))
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun SignaturePadDialog(
    onDismiss: () -> Unit,
    onSave: (List<List<PdfNormPoint>>) -> Unit
) {
    var strokes by remember { mutableStateOf<List<List<PdfNormPoint>>>(emptyList()) }
    var currentStroke by remember { mutableStateOf<List<PdfNormPoint>>(emptyList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Draw Your Signature") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sign inside the box below. Saved locally for reuse on any PDF.", fontSize = 12.sp, color = Color.Gray)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .border(1.dp, Color.LightGray, RoundedCornerShape(10.dp))
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { o ->
                                        currentStroke = listOf(PdfNormPoint((o.x / size.width).coerceIn(0f, 1f), (o.y / size.height).coerceIn(0f, 1f)))
                                    },
                                    onDragEnd = {
                                        if (currentStroke.size >= 2) strokes = strokes + listOf(currentStroke)
                                        currentStroke = emptyList()
                                    },
                                    onDragCancel = { currentStroke = emptyList() },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val p = change.position
                                        currentStroke = currentStroke + PdfNormPoint((p.x / size.width).coerceIn(0f, 1f), (p.y / size.height).coerceIn(0f, 1f))
                                    }
                                )
                            }
                    ) {
                        val all = if (currentStroke.size >= 2) strokes + listOf(currentStroke) else strokes
                        all.forEach { s ->
                            val path = Path().apply {
                                moveTo(s.first().x * size.width, s.first().y * size.height)
                                for (i in 1 until s.size) lineTo(s[i].x * size.width, s[i].y * size.height)
                            }
                            drawPath(path, Color(0xFF111827), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(strokes) },
                enabled = strokes.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = EditorAccent)
            ) { Text("Save Signature") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { strokes = emptyList() }) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
