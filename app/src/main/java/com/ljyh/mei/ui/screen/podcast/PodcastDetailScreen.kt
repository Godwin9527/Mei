package com.ljyh.mei.ui.screen.podcast

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ljyh.mei.constants.CommonImageRadius
import com.ljyh.mei.constants.DefaultPodcastTextSize
import com.ljyh.mei.constants.PodcastTextSizeKey
import com.ljyh.mei.data.model.domain.toMediaItem
import com.ljyh.mei.data.model.domain.toMediaMetadata
import com.ljyh.mei.data.model.podcast.DjProgram
import com.ljyh.mei.data.model.podcast.DjRadio
import com.ljyh.mei.playback.queue.ListQueue
import com.ljyh.mei.playback.queue.QueueSource
import com.ljyh.mei.ui.component.MeiStatePanel
import com.ljyh.mei.ui.component.glass.MeiGlassBackButton
import com.ljyh.mei.ui.component.glass.MeiTopAppBar
import com.ljyh.mei.ui.component.layout.meiPageGutter
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets
import com.ljyh.mei.ui.local.LocalPlayerConnection
import com.ljyh.mei.utils.TimeUtils
import com.ljyh.mei.utils.preferences.rememberPreference
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 单个电台的节目列表：可排序、可搜索、点任意一期直接播放（队列为该电台的当前排序）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastDetailScreen(
    radioId: Long,
    viewModel: PodcastViewModel = hiltViewModel(),
) {
    val nav = LocalNavController.current
    val playerConnection = LocalPlayerConnection.current
    val context = LocalContext.current
    val state by viewModel.programs.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val pageHaze = rememberHazeState()
    val (textSize, _) = rememberPreference(PodcastTextSizeKey, DefaultPodcastTextSize)
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(radioId) { viewModel.openRadio(radioId) }

    val radio = state.radio
    val title = radio?.name?.takeIf { it.isNotBlank() } ?: "播客"

    fun buildQueue(): ListQueue? {
        val pairs = state.programs.mapNotNull { program ->
            program.toMediaMetadata()?.let { meta -> meta.id.toString() to meta.toMediaItem() }
        }
        if (pairs.isEmpty()) return null
        return ListQueue(
            id = "podcast_$radioId",
            title = title,
            source = QueueSource(QueueSource.Type.PODCAST, radioId.toString(), title),
            items = pairs,
            startIndex = 0,
        )
    }

    Scaffold(
        topBar = {
            MeiTopAppBar(
                hazeState = pageHaze,
                title = {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { MeiGlassBackButton(onClick = { nav.popBackStack() }) },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("清除本电台播放记忆") },
                                onClick = {
                                    menuOpen = false
                                    confirmClear = true
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier.hazeSource(pageHaze).fillMaxSize(),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding() + 24.dp,
            ),
        ) {
            item(key = "podcast_header") {
                PodcastHeader(radio = radio, total = state.total, fallbackTitle = title)
            }
            item(key = "podcast_search") {
                PodcastSearchField(
                    value = state.query,
                    onValueChange = { viewModel.setQuery(it) },
                    placeholder = "搜索本电台的节目",
                    modifier = Modifier
                        .padding(horizontal = meiPageGutter())
                        .padding(bottom = 10.dp),
                )
            }
            item(key = "podcast_sort") {
                PodcastSortRow(
                    selected = state.sort,
                    onSelect = { viewModel.setSort(it) },
                )
            }
            when {
                state.loading && state.programs.isEmpty() -> item(key = "podcast_loading") {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }

                state.error != null && state.programs.isEmpty() -> item(key = "podcast_error") {
                    MeiStatePanel(
                        title = "暂时无法加载节目",
                        icon = Icons.Rounded.CloudOff,
                        actionLabel = "重新加载",
                        onAction = { viewModel.reload() },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    )
                }

                state.programs.isEmpty() -> item(key = "podcast_empty") {
                    MeiStatePanel(
                        title = if (state.query.isNotBlank()) "没有匹配的节目" else "这个电台还没有节目",
                        icon = Icons.Rounded.Mic,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    )
                }

                else -> itemsIndexed(
                    state.programs,
                    key = { _, program -> "program_${program.mainSong?.id ?: program.id}" },
                ) { index, program ->
                    PodcastProgramRow(
                        program = program,
                        index = index + 1,
                        textSize = textSize,
                        listenedMs = state.progressBySong[program.mainSong?.id?.toString()],
                        onClick = {
                            val songId = program.mainSong?.id?.takeIf { it > 0 } ?: return@PodcastProgramRow
                            playerConnection?.onTrackClicked(
                                trackId = songId.toString(),
                                fallbackSource = QueueSource(
                                    QueueSource.Type.PODCAST, radioId.toString(), title,
                                ),
                                buildQueue = { buildQueue() },
                            )
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 64.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = 0.5.dp,
                    )
                }
            }
            if (state.loadingAll || state.error != null) item(key = "podcast_more") {
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    if (state.loadingAll) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "正在加载 ${state.loaded}/${maxOf(state.total, state.loaded)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        TextButton(onClick = { viewModel.loadMore() }) { Text("加载中断，点此重试") }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清除播放记忆") },
            text = { Text("将清空「$title」所有单集的收听进度，下次从每集开头播放。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearRadioProgress { count ->
                        Toast.makeText(
                            context,
                            if (count > 0) "已清除 $count 集的播放记忆" else "没有可清除的记忆",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }) { Text("清除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun PodcastHeader(radio: DjRadio?, total: Int, fallbackTitle: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = meiPageGutter())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = radio?.picUrl.orEmpty(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(CommonImageRadius)),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    radio?.name?.takeIf { it.isNotBlank() } ?: fallbackTitle,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                val host = radio?.dj?.nickname.orEmpty()
                if (host.isNotBlank()) {
                    Text(
                        host,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                val count = when {
                    total > 0 -> total
                    (radio?.programCount ?: 0) > 0 -> radio?.programCount ?: 0
                    else -> 0
                }
                if (count > 0) {
                    Text(
                        "$count 个节目",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PodcastSortRow(selected: PodcastSort, onSelect: (PodcastSort) -> Unit) {
    // 四个选项在窄屏放不下一行，横向可滚动，避免最后一个被裁掉。
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        contentPadding = PaddingValues(horizontal = meiPageGutter()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(PodcastSort.entries, key = { it.name }) { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(option.label, fontSize = 12.sp) },
            )
        }
    }
}

@Composable
private fun PodcastProgramRow(
    program: DjProgram,
    index: Int,
    textSize: Int,
    listenedMs: Long?,
    onClick: () -> Unit,
) {
    val date = remember(program.createTime) {
        program.createTime.takeIf { it > 0 }?.let {
            SimpleDateFormat("yyyy/M/d", Locale.getDefault()).format(Date(it))
        }
    }
    val meta = listOfNotNull(
        program.serialNum?.takeIf { it > 0 }?.let { "第 $it 期" },
        date,
        program.duration.takeIf { it > 0 }?.let { TimeUtils.formatDuration(it) },
    ).joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = meiPageGutter(), vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(CommonImageRadius)),
        ) {
            AsyncImage(
                model = program.coverUrl.orEmpty(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                program.name.ifBlank { "第 $index 期" },
                fontSize = textSize.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotBlank() || listenedMs != null) {
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        meta,
                        fontSize = (textSize - 3).coerceAtLeast(10).sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (listenedMs != null) {
                        if (meta.isNotBlank()) {
                            Text(
                                " · ",
                                fontSize = (textSize - 3).coerceAtLeast(10).sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "已听 ${TimeUtils.formatDuration(listenedMs)}",
                            fontSize = (textSize - 3).coerceAtLeast(10).sp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
