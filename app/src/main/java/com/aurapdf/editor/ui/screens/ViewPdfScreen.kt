package com.aurapdf.editor.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aurapdf.editor.data.PdfRepository
import com.aurapdf.editor.engine.PdfBoxEngine
import com.aurapdf.editor.model.BookmarkItem
import com.aurapdf.editor.model.CompressionResult
import com.aurapdf.editor.model.EditorLaunchAction
import com.aurapdf.editor.model.PendingFolderExport
import com.aurapdf.editor.model.PageRangeMode
import com.aurapdf.editor.model.ExportFormat
import com.aurapdf.editor.model.FreehandStroke
import com.aurapdf.editor.model.NormPoint
import com.aurapdf.editor.model.OverlayItem
import com.aurapdf.editor.model.PdfFontFamily
import com.aurapdf.editor.model.RecentPdfItem
import com.aurapdf.editor.model.SearchMatch
import com.aurapdf.editor.ui.components.CompressPdfSheet
import com.aurapdf.editor.ui.components.CreateBlankPdfDialog
import com.aurapdf.editor.ui.components.ExportPdfSheet
import com.aurapdf.editor.ui.components.MergePdfsDialog
import com.aurapdf.editor.ui.components.OcrResultDialog
import com.aurapdf.editor.ui.components.PasswordProtectionDialog
import com.aurapdf.editor.ui.components.ShareUtils
import com.aurapdf.editor.ui.components.SignaturePadDialog
import com.aurapdf.editor.ui.theme.AcrobatRed
import com.aurapdf.editor.ui.theme.ToolCommentBlue
import com.aurapdf.editor.ui.theme.ToolCompressGreen
import com.aurapdf.editor.ui.theme.ToolEditPink
import com.aurapdf.editor.ui.theme.ToolMergeOrange
import com.aurapdf.editor.ui.theme.ToolOrganizeTeal
import com.aurapdf.editor.ui.theme.ToolProtectRed
import com.aurapdf.editor.ui.theme.ToolSignPurple
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ViewerQuickTool {
    NONE, HIGHLIGHT, DRAW
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewPdfScreen(
    pdfPath: String,
    repository: PdfRepository,
    engine: PdfBoxEngine,
    onBack: () -> Unit,
    onOpenEditPdf: (String, EditorLaunchAction) -> Unit,
    onOpenScanner: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val file = remember(pdfPath) { File(pdfPath) }

    var password by remember { mutableStateOf<String?>(null) }
    var isLocked by remember { mutableStateOf(false) }
    var unlockInput by remember { mutableStateOf("") }
    var pageCount by remember { mutableIntStateOf(1) }
    var currentPage by remember { mutableIntStateOf(0) }
    var reloadNonce by remember { mutableIntStateOf(0) }

    // View Settings
    var continuousScroll by remember { mutableStateOf(true) }
    var fitWidth by remember { mutableStateOf(true) }
    var nightInvert by remember { mutableStateOf(false) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Starred & Bookmarks
    var isStarred by remember { mutableStateOf(false) }
    var bookmarks by remember { mutableStateOf<List<BookmarkItem>>(emptyList()) }

    // Page Bitmaps & Extracted Text Cache
    val pageBitmaps = remember { mutableStateMapOf<Int, Bitmap>() }
    var pagesText by remember { mutableStateOf<List<String>>(emptyList()) }

    // Search State
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var caseSensitive by remember { mutableStateOf(false) }
    var wholeWord by remember { mutableStateOf(false) }
    var searchMatches by remember { mutableStateOf<List<SearchMatch>>(emptyList()) }
    var activeMatchIdx by remember { mutableIntStateOf(0) }

    // Offline Android TTS (Read Aloud)
    var isSpeaking by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                instance?.language = Locale.getDefault()
                ttsReady = true
            }
        }
        instance
    }

    DisposableEffect(Unit) {
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    fun toggleReadAloud() {
        if (isSpeaking) {
            tts?.stop()
            isSpeaking = false
        } else if (ttsReady) {
            val textToRead = pagesText.getOrElse(currentPage) { "" }.trim()
            if (textToRead.isEmpty()) {
                Toast.makeText(context, "No selectable text on Page ${currentPage + 1} (try OCR)", Toast.LENGTH_SHORT).show()
            } else {
                isSpeaking = true
                tts?.speak(textToRead, TextToSpeech.QUEUE_FLUSH, null, "aura_page_$currentPage")
            }
        } else {
            Toast.makeText(context, "Offline TTS engine initializing...", Toast.LENGTH_SHORT).show()
        }
    }

    // Bottom Bar Quick Tools & Sheets
    var quickTool by remember { mutableStateOf(ViewerQuickTool.NONE) }
    var liveStroke by remember { mutableStateOf<List<NormPoint>>(emptyList()) }
    var highlightDragStart by remember { mutableStateOf<NormPoint?>(null) }
    var highlightDragEnd by remember { mutableStateOf<NormPoint?>(null) }

    var showMoreToolsGrid by remember { mutableStateOf(false) }
    var showOverflowSheet by remember { mutableStateOf(false) }
    var showPagesGridSheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showCommentDialog by remember { mutableStateOf(false) }
    var showCommentsListSheet by remember { mutableStateOf(false) }
    var pdfComments by remember { mutableStateOf<List<Triple<Int, String, String>>>(emptyList()) }
    var showFillSignQuickSheet by remember { mutableStateOf(false) }
    var showSignaturePad by remember { mutableStateOf(false) }
    var showCompressSheet by remember { mutableStateOf(false) }
    var lastCompressionResult by remember { mutableStateOf<CompressionResult?>(null) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showMergeDialog by remember { mutableStateOf(false) }
    var showCreateBlankDialog by remember { mutableStateOf(false) }
    var ocrResultText by remember { mutableStateOf<String?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    var recentPdfs by remember { mutableStateOf<List<RecentPdfItem>>(emptyList()) }
    var pendingExportShareFile by remember { mutableStateOf<File?>(null) }
    var pendingFolderExport by remember { mutableStateOf<PendingFolderExport?>(null) }

    val listState = rememberLazyListState()

    val importForMergeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching { repository.importPdfFromUri(uri) }
                    .onSuccess {
                        recentPdfs = repository.loadRecentPdfs()
                        Toast.makeText(context, "Imported ${it.name}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    // SAF Save a Copy Launcher
    val saveCopySafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri: Uri? ->
        if (destUri != null && file.exists()) {
            runCatching {
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
                Toast.makeText(context, "Saved a copy to device", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val folderExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        val req = pendingFolderExport
        if (treeUri != null && req != null) {
            isWorking = true
            scope.launch {
                runCatching {
                    engine.exportPdfImagesToFolder(
                        file = file,
                        pageRangeMode = req.rangeMode,
                        customRange = req.customRange,
                        currentPage = currentPage,
                        format = req.format,
                        jpegQuality = req.jpegQuality,
                        dpi = req.dpi,
                        treeUri = treeUri,
                        password = password
                    )
                }.onSuccess { count ->
                    isWorking = false
                    Toast.makeText(context, "Saved $count individual ${req.format.extension.uppercase()} files to folder", Toast.LENGTH_LONG).show()
                }.onFailure {
                    isWorking = false
                    Toast.makeText(context, "Folder export failed: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        pendingFolderExport = null
    }

    val exportSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destUri: Uri? ->
        val src = pendingExportShareFile
        if (destUri != null && src != null && src.exists()) {
            runCatching {
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    src.inputStream().use { it.copyTo(out) }
                }
                Toast.makeText(context, "Exported ${src.name}", Toast.LENGTH_SHORT).show()
            }
        }
        pendingExportShareFile = null
    }

    // Layered BackHandler
    BackHandler {
        when {
            quickTool != ViewerQuickTool.NONE -> quickTool = ViewerQuickTool.NONE
            showSearchBar -> {
                showSearchBar = false
                searchQuery = ""
                searchMatches = emptyList()
            }
            showMoreToolsGrid -> showMoreToolsGrid = false
            showOverflowSheet -> showOverflowSheet = false
            showPagesGridSheet -> showPagesGridSheet = false
            showBookmarksSheet -> showBookmarksSheet = false
            showCommentsListSheet -> showCommentsListSheet = false
            else -> onBack()
        }
    }

    LaunchedEffect(pdfPath, password, reloadNonce) {
        val encrypted = engine.checkIsPasswordLocked(file)
        if (encrypted && password.isNullOrEmpty()) {
            isLocked = true
            return@LaunchedEffect
        }
        isLocked = false
        pageBitmaps.clear()
        pageCount = engine.getPageCount(file, password)
        pagesText = engine.extractAllPagesText(file, password)
        bookmarks = repository.getBookmarks(pdfPath)
        pdfComments = engine.getComments(file, password)
        recentPdfs = repository.loadRecentPdfs()
        isStarred = recentPdfs.firstOrNull { it.filePath == pdfPath }?.isStarred ?: false
    }

    LaunchedEffect(searchQuery, caseSensitive, wholeWord, pagesText) {
        if (searchQuery.isBlank()) {
            searchMatches = emptyList()
            activeMatchIdx = 0
        } else {
            searchMatches = engine.searchInPages(pagesText, searchQuery, caseSensitive, wholeWord)
            activeMatchIdx = 0
            if (searchMatches.isNotEmpty()) {
                val targetPage = searchMatches.first().pageIndex
                currentPage = targetPage
                if (continuousScroll) listState.scrollToItem(targetPage)
            }
        }
    }

    val nightInvertFilter = remember {
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

    if (isLocked) {
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text("Password Protected PDF", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter password to unlock \"${file.name}\":")
                    OutlinedTextField(
                        value = unlockInput,
                        onValueChange = { unlockInput = it },
                        label = { Text("Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            if (engine.verifyPassword(file, unlockInput)) {
                                password = unlockInput
                                isLocked = false
                            } else {
                                Toast.makeText(context, "Incorrect password", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AcrobatRed)
                ) {
                    Text("Unlock", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = onBack) { Text("Back") }
            }
        )
        return
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = {
                        Column {
                            Text(
                                file.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "Page ${currentPage + 1} of $pageCount",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { toggleReadAloud() }) {
                            Icon(
                                if (isSpeaking) Icons.Default.Stop else Icons.Default.RecordVoiceOver,
                                contentDescription = "Read Aloud",
                                tint = if (isSpeaking) AcrobatRed else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { nightInvert = !nightInvert }) {
                            Icon(
                                Icons.Default.DarkMode,
                                contentDescription = "Night Invert",
                                tint = if (nightInvert) AcrobatRed else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { showSearchBar = !showSearchBar }) {
                            Icon(
                                if (showSearchBar) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search in PDF"
                            )
                        }
                        IconButton(onClick = { ShareUtils.shareFile(context, file) }) {
                            Icon(Icons.Default.Share, contentDescription = "Share PDF")
                        }
                        IconButton(onClick = { showOverflowSheet = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Full-Freedom Search Bar + Match Jump Controls + Clickable Snippets
                AnimatedVisibility(
                    visible = showSearchBar,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Search across all $pageCount pages...") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        if (searchMatches.isNotEmpty()) {
                                            activeMatchIdx = (activeMatchIdx - 1 + searchMatches.size) % searchMatches.size
                                            val p = searchMatches[activeMatchIdx].pageIndex
                                            currentPage = p
                                            if (continuousScroll) scope.launch { listState.scrollToItem(p) }
                                        }
                                    },
                                    enabled = searchMatches.isNotEmpty()
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous Match")
                                }
                                IconButton(
                                    onClick = {
                                        if (searchMatches.isNotEmpty()) {
                                            activeMatchIdx = (activeMatchIdx + 1) % searchMatches.size
                                            val p = searchMatches[activeMatchIdx].pageIndex
                                            currentPage = p
                                            if (continuousScroll) scope.launch { listState.scrollToItem(p) }
                                        }
                                    },
                                    enabled = searchMatches.isNotEmpty()
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next Match")
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                FilterChip(
                                    selected = caseSensitive,
                                    onClick = { caseSensitive = !caseSensitive },
                                    label = { Text("Case-Sensitive") }
                                )
                                FilterChip(
                                    selected = wholeWord,
                                    onClick = { wholeWord = !wholeWord },
                                    label = { Text("Whole Word") }
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = if (searchQuery.isBlank()) ""
                                    else if (searchMatches.isEmpty()) "0 matches"
                                    else "${activeMatchIdx + 1} / ${searchMatches.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AcrobatRed
                                )
                            }

                            if (searchMatches.isNotEmpty()) {
                                LazyColumn(modifier = Modifier.height(110.dp).padding(top = 4.dp)) {
                                    items(searchMatches) { match ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    currentPage = match.pageIndex
                                                    if (continuousScroll) {
                                                        scope.launch { listState.scrollToItem(match.pageIndex) }
                                                    }
                                                }
                                                .padding(vertical = 4.dp, horizontal = 4.dp)
                                        ) {
                                            Surface(
                                                color = AcrobatRed,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    "P${match.pageIndex + 1}",
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text(
                                                text = match.snippet,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(start = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Active Quick Tool Banner (Highlight / Draw)
                AnimatedVisibility(
                    visible = quickTool != ViewerQuickTool.NONE,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        color = ToolCommentBlue,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (quickTool == ViewerQuickTool.HIGHLIGHT)
                                    "Drag across Page ${currentPage + 1} to highlight"
                                else "Draw freehand on Page ${currentPage + 1}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge
                            )
                            TextButton(onClick = { quickTool = ViewerQuickTool.NONE }) {
                                Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Clean 6-item Adobe Acrobat Bottom Bar
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = false,
                    onClick = { onOpenEditPdf(pdfPath, EditorLaunchAction.NONE) },
                    icon = { Icon(Icons.Default.Edit, contentDescription = "Edit PDF", tint = ToolEditPink) },
                    label = { Text("Edit PDF", maxLines = 1) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { showCommentsListSheet = true },
                    icon = { Icon(Icons.Default.Comment, contentDescription = "Comment", tint = ToolCommentBlue) },
                    label = { Text("Comment", maxLines = 1) }
                )
                NavigationBarItem(
                    selected = quickTool == ViewerQuickTool.HIGHLIGHT,
                    onClick = {
                        quickTool = if (quickTool == ViewerQuickTool.HIGHLIGHT) ViewerQuickTool.NONE
                        else ViewerQuickTool.HIGHLIGHT
                    },
                    icon = { Icon(Icons.Default.Create, contentDescription = "Highlight", tint = Color(0xFFFBC02D)) },
                    label = { Text("Highlight", maxLines = 1) }
                )
                NavigationBarItem(
                    selected = quickTool == ViewerQuickTool.DRAW,
                    onClick = {
                        quickTool = if (quickTool == ViewerQuickTool.DRAW) ViewerQuickTool.NONE
                        else ViewerQuickTool.DRAW
                    },
                    icon = { Icon(Icons.Default.Draw, contentDescription = "Draw", tint = ToolOrganizeTeal) },
                    label = { Text("Draw", maxLines = 1) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { showFillSignQuickSheet = true },
                    icon = { Icon(Icons.Default.Gesture, contentDescription = "Fill & Sign", tint = ToolSignPurple) },
                    label = { Text("Sign", maxLines = 1) }
                )
                NavigationBarItem(
                    selected = showMoreToolsGrid,
                    onClick = { showMoreToolsGrid = true },
                    icon = { Icon(Icons.Default.MoreHoriz, contentDescription = "More Tools", tint = AcrobatRed) },
                    label = { Text("More", maxLines = 1) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (continuousScroll) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(pageCount) { pageIdx ->
                        LaunchedEffect(pageIdx, reloadNonce) {
                            currentPage = pageIdx
                            if (!pageBitmaps.containsKey(pageIdx)) {
                                engine.renderPageBitmap(file, pageIdx, 1280, password)?.let { bmp ->
                                    pageBitmaps[pageIdx] = bmp
                                }
                            }
                        }
                        ViewerPageCard(
                            pageIndex = pageIdx,
                            bitmap = pageBitmaps[pageIdx],
                            fitWidth = fitWidth,
                            nightInvert = nightInvert,
                            quickTool = if (pageIdx == currentPage) quickTool else ViewerQuickTool.NONE,
                            liveStroke = if (pageIdx == currentPage) liveStroke else emptyList(),
                            highlightStart = if (pageIdx == currentPage) highlightDragStart else null,
                            highlightEnd = if (pageIdx == currentPage) highlightDragEnd else null,
                            onPageFocused = { currentPage = pageIdx },
                            onStrokeUpdate = { liveStroke = it },
                            onStrokeCommit = { pts ->
                                liveStroke = emptyList()
                                if (pts.size >= 2) {
                                    scope.launch {
                                        engine.saveEditsToPdf(
                                            file,
                                            emptyList(),
                                            listOf(FreehandStroke(pageIndex = pageIdx, points = pts, colorArgb = 0xFFE52521)),
                                            password
                                        )
                                        reloadNonce++
                                    }
                                }
                            },
                            onHighlightUpdate = { s, e ->
                                highlightDragStart = s
                                highlightDragEnd = e
                            },
                            onHighlightCommit = { s, e ->
                                highlightDragStart = null
                                highlightDragEnd = null
                                val x = minOf(s.x, e.x)
                                val y = minOf(s.y, e.y)
                                val w = kotlin.math.abs(e.x - s.x).coerceAtLeast(0.04f)
                                val h = kotlin.math.abs(e.y - s.y).coerceAtLeast(0.025f)
                                scope.launch {
                                    engine.saveEditsToPdf(
                                        file,
                                        listOf(
                                            OverlayItem.HighlightOverlay(
                                                pageIndex = pageIdx,
                                                xNorm = x,
                                                yNorm = y,
                                                wNorm = w,
                                                hNorm = h
                                            )
                                        ),
                                        emptyList(),
                                        password
                                    )
                                    reloadNonce++
                                }
                            }
                        )
                    }
                }
            } else {
                // Single Page Mode with Pinch-to-Zoom & Prev/Next controls
                LaunchedEffect(currentPage, reloadNonce) {
                    if (!pageBitmaps.containsKey(currentPage)) {
                        engine.renderPageBitmap(file, currentPage, 1400, password)?.let { bmp ->
                            pageBitmaps[currentPage] = bmp
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val animatedZoom by animateFloatAsState(
                        targetValue = zoomScale,
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                        label = "single_page_zoom"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        if (zoomScale > 1.15f) {
                                            zoomScale = 1f
                                            panOffset = Offset.Zero
                                        } else {
                                            zoomScale = 2.2f
                                        }
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    zoomScale = (zoomScale * zoom).coerceIn(1f, 3.5f)
                                    panOffset = if (zoomScale > 1.01f) panOffset + pan else Offset.Zero
                                }
                            }
                            .graphicsLayer(
                                scaleX = animatedZoom,
                                scaleY = animatedZoom,
                                translationX = panOffset.x,
                                translationY = panOffset.y
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        ViewerPageCard(
                            pageIndex = currentPage,
                            bitmap = pageBitmaps[currentPage],
                            fitWidth = fitWidth,
                            nightInvert = nightInvert,
                            quickTool = quickTool,
                            liveStroke = liveStroke,
                            highlightStart = highlightDragStart,
                            highlightEnd = highlightDragEnd,
                            onPageFocused = {},
                            onStrokeUpdate = { liveStroke = it },
                            onStrokeCommit = { pts ->
                                liveStroke = emptyList()
                                if (pts.size >= 2) {
                                    scope.launch {
                                        engine.saveEditsToPdf(
                                            file,
                                            emptyList(),
                                            listOf(FreehandStroke(pageIndex = currentPage, points = pts, colorArgb = 0xFFE52521)),
                                            password
                                        )
                                        reloadNonce++
                                    }
                                }
                            },
                            onHighlightUpdate = { s, e ->
                                highlightDragStart = s
                                highlightDragEnd = e
                            },
                            onHighlightCommit = { s, e ->
                                highlightDragStart = null
                                highlightDragEnd = null
                                val x = minOf(s.x, e.x)
                                val y = minOf(s.y, e.y)
                                val w = kotlin.math.abs(e.x - s.x).coerceAtLeast(0.04f)
                                val h = kotlin.math.abs(e.y - s.y).coerceAtLeast(0.025f)
                                scope.launch {
                                    engine.saveEditsToPdf(
                                        file,
                                        listOf(
                                            OverlayItem.HighlightOverlay(
                                                pageIndex = currentPage,
                                                xNorm = x,
                                                yNorm = y,
                                                wNorm = w,
                                                hNorm = h
                                            )
                                        ),
                                        emptyList(),
                                        password
                                    )
                                    reloadNonce++
                                }
                            }
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { currentPage = (currentPage - 1).coerceAtLeast(0) },
                            enabled = currentPage > 0
                        ) {
                            Text("Previous")
                        }
                        Text("Page ${currentPage + 1} / $pageCount", fontWeight = FontWeight.Bold)
                        OutlinedButton(
                            onClick = { currentPage = (currentPage + 1).coerceAtMost(pageCount - 1) },
                            enabled = currentPage < pageCount - 1
                        ) {
                            Text("Next")
                        }
                    }
                }
            }

            // Floating Acrobat-Style Translucent Page Pill
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 16.dp)
                    .clickable { showPagesGridSheet = true }
            ) {
                Text(
                    text = "${currentPage + 1} / $pageCount",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
    }

    // === 3-COLUMN COLOR-CODED ACROBAT "MORE TOOLS" GRID SHEET ===
    if (showMoreToolsGrid) {
        ModalBottomSheet(
            onDismissRequest = { showMoreToolsGrid = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "All Acrobat Tools",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                val gridItems = listOf(
                    Triple("Export PDF", Icons.Default.FileDownload, ToolCommentBlue) to {
                        showMoreToolsGrid = false
                        showExportSheet = true
                    },
                    Triple("Combine Files", Icons.Default.MergeType, ToolMergeOrange) to {
                        showMoreToolsGrid = false
                        showMergeDialog = true
                    },
                    Triple("Compress PDF", Icons.Default.Compress, ToolCompressGreen) to {
                        showMoreToolsGrid = false
                        showCompressSheet = true
                    },
                    Triple("Set Password", Icons.Default.Lock, ToolProtectRed) to {
                        showMoreToolsGrid = false
                        showPasswordDialog = true
                    },
                    Triple("Organize Pages", Icons.Default.GridView, ToolOrganizeTeal) to {
                        showMoreToolsGrid = false
                        onOpenEditPdf(pdfPath, EditorLaunchAction.ORGANIZE)
                    },
                    Triple("Crop Pages", Icons.Default.Crop, ToolOrganizeTeal) to {
                        showMoreToolsGrid = false
                        onOpenEditPdf(pdfPath, EditorLaunchAction.CROP)
                    },
                    Triple("Recognize Text", Icons.Default.Visibility, ToolSignPurple) to {
                        showMoreToolsGrid = false
                        isWorking = true
                        scope.launch {
                            runCatching { engine.runOfflineOcrOnPage(file, currentPage, password) }
                                .onSuccess {
                                    isWorking = false
                                    ocrResultText = it
                                }
                                .onFailure {
                                    isWorking = false
                                    Toast.makeText(context, "OCR failed: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    },
                    Triple("Create PDF", Icons.Default.NoteAdd, ToolEditPink) to {
                        showMoreToolsGrid = false
                        showCreateBlankDialog = true
                    },
                    Triple("Add Text", Icons.Default.TextFields, ToolEditPink) to {
                        showMoreToolsGrid = false
                        onOpenEditPdf(pdfPath, EditorLaunchAction.ADD_TEXT)
                    },
                    Triple("New Scan", Icons.Default.DocumentScanner, AcrobatRed) to {
                        showMoreToolsGrid = false
                        onOpenScanner()
                    },
                    Triple("Read Aloud", Icons.Default.RecordVoiceOver, ToolCommentBlue) to {
                        showMoreToolsGrid = false
                        toggleReadAloud()
                    }
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.height(330.dp)
                ) {
                    items(gridItems.size) { idx ->
                        val (meta, action) = gridItems[idx]
                        val (label, icon, color) = meta
                        Card(
                            onClick = action,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp, horizontal = 6.dp)
                            ) {
                                Surface(
                                    color = color.copy(alpha = 0.18f),
                                    shape = CircleShape,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(icon, contentDescription = label, tint = color)
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    // === 3-DOT (⋮) BOTTOM SHEET ===
    if (showOverflowSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOverflowSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // File Header + Star Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(file.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${Formatter.formatShortFileSize(context, file.length())} • $pageCount pages",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        isStarred = repository.toggleStar(pdfPath)
                    }) {
                        Icon(
                            if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Star",
                            tint = if (isStarred) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                HorizontalDivider()

                // View Settings
                Text("View Settings", style = MaterialTheme.typography.labelLarge, color = AcrobatRed)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = continuousScroll,
                        onClick = { continuousScroll = true },
                        label = { Text("Continuous Scroll") }
                    )
                    FilterChip(
                        selected = !continuousScroll,
                        onClick = { continuousScroll = false },
                        label = { Text("Single Page") }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Fit Width")
                    Switch(checked = fitWidth, onCheckedChange = { fitWidth = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Night Invert Mode")
                    Switch(checked = nightInvert, onCheckedChange = { nightInvert = it })
                }

                HorizontalDivider()

                OverflowMenuRow(Icons.Default.GridView, "Pages Thumbnail Grid") {
                    showOverflowSheet = false
                    showPagesGridSheet = true
                }
                OverflowMenuRow(Icons.Default.Bookmark, "Bookmarks (${bookmarks.size})") {
                    showOverflowSheet = false
                    showBookmarksSheet = true
                }
                OverflowMenuRow(Icons.Default.Comment, "Comment List (${pdfComments.size})") {
                    showOverflowSheet = false
                    showCommentsListSheet = true
                }
                OverflowMenuRow(Icons.Default.RecordVoiceOver, if (isSpeaking) "Stop Read Aloud" else "Read Aloud") {
                    showOverflowSheet = false
                    toggleReadAloud()
                }
                OverflowMenuRow(Icons.Default.Edit, "Edit PDF") {
                    showOverflowSheet = false
                    onOpenEditPdf(pdfPath, EditorLaunchAction.NONE)
                }
                OverflowMenuRow(Icons.Default.FileDownload, "Export as...") {
                    showOverflowSheet = false
                    showExportSheet = true
                }
                OverflowMenuRow(Icons.Default.Compress, "Compress PDF") {
                    showOverflowSheet = false
                    showCompressSheet = true
                }
                OverflowMenuRow(Icons.Default.MergeType, "Combine Files") {
                    showOverflowSheet = false
                    showMergeDialog = true
                }
                OverflowMenuRow(Icons.Default.Lock, "Set Password") {
                    showOverflowSheet = false
                    showPasswordDialog = true
                }
                OverflowMenuRow(Icons.Default.NoteAdd, "Create PDF") {
                    showOverflowSheet = false
                    showCreateBlankDialog = true
                }
                OverflowMenuRow(Icons.Default.FileDownload, "Save a Copy") {
                    showOverflowSheet = false
                    saveCopySafLauncher.launch(file.name)
                }
                OverflowMenuRow(Icons.Default.Print, "Print") {
                    showOverflowSheet = false
                    engine.printPdf(context, file, password)
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // === PAGES THUMBNAIL GRID SHEET ===
    if (showPagesGridSheet) {
        ModalBottomSheet(onDismissRequest = { showPagesGridSheet = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Jump to Page ($pageCount Pages)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.height(380.dp)
                ) {
                    items(pageCount) { idx ->
                        LaunchedEffect(idx) {
                            if (!pageBitmaps.containsKey(idx)) {
                                engine.renderPageBitmap(file, idx, 360, password)?.let { pageBitmaps[idx] = it }
                            }
                        }
                        Card(
                            onClick = {
                                currentPage = idx
                                showPagesGridSheet = false
                                if (continuousScroll) scope.launch { listState.scrollToItem(idx) }
                            },
                            border = if (idx == currentPage) androidx.compose.foundation.BorderStroke(2.dp, AcrobatRed) else null
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(6.dp)
                            ) {
                                val bmp = pageBitmaps[idx]
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Page ${idx + 1}",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                            .background(Color.White)
                                    )
                                } else {
                                    Box(Modifier.fillMaxWidth().height(110.dp).background(Color.White))
                                }
                                Text("Page ${idx + 1}", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    // === BOOKMARKS SHEET ===
    if (showBookmarksSheet) {
        var bmTitle by remember { mutableStateOf("Page ${currentPage + 1}") }
        ModalBottomSheet(onDismissRequest = { showBookmarksSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Bookmarks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = bmTitle,
                        onValueChange = { bmTitle = it },
                        label = { Text("Bookmark Title (Page ${currentPage + 1})") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            bookmarks = repository.addBookmark(pdfPath, currentPage, bmTitle)
                            bmTitle = "Page ${currentPage + 1}"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AcrobatRed)
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = null, tint = Color.White)
                    }
                }
                if (bookmarks.isEmpty()) {
                    Text("No bookmarks saved for this PDF.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(modifier = Modifier.height(220.dp)) {
                        items(bookmarks, key = { it.id }) { bm ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentPage = bm.pageIndex.coerceIn(0, pageCount - 1)
                                        showBookmarksSheet = false
                                        if (continuousScroll) scope.launch { listState.scrollToItem(currentPage) }
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Bookmark, contentDescription = null, tint = AcrobatRed)
                                Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                    Text(bm.title, fontWeight = FontWeight.SemiBold)
                                    Text("Page ${bm.pageIndex + 1}", style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = {
                                    bookmarks = repository.deleteBookmark(pdfPath, bm.id)
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Bookmark", tint = AcrobatRed)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }

    // === COMMENT LIST SHEET ===
    if (showCommentsListSheet) {
        ModalBottomSheet(onDismissRequest = { showCommentsListSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Comments (${pdfComments.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = {
                            showCommentsListSheet = false
                            showCommentDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ToolCommentBlue)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Note", color = Color.White)
                    }
                }
                if (pdfComments.isEmpty()) {
                    Text(
                        "No comments or sticky notes yet. Tap Add Note to place one on Page ${currentPage + 1}.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.height(220.dp)) {
                        items(pdfComments) { (pIdx, author, body) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentPage = pIdx.coerceIn(0, pageCount - 1)
                                        showCommentsListSheet = false
                                        if (continuousScroll) scope.launch { listState.scrollToItem(currentPage) }
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Comment, contentDescription = null, tint = ToolCommentBlue)
                                Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                    Text("Page ${pIdx + 1} • $author", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text(body, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }

    // === QUICK STICKY NOTE DIALOG ===
    if (showCommentDialog) {
        var noteText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCommentDialog = false },
            title = { Text("Add Sticky Note (Page ${currentPage + 1})", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Comment Note") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCommentDialog = false
                        scope.launch {
                            engine.saveEditsToPdf(
                                file,
                                listOf(OverlayItem.StickyNoteOverlay(pageIndex = currentPage, noteText = noteText)),
                                emptyList(),
                                password
                            )
                            reloadNonce++
                            Toast.makeText(context, "Sticky note added", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = noteText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = ToolCommentBlue)
                ) {
                    Text("Add Note", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCommentDialog = false }) { Text("Cancel") }
            }
        )
    }

    // === QUICK FILL & SIGN SHEET ===
    if (showFillSignQuickSheet) {
        ModalBottomSheet(onDismissRequest = { showFillSignQuickSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Quick Fill & Sign — Page ${currentPage + 1}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Button(
                    onClick = {
                        showFillSignQuickSheet = false
                        showSignaturePad = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ToolSignPurple),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Gesture, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Draw Signature / Digital Cert", color = Color.White)
                }
                OutlinedButton(
                    onClick = {
                        showFillSignQuickSheet = false
                        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        scope.launch {
                            engine.saveEditsToPdf(
                                file,
                                listOf(
                                    OverlayItem.TextOverlay(
                                        pageIndex = currentPage,
                                        xNorm = 0.62f,
                                        yNorm = 0.82f,
                                        text = today,
                                        fontSizeSp = 14f,
                                        fontFamily = PdfFontFamily.SANS
                                    )
                                ),
                                emptyList(),
                                password
                            )
                            reloadNonce++
                            Toast.makeText(context, "Stamped today's date ($today)", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Stamp Today's Date")
                }
                OutlinedButton(
                    onClick = {
                        showFillSignQuickSheet = false
                        scope.launch {
                            engine.saveEditsToPdf(
                                file,
                                listOf(
                                    OverlayItem.TextOverlay(
                                        pageIndex = currentPage,
                                        xNorm = 0.25f,
                                        yNorm = 0.45f,
                                        text = "✓",
                                        fontSizeSp = 22f,
                                        isBold = true,
                                        colorArgb = 0xFF2E7D32
                                    )
                                ),
                                emptyList(),
                                password
                            )
                            reloadNonce++
                            Toast.makeText(context, "Checkmark placed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Place Checkmark (✓)")
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }

    if (showSignaturePad) {
        SignaturePadDialog(
            pageIndex = currentPage,
            onConfirm = { sigOverlay ->
                showSignaturePad = false
                scope.launch {
                    engine.saveEditsToPdf(file, listOf(sigOverlay), emptyList(), password)
                    reloadNonce++
                    Toast.makeText(context, "Signature applied to Page ${currentPage + 1}", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showSignaturePad = false }
        )
    }

    if (showCompressSheet) {
        CompressPdfSheet(
            file = file,
            lastResult = lastCompressionResult,
            isWorking = isWorking,
            onRunCompress = { config, replace ->
                isWorking = true
                scope.launch {
                    runCatching { engine.compressPdf(file, config, replace, password) }
                        .onSuccess { res ->
                            isWorking = false
                            lastCompressionResult = res
                            reloadNonce++
                        }
                        .onFailure {
                            isWorking = false
                            Toast.makeText(context, "Compress failed: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            },
            onDismiss = { showCompressSheet = false }
        )
    }

    if (showExportSheet) {
        ExportPdfSheet(
            totalPages = pageCount,
            currentPage = currentPage,
            isWorking = isWorking,
            onExport = { rangeMode, customRange, fmt, quality, dpi, zip, saveSaf ->
                val isMultiImageFolder = saveSaf && !zip &&
                    (fmt == ExportFormat.PNG || fmt == ExportFormat.JPEG) &&
                    (rangeMode != PageRangeMode.CURRENT && pageCount > 1)
                if (isMultiImageFolder) {
                    showExportSheet = false
                    pendingFolderExport = PendingFolderExport(rangeMode, customRange, fmt, quality, dpi)
                    folderExportLauncher.launch(null)
                } else {
                    isWorking = true
                    scope.launch {
                        runCatching {
                            engine.exportPdf(file, rangeMode, customRange, currentPage, fmt, quality, dpi, zip, password)
                        }.onSuccess { exportedFile ->
                            isWorking = false
                            showExportSheet = false
                            if (saveSaf) {
                                pendingExportShareFile = exportedFile
                                exportSafLauncher.launch(exportedFile.name)
                            } else {
                                ShareUtils.shareFile(context, exportedFile, fmt.mimeType)
                            }
                        }.onFailure {
                            isWorking = false
                            Toast.makeText(context, "Export failed: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onDismiss = { showExportSheet = false }
        )
    }

    if (showPasswordDialog) {
        PasswordProtectionDialog(
            isCurrentlyEncrypted = !password.isNullOrEmpty(),
            onSetPassword = { newPwd ->
                showPasswordDialog = false
                scope.launch {
                    runCatching { engine.setPasswordProtection(file, newPwd, password) }
                        .onSuccess {
                            password = newPwd
                            Toast.makeText(context, "128-bit password applied", Toast.LENGTH_SHORT).show()
                        }
                        .onFailure {
                            Toast.makeText(context, "Encryption failed: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            },
            onRemovePassword = { curPwd ->
                showPasswordDialog = false
                scope.launch {
                    runCatching { engine.removePasswordProtection(file, curPwd) }
                        .onSuccess {
                            password = null
                            reloadNonce++
                            Toast.makeText(context, "Password protection removed", Toast.LENGTH_SHORT).show()
                        }
                        .onFailure {
                            Toast.makeText(context, "Incorrect password", Toast.LENGTH_SHORT).show()
                        }
                }
            },
            onDismiss = { showPasswordDialog = false }
        )
    }

    if (showMergeDialog) {
        MergePdfsDialog(
            availablePdfs = recentPdfs,
            onPickFromStorage = { importForMergeLauncher.launch(arrayOf("application/pdf")) },
            onMerge = { paths, outTitle ->
                showMergeDialog = false
                scope.launch {
                    runCatching { repository.mergePdfs(paths, outTitle) }
                        .onSuccess { merged ->
                            Toast.makeText(context, "Created ${merged.name}", Toast.LENGTH_SHORT).show()
                        }
                }
            },
            onDismiss = { showMergeDialog = false }
        )
    }

    if (showCreateBlankDialog) {
        CreateBlankPdfDialog(
            onDismiss = { showCreateBlankDialog = false },
            onCreate = { title, size, orient ->
                showCreateBlankDialog = false
                scope.launch {
                    val created = repository.createBlankPdf(title, size, orient)
                    onOpenEditPdf(created.absolutePath, EditorLaunchAction.NONE)
                }
            }
        )
    }

    ocrResultText?.let { recognized ->
        OcrResultDialog(
            pageIndex = currentPage,
            recognizedText = recognized,
            onDismiss = { ocrResultText = null }
        )
    }
}

@Composable
private fun OverflowMenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ViewerPageCard(
    pageIndex: Int,
    bitmap: Bitmap?,
    fitWidth: Boolean,
    nightInvert: Boolean,
    quickTool: ViewerQuickTool,
    liveStroke: List<NormPoint>,
    highlightStart: NormPoint?,
    highlightEnd: NormPoint?,
    onPageFocused: () -> Unit,
    onStrokeUpdate: (List<NormPoint>) -> Unit,
    onStrokeCommit: (List<NormPoint>) -> Unit,
    onHighlightUpdate: (NormPoint, NormPoint) -> Unit,
    onHighlightCommit: (NormPoint, NormPoint) -> Unit
) {
    val invertFilter = remember {
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
    Card(
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .then(if (fitWidth) Modifier.fillMaxWidth() else Modifier.widthIn(max = 310.dp))
            .clickable { onPageFocused() }
    ) {
        val aspect = if (bitmap != null && bitmap.height > 0) {
            bitmap.width.toFloat() / bitmap.height.toFloat()
        } else 0.707f

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect.coerceIn(0.4f, 2.0f))
                .background(Color.White)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    contentScale = ContentScale.FillBounds,
                    colorFilter = if (nightInvert) invertFilter else null,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (quickTool != ViewerQuickTool.NONE) {
                val latestStroke by rememberUpdatedState(liveStroke)
                val latestHlStart by rememberUpdatedState(highlightStart)
                val latestHlEnd by rememberUpdatedState(highlightEnd)
                val latestOnStrokeUpdate by rememberUpdatedState(onStrokeUpdate)
                val latestOnStrokeCommit by rememberUpdatedState(onStrokeCommit)
                val latestOnHighlightUpdate by rememberUpdatedState(onHighlightUpdate)
                val latestOnHighlightCommit by rememberUpdatedState(onHighlightCommit)

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(quickTool, pageIndex) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val pt = NormPoint(
                                        (offset.x / size.width).coerceIn(0f, 1f),
                                        (offset.y / size.height).coerceIn(0f, 1f)
                                    )
                                    if (quickTool == ViewerQuickTool.DRAW) {
                                        latestOnStrokeUpdate(listOf(pt))
                                    } else {
                                        latestOnHighlightUpdate(pt, pt)
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val pt = NormPoint(
                                        (change.position.x / size.width).coerceIn(0f, 1f),
                                        (change.position.y / size.height).coerceIn(0f, 1f)
                                    )
                                    if (quickTool == ViewerQuickTool.DRAW) {
                                        latestOnStrokeUpdate(latestStroke + pt)
                                    } else {
                                        val s = latestHlStart
                                        if (s != null) latestOnHighlightUpdate(s, pt)
                                    }
                                },
                                onDragEnd = {
                                    if (quickTool == ViewerQuickTool.DRAW) {
                                        latestOnStrokeCommit(latestStroke)
                                    } else {
                                        val s = latestHlStart
                                        val e = latestHlEnd
                                        if (s != null && e != null) latestOnHighlightCommit(s, e)
                                    }
                                }
                            )
                        }
                ) {
                    if (liveStroke.size >= 2) {
                        val path = Path()
                        path.moveTo(liveStroke[0].x * size.width, liveStroke[0].y * size.height)
                        for (pt in liveStroke.drop(1)) {
                            path.lineTo(pt.x * size.width, pt.y * size.height)
                        }
                        drawPath(path, color = AcrobatRed, style = Stroke(width = 6f, cap = StrokeCap.Round))
                    }
                    if (highlightStart != null && highlightEnd != null) {
                        val left = minOf(highlightStart.x, highlightEnd.x) * size.width
                        val top = minOf(highlightStart.y, highlightEnd.y) * size.height
                        val w = kotlin.math.abs(highlightEnd.x - highlightStart.x) * size.width
                        val h = kotlin.math.abs(highlightEnd.y - highlightStart.y) * size.height
                        drawRect(
                            color = Color(0x66FFEB3B),
                            topLeft = Offset(left, top),
                            size = androidx.compose.ui.geometry.Size(w, h)
                        )
                    }
                }
            }
        }
    }
}
    }
