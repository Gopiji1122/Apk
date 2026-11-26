package com.aurapdf.editor.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aurapdf.editor.BuildConfig
import com.aurapdf.editor.util.FileUtils

@Composable
fun SplitPdfDialog(
    totalPages: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var startText by remember { mutableStateOf("1") }
    var endText by remember { mutableStateOf(totalPages.coerceAtLeast(1).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Split / Extract Pages") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Total pages: $totalPages", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it.filter(Char::isDigit) },
                    label = { Text("Start Page") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it.filter(Char::isDigit) },
                    label = { Text("End Page") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val s = startText.toIntOrNull() ?: 1
                val e = endText.toIntOrNull() ?: totalPages
                onConfirm(s, e)
            }) { Text("Extract") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun PasswordDialog(
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                enabled = password.isNotBlank(),
                onClick = { onConfirm(password) }
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun WatermarkDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Float) -> Unit
) {
    var text by remember { mutableStateOf("CONFIDENTIAL") }
    var opacity by remember { mutableFloatStateOf(0.22f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Diagonal Watermark") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Watermark Text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Opacity: ${(opacity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0.1f..0.6f)
            }
        },
        confirmButton = {
            Button(
                enabled = text.isNotBlank(),
                onClick = { onConfirm(text, opacity) }
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun CompressDialog(
    onDismiss: () -> Unit,
    onConfirm: (quality: Float, scale: Float) -> Unit
) {
    var selectedLevel by remember { mutableIntStateOf(1) }
    val labels = listOf(
        "Strong (Smallest file)",
        "Balanced (Recommended)",
        "Light (Best clarity)"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compress PDF Offline") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                labels.forEachIndexed { idx, label ->
                    FilterChip(
                        selected = selectedLevel == idx,
                        onClick = { selectedLevel = idx },
                        label = { Text(label) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                when (selectedLevel) {
                    0 -> onConfirm(0.40f, 0.70f)
                    1 -> onConfirm(0.65f, 0.85f)
                    else -> onConfirm(0.82f, 1.0f)
                }
            }) { Text("Compress") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun OcrResultDialog(
    extractedText: String,
    onDismiss: () -> Unit,
    onCopied: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Extracted Text (Offline OCR)", fontWeight = FontWeight.Bold) },
        text = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 340.dp)
            ) {
                Text(
                    text = extractedText.ifBlank { "No readable text detected." },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                enabled = extractedText.isNotBlank(),
                onClick = {
                    FileUtils.copyTextToClipboard(context, "Aura PDF OCR", extractedText)
                    onCopied()
                }
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy All")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun AboutAppDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aura PDF Editor", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "App ID: ${BuildConfig.APPLICATION_ID}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    "Privacy Policy: Aura PDF Editor processes 100% of your PDFs, scans, signatures, and OCR locally on your device. No files or personal data are ever uploaded to any server.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Open Source Credits: Apache PDFBox-Android (Apache 2.0), Google ML Kit, Jetpack Compose.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Close") }
        }
    )
}
