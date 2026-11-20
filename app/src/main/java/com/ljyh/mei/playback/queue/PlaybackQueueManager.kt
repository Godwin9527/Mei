package com.ljyh.mei.playback.queue

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder
import com.ljyh.mei.data.model.domain.MediaMetadata
import com.ljyh.mei.data.model.domain.PLACEHOLDER_URI
import com.ljyh.mei.data.model.domain.createPlaceholder
import com.ljyh.mei.data.model.domain.toMediaItem
import com.ljyh.mei.data.model.domain.toMediaMetadata
import com.ljyh.mei.data.network.api.ApiService
import com.ljyh.mei.data.network.api.WeApiService
import com.ljyh.mei.playback.queue.Queue
import com.ljyh.mei.playback.transition.TransitionDeckPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** 播客节目表一次取多少条；评书类电台常见上百集。 */
private const val PODCAST_PROGRAM_LIMIT = 500

/** 同一电台补全失败后，这段时间内不再重试，避免滚动时反复打接口。 */
private const val PODCAST_RETRY_COOLDOWN_MS = 60L * 60_000L

internal fun prioritizeInsertedShuffleItems(
    order: List<Int>,
    currentIndex: Int,
    insertIndex: Int,
    count: Int,
): List<Int> {
    val nextOrder = order.filterNot { it in insertIndex until insertIndex + count }.toMutableList()
    val afterCurrent = nextOrder.indexOf(currentIndex).takeIf { it >= 0 }?.plus(1) ?: 0
    nextOrder.addAll(afterCurrent, (insertIndex until insertIndex + count).toList())
    return nextOrder
}

