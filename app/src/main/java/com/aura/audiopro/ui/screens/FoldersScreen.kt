package com.aura.audiopro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.audiopro.core.LyricsAndMetadataHelper
import com.aura.audiopro.model.Song
import com.aura.audiopro.ui.components.ProIconBox
import com.aura.audiopro.ui.components.bounceClick
import com.aura.audiopro.ui.viewmodel.AuraViewModel

@Composable
fun FoldersScreen(
    vm: AuraViewModel,
    accent: Color,
    onOpenSettings: () -> Unit,
    onOpenFolderSongs: (String, String, List<Song>) -> Unit
) {
    val allSongs by vm.allSongs.collectAsState()
    val excludedFolders by vm.excludedFolders.collectAsState()
    val isScanning by vm.isScanning.collectAsState()

    var filterText by remember { mutableStateOf("") }
    var showDuplicatesDialog by remember { mutableStateOf(false) }
    var showExcludedDialog by remember { mutableStateOf(false) }

    val folders = remember(allSongs, filterText) {
        allSongs.groupBy { it.folderPath }
            .entries
            .filter {
                filterText.isBlank() ||
                    it.key.contains(filterText, ignoreCase = true) ||
                    it.value.any { s -> s.filePath.contains(filterText, ignoreCase = true) }
            }
            .sortedBy { it.key.lowercase() }
    }

    val duplicates = remember(allSongs) {
        LyricsAndMetadataHelper.findDuplicateGroups(allSongs)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Folder",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${folders.size} active folders • ${excludedFolders.size} hidden",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .bounceClick(scaleDown = 0.90f) { showDuplicatesDialog = true }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.FileCopy, contentDescription = "Duplicates", tint = accent, modifier = Modifier.size(19.dp))
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .bounceClick(scaleDown = 0.90f) { showExcludedDialog = true }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.RuleFolder, contentDescription = "Hidden Folders", tint = accent, modifier = Modifier.size(20.dp))
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .bounceClick(scaleDown = 0.90f, onClick = onOpenSettings)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        OutlinedTextField(
            value = filterText,
            onValueChange = { filterText = it },
            placeholder = { Text("Search by folder or filename...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = accent) },
            trailingIcon = {
                if (filterText.isNotEmpty()) {
                    IconButton(onClick = { filterText = "" }) {
                        Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedBorderColor = accent,
                unfocusedBorderColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        PullToRefreshBox(
            isRefreshing = isScanning,
            onRefresh = { vm.rescanLibrary() },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(folders, key = { it.key }) { entry ->
                    val folderName = entry.value.firstOrNull()?.folderName ?: "Music"
                    val totalBytes = entry.value.sumOf { it.size }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceClick(scaleDown = 0.97f) {
                                onOpenFolderSongs(folderName, entry.key, entry.value)
                            }
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProIconBox(icon = Icons.Rounded.Folder, tint = accent)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(folderName, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                            Text(
                                "${entry.value.size} songs • ${LyricsAndMetadataHelper.formatFileSize(totalBytes)}",
                                fontSize = 12.sp,
                                color = accent,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                entry.key,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { vm.excludeFolder(entry.key) }) {
                            Icon(
                                imageVector = Icons.Rounded.VisibilityOff,
                                contentDescription = "Hide Folder",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showExcludedDialog) {
        var manualPath by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showExcludedDialog = false },
            title = { Text("Hidden Folders (${excludedFolders.size})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (excludedFolders.isEmpty()) {
                        Text("No folders are currently hidden.", fontSize = 13.sp)
                    } else {
                        excludedFolders.forEach { path ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(path, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                TextButton(onClick = { vm.includeFolder(path) }) {
                                    Text("Restore", color = accent, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = manualPath,
                        onValueChange = { manualPath = it },
                        placeholder = { Text("/storage/emulated/0/WhatsApp/Media") },
                        singleLine = true
                    )
                    if (manualPath.isNotBlank()) {
                        Button(
                            onClick = {
                                vm.excludeFolder(manualPath.trim())
                                manualPath = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                        ) {
                            Text("Hide Path", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExcludedDialog = false }) { Text("Done") }
            }
        )
    }

    if (showDuplicatesDialog) {
        AlertDialog(
            onDismissRequest = { showDuplicatesDialog = false },
            title = { Text("Duplicate Tracks (${duplicates.size} groups)") },
            text = {
                if (duplicates.isEmpty()) {
                    Text("No duplicate audio tracks detected in your library.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(duplicates) { group ->
                            val first = group.songs.first()
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Text("${first.title} – ${first.artist}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                group.songs.forEach { s ->
                                    Text("• ${s.filePath}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDuplicatesDialog = false }) { Text("Close") }
            }
        )
    }
}
