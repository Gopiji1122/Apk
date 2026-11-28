package com.offlinepdf.studio.feature.reader

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp

/**
 * Shared pinch-zoom / pan state for every reading layout.
 *
 * The content layer is scaled around the viewport centre and translated by (offsetX, offsetY).
 * Vertical scrolling of a continuous list while zoomed is delegated to a `scroller`, so the page
 * follows the finger 1:1 instead of the previous behaviour where list-scroll and pan stacked.
 */
@Stable
class ReaderZoomState(val maxScale: Float = 7f) {
    var scale by mutableFloatStateOf(1f)
        private set
    var offsetX by mutableFloatStateOf(0f)
        private set
    var offsetY by mutableFloatStateOf(0f)
        private set
    var viewport by mutableStateOf(IntSize.Zero)

    /** True while a finger is down or a fling / zoom animation is running. */
    var isInteracting by mutableStateOf(false)
        private set

    val isZoomed: Boolean get() = scale > 1.02f

    private var flingJob: Job? = null
    private var animJob: Job? = null

    private fun maxX(s: Float) = viewport.width * (s - 1f).coerceAtLeast(0f) / 2f
    private fun maxY(s: Float) = viewport.height * (s - 1f).coerceAtLeast(0f) / 2f

    fun reset() {
        cancelMotion()
        scale = 1f; offsetX = 0f; offsetY = 0f
    }

    fun cancelMotion() {
        flingJob?.cancel(); animJob?.cancel()
        flingJob = null; animJob = null
    }

    fun setInteracting(value: Boolean) { isInteracting = value }

    /** Pinch about [focus] (viewport coordinates). */
    fun pinch(zoomChange: Float, focus: Offset, pan: Offset) {
        val old = scale
        val new = (old * zoomChange).coerceIn(1f, maxScale)
        val factor = if (old > 0f) new / old else 1f
        val fx = focus.x - viewport.width / 2f
        val fy = focus.y - viewport.height / 2f
        scale = new
        offsetX = ((offsetX - fx) * factor + fx + pan.x).coerceIn(-maxX(new), maxX(new))
        offsetY = ((offsetY - fy) * factor + fy + pan.y).coerceIn(-maxY(new), maxY(new))
        if (new <= 1.001f) { offsetX = 0f; offsetY = 0f }
    }

    /**
     * Pans by [pan] screen pixels. [scroller] (content px, +ve = forward) scrolls the underlying
     * list first and returns how much it actually consumed; the remainder moves the layer.
     */
    fun panBy(pan: Offset, scroller: ((Float) -> Float)?) {
        val s = scale
        offsetX = (offsetX + pan.x).coerceIn(-maxX(s), maxX(s))
        if (scroller != null) {
            val consumed = scroller(-pan.y / s)
            val leftover = pan.y + consumed * s
            offsetY = (offsetY + leftover).coerceIn(-maxY(s), maxY(s))
        } else {
            offsetY = (offsetY + pan.y).coerceIn(-maxY(s), maxY(s))
        }
    }

    /** Smoothly animates scale + offset together (no focus jump, unlike animating scale alone). */
    fun animateTo(scope: CoroutineScope, targetScale: Float, focus: Offset?, targetOffsetY: Float? = null) {
        cancelMotion()
        val startS = scale; val startX = offsetX; val startY = offsetY
        val endS = targetScale.coerceIn(1f, maxScale)
        val factor = if (startS > 0f) endS / startS else 1f
        val fx = (focus?.x ?: viewport.width / 2f) - viewport.width / 2f
        val fy = (focus?.y ?: viewport.height / 2f) - viewport.height / 2f
        val endX = if (endS <= 1.001f) 0f else ((startX - fx) * factor + fx).coerceIn(-maxX(endS), maxX(endS))
        val endY = if (endS <= 1.001f) 0f
        else (targetOffsetY ?: ((startY - fy) * factor + fy)).coerceIn(-maxY(endS), maxY(endS))
        animJob = scope.launch {
            isInteracting = true
            try {
                animate(0f, 1f, animationSpec = tween(280, easing = FastOutSlowInEasing)) { v, _ ->
                    scale = startS + (endS - startS) * v
                    offsetX = startX + (endX - startX) * v
                    offsetY = startY + (endY - startY) * v
                }
            } finally {
                isInteracting = false
            }
        }
    }

    fun doubleTap(scope: CoroutineScope, tap: Offset) {
        if (scale > 1.2f) animateTo(scope, 1f, null) else animateTo(scope, 2.6f, tap)
    }

    fun fling(scope: CoroutineScope, vx0: Float, vy0: Float, scroller: ((Float) -> Float)?) {
        flingJob?.cancel()
        flingJob = scope.launch {
            isInteracting = true
            try {
                var vx = vx0; var vy = vy0
                var last = withFrameNanos { it }
                while (isActive && (abs(vx) > 40f || abs(vy) > 40f)) {
                    val now = withFrameNanos { it }
                    val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    last = now
                    panBy(Offset(vx * dt, vy * dt), scroller)
                    val friction = exp(-3.6f * dt)
                    vx *= friction; vy *= friction
                }
            } finally {
                isInteracting = false
            }
        }
    }
}

/**
 * Pinch / pan / double-tap / single-tap for the reader. Runs in the Initial pass so a pinch is never
 * stolen by the scrolling child, and single-finger drags are only consumed while zoomed in.
 */
fun Modifier.readerZoomGestures(
    zoom: ReaderZoomState,
    scope: CoroutineScope,
    scroller: ((Float) -> Float)?,
    onTap: () -> Unit
): Modifier = this
    .pointerInput(zoom) {
        detectTapGestures(
            onTap = { onTap() },
            onDoubleTap = { zoom.doubleTap(scope, it) }
        )
    }
    .pointerInput(zoom, scroller) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            zoom.cancelMotion()
            zoom.setInteracting(true)
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var pastSlop = false
            var accumulated = Offset.Zero
            var multi = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.count { it.pressed }
                if (pressed >= 2) {
                    multi = true
                    val z = event.calculateZoom()
                    val pan = event.calculatePan()
                    zoom.pinch(z, event.calculateCentroid(useCurrent = true), pan)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                } else if (pressed == 1 && zoom.isZoomed && !multi) {
                    val change = event.changes.firstOrNull { it.pressed } ?: continue
                    tracker.addPosition(change.uptimeMillis, change.position)
                    var pan = event.calculatePan()
                    if (!pastSlop) {
                        accumulated += pan
                        if (accumulated.getDistance() > slop) { pastSlop = true; pan = accumulated }
                    }
                    if (pastSlop) {
                        zoom.panBy(pan, scroller)
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                }
            } while (event.changes.any { it.pressed })

            if (pastSlop && !multi) {
                val v = tracker.calculateVelocity()
                zoom.setInteracting(false)
                zoom.fling(scope, v.x, v.y, scroller)
            } else {
                zoom.setInteracting(false)
            }
        }
    }