class PlaybackQueueManager(
    private val player: Player,
    private val apiService: ApiService,
    private val weApiService: WeApiService,
    private val scope: CoroutineScope
) : Player.Listener {

    private val TAG = "QueueManager"
    private val _queueState = MutableStateFlow<QueueState>(QueueState.Idle)
    private val loadingIds = ConcurrentHashMap.newKeySet<String>()

    /** 电台 id → (可播 id → 节目元数据)，只用节目表补一回。 */
    private val podcastProgramCache = ConcurrentHashMap<Long, Map<String, MediaMetadata>>()
    private val podcastFetchFailedAt = ConcurrentHashMap<Long, Long>()

    private val _isShuffleModeEnabled = MutableStateFlow(false)
    var isFmMode = false
    private var isFetchingFm = false


    init {
        player.addListener(this)
    }

    fun startFmMode(seedItem: MediaItem) {
        scope.launch(Dispatchers.Main) {
            isFmMode = true
            _queueState.value = QueueState.Loading("私人 FM")

            // 1. 准备播放器环境
            player.stop()
            player.clearMediaItems()
            player.shuffleModeEnabled = false
            _isShuffleModeEnabled.value = false
            player.repeatMode = Player.REPEAT_MODE_ALL

            // 2. 立即添加种子歌曲并播放 (保证 UI 立即显示正确的封面和标题)
            player.addMediaItem(seedItem.withQueueEntry(QueueSource(QueueSource.Type.RADIO)))
            player.prepare()
            player.play()

            // 3. 异步获取 FM 推荐列表并追加
            // 注意：这里传 seedItem.mediaId 是为了告诉 API 排除掉当前这一首（取决于 API 支持）
            fetchAndAppendFmRecommendations(seedItem.mediaId)
        }
    }

    fun startFmModeById(seedId: String?) {
        scope.launch(Dispatchers.Main) {
            if (seedId == null) {
                isFmMode = true
                fetchAndAppendFmRecommendations()
            } else {
                // 先加载这首歌的详情，再调用上面的方法
                val details = loadSongDetails(listOf(seedId))
                if (details.isNotEmpty()) {
                    startFmMode(details.first())
                } else {
                    // 如果加载失败，降级处理：直接进入普通 FM
                    isFmMode = true
                    fetchAndAppendFmRecommendations()
                }
            }

        }
    }

    suspend fun fetchAndAppendFmRecommendations(currentId: String? = null) {
        if (isFetchingFm) return
        isFetchingFm = true
        try {
            // 调用 FM 接口
            val response = weApiService.getRadio(mapOf())
            if (response.code == 200 && response.data.isNotEmpty()) {

                // 过滤掉当前正在播放的 ID，防止重复
                val items = response.data
                    .filter { it.id.toString() != currentId }
                    .map { it.toMediaMetadata().toMediaItem().withQueueEntry(QueueSource(QueueSource.Type.RADIO)) }

                withContext(Dispatchers.Main) {
                    // 追加到列表末尾
                    player.addMediaItems(items)

                    _queueState.value = QueueState.Playing("私人 FM", player.mediaItemCount)

                    // 触发占位符检查（如果你 FM 接口返回的是简略信息需要补全详情的话）
                    checkAndLoadMetadata()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "FM Fetch Error", e)
        } finally {
            isFetchingFm = false
        }
    }


    fun fmTrashCurrent() {
        if (!isFmMode) return

        val currentIndex = player.currentMediaItemIndex
        val currentId = player.currentMediaItem?.mediaId ?: return

        scope.launch {
            try {
                // 1. 调用云音乐的“不喜欢”接口 (建议在 apiService 中实现)
                // apiService.dislikeSong(currentId)

                withContext(Dispatchers.Main) {
                    // 2. 移除当前项，ExoPlayer 会自动切换到下一首
                    player.removeMediaItem(currentIndex)

                    // 3. 如果移除后快没了，赶紧补充
                    if (player.mediaItemCount - player.currentMediaItemIndex <= 2) {
                        fetchAndAppendFmRecommendations()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Trash failed", e)
            }
        }
    }

    @OptIn(UnstableApi::class)
    fun playQueue(
        queue: Queue,
        startInShuffleMode: Boolean = false,
        playWhenReady: Boolean = true,
    ) {
        isFmMode = false
        scope.launch(Dispatchers.Main) {
            try {
                _queueState.value = QueueState.Loading(queue.title ?: "加载中")

                val status = queue.getInitialStatus()
                val allIds = status.ids

                if (allIds.isEmpty()) {
                    _queueState.value = QueueState.Empty
                    return@launch
                }

                val mediaItems = allIds.map { item ->
                    (item.second ?: createPlaceholder(item.first)).withQueueEntry(queue.source)
                }

                // 强制先关闭随机模式 必须先关掉，才能保证 setMediaItems 里的 index 是线性的、准确的
                player.shuffleModeEnabled = false
                _isShuffleModeEnabled.value = false

                // setMediaItems replaces the playlist atomically. Clearing first publishes an
                // empty timeline to MediaSession/UI and races with the new queue transition.
                player.setMediaItems(
                    mediaItems,
                    status.mediaItemIndex,
                    status.position.toLong()
                )

                player.repeatMode = Player.REPEAT_MODE_ALL
                player.prepare()

                // 如果需要，再开启随机 此时当前播放的歌曲已经定下来了，ExoPlayer 只会打乱"后面"的歌
                if (startInShuffleMode) {
                    player.shuffleModeEnabled = true
                    _isShuffleModeEnabled.value = true
                }

                player.playWhenReady = playWhenReady

                _queueState.value = QueueState.Playing(queue.title ?: "播放列表", allIds.size)

                // 6. 触发懒加载
                // 因为当前歌曲已经是 RealItem 了，这个方法会自动跳过当前歌曲，去加载下一首
                checkAndLoadMetadata()

            } catch (e: Exception) {
                Log.e(TAG, "playQueue Error", e)
                _queueState.value = QueueState.Error(e.message ?: "播放错误")
            }
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_IDLE && player.playerError != null) {
            _queueState.value = QueueState.Error(player.playerError?.message ?: "播放错误")
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (mediaItem == null) return

        // 基础元数据加载逻辑
        checkAndLoadMetadata()

        // --- 新增 FM 边界检查 ---
        if (isFmMode) {
            val currentIndex = player.currentMediaItemIndex
            val totalCount = player.mediaItemCount

            // 当播放到倒数第 2 首时，自动拉取新歌
            if (totalCount - currentIndex <= 2) {
                scope.launch {
                    fetchAndAppendFmRecommendations()
                }
            }

            // 可选：清理历史，防止内存溢出（保留当前和上一首，删除更早的）
            if (currentIndex > 5) {
                player.removeMediaItems(0, currentIndex - 1)
            }
        }
    }

    fun setShuffleModeEnabled(enabled: Boolean) {
        if (isFmMode && enabled) {
            Log.w(TAG, "Shuffle is not allowed in FM mode")
            return
        }
        scope.launch(Dispatchers.Main) {
            _isShuffleModeEnabled.value = enabled
            player.shuffleModeEnabled = enabled
        }
    }


    private fun checkAndLoadMetadata(windowSize: Int = 3) {
        scope.launch(Dispatchers.Main) {
            // 基础状态检查 确保线程存活且列表不为空
            if (!player.applicationLooper.thread.isAlive || player.mediaItemCount == 0) return@launch

            val timeline = player.currentTimeline
            if (timeline.isEmpty) return@launch

            val currentIndex = player.currentMediaItemIndex
            val indicesToCheck = mutableSetOf<Int>()
            indicesToCheck.add(currentIndex)

            // 计算窗口索引 必须在获取到 INDEX_UNSET 时立即 break，不能把 -1 传给下一次调用

            // 向后找 (Next)
            var next = currentIndex
            for (i in 0 until windowSize) {
                // 如果当前已经是最后一个，next 会变成 INDEX_UNSET (-1)
                if (next == C.INDEX_UNSET) break

                next =
                    timeline.getNextWindowIndex(next, player.repeatMode, player.shuffleModeEnabled)
                if (next != C.INDEX_UNSET) {
                    indicesToCheck.add(next)
                }
            }

            // 向前找 (Previous)
            var prev = currentIndex
            for (i in 0 until windowSize) {
                // 如果当前已经是第一个，prev 会变成 INDEX_UNSET (-1)
                if (prev == C.INDEX_UNSET) break

                prev = timeline.getPreviousWindowIndex(
                    prev,
                    player.repeatMode,
                    player.shuffleModeEnabled
                )
                if (prev != C.INDEX_UNSET) {
                    indicesToCheck.add(prev)
                }
            }

            loadMetadataAtIndices(indicesToCheck)
        }
    }

    /** Resolve placeholders around the visible queue rows as the user scrolls. */
    fun loadMetadataForQueueRange(firstIndex: Int, lastIndex: Int) {
        scope.launch(Dispatchers.Main) {
            val end = lastIndex.coerceAtMost(player.mediaItemCount - 1)
            val start = firstIndex.coerceAtLeast(0)
            if (start <= end) loadMetadataAtIndices(start..end)
        }
    }

    private suspend fun loadMetadataAtIndices(indices: Iterable<Int>) {
        val indicesToCheck = indices.filter { it >= 0 && it < player.mediaItemCount }.toList()
        // 播客队列按电台归类：这些 id 在 /song/detail 里查不到标题（只有时长），
        // 换一条路用节目表还原，否则永远停在「加载中」。
        val placeholdersByRadio = mutableMapOf<Long, MutableSet<String>>()
        val itemsNeedLoading = indicesToCheck.mapNotNull { index ->
            val item = player.getMediaItemAt(index)
            val isPlaceholder = item.localConfiguration?.uri.toString() == PLACEHOLDER_URI
            val id = item.mediaId
            if (!isPlaceholder || !loadingIds.add(id)) return@mapNotNull null
            val radioId = item.queueSource
                ?.takeIf { it.type == QueueSource.Type.PODCAST }
                ?.id?.toLongOrNull()?.takeIf { it > 0L }
            if (radioId != null) placeholdersByRadio.getOrPut(radioId) { mutableSetOf() }.add(id)
            id
        }
        if (itemsNeedLoading.isEmpty()) return

        try {
            val podcastIds = placeholdersByRadio.values.flatten().toSet()
            val podcastItems = placeholdersByRadio.flatMap { (radioId, ids) ->
                loadPodcastEpisodes(radioId, ids)
            }
            val songIds = itemsNeedLoading.filterNot { it in podcastIds }
            val songItems = if (songIds.isNotEmpty()) {
                withContext(Dispatchers.IO) { loadSongDetails(songIds) }
            } else {
                emptyList()
            }
            val loadedItems = podcastItems + songItems
            if (loadedItems.isNotEmpty()) {
                if (player.mediaItemCount == 0) return
                // 播客的 mainSong.id 在 /song/detail 里通常只返回时长，标题和封面是空的。
                // 拿这种残缺数据替换占位符，队列里就会多出「没有图标、没有标题、只剩一个时长」
                // 的空行；宁可保留占位符，等播出队列快照里的节目元数据来补。
                val loadedById = loadedItems
                    .filter { it.mediaMetadata.title?.isNotBlank() == true }
                    .associateBy(MediaItem::mediaId)
                // A song can appear more than once in the queue. Resolve every matching
                // placeholder, including occurrences outside this request's visible range.
                for (index in 0 until player.mediaItemCount) {
                    val currentItem = player.getMediaItemAt(index)
                    val newItem = loadedById[currentItem.mediaId]
                    if (newItem != null && currentItem.localConfiguration?.uri.toString() == PLACEHOLDER_URI) {
                        player.replaceMediaItem(index, newItem.withQueueEntry(
                            currentItem.queueSource, currentItem.queueEntryId ?: java.util.UUID.randomUUID().toString()
                        ))
                    }
                }
            }
        } finally {
            // Missing IDs remain retryable when the queue is reopened or scrolled again.
            loadingIds.removeAll(itemsNeedLoading.toSet())
        }
    }

    /**
     * 添加到队列末尾
     */
    fun addToQueue(items: List<MediaItem>, source: QueueSource? = null) {
        scope.launch(Dispatchers.Main) {
            player.addMediaItems(items.map { it.withQueueEntry(source ?: it.queueSource ?: QueueSource(QueueSource.Type.MANUAL)) })

            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
        }
    }

    /**
     * 在下一首播放 (插队)
     */
    @OptIn(UnstableApi::class)
    fun playNext(items: List<MediaItem>, source: QueueSource? = null) {
        scope.launch(Dispatchers.Main) {
            if (items.isEmpty()) return@launch
            val wasEmpty = player.mediaItemCount == 0
            val currentIndex = player.currentMediaItemIndex
            val insertIndex =
                if (wasEmpty) 0 else currentIndex + 1
            player.addMediaItems(insertIndex, items.map { it.withQueueEntry(source ?: it.queueSource ?: QueueSource(QueueSource.Type.MANUAL)) })

            if (player.shuffleModeEnabled && !wasEmpty) {
                val active = (player as? TransitionDeckPlayer)?.active
                if (active != null) {
                    val shuffleOrder = active.shuffleOrder
                    val order = mutableListOf<Int>()
                    var index = shuffleOrder.firstIndex
                    while (index != C.INDEX_UNSET && order.size < active.mediaItemCount) {
                        order += index
                        index = shuffleOrder.getNextIndex(index)
                    }
                    if (order.size == active.mediaItemCount) {
                        active.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(
                            prioritizeInsertedShuffleItems(order, currentIndex, insertIndex, items.size)
                                .toIntArray(),
                            System.nanoTime(),
                        ))
                    }
                }
            }

            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            if (wasEmpty) player.play()
        }
    }


    private suspend fun loadSongDetails(ids: List<String>): List<MediaItem> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getSongDetail(
                    com.ljyh.mei.data.model.api.GetSongDetails(c = ids.joinToString(","))
                )
                response.songs.map { it.toMediaItem() }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load song details", e)
                emptyList()
            }
        }
    }

    /**
     * 用电台节目表补全播客队列里的占位符。
     *
     * 播客的 `mainSong.id` 在 `/song/detail` 里只回时长，标题和封面都是空的，所以这里
     * 换成 `/dj/program/byradio`，拿节目名/封面/主播重建完整的 [MediaMetadata]。
     * 同一个电台一小时内只失败重试一次，避免滚动时反复打接口。
     */
    private suspend fun loadPodcastEpisodes(radioId: Long, ids: Set<String>): List<MediaItem> {
        if (ids.isEmpty()) return emptyList()
        val index = podcastProgramCache[radioId] ?: run {
            val lastFailure = podcastFetchFailedAt[radioId] ?: 0L
            if (System.currentTimeMillis() - lastFailure < PODCAST_RETRY_COOLDOWN_MS) return emptyList()
            val fetched = withContext(Dispatchers.IO) {
                try {
                    val response = weApiService.getRadioPrograms(
                        radioId = radioId,
                        limit = PODCAST_PROGRAM_LIMIT,
                        offset = 0,
                        asc = true,
                    )
                    if (response.code == 200 || response.code == 0) {
                        response.programs
                            .mapNotNull { it.toMediaMetadata() }
                            .associateBy { it.id.toString() }
                    } else {
                        emptyMap()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load podcast programs for $radioId", e)
                    emptyMap()
                }
            }
            if (fetched.isEmpty()) {
                podcastFetchFailedAt[radioId] = System.currentTimeMillis()
                return emptyList()
            }
            podcastProgramCache[radioId] = fetched
            fetched
        }
        return ids.mapNotNull { index[it]?.toMediaItem() }
    }

    fun release() {
        player.removeListener(this)
        loadingIds.clear()
        podcastProgramCache.clear()
        podcastFetchFailedAt.clear()
        // 不要 cancel scope，因为它是由外部 Service 传进来的
    }

    sealed class QueueState {
        object Idle : QueueState()
        data class Loading(val queueName: String) : QueueState()
        data class Playing(val queueName: String, val itemCount: Int) : QueueState()
        data class ItemsAdded(val count: Int) : QueueState()
        data class Error(val message: String) : QueueState()
        object Completed : QueueState()
        object Empty : QueueState()
    }
}
