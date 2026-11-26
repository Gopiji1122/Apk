package com.offlinepdf.studio.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.offlinepdf.studio.core.designsystem.components.PhaseBadge

private data class ToolRoadmapItem(
    val title: String,
    val description: String,
    val phase: String,
    val live: Boolean
)

private val TOOL_ITEMS = listOf(
    ToolRoadmapItem("MuPDF Reader & Search", "Lazy rendering, zoom, rotation, night mode, password unlock, in-PDF text search & bookmarks.", "Phase 1 — Live", true),
    ToolRoadmapItem("Document Scanner", "CameraX + OpenCV edge detection, perspective crop, 10 filters, unlimited pages.", "Phase 3", false),
    ToolRoadmapItem("Offline OCR (EN + HI)", "Tesseract4Android Devanagari + English text extraction & searchable PDF.", "Phase 4", false),
    ToolRoadmapItem("PDF Annotations", "Highlight, underline, freehand ink, notes, shapes in PDF-space coordinates.", "Phase 5", false),
    ToolRoadmapItem("PDF Content Editor", "Add/edit supported text, images, links, and layout overlays.", "Phase 6", false),
    ToolRoadmapItem("Page Organizer", "Merge, split, extract, reorder, rotate, delete, and insert pages.", "Phase 7", false),
    ToolRoadmapItem("Forms, Signatures & Security", "Fill forms, reusable signatures, compression, watermarks, passwords & true redaction.", "Phase 8", false)
)

@Composable
fun ToolsScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("PDF Toolkit", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Only implemented tools are active; upcoming phases are clearly labeled.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(TOOL_ITEMS) { item ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        PhaseBadge(label = item.phase, active = item.live)
                    }
                    Text(
                        item.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
