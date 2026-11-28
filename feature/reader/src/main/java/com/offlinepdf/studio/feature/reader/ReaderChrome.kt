package com.offlinepdf.studio.feature.reader

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinepdf.studio.core.common.Formatters
import com.offlinepdf.studio.core.database.BookmarkEntity
import com.offlinepdf.studio.core.designsystem.R
import com.offlinepdf.studio.core.designsystem.components.SegmentedChoice
import com.offlinepdf.studio.core.designsystem.theme.extras
import com.offlinepdf.studio.core.pdf.PdfMetadata
import com.offlinepdf.studio.core.pdf.PdfOutlineEntry
import com.offlinepdf.studio.core.pdf.ReadingCanvasTheme
import com.offlinepdf.studio.core.pdf.ReadingLayoutMode
import com.offlinepdf.studio.core.pdf.ZoomPreset
import kotlin.math.roundToInt

private val ChromeFg = Color(0xFFF3F4F8)
private val ChromeMuted = Color(0xFF9AA3B7)

@Composable
internal fun ReaderHeader(
    visible: Boolean,
    searchMode: Boolean,
    title: String,
    bookmarked: Boolean,
    query: String,
    matchCount: Int,
    activeMatch: Int,
    isSearching: Boolean,
    searchProgress: Float,
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onPrevMatch: () -> Unit,
    onNextMatch: () -> Unit,
    onNavigate: () -> Unit,
    onBookmark: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onViewOptions: () -> Unit,
    onCopyText: () -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(260, easing = FastOutSlowInEasing)) { -it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(160)),
        modifier = modifier
    ) {
        Surface(
            color = MaterialTheme.extras.readerChrome,
            contentColor = ChromeFg,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(0.6.dp, Color(0x33FFFFFF)),
            shadowElevation = 10.dp,
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (searchMode) {
                        SearchBar(
                            query, matchCount, activeMatch, isSearching,
                            onCloseSearch, onQueryChange, onPrevMatch, onNextMatch
                        )
                    } else {
                        HeaderIcon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.cd_back), onBack)
                        Text(
                            text = title.ifBlank { "Document.pdf" },
                            color = ChromeFg,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                        )
                        HeaderIcon(Icons.Outlined.Search, stringResource(R.string.cd_search), onOpenSearch)
                        HeaderIcon(Icons.Outlined.GridView, stringResource(R.string.cd_pages), onNavigate)
                        HeaderIcon(
                            if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                            stringResource(R.string.cd_bookmark), onBookmark,
                            tint = if (bookmarked) Color(0xFFFFB4BE) else ChromeFg
                        )
                        OverflowMenu(onSave, onShare, onViewOptions, onCopyText, onInfo)
                    }
                }
                if (searchMode && isSearching) {
                    LinearProgressIndicator(
                        progress = { searchProgress },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = Color(0xFFFF8A98),
                        trackColor = Color.Transparent
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = ChromeFg,
    enabled: Boolean = true
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(42.dp)) {
        Icon(icon, contentDescription = description, tint = if (enabled) tint else ChromeMuted.copy(alpha = 0.5f), modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SearchBar(
    query: String,
    matchCount: Int,
    activeMatch: Int,
    isSearching: Boolean,
    onClose: () -> Unit,
    onQueryChange: (String) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }

    HeaderIcon(Icons.Outlined.Close, stringResource(R.string.cd_close), onClose)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0x22FFFFFF),
        modifier = Modifier
            .weight(1f)
            .height(38.dp)
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(stringResource(R.string.reader_search_hint), color = ChromeMuted, fontSize = 14.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = ChromeFg, fontSize = 15.sp),
                cursorBrush = SolidColor(Color(0xFFFF8A98)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); onNext() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
            )
        }
    }
    if (query.isNotBlank()) {
        Text(
            text = when {
                matchCount > 0 -> stringResource(R.string.reader_match_of, activeMatch + 1, matchCount)
                isSearching -> "…"
                else -> stringResource(R.string.reader_no_matches)
            },
            color = ChromeFg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
    }
    HeaderIcon(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.cd_prev), onPrev, enabled = matchCount > 0)
    HeaderIcon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.cd_next), onNext, enabled = matchCount > 0)
}

@Composable
private fun OverflowMenu(
    onSave: () -> Unit,
    onShare: () -> Unit,
    onViewOptions: () -> Unit,
    onCopyText: () -> Unit,
    onInfo: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        HeaderIcon(Icons.Outlined.MoreVert, stringResource(R.string.cd_menu), { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            @Composable
            fun item(label: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, action: () -> Unit) {
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    leadingIcon = { Icon(icon, null) },
                    onClick = { open = false; action() }
                )
            }
            item(R.string.menu_view_options, Icons.Outlined.Tune, onViewOptions)
            item(R.string.menu_copy_text, Icons.Outlined.ContentCopy, onCopyText)
            item(R.string.menu_save, Icons.Outlined.Download, onSave)
            item(R.string.menu_share, Icons.Outlined.Share, onShare)
            item(R.string.menu_info, Icons.Outlined.Info, onInfo)
        }
    }
}

