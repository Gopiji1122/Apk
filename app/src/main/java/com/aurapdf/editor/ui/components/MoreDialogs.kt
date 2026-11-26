package com.aurapdf.editor.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun PageNumberDialog(onDismiss: () -> Unit, onConfirm: (position: Int, style: Int) -> Unit) {
    var position by remember { mutableIntStateOf(1) }
    var style by remember { mutableIntStateOf(1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Page Numbers") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Position", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Left", "Center", "Right").forEachIndexed { i, label ->
                        FilterChip(selected = position == i, onClick = { position = i }, label = { Text(label) })
                    }
                }
                Text("Format", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("1", "Page 1", "1 of N").forEachIndexed { i, label ->
                        FilterChip(selected = style == i, onClick = { style = i }, label = { Text(label) })
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(position, style) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private val StampPresets = listOf(
    "APPROVED" to 0x2E7D32, "DRAFT" to 0x607D8B, "CONFIDENTIAL" to 0xC62828,
    "URGENT" to 0xEF6C00, "REVIEWED" to 0x1565C0, "FINAL" to 0x6A1B9A
)

@Composable
fun StampDialog(onDismiss: () -> Unit, onConfirm: (text: String, rgb: Int) -> Unit) {
    var selected by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stamp Pages") },
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                StampPresets.forEachIndexed { i, (label, _) ->
                    FilterChip(selected = selected == i, onClick = { selected = i }, label = { Text(label) })
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(StampPresets[selected].first, StampPresets[selected].second) }) { Text("Stamp") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun CropDialog(onDismiss: () -> Unit, onConfirm: (Float, Float, Float, Float) -> Unit) {
    var left by remember { mutableStateOf("20") }
    var top by remember { mutableStateOf("20") }
    var right by remember { mutableStateOf("20") }
    var bottom by remember { mutableStateOf("20") }
    val kb = KeyboardOptions(keyboardType = KeyboardType.Number)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crop / Trim Margins") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Points to trim from each side (72 pt = 1 inch)", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(left, { left = it.filter(Char::isDigit) }, label = { Text("Left") },
                        keyboardOptions = kb, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(right, { right = it.filter(Char::isDigit) }, label = { Text("Right") },
                        keyboardOptions = kb, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(top, { top = it.filter(Char::isDigit) }, label = { Text("Top") },
                        keyboardOptions = kb, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(bottom, { bottom = it.filter(Char::isDigit) }, label = { Text("Bottom") },
                        keyboardOptions = kb, singleLine = true, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(
                    left.toFloatOrNull() ?: 0f, top.toFloatOrNull() ?: 0f,
                    right.toFloatOrNull() ?: 0f, bottom.toFloatOrNull() ?: 0f
                )
            }) { Text("Crop") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun BlankPageDialog(totalPages: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var after by remember { mutableStateOf(totalPages.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert Blank Page") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Document has $totalPages pages. Use 0 to insert at the start.", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = after, onValueChange = { after = it.filter(Char::isDigit) },
                    label = { Text("Insert after page") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm((after.toIntOrNull() ?: totalPages).coerceIn(0, totalPages)) }) { Text("Insert") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
