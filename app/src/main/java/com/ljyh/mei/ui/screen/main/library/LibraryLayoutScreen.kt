package com.ljyh.mei.ui.screen.main.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ljyh.mei.constants.LibraryCardHeightKey
import com.ljyh.mei.constants.LibraryPinnedKey
import com.ljyh.mei.constants.UserIdKey
import com.ljyh.mei.ui.component.layout.meiPageHorizontalPadding
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.screen.main.library.component.DEFAULT_LIBRARY_CARD_HEIGHT
import com.ljyh.mei.ui.screen.main.library.component.DEFAULT_PINNED_COUNT
import com.ljyh.mei.ui.screen.main.library.component.LibraryBannerCard
import com.ljyh.mei.ui.screen.main.library.component.LibraryPinned
import com.ljyh.mei.ui.screen.main.library.component.LibraryPinnedCodec
import com.ljyh.mei.ui.screen.main.library.component.MAX_LIBRARY_CARD_HEIGHT
import com.ljyh.mei.ui.screen.main.library.component.MIN_LIBRARY_CARD_HEIGHT
import com.ljyh.mei.ui.screen.main.library.component.PinnableSection
import com.ljyh.mei.ui.screen.main.library.component.coerceLibraryCardHeight
import com.ljyh.mei.ui.screen.main.library.component.encodeLibrarySectionOrder
import com.ljyh.mei.ui.screen.main.library.component.rememberLibrarySectionOrder
import com.ljyh.mei.utils.preferences.rememberPreference
import kotlin.math.roundToInt

/** 一行的数据形态：歌单和专辑都折成同一种，勾选与排序逻辑共用一套。 */
private data class PinnedItem(val id: String, val title: String, val subtitle: String)

