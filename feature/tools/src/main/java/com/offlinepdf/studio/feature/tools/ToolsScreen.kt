package com.offlinepdf.studio.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.offlinepdf.studio.core.designsystem.R
import com.offlinepdf.studio.core.designsystem.components.GradientActionCard
import com.offlinepdf.studio.core.designsystem.components.PillTone
import com.offlinepdf.studio.core.designsystem.components.ScreenHeader
import com.offlinepdf.studio.core.designsystem.components.SectionTitle
import com.offlinepdf.studio.core.designsystem.components.StatusPill
import com.offlinepdf.studio.core.designsystem.components.StudioCard
import com.offlinepdf.studio.core.designsystem.components.staggeredEntrance
import com.offlinepdf.studio.core.designsystem.theme.extras

private data class UpcomingTool(val titleRes: Int, val descRes: Int)

private val UPCOMING = listOf(
    UpcomingTool(R.string.tool_ocr, R.string.tool_ocr_desc),
    UpcomingTool(R.string.tool_annotate, R.string.tool_annotate_desc),
    UpcomingTool(R.string.tool_editor, R.string.tool_editor_desc),
    UpcomingTool(R.string.tool_organizer, R.string.tool_organizer_desc),
    UpcomingTool(R.string.tool_security, R.string.tool_security_desc)
)

@Composable
fun ToolsScreen(
    onOpenReader: () -> Unit = {},
    onOpenScanner: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenHeader(stringResource(R.string.tools_title), stringResource(R.string.tools_subtitle)) }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .staggeredEntrance(0),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GradientActionCard(
                    title = stringResource(R.string.tool_reader),
                    subtitle = stringResource(R.string.tool_reader_sub),
                    icon = Icons.Outlined.AutoStories,
                    brush = MaterialTheme.extras.brandBrush,
                    onClick = onOpenReader,
                    modifier = Modifier.weight(1f)
                )
                GradientActionCard(
                    title = stringResource(R.string.tool_scanner),
                    subtitle = stringResource(R.string.tool_scanner_sub),
                    icon = Icons.Outlined.DocumentScanner,
                    brush = MaterialTheme.extras.scanBrush,
                    onClick = onOpenScanner,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item { SectionTitle(stringResource(R.string.tools_coming_soon), Modifier.staggeredEntrance(1)) }

        items(UPCOMING.size) { index ->
            val tool = UPCOMING[index]
            StudioCard(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .staggeredEntrance(index + 2),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(tool.titleRes),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        StatusPill(stringResource(R.string.pill_soon), tone = PillTone.NEUTRAL)
                    }
                    Text(
                        stringResource(tool.descRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
