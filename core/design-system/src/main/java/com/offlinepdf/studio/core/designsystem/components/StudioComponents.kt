package com.offlinepdf.studio.core.designsystem.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinepdf.studio.core.designsystem.theme.LocalStudioPrefs
import com.offlinepdf.studio.core.designsystem.theme.extras
import kotlinx.coroutines.delay

// ───────────────────────────── Haptics ─────────────────────────────
@Stable
class StudioHaptics(private val haptic: HapticFeedback, private val enabled: Boolean) {
    fun tick() {
        if (enabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun confirm() {
        if (enabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

@Composable
fun rememberStudioHaptics(): StudioHaptics {
    val haptic = LocalHapticFeedback.current
    val enabled = LocalStudioPrefs.current.hapticsEnabled
    return remember(haptic, enabled) { StudioHaptics(haptic, enabled) }
}

// ───────────────────────────── Modifiers ─────────────────────────────

/** Spring "press" feedback + subtle haptic tick. Replaces ripple for a more premium tactile feel. */
fun Modifier.bounceClick(
    enabled: Boolean = true,
    haptic: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val animations = LocalStudioPrefs.current.animationsEnabled
    val scale by animateFloatAsState(
        targetValue = if (pressed && animations) 0.965f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "press-scale"
    )
    val haptics = rememberStudioHaptics()
    val latestClick by rememberUpdatedState(onClick)
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled
        ) {
            if (haptic) haptics.tick()
            latestClick()
        }
}

/** Fade + rise entrance, staggered by [index]. Plays once per item composition. */
fun Modifier.staggeredEntrance(index: Int, stepMillis: Int = 45): Modifier = composed {
    val animations = LocalStudioPrefs.current.animationsEnabled
    var shown by remember { mutableStateOf(!animations) }
    LaunchedEffect(Unit) {
        if (!shown) {
            delay(index.coerceIn(0, 8) * stepMillis.toLong())
            shown = true
        }
    }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "entrance-alpha"
    )
    val rise by animateFloatAsState(
        targetValue = if (shown) 0f else 22f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "entrance-rise"
    )
    this.graphicsLayer {
        this.alpha = alpha
        translationY = rise.dp.toPx()
    }
}

/** Soft moving highlight used for skeleton placeholders. */
fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer-progress"
    )
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    this.background(
        Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = androidx.compose.ui.geometry.Offset(progress * 600f, 0f),
            end = androidx.compose.ui.geometry.Offset(progress * 600f + 400f, 200f)
        )
    )
}

// ───────────────────────────── Surfaces ─────────────────────────────
@Composable
fun StudioCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.large,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    content: @Composable ColumnScope.() -> Unit
) {
    val clickable = if (onClick != null) Modifier.bounceClick(onClick = onClick) else Modifier
    Surface(
        modifier = modifier.then(clickable),
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(content = content)
    }
}

@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing()
    }
}

@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        action?.invoke()
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.extras.brandBrush),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(6.dp))
            action()
        }
    }
}

/** Gradient "PDF" tile used as a document avatar when no thumbnail is available. */
@Composable
fun PdfFileIcon(
    modifier: Modifier = Modifier,
    scanned: Boolean = false,
    size: Dp = 44.dp
) {
    val brush = if (scanned) MaterialTheme.extras.scanBrush else MaterialTheme.extras.brandBrush
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(brush),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (scanned) Icons.Outlined.DocumentScanner else Icons.Outlined.PictureAsPdf,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.54f)
        )
    }
}

enum class PillTone { BRAND, SCAN, NEUTRAL, SUCCESS }

@Composable
fun StatusPill(
    label: String,
    modifier: Modifier = Modifier,
    tone: PillTone = PillTone.NEUTRAL
) {
    val (bg, fg) = when (tone) {
        PillTone.BRAND -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        PillTone.SCAN -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        PillTone.SUCCESS -> MaterialTheme.extras.success.copy(alpha = 0.16f) to MaterialTheme.extras.success
        PillTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(modifier = modifier, color = bg, contentColor = fg, shape = RoundedCornerShape(50)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/** Backwards-compatible badge used by roadmap lists. */
@Composable
fun PhaseBadge(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    StatusPill(label = label, modifier = modifier, tone = if (active) PillTone.SUCCESS else PillTone.NEUTRAL)
}

@Composable
fun GradientActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    brush: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .bounceClick(onClick = onClick)
            .clip(MaterialTheme.shapes.large)
            .background(brush)
            .height(132.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.16f),
            modifier = Modifier
                .size(104.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 18.dp, y = 22.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Column {
                Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Thin animated progress bar (reading progress, export progress…). */
@Composable
fun ProgressHairline(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
    brush: Brush = MaterialTheme.extras.brandBrush
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "hairline"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(RoundedCornerShape(50))
                .background(brush)
        )
    }
}

/** Compact animated segmented control (theme, layout mode, etc.). */
@Composable
fun <T> SegmentedChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberStudioHaptics()
    val selectedIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(3.dp)
    ) {
        val segmentWidth = maxWidth / options.size
        val indicatorX by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            label = "segment-x"
        )
        Box(
            modifier = Modifier
                .offset(x = indicatorX)
                .width(segmentWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.surface)
        )
        Row(modifier = Modifier.fillMaxSize()) {
            options.forEachIndexed { index, (value, label) ->
                val active = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(11.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (!active) haptics.tick()
                            onSelect(value)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

/** First-page thumbnail with graceful gradient fallback and a soft fade-in. */
@Composable
fun DocumentThumb(
    key: String,
    scanned: Boolean,
    loader: suspend (String) -> android.graphics.Bitmap?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    var bitmap by remember(key) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var failed by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        val result = runCatching { loader(key) }.getOrNull()
        bitmap = result
        failed = result == null
    }
    val alpha by animateFloatAsState(
        targetValue = if (bitmap != null) 1f else 0f,
        animationSpec = tween(320),
        label = "thumb-fade"
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        when {
            bmp != null && !bmp.isRecycled -> androidx.compose.foundation.Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { this.alpha = alpha }
            )
            failed -> PdfFileIcon(scanned = scanned, size = 40.dp)
            else -> Box(Modifier.fillMaxSize().shimmer())
        }
    }
}
