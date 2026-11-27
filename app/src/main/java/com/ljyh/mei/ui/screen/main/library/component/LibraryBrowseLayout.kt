package com.ljyh.mei.ui.screen.main.library.component

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ljyh.mei.constants.LibraryCardHeightKey
import com.ljyh.mei.constants.LibraryPinnedKey
import com.ljyh.mei.constants.PlayModeMemoryKey
import com.ljyh.mei.constants.RecentPodcastKey
import com.ljyh.mei.data.model.domain.MediaMetadata
import com.ljyh.mei.data.model.domain.RecentPodcastCodec
import com.ljyh.mei.data.model.domain.RecentPodcastEpisode
import com.ljyh.mei.data.model.domain.toMediaItem
import com.ljyh.mei.data.model.room.PlaybackProgress
import com.ljyh.mei.data.model.room.Playlist
import com.ljyh.mei.data.model.room.QueueResume
import com.ljyh.mei.di.repository.queueResumeKeyOf
import com.ljyh.mei.playback.PlayModeSnapshot
import com.ljyh.mei.playback.decodePlayModeMemory
import com.ljyh.mei.playback.queue.ListQueue
import com.ljyh.mei.playback.queue.QueueSource
import com.ljyh.mei.playback.shortLabel
import com.ljyh.mei.ui.component.MeiSectionTitle
import com.ljyh.mei.ui.component.MeiShowAllButton
import com.ljyh.mei.ui.component.MeiStatePanel
import com.ljyh.mei.ui.component.card.ArtworkCard
import com.ljyh.mei.ui.component.glass.*
import com.ljyh.mei.ui.component.layout.LocalPageStartInset
import com.ljyh.mei.ui.component.layout.meiPageGutter
import com.ljyh.mei.ui.component.layout.meiPageHorizontalPadding
import com.ljyh.mei.ui.component.utils.rememberDeviceInfo
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.local.LocalPlayerConnection
import com.ljyh.mei.ui.model.Album
import com.ljyh.mei.ui.screen.Screen
import com.ljyh.mei.ui.screen.main.library.*
import com.ljyh.mei.ui.screen.podcast.PodcastPlaybackViewModel
import com.ljyh.mei.utils.preferences.rememberPreference
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

