package com.aurapdf.editor.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aurapdf.editor.ui.components.PremiumToolCard
import com.aurapdf.editor.util.FileUtils
import java.io.File

enum class HomeTool {
    OPEN, SCAN, MERGE, IMAGES, OFFICE, SPLIT, BLANK, CROP,
    PAGE_NUMBERS, STAMP, FLATTEN, LOCK, COMPRESS, OCR, TEXT_EXPORT
}

private data class ToolItem(val tool: HomeTool, val title: String, val sub: String, val icon: ImageVector, val color: Color)
private data class ToolSection(val title: String, val items: List<ToolItem>)

private val sections = listOf(
    ToolSection("Create", listOf(
        ToolItem(HomeTool.SCAN, "Scan Doc", "Auto-crop camera", Icons.Rounded.DocumentScanner, Color(0xFF1E88E5)),
        ToolItem(HomeTool.IMAGES, "Photos to PDF", "Images to document", Icons.Rounded.PhotoLibrary, Color(0xFF8E24AA)),
        ToolItem(HomeTool.OFFICE, "Word / Excel / TXT", "Convert to PDF", Icons.Rounded.Description, Color(0xFF3949AB))
    )),
    ToolSection("Organize", listOf(
        ToolItem(HomeTool.MERGE, "Merge PDFs", "Combine multiple", Icons.Rounded.MergeType, Color(0xFF43A047)),
        ToolItem(HomeTool.SPLIT, "Split & Extract", "Pick page range", Icons.Rounded.CallSplit, Color(0xFFFB8C00)),
        ToolItem(HomeTool.BLANK, "Blank Page", "Insert anywhere", Icons.Rounded.NoteAdd, Color(0xFF00ACC1)),
        ToolItem(HomeTool.CROP, "Crop Pages", "Trim margins", Icons.Rounded.Crop, Color(0xFF6D4C41))
    )),
    ToolSection("Edit & Mark up", listOf(
        ToolItem(HomeTool.PAGE_NUMBERS, "Page Numbers", "Footer numbering", Icons.Rounded.FormatListNumbered, Color(0xFF5E35B1)),
        ToolItem(HomeTool.STAMP, "Stamps", "Approved, Draft...", Icons.Rounded.Approval, Color(0xFFD81B60)),
        ToolItem(HomeTool.FLATTEN, "Flatten Forms", "Lock filled fields", Icons.Rounded.Layers, Color(0xFF00897B))
    )),
    ToolSection("Protect & Optimize", listOf(
        ToolItem(HomeTool.LOCK, "Lock PDF", "AES-128 password", Icons.Rounded.Lock, Color(0xFF3949AB)),
        ToolItem(HomeTool.COMPRESS, "Compress PDF", "Shrink file size", Icons.Rounded.Compress, Color(0xFF00897B))
    )),
    ToolSection("Extract", listOf(
        ToolItem(HomeTool.OCR, "Offline OCR", "Text from scans", Icons.Rounded.TextSnippet, Color(0xFFD81B60)),
        ToolItem(HomeTool.TEXT_EXPORT, "Export Text", "PDF to .txt", Icons.Rounded.Notes, Color(0xFFEF6C00))
    ))
)

@Composable
private fun HeroCard(onOpen: () -> Unit, onScan: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(Color(0xFFC62828), Color(0xFF7B1FA2))),
                RoundedCornerShape(28.dp)
            )
            .padding(22.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Text("Your documents,", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text("fully in your hands.", style = MaterialTheme.typography.headlineMedium, color = Color.White.copy(alpha = 0.8f))
            }
            Text(
                "Scan, edit, sign and convert. 100% offline, nothing ever leaves your phone.",
                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onOpen,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFC62828)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open PDF")
                }
                OutlinedButton(
                    onClick = onScan,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Scan")
                }
            }
        }
    }
}

@Composable
private fun StaggeredCard(index: Int, item: ToolItem, onClick: () -> Unit, modifier: Modifier) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 40L)
        shown = true
    }
    Box(modifier = modifier.height(112.dp)) {
        AnimatedVisibility(visible = shown, enter = fadeIn() + slideInVertically { it / 5 }) {
            PremiumToolCard(
                title = item.title, subtitle = item.sub, icon = item.icon,
                accentColor = item.color, onClick = onClick
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    recentFiles: List<File>,
    onTool: (HomeTool) -> Unit,
    onOpenRecent: (File) -> Unit,
    onShareFile: (File) -> Unit,
    onDeleteRecent: (File) -> Unit,
    onOpenAbout: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredFiles = remember(recentFiles, searchQuery) {
        if (searchQuery.isBlank()) recentFiles
        else recentFiles.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aura", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onOpenAbout) {
                        Icon(Icons.Rounded.Info, contentDescription = "About Aura PDF Editor")
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
            item { HeroCard(onOpen = { onTool(HomeTool.OPEN) }, onScan = { onTool(HomeTool.SCAN) }) }

            var counter = 0
            sections.forEach { section ->
                item {
                    Text(
                        section.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                section.items.chunked(2).forEach { pair ->
                    val base = counter
                    counter += pair.size
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEachIndexed { k, tool ->
                                StaggeredCard(base + k, tool, { onTool(tool.tool) }, Modifier.weight(1f))
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Recent Documents", style = MaterialTheme.typography.titleMedium)
            }

            if (recentFiles.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search recent PDFs...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (filteredFiles.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No documents yet. Open, scan, or create a PDF above.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            } else {
                items(filteredFiles, key = { it.absolutePath }) { file ->
                    Surface(
                        onClick = { onOpenRecent(file) },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = FileUtils.formatSize(file.length()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onShareFile(file) }) { Icon(Icons.Rounded.Share, contentDescription = "Share") }
                            IconButton(onClick = { onDeleteRecent(file) }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete") }
                        }
                    }
                }
            }
        }
    }
}