/**
 * 音乐库首页布局：卡片高度、区块顺序、每个区块勾选上首页的条目与顺序。
 *
 * 排序一律用上下箭头而不是长按拖动：与原来的弹窗交互一致，条目多时也不容易拖错。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryLayoutScreen(
    viewModel: LibraryLayoutViewModel = hiltViewModel(),
) {
    val nav = LocalNavController.current
    val layoutDirection = LocalLayoutDirection.current
    val gutter = meiPageHorizontalPadding()
    val (userId, _) = rememberPreference(UserIdKey, "")
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()

    val sectionOrder = rememberLibrarySectionOrder()
    val (storedHeight, setStoredHeight) = rememberPreference(LibraryCardHeightKey, DEFAULT_LIBRARY_CARD_HEIGHT)
    val (pinnedRaw, setPinnedRaw) = rememberPreference(LibraryPinnedKey, "")

    val createdItems = remember(playlists, userId) {
        playlists.filter { it.author == userId }
            .map { PinnedItem(it.id, it.title, "${it.count} 首") }
    }
    val collectedItems = remember(playlists, userId) {
        playlists.filter { it.author != userId }
            .map { PinnedItem(it.id, it.title, "${it.authorName} · ${it.count} 首") }
    }
    val albumItems = remember(albums) {
        albums.map { PinnedItem(it.id.toString(), it.title, it.artist.joinToString(" / ") { a -> a.name }) }
    }

    val pinned = remember(pinnedRaw) { LibraryPinnedCodec.decode(pinnedRaw) }
    // 从未配置过时按默认前 4 展示，进来看到的就是首页当前状态。
    val effective = pinned ?: LibraryPinned(
        created = createdItems.take(DEFAULT_PINNED_COUNT).map { it.id },
        collected = collectedItems.take(DEFAULT_PINNED_COUNT).map { it.id },
        albums = albumItems.take(DEFAULT_PINNED_COUNT).map { it.id },
    )

    var query by remember { mutableStateOf("") }
    var slider by remember { mutableFloatStateOf(coerceLibraryCardHeight(storedHeight).toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(storedHeight) {
        if (!dragging) slider = coerceLibraryCardHeight(storedHeight).toFloat()
    }

    // 用 lambda 而不是局部 fun：局部函数在 compose 作用域里会被当成 composable，
    // 就没法塞进 clickable / onClick 这类普通回调里了。
    val persist: (LibraryPinned) -> Unit = { next ->
        setPinnedRaw(LibraryPinnedCodec.encode(next))
    }

    val toggle: (PinnableSection, String) -> Unit = { section, id ->
        val current = effective.idsOf(section)
        val next = if (id in current) current - id else current + id
        persist(effective.with(section, next))
    }

    val move: (PinnableSection, String, Int) -> Unit = { section, id, delta ->
        val current = effective.idsOf(section).toMutableList()
        val index = current.indexOf(id)
        if (index >= 0) {
            current.removeAt(index)
            current.add((index + delta).coerceIn(0, current.size), id)
            persist(effective.with(section, current))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("音乐库首页布局") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = gutter.calculateStartPadding(layoutDirection),
                end = gutter.calculateEndPadding(layoutDirection),
                top = inner.calculateTopPadding() + 8.dp,
                bottom = inner.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "preview") {
                Column(Modifier.padding(bottom = 12.dp)) {
                    Text(
                        "预览",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    LibraryBannerCard(
                        title = "通勤路上",
                        subtitle = "32 首 · 第 8 首 · 顺序",
                        coverUrl = null,
                        fallbackIcon = Icons.Rounded.LibraryMusic,
                        height = slider.roundToInt().dp,
                        isCurrent = true,
                        isPlaying = true,
                        preparing = false,
                        canPlay = true,
                        onOpen = {},
                        onPlay = {},
                        onToggle = {},
                        onPlayFromStart = {},
                    )
                }
            }

            item(key = "height") {
                Column(Modifier.padding(bottom = 8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("卡片高度", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${slider.roundToInt()} dp",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = slider,
                        onValueChange = {
                            dragging = true
                            slider = it
                        },
                        onValueChangeFinished = {
                            dragging = false
                            setStoredHeight(coerceLibraryCardHeight(slider.roundToInt()))
                        },
                        valueRange = MIN_LIBRARY_CARD_HEIGHT.toFloat()..MAX_LIBRARY_CARD_HEIGHT.toFloat(),
                        steps = (MAX_LIBRARY_CARD_HEIGHT - MIN_LIBRARY_CARD_HEIGHT) / 4 - 1,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(120, 148, 176, 190).forEach { preset ->
                            HeightPreset(
                                value = preset,
                                current = slider,
                                onPick = {
                                    slider = it.toFloat()
                                    setStoredHeight(it)
                                },
                            )
                        }
                    }
                    Text(
                        "四类通栏卡（喜欢、歌单、专辑、播客）共用这个高度；长按卡片播放键可以从头播放。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                HorizontalDivider()
            }

            item(key = "order_header") {
                SectionLabel("区块顺序", Modifier.padding(top = 12.dp, bottom = 4.dp))
            }
            itemsIndexed(sectionOrder, key = { _, section -> "section_${section.id}" }) { index, section ->
                ReorderRow(
                    title = section.label,
                    index = index,
                    total = sectionOrder.size,
                    onMove = { delta ->
                        val next = sectionOrder.toMutableList().apply {
                            removeAt(index)
                            add((index + delta).coerceIn(0, size), section)
                        }
                        viewModel.saveSectionOrder(encodeLibrarySectionOrder(next))
                    },
                )
            }

            item(key = "pin_header") {
                Column(Modifier.padding(top = 16.dp)) {
                    HorizontalDivider()
                    SectionLabel("首页显示的歌单与专辑", Modifier.padding(top = 12.dp))
                    Text(
                        "勾选即显示，不设上限；全部取消时该区块整体隐藏。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索当前分组的条目") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
            }

            pinnedGroup(
                key = "created",
                title = "我创建的歌单",
                all = createdItems,
                selectedIds = effective.created,
                query = query,
                onToggle = { toggle(PinnableSection.CREATED, it) },
                onMove = { id, delta -> move(PinnableSection.CREATED, id, delta) },
            )
            pinnedGroup(
                key = "collected",
                title = "收藏歌单",
                all = collectedItems,
                selectedIds = effective.collected,
                query = query,
                onToggle = { toggle(PinnableSection.COLLECTED, it) },
                onMove = { id, delta -> move(PinnableSection.COLLECTED, id, delta) },
            )
            pinnedGroup(
                key = "albums",
                title = "收藏专辑",
                all = albumItems,
                selectedIds = effective.albums,
                query = query,
                onToggle = { toggle(PinnableSection.ALBUMS, it) },
                onMove = { id, delta -> move(PinnableSection.ALBUMS, id, delta) },
            )

            item(key = "reset") {
                Column(Modifier.padding(top = 24.dp)) {
                    HorizontalDivider()
                    TextButton(
                        onClick = { viewModel.resetLayout() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("重置布局（区块顺序 + 显示项 + 卡片高度）") }
                    TextButton(
                        onClick = { viewModel.clearPlayModeMemory() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("清除播放模式记忆（各歌单回到跟随全局）") }
                    Text(
                        "升级前听完的集数记录已被旧版本删除，重新听一次就会恢复显示。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * 一个分组：先是已勾选（带上下箭头，顺序即首页顺序），再是未勾选（点一下加入）。
 * 分组为空时整段不渲染，避免出现一个只有标题的空块。
 */
