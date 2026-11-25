package com.gopiji.auraai

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gopiji.auraai.data.AppPrefs
import com.gopiji.auraai.data.MemoryDbHelper
import com.gopiji.auraai.data.StoredChatMessage
import com.gopiji.auraai.engine.LlamaEngine
import com.gopiji.auraai.engine.PromptTurn
import com.gopiji.auraai.theme.AuraAiTheme
import com.gopiji.auraai.ui.ChatScreen
import com.gopiji.auraai.ui.SettingsScreen
import com.gopiji.auraai.ui.SettingsSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AuraAiRoot()
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
fun AuraAiRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val db = remember { MemoryDbHelper(context) }
    val prefs = remember { AppPrefs(context) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var themeMode by remember { mutableStateOf(prefs.themeMode) }
    var fontScale by remember { mutableFloatStateOf(prefs.fontScale) }

    var showSettings by remember { mutableStateOf(false) }
    var settingsStartSection by remember { mutableStateOf(SettingsSection.MODELS) }
    var showModelPickerSheet by remember { mutableStateOf(false) }
    var drawerSearch by remember { mutableStateOf("") }

    var sessions by remember {
        mutableStateOf(db.getAllSessions().ifEmpty { listOf(db.createSession("New Chat")) })
    }
    var activeSessionId by remember { mutableLongStateOf(sessions.first().id) }
    var messages by remember { mutableStateOf(db.getMessagesForSession(activeSessionId)) }
    var notes by remember { mutableStateOf(db.getAllNotes()) }
    var importedModels by remember { mutableStateOf(prefs.listImportedModels()) }

    var isModelLoaded by remember { mutableStateOf(LlamaEngine.isLoaded) }
    var loadedModelName by remember {
        mutableStateOf(LlamaEngine.currentModelPath?.let { File(it).name } ?: "Select Model")
    }
    var isModelLoading by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    var cpuThreads by remember { mutableIntStateOf(prefs.cpuThreads) }
    var contextSize by remember { mutableIntStateOf(prefs.contextSize) }
    var maxTokens by remember { mutableIntStateOf(prefs.maxTokens) }
    var temperature by remember { mutableFloatStateOf(prefs.temperature) }
    var topK by remember { mutableIntStateOf(prefs.topK) }
    var topP by remember { mutableFloatStateOf(prefs.topP) }
    var fastNoThink by remember { mutableStateOf(prefs.fastNoThinkMode) }
    var ragEnabled by remember { mutableStateOf(prefs.ragEnabled) }
    var ragMaxNotes by remember { mutableIntStateOf(prefs.ragMaxNotes) }
    var systemPrompt by remember { mutableStateOf(prefs.systemPrompt) }

    fun loadModelFile(file: File) {
        scope.launch {
            isModelLoading = true
            statusBanner = "Loading ${file.name}..."
            val ok = withContext(Dispatchers.IO) {
                LlamaEngine.loadModel(file.absolutePath, contextSize, cpuThreads)
            }
            isModelLoading = false
            isModelLoaded = ok
            if (ok) {
                prefs.savedModelPath = file.absolutePath
                loadedModelName = file.name
                statusBanner = null
            } else {
                statusBanner = "Failed to load ${file.name}. Try lowering Context Window in Settings."
            }
        }
    }

    fun importModelFromUri(uri: Uri) {
        scope.launch {
            isModelLoading = true
            showModelPickerSheet = false
            try {
                val file = withContext(Dispatchers.IO) {
                    prefs.importModelFromUri(uri) { pct ->
                        scope.launch(Dispatchers.Main) {
                            statusBanner = "Importing model... $pct%"
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
    }

    val quickModelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) importModelFromUri(uri) }

    fun sendQuery(query: String, appendUserMessage: Boolean = true) {
        if (!isModelLoaded || isGenerating) return
        val currentSessionId = activeSessionId

        val historyBefore = messages
        if (appendUserMessage) {
            if (messages.isEmpty()) {
                db.updateSessionTitle(currentSessionId, query.take(40))
                sessions = db.getAllSessions()
            }
            val userMsgId = db.addMessage(currentSessionId, isUser = true, text = query)
            messages = messages + StoredChatMessage(userMsgId, currentSessionId, true, query, 0)
        }

        val relevantNotes = if (ragEnabled) db.searchRelevant(query, limit = ragMaxNotes) else emptyList()
        val finalSystem = buildString {
            append(systemPrompt.trim().ifEmpty { AppPrefs.DEFAULT_SYSTEM_PROMPT })
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

        val turns = mutableListOf<PromptTurn>()
        turns.add(PromptTurn("system", finalSystem))
        val priorMessages = if (appendUserMessage) historyBefore else messages.dropLast(1)
        priorMessages.forEach { m ->
            turns.add(PromptTurn(if (m.isUser) "user" else "assistant", m.text))
        }
        turns.add(PromptTurn("user", if (fastNoThink) "$query /no_think" else query))

        val tempAiId = -System.nanoTime()
        messages = messages + StoredChatMessage(
            id = tempAiId,
            sessionId = currentSessionId,
            isUser = false,
            text = "",
            usedNotesCount = relevantNotes.size
        )
        isGenerating = true

        scope.launch {
            val acc = StringBuilder()
            withContext(Dispatchers.IO) {
                LlamaEngine.generateChat(
                    turns = turns,
                    maxTokens = maxTokens,
                    temperature = temperature,
                    topK = topK,
                    topP = topP
                ) { token ->
                    acc.append(token)
                    val snapshot = acc.toString()
                    scope.launch(Dispatchers.Main) {
                        messages = messages.map {
                            if (it.id == tempAiId) it.copy(text = snapshot) else it
                        }
                    }
                }
            }
            val finalReply = acc.toString().ifEmpty { "(Stopped)" }
            val savedAiId = db.addMessage(currentSessionId, isUser = false, text = finalReply, usedNotesCount = relevantNotes.size)
            messages = messages.map {
                if (it.id == tempAiId) it.copy(id = savedAiId, text = finalReply) else it
            }
            sessions = db.getAllSessions()
            isGenerating = false
        }
    }

    LaunchedEffect(Unit) {
        val lastPath = prefs.savedModelPath
        if (!LlamaEngine.isLoaded && lastPath != null) {
            val f = File(lastPath)
            if (f.exists()) loadModelFile(f)
        }
    }

    AuraAiTheme(mode = themeMode) {
        AnimatedContent(
            targetState = showSettings,
            transitionSpec = {
                if (targetState) {
                    (slideInHorizontally(tween(240)) { it / 4 } + fadeIn(tween(220))) togetherWith
                        (fadeOut(tween(180)))
                } else {
                    (fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(240)) { it / 4 } + fadeOut(tween(180)))
                }
            },
            label = "RootNavAnim"
        ) { inSettings ->
            if (inSettings) {
                SettingsScreen(
                    initialSection = settingsStartSection,
                    hardwareInfo = prefs.getHardwareInfo(),
                    importedModels = importedModels,
                    currentModelPath = LlamaEngine.currentModelPath,
                    isModelLoading = isModelLoading,
                    notes = notes,
                    themeMode = themeMode,
                    fontScale = fontScale,
                    cpuThreads = cpuThreads,
                    contextSize = contextSize,
                    maxTokens = maxTokens,
                    temperature = temperature,
                    topK = topK,
                    topP = topP,
                    fastNoThink = fastNoThink,
                    ragEnabled = ragEnabled,
                    ragMaxNotes = ragMaxNotes,
                    systemPrompt = systemPrompt,
                    onBack = { showSettings = false },
                    onImportModelUri = { importModelFromUri(it) },
                    onSelectModel = { loadModelFile(it) },
                    onDeleteModel = { file ->
                        if (LlamaEngine.currentModelPath == file.absolutePath) {
                            LlamaEngine.unload()
                            isModelLoaded = false
                            loadedModelName = "Select Model"
                        }
                        file.delete()
                        importedModels = prefs.listImportedModels()
                    },
                    onReloadModelWithSettings = {
                        LlamaEngine.currentModelPath?.let { p ->
                            val f = File(p)
                            if (f.exists()) loadModelFile(f)
                        }
                    },
                    onAddNote = { t, c, body ->
                        db.addNote(t, c, body)
                        notes = db.getAllNotes()
                    },
                    onImportTextFile = { uri ->
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val name = prefs.getFileName(uri)
                                val text = context.contentResolver.openInputStream(uri)
                                    ?.bufferedReader()?.use { it.readText() } ?: ""
                                db.addChunkedDocument(name, "Imported", text)
                            }
                            notes = db.getAllNotes()
                        }
                    },
                    onDeleteNote = { id ->
                        db.deleteNote(id)
                        notes = db.getAllNotes()
                    },
                    onThemeChange = { themeMode = it; prefs.themeMode = it },
                    onFontScaleChange = { fontScale = it; prefs.fontScale = it },
                    onThreadsChange = { cpuThreads = it; prefs.cpuThreads = it },
                    onContextChange = { contextSize = it; prefs.contextSize = it },
                    onMaxTokensChange = { maxTokens = it; prefs.maxTokens = it },
                    onTemperatureChange = { temperature = it; prefs.temperature = it },
                    onTopKChange = { topK = it; prefs.topK = it },
                    onTopPChange = { topP = it; prefs.topP = it },
                    onFastModeChange = { fastNoThink = it; prefs.fastNoThinkMode = it },
                    onRagEnabledChange = { ragEnabled = it; prefs.ragEnabled = it },
                    onRagMaxNotesChange = { ragMaxNotes = it; prefs.ragMaxNotes = it },
                    onSystemPromptChange = { systemPrompt = it; prefs.systemPrompt = it }
                )
            } else {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = !isGenerating,
                    drawerContent = {
                        ModalDrawerSheet(
                            drawerContainerColor = MaterialTheme.colorScheme.surface,
                            drawerContentColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.width(300.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .navigationBarsPadding()
                                    .padding(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_aura_logo),
                                        contentDescription = "Aura AI",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text("Aura AI", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }

                                Spacer(Modifier.height(10.dp))

                                Button(
                                    onClick = {
                                        if (isGenerating) return@Button
                                        val created = db.createSession("New Chat")
                                        sessions = db.getAllSessions()
                                        activeSessionId = created.id
                                        messages = emptyList()
                                        scope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("New Chat", fontWeight = FontWeight.Medium)
                                }

                                Spacer(Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = drawerSearch,
                                    onValueChange = { drawerSearch = it },
                                    placeholder = { Text("Search chats...", fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Recent Chats",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                )

                                val filteredSessions = remember(sessions, drawerSearch) {
                                    if (drawerSearch.isBlank()) sessions
                                    else sessions.filter { it.title.contains(drawerSearch, ignoreCase = true) }
                                }

                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(filteredSessions, key = { it.id }) { session ->
                                        val isSelected = session.id == activeSessionId
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    if (isGenerating) return@clickable
                                                    activeSessionId = session.id
                                                    messages = db.getMessagesForSession(session.id)
                                                    scope.launch { drawerState.close() }
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = session.title,
                                                    fontSize = 14.sp,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(
                                                    onClick = {
                                                        if (isGenerating) return@IconButton
                                                        db.deleteSession(session.id)
                                                        val updated = db.getAllSessions()
                                                            .ifEmpty { listOf(db.createSession("New Chat")) }
                                                        sessions = updated
                                                        if (activeSessionId == session.id) {
                                                            activeSessionId = updated.first().id
                                                            messages = db.getMessagesForSession(activeSessionId)
                                                        }
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.DeleteOutline,
                                                        contentDescription = "Delete",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                Spacer(Modifier.height(8.dp))

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.Transparent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            settingsStartSection = SettingsSection.MODELS
                                            scope.launch { drawerState.close() }
                                            showSettings = true
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text("Settings", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                            Text(
                                                "Models, Teach AI, CPU & Memory",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.background,
                        topBar = {
                            CenterAlignedTopAppBar(
                                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.background,
                                    titleContentColor = MaterialTheme.colorScheme.onBackground
                                ),
                                navigationIcon = {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Default.Menu, contentDescription = "Chat Menu")
                                    }
                                },
                                title = {
                                    val shortModel = loadedModelName
                                        .removeSuffix(".gguf")
                                        .removeSuffix(".GGUF")
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .clickable { showModelPickerSheet = true }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        when {
                                                            isModelLoading -> Color(0xFFFFB74D)
                                                            isModelLoaded -> Color(0xFF66BB6A)
                                                            else -> Color(0xFF888888)
                                                        }
                                                    )
                                            )
                                            Spacer(Modifier.width(7.dp))
                                            Text(
                                                text = if (isModelLoading) "Loading..." else shortModel,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.widthIn(max = 175.dp)
                                            )
                                            Spacer(Modifier.width(3.dp))
                                            Icon(
                                                Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Switch Model",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                actions = {
                                    IconButton(
                                        onClick = {
                                            settingsStartSection = SettingsSection.AI_PARAMS
                                            showSettings = true
                                        }
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                                    }
                                }
                            )
                        }
                    ) { innerPadding ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            if (statusBanner != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = statusBanner!!,
                                            fontSize = 12.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "Dismiss",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.clickable { statusBanner = null }
                                        )
                                    }
                                }
                            }

                            ChatScreen(
                                messages = messages,
                                isModelLoaded = isModelLoaded,
                                isGenerating = isGenerating,
                                fontScale = fontScale,
                                onOpenModelPicker = { showModelPickerSheet = true },
                                onStop = { LlamaEngine.stopGeneration() },
                                onRegenerateLast = {
                                    if (!isGenerating && messages.size >= 2 && !messages.last().isUser) {
                                        val lastAi = messages.last()
                                        val lastUser = messages[messages.size - 2]
                                        if (lastUser.isUser) {
                                            db.deleteMessage(lastAi.id)
                                            messages = messages.dropLast(1)
                                            sendQuery(lastUser.text, appendUserMessage = false)
                                        }
                                    }
                                },
                                onSend = { q -> sendQuery(q, appendUserMessage = true) }
                            )
                        }
                    }
                }
            }
        }

        if (showModelPickerSheet) {
            ModalBottomSheet(
                onDismissRequest = { showModelPickerSheet = false },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text("Switch Offline Model", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Pick any imported .gguf model or import a new one.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))

                    if (importedModels.isEmpty()) {
                        Text(
                            "No models imported yet.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        importedModels.forEach { file ->
                            val isActive = LlamaEngine.currentModelPath == file.absolutePath
                            val sizeGb = String.format("%.2f GB", file.length() / (1024.0 * 1024.0 * 1024.0))
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isActive) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                                border = BorderStroke(
                                    1.dp,
                                    if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable(enabled = !isModelLoading) {
                                        showModelPickerSheet = false
                                        if (!isActive) loadModelFile(file)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(file.name, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(sizeGb, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (isActive) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = Color(0xFF66BB6A))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { quickModelPicker.launch(arrayOf("*/*")) },
                        enabled = !isModelLoading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Import .gguf Model from Phone")
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
    }
}
