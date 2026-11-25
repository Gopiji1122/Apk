package com.aurapdf.editor.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurapdf.editor.data.PdfStorage
import com.aurapdf.editor.engine.CreatorElement
import com.aurapdf.editor.engine.PdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorScreen(
    storage: PdfStorage,
    onBack: () -> Unit,
    onPdfCreated: (File) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var fileName by remember { mutableStateOf("My_Document") }
    var author by remember { mutableStateOf("") }
    var pageSize by remember { mutableStateOf("A4") }
    var landscape by remember { mutableStateOf(false) }
    var addPageNumbers by remember { mutableStateOf(true) }
    var pageCount by remember { mutableIntStateOf(1) }
    var activePage by remember { mutableIntStateOf(0) }
    var elements by remember { mutableStateOf(listOf<CreatorElement>()) }

    // Text builder inputs
    var textInput by remember { mutableStateOf("") }
    var fontSize by remember { mutableFloatStateOf(14f) }
    var isBold by remember { mutableStateOf(false) }
    var linkUrl by remember { mutableStateOf("") }
    var colorHex by remember { mutableIntStateOf(0x000000) }
    var xPct by remember { mutableFloatStateOf(0.08f) }
    var yPct by remember { mutableFloatStateOf(0.12f) }
    var isSaving by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            elements = elements + CreatorElement(
                pageIndex = activePage,
                isImage = true,
                imageUri = uri,
                xPercent = xPct,
                yPercent = yPct,
                widthPercent = 0.72f
            )
            yPct = (yPct + 0.35f).coerceAtMost(0.85f)
            statusMsg = "Added image to Page ${activePage + 1}"
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Create PDF Studio", fontWeight = FontWeight.SemiBold, fontSize = 18.sp) },
                actions = {
                    TextButton(
                        enabled = !isSaving,
                        onClick = {
                            scope.launch {
                                isSaving = true
                                try {
                                    val out = storage.newOutputFile(fileName)
                                    withContext(Dispatchers.IO) {
                                        PdfEngine.createCustomPdf(
                                            context = context,
                                            outputFile = out,
                                            pageCount = pageCount,
                                            pageSizeName = pageSize,
                                            landscape = landscape,
                                            addPageNumbers = addPageNumbers,
                                            titleMeta = fileName,
                                            authorMeta = author,
                                            elements = elements
                                        )
                                    }
                                    isSaving = false
                                    onPdfCreated(out)
                                } catch (e: Exception) {
                                    isSaving = false
                                    statusMsg = "Error: ${e.message}"
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isSaving) "Saving..." else "Export")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (statusMsg != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(statusMsg!!, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Document Setup", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        OutlinedTextField(
                            value = fileName,
                            onValueChange = { fileName = it },
                            label = { Text("PDF File Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = author,
                            onValueChange = { author = it },
                            label = { Text("Author (Optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("A4", "Letter", "Legal").forEach { sz ->
                                FilterChip(
                                    selected = pageSize == sz,
                                    onClick = { pageSize = sz },
                                    label = { Text(sz, fontSize = 12.sp) }
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Landscape Orientation", fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Switch(checked = landscape, onCheckedChange = { landscape = it })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Auto Page Numbers", fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Switch(checked = addPageNumbers, onCheckedChange = { addPageNumbers = it })
                        }
                    }
                }
            }

            // Page switcher
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Pages ($pageCount)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = {
                                pageCount++
                                activePage = pageCount - 1
                                yPct = 0.12f
                            }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add Blank Page", fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (p in 0 until pageCount) {
                                FilterChip(
                                    selected = activePage == p,
                                    onClick = { activePage = p },
                                    label = { Text("P${p + 1}", fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                }
            }

            // Add Text, Clickable Link, or JPG/PNG Image to current page
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Add Content to Page ${activePage + 1}", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)

                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            label = { Text("Write Text (Title, Paragraph, Notes)") },
                            minLines = 2,
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = linkUrl,
                            onValueChange = { linkUrl = it },
                            label = { Text("Attach Clickable Link URL (Optional)") },
                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Font Size: ${fontSize.toInt()} pt", fontSize = 12.sp)
                        Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 9f..32f)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(selected = isBold, onClick = { isBold = !isBold }, label = { Text("Bold", fontSize = 12.sp) })
                            listOf(
                                "Black" to 0x000000,
                                "Blue" to 0x1565C0,
                                "Red" to 0xC62828,
                                "Green" to 0x2E7D32
                            ).forEach { (lbl, hex) ->
                                FilterChip(
                                    selected = colorHex == hex,
                                    onClick = { colorHex = hex },
                                    label = { Text(lbl, fontSize = 11.sp) }
                                )
                            }
                        }

                        Text("Vertical Position on Page: ${(yPct * 100).toInt()}%", fontSize = 12.sp)
                        Slider(value = yPct, onValueChange = { yPct = it }, valueRange = 0.06f..0.90f)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    if (textInput.isNotBlank()) {
                                        elements = elements + CreatorElement(
                                            pageIndex = activePage,
                                            isImage = false,
                                            text = textInput,
                                            fontSize = fontSize,
                                            isBold = isBold,
                                            colorHex = colorHex,
                                            linkUrl = linkUrl.trim(),
                                            xPercent = xPct,
                                            yPercent = yPct
                                        )
                                        textInput = ""
                                        linkUrl = ""
                                        yPct = (yPct + 0.09f).coerceAtMost(0.90f)
                                        statusMsg = "Added text to Page ${activePage + 1}"
                                    }
                                },
                                enabled = textInput.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add Text")
                            }

                            OutlinedButton(
                                onClick = { imagePicker.launch(arrayOf("image/jpeg", "image/png", "image/*")) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add JPG/PNG")
                            }
                        }
                    }
                }
            }

            // Elements list on active page
            val pageEls = elements.filter { it.pageIndex == activePage }
            item {
                Text(
                    "Items on Page ${activePage + 1} (${pageEls.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(pageEls, key = { it.id }) { el ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (el.isImage) "Image (JPG/PNG)" else el.text.take(60),
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                            Text(
                                text = buildString {
                                    append("Y: ${(el.yPercent * 100).toInt()}%")
                                    if (el.linkUrl.isNotBlank()) append(" • Link: ${el.linkUrl}")
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { elements = elements.filterNot { it.id == el.id } }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Remove", tint = Color(0xFFEF5350))
                        }
                    }
                }
            }
        }
    }
}