/** Artwork composition leads the overview; secondary collections retain their own browsing rhythm. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryBrowseLayout(
    state: LibraryUiState,
    likedArtwork: String?,
    likedSongTitle: String?,
    signedIn: Boolean,
    scrollBehavior: TopAppBarScrollBehavior,
    preparingPlayback: Boolean,
    preparingKey: String?,
    canPlay: Boolean,
    resumeByKey: Map<String, QueueResume>,
    onPlayLiked: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenCollection: (LibraryCollection) -> Unit,
    onLogin: () -> Unit,
    onRetry: () -> Unit,
    onOpenProfile: () -> Unit = {},
    onPlayPlaylist: (String, Boolean) -> Unit = { id, _ -> onPlayLiked(id) },
    onPlayAlbum: (Long, Boolean) -> Unit = { _, _ -> },
    onTogglePlayback: () -> Unit = {},
) {
    val source = rememberHazeState()
    val listState = rememberLazyListState()
    val tablet = rememberDeviceInfo().isTablet
    val gutter = meiPageGutter()
    val rowPadding = meiPageHorizontalPadding()
    val sideInset = LocalPageStartInset.current
    val bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val distance = with(LocalDensity.current) { 56.dp.toPx() }
    val progress by remember(listState, distance) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / distance).coerceIn(0f, 1f)
        }
    }
    val data = (state as? LibraryUiState.Content)?.data
    val sectionOrder = rememberLibrarySectionOrder()

    val cardHeightPref = rememberPreference(LibraryCardHeightKey, DEFAULT_LIBRARY_CARD_HEIGHT)
    val cardHeight = coerceLibraryCardHeight(cardHeightPref.value).dp
    val pinnedPref = rememberPreference(LibraryPinnedKey, "")
    val pinned = remember(pinnedPref.value) { LibraryPinnedCodec.decode(pinnedPref.value) }
    val modePref = rememberPreference(PlayModeMemoryKey, "")
    val modeMemory = remember(modePref.value) { decodePlayModeMemory(modePref.value) }

    val connection = LocalPlayerConnection.current
    val queueSource = connection?.currentQueueSource?.collectAsStateWithLifecycle()?.value
    val currentQueueKey = remember(queueSource) { queueResumeKeyOf(queueSource) }
    val playingAudio = connection?.isPlaying?.collectAsStateWithLifecycle()?.value ?: false

    val composition = remember(data) { data?.dashboardSelection() }
    val collected = remember(data) { data?.playlistsIn(LibraryCollection.Collected).orEmpty() }
    val createdLimit = if (tablet) 8 else 4
    val remainingLimit = createdLimit - (if (composition?.topCreated != null) 1 else 0)
    val collectedLimit = if (tablet) 8 else 6
    val loading = state is LibraryUiState.Loading || data?.let {
        it.playlistsLoading && it.createdPlaylists.isEmpty() && it.collectedPlaylists.isEmpty()
    } == true
    val failed = state is LibraryUiState.Error || data?.let {
        it.playlistsError != null && it.createdPlaylists.isEmpty() && it.collectedPlaylists.isEmpty()
    } == true

    BoxWithConstraints(Modifier
        .fillMaxSize()
        .nestedScroll(scrollBehavior.nestedScrollConnection)) {
        val compactWide = !tablet && sideInset > 0.dp
        val direction = LocalLayoutDirection.current
        val cardWidth = maxWidth - rowPadding.calculateStartPadding(direction) - rowPadding.calculateEndPadding(direction)
        val mobileColumns = if (compactWide) {
            ((cardWidth + 16.dp) / 148.dp).toInt().coerceIn(2, 4)
        } else 2
        val columns =
            if (tablet) ((maxWidth - gutter * 2 + 16.dp) / 184.dp).toInt().coerceIn(2, 6) else 2
        val createdRows = remember(composition, remainingLimit, columns) {
            composition?.remainingCreated.orEmpty().take(remainingLimit).chunked(columns)
        }
        CompositionLocalProvider(
            LocalMeiHazeState provides source,
            LocalMeiPageProgress provides progress
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(source)
                    .background(MaterialTheme.colorScheme.surface),
                contentPadding = PaddingValues(top = top + 12.dp, bottom = bottom + 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "title") {
                    MeiLargeTitle(
                        "音乐库",
                        Modifier.padding(rowPadding),
                        startPadding = 0.dp
                    )
                }
                when {
                    !signedIn -> item(key = "login") {
                        MeiStatePanel(
                            "登录查看歌单与收藏",
                            Icons.Rounded.LibraryMusic,
                            actionLabel = "登录账号",
                            onAction = onLogin
                        )
                    }

                    loading -> item(key = "loading") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            Alignment.Center
                        ) { CircularProgressIndicator() }
                    }

                    failed -> item(key = "error") {
                        MeiStatePanel(
                            "暂时无法同步音乐库",
                            Icons.Rounded.CloudOff,
                            actionLabel = "重试",
                            onAction = onRetry
                        )
                    }

                    else -> {
                        if (!tablet && data != null) {
                            mobileLibraryContent(
                                data = data,
                                likedArtwork = likedArtwork,
                                cardHeight = cardHeight,
                                pinned = pinned,
                                resumeByKey = resumeByKey,
                                modeMemory = modeMemory,
                                currentQueueKey = currentQueueKey,
                                playingAudio = playingAudio,
                                preparingKey = preparingKey,
                                canPlay = canPlay,
                                rowPadding = rowPadding,
                                order = sectionOrder,
                                onOpenPlaylist = onOpenPlaylist,
                                onOpenAlbum = onOpenAlbum,
                                onOpenCollection = onOpenCollection,
                                onPlayPlaylist = onPlayPlaylist,
                                onPlayAlbum = onPlayAlbum,
                                onTogglePlayback = onTogglePlayback,
                            )
                        } else {
                            if (composition != null) item(key = "library_dashboard") {
                                LibraryDashboard(
                                    composition,
                                    likedArtwork,
                                    preparingPlayback,
                                    canPlay,
                                    loadingRecent = data?.playlistsLoading == true,
                                    recentSyncFailed = data?.playlistsError != null,
                                    onOpenPlaylist = onOpenPlaylist,
                                    onPlayLiked = onPlayLiked,
                                    modifier = Modifier.padding(rowPadding)
                                )
                            }
                            if (!composition?.remainingCreated.isNullOrEmpty() || composition?.topCreated == null) item(
                                key = "created_heading"
                            ) {
                                LibrarySectionHeading(
                                    "我创建的",
                                    data?.createdPlaylistOverview().orEmpty().size > createdLimit,
                                    modifier = Modifier.padding(rowPadding)
                                ) { onOpenCollection(LibraryCollection.Created) }
                            }
                            if (composition?.remainingCreated.isNullOrEmpty() && composition?.topCreated == null) item(
                                key = "created_empty"
                            ) {
                                LibraryEmptyLabel("还没有创建歌单", Modifier.padding(rowPadding))
                            }
                            items(createdRows, key = { "created_row_${it.first().id}" }) { row ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(rowPadding),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    row.forEach { playlist ->
                                        ArtworkCard(
                                            playlist.title,
                                            playlist.cover,
                                            Modifier.weight(1f),
                                            subtitle = "${playlist.count} 首",
                                            onClick = { onOpenPlaylist(playlist.id) })
                                    }
                                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                            item(key = "collected_heading") {
                                LibrarySectionHeading(
                                    "收藏歌单", collected.size > collectedLimit,
                                    modifier = Modifier
                                        .padding(rowPadding)
                                        .padding(top = 16.dp)
                                ) { onOpenCollection(LibraryCollection.Collected) }
                            }
                            if (collected.isEmpty()) item(key = "collected_empty") {
                                LibraryEmptyLabel("还没有收藏歌单", Modifier.padding(rowPadding))
                            } else item(key = "collected_shelf") {
                                LazyRow(
                                    contentPadding = rowPadding,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(
                                        collected.take(collectedLimit),
                                        key = { it.id }) { playlist ->
                                        ArtworkCard(
                                            playlist.title,
                                            playlist.cover,
                                            Modifier.width(if (tablet) 144.dp else 128.dp),
                                            subtitle = playlist.authorName,
                                            reserveSubtitleSpace = true,
                                            onClick = { onOpenPlaylist(playlist.id) })
                                    }
                                }
                            }
                            item(key = "albums_entry") {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(rowPadding)
                                        .padding(top = 16.dp)
                                        .clickable(role = Role.Button) {
                                            onOpenCollection(
                                                LibraryCollection.Albums
                                            )
                                        }
                                        .heightIn(min = 64.dp)
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(
                                        Icons.Rounded.Album,
                                        null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "收藏专辑",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        when {
                                            data?.albumsLoading == true -> "同步中"
                                            data?.albumsError != null -> "同步失败"
                                            else -> "${data?.albums?.size ?: 0} 张"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Icon(
                                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (state is LibraryUiState.Content && state.warning != null) item(key = "warning") {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(rowPadding),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "部分收藏同步失败",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = onRetry) { Text("重试") }
                            }
                        }
                    }
                }
            }
        }
        MeiHazeTopBar(state = source, title = "音乐库", progress = progress,
                actions = {
                    if (!tablet) {
                        IconButton(onClick = { Screen.LibraryLayout.navigate(LocalNavController.current) }) {
                            Icon(Icons.Rounded.Tune, contentDescription = "音乐库首页布局")
                        }
                    }
                    com.ljyh.mei.ui.screen.profile.PersonalCenterEntry(
                        onOpenProfile,
                        data?.profile?.avatarUrl.takeIf { signedIn },
                    )
                })
    }
}

@Composable
private fun LibrarySectionHeading(
    title: String,
    showAll: Boolean,
    modifier: Modifier = Modifier,
    onShowAll: () -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MeiSectionTitle(title, Modifier.weight(1f))
        if (showAll) MeiShowAllButton(onClick = onShowAll)
    }
}

@Composable
private fun LibraryEmptyLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * 卡片副标题：曲目数 → 播到第几首 → 播放模式，按重要性从左往右排，
 * 宽度不够时截掉的是后面的信息，不会出现「只见作者不见进度」。
 */
