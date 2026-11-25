package com.offlineai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val vm: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                App(vm)
            }
        }
    }
}

@Composable
fun App(vm: ChatViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("Chat", "Knowledge", "Models").forEachIndexed { i, s ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = {}, label = { Text(s) })
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            vm.error?.let {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.error = null }) { Text("OK") }
                }
            }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> ChatScreen(vm)
                    1 -> KnowledgeScreen(vm)
                    else -> ModelsScreen(vm)
                }
            }
        }
    }
}

@Composable
fun ChatScreen(vm: ChatViewModel) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(vm.messages.size, vm.messages.lastOrNull()?.text?.length) {
        if (vm.messages.isNotEmpty()) listState.scrollToItem(vm.messages.size - 1)
    }
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(vm.status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.clearChat() }, enabled = !vm.generating) { Text("Clear") }
        }
        if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (vm.messages.isEmpty()) item {
                Text(
                    "Ask anything. To teach me a fact, type: remember <fact>",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
            items(vm.messages.size) { i ->
                val m = vm.messages[i]
                val user = m.role == "user"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
                    Surface(
                        color = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.widthIn(max = 320.dp)
                    ) {
                        Text(if (m.text.isEmpty()) "..." else m.text, modifier = Modifier.padding(10.dp))
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f), placeholder = { Text("Message") }, maxLines = 4
            )
            Spacer(Modifier.width(6.dp))
            if (vm.generating) Button(onClick = { vm.stop() }) { Text("Stop") }
            else Button(onClick = { vm.send(input); input = "" }, enabled = input.isNotBlank() && !vm.busy) { Text("Send") }
        }
    }
}

@Composable
fun KnowledgeScreen(vm: ChatViewModel) {
    var note by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importText(uri)
    }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Teach the AI", style = MaterialTheme.typography.titleMedium)
            Text(
                "Notes are always included in answers. Imported text files are searched by keyword and the best parts are added to each question.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("A fact or note") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.addNote(note); note = "" }, enabled = note.isNotBlank()) { Text("Save note") }
                OutlinedButton(onClick = { pick.launch(arrayOf("text/*", "application/json")) }) { Text("Import .txt / .md") }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
        }
        items(vm.entries.size) { i ->
            val e = vm.entries[i]
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(e.title, style = MaterialTheme.typography.labelLarge)
                        Text(e.preview, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { vm.deleteEntry(e) }) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
fun ModelsScreen(vm: ChatViewModel) {
    var url by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importModel(uri)
    }
    val extras = vm.downloaded.filter { f -> Catalog.models.none { it.file == f } }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(vm.status, style = MaterialTheme.typography.bodySmall)
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Models download once over Wi-Fi, then work offline. Keep the app open while downloading.", style = MaterialTheme.typography.bodySmall)
        }
        items(Catalog.models) { m ->
            ModelCard(vm, m.name, m.file, "${m.sizeMb} MB. ${m.note}", true, m.tmpl) { vm.downloadModel(m) }
        }
        items(extras) { f ->
            ModelCard(vm, f, f, "Custom model", false, Catalog.guess(f)) {}
        }
        item {
            HorizontalDivider()
            Text("Custom model", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("Direct .gguf https link") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.downloadCustom(url) }, enabled = url.isNotBlank() && vm.progress.isEmpty()) { Text("Download") }
                OutlinedButton(onClick = { pick.launch(arrayOf("*/*")) }, enabled = !vm.busy) { Text("Pick .gguf file") }
            }
        }
    }
}

@Composable
fun ModelCard(vm: ChatViewModel, name: String, file: String, note: String, canDownload: Boolean, t: Tmpl, onDownload: () -> Unit) {
    val has = file in vm.downloaded
    val prog = vm.progress[file]
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(note, style = MaterialTheme.typography.bodySmall)
            if (prog != null) {
                LinearProgressIndicator(progress = { prog }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${(prog * 100).toInt()}%", Modifier.weight(1f))
                    TextButton(onClick = { vm.cancelDownload() }) { Text("Pause") }
                }
            } else if (has) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (vm.loadedName == file) Text("Loaded", color = MaterialTheme.colorScheme.primary)
                    else Button(onClick = { vm.loadModel(file, t) }, enabled = !vm.busy && !vm.generating) { Text("Load") }
                    TextButton(onClick = { vm.deleteModel(file) }, enabled = !vm.busy && !vm.generating) { Text("Delete") }
                }
            } else if (canDownload) {
                Button(onClick = onDownload, enabled = vm.progress.isEmpty()) { Text("Download") }
            }
        }
    }
}
