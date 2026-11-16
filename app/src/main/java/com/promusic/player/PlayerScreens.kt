package com.promusic.player

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// ======================= MINI PLAYER =======================

@Composable
fun MiniPlayer(vm: PlayerViewModel) {
    val song = vm.current
    var last by remember { mutableStateOf<Song?>(null) }
    LaunchedEffect(song) { if (song != null) last = song }
    val shown = song ?: last
    AnimatedVisibility(
        visible = song != null,
        enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(250)) { it } + fadeOut(tween(200))
    ) {
        if (shown != null) MiniPlayerBody(vm, shown)
    }
}

@Composable
private fun MiniPlayerBody(vm: PlayerViewModel, song: Song) {
    var frac by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(song.id) {
        while (true) {
            frac = (vm.positionMs().toFloat() / song.durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
            delay(500)
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .bounceClick { vm.showPlayer = true }
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1D1D2A))
    ) {
        Row(
            Modifier.padding(start = 8.dp, top = 8.dp, end = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtImage(song, Modifier.size(46.dp), 128, RoundedCornerShape(12.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { vm.togglePlay() }) {
                Crossfade(targetState = vm.isPlaying, animationSpec = tween(180), label = "mini") { p ->
                    Icon(if (p) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause", modifier = Modifier.size(30.dp))
                }
            }
            IconButton(onClick = { vm.next() }) {
                Icon(Icons.Rounded.SkipNext, "Next", modifier = Modifier.size(28.dp))
            }
        }
        LinearProgressIndicator(
            progress = { frac },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Transparent
        )
    }
}

// ======================= FULL PLAYER =======================

@Composable
fun FullPlayer(vm: PlayerViewModel) {
    val song = vm.current ?: return
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    BackHandler { vm.showPlayer = false }

    var dominant by remember { mutableStateOf(Color(0xFF2A2A3A)) }
    LaunchedEffect(song.id) {
        val b = ArtLoader.load(ctx, song, 128)
        dominant = if (b != null) dominantColor(b) else Color(0xFF2A2A3A)
    }
    val topColor by animateColorAsState(dominant, tween(700), label = "dominant")
    val bg = MaterialTheme.colorScheme.background
    val primary = MaterialTheme.colorScheme.primary

    val offsetY = remember { Animatable(0f) }
    val liked = song.id in vm.favorites
    val heart = remember { Animatable(1f) }
    LaunchedEffect(liked) {
        heart.snapTo(0.7f)
        heart.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    val artScale by animateFloatAsState(
        targetValue = if (vm.isPlaying) 1f else 0.84f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 220f),
        label = "artScale"
    )

    var pos by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragVal by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(song.id) {
        while (true) {
            if (!dragging) pos = vm.positionMs()
            delay(200)
        }
    }
    val dur = song.durationMs.coerceAtLeast(1L).toFloat()

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (offsetY.value > 320f) {
                            vm.showPlayer = false
                        } else {
                            scope.launch { offsetY.animateTo(0f, spring()) }
                        }
                    },
                    onDragCancel = { scope.launch { offsetY.animateTo(0f, spring()) } },
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        scope.launch { offsetY.snapTo((offsetY.value + dy).coerceAtLeast(0f)) }
                    }
                )
            }
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .background(Brush.verticalGradient(0f to topColor, 0.78f to bg, 1f to bg))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // top bar
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.showPlayer = false }) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Close player", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Playing from album", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                    Text(
                        song.album,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White
                    )
                }
                Spacer(Modifier.size(48.dp))
            }

            // artwork (swipe left or right to change song)
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val side = (if (maxWidth < maxHeight) maxWidth else maxHeight) - 8.dp
                Box(
                    Modifier
                        .size(side.coerceAtLeast(120.dp))
                        .graphicsLayer { scaleX = artScale; scaleY = artScale }
                        .shadow(24.dp, RoundedCornerShape(26.dp), ambientColor = Color.Black, spotColor = Color.Black)
                        .pointerInput(song.id) {
                            var total = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { total = 0f },
                                onDragEnd = {
                                    if (total > 140f) vm.previous() else if (total < -140f) vm.next()
                                },
                                onHorizontalDrag = { change, dx ->
                                    change.consume()
                                    total += dx
                                }
                            )
                        }
                ) {
                    AnimatedContent(
                        targetState = song,
                        transitionSpec = {
                            val q = vm.queue
                            val forward = q.indexOfFirst { it.id == targetState.id } >= q.indexOfFirst { it.id == initialState.id }
                            (slideInHorizontally(tween(350)) { if (forward) it / 2 else -it / 2 } + fadeIn(tween(350))) togetherWith
                                (slideOutHorizontally(tween(350)) { if (forward) -it / 2 else it / 2 } + fadeOut(tween(200)))
                        },
                        label = "artSwap"
                    ) { s ->
                        ArtImage(s, Modifier.fillMaxSize(), 640, RoundedCornerShape(26.dp), 80.dp)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // title + like
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { vm.toggleFavorite(song) }) {
                    Icon(
                        if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        "Like",
                        tint = if (liked) primary else Color.White.copy(alpha = 0.8f),
                        modifier = Modifier
                            .size(28.dp)
                            .graphicsLayer { scaleX = heart.value; scaleY = heart.value }
                    )
                }
            }

            // seek bar
            Slider(
                value = (if (dragging) dragVal else pos.toFloat()).coerceIn(0f, dur),
                onValueChange = { dragging = true; dragVal = it },
                onValueChangeFinished = {
                    vm.seekTo(dragVal.toLong())
                    pos = dragVal.toLong()
                    dragging = false
                },
                valueRange = 0f..dur,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                )
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmtTime(if (dragging) dragVal.toLong() else pos), fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                Text(fmtTime(song.durationMs), fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
            }

            Spacer(Modifier.height(10.dp))

            // transport controls
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.toggleShuffle() }) {
                    Icon(
                        Icons.Rounded.Shuffle, "Shuffle",
                        tint = if (vm.shuffle) primary else Color.White.copy(alpha = 0.7f)
                    )
                }
                IconButton(onClick = { vm.previous() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(38.dp))
                }
                Box(
                    Modifier
                        .size(76.dp)
                        .bounceClick { vm.togglePlay() }
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(targetState = vm.isPlaying, animationSpec = tween(180), label = "pp") { p ->
                        Icon(
                            if (p) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            "Play or pause",
                            tint = Color.Black,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
                IconButton(onClick = { vm.next() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(38.dp))
                }
                IconButton(onClick = { vm.cycleRepeat() }) {
                    Icon(
                        if (vm.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        "Repeat",
                        tint = if (vm.repeatMode == Player.REPEAT_MODE_OFF) Color.White.copy(alpha = 0.7f) else primary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // extras
            val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
            val speedLabel = ("%.2f".format(vm.speed)).trimEnd('0').trimEnd('.') + "x"
            val timerOn = SleepTimer.endsAt > 0L || SleepTimer.endOfTrack
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .bounceClick {
                            val idx = speeds.indexOfFirst { abs(it - vm.speed) < 0.01f }
                            vm.changeSpeed(speeds[(idx + 1) % speeds.size])
                        }
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(speedLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                IconButton(onClick = { vm.showSleep = true }) {
                    Icon(Icons.Rounded.Bedtime, "Sleep timer", tint = if (timerOn) primary else Color.White.copy(alpha = 0.8f))
                }
                IconButton(onClick = { vm.showEq = true }) {
                    Icon(Icons.Rounded.Equalizer, "Equalizer", tint = if (AudioFx.enabled) primary else Color.White.copy(alpha = 0.8f))
                }
                IconButton(onClick = { vm.showQueue = true }) {
                    Icon(Icons.Rounded.QueueMusic, "Queue", tint = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}

// ======================= QUEUE =======================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(vm: PlayerViewModel) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { vm.showQueue = false },
        sheetState = state,
        containerColor = Color(0xFF15151D)
    ) {
        Text(
            "Up next",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)
        )
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = (vm.queueIndex - 1).coerceAtLeast(0))
        if (vm.queue.isEmpty()) {
            Text(
                "The queue is empty. Play a song to start.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp)
        ) {
            itemsIndexed(vm.queue) { i, s ->
                val isCurrent = i == vm.queueIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { vm.jumpTo(i) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArtImage(s, Modifier.size(46.dp), 128, RoundedCornerShape(10.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            s.title,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            s.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (isCurrent) {
                        PlayingBars(vm.isPlaying, MaterialTheme.colorScheme.primary, Modifier.padding(end = 8.dp))
                    } else {
                        IconButton(onClick = { vm.removeFromQueue(i) }) {
                            Icon(Icons.Rounded.Close, "Remove from queue", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// ======================= SLEEP TIMER =======================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepSheet(vm: PlayerViewModel) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    val remaining = (SleepTimer.endsAt - now).coerceAtLeast(0L)
    val active = SleepTimer.endsAt > 0L || SleepTimer.endOfTrack
    ModalBottomSheet(
        onDismissRequest = { vm.showSleep = false },
        sheetState = state,
        containerColor = Color(0xFF15151D)
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding()
        ) {
            Text("Sleep timer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                when {
                    SleepTimer.endOfTrack -> "Music will pause after this song."
                    SleepTimer.endsAt > 0L -> "Music will pause in " + fmtTime(remaining) + "."
                    else -> "Pick when the music should stop."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
            )
            listOf(15, 30, 45, 60, 90).forEach { m ->
                SleepOption("$m minutes") {
                    SleepTimer.startMinutes(m)
                    vm.toast("Sleep timer set for $m minutes")
                    vm.showSleep = false
                }
            }
            SleepOption("End of current song") {
                SleepTimer.startEndOfTrack()
                vm.toast("Music will stop after this song")
                vm.showSleep = false
            }
            if (active) {
                SleepOption("Turn off timer") {
                    SleepTimer.cancel()
                    vm.showSleep = false
                }
            }
        }
    }
}

@Composable
private fun SleepOption(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .bounceClick(onClick)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

// ======================= EQUALIZER =======================

@Composable
fun EqualizerScreen(vm: PlayerViewModel) {
    BackHandler { vm.showEq = false }
    Surface(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {},
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.showEq = false }) { Icon(Icons.Rounded.ArrowBack, "Back") }
                Text(
                    "Equalizer",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = AudioFx.enabled,
                    onCheckedChange = { AudioFx.enable(it) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedThumbColor = Color.Black
                    )
                )
            }
            Spacer(Modifier.height(12.dp))

            if (!AudioFx.available || AudioFx.minLevel >= AudioFx.maxLevel) {
                Text(
                    "Start playing a song to unlock the equalizer. If it stays locked, your phone does not support system audio effects.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start
                )
            } else {
                Text("Presets", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(AudioFx.presets) { i, name ->
                        Pill(name, AudioFx.preset == i) { AudioFx.applyPreset(i) }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("Bands", fontWeight = FontWeight.Bold)
                for (i in 0 until AudioFx.levels.size) {
                    val hz = AudioFx.freqs.getOrNull(i) ?: 0
                    val label = if (hz >= 1000) "%.1f kHz".format(hz / 1000f) else "$hz Hz"
                    val db = AudioFx.levels[i] / 100f
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, modifier = Modifier.width(72.dp), fontSize = 13.sp)
                        Slider(
                            value = AudioFx.levels[i].toFloat(),
                            onValueChange = { AudioFx.changeBand(i, it.toInt()) },
                            valueRange = AudioFx.minLevel.toFloat()..AudioFx.maxLevel.toFloat(),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "%+.0f dB".format(db),
                            modifier = Modifier.width(56.dp),
                            textAlign = TextAlign.End,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (AudioFx.bassSupported) {
                    Spacer(Modifier.height(16.dp))
                    Text("Bass boost", fontWeight = FontWeight.Bold)
                    Slider(
                        value = AudioFx.bassStrength.toFloat(),
                        onValueChange = { AudioFx.changeBass(it.toInt()) },
                        valueRange = 0f..1000f
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { AudioFx.resetAll() }) { Text("Reset to flat") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
