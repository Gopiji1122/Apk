package com.offlinepdf.studio.feature.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
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
import com.offlinepdf.studio.core.designsystem.components.GradientActionCard
import com.offlinepdf.studio.core.designsystem.components.PdfFileIcon
import com.offlinepdf.studio.core.designsystem.components.ProgressHairline
import com.offlinepdf.studio.core.designsystem.components.SectionTitle
import com.offlinepdf.studio.core.designsystem.components.StudioCard
import com.offlinepdf.studio.core.designsystem.components.bounceClick
import com.offlinepdf.studio.core.designsystem.components.staggeredEntrance
import com.offlinepdf.studio.core.designsystem.theme.extras

@Composable
fun HomeScreen(
    onOpenDocument: (String) -> Unit,
    onNavigateToFiles: () -> Unit,
    onNavigateToScanner: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.onDocumentPicked(uri, onOpenDocument)
    }
    val loadThumb: suspend (String) -> android.graphics.Bitmap? = { viewModel.thumbnail(it) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .staggeredEntrance(0),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    PdfFileIcon(size = 44.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.app_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.extras.success,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.home_private_offline),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .staggeredEntrance(1),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GradientActionCard(
                    title = stringResource(R.string.home_open_pdf),
                    subtitle = stringResource(R.string.home_open_pdf_sub),
                    icon = Icons.Outlined.FolderOpen,
                    brush = MaterialTheme.extras.brandBrush,
                    onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) },
                    modifier = Modifier.weight(1f)
                )
                GradientActionCard(
                    title = stringResource(R.string.home_scan),
                    subtitle = stringResource(R.string.home_scan_sub),
                    icon = Icons.Outlined.DocumentScanner,
                    brush = MaterialTheme.extras.scanBrush,
                    onClick = onNavigateToScanner,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        state.continueReading?.let { doc ->
            item(key = "continue") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.staggeredEntrance(2)
                ) {
                    SectionTitle(stringResource(R.string.home_continue))
                    ContinueCard(
                        doc = doc,
                        loadThumb = loadThumb,
                        onClick = { onOpenDocument(doc.uriString) },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
        }

        if (state.scannedDocuments.isNotEmpty()) {
            item(key = "scans") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.staggeredEntrance(3)
                ) {
                    SectionTitle(stringResource(R.string.home_recent_scans))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.scannedDocuments, key = { "scan_${it.uriString}" }) { doc ->
                            TileCard(doc, loadThumb) { onOpenDocument(doc.uriString) }
                        }
                    }
                }
            }
        }

        if (state.favoriteDocuments.isNotEmpty()) {
            item(key = "favs") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.staggeredEntrance(4)
                ) {
                    SectionTitle(stringResource(R.string.home_favorites))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.favoriteDocuments, key = { "fav_${it.uriString}" }) { doc ->
                            TileCard(doc, loadThumb) { onOpenDocument(doc.uriString) }
                        }
                    }
                }
            }
        }

        item {
            SectionTitle(
                text = stringResource(R.string.home_recent),
                modifier = Modifier.staggeredEntrance(5),
                action = {
                    if (state.recentDocuments.isNotEmpty()) {
                        TextButton(onClick = onNavigateToFiles) {
                            Text(stringResource(R.string.home_see_all))
                        }
                    }
                }
            )
        }
        if (state.recentDocuments.isEmpty()) {
            item {
                StudioCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                    EmptyState(
                        icon = Icons.Outlined.Description,
                        title = stringResource(R.string.home_empty_title),
                        message = stringResource(R.string.home_empty_body)
                    )
                }
            }
        } else {
            items(state.recentDocuments, key = { it.uriString }) { doc ->
                DocumentRow(
                    doc = doc,
                    loadThumb = loadThumb,
                    onClick = { onOpenDocument(doc.uriString) },
                    onToggleFavorite = { viewModel.toggleFavorite(doc) },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
    }
}

@Composable
private fun ContinueCard(
    doc: DocumentEntity,
    loadThumb: suspend (String) -> android.graphics.Bitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (doc.pageCount > 0) (doc.lastReadPage + 1f) / doc.pageCount else 0f
    StudioCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DocumentThumb(
                key = doc.uriString,
                scanned = doc.isScanned,
                loader = loadThumb,
                modifier = Modifier
                    .width(64.dp)
                    .height(84.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    doc.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (doc.pageCount > 0) {
                        stringResource(R.string.page_x_of_y, doc.lastReadPage + 1, doc.pageCount)
                    } else {
                        Formatters.formatFileSize(doc.sizeBytes)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (doc.pageCount > 0) ProgressHairline(progress = progress)
            }
        }
    }
}

@Composable
private fun TileCard(
    doc: DocumentEntity,
    loadThumb: suspend (String) -> android.graphics.Bitmap?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(112.dp)
            .bounceClick(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DocumentThumb(
            key = doc.uriString,
            scanned = doc.isScanned,
            loader = loadThumb,
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp),
            shape = RoundedCornerShape(16.dp)
        )
        Text(
            doc.displayName,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DocumentRow(
    doc: DocumentEntity,
    loadThumb: suspend (String) -> android.graphics.Bitmap?,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    StudioCard(modifier = modifier.fillMaxWidth(), onClick = onClick, shape = MaterialTheme.shapes.medium) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DocumentThumb(
                key = doc.uriString,
                scanned = doc.isScanned,
                loader = loadThumb,
                modifier = Modifier
                    .width(44.dp)
                    .height(58.dp),
                shape = RoundedCornerShape(10.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    doc.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = buildString {
                    append(Formatters.formatFileSize(doc.sizeBytes))
                    if (doc.pageCount > 0) append(" • ${doc.pageCount} p")
                    val time = Formatters.formatTimestamp(doc.lastOpenedAt)
                    if (time.isNotEmpty()) append(" • $time")
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val starScale by animateFloatAsState(if (doc.isFavorite) 1.12f else 1f, label = "star")
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (doc.isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                    contentDescription = stringResource(R.string.cd_favorite),
                    tint = if (doc.isFavorite) Color(0xFFF5A524) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size((22 * starScale).dp)
                )
            }
        }
    }
}
