package com.offlineai.assistant.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.offlineai.assistant.data.AppThemeMode
import com.offlineai.assistant.data.DeviceHardwareInfo
import com.offlineai.assistant.data.KnowledgeNote
import java.io.File

enum class SettingsSection(val label: String) {
    MODELS("Models"),
    TEACH("Teach AI"),
    AI_PARAMS("AI & Prompt"),
    DEVICE("CPU & RAM"),
    APPEARANCE("App")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    initialSection: SettingsSection = SettingsSection.MODELS,
    hardwareInfo: DeviceHardwareInfo,
    importedModels: List<File>,
    currentModelPath: String?,
    isModelLoading: Boolean,
    notes: List<KnowledgeNote>,
    themeMode: AppThemeMode,
    fontScale: Float,
    cpuThreads: Int,
    contextSize: Int,
    maxTokens: Int,
    temperature: Float,
    topK: Int,
    topP: Float,
    fastNoThink: Boolean,
    ragEnabled: Boolean,
    ragMaxNotes: Int,
    systemPrompt: String,
    onBack: () -> Unit,
    onImportModelUri: (Uri) -> Unit,
    onSelectModel: (File) -> Unit,
    onDeleteModel: (File) -> Unit,
    onReloadModelWithSettings: () -> Unit,
    onAddNote: (String, String, String) -> Unit,
    onImportTextFile: (Uri) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onThemeChange: (AppThemeMode) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onThreadsChange: (Int) -> Unit,
    onContextChange: (Int) -> Unit,
    onMaxTokensChange: (Int) -> Unit,
    onTemperatureChange: (Float) -> Unit,
    onTopKChange: (Int) -> Unit,
    onTopPChange: (Float) -> Unit,
    onFastModeChange: (Boolean) -> Unit,
    onRagEnabledChange: (Boolean) -> Unit,
    onRagMaxNotesChange: (Int) -> Unit,
    onSystemPromptChange: (String) -> Unit
) {
    var activeSection by remember { mutableStateOf(initialSection) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text("Settings", fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(SettingsSection.entries) { section ->
                    FilterChip(
                        selected = activeSection == section,
                        onClick = { activeSection = section },
                        label = {
                            Text(
                                text = if (section == SettingsSection.TEACH) "${section.label} (${notes.size})" else section.label,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            AnimatedContent(
                targetState = activeSection,
                modifier = Modifier.weight(1f),
                label = "SettingsTabAnim"
            ) { section ->
                when (section) {
                    SettingsSection.MODELS -> ModelsSettingsTab(
                        importedModels = importedModels,
                        currentModelPath = currentModelPath,
                        isModelLoading = isModelLoading,
                        totalRamGb = hardwareInfo.totalRamGb,
                        onImportModelUri = onImportModelUri,
                        onSelectModel = onSelectModel,
                        onDeleteModel = onDeleteModel
                    )

                    SettingsSection.TEACH -> TeachSettingsTab(
                        notes = notes,
                        ragEnabled = ragEnabled,
                        ragMaxNotes = ragMaxNotes,
                        onRagEnabledChange = onRagEnabledChange,
                        onRagMaxNotesChange = onRagMaxNotesChange,
                        onAddNote = onAddNote,
                        onImportTextFile = onImportTextFile,
                        onDeleteNote = onDeleteNote
                    )

                    SettingsSection.AI_PARAMS -> AiParamsSettingsTab(
                        systemPrompt = systemPrompt,
                        maxTokens = maxTokens,
                        temperature = temperature,
                        topK = topK,
                        topP = topP,
                        fastNoThink = fastNoThink,
                        onSystemPromptChange = onSystemPromptChange,
                        onMaxTokensChange = onMaxTokensChange,
                        onTemperatureChange = onTemperatureChange,
                        onTopKChange = onTopKChange,
                        onTopPChange = onTopPChange,
                        onFastModeChange = onFastModeChange
                    )

                    SettingsSection.DEVICE -> DeviceSettingsTab(
                        hardwareInfo = hardwareInfo,
                        cpuThreads = cpuThreads,
                        contextSize = contextSize,
                        hasLoadedModel = currentModelPath != null,
                        isModelLoading = isModelLoading,
                        onThreadsChange = onThreadsChange,
                        onContextChange = onContextChange,
                        onApplyAndReload = onReloadModelWithSettings
                    )

                    SettingsSection.APPEARANCE -> AppearanceSettingsTab(
                        themeMode = themeMode,
                        fontScale = fontScale,
                        onThemeChange = onThemeChange,
                        onFontScaleChange = onFontScaleChange
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelsSettingsTab(
    importedModels: List<File>,
    currentModelPath: String?,
    isModelLoading: Boolean,
    totalRamGb: Double,
    onImportModelUri: (Uri) -> Unit,
    onSelectModel: (File) -> Unit,
    onDeleteModel: (File) -> Unit
) {
    val modelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onImportModelUri(uri) }

    val ramTip = when {
        totalRamGb >= 10.0 -> "Your device has %.1f GB RAM — supports 4B to 8B Q4_K_M models.".format(totalRamGb)
        totalRamGb >= 6.0 -> "Your device has %.1f GB RAM — best with 3B to 4B Q4_K_M models (Qwen 3.5 4B, Phi-4-Mini).".format(totalRamGb)
        else -> "Your device has %.1f GB RAM — recommended: 1B to 2B Q4_K_M models.".format(totalRamGb)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsCard {
                Text("Offline GGUF Models", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text(ramTip, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { modelPicker.launch(arrayOf("*/*")) },
                    enabled = !isModelLoading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isModelLoading) "Working..." else "Import .gguf Model from Storage")
                }
            }
        }

        item {
            Text(
                "Installed Models (${importedModels.size})",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (importedModels.isEmpty()) {
            item {
                Text(
                    "No .gguf models imported yet. Download any Q4_K_M .gguf file from Hugging Face, then tap Import above.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(importedModels, key = { it.absolutePath }) { file ->
                val isActive = currentModelPath == file.absolutePath
                val sizeGb = String.format("%.2f GB", file.length() / (1024.0 * 1024.0 * 1024.0))
                OutlinedCard(
                    border = BorderStroke(
                        1.dp,
                        if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    ),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = if (isActive) "$sizeGb • Active" else sizeGb,
                                fontSize = 12.sp,
                                color = if (isActive) Color(0xFF66BB6A) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!isActive) {
                            TextButton(onClick = { onSelectModel(file) }, enabled = !isModelLoading) {
                                Text("Load")
                            }
                        }
                        IconButton(onClick = { onDeleteModel(file) }, enabled = !isModelLoading) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeachSettingsTab(
    notes: List<KnowledgeNote>,
    ragEnabled: Boolean,
    ragMaxNotes: Int,
    onRagEnabledChange: (Boolean) -> Unit,
    onRagMaxNotesChange: (Int) -> Unit,
    onAddNote: (String, String, String) -> Unit,
    onImportTextFile: (Uri) -> Unit,
    onDeleteNote: (Long) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Study") }
    var content by remember { mutableStateOf("") }

    val txtPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onImportTextFile(uri) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Use Saved Knowledge (RAG)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(
                            "Automatically search your notes and feed matching facts into answers.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = ragEnabled, onCheckedChange = onRagEnabledChange)
                }
                if (ragEnabled) {
                    Spacer(Modifier.height(10.dp))
                    Text("Max matching notes per answer: $ragMaxNotes", fontSize = 13.sp)
                    Slider(
                        value = ragMaxNotes.toFloat(),
                        onValueChange = { onRagMaxNotesChange(it.toInt()) },
                        valueRange = 1f..6f,
                        steps = 4
                    )
                }
            }
        }

        item {
            SettingsCard {
                Text("Add New Knowledge", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title (e.g., Physics Ch.1 / PC Specs)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Study", "PC Help", "Personal").forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, fontSize = 12.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Facts or notes to remember") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
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
                    OutlinedButton(onClick = { txtPicker.launch(arrayOf("text/plain", "text/markdown", "*/*")) }) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import .txt")
                    }
                }
            }
        }

        item {
            Text(
                "Saved Notes (${notes.size})",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(notes, key = { it.id }) { note ->
            SettingsCard {
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("[${note.category}] ${note.title}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            note.content,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { onDeleteNote(note.id) }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun AiParamsSettingsTab(
    systemPrompt: String,
    maxTokens: Int,
    temperature: Float,
    topK: Int,
    topP: Float,
    fastNoThink: Boolean,
    onSystemPromptChange: (String) -> Unit,
    onMaxTokensChange: (Int) -> Unit,
    onTemperatureChange: (Float) -> Unit,
    onTopKChange: (Int) -> Unit,
    onTopPChange: (Float) -> Unit,
    onFastModeChange: (Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Custom System Prompt", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onSystemPromptChange(AppPrefs.DEFAULT_SYSTEM_PROMPT) }) {
                        Text("Reset", fontSize = 12.sp)
                    }
                }
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = onSystemPromptChange,
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            SettingsCard {
                Text("Generation Controls", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))

                Text("Max Reply Length: $maxTokens tokens", fontSize = 13.sp)
                Slider(
                    value = maxTokens.toFloat(),
                    onValueChange = { onMaxTokensChange(((it.toInt() / 64) * 64).coerceAtLeast(128)) },
                    valueRange = 128f..2048f
                )

                val tempLabel = when {
                    temperature < 0.35f -> "Strict / Factual"
                    temperature <= 0.85f -> "Balanced"
                    else -> "Creative"
                }
                Text("Temperature: ${String.format("%.2f", temperature)} ($tempLabel)", fontSize = 13.sp)
                Slider(
                    value = temperature,
                    onValueChange = { onTemperatureChange(it) },
                    valueRange = 0.0f..1.5f
                )

                Text("Top-P (Nucleus Sampling): ${String.format("%.2f", topP)}", fontSize = 13.sp)
                Slider(
                    value = topP,
                    onValueChange = { onTopPChange(it) },
                    valueRange = 0.50f..1.0f
                )

                Text("Top-K: $topK", fontSize = 13.sp)
                Slider(
                    value = topK.toFloat(),
                    onValueChange = { onTopKChange(it.toInt()) },
                    valueRange = 10f..100f
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline)

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Fast Mode (Skip Long Thinking)", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(
                            "Appends /no_think for Qwen 3/3.5 so answers start immediately. Turn OFF for hard logic puzzles.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = fastNoThink, onCheckedChange = onFastModeChange)
                }
            }
        }
    }
}

@Composable
private fun DeviceSettingsTab(
    hardwareInfo: DeviceHardwareInfo,
    cpuThreads: Int,
    contextSize: Int,
    hasLoadedModel: Boolean,
    isModelLoading: Boolean,
    onThreadsChange: (Int) -> Unit,
    onContextChange: (Int) -> Unit,
    onApplyAndReload: () -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsCard {
                Text("Detected Device Hardware", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                Text("Device: ${hardwareInfo.deviceName}", fontSize = 13.sp)
                Text("OS: ${hardwareInfo.androidVersion}", fontSize = 13.sp)
                Text("CPU Cores: ${hardwareInfo.cpuCores}", fontSize = 13.sp)
                Text(
                    "RAM: %.1f GB available / %.1f GB total".format(hardwareInfo.availRamGb, hardwareInfo.totalRamGb),
                    fontSize = 13.sp
                )
            }
        }

        item {
            SettingsCard {
                Text("CPU & Memory Allocation", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))

                val maxCores = hardwareInfo.cpuCores.coerceAtLeast(2)
                Text("CPU Threads: $cpuThreads / $maxCores cores", fontSize = 13.sp)
                Slider(
                    value = cpuThreads.toFloat().coerceIn(1f, maxCores.toFloat()),
                    onValueChange = { onThreadsChange(it.toInt()) },
                    valueRange = 1f..maxCores.toFloat(),
                    steps = (maxCores - 2).coerceAtLeast(0)
                )

                Text("Context Window (Chat Memory): $contextSize tokens", fontSize = 13.sp)
                Text(
                    "Higher context remembers longer chats but uses more RAM.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = contextSize.toFloat(),
                    onValueChange = { onContextChange(((it.toInt() / 512) * 512).coerceAtLeast(512)) },
                    valueRange = 512f..8192f
                )

                if (hasLoadedModel) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onApplyAndReload,
                        enabled = !isModelLoading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(if (isModelLoading) "Reloading..." else "Apply CPU & Memory Changes Now")
                    }
                }
            }
        }

        item {
            SettingsCard {
                Text("Privacy & App Permissions", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "• Network Permission: NONE (100% Offline)
• Storage Access: Secure System File Picker (works on Android 7–15 without broad file permissions).",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open Android App Permissions & Battery Settings")
                }
            }
        }
    }
}

@Composable
private fun AppearanceSettingsTab(
    themeMode: AppThemeMode,
    fontScale: Float,
    onThemeChange: (AppThemeMode) -> Unit,
    onFontScaleChange: (Float) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsCard {
                Text("Theme", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { onThemeChange(mode) },
                            label = { Text(mode.label, fontSize = 13.sp) }
                        )
                    }
                }
            }
        }

        item {
            SettingsCard {
                Text("Chat Text Size: ${(fontScale * 100).toInt()}%", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Slider(
                    value = fontScale,
                    onValueChange = onFontScaleChange,
                    valueRange = 0.85f..1.30f
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), content = content)
    }
}
