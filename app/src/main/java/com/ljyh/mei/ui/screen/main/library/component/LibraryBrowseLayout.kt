package com.ljyh.mei.ui.screen.main.library.component

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ljyh.mei.constants.RecentPodcastKey
import com.ljyh.mei.data.model.domain.MediaMetadata
import com.ljyh.mei.data.model.domain.RecentPodcastCodec
import com.ljyh.mei.data.model.domain.RecentPodcastEpisode
import com.ljyh.mei.data.model.domain.toMediaItem
import com.ljyh.mei.playback.queue.ListQueue
import com.ljyh.mei.playback.queue.QueueSource
import com.ljyh.mei.ui.component.MeiSectionTitle
import com.ljyh.mei.ui.component.MeiShowAllButton
import com.ljyh.mei.ui.component.MeiStatePanel
import com.ljyh.mei.ui.component.card.ArtworkCard
import com.ljyh.mei.ui.component.card.VinylAlbumCard
import com.ljyh.mei.ui.component.artwork.VinylAlbumArtworkRatio
import com.ljyh.mei.ui.component.glass.*
import com.ljyh.mei.ui.component.layout.LocalPageStartInset
import com.ljyh.mei.ui.component.layout.meiPageGutter
import com.ljyh.mei.ui.component.layout.meiPageHorizontalPadding
import com.ljyh.mei.ui.component.utils.rememberDeviceInfo
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.local.LocalPlayerConnection
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
    canPlay: Boolean,
    onPlayLiked: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenCollection: (LibraryCollection) -> Unit,
    onLogin: () -> Unit,
    onRetry: () -> Unit,
    onOpenProfile: () -> Unit = {},
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
    var showLayoutEditor by remember { mutableStateOf(false) }
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
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
                                data,
                                likedArtwork,
                                likedSongTitle,
                                mobileColumns,
                                if (compactWide) 128.dp else 190.dp,
                                preparingPlayback,
                                canPlay,
                                rowPadding,
                                sectionOrder,
                                onOpenPlaylist,
                                onPlayLiked,
                                onOpenAlbum,
                                onOpenCollection
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
                        mobilePodcastSection(rowPadding, 190.dp, canPlay)
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
                        IconButton(onClick = { showLayoutEditor = true }) {
                            Icon(Icons.Rounded.SwapVert, contentDescription = "调整音乐库顺序")
                        }
                    }
                    com.ljyh.mei.ui.screen.profile.PersonalCenterEntry(
                        onOpenProfile,
                        data?.profile?.avatarUrl.takeIf { signedIn },
                    )
                })
    }

    if (showLayoutEditor) {
        LibraryLayoutDialog(
            order = sectionOrder,
            onDismiss = { showLayoutEditor = false },
        )
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


private fun androidx.compose.foundation.lazy.LazyListScope.mobileLibraryContent(
    data: LibraryContentUiState,
    likedArtwork: String?,
    likedSongTitle: String?,
    columns: Int,
    likedHeight: Dp,
    preparingPlayback: Boolean,
    canPlay: Boolean,
    rowPadding: PaddingValues,
    order: List<LibrarySection>,
    onOpenPlaylist: (String) -> Unit,
    onPlayLiked: (String) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenCollection: (LibraryCollection) -> Unit,
) {
    // 区块顺序由「调整音乐库顺序」决定，这里只按顺序把它们拼出来。
    order.forEach { section ->
        when (section) {
            LibrarySection.LIKED -> mobileLikedSection(
                data, likedArtwork, likedSongTitle, likedHeight,
                preparingPlayback, canPlay, rowPadding, onOpenPlaylist, onPlayLiked,
            )

            LibrarySection.CREATED -> mobileCreatedSection(
                data, columns, rowPadding, onOpenPlaylist, onOpenCollection,
            )

            LibrarySection.COLLECTED -> mobileCollectedSection(
                data, columns, rowPadding, onOpenPlaylist, onOpenCollection,
            )

            LibrarySection.ALBUMS -> mobileAlbumsSection(
                data, rowPadding, onOpenAlbum, onOpenCollection,
            )

            LibrarySection.PODCASTS -> mobilePodcastSection(
                rowPadding, likedHeight, canPlay,
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.mobileLikedSection(
    data: LibraryContentUiState,
    likedArtwork: String?,
    likedSongTitle: String?,
    likedHeight: Dp,
    preparingPlayback: Boolean,
    canPlay: Boolean,
    rowPadding: PaddingValues,
    onOpenPlaylist: (String) -> Unit,
    onPlayLiked: (String) -> Unit,
) {
    val liked = data.likedPlaylist()
    item(key = "mobile_liked") {
        LibraryLikedPlaylist(
            liked, likedArtwork, preparingPlayback, canPlay,
            modifier = Modifier
                .fillMaxWidth()
                .padding(rowPadding)
                .height(likedHeight),
            onOpen = { liked?.let { onOpenPlaylist(it.id) } },
            onPlay = { liked?.let { onPlayLiked(it.id) } },
            songTitle = likedSongTitle, playAtBottom = true
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.mobileCreatedSection(
    data: LibraryContentUiState,
    columns: Int,
    rowPadding: PaddingValues,
    onOpenPlaylist: (String) -> Unit,
    onOpenCollection: (LibraryCollection) -> Unit,
) {
    val created = data.createdPlaylistOverview()
    item(key = "mobile_created_heading") {
        LibrarySectionHeading(
            "我创建的歌单",
            true,
            Modifier
                .padding(rowPadding)
                .padding(top = 8.dp)
        ) {
            onOpenCollection(LibraryCollection.Created)
        }
    }
    if (created.isEmpty()) item(key = "mobile_created_empty") {
        LibraryEmptyLabel(
            if (data.playlistsLoading) "同步中" else if (data.playlistsError != null) "暂时无法同步歌单" else "还没有创建歌单",
            Modifier.padding(rowPadding)
        )
    }
    items(created.take(4).chunked(columns), key = { "mobile_created_${it.first().id}" }) { row ->
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
                ) {
                    onOpenPlaylist(playlist.id)
                }
            }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.mobileCollectedSection(
    data: LibraryContentUiState,
    columns: Int,
    rowPadding: PaddingValues,
    onOpenPlaylist: (String) -> Unit,
    onOpenCollection: (LibraryCollection) -> Unit,
) {
    val collected = data.playlistsIn(LibraryCollection.Collected)
    item(key = "mobile_collected_heading") {
        LibrarySectionHeading(
            "收藏歌单",
            true,
            Modifier
                .padding(rowPadding)
                .padding(top = 8.dp)
        ) {
            onOpenCollection(LibraryCollection.Collected)
        }
    }
    if (collected.isEmpty()) item(key = "mobile_collected_empty") {
        LibraryEmptyLabel(
            if (data.playlistsLoading) "同步中" else if (data.playlistsError != null) "暂时无法同步歌单" else "还没有收藏歌单",
            Modifier.padding(rowPadding)
        )
    }
    items(collected.take(4).chunked(columns), key = { "mobile_collected_${it.first().id}" }) { row ->
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
                    subtitle = playlist.authorName,
                ) {
                    onOpenPlaylist(playlist.id)
                }
            }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.mobileAlbumsSection(
    data: LibraryContentUiState,
    rowPadding: PaddingValues,
    onOpenAlbum: (Long) -> Unit,
    onOpenCollection: (LibraryCollection) -> Unit,
) {
    item(key = "mobile_albums_heading") {
        LibrarySectionHeading("收藏专辑", true, Modifier
            .padding(rowPadding)
            .padding(top = 8.dp)) {
            onOpenCollection(LibraryCollection.Albums)
        }
    }
    if (data.albums.isEmpty()) item(key = "mobile_albums_empty") {
        LibraryEmptyLabel(
            if (data.albumsLoading) "同步中" else if (data.albumsError != null) "暂时无法同步专辑" else "还没有收藏专辑",
            Modifier.padding(rowPadding)
        )
    } else item(key = "mobile_albums_shelf") {
        LazyRow(contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(data.albums, key = { it.id }) { album ->
                VinylAlbumCard(
                    title = album.title, coverUrl = album.cover,
                    modifier = Modifier.width(144.dp * VinylAlbumArtworkRatio),
                    subtitle = album.artist.joinToString(" / ") { it.name },
                ) { onOpenAlbum(album.id) }
            }
        }
    }
}

/**
 * 「我关注的播客」：标题 + 通栏卡片，结构对齐「我创建的歌单」。
 *
 * 卡片主体是最近收听的那一集：点卡进电台节目单，点右下播放键直接接着上次的进度播，
 * 不用再走「音乐库 → 我关注的播客 → 找电台 → 下滑找集数」这条路。
 */
private fun androidx.compose.foundation.lazy.LazyListScope.mobilePodcastSection(
    rowPadding: PaddingValues,
    cardHeight: Dp,
    canPlay: Boolean,
) {
    item(key = "mobile_podcast_heading") {
        val nav = LocalNavController.current
        LibrarySectionHeading(
            "我关注的播客",
            true,
            Modifier
                .padding(rowPadding)
                .padding(top = 8.dp)
        ) { Screen.Podcasts.navigate(nav) }
    }
    item(key = "mobile_podcast_card") {
        val nav = LocalNavController.current
        val playerConnection = LocalPlayerConnection.current
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        // 播放键要先把整张节目表拉下来才能排成队列，所以走播客侧的视图模型，
        // 顺便把本地进度表读进来，卡片上的「已听」不再依赖偏好里那份副本。
        val playback: PodcastPlaybackViewModel = hiltViewModel()
        val raw by rememberPreference(RecentPodcastKey, "")
        val recent = remember(raw) { RecentPodcastCodec.decode(raw) }
        val progressBySong by playback.progressBySong.collectAsStateWithLifecycle()
        val preparingId by playback.preparingId.collectAsStateWithLifecycle()
        val songId = recent?.songId?.toString()
        val playing = preparingId != null && preparingId == songId
        LibraryPodcastCard(
            recent = recent,
            progress = songId?.let { progressBySong[it] },
            playing = playing,
            height = cardHeight,
            modifier = Modifier.padding(rowPadding),
            canPlay = canPlay,
            onOpen = {
                recent?.let {
                    Screen.PodcastDetail.navigate(nav) { addPath(it.radioId.toString()) }
                }
            },
            onPlay = {
                val episode = recent
                if (episode != null && !playing) {
                    scope.launch {
                        // 用整张节目表入队并定位到这一集，才能继续上一集/下一集；
                        // 拉不到时才回落到「只播这一集」，并明确告诉用户原因。
                        val queue = playback.buildQueue(episode)
                        val connection = playerConnection ?: return@launch
                        if (queue != null) {
                            connection.playQueue(queue, startInShuffleMode = false)
                        } else {
                            connection.playQueue(episode.asSingleQueue(), startInShuffleMode = false)
                            Toast.makeText(context, "节目表加载失败，先播放这一集", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onBrowse = { Screen.Podcasts.navigate(nav) },
        )
    }
}

/** 进度表拉不到时的兜底：只播这一集。 */
private fun RecentPodcastEpisode.asSingleQueue(): ListQueue {
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
        source = QueueSource(QueueSource.Type.PODCAST, radioId.toString(), radioName),
    )
}
