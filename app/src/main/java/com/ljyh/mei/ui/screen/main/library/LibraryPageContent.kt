package com.ljyh.mei.ui.screen.main.library

import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import com.ljyh.mei.data.model.room.QueueResume
import com.ljyh.mei.ui.component.layout.LocalPhoneLandscape
import com.ljyh.mei.ui.screen.main.library.component.AdaptiveLibraryContent
import com.ljyh.mei.ui.screen.main.library.component.LibraryBrowseLayout

internal data class LibraryPageState(
    val state: LibraryUiState,
    val likedArtwork: String?,
    val likedSongTitle: String?,
    val signedIn: Boolean,
    val scrollBehavior: TopAppBarScrollBehavior,
    val preparingPlayback: Boolean,
    val canPlay: Boolean,
    val likedArtworks: List<String> = listOfNotNull(likedArtwork),
    val likedArtworkLoading: Boolean = false,
    /** 正在准备播放的集合键（`playlist:123` 等），对应卡片上的转圈。 */
    val preparingKey: String? = null,
    /** 各集合上次播到哪一条，卡片副标题据此写「第 8 首」。 */
    val resumeByKey: Map<String, QueueResume> = emptyMap(),
)

internal data class LibraryPageActions(
    val onPlayLiked: (String) -> Unit,
    val onOpenPlaylist: (String) -> Unit,
    val onOpenAlbum: (Long) -> Unit,
    val onOpenCollection: (LibraryCollection) -> Unit,
    val onLogin: () -> Unit,
    val onRetry: () -> Unit,
    val onOpenProfile: () -> Unit = {},
    /** [fromStart] 为 true 表示长按播放键：忽略记忆，从第一首开始。 */
    val onPlayPlaylist: (String, Boolean) -> Unit = { id, _ -> onPlayLiked(id) },
    val onPlayAlbum: (Long, Boolean) -> Unit = { _, _ -> },
    /** 当前队列就是这个卡片时的播放/暂停切换，不重建队列。 */
    val onTogglePlayback: () -> Unit = {},
)

@Composable
internal fun LibraryPageContent(state: LibraryPageState, actions: LibraryPageActions) {
    if (LocalPhoneLandscape.current && state.signedIn) AdaptiveLibraryContent(state, actions)
    else LibrarySinglePaneContent(state, actions)
}

@Composable
internal fun LibrarySinglePaneContent(state: LibraryPageState, actions: LibraryPageActions) {
    LibraryBrowseLayout(
        state = state.state,
        likedArtwork = state.likedArtwork,
        likedSongTitle = state.likedSongTitle,
        signedIn = state.signedIn,
        scrollBehavior = state.scrollBehavior,
        preparingPlayback = state.preparingPlayback,
        preparingKey = state.preparingKey,
        canPlay = state.canPlay,
        resumeByKey = state.resumeByKey,
        onPlayLiked = actions.onPlayLiked,
        onOpenPlaylist = actions.onOpenPlaylist,
        onOpenAlbum = actions.onOpenAlbum,
        onOpenCollection = actions.onOpenCollection,
        onLogin = actions.onLogin,
        onRetry = actions.onRetry,
        onOpenProfile = actions.onOpenProfile,
        onPlayPlaylist = actions.onPlayPlaylist,
        onPlayAlbum = actions.onPlayAlbum,
        onTogglePlayback = actions.onTogglePlayback,
    )
}
