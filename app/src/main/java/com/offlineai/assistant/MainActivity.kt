package com.offlineai.assistant

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlineai.assistant.data.AppPrefs
import com.offlineai.assistant.data.KnowledgeNote
import com.offlineai.assistant.data.MemoryDbHelper
import com.offlineai.assistant.engine.LlamaEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ChatMessage(
    val id: Long = System.nanoTime(),
    val isUser: Boolean,
    val text: String,
    val usedNotesCount: Int = 0
)

private val AmoledColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = Color(0xFFBDBDBD),
    onSecondary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF0E0E0E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFBDBDBD),
    outline = Color(0xFF2E2E2E)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = AmoledColorScheme) {
                OfflineAiApp()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            LlamaEngine.unload()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineAiApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val db = remember { MemoryDbHelper(context) }
    val prefs = remember { AppPrefs(context) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var notes by remember { mutableStateOf(db.getAllNotes()) }
    var importedModels by remember { mutableStateOf(prefs.listImportedModels()) }

    var isModelLoaded by remember { mutableStateOf(LlamaEngine.isLoaded) }
    var loadedModelName by remember {
        mutableStateOf(LlamaEngine.currentModelPath?.let { File(it).name } ?: "No model loaded")
    }
    var isModelLoading by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    var cpuThreads by remember { mutableIntStateOf(prefs.cpuThreads) }
    var contextSize by remember { mutableIntStateOf(prefs.contextSize) }
    var fastNoThink by remember { mutableStateOf(prefs.fastNoThinkMode) }

    fun loadModelFile(file: File) {
        scope.launch {
            isModelLoading = true
            statusBanner = "Loading ${file.name} into RAM..."
            val ok = withContext(Dispatchers.IO) {
                LlamaEngine.loadModel(file.absolutePath, contextSize, cpuThreads)
            }
            isModelLoading = false
            isModelLoaded = ok
            if (ok) {
                prefs.savedModelPath = file.absolutePath
                loadedModelName = file.name
                statusBanner = "Ready: ${file.name}"
            } else {
                statusBanner = "Failed to load ${file.name}. Free up RAM or try a smaller Q4_K_M model."
            }
        }
    }

    // Auto-load previously selected model if it still exists
    LaunchedEffect(Unit) {
        val lastPath = prefs.savedModelPath
        if (!LlamaEngine.isLoaded && lastPath != null) {
            val f = File(lastPath)
            if (f.exists()) {
                loadModelFile(f)
            }
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White
                ),
                title = {
                    Column {
                        Text("Offline AI", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            text = if (isModelLoading) "Loading model..." else loadedModelName,
                            fontSize = 12.sp,
                            color = if (isModelLoaded) Color(0xFF81C784) else Color(0xFF9E9E9E),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    if (selectedTab == 0 && messages.isNotEmpty() && !isGenerating) {
                        IconButton(onClick = { messages = emptyList() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Chat", tint = Color.White)
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0A0A0A)) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Chat") },
                    label = { Text("Chat") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.MenuBook, contentDescription = "Teach AI") },
                    label = { Text("Teach (${notes.size})") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Memory, contentDescription = "Model") },
                    label = { Text("Model") }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            if (statusBanner != null) {
                Surface(
                    color = Color(0xFF161616),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = statusBanner!!,
                            fontSize = 12.sp,
                            color = Color(0xFFE0E0E0),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Dismiss",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.clickable { statusBanner = null }
                        )
                    }
                }
            }

            when (selectedTab) {
                0 -> ChatScreen(
                    messages = messages,
                    isModelLoaded = isModelLoaded,
                    isGenerating = isGenerating,
                    onOpenModelTab = { selectedTab = 2 },
                    onStop = { LlamaEngine.stopGeneration() },
                    onSend = { query ->
                        if (!isModelLoaded || isGenerating) return@ChatScreen
                        val relevantNotes = db.searchRelevant(query, limit = 3)
                        val systemPrompt = buildString {
                            append("You are a concise, accurate offline personal assistant for study help, PC troubleshooting, and answering from the user's saved notes.")
                            if (fastNoThink) {
                                append(" Give direct, clear answers without long internal thinking blocks.")
                            }
                            if (relevantNotes.isNotEmpty()) {
                                append("\n\n[USER SAVED KNOWLEDGE - PRIORITIZE THESE FACTS]\n")
                                relevantNotes.forEachIndexed { idx, n ->
                                    append("${idx + 1}. [${n.category}] ${n.title}: ${n.content}\n")
                                }
                            }
                        }

                        // Include last 2 turns of recent chat for continuity without blowing 2048 ctx
                        val recentTurns = messages.takeLast(4)
                        val finalUserPrompt = buildString {
                            if (recentTurns.isNotEmpty()) {
                                append("Recent conversation:\n")
                                recentTurns.forEach { m ->
                                    val role = if (m.isUser) "User" else "Assistant"
                                    append("$role: ${m.text.take(400)}\n")
                                }
                                append("\nCurrent question: ")
                            }
                            append(query)
                            if (fastNoThink) {
                                append(" /no_think")
                            }
                        }

                        val userMsg = ChatMessage(isUser = true, text = query)
                        val aiMsgId = System.nanoTime() + 1
                        val aiPlaceholder = ChatMessage(
                            id = aiMsgId,
                            isUser = false,
                            text = "",
                            usedNotesCount = relevantNotes.size
                        )
                        messages = messages + userMsg + aiPlaceholder
                        isGenerating = true

                        scope.launch {
                            val acc = StringBuilder()
                            withContext(Dispatchers.IO) {
                                LlamaEngine.generate(
                                    systemPrompt = systemPrompt,
                                    userPrompt = finalUserPrompt,
                                    maxTokens = 512,
                                    temperature = 0.7f
                                ) { token ->
                                    acc.append(token)
                                    val snapshot = acc.toString()
                                    scope.launch(Dispatchers.Main) {
                                        messages = messages.map {
                                            if (it.id == aiMsgId) it.copy(text = snapshot) else it
                                        }
                                    }
                                }
                            }
                            if (acc.isEmpty()) {
                                messages = messages.map {
                                    if (it.id == aiMsgId) it.copy(text = "(Stopped)") else it
                                }
                            }
                            isGenerating = false
                        }
                    }
                )

                1 -> TeachAiScreen(
                    notes = notes,
                    onAddNote = { title, category, content ->
                        db.addNote(title, category, content)
                        notes = db.getAllNotes()
                        statusBanner = "Saved to offline AI memory."
                    },
                    onImportTextFile = { uri ->
                        scope.launch {
                            val count = withContext(Dispatchers.IO) {
                                val fileName = prefs.getFileName(uri)
                                val text = context.contentResolver.openInputStream(uri)
                                    ?.bufferedReader()?.use { it.readText() } ?: ""
                                db.addChunkedDocument(fileName, "Imported File", text)
                            }
                            notes = db.getAllNotes()
                            statusBanner = "Imported $count knowledge chunks from file."
                        }
                    },
                    onDeleteNote = { id ->
                        db.deleteNote(id)
                        notes = db.getAllNotes()
                    }
                )

                2 -> ModelScreen(
                    importedModels = importedModels,
                    currentModelPath = LlamaEngine.currentModelPath,
                    isModelLoading = isModelLoading,
                    cpuThreads = cpuThreads,
                    contextSize = contextSize,
                    fastNoThink = fastNoThink,
                    onImportModelUri = { uri ->
                        scope.launch {
                            isModelLoading = true
                            try {
                                val file = withContext(Dispatchers.IO) {
                                    prefs.importModelFromUri(uri) { pct ->
                                        scope.launch(Dispatchers.Main) {
                                            statusBanner = "Copying GGUF model to app storage... $pct%"
                                        }
                                    }
                                }
                                importedModels = prefs.listImportedModels()
                                loadModelFile(file)
                            } catch (e: Exception) {
                                isModelLoading = false
                                statusBanner = "Import failed: ${e.message}"
                            }
                        }
                    },
                    onSelectModel = { file -> loadModelFile(file) },
                    onDeleteModel = { file ->
                        if (LlamaEngine.currentModelPath == file.absolutePath) {
                            LlamaEngine.unload()
                            isModelLoaded = false
                            loadedModelName = "No model loaded"
                        }
                        file.delete()
                        importedModels = prefs.listImportedModels()
                    },
                    onThreadsChange = {
                        cpuThreads = it
                        prefs.cpuThreads = it
                    },
                    onContextChange = {
                        contextSize = it
                        prefs.contextSize = it
                    },
                    onFastModeChange = {
                        fastNoThink = it
                        prefs.fastNoThinkMode = it
                    }
                )
            }
        }
    }
}

