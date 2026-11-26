package com.offlinepdf.studio.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.offlinepdf.studio.core.designsystem.components.PhaseBadge

/**
 * Dedicated PDF Editor workspace kept completely separate from the minimalist PDF Viewer.
 * Populated with live Annotation, Content Editing, Page Organization, and Security tools in Phases 5–8.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfEditorScreen(
    uriString: String,
    onBackToViewer: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF Editor Workspace") },
                navigationIcon = {
                    IconButton(onClick = onBackToViewer) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to viewer")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PhaseBadge(label = "Editor Tools — Phases 5–8", active = false)
            Text(
                text = "Editing workspace is separated from the minimalist viewer.",
                style = MaterialTheme.typography.titleMedium
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Upcoming Editing Tools for this PDF:", style = MaterialTheme.typography.titleMedium)
                    Text("• Phase 5: Highlight, Underline, Freehand Pen, Sticky Notes & Shapes")
                    Text("• Phase 6: Add/Edit Text, Insert/Replace Images & Links")
                    Text("• Phase 7: Reorder, Rotate, Delete, Extract, Merge & Split Pages")
                    Text("• Phase 8: Forms, Signatures, Watermark, Compression & True Redaction")
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Tap Back to return immediately to the minimalist reader.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
