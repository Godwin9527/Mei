package com.ljyh.mei.ui.screen.main.library

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ljyh.mei.constants.LibraryCardHeightKey
import com.ljyh.mei.constants.LibraryPinnedKey
import com.ljyh.mei.constants.LibrarySectionOrderKey
import com.ljyh.mei.data.network.Resource
import com.ljyh.mei.data.repository.UserRepository
import com.ljyh.mei.di.repository.LocalPlaylistRepository
import com.ljyh.mei.playback.PlayModeMemoryStore
import com.ljyh.mei.ui.model.Album
import com.ljyh.mei.ui.model.toAlbum
import com.ljyh.mei.utils.preferences.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 「音乐库首页布局」设置页的数据。
 *
 * 歌单直接读本地库（首页同步时已写入），不依赖首页那套视图模型的会话状态；
 * 收藏专辑只有网络接口，拉失败就留空，不影响其余设置项。
 */
@HiltViewModel
class LibraryLayoutViewModel @Inject constructor(
    localPlaylist: LocalPlaylistRepository,
    private val user: UserRepository,
    private val playModeStore: PlayModeMemoryStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val playlists: StateFlow<List<com.ljyh.mei.data.model.room.Playlist>> =
        localPlaylist.getAllPlaylist().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList(),
        )

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums

    init {
        viewModelScope.launch {
            try {
                val result = user.getAlbumList()
                if (result is Resource.Success) {
                    _albums.value = result.data.data.map { it.toAlbum() }
                }
            } catch (e: Exception) {
                Timber.tag("LibraryLayout").e(e, "load albums failed")
            }
        }
    }

    fun saveSectionOrder(raw: String) {
        viewModelScope.launch {
            try {
                context.dataStore.edit { it[LibrarySectionOrderKey] = raw }
            } catch (e: Exception) {
                Timber.tag("LibraryLayout").e(e, "save section order failed")
            }
        }
    }

    /** 回到「未配置」状态：区块顺序恢复默认，显示项交回默认前 4，卡片高度回到 148。 */
    fun resetLayout() {
        viewModelScope.launch {
            try {
                context.dataStore.edit { preferences ->
                    preferences.remove(LibrarySectionOrderKey)
                    preferences.remove(LibraryPinnedKey)
                    preferences.remove(LibraryCardHeightKey)
                }
            } catch (e: Exception) {
                Timber.tag("LibraryLayout").e(e, "reset layout failed")
            }
        }
    }

    fun clearPlayModeMemory() {
        viewModelScope.launch { playModeStore.clearAll() }
    }
}
