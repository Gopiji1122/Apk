package com.promusic.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val vm: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT)
        )
        setContent { App(vm) }
    }

    override fun onStart() {
        super.onStart()
        vm.onAppStart()
    }

    override fun onStop() {
        vm.saveState()
        super.onStop()
    }
}

@Composable
fun App(vm: PlayerViewModel) {
    val ctx = LocalContext.current
    val audioPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onPermissionResult()
    }

    LaunchedEffect(Unit) {
        val need = ArrayList<String>()
        if (ContextCompat.checkSelfPermission(ctx, audioPerm) != PackageManager.PERMISSION_GRANTED) {
            need.add(audioPerm)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !vm.store.getBool("asked_notif", false)
        ) {
            need.add(Manifest.permission.POST_NOTIFICATIONS)
            vm.store.putBool("asked_notif", true)
        }
        if (need.isNotEmpty()) launcher.launch(need.toTypedArray())
    }

    ProTheme(
        accent = Accents[vm.accentIndex.coerceIn(0, Accents.lastIndex)].second,
        amoled = vm.amoled
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (!vm.hasPermission) {
                PermissionScreen(onGrant = { launcher.launch(arrayOf(audioPerm)) })
            } else {
                MainShell(vm)
            }
        }
    }
}

@Composable
fun PermissionScreen(onGrant: () -> Unit) {
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(84.dp))
        Spacer(Modifier.height(20.dp))
        Text(
            "Let Aura Audio Player find your songs",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Your music stays on your phone. The app works fully offline and never sends anything anywhere.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(26.dp))
        PillButton("Allow music access", Icons.Rounded.MusicNote, true) { onGrant() }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = {
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                )
            } catch (_: Exception) {
            }
        }) { Text("Open app settings") }
    }
}

@Composable
fun MainShell(vm: PlayerViewModel) {
    val top = vm.detailStack.lastOrNull()
    var lastDetail by remember { mutableStateOf<Detail?>(null) }
    LaunchedEffect(top) { if (top != null) lastDetail = top }

    androidx.activity.compose.BackHandler(enabled = vm.detailStack.isNotEmpty()) { vm.backDetail() }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Column {
                    MiniPlayer(vm)
                    BottomBar(vm)
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                AnimatedContent(
                    targetState = vm.tab,
                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                    label = "tabs"
                ) { t ->
                    when (t) {
                        0 -> HomeScreen(vm)
                        1 -> LibraryScreen(vm)
                        2 -> SearchScreen(vm)
                        else -> SettingsScreen(vm)
                    }
                }
                AnimatedVisibility(
                    visible = top != null,
                    enter = slideInHorizontally(tween(300)) { it } + fadeIn(tween(200)),
                    exit = slideOutHorizontally(tween(250)) { it } + fadeOut(tween(200))
                ) {
                    val d = top ?: lastDetail
                    if (d != null) DetailScreen(vm, d)
                }
            }
        }

        AnimatedVisibility(
            visible = vm.showPlayer && vm.current != null,
            enter = slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(300)) { it } + fadeOut(tween(250))
        ) {
            FullPlayer(vm)
        }

        AnimatedVisibility(
            visible = vm.showEq,
            enter = slideInVertically(tween(350, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(280)) { it } + fadeOut(tween(200))
        ) {
            EqualizerScreen(vm)
        }

        if (vm.showQueue) QueueSheet(vm)
        if (vm.showSleep) SleepSheet(vm)
        if (vm.playlistTarget != null) PlaylistPickerDialog(vm)
    }
}

@Composable
fun BottomBar(vm: PlayerViewModel) {
    val items = listOf(
        "Home" to Icons.Rounded.Home,
        "Library" to Icons.Rounded.LibraryMusic,
        "Search" to Icons.Rounded.Search,
        "Settings" to Icons.Rounded.Settings
    )
    val primary = MaterialTheme.colorScheme.primary
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        items.forEachIndexed { i, (label, icon) ->
            NavigationBarItem(
                selected = vm.tab == i,
                onClick = {
                    vm.tab = i
                    vm.clearDetails()
                },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.Black,
                    selectedTextColor = primary,
                    indicatorColor = primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
