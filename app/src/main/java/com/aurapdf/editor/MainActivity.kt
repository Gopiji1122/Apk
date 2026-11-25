package com.aurapdf.editor

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MergeType
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
import com.aurapdf.editor.ui.EditorWorkspaceScreen
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class AppRoute {
    data object Home : AppRoute()
    data class Workspace(val file: File) : AppRoute()
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
    var showNewBlankDialog by remember { mutableStateOf(false) }

    val backStack = remember { mutableStateListOf<AppRoute>(AppRoute.Home) }
    val currentRoute = backStack.last()

    fun openWorkspace(file: File) {
        recentPdfs = storage.listRecentPdfs()
        backStack.add(AppRoute.Workspace(file))
    }

    fun navigateBack() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
            recentPdfs = storage.listRecentPdfs()
        }
    }

    BackHandler(enabled = drawerState.isOpen || backStack.size > 1) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            navigateBack()
        }
    }

    val openPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val f = withContext(Dispatchers.IO) { storage.importPdfFromUri(uri) }
                openWorkspace(f)
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
                            else -> throw IllegalStateException("Select a .docx, .xlsx, or .csv file.")
                        }
                    }
                    homeBanner = null
                    openWorkspace(out)
                } catch (e: Exception) {
                    homeBanner = "Convert failed: ${e.message}"
                }
            }
        }
    }

    val imagesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                homeBanner = "Creating PDF from ${uris.size} image(s)..."
                val out = storage.newOutputFile("Images_PDF")
                withContext(Dispatchers.IO) { PdfEngine.imagesToPdf(context, uris, out) }
                homeBanner = null
                openWorkspace(out)
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
                    for (i in 1 until files.size) {
                        PdfEngine.appendPdfIntoCurrent(out, files[i])
                    }
                }
                homeBanner = null
                openWorkspace(out)
            }
        } else if (uris.isNotEmpty()) {
            homeBanner = "Select at least 2 PDFs to merge."
        }
    }

    AuraPdfTheme(mode = themeMode) {
        if (showNewBlankDialog) {
            var docName by remember { mutableStateOf("Untitled_Document") }
            var pageSize by remember { mutableStateOf("A4") }
            var landscape by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showNewBlankDialog = false },
                title = { Text("Create New Blank PDF") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = docName,
                            onValueChange = { docName = it },
                            label = { Text("Document Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("A4", "Letter", "Legal").forEach { sz ->
                                FilterChip(selected = pageSize == sz, onClick = { pageSize = sz }, label = { Text(sz, fontSize = 12.sp) })
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Landscape Page", fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Switch(checked = landscape, onCheckedChange = { landscape = it })
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        showNewBlankDialog = false
                        scope.launch {
                            val out = storage.newOutputFile(docName)
                            withContext(Dispatchers.IO) { PdfEngine.createBlankPdf(out, pageSize, landscape) }
                            openWorkspace(out)
                        }
                    }) { Text("Create & Open Editor") }
                },
                dismissButton = {
                    TextButton(onClick = { showNewBlankDialog = false }) { Text("Cancel") }
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

                        DrawerActionRow(Icons.Default.Add, "New Blank PDF (Full Editor)") {
                            scope.launch { drawerState.close() }
                            showNewBlankDialog = true
                        }
                        DrawerActionRow(Icons.Default.FolderOpen, "Open PDF to Edit") {
                            scope.launch { drawerState.close() }
                            openPdfLauncher.launch(arrayOf("application/pdf"))
                        }
                        DrawerActionRow(Icons.Default.TableChart, "Word & Excel to PDF") {
                            scope.launch { drawerState.close() }
                            officeLauncher.launch(arrayOf("*/*"))
                        }
                        DrawerActionRow(Icons.Default.Image, "Images (JPG/PNG) to PDF") {
                            scope.launch { drawerState.close() }
                            imagesLauncher.launch(arrayOf("image/*"))
                        }
                        DrawerActionRow(Icons.Default.MergeType, "Merge Multiple PDFs") {
                            scope.launch { drawerState.close() }
                            mergeLauncher.launch(arrayOf("application/pdf"))
                        }

                        Spacer(Modifier.weight(1f))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(6.dp))

                        DrawerActionRow(Icons.Default.DarkMode, "Theme: ${themeMode.label}") {
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
                label = "AuraNav"
            ) { route ->
                when (route) {
                    is AppRoute.Home -> {
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
                                    },
                                    actions = {
                                        IconButton(onClick = { showNewBlankDialog = true }) {
                                            Icon(Icons.Default.Add, contentDescription = "New Blank PDF")
                                        }
                                    }
                                )
                            }
                        ) { padding ->
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().padding(padding),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
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

                                // 1. DIRECTLY SHOW RECENT PDFs UNDER "Aura PDF Editor"
                                item {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            "Recent PDFs (${recentPdfs.size})",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) }) {
                                            Text("Open PDF", fontSize = 13.sp)
                                        }
                                    }
                                }

                                if (recentPdfs.isEmpty()) {
                                    item {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { showNewBlankDialog = true }
                                        ) {
                                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(28.dp))
                                                Spacer(Modifier.width(12.dp))
                                                Column {
                                                    Text("No recent PDFs yet", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                                    Text(
                                                        "Tap to create a Blank PDF or open any PDF — all Acrobat-style tools work inside one workspace.",
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
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
                                                .clickable { openWorkspace(item.file) }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(24.dp))
                                                Spacer(Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(item.name, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text("${item.sizeKb} KB • Tap to Edit", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

                                // 2. CREATE & CONVERT TOOLS AFTER RECENT FILES
                                item {
                                    Spacer(Modifier.height(6.dp))
                                    Text("Create & Convert", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                }

                                item {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                        HomeFeatureCard(
                                            title = "New Blank PDF",
                                            subtitle = "Full Editor Workspace",
                                            icon = Icons.Default.Add,
                                            modifier = Modifier.weight(1f),
                                            onClick = { showNewBlankDialog = true }
                                        )
                                        HomeFeatureCard(
                                            title = "Word / Excel",
                                            subtitle = ".docx, .xlsx, .csv",
                                            icon = Icons.Default.TableChart,
                                            modifier = Modifier.weight(1f),
                                            onClick = { officeLauncher.launch(arrayOf("*/*")) }
                                        )
                                    }
                                }

                                item {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                        HomeFeatureCard(
                                            title = "Images to PDF",
                                            subtitle = "JPG / PNG to Editor",
                                            icon = Icons.Default.Image,
                                            modifier = Modifier.weight(1f),
                                            onClick = { imagesLauncher.launch(arrayOf("image/*")) }
                                        )
                                        HomeFeatureCard(
                                            title = "Merge PDFs",
                                            subtitle = "Combine & Edit",
                                            icon = Icons.Default.MergeType,
                                            modifier = Modifier.weight(1f),
                                            onClick = { mergeLauncher.launch(arrayOf("application/pdf")) }
                                        )
                                    }
                                }

                                item { Spacer(Modifier.height(24.dp)) }
                            }
                        }
                    }

                    is AppRoute.Workspace -> EditorWorkspaceScreen(
                        file = route.file,
                        storage = storage,
                        onBack = { navigateBack() },
                        onOpenNewFile = { newFile -> openWorkspace(newFile) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeFeatureCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(10.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DrawerActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
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
