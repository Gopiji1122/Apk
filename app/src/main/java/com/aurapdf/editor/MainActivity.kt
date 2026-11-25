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
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurapdf.editor.data.PdfStorage
import com.aurapdf.editor.theme.AuraPdfTheme
import com.aurapdf.editor.theme.PdfThemeMode
import com.aurapdf.editor.ui.CreatorScreen
import com.aurapdf.editor.ui.LinkManagerScreen
import com.aurapdf.editor.ui.OcrScreen
import com.aurapdf.editor.ui.OfficeToPdfScreen
import com.aurapdf.editor.ui.QuickUtilityScreen
import com.aurapdf.editor.ui.ReaderScreen
import com.aurapdf.editor.ui.SignatureScreen
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class AppRoute {
    data object Home : AppRoute()
    data class Reader(val file: File) : AppRoute()
    data object Creator : AppRoute()
    data object OfficeToPdf : AppRoute()
    data object LinkManager : AppRoute()
    data object Signature : AppRoute()
    data object Ocr : AppRoute()
    data class Utility(val toolType: String) : AppRoute()
}

data class ToolItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: AppRoute
)

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

    // Proper navigation back stack so pressing Back ALWAYS returns to the previous page!
    val backStack = remember { mutableStateListOf<AppRoute>(AppRoute.Home) }
    val currentRoute = backStack.last()

    fun navigateTo(route: AppRoute) {
        if (backStack.lastOrNull() != route) {
            backStack.add(route)
        }
    }

    fun navigateBack() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
            recentPdfs = storage.listRecentPdfs()
        }
    }

    // Intercept system Back button: close drawer first, otherwise pop back stack to last page
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
                val imported = withContext(Dispatchers.IO) { storage.importPdfFromUri(uri) }
                recentPdfs = storage.listRecentPdfs()
                navigateTo(AppRoute.Reader(imported))
            }
        }
    }

    val allTools = remember {
        listOf(
            ToolItem("Create Blank PDF", "Text, JPG/PNG & Links", Icons.Default.Add, AppRoute.Creator),
            ToolItem("Word & Excel to PDF", ".docx, .xlsx & .csv", Icons.Default.TableChart, AppRoute.OfficeToPdf),
            ToolItem("PDF Link Manager", "Add or Remove Links", Icons.Default.Link, AppRoute.LinkManager),
            ToolItem("Images to PDF", "JPG / PNG to PDF", Icons.Default.Image, AppRoute.Utility("img2pdf")),
            ToolItem("Merge PDFs", "Combine multiple PDFs", Icons.Default.MergeType, AppRoute.Utility("merge")),
            ToolItem("Split PDF", "Extract page range", Icons.Default.CallSplit, AppRoute.Utility("split")),
            ToolItem("Sign PDF", "Draw & stamp signature", Icons.Default.Create, AppRoute.Signature),
            ToolItem("Offline OCR", "Scan text from PDF", Icons.Default.DocumentScanner, AppRoute.Ocr),
            ToolItem("Rotate Pages", "Rotate 90 degrees", Icons.Default.RotateRight, AppRoute.Utility("rotate")),
            ToolItem("Delete Page", "Remove specific page", Icons.Default.DeleteOutline, AppRoute.Utility("delete_page")),
            ToolItem("Watermark", "Stamp custom text", Icons.Default.WaterDrop, AppRoute.Utility("watermark")),
            ToolItem("Lock PDF", "Add password security", Icons.Default.Lock, AppRoute.Utility("lock")),
            ToolItem("Unlock PDF", "Remove PDF password", Icons.Default.LockOpen, AppRoute.Utility("unlock"))
        )
    }

    AuraPdfTheme(mode = themeMode) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = currentRoute is AppRoute.Home,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = MaterialTheme.colorScheme.surface,
                    drawerContentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(290.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item {
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
                        }

                        item {
                            DrawerMenuRow(
                                icon = Icons.Default.FolderOpen,
                                label = "Open PDF from Storage",
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    openPdfLauncher.launch(arrayOf("application/pdf"))
                                }
                            )
                        }

                        items(allTools) { tool ->
                            DrawerMenuRow(
                                icon = tool.icon,
                                label = tool.title,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    navigateTo(tool.route)
                                }
                            )
                        }

                        item {
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.height(8.dp))
                            DrawerMenuRow(
                                icon = Icons.Default.DarkMode,
                                label = "Theme: ${themeMode.label}",
                                onClick = {
                                    val next = when (themeMode) {
                                        PdfThemeMode.AMOLED -> PdfThemeMode.DARK
                                        PdfThemeMode.DARK -> PdfThemeMode.LIGHT
                                        PdfThemeMode.LIGHT -> PdfThemeMode.AMOLED
                                    }
                                    themeMode = next
                                    storage.themeMode = next
                                }
                            )
                        }
                    }
                }
            }
        ) {
            AnimatedContent(
                targetState = currentRoute,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(150)) },
                label = "ScreenNav"
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
                                        IconButton(onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) }) {
                                            Icon(Icons.Default.FolderOpen, contentDescription = "Open PDF")
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
                                // 1. DIRECTLY SHOW RECENT PDFs FIRST UNDER "Aura PDF Editor"
                                item {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "Recent PDFs (${recentPdfs.size})",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) }) {
                                            Text("Open File", fontSize = 13.sp)
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
                                                .clickable { openPdfLauncher.launch(arrayOf("application/pdf")) }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(28.dp))
                                                Spacer(Modifier.width(12.dp))
                                                Column {
                                                    Text("No recent PDFs yet", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                                    Text(
                                                        "Tap here to open a PDF or create a new one below.",
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    items(recentPdfs.take(6), key = { it.file.absolutePath }) { item ->
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { navigateTo(AppRoute.Reader(item.file)) }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(24.dp))
                                                Spacer(Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(item.name, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text("${item.sizeKb} KB", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                IconButton(onClick = { storage.sharePdf(item.file) }, modifier = Modifier.size(32.dp)) {
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

                                // 2. AFTER RECENT FILES, ALL FEATURES APPEAR BELOW
                                item {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "All PDF Tools",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    )
                                }

                                val paired = allTools.chunked(2)
                                items(paired) { rowTools ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        rowTools.forEach { tool ->
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { navigateTo(tool.route) }
                                            ) {
                                                Column(modifier = Modifier.padding(14.dp)) {
                                                    Icon(tool.icon, contentDescription = null, modifier = Modifier.size(24.dp))
                                                    Spacer(Modifier.height(10.dp))
                                                    Text(tool.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text(tool.subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                            }
                                        }
                                        if (rowTools.size == 1) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                                item { Spacer(Modifier.height(24.dp)) }
                            }
                        }
                    }

                    is AppRoute.Reader -> ReaderScreen(
                        file = route.file,
                        storage = storage,
                        onBack = { navigateBack() }
                    )

                    is AppRoute.Creator -> CreatorScreen(
                        storage = storage,
                        onBack = { navigateBack() },
                        onPdfCreated = { created ->
                            recentPdfs = storage.listRecentPdfs()
                            navigateTo(AppRoute.Reader(created))
                        }
                    )

                    is AppRoute.OfficeToPdf -> OfficeToPdfScreen(
                        storage = storage,
                        onBack = { navigateBack() },
                        onPdfReady = { created ->
                            recentPdfs = storage.listRecentPdfs()
                            navigateTo(AppRoute.Reader(created))
                        }
                    )

                    is AppRoute.LinkManager -> LinkManagerScreen(
                        storage = storage,
                        onBack = { navigateBack() },
                        onPdfReady = { created ->
                            recentPdfs = storage.listRecentPdfs()
                            navigateTo(AppRoute.Reader(created))
                        }
                    )

                    is AppRoute.Signature -> SignatureScreen(
                        storage = storage,
                        onBack = { navigateBack() },
                        onPdfReady = { created ->
                            recentPdfs = storage.listRecentPdfs()
                            navigateTo(AppRoute.Reader(created))
                        }
                    )

                    is AppRoute.Ocr -> OcrScreen(
                        storage = storage,
                        onBack = { navigateBack() }
                    )

                    is AppRoute.Utility -> QuickUtilityScreen(
                        toolType = route.toolType,
                        storage = storage,
                        onBack = { navigateBack() },
                        onPdfReady = { created ->
                            recentPdfs = storage.listRecentPdfs()
                            navigateTo(AppRoute.Reader(created))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerMenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
