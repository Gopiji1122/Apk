package com.aurapdf.editor.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private enum class MarkupTool { PEN, HIGHLIGHTER, TEXT }

private data class DrawStroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float,
    val alpha: Float
)

private data class TextStamp(
    val position: Offset,
    val text: String,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotateScreen(
    pdfFile: File,
    pageIndex: Int,
    onBack: () -> Unit,
    onSaveOverlay: (Bitmap) -> Unit
) {
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loadError by remember { mutableStateOf(false) }
    var activeTool by remember { mutableStateOf(MarkupTool.PEN) }
    var selectedColor by remember { mutableStateOf(Color(0xFFE53935)) }
    val palette = listOf(
        Color(0xFF0F172A),
        Color(0xFFE53935),
        Color(0xFF1E88E5),
        Color(0xFF43A047),
        Color(0xFFFDD835)
    )

    val strokes = remember { mutableStateListOf<DrawStroke>() }
    val textStamps = remember { mutableStateListOf<TextStamp>() }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }

    var pendingTextPos by remember { mutableStateOf<Offset?>(null) }
    var textInput by remember { mutableStateOf("") }
    val density = LocalDensity.current

    LaunchedEffect(pdfFile, pageIndex) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        renderer.openPage(pageIndex.coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0))).use { page ->
                            val w = 1080
                            val h = (w.toFloat() / page.width * page.height).toInt()
                            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                                it.eraseColor(AndroidColor.WHITE)
                                page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    }
                }
            }.getOrNull()
        }
        pageBitmap = loaded
        loadError = loaded == null
    }

    if (pendingTextPos != null) {
        AlertDialog(
            onDismissRequest = { pendingTextPos = null; textInput = "" },
            title = { Text("Add Text Annotation") },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text("Enter text") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    enabled = textInput.isNotBlank(),
                    onClick = {
                        pendingTextPos?.let { pos ->
                            textStamps.add(TextStamp(pos, textInput, selectedColor))
                        }
                        pendingTextPos = null
                        textInput = ""
                    }
                ) { Text("Place") }
            },
            dismissButton = {
                TextButton(onClick = { pendingTextPos = null; textInput = "" }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Markup Page ${pageIndex + 1}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        enabled = strokes.isNotEmpty() || textStamps.isNotEmpty(),
                        onClick = {
                            if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex)
                            else if (textStamps.isNotEmpty()) textStamps.removeAt(textStamps.lastIndex)
                        }
                    ) {
                        Icon(Icons.Rounded.Undo, contentDescription = "Undo")
                    }
                    Button(
                        enabled = strokes.isNotEmpty() || textStamps.isNotEmpty(),
                        onClick = {
                            val bmp = pageBitmap ?: return@Button
                            val overlay = renderAnnotationOverlay(
                                outW = bmp.width,
                                outH = bmp.height,
                                viewW = canvasSize.width.toFloat(),
                                viewH = canvasSize.height.toFloat(),
                                strokes = strokes.toList(),
                                texts = textStamps.toList()
                            )
                            onSaveOverlay(overlay)
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Apply")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FilterChip(
                            selected = activeTool == MarkupTool.PEN,
                            onClick = { activeTool = MarkupTool.PEN },
                            label = { Text("Pen") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(16.dp)) }
                        )
                        FilterChip(
                            selected = activeTool == MarkupTool.HIGHLIGHTER,
                            onClick = {
                                activeTool = MarkupTool.HIGHLIGHTER
                                selectedColor = Color(0xFFFDD835)
                            },
                            label = { Text("Highlight") },
                            leadingIcon = { Icon(Icons.Rounded.Brush, null, modifier = Modifier.size(16.dp)) }
                        )
                        FilterChip(
                            selected = activeTool == MarkupTool.TEXT,
                            onClick = { activeTool = MarkupTool.TEXT },
                            label = { Text("Text") },
                            leadingIcon = { Icon(Icons.Rounded.TextFields, null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        palette.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(
                                        width = if (selectedColor == c) 3.dp else 1.dp,
                                        color = if (selectedColor == c) MaterialTheme.colorScheme.primary else Color.LightGray,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = c }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val bmp = pageBitmap
            if (loadError) {
                Text("Could not render this page for markup.")
            } else if (bmp == null) {
                CircularProgressIndicator()
            } else {
                val aspect = bmp.width.toFloat() / bmp.height.toFloat()
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .aspectRatio(aspect)
                        .clip(RoundedCornerShape(8.dp))
                        .onSizeChanged { canvasSize = it }
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(activeTool, selectedColor) {
                                if (activeTool == MarkupTool.TEXT) {
                                    detectTapGestures { offset ->
                                        pendingTextPos = offset
                                    }
                                } else {
                                    detectDragGestures(
                                        onDragStart = { currentPoints = listOf(it) },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            currentPoints = currentPoints + change.position
                                        },
                                        onDragEnd = {
                                            if (currentPoints.size > 1) {
                                                strokes.add(
                                                    DrawStroke(
                                                        points = currentPoints,
                                                        color = selectedColor,
                                                        width = if (activeTool == MarkupTool.HIGHLIGHTER) 34f else 8f,
                                                        alpha = if (activeTool == MarkupTool.HIGHLIGHTER) 0.38f else 1f
                                                    )
                                                )
                                            }
                                            currentPoints = emptyList()
                                        }
                                    )
                                }
                            }
                    ) {
                        fun renderPath(pts: List<Offset>, col: Color, w: Float, a: Float) {
                            if (pts.size < 2) return
                            val p = Path().apply {
                                moveTo(pts.first().x, pts.first().y)
                                pts.drop(1).forEach { lineTo(it.x, it.y) }
                            }
                            drawPath(
                                path = p,
                                color = col.copy(alpha = a),
                                style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }

                        strokes.forEach { s -> renderPath(s.points, s.color, s.width, s.alpha) }
                        if (currentPoints.size > 1) {
                            renderPath(
                                currentPoints,
                                selectedColor,
                                if (activeTool == MarkupTool.HIGHLIGHTER) 34f else 8f,
                                if (activeTool == MarkupTool.HIGHLIGHTER) 0.38f else 1f
                            )
                        }
                    }

                    textStamps.forEach { stamp ->
                        Text(
                            text = stamp.text,
                            color = stamp.color,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.offset(
                                x = with(density) { stamp.position.x.toDp() },
                                y = with(density) { stamp.position.y.toDp() }
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun renderAnnotationOverlay(
    outW: Int,
    outH: Int,
    viewW: Float,
    viewH: Float,
    strokes: List<DrawStroke>,
    texts: List<TextStamp>
): Bitmap {
    val sx = outW / viewW.coerceAtLeast(1f)
    val sy = outH / viewH.coerceAtLeast(1f)
    val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)

    strokes.forEach { s ->
        if (s.points.size >= 2) {
            val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                color = s.color.copy(alpha = s.alpha).toArgb()
                style = AndroidPaint.Style.STROKE
                strokeWidth = s.width * sx
                strokeCap = AndroidPaint.Cap.ROUND
                strokeJoin = AndroidPaint.Join.ROUND
            }
            val path = AndroidPath().apply {
                moveTo(s.points.first().x * sx, s.points.first().y * sy)
                s.points.drop(1).forEach { lineTo(it.x * sx, it.y * sy) }
            }
            canvas.drawPath(path, paint)
        }
    }

    texts.forEach { t ->
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = t.color.toArgb()
            textSize = 42f * sx * 0.45f
            isFakeBoldText = true
        }
        canvas.drawText(t.text, t.position.x * sx, t.position.y * sy + paint.textSize, paint)
    }

    return bitmap
}
