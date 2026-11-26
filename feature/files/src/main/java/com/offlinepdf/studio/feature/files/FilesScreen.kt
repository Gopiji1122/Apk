package com.offlinepdf.studio.feature.files

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinepdf.studio.core.common.Formatters
import com.offlinepdf.studio.core.database.DocumentEntity

@Composable
fun FilesScreen(
    onOpenDocument: (String) -> Unit,
    viewModel: FilesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var renameTarget by remember { mutableStateOf<DocumentEntity?>(null) }
    var renameInput by remember { mutableStateOf("") }

    val multiPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importDocuments(uris)
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { multiPicker.launch(arrayOf("application/pdf")) }
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Import PDF")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Files", style = MaterialTheme.typography.headlineMedium)
                IconButton(onClick = viewModel::toggleGridView) {
                    Icon(
                        imageVector = if (state.isGridView) Icons.Outlined.ViewList else Icons.Outlined.GridView,
                        contentDescription = "Toggle layout"
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::updateQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text("Search PDFs by filename...") }
            )

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.sortOrder == FileSortOrder.DATE_DESC,
                    onClick = { viewModel.updateSort(FileSortOrder.DATE_DESC) },
                    label = { Text("Recent") }
                )
                FilterChip(
                    selected = state.sortOrder == FileSortOrder.NAME_ASC,
                    onClick = { viewModel.updateSort(FileSortOrder.NAME_ASC) },
                    label = { Text("Name") }
                )
                FilterChip(
                    selected = state.sortOrder == FileSortOrder.SIZE_DESC,
                    onClick = { viewModel.updateSort(FileSortOrder.SIZE_DESC) },
                    label = { Text("Size") }
                )
                FilterChip(
                    selected = state.favoritesOnly,
                    onClick = viewModel::toggleFavoritesOnly,
                    label = { Text("Favorites") }
                )
            }

            Spacer(Modifier.height(12.dp))
            if (state.documents.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No matching PDF files", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Tap + to add PDFs using Android Storage Access Framework.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (state.isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 156.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(state.documents, key = { it.uriString }) { doc ->
                        FileCard(
                            doc = doc,
                            onClick = { onOpenDocument(doc.uriString) },
                            onFavorite = { viewModel.toggleFavorite(doc) },
                            onRename = {
                                renameTarget = doc
                                renameInput = doc.displayName
                            },
                            onShare = { sharePdf(context, doc.uriString) },
                            onRemove = { viewModel.removeFromLibrary(doc.uriString) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(state.documents, key = { it.uriString }) { doc ->
                        FileCard(
                            doc = doc,
                            onClick = { onOpenDocument(doc.uriString) },
                            onFavorite = { viewModel.toggleFavorite(doc) },
                            onRename = {
                                renameTarget = doc
                                renameInput = doc.displayName
                            },
                            onShare = { sharePdf(context, doc.uriString) },
                            onRemove = { viewModel.removeFromLibrary(doc.uriString) }
                        )
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Document") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    label = { Text("Filename") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rename(target.uriString, renameInput)
                    renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun FileCard(
    doc: DocumentEntity,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onRemove: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.PictureAsPdf,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${Formatters.formatFileSize(doc.sizeBytes)} • ${if (doc.pageCount > 0) "${doc.pageCount}p" else "PDF"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onFavorite) {
                Icon(
                    imageVector = if (doc.isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                    contentDescription = "Favorite"
                )
            }
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
                        onClick = { menuExpanded = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Outlined.Share, null) },
                        onClick = { menuExpanded = false; onShare() }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove from list") },
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
                        onClick = { menuExpanded = false; onRemove() }
                    )
                }
            }
        }
    }
}

private fun sharePdf(context: android.content.Context, uriString: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, Uri.parse(uriString))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share PDF"))
}