private fun bannerSubtitle(
    countLabel: String?,
    indexInList: Int?,
    totalCount: Int,
    mode: PlayModeSnapshot?,
    trailing: String? = null,
): String {
    val index = indexInList?.takeIf { it >= 0 && (totalCount <= 0 || it < totalCount) }
    return listOfNotNull(
        countLabel?.takeIf { it.isNotBlank() },
        index?.let { "第 ${it + 1} 首" },
        mode?.shortLabel(),
        trailing?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
}

private fun LazyListScope.mobileLibraryContent(
    data: LibraryContentUiState,
    likedArtwork: String?,
    cardHeight: Dp,
    pinned: LibraryPinned?,
    resumeByKey: Map<String, QueueResume>,
    modeMemory: Map<String, PlayModeSnapshot>,
    currentQueueKey: String?,
    playingAudio: Boolean,
    preparingKey: String?,
    canPlay: Boolean,
    rowPadding: PaddingValues,
    order: List<LibrarySection>,
    onOpenPlaylist: (String) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenCollection: (LibraryCollection) -> Unit,
    onPlayPlaylist: (String, Boolean) -> Unit,
    onPlayAlbum: (Long, Boolean) -> Unit,
    onTogglePlayback: () -> Unit,
) {
    val created = data.createdPlaylistOverview()
    val collected = data.playlistsIn(LibraryCollection.Collected)
    val albums = data.albums

    // 从未配置过时按智能排序取前 4 个；配置过就严格按用户勾选与顺序，失效 id 自动跳过。
    val createdShown = pickPinned(pinned?.created, created) { it.id }
    val collectedShown = pickPinned(pinned?.collected, collected) { it.id }
    val albumsShown = pickPinned(pinned?.albums, albums) { it.id.toString() }

    order.forEach { section ->
        when (section) {
            LibrarySection.LIKED -> mobileLikedSection(
                data = data,
                likedArtwork = likedArtwork,
                cardHeight = cardHeight,
                resumeByKey = resumeByKey,
                modeMemory = modeMemory,
                currentQueueKey = currentQueueKey,
                playingAudio = playingAudio,
                preparingKey = preparingKey,
                canPlay = canPlay,
                rowPadding = rowPadding,
                onOpenPlaylist = onOpenPlaylist,
                onPlayPlaylist = onPlayPlaylist,
                onTogglePlayback = onTogglePlayback,
            )

            LibrarySection.CREATED -> mobilePlaylistShelf(
                key = "created",
                title = "我创建的歌单",
                entries = createdShown,
                showAll = true,
                cardHeight = cardHeight,
                resumeByKey = resumeByKey,
                modeMemory = modeMemory,
                currentQueueKey = currentQueueKey,
                playingAudio = playingAudio,
                preparingKey = preparingKey,
                canPlay = canPlay,
                rowPadding = rowPadding,
                onOpenCollection = onOpenCollection,
                onOpenPlaylist = onOpenPlaylist,
                onPlayPlaylist = onPlayPlaylist,
                onTogglePlayback = onTogglePlayback,
                collection = LibraryCollection.Created,
            )

            LibrarySection.COLLECTED -> mobilePlaylistShelf(
                key = "collected",
                title = "收藏歌单",
                entries = collectedShown,
                showAll = true,
                cardHeight = cardHeight,
                resumeByKey = resumeByKey,
                modeMemory = modeMemory,
                currentQueueKey = currentQueueKey,
                playingAudio = playingAudio,
                preparingKey = preparingKey,
                canPlay = canPlay,
                rowPadding = rowPadding,
                onOpenCollection = onOpenCollection,
                onOpenPlaylist = onOpenPlaylist,
                onPlayPlaylist = onPlayPlaylist,
                onTogglePlayback = onTogglePlayback,
                collection = LibraryCollection.Collected,
            )

            LibrarySection.ALBUMS -> mobileAlbumShelf(
                entries = albumsShown,
                cardHeight = cardHeight,
                resumeByKey = resumeByKey,
                modeMemory = modeMemory,
                currentQueueKey = currentQueueKey,
                playingAudio = playingAudio,
                preparingKey = preparingKey,
                canPlay = canPlay,
                rowPadding = rowPadding,
                onOpenCollection = onOpenCollection,
                onOpenAlbum = onOpenAlbum,
                onPlayAlbum = onPlayAlbum,
                onTogglePlayback = onTogglePlayback,
            )

            LibrarySection.PODCASTS -> mobilePodcastSection(
                rowPadding = rowPadding,
                cardHeight = cardHeight,
                canPlay = canPlay,
                currentQueueKey = currentQueueKey,
                playingAudio = playingAudio,
                onTogglePlayback = onTogglePlayback,
            )
        }
    }
}

/** 按配置挑出要显示的条目：未配置走默认前 4，配置了就按顺序取，失效 id 跳过。 */
private fun <T> pickPinned(
    pinnedIds: List<String>?,
    all: List<T>,
    idOf: (T) -> String,
): List<T> {
    if (pinnedIds == null) return all.take(DEFAULT_PINNED_COUNT)
    if (pinnedIds.isEmpty()) return emptyList()
    val byId = all.associateBy(idOf)
    return pinnedIds.mapNotNull(byId::get)
}

private fun LazyListScope.mobileLikedSection(
    data: LibraryContentUiState,
    likedArtwork: String?,
    cardHeight: Dp,
    resumeByKey: Map<String, QueueResume>,
    modeMemory: Map<String, PlayModeSnapshot>,
    currentQueueKey: String?,
    playingAudio: Boolean,
    preparingKey: String?,
    canPlay: Boolean,
    rowPadding: PaddingValues,
    onOpenPlaylist: (String) -> Unit,
    onPlayPlaylist: (String, Boolean) -> Unit,
    onTogglePlayback: () -> Unit,
) {
    val liked = data.likedPlaylist()
    val key = liked?.id?.let { "playlist:$it" }
    item(key = "mobile_liked") {
        LibraryBannerCard(
            title = "我喜欢的音乐",
            subtitle = bannerSubtitle(
                countLabel = liked?.let { "${it.count} 首" },
                indexInList = key?.let { resumeByKey[it]?.indexInList },
                totalCount = liked?.count ?: 0,
                mode = key?.let { modeMemory[it] },
            ),
            coverUrl = likedArtwork,
            fallbackIcon = Icons.Rounded.Favorite,
            height = cardHeight,
            isCurrent = key != null && key == currentQueueKey,
            isPlaying = playingAudio,
            preparing = preparingKey != null && preparingKey == key,
            canPlay = canPlay && liked != null && liked.count > 0,
            modifier = Modifier.padding(rowPadding),
            onOpen = { liked?.let { onOpenPlaylist(it.id) } },
            onPlay = { liked?.let { onPlayPlaylist(it.id, false) } },
            onPlayFromStart = { liked?.let { onPlayPlaylist(it.id, true) } },
            onToggle = onTogglePlayback,
        )
    }
}

private fun LazyListScope.mobilePlaylistShelf(
    key: String,
    title: String,
    entries: List<Playlist>,
    showAll: Boolean,
    cardHeight: Dp,
    resumeByKey: Map<String, QueueResume>,
    modeMemory: Map<String, PlayModeSnapshot>,
    currentQueueKey: String?,
    playingAudio: Boolean,
    preparingKey: String?,
    canPlay: Boolean,
    rowPadding: PaddingValues,
    collection: LibraryCollection,
    onOpenCollection: (LibraryCollection) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onPlayPlaylist: (String, Boolean) -> Unit,
    onTogglePlayback: () -> Unit,
) {
    // 勾 0 个等于「不要这块」：标题和卡片一起隐藏，首页不留空壳。
    if (entries.isEmpty()) return
    item(key = "mobile_${key}_heading") {
        LibrarySectionHeading(title, showAll, Modifier.padding(rowPadding)) {
            onOpenCollection(collection)
        }
    }
    items(entries, key = { "mobile_${key}_${it.id}" }) { playlist ->
        val queueKey = "playlist:${playlist.id}"
        LibraryBannerCard(
            title = playlist.title,
            subtitle = bannerSubtitle(
                countLabel = "${playlist.count} 首",
                indexInList = resumeByKey[queueKey]?.indexInList,
                totalCount = playlist.count,
                mode = modeMemory[queueKey],
            ),
            coverUrl = playlist.cover,
            fallbackIcon = Icons.Rounded.LibraryMusic,
            height = cardHeight,
            isCurrent = queueKey == currentQueueKey,
            isPlaying = playingAudio,
            preparing = preparingKey != null && preparingKey == queueKey,
            canPlay = canPlay && playlist.count > 0,
            modifier = Modifier.padding(rowPadding),
            onOpen = { onOpenPlaylist(playlist.id) },
            onPlay = { onPlayPlaylist(playlist.id, false) },
            onPlayFromStart = { onPlayPlaylist(playlist.id, true) },
            onToggle = onTogglePlayback,
        )
    }
}

private fun LazyListScope.mobileAlbumShelf(
    entries: List<Album>,
    cardHeight: Dp,
    resumeByKey: Map<String, QueueResume>,
    modeMemory: Map<String, PlayModeSnapshot>,
    currentQueueKey: String?,
    playingAudio: Boolean,
    preparingKey: String?,
    canPlay: Boolean,
    rowPadding: PaddingValues,
    onOpenCollection: (LibraryCollection) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onPlayAlbum: (Long, Boolean) -> Unit,
    onTogglePlayback: () -> Unit,
) {
    if (entries.isEmpty()) return
    item(key = "mobile_albums_heading") {
        LibrarySectionHeading("收藏专辑", true, Modifier.padding(rowPadding)) {
            onOpenCollection(LibraryCollection.Albums)
        }
    }
    items(entries, key = { "mobile_album_${it.id}" }) { album ->
        val queueKey = "album:${album.id}"
        LibraryBannerCard(
            title = album.title,
            subtitle = bannerSubtitle(
                countLabel = null,
                indexInList = resumeByKey[queueKey]?.indexInList,
                totalCount = 0,
                mode = modeMemory[queueKey],
                trailing = album.artist.joinToString(" / ") { it.name },
            ),
            coverUrl = album.cover,
            fallbackIcon = Icons.Rounded.Album,
            height = cardHeight,
            isCurrent = queueKey == currentQueueKey,
            isPlaying = playingAudio,
            preparing = preparingKey != null && preparingKey == queueKey,
            canPlay = canPlay,
            modifier = Modifier.padding(rowPadding),
            onOpen = { onOpenAlbum(album.id) },
            onPlay = { onPlayAlbum(album.id, false) },
            onPlayFromStart = { onPlayAlbum(album.id, true) },
            onToggle = onTogglePlayback,
        )
    }
}

/**
 * 「我关注的播客」：最近收听的那一集。点卡进节目单，点播放键定位到那一集并跳进集内进度。
 */
private fun LazyListScope.mobilePodcastSection(
    rowPadding: PaddingValues,
    cardHeight: Dp,
    canPlay: Boolean,
    currentQueueKey: String?,
    playingAudio: Boolean,
    onTogglePlayback: () -> Unit,
) {
    item(key = "mobile_podcast_heading") {
        val nav = LocalNavController.current
        LibrarySectionHeading(
            "我关注的播客",
            true,
            Modifier.padding(rowPadding),
        ) { Screen.Podcasts.navigate(nav) }
    }
    item(key = "mobile_podcast_card") {
        val nav = LocalNavController.current
        val connection = LocalPlayerConnection.current
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val playback: PodcastPlaybackViewModel = hiltViewModel()
        val raw by rememberPreference(RecentPodcastKey, "")
        val recent = remember(raw) { RecentPodcastCodec.decode(raw) }
        val progressBySong by playback.progressBySong.collectAsStateWithLifecycle()
        val preparingId by playback.preparingId.collectAsStateWithLifecycle()
        val recentProgress: PlaybackProgress? = recent?.songId?.toString()?.let { progressBySong[it] }
        // 播客记忆的是「哪一集 + 集内秒数」，副标题直接给集内进度比「第几集」更有用。
        val queueKey = recent?.radioId?.let { "podcast:$it" }
        val preparing = preparingId != null

        // 用 lambda 而不是局部 fun：局部函数在 compose 作用域里会被当成 composable，
        // 就没法塞进 LibraryBannerCard 的普通回调里了。
        val start: (Boolean) -> Unit = { fromStart ->
            val episode = recent
            if (episode != null && !preparing) {
                scope.launch {
                    val queue = playback.buildQueue(episode, fromMemory = !fromStart)
                    val target = connection
                    if (target == null) return@launch
                    if (queue != null) {
                        target.playQueue(queue, startInShuffleMode = false)
                    } else {
                        // 节目表拉不到时只播这一集：至少能从这一集恢复，而不是整块失效。
                        target.playQueue(episode.asSingleQueue(0L), startInShuffleMode = false)
                        Toast.makeText(context, "节目表加载失败，先播放这一集", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        LibraryBannerCard(
            title = recent?.radioName?.takeIf { it.isNotBlank() } ?: "我关注的播客",
            subtitle = podcastSubtitle(recent, recentProgress),
            coverUrl = recent?.coverUrl,
            fallbackIcon = Icons.Rounded.Mic,
            height = cardHeight,
            isCurrent = queueKey != null && queueKey == currentQueueKey,
            isPlaying = playingAudio,
            preparing = preparing,
            canPlay = canPlay && recent != null,
            modifier = Modifier.padding(rowPadding),
            onOpen = {
                recent?.let { Screen.PodcastDetail.navigate(nav) { addPath(it.radioId.toString()) } }
                    ?: Screen.Podcasts.navigate(nav)
            },
            onPlay = { start(false) },
            onPlayFromStart = { start(true) },
            onToggle = onTogglePlayback,
        )
    }
}

/** 节目表拉不到时的兜底：只播这一集，位置交给调用方决定。 */
private fun RecentPodcastEpisode.asSingleQueue(positionMs: Long): ListQueue {
    val meta = MediaMetadata(
        id = songId,
        title = episodeTitle,
        coverUrl = coverUrl,
        artists = listOf(MediaMetadata.Artist(0L, artist.ifBlank { "网易云播客" })),
        duration = durationMs,
        album = MediaMetadata.Album(id = radioId, title = radioName),
        isPodcast = true,
        neteaseId = songId,
    )
    return ListQueue(
        id = "podcast_$radioId",
        title = radioName.ifBlank { "播客" },
        items = listOf(songId.toString() to meta.toMediaItem()),
        startIndex = 0,
        position = positionMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
        source = QueueSource(QueueSource.Type.PODCAST, radioId.toString(), radioName),
    )
}

private fun podcastSubtitle(recent: RecentPodcastEpisode?, progress: PlaybackProgress?): String {
    if (recent == null) return "还没有收听记录，去挑一部评书"
    val total = progress?.durationMs?.takeIf { it > 0L } ?: recent.durationMs
    if (progress?.completed == true) {
        return if (total > 0L) "已听完 · ${formatShort(total)}" else "已听完"
    }
    val listened = progress?.positionMs ?: recent.positionMs
    return buildString {
        append("已听 ${formatShort(listened)}")
        if (total > 0L) append(" / ${formatShort(total)}")
    }
}

private fun formatShort(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0L)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val rest = seconds % 60
    return if (hours > 0) {
        String.format(java.util.Locale.getDefault(), "%d:%02d:%02d", hours, minutes, rest)
    } else {
        String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, rest)
    }
}
