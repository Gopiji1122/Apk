package com.gopiji1122.aurapdfeditor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gopiji1122.aurapdfeditor.core.pdfengine.PdfEngine
import com.gopiji1122.aurapdfeditor.core.theme.AuraPdfTheme
import com.gopiji1122.aurapdfeditor.feature.viewer.PdfViewerScreen
import kotlinx.coroutines.launch

private enum class ActiveTool {
    NONE, MERGE, SPLIT, ROTATE, COMPRESS, IMG_TO_PDF, WATERMARK, LOCK, UNLOCK, SCRUB_META, BLANK
}

private data class ToolCard(
    val tool: ActiveTool,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val incomingUri: Uri? = if (intent?.action == Intent.ACTION_VIEW) intent.data else null

        setContent {
            var darkTheme by remember { mutableStateOf(true) }
            AuraPdfTheme(darkTheme = darkTheme) {
                AuraPdfRoot(
                    initialPdfUri = incomingUri,
                    darkTheme = darkTheme,
                    onToggleTheme = { darkTheme = !darkTheme }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraPdfRoot(
    initialPdfUri: Uri?,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var openedPdfUri by remember { mutableStateOf(initialPdfUri) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var activeTool by remember { mutableStateOf(ActiveTool.NONE) }
    var pendingSingleUri by remember { mutableStateOf<Uri?>(null) }
    var pendingMultiUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var inputParam by remember { mutableStateOf("") }
    var showParamDialog by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    val openPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) openedPdfUri = uri
    }

    val savePdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        if (destUri == null) {
            activeTool = ActiveTool.NONE
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            isProcessing = true
            val result = when (activeTool) {
                ActiveTool.BLANK -> PdfEngine.createBlankPdf(context, destUri, 1)
                ActiveTool.MERGE -> PdfEngine.mergePdfs(context, pendingMultiUris, destUri)
                ActiveTool.SPLIT -> PdfEngine.splitExtractPages(context, pendingSingleUri!!, inputParam, destUri)
                ActiveTool.ROTATE -> PdfEngine.rotateAllPages(context, pendingSingleUri!!, 90, destUri)
                ActiveTool.COMPRESS -> PdfEngine.compressPdf(context, pendingSingleUri!!, 0.55f, destUri)
                ActiveTool.IMG_TO_PDF -> PdfEngine.imagesToPdf(context, pendingMultiUris, destUri)
                ActiveTool.WATERMARK -> PdfEngine.addWatermarkAndPageNumbers(context, pendingSingleUri!!, inputParam, true, destUri)
                ActiveTool.LOCK -> PdfEngine.protectPdf(context, pendingSingleUri!!, inputParam, destUri)
                ActiveTool.UNLOCK -> PdfEngine.unlockPdf(context, pendingSingleUri!!, inputParam, destUri)
                ActiveTool.SCRUB_META -> PdfEngine.scrubMetadata(context, pendingSingleUri!!, destUri)
                ActiveTool.NONE -> Result.success("")
            }
            isProcessing = false
            activeTool = ActiveTool.NONE
            snackbarMessage = result.getOrElse { "Error: ${it.message}" }
        }
    }

    val pickSinglePdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            activeTool = ActiveTool.NONE
            return@rememberLauncherForActivityResult
        }
        pendingSingleUri = uri
        when (activeTool) {
            ActiveTool.SPLIT -> {
                inputParam = "1-2"
                showParamDialog = true
            }
            ActiveTool.WATERMARK -> {
                inputParam = "CONFIDENTIAL"
                showParamDialog = true
            }
            ActiveTool.LOCK, ActiveTool.UNLOCK -> {
                inputParam = ""
                showParamDialog = true
            }
            else -> savePdfLauncher.launch("AuraPDF_Output.pdf")
        }
    }

    val pickMultiPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) {
            activeTool = ActiveTool.NONE
            return@rememberLauncherForActivityResult
        }
        pendingMultiUris = uris
        savePdfLauncher.launch("AuraPDF_Merged.pdf")
    }

    val pickMultiImagesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) {
            activeTool = ActiveTool.NONE
            return@rememberLauncherForActivityResult
        }
        pendingMultiUris = uris
        savePdfLauncher.launch("AuraPDF_Images.pdf")
    }

    if (openedPdfUri != null) {
        PdfViewerScreen(
            pdfUri = openedPdfUri!!,
            onBack = { openedPdfUri = null }
        )
        return
    }

    val tools = remember {
        listOf(
            ToolCard(ActiveTool.MERGE, "Merge PDFs", "Combine 2+ files", Icons.Default.MergeType),
            ToolCard(ActiveTool.SPLIT, "Split / Extract", "Save page ranges", Icons.Default.ContentCut),
            ToolCard(ActiveTool.COMPRESS, "Compress PDF", "Reduce file size", Icons.Default.Compress),
            ToolCard(ActiveTool.IMG_TO_PDF, "Image to PDF", "Photos to A4 PDF", Icons.Default.Image),
            ToolCard(ActiveTool.ROTATE, "Rotate Pages", "Rotate 90° CW", Icons.Default.RotateRight),
            ToolCard(ActiveTool.WATERMARK, "Watermark", "Stamp & page #", Icons.Default.BrandingWatermark),
            ToolCard(ActiveTool.LOCK, "Lock PDF", "AES-256 encrypt", Icons.Default.Lock),
            ToolCard(ActiveTool.UNLOCK, "Unlock PDF", "Remove password", Icons.Default.LockOpen),
            ToolCard(ActiveTool.SCRUB_META, "Scrub Metadata", "Wipe hidden info", Icons.Default.CleaningServices),
            ToolCard(ActiveTool.BLANK, "Blank PDF", "Create new A4", Icons.Default.NoteAdd)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aura PDF Editor", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme"
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Build, contentDescription = "Tools") },
                    label = { Text("Offline Tools") }
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) },
                icon = { Icon(Icons.Default.FolderOpen, contentDescription = "Open PDF") },
                text = { Text("Open PDF") }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (selectedTab == 0) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "100% Offline PDF Editor",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Zero internet permission. All viewing, merging, splitting, compression, encryption, and read-aloud happen locally on your device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Browse & Open Local PDF")
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Explore 10 Offline PDF Tools")
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(tools) { item ->
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    activeTool = item.tool
                                    when (item.tool) {
                                        ActiveTool.BLANK -> savePdfLauncher.launch("AuraPDF_Blank.pdf")
                                        ActiveTool.MERGE -> pickMultiPdfLauncher.launch(arrayOf("application/pdf"))
                                        ActiveTool.IMG_TO_PDF -> pickMultiImagesLauncher.launch(arrayOf("image/*"))
                                        else -> pickSinglePdfLauncher.launch(arrayOf("application/pdf"))
                                    }
                                }
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Icon(item.icon, contentDescription = null, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.height(10.dp))
                                Text(item.title, fontWeight = FontWeight.SemiBold)
                                Text(
                                    item.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (isProcessing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(14.dp))
                            Text("Processing offline...")
                        }
                    }
                }
            }

            snackbarMessage?.let { msg ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { snackbarMessage = null }) { Text("OK") }
                    }
                ) {
                    Text(msg)
                }
            }
        }
    }

    if (showParamDialog) {
        val title = when (activeTool) {
            ActiveTool.SPLIT -> "Page Ranges (e.g. 1-3, 5)"
            ActiveTool.WATERMARK -> "Watermark Text"
            ActiveTool.LOCK -> "Set AES-256 Password"
            ActiveTool.UNLOCK -> "Enter Current Password"
            else -> "Input"
        }
        AlertDialog(
            onDismissRequest = {
                showParamDialog = false
                activeTool = ActiveTool.NONE
            },
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = inputParam,
                    onValueChange = { inputParam = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    showParamDialog = false
                    savePdfLauncher.launch("AuraPDF_Output.pdf")
                }) {
                    Text("Continue & Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showParamDialog = false
                    activeTool = ActiveTool.NONE
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}
