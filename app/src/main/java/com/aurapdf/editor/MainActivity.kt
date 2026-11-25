package com.aurapdf.editor

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
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurapdf.editor.data.PdfStorage
import com.aurapdf.editor.engine.OfficeConverter
import com.aurapdf.editor.engine.PdfEngine
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
        setContent {
            AuraPdfRoot()
        }
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

    // Proper Back Navigation: closes FAB menu or Drawer first, then goes to previous page
    BackHandler(enabled = fabExpanded || drawerState.isOpen || backStack.size > 1) {
        when {
            fabExpanded -> fabExpanded = false
            drawerState.isOpen -> scope.launch { drawerState.close() }
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
                            name.endsWith(".docx", ignoreCase = true) ->
                                OfficeConverter.convertWordDocxToPdf(context, uri, out, name)
                            name.endsWith(".xlsx", ignoreCase = true) || name.endsWith(".csv", ignoreCase = true) ->
                                OfficeConverter.convertExcelOrCsvToPdf(context, uri, name, out)
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
                        OutlinedTextField(
                            value = docName,
                            onValueChange = { docName = it },
                            label = { Text("File Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("A4", "Letter", "Legal").forEach { sz ->
                                FilterChip(selected = pageSize == sz, onClick = { pageSize = sz }, label = { Text(sz, fontSize = 12.sp) })
                            }
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
                dismissButton = {
                    TextButton(onClick = { showBlankDialog = false }) { Text("Cancel") }
                }
            )
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = currentRoute is AppRoute.Home,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = MaterialTheme.colorScheme.surface,
                    drawerContentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(290.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Aura PDF Editor", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(6.dp))

                        // ALL FEATURES INSIDE LEFT 3-LINE SIDE MENU
                        DrawerMenuRow(Icons.Default.FolderOpen, "Open PDF from Storage") {
                            scope.launch { drawerState.close() }
                            openPdfLauncher.launch(arrayOf("application/pdf"))
                        }
                        DrawerMenuRow(Icons.Default.CameraAlt, "Scan Document") {
                            scope.launch { drawerState.close() }
                            navigateTo(AppRoute.Scan)
                        }
                        DrawerMenuRow(Icons.Default.NoteAdd, "Create Blank PDF") {
                            scope.launch { drawerState.close() }
                            showBlankDialog = true
                        }
                        DrawerMenuRow(Icons.Default.Image, "Images (JPG/PNG) to PDF") {
                            scope.launch { drawerState.close() }
                            fromImageLauncher.launch(arrayOf("image/*"))
                        }
                        DrawerMenuRow(Icons.Default.TableChart, "Word & Excel to PDF") {
                            scope.launch { drawerState.close() }
                            officeLauncher.launch(arrayOf("*/*"))
                        }
                        DrawerMenuRow(Icons.Default.MergeType, "Merge Multiple PDFs") {
                            scope.launch { drawerState.close() }
                            mergeLauncher.launch(arrayOf("application/pdf"))
                        }

                        Spacer(Modifier.weight(1f))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(6.dp))

                        DrawerMenuRow(Icons.Default.DarkMode, "Theme: ${themeMode.label}") {
                            val next = when (themeMode) {
                                PdfThemeMode.AMOLED -> PdfThemeMode.DARK
                                PdfThemeMode.DARK -> PdfThemeMode.LIGHT
                                PdfThemeMode.LIGHT -> PdfThemeMode.AMOLED
                            }
                            themeMode = next
                            storage.themeMode = next
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
                                            Text("Aura PDF Editor", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                        }
                                    )
                                }
                            ) { padding ->
                                // CLEAN HOME SCREEN: ONLY RECENT PDFs DIRECTLY UNDER HEADING (NO CLUTTER!)
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize().padding(padding),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (homeBanner != null) {
                                        item {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(homeBanner!!, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                                            }
                                        }
                                    }

                                    item {
                                        Text(
                                            text = "Recent Files (${recentPdfs.size})",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }

                                    if (recentPdfs.isEmpty()) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 120.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(
                                                        Icons.Default.Description,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(48.dp)
                                                    )
                                                    Spacer(Modifier.height(12.dp))
                                                    Text("No recent PDFs yet", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(
                                                        "Tap the + button below to Scan, create a Blank Page, or convert from Images.",
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        items(recentPdfs, key = { it.file.absolutePath }) { item ->
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { navigateTo(AppRoute.EditPdf(item.file)) }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(26.dp))
                                                    Spacer(Modifier.width(12.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(item.name, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        Text("${item.sizeKb} KB", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                    IconButton(onClick = { storage.shareFile(item.file) }, modifier = Modifier.size(32.dp)) {
                                                        Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            item.file.delete()
                                                            recentPdfs = storage.listRecentPdfs()
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Dim backdrop when Floating + Speed Dial Menu is open
                            if (fabExpanded) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.52f))
                                        .clickable { fabExpanded = false }
                                )
                            }

                            // BOTTOM-RIGHT FLOATING "+" SPEED DIAL (FLOATING ICONS UPWARD + TEXT ON LEFT SIDE!)
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .navigationBarsPadding()
                                    .padding(end = 20.dp, bottom = 20.dp)
                            ) {
                                AnimatedVisibility(
                                    visible = fabExpanded,
                                    enter = fadeIn(tween(160)) + slideInVertically(tween(180)) { it / 2 },
                                    exit = fadeOut(tween(120)) + slideOutVertically(tween(140)) { it / 2 }
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        SpeedDialRow(label = "From Image", icon = Icons.Default.Image) {
                                            fabExpanded = false
                                            fromImageLauncher.launch(arrayOf("image/*"))
                                        }
                                        SpeedDialRow(label = "Blank Page", icon = Icons.Default.NoteAdd) {
                                            fabExpanded = false
                                            showBlankDialog = true
                                        }
                                        SpeedDialRow(label = "Scan", icon = Icons.Default.CameraAlt) {
                                            fabExpanded = false
                                            navigateTo(AppRoute.Scan)
                                        }
                                    }
                                }

                                FloatingActionButton(
                                    onClick = { fabExpanded = !fabExpanded },
                                    shape = CircleShape,
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Icon(
                                        imageVector = if (fabExpanded) Icons.Default.Close else Icons.Default.Add,
                                        contentDescription = "Create or Scan"
                                    )
                                }
                            }
                        }
                    }

                    is AppRoute.Scan -> ScanScreen(
                        storage = storage,
                        onBack = { navigateBack() },
                        onSavedOnly = {
                            recentPdfs = storage.listRecentPdfs()
                            navigateBack()
                        },
                        onOpenInEditor = { scannedPdf ->
                            recentPdfs = storage.listRecentPdfs()
                            navigateTo(AppRoute.EditPdf(scannedPdf))
                        }
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
private fun SpeedDialRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shadowElevation = 4.dp
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
        SmallFloatingActionButton(
            onClick = onClick,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun DrawerMenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}
