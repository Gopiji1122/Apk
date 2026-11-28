package com.offlinepdf.studio.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.offlinepdf.studio.core.designsystem.R
import com.offlinepdf.studio.core.designsystem.components.EmptyState
import com.offlinepdf.studio.core.designsystem.components.PillTone
import com.offlinepdf.studio.core.designsystem.components.StatusPill
import com.offlinepdf.studio.core.designsystem.components.StudioCard

/**
 * Dedicated editing workspace, kept separate from the minimalist viewer.
 * The editing tools themselves arrive in later phases.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfEditorScreen(
    uriString: String,
    onBackToViewer: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackToViewer) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StudioCard(modifier = Modifier.fillMaxWidth()) {
                EmptyState(
                    icon = Icons.Outlined.Edit,
                    title = stringResource(R.string.editor_soon_title),
                    message = stringResource(R.string.editor_soon_body)
                )
            }
            listOf(
                R.string.editor_group_annotate,
                R.string.editor_group_content,
                R.string.editor_group_pages,
                R.string.editor_group_secure
            ).forEach { label ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(label), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    StatusPill(stringResource(R.string.pill_soon), tone = PillTone.NEUTRAL)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
