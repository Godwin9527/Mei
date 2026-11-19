package com.ljyh.mei.di.repository

import com.ljyh.mei.data.model.room.PlaybackProgress
import com.ljyh.mei.di.dao.PlaybackProgressDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * 播放进度（接着上次听）。普通歌曲与播客单集共用。
 *
 * 判断「算不算听完」的规则集中在 [PlaybackProgressPolicy]，读写都从这里过，
 * 避免播放服务与界面各写一套阈值。
 */
@Singleton
class PlaybackProgressRepository @Inject constructor(
    private val dao: PlaybackProgressDao,
) {
    fun observeAll(): Flow<List<PlaybackProgress>> = dao.observeAll()

    suspend fun progressOf(songId: String): PlaybackProgress? = dao.get(songId)

    /** 写入进度；进度接近结尾时视作听完，直接删掉，下次从头开始。 */
    suspend fun record(songId: String, positionMs: Long, durationMs: Long) {
        if (songId.isBlank()) return
        if (!PlaybackProgressPolicy.shouldKeep(positionMs, durationMs)) {
            dao.delete(songId)
            return
        }
        dao.upsert(
            PlaybackProgress(
                songId = songId,
                positionMs = positionMs,
                durationMs = durationMs,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    /** 可续播的位置；不满足条件时返回 null（同时顺手清掉已听完的残留记录）。 */
    suspend fun resumePositionOf(songId: String): Long? {
        val saved = dao.get(songId) ?: return null
        if (!PlaybackProgressPolicy.shouldKeep(saved.positionMs, saved.durationMs)) {
            dao.delete(songId)
            return null
        }
        return saved.positionMs
    }

    suspend fun clear(songId: String) = dao.delete(songId)

    suspend fun clearAll() = dao.clear()

    suspend fun clearFor(songIds: Collection<String>) {
        val ids = songIds.filter { it.isNotBlank() }
        if (ids.isNotEmpty()) dao.deleteByIds(ids)
    }
}

object PlaybackProgressPolicy {
    /** 不足这个长度不值得记，重听比跳到 8 秒更像用户预期。 */
    const val MIN_RESUME_MS = 15_000L

    /** 距结尾不到这个长度视作听完。 */
    const val END_GUARD_MS = 20_000L

    fun shouldKeep(positionMs: Long, durationMs: Long): Boolean {
        if (positionMs < MIN_RESUME_MS) return false
        if (durationMs > 0 && positionMs >= durationMs - END_GUARD_MS) return false
        return true
    }
}
