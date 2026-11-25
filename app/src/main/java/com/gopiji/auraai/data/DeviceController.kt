package com.gopiji.auraai.data

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.provider.AlarmClock
import android.provider.Settings

data class ParsedDeviceAction(
    val rawTag: String,
    val type: String,
    val param1: String,
    val param2: String,
    val label: String
)

object DeviceController {
    const val ACTION_INSTRUCTIONS =
        "If the user explicitly asks to control their phone (set a timer, set an alarm, turn flashlight on/off, or open Wi-Fi/Bluetooth settings), include ONE action tag at the end of your reply in this exact format: [ACTION:TIMER|seconds|label] or [ACTION:ALARM|hour24|minute|label] or [ACTION:FLASHLIGHT|ON|] or [ACTION:FLASHLIGHT|OFF|] or [ACTION:SETTINGS|WIFI|]."

    fun parseAction(text: String): ParsedDeviceAction? {
        val regex = Regex("\\[ACTION:([A-Z_]+)\\|([^|\\]]*)\\|?([^\\]]*)\\]")
        val match = regex.find(text) ?: return null
        val type = match.groupValues[1]
        val p1 = match.groupValues[2].trim()
        val p2 = match.groupValues[3].trim()
        val label = when (type) {
            "TIMER" -> "Set Timer (${p1}s)"
            "ALARM" -> "Set Alarm ($p1:$p2)"
            "FLASHLIGHT" -> "Turn Flashlight $p1"
            "SETTINGS" -> "Open $p1 Settings"
            else -> "Run $type"
        }
        return ParsedDeviceAction(match.value, type, p1, p2, label)
    }

    fun executeAction(context: Context, action: ParsedDeviceAction): String {
        return try {
            when (action.type) {
                "TIMER" -> {
                    val secs = action.param1.toIntOrNull()?.coerceAtLeast(1) ?: 60
                    val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, secs)
                        putExtra(AlarmClock.EXTRA_MESSAGE, action.param2.ifEmpty { "Aura AI Timer" })
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    "Opened Timer for $secs seconds."
                }
                "ALARM" -> {
                    val hr = action.param1.toIntOrNull()?.coerceIn(0, 23) ?: 7
                    val min = action.param2.split("|").firstOrNull()?.toIntOrNull()?.coerceIn(0, 59) ?: 0
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, hr)
                        putExtra(AlarmClock.EXTRA_MINUTES, min)
                        putExtra(AlarmClock.EXTRA_MESSAGE, "Aura AI Alarm")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    "Opened Alarm for %02d:%02d.".format(hr, min)
                }
                "FLASHLIGHT" -> {
                    val turnOn = action.param1.equals("ON", ignoreCase = true)
                    val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    val camId = cm.cameraIdList.firstOrNull()
                    if (camId != null) {
                        cm.setTorchMode(camId, turnOn)
                        "Flashlight turned ${if (turnOn) "ON" else "OFF"}."
                    } else {
                        "No flashlight hardware found."
                    }
                }
                "SETTINGS" -> {
                    val target = when (action.param1.uppercase()) {
                        "WIFI" -> Settings.ACTION_WIFI_SETTINGS
                        "BLUETOOTH" -> Settings.ACTION_BLUETOOTH_SETTINGS
                        else -> Settings.ACTION_SETTINGS
                    }
                    val intent = Intent(target).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(intent)
                    "Opened ${action.param1} settings."
                }
                else -> "Unsupported action: ${action.type}"
            }
        } catch (e: Exception) {
            "Action failed: ${e.message}"
        }
    }
}
