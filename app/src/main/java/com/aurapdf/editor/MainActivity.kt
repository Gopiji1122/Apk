package com.aurapdf.editor

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurapdf.editor.data.PdfStorage
import com.aurapdf.editor.data.RecentPdfItem
import com.aurapdf.editor.engine.OfficeConverter
import com.aurapdf.editor.engine.PdfEngine
import com.aurapdf.editor.theme.AcrobatColors
import com.aurapdf.editor.theme.AuraPdfTheme
import com.aurapdf.editor.theme.PdfThemeMode
import com.aurapdf.editor.ui.EditPdfScreen
import com.aurapdf.editor.ui.ScanScreen
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class AppRoute {
    data object Home : AppRoute()
    data object Scan : AppRoute()
    data class EditPdf(val file: File) : AppRoute()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        setContent { AuraPdfRoot() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraPdfRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storage = remember { PdfStorage(context) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var themeMode by remember { mutableStateOf(storage.themeMode) }
    var recentPdfs by remember { mutableStateOf(storage.listRecentPdfs()) }
    var homeBanner by remember { mutableStateOf<String?>(null) }
    var fabExpanded by remember { mutableStateOf(false) }
    var showBlankDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var filterStarredOnly by remember { mutableStateOf(false) }

    val backStack = remember { mutableStateListOf<AppRoute>(AppRoute.Home) }
    val currentRoute = backStack.last()

    fun navigateTo(route: AppRoute) {
        fabExpanded = false
        recentPdfs = storage.listRecentPdfs()
        backStack.add(route)
    }

    fun navigateBack() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
            recentPdfs = storage.listRecentPdfs()
        }
    }

    BackHandler(enabled = fabExpanded || drawerState.isOpen || showSearch || backStack.size > 1) {
        when {
            fabExpanded -> fabExpanded = false
            drawerState.isOpen -> scope.launch { drawerState.close() }
            showSearch -> { showSearch = false; searchQuery = "" }
            else -> navigateBack()
        }
    }

    val openPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val f = withContext(Dispatchers.IO) { storage.importPdfFromUri(uri) }
                navigateTo(AppRoute.EditPdf(f))
            }
        }
    }

    val fromImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                homeBanner = "Creating PDF from ${uris.size} image(s)..."
                val out = storage.newOutputFile("Images_PDF")
                withContext(Dispatchers.IO) { PdfEngine.imagesToPdf(context, uris, out) }
                homeBanner = null
                navigateTo(AppRoute.EditPdf(out))
            }
        }
    }

    val officeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                homeBanner = "Converting Word/Excel to PDF..."
                try {
                    val name = storage.getDisplayName(uri)
                    val out = storage.newOutputFile(name.substringBeforeLast("."))
                    withContext(Dispatchers.IO) {
                        when {
                            name.endsWith(".docx", ignoreCase = true) -> OfficeConverter.convertWordDocxToPdf(context, uri, out, name)
                            name.endsWith(".xlsx", ignoreCase = true) || name.endsWith(".csv", ignoreCase = true) -> OfficeConverter.convertExcelOrCsvToPdf(context, uri, name, out)
                            else -> throw IllegalStateException("Pick a .docx, .xlsx, or .csv file.")
                        }
                    }
                    homeBanner = null
                    navigateTo(AppRoute.EditPdf(out))
                } catch (e: Exception) {
                    homeBanner = "Convert failed: ${e.message}"
                }
            }
        }
    }

    val mergeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.size >= 2) {
            scope.launch {
                homeBanner = "Merging ${uris.size} PDFs..."
                val files = withContext(Dispatchers.IO) { uris.map { storage.importPdfFromUri(it) } }
                val out = storage.newOutputFile("Merged_PDF")
                withContext(Dispatchers.IO) {
                    files.first().copyTo(out, overwrite = true)
                    for (i in 1 until files.size) PdfEngine.appendPdfIntoCurrent(out, files[i])
                }
                homeBanner = null
                navigateTo(AppRoute.EditPdf(out))
            }
        } else if (uris.isNotEmpty()) {
            homeBanner = "Select at least 2 PDFs to merge."
        }
    }

    AuraPdfTheme(mode = themeMode) {
        if (showBlankDialog) {
            var docName by remember { mutableStateOf("Blank_Document") }
            var pageSize by remember { mutableStateOf("A4") }
            var landscape by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showBlankDialog = false },
                title = { Text("Create Blank PDF") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = docName, onValueChange = { docName = it }, label = { Text("File Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("A4", "Letter", "Legal").forEach { sz -> FilterChip(selected = pageSize == sz, onClick = { pageSize = sz }, label = { Text(sz, fontSize = 12.sp) }) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Landscape", fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Switch(checked = landscape, onCheckedChange = { landscape = it })
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        showBlankDialog = false
                        scope.launch {
                            val out = storage.newOutputFile(docName)
                            withContext(Dispatchers.IO) { PdfEngine.createBlankPdf(out, pageSize, landscape) }
                            navigateTo(AppRoute.EditPdf(out))
                        }
                    }) { Text("Create & Edit") }
                },
                dismissButton = { TextButton(onClick = { showBlankDialog = false }) { Text("Cancel") } }
            )
        }

        if (renameTarget != null) {
            var newName by remember { mutableStateOf(renameTarget!!.nameWithoutExtension) }
            AlertDialog(
                onDismissRequest = { renameTarget = null },
                title = { Text("Rename PDF") },
                text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("New Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
                confirmButton = {
                    Button(enabled = newName.isNotBlank(), onClick = {
                        storage.renamePdf(renameTarget!!, newName)
                        renameTarget = null
                        recentPdfs = storage.listRecentPdfs()
                    }) { Text("Rename") }
                },
                dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
            )
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = currentRoute is AppRoute.Home,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = MaterialTheme.colorScheme.surface,
                    drawerContentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(295.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)) {
                            Surface(shape = RoundedCornerShape(10.dp), color = AcrobatColors.Red, modifier = Modifier.size(34.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Aura PDF Editor", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text("Acrobat-Style Offline Suite", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(6.dp))

                        ColoredDrawerRow(Icons.Default.FolderOpen, "Open PDF from Storage", AcrobatColors.CommentBlue) {
                            scope.launch { drawerState.close() }; openPdfLauncher.launch(arrayOf("application/pdf"))
                        }
                        ColoredDrawerRow(Icons.Default.CameraAlt, "Pro Auto / Manual Scan", AcrobatColors.Red) {
                            scope.launch { drawerState.close() }; navigateTo(AppRoute.Scan)
                        }
                        ColoredDrawerRow(Icons.Default.NoteAdd, "Create Blank PDF", AcrobatColors.EditPink) {
                            scope.launch { drawerState.close() }; showBlankDialog = true
                        }
                        ColoredDrawerRow(Icons.Default.Image, "Images (JPG/PNG) to PDF", AcrobatColors.CompressGreen) {
                            scope.launch { drawerState.close() }; fromImageLauncher.launch(arrayOf("image/*"))
                        }
                        ColoredDrawerRow(Icons.Default.TableChart, "Word & Excel to PDF", AcrobatColors.MergeOrange) {
                            scope.launch { drawerState.close() }; officeLauncher.launch(arrayOf("*/*"))
                        }
                        ColoredDrawerRow(Icons.Default.MergeType, "Combine / Merge PDFs", AcrobatColors.OrganizeTeal) {
                            scope.launch { drawerState.close() }; mergeLauncher.launch(arrayOf("application/pdf"))
                        }

                        Spacer(Modifier.weight(1f))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(6.dp))

                        ColoredDrawerRow(Icons.Default.DarkMode, "Theme: ${themeMode.label}", AcrobatColors.SignPurple) {
                            val next = when (themeMode) {
                                PdfThemeMode.AMOLED -> PdfThemeMode.DARK
                                PdfThemeMode.DARK -> PdfThemeMode.LIGHT
                                PdfThemeMode.LIGHT -> PdfThemeMode.AMOLED
                            }
                            themeMode = next; storage.themeMode = next
                        }
                    }
                }
            }
        ) {
            AnimatedContent(
                targetState = currentRoute,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                label = "RootNav"
            ) { route ->
                when (route) {
                    is AppRoute.Home -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Scaffold(
                                containerColor = MaterialTheme.colorScheme.background,
                                topBar = {
                                    TopAppBar(
                                        colors = TopAppBarDefaults.topAppBarColors(
                                            containerColor = MaterialTheme.colorScheme.background,
                                            titleContentColor = MaterialTheme.colorScheme.onBackground
                                        ),
                                        navigationIcon = {
                                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                                            }
                                        },
                                        title = {
                                            if (showSearch) {
                                                OutlinedTextField(
                                                    value = searchQuery,
                                                    onValueChange = { searchQuery = it },
                                                    placeholder = { Text("Search PDFs...", fontSize = 14.sp) },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            } else {
                                                Text("Aura PDF Editor", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                            }
                                        },
                                        actions = {
                                            IconButton(onClick = { showSearch = !showSearch; if (!showSearch) searchQuery = "" }) {
                                                Icon(if (showSearch) Icons.Default.Close else Icons.Default.Search, contentDescription = "Search")
                                            }
                                        }
                                    )
                                }
                            ) { padding ->
                                val displayed = remember(recentPdfs, searchQuery, filterStarredOnly) {
                                    recentPdfs.filter { item ->
                                        (!filterStarredOnly || item.isStarred) &&
                                            (searchQuery.isBlank() || item.name.contains(searchQuery, ignoreCase = true))
                                    }
                                }

                                LazyColumn(
                                    modifier = Modifier.fillMaxSize().padding(padding),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (homeBanner != null) {
                                        item {
                                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                                                Text(homeBanner!!, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                                            }
                                        }
                                    }

                                    item {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            FilterChip(selected = !filterStarredOnly, onClick = { filterStarredOnly = false }, label = { Text("Recent (${recentPdfs.size})", fontSize = 12.sp) })
                                            FilterChip(selected = filterStarredOnly, onClick = { filterStarredOnly = true }, label = { Text("Starred", fontSize = 12.sp) })
                                        }
                                    }

                                    if (displayed.isEmpty()) {
                                        item {
                                            Box(modifier = Modifier.fillMaxWidth().padding(top = 110.dp), contentAlignment = Alignment.Center) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(52.dp))
                                                    Spacer(Modifier.height(12.dp))
                                                    Text(if (filterStarredOnly) "No starred PDFs" else "No recent PDFs yet", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                                    Spacer(Modifier.height(4.dp))
                                                    Text("Tap the red + button below to Scan, create a Blank Page, or convert Images.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    } else {
                                        items(displayed, key = { it.file.absolutePath }) { item ->
                                            AcrobatRecentPdfCard(
                                                item = item,
                                                onOpen = { navigateTo(AppRoute.EditPdf(item.file)) },
                                                onToggleStar = { storage.toggleStar(item.file); recentPdfs = storage.listRecentPdfs() },
                                                onShare = { storage.shareFile(item.file) },
                                                onRename = { renameTarget = item.file },
                                                onDelete = { item.file.delete(); recentPdfs = storage.listRecentPdfs() }
                                            )
                                        }
                                    }
                                }
                            }

                            if (fabExpanded) {
                                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable { fabExpanded = false })
                            }

                            // ACROBAT-STYLE FLOATING "+" SPEED DIAL (COLORED FLOATING ICONS + TEXT ON LEFT)
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 20.dp)
                            ) {
                                AnimatedVisibility(
                                    visible = fabExpanded,
                                    enter = fadeIn(tween(160)) + slideInVertically(tween(180)) { it / 2 },
                                    exit = fadeOut(tween(120)) + slideOutVertically(tween(140)) { it / 2 }
                                ) {
                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        SpeedDialRow("From Image", Icons.Default.Image, AcrobatColors.CompressGreen) {
                                            fabExpanded = false; fromImageLauncher.launch(arrayOf("image/*"))
                                        }
                                        SpeedDialRow("Blank Page", Icons.Default.NoteAdd, AcrobatColors.CommentBlue) {
                                            fabExpanded = false; showBlankDialog = true
                                        }
                                        SpeedDialRow("Scan", Icons.Default.CameraAlt, AcrobatColors.Red) {
                                            fabExpanded = false; navigateTo(AppRoute.Scan)
                                        }
                                    }
                                }

                                FloatingActionButton(
                                    onClick = { fabExpanded = !fabExpanded },
                                    shape = CircleShape,
                                    containerColor = AcrobatColors.Red,
                                    contentColor = Color.White
                                ) {
                                    Icon(imageVector = if (fabExpanded) Icons.Default.Close else Icons.Default.Add, contentDescription = "Create or Scan")
                                }
                            }
                        }
                    }

                    is AppRoute.Scan -> ScanScreen(
                        storage = storage,
                        onBack = { navigateBack() },
                        onSavedOnly = { recentPdfs = storage.listRecentPdfs(); navigateBack() },
                        onOpenInEditor = { scannedPdf -> recentPdfs = storage.listRecentPdfs(); navigateTo(AppRoute.EditPdf(scannedPdf)) }
                    )

                    is AppRoute.EditPdf -> EditPdfScreen(
                        file = route.file,
                        storage = storage,
                        onBack = { navigateBack() },
                        onOpenNewFile = { newFile -> navigateTo(AppRoute.EditPdf(newFile)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AcrobatRecentPdfCard(
    item: RecentPdfItem,
    onOpen: () -> Unit,
    onToggleStar: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var thumb by remember(item.file.absolutePath, item.modifiedAt) { mutableStateOf<Bitmap?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(item.file.absolutePath, item.modifiedAt) {
        withContext(Dispatchers.IO) {
            try { thumb = PdfEngine.renderPageBitmap(item.file, 0, 180) } catch (_: Exception) {}
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            // Real PDF Page 1 Thumbnail Preview (just like Adobe Acrobat!)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.White,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.size(width = 44.dp, height = 56.dp)
            ) {
                if (thumb != null) {
                    Image(bitmap = thumb!!.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = AcrobatColors.Red, modifier = Modifier.size(22.dp))
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text("PDF • ${item.sizeKb} KB", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onToggleStar, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (item.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Star",
                    tint = if (item.isStarred) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Edit PDF") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menuOpen = false; onOpen() })
                    DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Default.Description, null) }, onClick = { menuOpen = false; onRename() })
                    DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { menuOpen = false; onShare() })
                    DropdownMenuItem(text = { Text("Delete", color = Color(0xFFEF5350)) }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = Color(0xFFEF5350)) }, onClick = { menuOpen = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun SpeedDialRow(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.clickable(onClick = onClick)) {
        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shadowElevation = 4.dp) {
            Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
        }
        SmallFloatingActionButton(onClick = onClick, shape = CircleShape, containerColor = color, contentColor = Color.White) {
            Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ColoredDrawerRow(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color.Transparent, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.16f), modifier = Modifier.size(32.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp)) }
            }
            Spacer(Modifier.width(12.dp))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}
