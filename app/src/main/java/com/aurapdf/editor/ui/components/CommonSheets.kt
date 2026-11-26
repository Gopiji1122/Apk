package com.aurapdf.editor.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.aurapdf.editor.model.AppThemeMode
import com.aurapdf.editor.model.CompressConfig
import com.aurapdf.editor.model.CompressPreset
import com.aurapdf.editor.model.CompressionResult
import com.aurapdf.editor.model.DigitalCertInfo
import com.aurapdf.editor.model.ExportFormat
import com.aurapdf.editor.model.NormPoint
import com.aurapdf.editor.model.OverlayItem
import com.aurapdf.editor.model.PageOrientation
import com.aurapdf.editor.model.PageRangeMode
import com.aurapdf.editor.model.PageSizeOption
import com.aurapdf.editor.model.PdfFontFamily
import com.aurapdf.editor.model.PdfLinkInfo
import com.aurapdf.editor.model.RecentPdfItem
import com.aurapdf.editor.ui.theme.AcrobatRed
import com.aurapdf.editor.ui.theme.ToolCompressGreen
import com.aurapdf.editor.ui.theme.ToolSignPurple
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object ShareUtils {
    fun shareFile(context: Context, file: File, mimeType: String = "application/pdf") {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${file.name}"))
    }
}

val RichTextColors: List<Pair<String, Long>> = listOf(
    "Black" to 0xFF111111,
    "Acrobat Red" to 0xFFE52521,
    "Royal Blue" to 0xFF1E88E5,
    "Emerald" to 0xFF2E7D32,
    "Purple" to 0xFF8E24AA,
    "Orange" to 0xFFEF6C00,
    "Teal" to 0xFF00897B,
    "Navy" to 0xFF1A237E,
    "Slate Gray" to 0xFF546E7A,
    "Brown" to 0xFF5D4037,
    "Pink" to 0xFFD81B60,
    "White" to 0xFFFFFFFF
)

