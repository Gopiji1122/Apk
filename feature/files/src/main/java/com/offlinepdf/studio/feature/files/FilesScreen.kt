package com.offlinepdf.studio.feature.files

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinepdf.studio.core.common.Formatters
import com.offlinepdf.studio.core.database.DocumentEntity
import com.offlinepdf.studio.core.designsystem.R
import com.offlinepdf.studio.core.designsystem.components.DocumentThumb
import com.offlinepdf.studio.core.designsystem.components.EmptyState
import com.offlinepdf.studio.core.designsystem.components.ScreenHeader
import com.offlinepdf.studio.core.designsystem.components.StatusPill
import com.offlinepdf.studio.core.designsystem.components.PillTone
import com.offlinepdf.studio.core.designsystem.components.StudioCard
import com.offlinepdf.studio.core.designsystem.components.staggeredEntrance
import com.offlinepdf.studio.core.designsystem.theme.extras
import kotlinx.coroutines.launch

@Composable
fun FilesScreen(
    onOpenDocument: (String) -> Unit,
    viewModel: FilesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var renameTarget by remember { mutableStateOf<DocumentEntity?>(null) }
    var removeTarget by remember { mutableStateOf<DocumentEntity?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var sortMenu by remember { mutableStateOf(false) }

    val multiPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) viewModel.importDocuments(uris) }

    val loadThumb: suspend (String) -> android.graphics.Bitmap? = { viewModel.thumbnail(it) }

    fun share(doc: DocumentEntity) {
        scope.launch {
            runCatching {
                val uri = viewModel.shareUri(doc)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, null))
            }.onFailure {
                Toast.makeText(context, R.string.toast_share_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = stringResource(R.string.files_title),
                subtitle = stringResource(R.string.files_count, state.documents.size),
                trailing = {
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.Outlined.Sort, contentDescription = stringResource(R.string.cd_sort))
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            listOf(
                                FileSortOrder.DATE_DESC to R.string.sort_recent,
                                FileSortOrder.NAME_ASC to R.string.sort_name,
                                FileSortOrder.SIZE_DESC to R.string.sort_size
                            ).forEach { (order, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(label),
                                            color = if (state.sortOrder == order) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = { sortMenu = false; viewModel.updateSort(order) }
                                )
                            }
                        }
                    }
                    IconButton(onClick = viewModel::toggleGridView) {
                        Icon(
                            imageVector = if (state.isGridView) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                            contentDescription = stringResource(R.string.cd_toggle_layout)
                        )
                    }
                }
            )

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::updateQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateQuery("") }) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.cd_clear))
                        }
                    }
                },
                placeholder = { Text(stringResource(R.string.files_search_hint)) }
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    listOf(
                        FileFilter.ALL to R.string.filter_all,
                        FileFilter.FAVORITES to R.string.filter_favorites,
                        FileFilter.SCANNED to R.string.filter_scanned
                    )
                ) { (filter, label) ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { viewModel.updateFilter(filter) },
                        label = { Text(stringResource(label)) },
                        shape = RoundedCornerShape(50)
                    )
                }
            }

            when {
                state.documents.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Outlined.FolderOpen,
                        title = stringResource(R.string.files_empty_title),
                        message = stringResource(R.string.files_empty_body),
                        modifier = Modifier.padding(top = 36.dp)
                    )
                }
                state.isGridView -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 110.dp)
                    ) {
                        items(state.documents, key = { it.uriString }) { doc ->
                            FileGridCard(
                                doc = doc,
                                loadThumb = loadThumb,
                                onClick = { onOpenDocument(doc.uriString) },
                                onFavorite = { viewModel.toggleFavorite(doc) },
                                onRename = { renameTarget = doc; renameInput = doc.displayName },
                                onShare = { share(doc) },
                                onRemove = { removeTarget = doc }
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 110.dp)
                    ) {
                        items(state.documents, key = { it.uriString }) { doc ->
                            FileListCard(
                                doc = doc,
                                loadThumb = loadThumb,
                                onClick = { onOpenDocument(doc.uriString) },
                                onFavorite = { viewModel.toggleFavorite(doc) },
                                onRename = { renameTarget = doc; renameInput = doc.displayName },
                                onShare = { share(doc) },
                                onRemove = { removeTarget = doc }
                            )
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { multiPicker.launch(arrayOf("application/pdf")) },
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.files_import)) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.rename_title)) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.rename_label)) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rename(target.uriString, renameInput)
                    renameTarget = null
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    removeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text(stringResource(R.string.remove_title)) },
            text = { Text(stringResource(R.string.remove_body, target.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeFromLibrary(target.uriString)
                    removeTarget = null
                }) { Text(stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
private fun DocMenu(
    doc: DocumentEntity,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.cd_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_rename)) },
                leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
                onClick = { expanded = false; onRename() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_share)) },
                leadingIcon = { Icon(Icons.Outlined.Share, null) },
                onClick = { expanded = false; onShare() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_remove_from_list)) },
                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
                onClick = { expanded = false; onRemove() }
            )
        }
    }
}

@Composable
private fun FavoriteButton(doc: DocumentEntity, onFavorite: () -> Unit) {
    IconButton(onClick = onFavorite, modifier = Modifier.size(36.dp)) {
        Icon(
            imageVector = if (doc.isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
            contentDescription = stringResource(R.string.cd_favorite),
            tint = if (doc.isFavorite) androidx.compose.ui.graphics.Color(0xFFF5A524)
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FileListCard(
    doc: DocumentEntity,
    loadThumb: suspend (String) -> android.graphics.Bitmap?,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onRemove: () -> Unit
) {
    StudioCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DocumentThumb(
                key = doc.uriString,
                scanned = doc.isScanned,
                loader = loadThumb,
                modifier = Modifier
                    .width(48.dp)
                    .height(64.dp),
                shape = RoundedCornerShape(10.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    doc.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${Formatters.formatFileSize(doc.sizeBytes)} • " +
                        if (doc.pageCount > 0) "${doc.pageCount} p" else "PDF",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (doc.isScanned) StatusPill(stringResource(R.string.pill_scanned), tone = PillTone.SCAN)
            }
            FavoriteButton(doc, onFavorite)
            DocMenu(doc, onRename, onShare, onRemove)
        }
    }
}

@Composable
private fun FileGridCard(
    doc: DocumentEntity,
    loadThumb: suspend (String) -> android.graphics.Bitmap?,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onRemove: () -> Unit
) {
    StudioCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        DocumentThumb(
            key = doc.uriString,
            scanned = doc.isScanned,
            loader = loadThumb,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.78f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        )
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    doc.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    Formatters.formatFileSize(doc.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DocMenu(doc, onRename, onShare, onRemove)
        }
    }
}
