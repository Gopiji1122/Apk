package com.aurapdf.editor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.aurapdf.editor.engine.OcrEngine
import com.aurapdf.editor.engine.PdfEngine
import com.aurapdf.editor.ui.components.*
import com.aurapdf.editor.ui.screens.*
import com.aurapdf.editor.ui.theme.AuraPdfTheme
import com.aurapdf.editor.util.FileUtils
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.launch
import java.io.File

private sealed interface AppScreen {
    data object Home : AppScreen
    data class Viewer(val file: File) : AppScreen
    data class Organizer(val file: File) : AppScreen
    data class Annotate(val file: File, val pageIndex: Int) : AppScreen
}

private enum class PendingHomeAction { NONE, SPLIT, COMPRESS, LOCK, OCR, PAGE_NUMBERS, STAMP, CROP, BLANK, TEXT_EXPORT, FLATTEN }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PdfEngine.init(this)

        val incomingUri: Uri? = if (intent?.action == Intent.ACTION_VIEW) intent.data else null

        setContent {
            AuraPdfTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AuraPdfRoot(activity = this, initialUri = incomingUri)
                }
            }
        }
    }
}

@Composable
private fun AuraPdfRoot(activity: ComponentActivity, initialUri: Uri?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Home) }
    var recentFiles by remember { mutableStateOf(FileUtils.listRecentPdfs(context)) }
    var processingMsg by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf(PendingHomeAction.NONE) }
    var showAboutDialog by remember { mutableStateOf(false) }

    // Dialog states
    var splitTarget by remember { mutableStateOf<Pair<File, Int>?>(null) }
    var lockTarget by remember { mutableStateOf<File?>(null) }
    var unlockTarget by remember { mutableStateOf<File?>(null) }
    var watermarkTarget by remember { mutableStateOf<File?>(null) }
    var compressTarget by remember { mutableStateOf<File?>(null) }
    var signTarget by remember { mutableStateOf<Pair<File, Int>?>(null) }
    var ocrResultText by remember { mutableStateOf<String?>(null) }
    var pageNumberTarget by remember { mutableStateOf<File?>(null) }
    var stampTarget by remember { mutableStateOf<File?>(null) }
    var cropTarget by remember { mutableStateOf<File?>(null) }
    var blankTarget by remember { mutableStateOf<Pair<File, Int>?>(null) }

    fun refreshRecents() {
        recentFiles = FileUtils.listRecentPdfs(context)
    }

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun runTask(message: String, block: suspend () -> Unit) {
        scope.launch {
            processingMsg = message
            try {
                block()
            } catch (e: Exception) {
                toast("Error: ${e.localizedMessage ?: "Operation failed"}")
            } finally {
                processingMsg = null
                refreshRecents()
            }
        }
    }

    LaunchedEffect(initialUri) {
        if (initialUri != null) {
            runTask("Opening PDF...") {
                val file = FileUtils.copyUriToWorkspace(context, initialUri)
                currentScreen = AppScreen.Viewer(file)
            }
        }
    }

    val openPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runTask("Loading PDF...") {
            val file = FileUtils.copyUriToWorkspace(context, uri)
            when (pendingAction) {
                PendingHomeAction.SPLIT -> {
                    val count = PdfEngine.getPageCount(file)
                    splitTarget = file to count
                }
                PendingHomeAction.COMPRESS -> compressTarget = file
                PendingHomeAction.LOCK -> lockTarget = file
                PendingHomeAction.PAGE_NUMBERS -> pageNumberTarget = file
                PendingHomeAction.STAMP -> stampTarget = file
                PendingHomeAction.CROP -> cropTarget = file
                PendingHomeAction.BLANK -> blankTarget = file to PdfEngine.getPageCount(file)
                PendingHomeAction.FLATTEN -> {
                    val out = File(FileUtils.getPdfWorkspaceDir(context), "flattened_${System.currentTimeMillis()}.pdf")
                    PdfEngine.flattenForms(file, out)
                    toast("Form fields flattened")
                    currentScreen = AppScreen.Viewer(out)
                }
                PendingHomeAction.TEXT_EXPORT -> {
                    val txt = File(context.cacheDir, "${file.nameWithoutExtension}.txt")
                    txt.writeText(PdfEngine.extractText(file).ifBlank { "(No embedded text found. Try Offline OCR.)" })
                    FileUtils.shareFile(context, txt, "text/plain")
                }
                PendingHomeAction.OCR -> {
                    val text = OcrEngine.extractTextFromPdf(file, null) { cur, tot ->
                        processingMsg = "Running Offline OCR ($cur/$tot)..."
                    }
                    ocrResultText = text
                }
                PendingHomeAction.NONE -> currentScreen = AppScreen.Viewer(file)
            }
            pendingAction = PendingHomeAction.NONE
        }
    }

    val mergeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.size < 2) {
            toast("Select at least 2 PDFs to merge")
            return@rememberLauncherForActivityResult
        }
        runTask("Merging ${uris.size} PDFs...") {
            val out = File(FileUtils.getPdfWorkspaceDir(context), "merged_${System.currentTimeMillis()}.pdf")
            PdfEngine.mergePdfs(context, uris, out)
            toast("Merged successfully!")
            currentScreen = AppScreen.Viewer(out)
        }
    }

    val imagesToPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        runTask("Converting ${uris.size} images to PDF...") {
            val bitmaps = FileUtils.decodeBitmapsFromUris(context, uris)
            val out = File(FileUtils.getPdfWorkspaceDir(context), "photos_${System.currentTimeMillis()}.pdf")
            PdfEngine.imagesToPdf(bitmaps, out)
            bitmaps.forEach { it.recycle() }
            toast("PDF created!")
            currentScreen = AppScreen.Viewer(out)
        }
    }

    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == ComponentActivity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanResult?.pdf?.uri?.let { pdfUri ->
                runTask("Saving scanned PDF...") {
                    val out = FileUtils.copyUriToWorkspace(context, pdfUri, "scan")
                    currentScreen = AppScreen.Viewer(out)
                }
            }
        }
    }

    fun launchScanner() {
        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setGalleryImportAllowed(true)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(activity)
            .addOnSuccessListener { sender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
            .addOnFailureListener {
                toast("Document Scanner requires Google Play Services on this device")
            }
    }

    val officeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val ext = (FileUtils.getFileName(context, uri) ?: "").substringAfterLast('.', "").lowercase()
        runTask("Converting to PDF...") {
            val out = File(FileUtils.getPdfWorkspaceDir(context), "converted_${System.currentTimeMillis()}.pdf")
            PdfEngine.officeToPdf(context, uri, ext, out)
            toast("PDF created!")
            currentScreen = AppScreen.Viewer(out)
        }
    }

    fun pickPdfFor(action: PendingHomeAction) {
        pendingAction = action
        openPdfLauncher.launch(arrayOf("application/pdf"))
    }

    // ---------------- Dialogs ----------------
    processingMsg?.let { ProcessingDialog(it) }
    if (showAboutDialog) AboutAppDialog(onDismiss = { showAboutDialog = false })

    splitTarget?.let { (file, count) ->
        SplitPdfDialog(
            totalPages = count,
            onDismiss = { splitTarget = null },
            onConfirm = { start, end ->
                splitTarget = null
                runTask("Extracting pages $start-$end...") {
                    val out = File(FileUtils.getPdfWorkspaceDir(context), "split_${start}_${end}_${System.currentTimeMillis()}.pdf")
                    PdfEngine.splitPdf(file, start, end, out)
                    toast("Extracted pages $start-$end")
                    currentScreen = AppScreen.Viewer(out)
                }
            }
        )
    }

    lockTarget?.let { file ->
        PasswordDialog(
            title = "Lock PDF (AES-128)",
            confirmLabel = "Lock",
            onDismiss = { lockTarget = null },
            onConfirm = { pwd ->
                lockTarget = null
                runTask("Encrypting PDF...") {
                    val out = File(FileUtils.getPdfWorkspaceDir(context), "locked_${file.name}")
                    PdfEngine.lockPdf(file, pwd, out)
                    toast("PDF locked with password")
                }
            }
        )
    }

    unlockTarget?.let { file ->
        PasswordDialog(
            title = "Unlock PDF",
            confirmLabel = "Unlock",
            onDismiss = { unlockTarget = null },
            onConfirm = { pwd ->
                unlockTarget = null
                runTask("Removing password...") {
                    val out = File(FileUtils.getPdfWorkspaceDir(context), "unlocked_${file.name}")
                    PdfEngine.unlockPdf(file, pwd, out)
                    toast("PDF unlocked")
                    currentScreen = AppScreen.Viewer(out)
                }
            }
        )
    }

    watermarkTarget?.let { file ->
        WatermarkDialog(
            onDismiss = { watermarkTarget = null },
            onConfirm = { text, opacity ->
                watermarkTarget = null
                runTask("Applying watermark...") {
                    val out = File(FileUtils.getPdfWorkspaceDir(context), "watermarked_${System.currentTimeMillis()}.pdf")
                    PdfEngine.addWatermark(file, text, opacity, out)
                    toast("Watermark added")
                    currentScreen = AppScreen.Viewer(out)
                }
            }
        )
    }

    compressTarget?.let { file ->
        CompressDialog(
            onDismiss = { compressTarget = null },
            onConfirm = { q, s ->
                compressTarget = null
                runTask("Compressing PDF...") {
                    val out = File(FileUtils.getPdfWorkspaceDir(context), "compressed_${System.currentTimeMillis()}.pdf")
                    PdfEngine.compressPdf(file, q, s, out)
                    toast("Saved ${FileUtils.formatSize(out.length())}")
                    currentScreen = AppScreen.Viewer(out)
                }
            }
        )
    }

    pageNumberTarget?.let { file ->
        PageNumberDialog(
            onDismiss = { pageNumberTarget = null },
            onConfirm = { pos, style ->
                pageNumberTarget = null
                runTask("Adding page numbers...") {
                    val out = File(FileUtils.getPdfWorkspaceDir(context), "numbered_${System.currentTimeMillis()}.pdf")
                    PdfEngine.addPageNumbers(file, pos, style, out)
                    currentScreen = AppScreen.Viewer(out)
                }
            }
        )
    }

    stampTarget?.let { file ->
        StampDialog(
            onDismiss = { stampTarget = null },
            onConfirm = { text, rgb ->
                stampTarget = null
                runTask("Stamping pages...") {
                    val out = File(FileUtils.getPdfWorkspaceDir(context), "stamped_${System.currentTimeMillis()}.pdf")
                    PdfEngine.addStamp(file, text, rgb, out)
                    currentScreen = AppScreen.Viewer(out)
                }
            }
        )
    }

    cropTarget?.let { file ->
        CropDialog(
            onDismiss = { cropTarget = null },
            onConfirm = { l, t, r, b ->
                cropTarget = null
                runTask("Cropping pages...") {
                    val out = File(FileUtils.getPdfWorkspaceDir(context), "cropped_${System.currentTimeMillis()}.pdf")
                    PdfEngine.cropPages(file, l, t, r, b, out)
                    currentScreen = AppScreen.Viewer(out)
                }
            }
        )
    }

    blankTarget?.let { (file, count) ->
        BlankPageDialog(
            totalPages = count,
            onDismiss = { blankTarget = null },
            onConfirm = { after ->
                blankTarget = null
                runTask("Inserting blank page...") {
                    val out = File(FileUtils.getPdfWorkspaceDir(context), "inserted_${System.currentTimeMillis()}.pdf")
                    PdfEngine.insertBlankPage(file, after, out)
                    currentScreen = AppScreen.Viewer(out)
                }
            }
        )
    }

    signTarget?.let { (file, pageIdx) ->
        SignaturePadDialog(
            onDismiss = { signTarget = null },
            onSaveSignature = { sigBmp ->
                signTarget = null
                runTask("Stamping signature...") {
                    val out = File(FileUtils.getPdfWorkspaceDir(context), "signed_${System.currentTimeMillis()}.pdf")
                    PdfEngine.stampBitmapOnPage(
                        inputFile = file,
                        pageIndex = pageIdx,
                        bitmap = sigBmp,
                        normX = 0.58f,
                        normY = 0.80f,
                        normW = 0.36f,
                        normH = 0.14f,
                        outputFile = out
                    )
                    sigBmp.recycle()
                    toast("Signature stamped!")
                    currentScreen = AppScreen.Viewer(out)
                }
            }
        )
    }

    ocrResultText?.let { txt ->
        OcrResultDialog(
            extractedText = txt,
            onDismiss = { ocrResultText = null },
            onCopied = {
                toast("Text copied to clipboard")
                ocrResultText = null
            }
        )
    }

    // ---------------- Screen Navigation ----------------
    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            (fadeIn(tween(280)) + scaleIn(initialScale = 0.94f, animationSpec = tween(280)))
                .togetherWith(fadeOut(tween(160)))
        },
        label = "rootNav"
    ) { screen ->
        when (screen) {
            is AppScreen.Home -> {
                HomeScreen(
                    recentFiles = recentFiles,
                    onTool = { tool ->
                        when (tool) {
                            HomeTool.OPEN -> pickPdfFor(PendingHomeAction.NONE)
                            HomeTool.SCAN -> launchScanner()
                            HomeTool.MERGE -> mergeLauncher.launch(arrayOf("application/pdf"))
                            HomeTool.IMAGES -> imagesToPdfLauncher.launch(arrayOf("image/*"))
                            HomeTool.OFFICE -> officeLauncher.launch(
                                arrayOf(
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "text/plain"
                                )
                            )
                            HomeTool.SPLIT -> pickPdfFor(PendingHomeAction.SPLIT)
                            HomeTool.BLANK -> pickPdfFor(PendingHomeAction.BLANK)
                            HomeTool.CROP -> pickPdfFor(PendingHomeAction.CROP)
                            HomeTool.PAGE_NUMBERS -> pickPdfFor(PendingHomeAction.PAGE_NUMBERS)
                            HomeTool.STAMP -> pickPdfFor(PendingHomeAction.STAMP)
                            HomeTool.FLATTEN -> pickPdfFor(PendingHomeAction.FLATTEN)
                            HomeTool.LOCK -> pickPdfFor(PendingHomeAction.LOCK)
                            HomeTool.COMPRESS -> pickPdfFor(PendingHomeAction.COMPRESS)
                            HomeTool.OCR -> pickPdfFor(PendingHomeAction.OCR)
                            HomeTool.TEXT_EXPORT -> pickPdfFor(PendingHomeAction.TEXT_EXPORT)
                        }
                    },
                    onOpenRecent = { file -> currentScreen = AppScreen.Viewer(file) },
                    onShareFile = { file -> FileUtils.shareFile(context, file) },
                    onDeleteRecent = { file ->
                        file.delete()
                        refreshRecents()
                    },
                    onOpenAbout = { showAboutDialog = true }
                )
            }

            is AppScreen.Viewer -> {
                PdfViewerScreen(
                    pdfFile = screen.file,
                    onBack = { currentScreen = AppScreen.Home },
                    onOpenMarkup = { idx -> currentScreen = AppScreen.Annotate(screen.file, idx) },
                    onOpenSign = { idx -> signTarget = screen.file to idx },
                    onOpenOrganizer = { currentScreen = AppScreen.Organizer(screen.file) },
                    onRunOcr = { singleIdx ->
                        runTask("Running Offline OCR...") {
                            val text = OcrEngine.extractTextFromPdf(screen.file, singleIdx) { cur, tot ->
                                processingMsg = "Offline OCR ($cur/$tot)..."
                            }
                            ocrResultText = text
                        }
                    },
                    onSplit = { total -> splitTarget = screen.file to total },
                    onLock = { lockTarget = screen.file },
                    onUnlock = { unlockTarget = screen.file },
                    onWatermark = { watermarkTarget = screen.file },
                    onCompress = { compressTarget = screen.file },
                    onExportImages = {
                        runTask("Exporting pages as PNG...") {
                            val outDir = File(context.cacheDir, "exported_${System.currentTimeMillis()}")
                            val images = PdfEngine.exportPagesToImages(screen.file, outDir)
                            var saved = 0
                            images.forEach { img ->
                                if (FileUtils.saveFileToDownloads(context, img, "image/png")) saved++
                            }
                            toast("Saved $saved PNG images to Downloads/AuraPDF")
                        }
                    },
                    onSaveDownloads = {
                        runTask("Saving to Downloads...") {
                            val ok = FileUtils.saveFileToDownloads(context, screen.file)
                            toast(if (ok) "Saved to Downloads/AuraPDF" else "Save failed")
                        }
                    },
                    onShare = { FileUtils.shareFile(context, screen.file) }
                )
            }

            is AppScreen.Organizer -> {
                PageOrganizerScreen(
                    pdfFile = screen.file,
                    onBack = { currentScreen = AppScreen.Viewer(screen.file) },
                    onSaveChanges = { specs ->
                        runTask("Saving page changes...") {
                            val out = File(FileUtils.getPdfWorkspaceDir(context), "edited_${System.currentTimeMillis()}.pdf")
                            PdfEngine.applyPageEdits(screen.file, specs, out)
                            toast("Pages updated!")
                            currentScreen = AppScreen.Viewer(out)
                        }
                    }
                )
            }

            is AppScreen.Annotate -> {
                AnnotateScreen(
                    pdfFile = screen.file,
                    pageIndex = screen.pageIndex,
                    onBack = { currentScreen = AppScreen.Viewer(screen.file) },
                    onSaveOverlay = { overlayBmp ->
                        runTask("Saving markup onto PDF...") {
                            val out = File(FileUtils.getPdfWorkspaceDir(context), "marked_${System.currentTimeMillis()}.pdf")
                            PdfEngine.stampBitmapOnPage(
                                inputFile = screen.file,
                                pageIndex = screen.pageIndex,
                                bitmap = overlayBmp,
                                normX = 0f,
                                normY = 0f,
                                normW = 1f,
                                normH = 1f,
                                outputFile = out
                            )
                            overlayBmp.recycle()
                            toast("Markup saved!")
                            currentScreen = AppScreen.Viewer(out)
                        }
                    }
                )
            }
        }
    }
}