/** Always-visible-with-controls page chip; tap to jump to a page. */
@Composable
internal fun PageChip(
    visible: Boolean,
    current: Int,
    total: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + slideInVertically(tween(240)) { it / 2 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 2 },
        modifier = modifier
    ) {
        Surface(
            color = MaterialTheme.extras.readerChrome,
            contentColor = ChromeFg,
            shape = RoundedCornerShape(50),
            border = BorderStroke(0.6.dp, Color(0x33FFFFFF)),
            shadowElevation = 8.dp,
            modifier = Modifier.clickable(onClick = onClick)
        ) {
            Text(
                text = "${current + 1} / $total",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
internal fun EditFab(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.scaleIn(tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
        exit = androidx.compose.animation.scaleOut(tween(180)) + fadeOut(tween(150)),
        modifier = modifier
    ) {
        FloatingActionButton(
            onClick = onClick,
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(54.dp)
        ) {
            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.cd_edit), modifier = Modifier.size(22.dp))
        }
    }
}

/** Slim right-edge fast scroller with a page pill; keeps its position when re-grabbed. */
@Composable
internal fun ReaderScrubber(
    visible: Boolean,
    currentPage: Int,
    pageCount: Int,
    isDragging: Boolean,
    onDragStateChanged: (Boolean) -> Unit,
    onScrubToPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(140)),
        exit = fadeOut(tween(300)),
        modifier = modifier
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxHeight()
                .width(120.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            val thumbHeight = 46.dp
            val thumbPx = with(LocalDensity.current) { thumbHeight.toPx() }
            val trackPx = (constraints.maxHeight.toFloat() - thumbPx).coerceAtLeast(1f)
            val fraction = if (pageCount > 1) currentPage.toFloat() / (pageCount - 1) else 0f
            val latestFraction by rememberUpdatedState(fraction)
            val latestScrub by rememberUpdatedState(onScrubToPage)
            val latestDrag by rememberUpdatedState(onDragStateChanged)
            var dragFraction by remember { mutableFloatStateOf(fraction) }
            LaunchedEffect(fraction, isDragging) { if (!isDragging) dragFraction = fraction }
            val shown = if (isDragging) dragFraction else fraction

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.offset { IntOffset(0, (shown * trackPx).roundToInt()) }
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.extras.readerChrome,
                    contentColor = ChromeFg,
                    border = BorderStroke(0.6.dp, Color(0x33FFFFFF)),
                    shadowElevation = 5.dp,
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        "${currentPage + 1}/$pageCount",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                val shape = RoundedCornerShape(topStart = 23.dp, bottomStart = 23.dp)
                Surface(
                    shape = shape,
                    color = MaterialTheme.extras.readerChrome,
                    contentColor = ChromeFg,
                    modifier = Modifier
                        .width(28.dp)
                        .height(thumbHeight)
                        .shadow(5.dp, shape)
                        .border(0.6.dp, Color(0x33FFFFFF), shape)
                        .pointerInput(pageCount, trackPx) {
                            detectVerticalDragGestures(
                                onDragStart = { dragFraction = latestFraction; latestDrag(true) },
                                onDragEnd = { latestDrag(false) },
                                onDragCancel = { latestDrag(false) },
                                onVerticalDrag = { change, amount ->
                                    change.consume()
                                    dragFraction = (dragFraction + amount / trackPx).coerceIn(0f, 1f)
                                    latestScrub((dragFraction * (pageCount - 1)).roundToInt())
                                }
                            )
                        }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(Icons.Outlined.KeyboardArrowUp, null, modifier = Modifier.size(14.dp))
                        Icon(Icons.Outlined.KeyboardArrowDown, null, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

enum class NavTab { PAGES, CONTENTS, BOOKMARKS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NavigateSheet(
    initialTab: NavTab,
    pageCount: Int,
    currentPage: Int,
    outline: List<PdfOutlineEntry>,
    bookmarks: List<BookmarkEntity>,
    renderThumbnail: suspend (Int) -> Bitmap?,
    onJump: (Int) -> Unit,
    onDeleteBookmark: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var tab by remember { mutableStateOf(initialTab) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            SegmentedChoice(
                options = listOf(
                    NavTab.PAGES to stringResource(R.string.tab_pages),
                    NavTab.CONTENTS to stringResource(R.string.tab_contents),
                    NavTab.BOOKMARKS to stringResource(R.string.tab_bookmarks)
                ),
                selected = tab,
                onSelect = { tab = it }
            )
            Spacer(Modifier.height(12.dp))
            Box(modifier = Modifier.height(460.dp)) {
                when (tab) {
                    NavTab.PAGES -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 100.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(pageCount, key = { "thumb_$it" }) { idx ->
                            ThumbCell(idx, idx == currentPage, renderThumbnail) { onJump(idx) }
                        }
                    }
                    NavTab.CONTENTS -> if (outline.isEmpty()) {
                        EmptyLine(stringResource(R.string.contents_empty))
                    } else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(outline) { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onJump(entry.pageIndex) }
                                    .padding(start = (entry.depth * 16).dp, top = 12.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    entry.title,
                                    style = if (entry.depth == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${entry.pageIndex + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    NavTab.BOOKMARKS -> if (bookmarks.isEmpty()) {
                        EmptyLine(stringResource(R.string.bookmarks_empty))
                    } else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(bookmarks, key = { it.pageIndex }) { bm ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onJump(bm.pageIndex) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Bookmark, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp))
                                Text(stringResource(R.string.page_n, bm.pageIndex + 1), modifier = Modifier.weight(1f))
                                IconButton(onClick = { onDeleteBookmark(bm.pageIndex) }) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.cd_delete))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLine(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ThumbCell(
    pageIndex: Int,
    selected: Boolean,
    render: suspend (Int) -> Bitmap?,
    onClick: () -> Unit
) {
    var thumb by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(pageIndex) { thumb = render(pageIndex) }
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                MaterialTheme.shapes.small
            )
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val bmp = thumb
        Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            if (bmp != null && !bmp.isRecycled) {
                Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
        Text("${pageIndex + 1}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ViewOptionsSheet(
    layout: ReadingLayoutMode,
    preset: ZoomPreset,
    theme: ReadingCanvasTheme,
    onLayout: (ReadingLayoutMode) -> Unit,
    onPreset: (ZoomPreset) -> Unit,
    onTheme: (ReadingCanvasTheme) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Label(stringResource(R.string.view_layout))
            SegmentedChoice(
                options = listOf(
                    ReadingLayoutMode.CONTINUOUS_VERTICAL to stringResource(R.string.layout_scroll),
                    ReadingLayoutMode.SINGLE_PAGE_HORIZONTAL to stringResource(R.string.layout_single),
                    ReadingLayoutMode.TWO_PAGE_SPREAD to stringResource(R.string.layout_spread)
                ),
                selected = layout, onSelect = onLayout
            )
            Label(stringResource(R.string.view_zoom))
            SegmentedChoice(
                options = listOf(
                    ZoomPreset.FIT_WIDTH to stringResource(R.string.zoom_fit_width),
                    ZoomPreset.FIT_PAGE to stringResource(R.string.zoom_fit_page),
                    ZoomPreset.ACTUAL_SIZE to stringResource(R.string.zoom_actual)
                ),
                selected = preset, onSelect = onPreset
            )
            Label(stringResource(R.string.view_canvas))
            SegmentedChoice(
                options = listOf(
                    ReadingCanvasTheme.DARK_CANVAS to stringResource(R.string.canvas_dark),
                    ReadingCanvasTheme.LIGHT to stringResource(R.string.canvas_light),
                    ReadingCanvasTheme.NIGHT_INVERTED to stringResource(R.string.canvas_night)
                ),
                selected = theme, onSelect = onTheme
            )
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun InfoDialog(title: String, sizeBytes: Long, meta: PdfMetadata, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.info_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${stringResource(R.string.info_name)}: ${title.ifBlank { meta.title }}")
                Text("${stringResource(R.string.info_size)}: ${Formatters.formatFileSize(sizeBytes)}")
                Text("${stringResource(R.string.info_pages)}: ${meta.pageCount}")
                Text("${stringResource(R.string.info_format)}: ${meta.format}")
                Text("${stringResource(R.string.info_encryption)}: ${meta.encryption}")
                if (meta.author.isNotBlank()) Text("${stringResource(R.string.info_author)}: ${meta.author}")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
    )
}

@Composable
internal fun GoToPageDialog(pageCount: Int, onGo: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val target = text.toIntOrNull()?.takeIf { it in 1..pageCount }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goto_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(6) },
                singleLine = true,
                label = { Text(stringResource(R.string.goto_hint, pageCount)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { target?.let { onGo(it - 1) } })
            )
        },
        confirmButton = {
            TextButton(onClick = { target?.let { onGo(it - 1) } }, enabled = target != null) {
                Text(stringResource(R.string.action_go))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
internal fun PasswordDialog(error: Boolean, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    isError = error,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit(password) }),
                    label = { Text(stringResource(R.string.password_label)) }
                )
                if (error) {
                    Text(
                        stringResource(R.string.password_wrong),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(password) }) { Text(stringResource(R.string.action_unlock)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
internal fun ExternalLinkDialog(uri: String, onOpen: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.link_title)) },
        text = { Text(uri, maxLines = 4, overflow = TextOverflow.Ellipsis) },
        confirmButton = { TextButton(onClick = onOpen) { Text(stringResource(R.string.link_open)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
