package com.ljyh.mei.ui.screen.podcast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ljyh.mei.data.model.domain.RecentPodcastEpisode
import com.ljyh.mei.data.model.domain.toMediaItem
import com.ljyh.mei.data.model.domain.toMediaMetadata
import com.ljyh.mei.data.model.podcast.DjProgram
import com.ljyh.mei.data.model.room.PlaybackProgress
import com.ljyh.mei.data.network.Resource
import com.ljyh.mei.data.repository.PodcastRepository
import com.ljyh.mei.di.repository.PlaybackProgressPolicy
import com.ljyh.mei.di.repository.PlaybackProgressRepository
import com.ljyh.mei.playback.queue.ListQueue
import com.ljyh.mei.playback.queue.QueueSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * 「不用先进播客页也能把整张节目表排进队列」。
 *
 * 音乐库卡片上的播放键以前只塞了单集，点完之后队列里就只剩这一首，
 * 上一集/下一集全都不见了。这里在后台把整张节目表拉下来再入队并定位到该集，
 * 网络失败才回落到单集播放。
 */
@HiltViewModel
class PodcastPlaybackViewModel @Inject constructor(
    private val repository: PodcastRepository,
    private val progressRepository: PlaybackProgressRepository,
) : ViewModel() {

    /** 正在准备中的单集 songId，界面据此在播放键上转圈。 */
    private val _preparingId = MutableStateFlow<String?>(null)
    val preparingId: StateFlow<String?> = _preparingId.asStateFlow()

    /** 卡片上的「已听」直接读本地进度表，不再依赖偏好里那份副本。 */
    val progressBySong: StateFlow<Map<String, PlaybackProgress>> = progressRepository.observeBySong()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyMap())

    /**
     * 拉整张节目表并构造以该集为起点的队列；拉不到（网络失败 / 节目已下架）返回 null。
     *
     * 起点位置直接取本地进度表并写进队列：交给 setMediaItems 的 startPosition，
     * 播放器第一帧就落在上次听到的位置，不依赖切歌回调里那次 seek。
     */
    suspend fun buildQueue(episode: RecentPodcastEpisode): ListQueue? {
        val songId = episode.songId.toString()
        if (_preparingId.value != null) return null
        _preparingId.value = songId
        return try {
            val startMs = resumePositionOf(songId).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            val programs = loadPrograms(episode.radioId)
            val pairs = programs.mapNotNull { program ->
                program.toMediaMetadata()?.let { meta -> meta.id.toString() to meta.toMediaItem() }
            }
            if (pairs.isEmpty()) return null
            val title = episode.radioName.ifBlank { "播客" }
            ListQueue(
                id = "podcast_${episode.radioId}",
                title = title,
                items = pairs,
                position = startMs,
                source = QueueSource(QueueSource.Type.PODCAST, episode.radioId.toString(), title),
            ).startingAt(songId, startMs)
        } finally {
            _preparingId.value = null
        }
    }

    /** 这一集该从哪继续；听完的、听太短的返回 0。 */
    suspend fun resumePositionOf(songId: String): Long {
        val saved = try {
            progressRepository.progressOf(songId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return PlaybackProgressPolicy.resumePosition(saved) ?: 0L
    }

    private suspend fun loadPrograms(radioId: Long): List<DjProgram> {
        val collected = LinkedHashMap<String, DjProgram>()
        var offset = 0
        var pageIndex = 0
        while (pageIndex < MAX_PAGES) {
            when (val result = repository.programs(radioId, PAGE_SIZE, offset, asc = true)) {
                is Resource.Success -> {
                    val page = result.data.programs
                    page.forEach { program ->
                        val key = program.mainSong?.id?.takeIf { it > 0 }?.toString()
                            ?: "program_${program.id}"
                        collected[key] = program
                    }
                    if (!result.data.more || page.isEmpty()) break
                    offset += page.size
                    pageIndex++
                    delay(PAGE_INTERVAL_MS)
                }

                // 只拿到一部分也先用着：能连续播几集好过回落到单集。
                is Resource.Error -> break

                Resource.Loading -> break
            }
        }
        return repository.playable(collected.values.toList())
    }

    private companion object {
        const val PAGE_SIZE = 500
        const val MAX_PAGES = 3
        const val PAGE_INTERVAL_MS = 400L
    }
}
