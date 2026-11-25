package com.gopiji.auraai.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gopiji.auraai.R
import com.gopiji.auraai.data.StoredChatMessage
import kotlinx.coroutines.delay

@Composable
fun ChatScreen(
    messages: List<StoredChatMessage>,
    isModelLoaded: Boolean,
    isGenerating: Boolean,
    fontScale: Float,
    onOpenModelPicker: () -> Unit,
    onStop: () -> Unit,
    onRegenerateLast: () -> Unit,
    onSend: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (!isModelLoaded) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.size(68.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_aura_logo),
                                contentDescription = "Aura AI",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Welcome to Aura AI",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 21.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Import any .gguf model from your device storage to start chatting 100% offline with full privacy.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = onOpenModelPicker,
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Choose or Import Model", fontWeight = FontWeight.Medium)
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        EmptyChatWelcome(onPromptClick = onSend)
                    }
                }

                itemsIndexed(messages, key = { _, msg -> msg.id }) { index, msg ->
                    val isLast = index == messages.lastIndex
                    if (msg.isUser) {
                        UserMessageBubble(msg = msg, fontScale = fontScale)
                    } else {
                        AssistantMessageBlock(
                            msg = msg,
                            fontScale = fontScale,
                            isStreaming = isLast && isGenerating,
                            showRegenerate = isLast && !isGenerating,
                            onRegenerate = onRegenerateLast
                        )
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                "Message Aura AI...",
                                fontSize = (15 * fontScale).sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.weight(1f),
                        maxLines = 5,
                        enabled = isModelLoaded && !isGenerating,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = (15 * fontScale).sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )

                    Spacer(Modifier.width(4.dp))

                    Box(modifier = Modifier.padding(bottom = 4.dp)) {
                        AnimatedContent(
                            targetState = isGenerating,
                            transitionSpec = {
                                (fadeIn(tween(150)) + scaleIn(initialScale = 0.85f)) togetherWith
                                    (fadeOut(tween(150)) + scaleOut(targetScale = 0.85f))
                            },
                            label = "SendStopAnim"
                        ) { generating ->
                            if (generating) {
                                FilledIconButton(
                                    onClick = onStop,
                                    shape = CircleShape,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = "Stop", modifier = Modifier.size(20.dp))
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
                                    enabled = isModelLoaded && inputText.isNotBlank(),
                                    shape = CircleShape,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        disabledContainerColor = MaterialTheme.colorScheme.outline,
                                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Send", modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChatWelcome(onPromptClick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 36.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.size(58.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_aura_logo),
                    contentDescription = "Aura AI",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "How can Aura AI help today?",
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "100% private • Offline on-device intelligence",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        val starters = listOf(
            Pair("Study Helper", "Quiz me with 3 MCQs from my saved notes"),
            Pair("PC Assistant", "Give me 5 safe fixes for a slow Windows PC"),
            Pair("Explain Simply", "Explain how CPU, RAM, and SSD work together")
        )
        starters.forEach { (tag, prompt) ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clickable { onPromptClick(prompt) }
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        tag,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        prompt,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun UserMessageBubble(msg: StoredChatMessage, fontScale: Float) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            SelectionContainer {
                Text(
                    text = msg.text,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = (15 * fontScale).sp,
                    lineHeight = (22 * fontScale).sp,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp)
                )
            }
        }
        Row(
            modifier = Modifier.padding(top = 2.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(msg.text))
                    copied = true
                },
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = "Copy prompt",
                    tint = if (copied) Color(0xFF66BB6A) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
private fun AssistantMessageBlock(
    msg: StoredChatMessage,
    fontScale: Float,
    isStreaming: Boolean,
    showRegenerate: Boolean,
    onRegenerate: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(150))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_aura_logo),
                        contentDescription = "Aura AI",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Aura AI",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (msg.usedNotesCount > 0) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1B3320)
                ) {
                    Text(
                        "Used ${msg.usedNotesCount} note(s)",
                        fontSize = 10.sp,
                        color = Color(0xFF81C784),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        if (msg.text.isEmpty() && isStreaming) {
            ThinkingPulse()
        } else {
            SelectionContainer {
                Text(
                    text = msg.text,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = (15 * fontScale).sp,
                    lineHeight = (23 * fontScale).sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 2.dp, end = 4.dp)
                )
            }
        }

        if (!isStreaming && msg.text.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            clipboard.setText(AnnotatedString(msg.text))
                            copied = true
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy answer",
                            tint = if (copied) Color(0xFF66BB6A) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (copied) "Copied" else "Copy",
                            fontSize = 12.sp,
                            color = if (copied) Color(0xFF66BB6A) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (showRegenerate) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onRegenerate() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Regenerate",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "Regenerate",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingPulse() {
    val infinite = rememberInfiniteTransition(label = "ThinkingPulse")
    val alpha by infinite.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .alpha(alpha)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Thinking...",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alpha(alpha)
        )
    }
}