private fun LazyListScope.pinnedGroup(
    key: String,
    title: String,
    all: List<PinnedItem>,
    selectedIds: List<String>,
    query: String,
    onToggle: (String) -> Unit,
    onMove: (String, Int) -> Unit,
) {
    if (all.isEmpty()) return
    val keyword = query.trim()
    val byId = all.associateBy { it.id }
    val selected = selectedIds.mapNotNull(byId::get)
        .filter { keyword.isBlank() || it.title.contains(keyword, ignoreCase = true) }
    val unselected = all.filter { it.id !in selectedIds }
        .filter { keyword.isBlank() || it.title.contains(keyword, ignoreCase = true) }

    if (selected.isEmpty() && unselected.isEmpty()) return

    item(key = "${key}_header") {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                "已显示 ${selectedIds.size} / ${all.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    items(selected, key = { "${key}_sel_${it.id}" }) { item ->
        val index = selectedIds.indexOf(item.id)
        PinnedRow(
            item = item,
            selected = true,
            canUp = index > 0,
            canDown = index in 0 until selectedIds.lastIndex,
            onUp = { onMove(item.id, -1) },
            onDown = { onMove(item.id, 1) },
            onClick = { onToggle(item.id) },
        )
    }

    items(unselected, key = { "${key}_un_${it.id}" }) { item ->
        PinnedRow(
            item = item,
            selected = false,
            canUp = false,
            canDown = false,
            onUp = {},
            onDown = {},
            onClick = { onToggle(item.id) },
        )
    }
}

@Composable
private fun PinnedRow(
    item: PinnedItem,
    selected: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (selected) Icons.Rounded.Check else Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.subtitle.isNotBlank()) {
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            IconButton(enabled = canUp, onClick = onUp) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "上移")
            }
            IconButton(enabled = canDown, onClick = onDown) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "下移")
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        modifier = modifier,
    )
}

@Composable
private fun HeightPreset(value: Int, current: Float, onPick: (Int) -> Unit) {
    val selected = current.roundToInt() == value
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .clickable { onPick(value) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            "$value",
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReorderRow(
    title: String,
    index: Int,
    total: Int,
    onMove: (Int) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        IconButton(enabled = index > 0, onClick = { onMove(-1) }) {
            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "上移")
        }
        IconButton(enabled = index < total - 1, onClick = { onMove(1) }) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "下移")
        }
    }
}
