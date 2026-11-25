package com.aurapdf.editor.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aurapdf.editor.ui.components.PremiumToolCard
import com.aurapdf.editor.util.FileUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    recentFiles: List<File>,
    onOpenPdf: () -> Unit,
    onScanDoc: () -> Unit,
    onMergePdfs: () -> Unit,
    onImagesToPdf: () -> Unit,
    onPickForSplit: () -> Unit,
    onPickForCompress: () -> Unit,
    onPickForLock: () -> Unit,
    onPickForOcr: () -> Unit,
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
                title = {
                    Column {
                        Text("Aura PDF Editor", fontWeight = FontWeight.Bold)
                        Text(
                            "100% Offline • Private • Zero Lag",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenAbout) {
                        Icon(Icons.Rounded.Info, contentDescription = "About Aura PDF Editor")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PremiumToolCard(
                            title = "Open PDF",
                            subtitle = "Zero-lag reader",
                            icon = Icons.Rounded.PictureAsPdf,
                            accentColor = Color(0xFFE53935),
                            onClick = onOpenPdf,
                            modifier = Modifier.weight(1f)
                        )
                        PremiumToolCard(
                            title = "Scan Doc",
                            subtitle = "Auto-crop camera",
                            icon = Icons.Rounded.DocumentScanner,
                            accentColor = Color(0xFF1E88E5),
                            onClick = onScanDoc,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PremiumToolCard(
                            title = "Merge PDFs",
                            subtitle = "Combine multiple",
                            icon = Icons.Rounded.MergeType,
                            accentColor = Color(0xFF43A047),
                            onClick = onMergePdfs,
                            modifier = Modifier.weight(1f)
                        )
                        PremiumToolCard(
                            title = "Photos to PDF",
                            subtitle = "Images to document",
                            icon = Icons.Rounded.PhotoLibrary,
                            accentColor = Color(0xFF8E24AA),
                            onClick = onImagesToPdf,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PremiumToolCard(
                            title = "Split & Pages",
                            subtitle = "Extract page range",
                            icon = Icons.Rounded.CallSplit,
                            accentColor = Color(0xFFFB8C00),
                            onClick = onPickForSplit,
                            modifier = Modifier.weight(1f)
                        )
                        PremiumToolCard(
                            title = "Compress PDF",
                            subtitle = "Shrink file size",
                            icon = Icons.Rounded.Compress,
                            accentColor = Color(0xFF00897B),
                            onClick = onPickForCompress,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PremiumToolCard(
                            title = "Lock PDF",
                            subtitle = "AES-128 password",
                            icon = Icons.Rounded.Lock,
                            accentColor = Color(0xFF3949AB),
                            onClick = onPickForLock,
                            modifier = Modifier.weight(1f)
                        )
                        PremiumToolCard(
                            title = "Offline OCR",
                            subtitle = "Extract scan text",
                            icon = Icons.Rounded.TextSnippet,
                            accentColor = Color(0xFFD81B60),
                            onClick = onPickForOcr,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Recent Documents",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
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
                            Icon(
                                Icons.Rounded.PictureAsPdf,
                                contentDescription = null,
                                tint = Color(0xFFE53935),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = FileUtils.formatSize(file.length()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onShareFile(file) }) {
                                Icon(Icons.Rounded.Share, contentDescription = "Share")
                            }
                            IconButton(onClick = { onDeleteRecent(file) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}
