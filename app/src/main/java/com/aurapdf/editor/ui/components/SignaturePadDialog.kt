package com.aurapdf.editor.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun SignaturePadDialog(
    onDismiss: () -> Unit,
    onSaveSignature: (Bitmap) -> Unit
) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var inkColor by remember { mutableStateOf(Color(0xFF0F172A)) }
    val colors = listOf(Color(0xFF0F172A), Color(0xFF1D4ED8), Color(0xFFC62828))

    var canvasWidth by remember { mutableFloatStateOf(1f) }
    var canvasHeight by remember { mutableFloatStateOf(1f) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Draw Signature",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentStroke = listOf(offset)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentStroke = currentStroke + change.position
                                    },
                                    onDragEnd = {
                                        if (currentStroke.size > 1) {
                                            strokes.add(currentStroke)
                                        }
                                        currentStroke = emptyList()
                                    }
                                )
                            }
                    ) {
                        canvasWidth = size.width
                        canvasHeight = size.height

                        fun drawPts(pts: List<Offset>) {
                            if (pts.size < 2) return
                            val path = Path().apply {
                                moveTo(pts.first().x, pts.first().y)
                                pts.drop(1).forEach { lineTo(it.x, it.y) }
                            }
                            drawPath(
                                path = path,
                                color = inkColor,
                                style = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }

                        strokes.forEach { drawPts(it) }
                        drawPts(currentStroke)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        colors.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(
                                        width = if (inkColor == c) 3.dp else 1.dp,
                                        color = if (inkColor == c) MaterialTheme.colorScheme.primary else Color.LightGray,
                                        shape = CircleShape
                                    )
                                    .clickable { inkColor = c }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { strokes.clear() }) {
                            Text("Clear")
                        }
                        Button(
                            enabled = strokes.isNotEmpty(),
                            onClick = {
                                val bmp = renderSignatureBitmap(
                                    strokes = strokes.toList(),
                                    srcW = canvasWidth,
                                    srcH = canvasHeight,
                                    colorArgb = inkColor.toArgb()
                                )
                                onSaveSignature(bmp)
                            }
                        ) {
                            Text("Stamp")
                        }
                    }
                }
            }
        }
    }
}

private fun renderSignatureBitmap(
    strokes: List<List<Offset>>,
    srcW: Float,
    srcH: Float,
    colorArgb: Int
): Bitmap {
    val outW = 900
    val outH = (outW * (srcH / srcW.coerceAtLeast(1f))).toInt().coerceAtLeast(300)
    val scaleX = outW / srcW.coerceAtLeast(1f)
    val scaleY = outH / srcH.coerceAtLeast(1f)

    val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = colorArgb
        style = AndroidPaint.Style.STROKE
        strokeWidth = 10f
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
    }

    strokes.forEach { pts ->
        if (pts.size >= 2) {
            val path = AndroidPath().apply {
                moveTo(pts.first().x * scaleX, pts.first().y * scaleY)
                pts.drop(1).forEach { lineTo(it.x * scaleX, it.y * scaleY) }
            }
            canvas.drawPath(path, paint)
        }
    }
    return bitmap
}