@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    isModelLoaded: Boolean,
    isGenerating: Boolean,
    onOpenModelTab: () -> Unit,
    onStop: () -> Unit,
    onSend: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!isModelLoaded) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Memory,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No Offline AI Model Loaded",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Import a .gguf model (like Qwen 3.5 4B or Phi-4-Mini 3.8B) in the Model tab to start chatting 100% offline.",
                        fontSize = 13.sp,
                        color = Color(0xFFBDBDBD)
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onOpenModelTab) {
                        Text("Open Model Tab")
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(top = 24.dp)) {
                            Text(
                                "Ask anything offline",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Your saved notes in the Teach tab are automatically searched and used in answers.",
                                fontSize = 13.sp,
                                color = Color(0xFF9E9E9E)
                            )
                            Spacer(Modifier.height(16.dp))
                            val starters = listOf(
                                "Quiz me from my saved study notes",
                                "My Windows PC is running slow, give me step-by-step fixes",
                                "Explain CPU vs RAM in simple words"
                            )
                            starters.forEach { prompt ->
                                OutlinedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { onSend(prompt) },
                                    border = BorderStroke(1.dp, Color(0xFF2E2E2E)),
                                    colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFF0E0E0E))
                                ) {
                                    Text(
                                        text = prompt,
                                        modifier = Modifier.padding(12.dp),
                                        fontSize = 13.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                items(messages, key = { it.id }) { msg ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (msg.isUser) Color.White else Color(0xFF141414),
                            border = if (msg.isUser) null else BorderStroke(1.dp, Color(0xFF262626)),
                            modifier = Modifier.widthIn(max = 310.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                if (!msg.isUser && msg.usedNotesCount > 0) {
                                    Text(
                                        text = "Used ${msg.usedNotesCount} saved note(s)",
                                        fontSize = 11.sp,
                                        color = Color(0xFF81C784),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.height(4.dp))
                                }
                                Text(
                                    text = msg.text.ifEmpty { "Thinking..." },
                                    color = if (msg.isUser) Color.Black else Color.White,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFF1F1F1F))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF080808))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Ask your offline AI...", fontSize = 14.sp) },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                enabled = isModelLoaded && !isGenerating,
                shape = RoundedCornerShape(22.dp)
            )
            Spacer(Modifier.width(8.dp))
            if (isGenerating) {
                FilledIconButton(
                    onClick = onStop,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFE53935))
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White)
                }
            } else {
                FilledIconButton(
                    onClick = {
                        val trimmed = inputText.trim()
                        if (trimmed.isNotEmpty()) {
                            inputText = ""
                            onSend(trimmed)
                        }
                    },
                    enabled = isModelLoaded && inputText.isNotBlank()
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
fun TeachAiScreen(
    notes: List<KnowledgeNote>,
    onAddNote: (String, String, String) -> Unit,
    onImportTextFile: (Uri) -> Unit,
    onDeleteNote: (Long) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Study") }
    var content by remember { mutableStateOf("") }

    val txtPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportTextFile(uri)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Teach Your AI (Offline Memory)", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "Add study notes, PC commands, or personal facts. The app automatically searches this database and feeds matching facts into every chat.",
                fontSize = 12.sp,
                color = Color(0xFF9E9E9E)
            )
        }

        item {
            OutlinedCard(
                border = BorderStroke(1.dp, Color(0xFF2A2A2A)),
                colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFF0E0E0E))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title (e.g., Physics Chapter 1 / My PC Specs)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Study", "PC Help", "Personal").forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat, fontSize = 12.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("Facts or Notes to Remember") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (content.isNotBlank()) {
                                    onAddNote(title, category, content)
                                    title = ""
                                    content = ""
                                }
                            },
                            enabled = content.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save Note")
                        }

                        OutlinedButton(
                            onClick = { txtPicker.launch(arrayOf("text/plain", "text/markdown", "*/*")) }
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import .txt")
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Saved Knowledge (${notes.size})",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color(0xFFBDBDBD)
            )
        }

        if (notes.isEmpty()) {
            item {
                Text(
                    "No saved notes yet. Add your first note above.",
                    fontSize = 13.sp,
                    color = Color(0xFF757575)
                )
            }
        } else {
            items(notes, key = { it.id }) { note ->
                OutlinedCard(
                    border = BorderStroke(1.dp, Color(0xFF222222)),
                    colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFF0C0C0C))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "[${note.category}] ${note.title}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                note.content,
                                fontSize = 13.sp,
                                color = Color(0xFFD0D0D0),
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onDeleteNote(note.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFF9E9E9E))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModelScreen(
    importedModels: List<File>,
    currentModelPath: String?,
    isModelLoading: Boolean,
    cpuThreads: Int,
    contextSize: Int,
    fastNoThink: Boolean,
    onImportModelUri: (Uri) -> Unit,
    onSelectModel: (File) -> Unit,
    onDeleteModel: (File) -> Unit,
    onThreadsChange: (Int) -> Unit,
    onContextChange: (Int) -> Unit,
    onFastModeChange: (Boolean) -> Unit
) {
    val modelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportModelUri(uri)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("GGUF Model Manager", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "Recommended for 8 GB RAM (OnePlus 8): Qwen3.5-4B (Q4_K_M, ~2.6 GB) or Phi-4-Mini-3.8B (Q4_K_M, ~2.4 GB).",
                fontSize = 12.sp,
                color = Color(0xFF9E9E9E)
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { modelPicker.launch(arrayOf("*/*")) },
                enabled = !isModelLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isModelLoading) "Working..." else "Import .gguf Model from Phone Storage")
            }
        }

        item {
            Text(
                "Installed Models (${importedModels.size})",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color(0xFFBDBDBD)
            )
        }

        if (importedModels.isEmpty()) {
            item {
                Text(
                    "No .gguf models imported yet. Download one from Hugging Face on your phone, then tap Import above.",
                    fontSize = 13.sp,
                    color = Color(0xFF757575)
                )
            }
        } else {
            items(importedModels, key = { it.absolutePath }) { file ->
                val isActive = currentModelPath == file.absolutePath
                val sizeGb = String.format("%.2f GB", file.length() / (1024.0 * 1024.0 * 1024.0))
                OutlinedCard(
                    border = BorderStroke(1.dp, if (isActive) Color.White else Color(0xFF262626)),
                    colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFF0E0E0E))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                text = if (isActive) "$sizeGb • Loaded" else sizeGb,
                                fontSize = 12.sp,
                                color = if (isActive) Color(0xFF81C784) else Color(0xFF9E9E9E)
                            )
                        }
                        if (!isActive) {
                            TextButton(
                                onClick = { onSelectModel(file) },
                                enabled = !isModelLoading
                            ) {
                                Text("Load")
                            }
                        }
                        IconButton(
                            onClick = { onDeleteModel(file) },
                            enabled = !isModelLoading
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFF9E9E9E))
                        }
                    }
                }
            }
        }

        item {
            HorizontalDivider(color = Color(0xFF222222))
            Spacer(Modifier.height(6.dp))
            Text("Performance Settings (OnePlus 8 Tuned)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))

            Text("CPU Threads: $cpuThreads (Recommended: 6)", fontSize = 13.sp)
            Slider(
                value = cpuThreads.toFloat(),
                onValueChange = { onThreadsChange(it.toInt()) },
                valueRange = 2f..8f,
                steps = 5
            )

            Text("Context Window: $contextSize tokens (2048 safest for 8 GB RAM)", fontSize = 13.sp)
            Slider(
                value = contextSize.toFloat(),
                onValueChange = { onContextChange((it.toInt() / 512) * 512) },
                valueRange = 1024f..4096f,
                steps = 5
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Fast Mode (Skip Long Thinking)", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "Recommended for Qwen 3.5 so answers start immediately.",
                        fontSize = 12.sp,
                        color = Color(0xFF9E9E9E)
                    )
                }
                Switch(checked = fastNoThink, onCheckedChange = onFastModeChange)
            }
        }
    }
}
