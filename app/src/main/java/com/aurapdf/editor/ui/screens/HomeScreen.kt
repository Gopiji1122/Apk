package com.aurapdf.editor.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aurapdf.editor.data.PdfRepository
import com.aurapdf.editor.model.AppThemeMode
import com.aurapdf.editor.model.RecentPdfItem
import com.aurapdf.editor.ui.components.CreateBlankPdfDialog
import com.aurapdf.editor.ui.components.MergePdfsDialog
import com.aurapdf.editor.ui.components.ShareUtils
import com.aurapdf.editor.ui.components.ThemeSwitcherDialog
import com.aurapdf.editor.ui.theme.AcrobatRed
import com.aurapdf.editor.ui.theme.ToolCommentBlue
import com.aurapdf.editor.ui.theme.ToolCompressGreen
import com.aurapdf.editor.ui.theme.ToolEditPink
import com.aurapdf.editor.ui.theme.ToolMergeOrange
import com.aurapdf.editor.ui.theme.ToolOrganizeTeal
import com.aurapdf.editor.ui.theme.ToolSignPurple
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: PdfRepository,
    themeMode: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit,
    onOpenPdfViewer: (String) -> Unit,
    onOpenPdfEditor: (String) -> Unit,
    onOpenScanner: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var recentPdfs by remember { mutableStateOf<List<RecentPdfItem>>(emptyList()) }
    var selectedFilter by remember { mutableIntStateOf(0) } // 0 = Recent, 1 = Starred
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var speedDialExpanded by remember { mutableStateOf(false) }

    var showCreateBlankDialog by remember { mutableStateOf(false) }
    var showMergeDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<RecentPdfItem?>(null) }
    var deleteTarget by remember { mutableStateOf<RecentPdfItem?>(null) }
    var saveCopyPendingFile by remember { mutableStateOf<File?>(null) }

    fun refreshList() {
        scope.launch {
            recentPdfs = repository.loadRecentPdfs()
        }
    }

    LaunchedEffect(Unit) {
        refreshList()
    }

    // Layered BackHandler: close open menus, speed-dial FAB, drawer, or search bar first
    BackHandler(enabled = speedDialExpanded || drawerState.isOpen || searchActive) {
        when {
            speedDialExpanded -> speedDialExpanded = false
            drawerState.isOpen -> scope.launch { drawerState.close() }
            searchActive -> {
                searchActive = false
                searchQuery = ""
            }
        }
    }

    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching { repository.importPdfFromUri(uri) }
                    .onSuccess { imported ->
                        refreshList()
                        onOpenPdfViewer(imported.absolutePath)
                    }
                    .onFailure {
                        Toast.makeText(context, "Failed to open PDF: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    val imagesToPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                runCatching {
                    repository.imagesToPdf(uris, "Images_${System.currentTimeMillis() % 100000}")
                }.onSuccess { pdfFile ->
                    refreshList()
                    onOpenPdfViewer(pdfFile.absolutePath)
                }.onFailure {
                    Toast.makeText(context, "Image conversion failed: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val officeToPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching { repository.officeOrCsvToPdf(uri) }
                    .onSuccess { pdfFile ->
                        Toast.makeText(context, "Converted to ${pdfFile.name}", Toast.LENGTH_SHORT).show()
                        refreshList()
                        onOpenPdfViewer(pdfFile.absolutePath)
                    }
                    .onFailure {
                        Toast.makeText(context, "Conversion error: ${it.message}", Toast.LENGTH_LONG).show()
                    }
            }
        }
    }

    val saveCopySafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri: Uri? ->
        val src = saveCopyPendingFile
        if (destUri != null && src != null && src.exists()) {
            runCatching {
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    src.inputStream().use { it.copyTo(out) }
                }
                Toast.makeText(context, "Saved copy to device storage", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "Save failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
        saveCopyPendingFile = null
    }

    val visibleList = remember(recentPdfs, selectedFilter, searchQuery) {
        recentPdfs
            .filter { if (selectedFilter == 1) it.isStarred else true }
            .filter {
                if (searchQuery.isBlank()) true
                else it.name.contains(searchQuery.trim(), ignoreCase = true)
            }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                // Brand Header in Drawer
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 22.dp)
                ) {
                    Surface(
                        color = AcrobatRed,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            "Aura PDF Editor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "100% Offline • Pro Studio",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                DrawerToolItem(
                    icon = Icons.Default.FolderOpen,
                    badgeColor = ToolCommentBlue,
                    title = "Open PDF from Storage",
                    onClick = {
                        scope.launch { drawerState.close() }
                        openPdfLauncher.launch(arrayOf("application/pdf"))
                    }
                )
                DrawerToolItem(
                    icon = Icons.Default.DocumentScanner,
                    badgeColor = AcrobatRed,
                    title = "Pro Auto / Manual Scan",
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenScanner()
                    }
                )
                DrawerToolItem(
                    icon = Icons.Default.NoteAdd,
                    badgeColor = ToolOrganizeTeal,
                    title = "Create Blank PDF",
                    onClick = {
                        scope.launch { drawerState.close() }
                        showCreateBlankDialog = true
                    }
                )
                DrawerToolItem(
                    icon = Icons.Default.Image,
                    badgeColor = ToolCompressGreen,
                    title = "Images (JPG/PNG) to PDF",
                    onClick = {
                        scope.launch { drawerState.close() }
                        imagesToPdfLauncher.launch("image/*")
                    }
                )
                DrawerToolItem(
                    icon = Icons.Default.Description,
                    badgeColor = ToolEditPink,
                    title = "Word (.docx) & Excel (.xlsx / .csv) to PDF",
                    onClick = {
                        scope.launch { drawerState.close() }
                        officeToPdfLauncher.launch(
                            arrayOf(
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "text/csv",
                                "text/comma-separated-values",
                                "*/*"
                            )
                        )
                    }
                )
                DrawerToolItem(
                    icon = Icons.Default.MergeType,
                    badgeColor = ToolMergeOrange,
                    title = "Combine / Merge PDFs",
                    onClick = {
                        scope.launch { drawerState.close() }
                        showMergeDialog = true
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                DrawerToolItem(
                    icon = Icons.Default.Palette,
                    badgeColor = ToolSignPurple,
                    title = "Theme Switcher (${themeMode.displayName})",
                    onClick = {
                        scope.launch { drawerState.close() }
                        showThemeDialog = true
                    }
                )
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Open Menu")
                            }
                        },
                        title = {
                            if (searchActive) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Filter PDFs...") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(AcrobatRed)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Aura PDF Editor",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                if (searchActive) {
                                    searchActive = false
                                    searchQuery = ""
                                } else {
                                    searchActive = true
                                }
                            }) {
                                Icon(
                                    if (searchActive) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = "Search PDFs"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp)
                ) {
                    // Clean Recent / Starred Filter Chips directly under header
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        FilterChip(
                            selected = selectedFilter == 0,
                            onClick = { selectedFilter = 0 },
                            label = { Text("Recent (${recentPdfs.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AcrobatRed,
                                selectedLabelColor = Color.White
                            )
                        )
                        FilterChip(
                            selected = selectedFilter == 1,
                            onClick = { selectedFilter = 1 },
                            label = { Text("Starred (${recentPdfs.count { it.isStarred }})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AcrobatRed,
                                selectedLabelColor = Color.White
                            )
                        )
                    }

                    if (visibleList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 72.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = AcrobatRed.copy(alpha = 0.75f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = if (selectedFilter == 1) "No Starred PDFs" else "No Recent PDFs Yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Tap + to scan, create a blank page, or import from images",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(visibleList, key = { it.filePath }) { item ->
                                RecentPdfCard(
                                    item = item,
                                    thumbnailFile = repository.getThumbnailFile(File(item.filePath)),
                                    onOpen = { onOpenPdfViewer(item.filePath) },
                                    onEdit = { onOpenPdfEditor(item.filePath) },
                                    onToggleStar = {
                                        repository.toggleStar(item.filePath)
                                        refreshList()
                                    },
                                    onRename = { renameTarget = item },
                                    onShare = {
                                        ShareUtils.shareFile(context, File(item.filePath))
                                    },
                                    onSaveCopy = {
                                        saveCopyPendingFile = File(item.filePath)
                                        saveCopySafLauncher.launch(item.name)
                                    },
                                    onDelete = { deleteTarget = item }
                                )
                            }
                        }
                    }
                }
            }

            // Screen Dimmer Overlay when Speed-Dial FAB is expanded
            AnimatedVisibility(
                visible = speedDialExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.58f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { speedDialExpanded = false }
                )
            }

            // Bottom-Right Floating "+" Speed-Dial FAB
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 24.dp)
            ) {
                AnimatedVisibility(
                    visible = speedDialExpanded,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 }
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SpeedDialOption(
                            label = "Scan",
                            icon = Icons.Default.DocumentScanner,
                            containerColor = AcrobatRed,
                            onClick = {
                                speedDialExpanded = false
                                onOpenScanner()
                            }
                        )
                        SpeedDialOption(
                            label = "Blank Page",
                            icon = Icons.Default.NoteAdd,
                            containerColor = ToolCommentBlue,
                            onClick = {
                                speedDialExpanded = false
                                showCreateBlankDialog = true
                            }
                        )
                        SpeedDialOption(
                            label = "From Image",
                            icon = Icons.Default.Image,
                            containerColor = ToolCompressGreen,
                            onClick = {
                                speedDialExpanded = false
                                imagesToPdfLauncher.launch("image/*")
                            }
                        )
                    }
                }

                val rotation by animateFloatAsState(
                    targetValue = if (speedDialExpanded) 45f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "fab_rot"
                )
                FloatingActionButton(
                    onClick = { speedDialExpanded = !speedDialExpanded },
                    containerColor = AcrobatRed,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Create or Scan",
                        modifier = Modifier
                            .size(28.dp)
                            .rotate(rotation)
                    )
                }
            }
        }
    }

    // Dialogs
    if (showCreateBlankDialog) {
        CreateBlankPdfDialog(
            onDismiss = { showCreateBlankDialog = false },
            onCreate = { title, size, orient ->
                showCreateBlankDialog = false
                scope.launch {
                    val created = repository.createBlankPdf(title, size, orient)
                    refreshList()
                    onOpenPdfEditor(created.absolutePath)
                }
            }
        )
    }

    if (showMergeDialog) {
        MergePdfsDialog(
            availablePdfs = recentPdfs,
            onPickFromStorage = {
                openPdfLauncher.launch(arrayOf("application/pdf"))
            },
            onMerge = { paths, outTitle ->
                showMergeDialog = false
                scope.launch {
                    runCatching { repository.mergePdfs(paths, outTitle) }
                        .onSuccess { merged ->
                            refreshList()
                            onOpenPdfViewer(merged.absolutePath)
                        }
                        .onFailure {
                            Toast.makeText(context, "Merge failed: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            },
            onDismiss = { showMergeDialog = false }
        )
    }

    if (showThemeDialog) {
        ThemeSwitcherDialog(
            currentMode = themeMode,
            onSelect = {
                onThemeChange(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    renameTarget?.let { target ->
        var newName by remember(target) { mutableStateOf(target.name.removeSuffix(".pdf")) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename PDF", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New Filename") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.renamePdf(target.filePath, newName)
                        renameTarget = null
                        refreshList()
                    },
                    enabled = newName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = AcrobatRed)
                ) {
                    Text("Rename", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete PDF?", fontWeight = FontWeight.Bold) },
            text = { Text("Delete \"${target.name}\" permanently from Aura PDF Editor?") },
            confirmButton = {
                Button(
                    onClick = {
                        repository.deletePdf(target.filePath)
                        deleteTarget = null
                        refreshList()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AcrobatRed)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DrawerToolItem(
    icon: ImageVector,
    badgeColor: Color,
    title: String,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        icon = {
            Surface(
                color = badgeColor.copy(alpha = 0.18f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        label = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}

@Composable
private fun SpeedDialOption(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 4.dp,
            modifier = Modifier.clickable(onClick = onClick)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = containerColor,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(icon, contentDescription = label)
        }
    }
}

@Composable
private fun RecentPdfCard(
    item: RecentPdfItem,
    thumbnailFile: File,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onToggleStar: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onSaveCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    val thumbBitmap = remember(thumbnailFile.absolutePath, thumbnailFile.lastModified()) {
        if (thumbnailFile.exists()) {
            BitmapFactory.decodeFile(thumbnailFile.absolutePath)?.asImageBitmap()
        } else null
    }

    val dateLabel = remember(item.lastModified) {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(item.lastModified))
    }

    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)
        ) {
            // Real Rendered Page 1 Thumbnail Preview with Acrobat PDF micro-badge
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(9.dp),
                shadowElevation = 2.dp,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .width(54.dp)
                    .height(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (thumbBitmap != null) {
                        Image(
                            bitmap = thumbBitmap,
                            contentDescription = "Page 1 Preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = AcrobatRed,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Surface(
                        color = AcrobatRed,
                        shape = RoundedCornerShape(topStart = 4.dp),
                        modifier = Modifier.align(Alignment.BottomEnd)
                    ) {
                        Text(
                            text = "PDF",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 13.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isEncrypted) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Password Protected",
                            tint = AcrobatRed,
                            modifier = Modifier.size(14.dp).padding(end = 3.dp)
                        )
                    }
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "${Formatter.formatShortFileSize(context, item.sizeBytes)}  •  ${item.pageCount} pg  •  $dateLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onToggleStar) {
                Icon(
                    imageVector = if (item.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Star PDF",
                    tint = if (item.isStarred) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("View PDF") },
                        onClick = {
                            menuExpanded = false
                            onOpen()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit PDF") },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = {
                            menuExpanded = false
                            onShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Save a Copy") },
                        onClick = {
                            menuExpanded = false
                            onSaveCopy()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = AcrobatRed) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