@Composable
fun CreateBlankPdfDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, size: PageSizeOption, orientation: PageOrientation) -> Unit
) {
    var title by remember { mutableStateOf("Blank_Document") }
    var selectedSize by remember { mutableStateOf(PageSizeOption.A4) }
    var selectedOrientation by remember { mutableStateOf(PageOrientation.PORTRAIT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Blank PDF", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Document Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Page Size", style = MaterialTheme.typography.labelLarge)
                PageSizeOption.entries.forEach { size ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedSize = size }
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = selectedSize == size,
                            onClick = { selectedSize = size }
                        )
                        Text(size.label, modifier = Modifier.padding(start = 6.dp))
                    }
                }
                Text("Orientation", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PageOrientation.entries.forEach { orient ->
                        FilterChip(
                            selected = selectedOrientation == orient,
                            onClick = { selectedOrientation = orient },
                            label = { Text(orient.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(title, selectedSize, selectedOrientation) },
                colors = ButtonDefaults.buttonColors(containerColor = AcrobatRed)
            ) {
                Text("Create PDF", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ThemeSwitcherDialog(
    currentMode: AppThemeMode,
    onSelect: (AppThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Appearance Theme", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppThemeMode.entries.forEach { mode ->
                    Card(
                        onClick = { onSelect(mode) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (mode == currentMode)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            RadioButton(
                                selected = mode == currentMode,
                                onClick = { onSelect(mode) }
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(mode.displayName, fontWeight = FontWeight.SemiBold)
                                Text(
                                    mode.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
fun MergePdfsDialog(
    availablePdfs: List<RecentPdfItem>,
    onPickFromStorage: () -> Unit,
    onMerge: (selectedPaths: List<String>, outputTitle: String) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedPaths = remember { mutableStateListOf<String>() }
    var outputTitle by remember { mutableStateOf("Combined_Document") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Combine / Merge PDFs", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = outputTitle,
                    onValueChange = { outputTitle = it },
                    label = { Text("Merged Output Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = onPickFromStorage,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("+ Import PDF from Device Storage")
                }
                Text(
                    "Select 2 or more PDFs in merge order (${selectedPaths.size} selected):",
                    style = MaterialTheme.typography.labelMedium
                )
                if (availablePdfs.isEmpty()) {
                    Text(
                        "No recent PDFs yet. Import PDFs from storage first.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    LazyColumn(modifier = Modifier.height(210.dp)) {
                        items(availablePdfs, key = { it.filePath }) { item ->
                            val idx = selectedPaths.indexOf(item.filePath)
                            val checked = idx >= 0
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (checked) selectedPaths.remove(item.filePath)
                                        else selectedPaths.add(item.filePath)
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        if (it) selectedPaths.add(item.filePath)
                                        else selectedPaths.remove(item.filePath)
                                    }
                                )
                                Text(
                                    text = if (checked) "#${idx + 1}  ${item.name}" else item.name,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onMerge(selectedPaths.toList(), outputTitle) },
                enabled = selectedPaths.size >= 2,
                colors = ButtonDefaults.buttonColors(containerColor = AcrobatRed)
            ) {
                Text("Merge (${selectedPaths.size})", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CompressPdfSheet(
    file: File,
    lastResult: CompressionResult?,
    isWorking: Boolean,
    onRunCompress: (config: CompressConfig, replaceOriginal: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var preset by remember { mutableStateOf(CompressPreset.BALANCED) }
    var quality by remember { mutableFloatStateOf(72f) }
    var dpi by remember { mutableFloatStateOf(150f) }
    var grayscale by remember { mutableStateOf(false) }
    var stripMetadata by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Compress PDF",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Original Size: ${Formatter.formatShortFileSize(context, file.length())}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("Quick Presets", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    CompressPreset.EXTREME,
                    CompressPreset.HIGH,
                    CompressPreset.BALANCED,
                    CompressPreset.LOW
                ).forEach { p ->
                    FilterChip(
                        selected = preset == p,
                        onClick = {
                            preset = p
                            quality = p.quality.toFloat()
                            dpi = p.dpi.toFloat()
                        },
                        label = { Text(p.label) }
                    )
                }
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("JPEG Image Quality", style = MaterialTheme.typography.labelLarge)
                    Text("${quality.roundToInt()}%", fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = quality,
                    onValueChange = {
                        quality = it
                        preset = CompressPreset.CUSTOM
                    },
                    valueRange = 15f..95f
                )
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Target Resolution (DPI)", style = MaterialTheme.typography.labelLarge)
                    Text("${dpi.roundToInt()} DPI", fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = dpi,
                    onValueChange = {
                        dpi = it
                        preset = CompressPreset.CUSTOM
                    },
                    valueRange = 72f..300f
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Convert to Grayscale")
                Switch(checked = grayscale, onCheckedChange = { grayscale = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Strip Metadata & Links")
                Switch(checked = stripMetadata, onCheckedChange = { stripMetadata = it })
            }

            if (lastResult != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = ToolCompressGreen.copy(alpha = 0.16f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Compression Complete!",
                            fontWeight = FontWeight.Bold,
                            color = ToolCompressGreen
                        )
                        Text(
                            "Before: ${Formatter.formatShortFileSize(context, lastResult.originalBytes)}  →  " +
                                "After: ${Formatter.formatShortFileSize(context, lastResult.compressedBytes)} " +
                                "(${String.format(Locale.US, "%.1f", lastResult.reductionPercent)}% reduction)"
                        )
                    }
                }
            }

            val config = CompressConfig(
                preset = preset,
                jpegQuality = quality.roundToInt(),
                targetDpi = dpi.roundToInt(),
                grayscale = grayscale,
                stripMetadata = stripMetadata
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onRunCompress(config, true) },
                    enabled = !isWorking,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isWorking) "Compressing..." else "Replace Original")
                }
                Button(
                    onClick = { onRunCompress(config, false) },
                    enabled = !isWorking,
                    colors = ButtonDefaults.buttonColors(containerColor = ToolCompressGreen),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save New Copy", color = Color.White)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExportPdfSheet(
    totalPages: Int,
    currentPage: Int,
    isWorking: Boolean,
    onExport: (
        rangeMode: PageRangeMode,
        customRange: String,
        format: ExportFormat,
        jpegQuality: Int,
        dpi: Int,
        bundleAsZip: Boolean,
        saveViaSaf: Boolean
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var rangeMode by remember { mutableStateOf(PageRangeMode.ALL) }
    var customRange by remember { mutableStateOf("1-$totalPages") }
    var format by remember { mutableStateOf(ExportFormat.PNG) }
    var jpegQuality by remember { mutableFloatStateOf(90f) }
    var dpi by remember { mutableFloatStateOf(150f) }
    var bundleAsZip by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Export PDF",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text("Pages to Export", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PageRangeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = rangeMode == mode,
                        onClick = { rangeMode = mode },
                        label = {
                            Text(
                                if (mode == PageRangeMode.CURRENT) "Page ${currentPage + 1}" else mode.label
                            )
                        }
                    )
                }
            }
            if (rangeMode == PageRangeMode.CUSTOM) {
                OutlinedTextField(
                    value = customRange,
                    onValueChange = { customRange = it },
                    label = { Text("Page Range (e.g. 1-3, 5)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text("Export Format", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ExportFormat.entries.forEach { fmt ->
                    FilterChip(
                        selected = format == fmt,
                        onClick = { format = fmt },
                        label = { Text(fmt.label) }
                    )
                }
            }

            if (format == ExportFormat.PNG || format == ExportFormat.JPEG) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Resolution (DPI)")
                    Text("${dpi.roundToInt()} DPI", fontWeight = FontWeight.Bold)
                }
                Slider(value = dpi, onValueChange = { dpi = it }, valueRange = 72f..300f)

                if (format == ExportFormat.JPEG) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("JPEG Quality")
                        Text("${jpegQuality.roundToInt()}%", fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = jpegQuality,
                        onValueChange = { jpegQuality = it },
                        valueRange = 40f..100f
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Bundle Multiple Pages as ZIP")
                    Switch(checked = bundleAsZip, onCheckedChange = { bundleAsZip = it })
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onExport(
                            rangeMode,
                            customRange,
                            format,
                            jpegQuality.roundToInt(),
                            dpi.roundToInt(),
                            bundleAsZip,
                            false
                        )
                    },
                    enabled = !isWorking,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Export & Share")
                }
                Button(
                    onClick = {
                        onExport(
                            rangeMode,
                            customRange,
                            format,
                            jpegQuality.roundToInt(),
                            dpi.roundToInt(),
                            bundleAsZip,
                            true
                        )
                    },
                    enabled = !isWorking,
                    colors = ButtonDefaults.buttonColors(containerColor = AcrobatRed),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isWorking) "Exporting..." else "Save to Device", color = Color.White)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun PasswordProtectionDialog(
    isCurrentlyEncrypted: Boolean,
    onSetPassword: (String) -> Unit,
    onRemovePassword: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (isCurrentlyEncrypted) Icons.Default.LockOpen else Icons.Default.Lock,
                contentDescription = null,
                tint = AcrobatRed
            )
        },
        title = {
            Text(
                if (isCurrentlyEncrypted) "Unlock / Remove PDF Password" else "Set 128-Bit PDF Password",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (isCurrentlyEncrypted) "Current Password" else "New Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!isCurrentlyEncrypted) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isCurrentlyEncrypted) onRemovePassword(password)
                    else onSetPassword(password)
                },
                enabled = password.isNotBlank() && (isCurrentlyEncrypted || password == confirmPassword),
                colors = ButtonDefaults.buttonColors(containerColor = AcrobatRed)
            ) {
                Text(if (isCurrentlyEncrypted) "Remove Lock" else "Encrypt PDF", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun WatermarkDialog(
    currentPage: Int,
    onApply: (text: String, size: Float, opacity: Float, angle: Float, colorArgb: Long, allPages: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("CONFIDENTIAL") }
    var fontSize by remember { mutableFloatStateOf(48f) }
    var opacity by remember { mutableFloatStateOf(0.28f) }
    var angle by remember { mutableFloatStateOf(40f) }
    var colorArgb by remember { mutableLongStateOf(0xFFE52521) }
    var allPages by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Watermark", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Watermark Text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Font Size: ${fontSize.roundToInt()} pt")
                Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 18f..96f)

                Text("Opacity: ${(opacity * 100).roundToInt()}%")
                Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0.08f..0.80f)

                Text("Angle: ${angle.roundToInt()}°")
                Slider(value = angle, onValueChange = { angle = it }, valueRange = -60f..60f)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RichTextColors.take(6).forEach { (_, argb) ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(
                                    width = if (colorArgb == argb) 3.dp else 1.dp,
                                    color = if (colorArgb == argb) AcrobatRed else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable { colorArgb = argb }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (allPages) "Apply to All Pages" else "Current Page (${currentPage + 1}) Only")
                    Switch(checked = allPages, onCheckedChange = { allPages = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(text, fontSize, opacity, angle, colorArgb, allPages) },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AcrobatRed)
            ) {
                Text("Apply Watermark", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun DocumentPropertiesDialog(
    initialProps: Map<String, String>,
    onSave: (title: String, author: String, subject: String, keywords: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initialProps["title"] ?: "") }
    var author by remember { mutableStateOf(initialProps["author"] ?: "") }
    var subject by remember { mutableStateOf(initialProps["subject"] ?: "") }
    var keywords by remember { mutableStateOf(initialProps["keywords"] ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Document Properties", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                OutlinedTextField(value = author, onValueChange = { author = it }, label = { Text("Author") })
                OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject") })
                OutlinedTextField(value = keywords, onValueChange = { keywords = it }, label = { Text("Keywords") })
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, author, subject, keywords) },
                colors = ButtonDefaults.buttonColors(containerColor = AcrobatRed)
            ) {
                Text("Save Metadata", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinksManagerSheet(
    links: List<PdfLinkInfo>,
    currentPage: Int,
    onAddLink: (pageIndex: Int, uri: String) -> Unit,
    onRemoveLink: (PdfLinkInfo) -> Unit,
    onRemoveAllLinks: () -> Unit,
    onDismiss: () -> Unit
) {
    var newUrl by remember { mutableStateOf("https://") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Links Manager (${links.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (links.isNotEmpty()) {
                    TextButton(onClick = onRemoveAllLinks) {
                        Icon(Icons.Default.LinkOff, contentDescription = null, tint = AcrobatRed)
                        Spacer(Modifier.width(4.dp))
                        Text("Remove All", color = AcrobatRed)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newUrl,
                    onValueChange = { newUrl = it },
                    label = { Text("Add URL to Page ${currentPage + 1}") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        if (newUrl.isNotBlank()) {
                            onAddLink(currentPage, newUrl)
                            newUrl = "https://"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AcrobatRed)
                ) {
                    Text("Add", color = Color.White)
                }
            }

            HorizontalDivider()

            if (links.isEmpty()) {
                Text(
                    "No hyperlinks embedded in this PDF yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.height(220.dp)) {
                    items(links) { link ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(link.uri, fontWeight = FontWeight.Medium, maxLines = 1)
                                Text(link.rectSummary, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { onRemoveLink(link) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete link", tint = AcrobatRed)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
fun OcrResultDialog(
    pageIndex: Int,
    recognizedText: String,
    onInsertAsTextOverlay: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Offline OCR — Page ${pageIndex + 1}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = recognizedText,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("OCR Text", recognizedText))
                            Toast.makeText(context, "Copied OCR text to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy All")
                    }
                    if (onInsertAsTextOverlay != null) {
                        Button(
                            onClick = { onInsertAsTextOverlay(recognizedText) },
                            colors = ButtonDefaults.buttonColors(containerColor = AcrobatRed),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Insert Text", color = Color.White)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RichTextEditorDialog(
    initial: OverlayItem.TextOverlay?,
    pageIndex: Int,
    onConfirm: (OverlayItem.TextOverlay) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial?.text ?: "") }
    var fontSize by remember { mutableFloatStateOf(initial?.fontSizeSp ?: 16f) }
    var colorArgb by remember { mutableLongStateOf(initial?.colorArgb ?: 0xFF111111) }
    var fontFamily by remember { mutableStateOf(initial?.fontFamily ?: PdfFontFamily.SANS) }
    var isBold by remember { mutableStateOf(initial?.isBold ?: false) }
    var isItalic by remember { mutableStateOf(initial?.isItalic ?: false) }
    var isUnderline by remember { mutableStateOf(initial?.isUnderline ?: false) }
    var isStrikethrough by remember { mutableStateOf(initial?.isStrikethrough ?: false) }
    var hasHighlight by remember { mutableStateOf(initial?.highlightColorArgb != null) }
    var highlightColor by remember { mutableLongStateOf(initial?.highlightColorArgb ?: 0xFFFFEB3B) }
    var linkUrl by remember { mutableStateOf(initial?.linkUrl ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial == null) "Add Rich Text" else "Edit Text Overlay",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Text Content") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                // Font family selector
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PdfFontFamily.entries.forEach { fam ->
                        FilterChip(
                            selected = fontFamily == fam,
                            onClick = { fontFamily = fam },
                            label = { Text(fam.name) }
                        )
                    }
                }

                // Bold / Italic / Underline / Strikethrough toggles
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = isBold,
                        onClick = { isBold = !isBold },
                        label = { Icon(Icons.Default.FormatBold, contentDescription = "Bold") }
                    )
                    FilterChip(
                        selected = isItalic,
                        onClick = { isItalic = !isItalic },
                        label = { Icon(Icons.Default.FormatItalic, contentDescription = "Italic") }
                    )
                    FilterChip(
                        selected = isUnderline,
                        onClick = { isUnderline = !isUnderline },
                        label = { Icon(Icons.Default.FormatUnderlined, contentDescription = "Underline") }
                    )
                    FilterChip(
                        selected = isStrikethrough,
                        onClick = { isStrikethrough = !isStrikethrough },
                        label = { Icon(Icons.Default.FormatStrikethrough, contentDescription = "Strike") }
                    )
                }

                // Font Size Slider
                Text("Font Size: ${fontSize.roundToInt()} pt", style = MaterialTheme.typography.labelLarge)
                Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 8f..54f)

                // 12 Color Swatches
                Text("Text Color", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RichTextColors.forEach { (_, argb) ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(
                                    width = if (colorArgb == argb) 3.dp else 1.dp,
                                    color = if (colorArgb == argb) AcrobatRed else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable { colorArgb = argb }
                        )
                    }
                }

                // Highlight Background Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Highlight Background")
                    Switch(checked = hasHighlight, onCheckedChange = { hasHighlight = it })
                }
                if (hasHighlight) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0xFFFFEB3B, 0xFFA5D6A7, 0xFF90CAF9, 0xFFF48FB1).forEach { bgArgb ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(bgArgb))
                                    .border(
                                        width = if (highlightColor == bgArgb) 3.dp else 1.dp,
                                        color = if (highlightColor == bgArgb) AcrobatRed else Color.Gray,
                                        shape = CircleShape
                                    )
                                    .clickable { highlightColor = bgArgb }
                            )
                        }
                    }
                }

                // Optional Clickable Hyperlink URL
                OutlinedTextField(
                    value = linkUrl,
                    onValueChange = { linkUrl = it },
                    label = { Text("Optional Hyperlink URL") },
                    placeholder = { Text("https://example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val item = (initial ?: OverlayItem.TextOverlay(pageIndex = pageIndex, text = text)).copy(
                        text = text,
                        fontSizeSp = fontSize,
                        colorArgb = colorArgb,
                        fontFamily = fontFamily,
                        isBold = isBold,
                        isItalic = isItalic,
                        isUnderline = isUnderline,
                        isStrikethrough = isStrikethrough,
                        highlightColorArgb = if (hasHighlight) highlightColor else null,
                        linkUrl = linkUrl.trim().takeIf { it.isNotEmpty() }
                    )
                    onConfirm(item)
                },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AcrobatRed)
            ) {
                Text("Apply", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SignaturePadDialog(
    pageIndex: Int,
    onConfirm: (OverlayItem.SignatureOverlay) -> Unit,
    onDismiss: () -> Unit
) {
    val completedStrokes = remember { mutableStateListOf<List<NormPoint>>() }
    var currentStroke by remember { mutableStateOf<List<NormPoint>>(emptyList()) }
    var inkColorArgb by remember { mutableLongStateOf(0xFF111111) }
    var includeDigitalCert by remember { mutableStateOf(false) }
    var signerName by remember { mutableStateOf("Authorized Signer") }
    var reason by remember { mutableStateOf("Document Approval") }
    var location by remember { mutableStateOf("Verified Offline Device") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hand & Digital Signature", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Ink colors: Black, Blue, Red
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Ink:", style = MaterialTheme.typography.labelLarge)
                    listOf(
                        "Black" to 0xFF111111,
                        "Blue" to 0xFF1565C0,
                        "Red" to 0xFFE52521
                    ).forEach { (_, argb) ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(
                                    width = if (inkColorArgb == argb) 3.dp else 1.dp,
                                    color = if (inkColorArgb == argb) AcrobatRed else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable { inkColorArgb = argb }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        completedStrokes.clear()
                        currentStroke = emptyList()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Clear")
                    }
                }

                // Drawing Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                ) {
                    Canvas(
                        modifier = Modifier
                            .matchParentSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val nx = (offset.x / size.width).coerceIn(0f, 1f)
                                        val ny = (offset.y / size.height).coerceIn(0f, 1f)
                                        currentStroke = listOf(NormPoint(nx, ny))
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val nx = (change.position.x / size.width).coerceIn(0f, 1f)
                                        val ny = (change.position.y / size.height).coerceIn(0f, 1f)
                                        currentStroke = currentStroke + NormPoint(nx, ny)
                                    },
                                    onDragEnd = {
                                        if (currentStroke.size >= 2) {
                                            completedStrokes.add(currentStroke)
                                        }
                                        currentStroke = emptyList()
                                    }
                                )
                            }
                    ) {
                        val all = completedStrokes.toList() + listOf(currentStroke)
                        for (stroke in all) {
                            if (stroke.size < 2) continue
                            val path = Path()
                            path.moveTo(stroke[0].x * size.width, stroke[0].y * size.height)
                            for (pt in stroke.drop(1)) {
                                path.lineTo(pt.x * size.width, pt.y * size.height)
                            }
                            drawPath(
                                path = path,
                                color = Color(inkColorArgb),
                                style = Stroke(
                                    width = 6f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                    if (completedStrokes.isEmpty() && currentStroke.isEmpty()) {
                        Text(
                            "Draw signature here",
                            color = Color.Gray,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                // Digital Certificate Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = ToolSignPurple)
                        Spacer(Modifier.width(6.dp))
                        Text("Attach Digital Certificate")
                    }
                    Switch(checked = includeDigitalCert, onCheckedChange = { includeDigitalCert = it })
                }

                if (includeDigitalCert) {
                    OutlinedTextField(
                        value = signerName,
                        onValueChange = { signerName = it },
                        label = { Text("Signer Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("Location") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cert = if (includeDigitalCert) {
                        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US).format(Date())
                        val payload = "$signerName|$reason|$location|$ts|${completedStrokes.hashCode()}"
                        val digest = MessageDigest.getInstance("SHA-256")
                            .digest(payload.toByteArray())
                            .joinToString("") { "%02x".format(it) }
                        DigitalCertInfo(
                            signerName = signerName.ifBlank { "Authorized Signer" },
                            reason = reason.ifBlank { "Approved" },
                            location = location.ifBlank { "Offline" },
                            timestamp = ts,
                            sha256Hash = digest
                        )
                    } else null

                    onConfirm(
                        OverlayItem.SignatureOverlay(
                            pageIndex = pageIndex,
                            strokes = completedStrokes.toList(),
                            inkColorArgb = inkColorArgb,
                            digitalCertInfo = cert,
                            hNorm = if (cert != null) 0.16f else 0.11f
                        )
                    )
                },
                enabled = completedStrokes.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = ToolSignPurple)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(4.dp))
                Text("Place Signature", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
