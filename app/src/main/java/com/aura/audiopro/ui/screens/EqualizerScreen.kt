package com.aura.audiopro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.audiopro.audio.EqualizerController
import com.aura.audiopro.ui.components.ProPill
import com.aura.audiopro.ui.components.bounceClick
import com.aura.audiopro.ui.viewmodel.AuraViewModel
import kotlin.math.abs

@Composable
fun EqualizerScreen(
    vm: AuraViewModel,
    accent: Color,
    onBack: () -> Unit
) {
    val eqState by EqualizerController.state.collectAsState()
    val customPresets by vm.customEqPresets.collectAsState()

    var showSavePresetDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .bounceClick(scaleDown = 0.88f, onClick = onBack)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Equalizer", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            if (eqState.isSupported) "5-Band Hardware DSP + L/R Balance" else "Hardware Equalizer",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = eqState.enabled,
                    onCheckedChange = { EqualizerController.setEnabled(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accent)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "SOUND PRESETS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = accent,
                    letterSpacing = 1.1.sp
                )
                Box(
                    modifier = Modifier
                        .bounceClick(scaleDown = 0.94f) { showSavePresetDialog = true }
                        .clip(RoundedCornerShape(50))
                        .background(accent.copy(alpha = 0.14f))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text("+ Save Custom", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = accent)
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(eqState.builtInPresets) { preset ->
                    ProPill(
                        text = preset,
                        selected = eqState.activePresetName == preset,
                        onClick = {
                            if (!eqState.enabled) EqualizerController.setEnabled(true)
                            EqualizerController.applyBuiltInPreset(preset)
                        }
                    )
                }
                items(customPresets, key = { it.id }) { custom ->
                    ProPill(
                        text = custom.name,
                        selected = eqState.activePresetName == custom.name,
                        onClick = {
                            if (!eqState.enabled) EqualizerController.setEnabled(true)
                            val levels = custom.bandLevelsCsv.split(",").mapNotNull { it.toIntOrNull() }
                            EqualizerController.applyCustomBandValues(
                                name = custom.name,
                                levels = levels,
                                bass = custom.bassStrength,
                                virt = custom.virtualizerStrength
                            )
                        }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "FREQUENCY BANDS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = accent,
                    letterSpacing = 1.1.sp
                )
                eqState.bandLevels.forEachIndexed { idx, level ->
                    val freq = eqState.bandFreqHz.getOrElse(idx) { 1000 }
                    val freqText = if (freq >= 1000) "${"%.1f".format(freq / 1000f)} kHz" else "$freq Hz"
                    val db = level / 100f
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(freqText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${if (db >= 0) "+" else ""}${"%.1f".format(db)} dB",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = accent
                            )
                        }
                        Slider(
                            value = level.toFloat(),
                            onValueChange = {
                                if (!eqState.enabled) EqualizerController.setEnabled(true)
                                EqualizerController.setBandLevel(idx, it.toInt())
                            },
                            valueRange = eqState.minLevel.toFloat()..eqState.maxLevel.toFloat(),
                            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Bass Boost", fontWeight = FontWeight.Bold)
                    Text("${eqState.bassBoostStrength / 10}%", color = accent, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = eqState.bassBoostStrength.toFloat(),
                    onValueChange = {
                        if (!eqState.enabled) EqualizerController.setEnabled(true)
                        EqualizerController.setBassBoost(it.toInt())
                    },
                    valueRange = 0f..1000f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("3D Virtualizer", fontWeight = FontWeight.Bold)
                    Text("${eqState.virtualizerStrength / 10}%", color = accent, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = eqState.virtualizerStrength.toFloat(),
                    onValueChange = {
                        if (!eqState.enabled) EqualizerController.setEnabled(true)
                        EqualizerController.setVirtualizer(it.toInt())
                    },
                    valueRange = 0f..1000f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Stereo Balance (L / R)", fontWeight = FontWeight.Bold)
                    val balLabel = when {
                        eqState.stereoBalance < -0.04f -> "L ${(abs(eqState.stereoBalance) * 100).toInt()}% (Tap to Center)"
                        eqState.stereoBalance > 0.04f -> "R ${(eqState.stereoBalance * 100).toInt()}% (Tap to Center)"
                        else -> "Center (0%)"
                    }
                    Text(
                        text = balLabel,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { EqualizerController.setStereoBalance(0f) }
                    )
                }
                Slider(
                    value = eqState.stereoBalance,
                    onValueChange = { raw ->
                        val snapped = if (abs(raw) < 0.04f) 0f else raw
                        EqualizerController.setStereoBalance(snapped)
                    },
                    valueRange = -1f..1f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )
            }
        }
    }

    if (showSavePresetDialog) {
        var presetName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text("Save Custom Equalizer Preset") },
            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    placeholder = { Text("My Bass Curve") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.saveCustomEqPreset(
                            name = presetName,
                            bands = eqState.bandLevels,
                            bass = eqState.bassBoostStrength,
                            virt = eqState.virtualizerStrength
                        )
                        showSavePresetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) { Text("Cancel") }
            }
        )
    }
}
