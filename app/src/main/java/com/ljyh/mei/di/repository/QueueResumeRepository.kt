package com.ljyh.mei.di.repository

import com.ljyh.mei.data.model.room.QueueResume
import com.ljyh.mei.di.dao.QueueResumeDao
import com.ljyh.mei.playback.queue.QueueSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 队列级记忆：这个歌单 / 专辑 / 电台上次播到哪一条。
 *
 * 只负责「哪一条」，不管「第几秒」；秒级进度仍在
 * [PlaybackProgressRepository] 里，播客续播会同时用到两者。
 */
@Singleton
class QueueResumeRepository @Inject constructor(
    private val dao: QueueResumeDao,
) {
    /** queueKey → 记忆，界面按 key 直接取。 */
    fun observeAll(): Flow<Map<String, QueueResume>> =
        dao.observeAll().map { list -> list.associateBy { it.queueKey } }

    suspend fun resumeOf(queueKey: String): QueueResume? = dao.get(queueKey)

    /** 记下当前播放到的曲目。id 为空或序号非法时直接跳过。 */
    suspend fun record(queueKey: String, mediaId: String, indexInList: Int) {
        if (queueKey.isBlank() || mediaId.isBlank() || indexInList < 0) return
        dao.upsert(
            QueueResume(
                queueKey = queueKey,
                mediaId = mediaId,
                indexInList = indexInList,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun clear(queueKey: String) = dao.delete(queueKey)

    suspend fun clearAll() = dao.clear()
}

/**
 * 把队列来源折算成记忆键。只有「还能再回来」的集合才有键：
 * 搜索结果、歌手页、私人 FM 这些每次内容都不同，记了也没有意义。
 */
fun queueResumeKeyOf(source: QueueSource?): String? {
    val id = source?.id?.takeIf { it.isNotBlank() }
    return when (source?.type) {
        QueueSource.Type.PLAYLIST -> id?.let { "playlist:$it" }
        QueueSource.Type.ALBUM -> id?.let { "album:$it" }
        QueueSource.Type.PODCAST -> id?.let { "podcast:$it" }
        QueueSource.Type.DAILY -> "daily"
        QueueSource.Type.LOCAL -> "local"
        else -> null
    }
}
