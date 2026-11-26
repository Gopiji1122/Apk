package com.offlinepdf.studio.feature.scanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.offlinepdf.studio.core.designsystem.components.PhaseBadge

@Composable
fun ScannerScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.DocumentScanner,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))
        PhaseBadge(label = "Scheduled for Phase 3", active = false)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Offline Document Scanner",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Planned Phase 3 Implementation:", style = MaterialTheme.typography.titleMedium)
                Text("• CameraX live preview with automatic edge detection & stability capture")
                Text("• OpenCV 4-point perspective correction & 10 enhancement filters")
                Text("• Unlimited multi-page disk-backed capture queue with drag-to-reorder")
                Text("• Phase 4: Offline English & Hindi (Devanagari) OCR via Tesseract4Android")
            }
        }
    }
}
