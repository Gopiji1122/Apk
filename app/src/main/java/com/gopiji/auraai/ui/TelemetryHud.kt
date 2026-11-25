package com.gopiji.auraai.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun TelemetryHudBar(
    tokensPerSec: Float,
    ttftMs: Long,
    activeGpuLayers: Int,
    isGenerating: Boolean
) {
    val context = LocalContext.current
    var usedRamGb by remember { mutableDoubleStateOf(0.0) }
    var totalRamGb by remember { mutableDoubleStateOf(0.0) }
    var cpuPercent by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        var prevCpuMs = Process.getElapsedCpuTime()
        var prevWallMs = System.currentTimeMillis()

        while (true) {
            am.getMemoryInfo(memInfo)
            totalRamGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
            usedRamGb = (memInfo.totalMem - memInfo.availMem) / (1024.0 * 1024.0 * 1024.0)

            val nowCpuMs = Process.getElapsedCpuTime()
            val nowWallMs = System.currentTimeMillis()
            val dWall = (nowWallMs - prevWallMs).coerceAtLeast(1L)
            val dCpu = (nowCpuMs - prevCpuMs).coerceAtLeast(0L)
            cpuPercent = ((dCpu * 100.0) / (dWall * cores)).toInt().coerceIn(0, 100)
            prevCpuMs = nowCpuMs
            prevWallMs = nowWallMs

            delay(1000L)
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HudMetric(label = "CPU", value = "$cpuPercent%")
            HudMetric(label = "RAM", value = "%.1f/%.0fG".format(usedRamGb, totalRamGb))
            HudMetric(label = "GPU", value = if (activeGpuLayers > 0) "${activeGpuLayers}L" else "CPU")
            HudMetric(
                label = "SPEED",
                value = if (tokensPerSec > 0f) "%.1f t/s".format(tokensPerSec) else "--",
                highlight = isGenerating
            )
            HudMetric(label = "TTFT", value = if (ttftMs > 0L) "${ttftMs}ms" else "--")
        }
    }
}

@Composable
private fun HudMetric(label: String, value: String, highlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (highlight) Color(0xFF66BB6A) else MaterialTheme.colorScheme.onSurface
        )
    }
}
