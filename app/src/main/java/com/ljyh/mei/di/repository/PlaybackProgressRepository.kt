package com.ljyh.mei.di.repository

import com.ljyh.mei.data.model.room.PlaybackProgress
import com.ljyh.mei.di.dao.PlaybackProgressDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 播放进度（接着上次听 + 列表上的「已听 / 已听完」）。普通歌曲与播客单集共用。
 *
 * 判断规则集中在 [PlaybackProgressPolicy]，播放服务与界面都从这里过，
 * 避免各自写一套阈值。
 */
@Singleton
class PlaybackProgressRepository @Inject constructor(
    private val dao: PlaybackProgressDao,
) {
    fun observeAll(): Flow<List<PlaybackProgress>> = dao.observeAll()

    /** songId → 进度，列表和队列按 id 直接取。 */
    fun observeBySong(): Flow<Map<String, PlaybackProgress>> =
        dao.observeAll().map { list -> list.associateBy { it.songId } }

    suspend fun progressOf(songId: String): PlaybackProgress? = dao.get(songId)

    /**
     * 写入进度。
     *
     * 听到结尾附近不再删行，而是写一条 `completed = true`：删掉记录后，
     * 播客列表与播放队列就分不清「听完了」和「一次没点过」，两处都会显示成未播放。
     * 重听时位置从 0 重新累计，`completed` 自然被覆盖回 false。
     */
    suspend fun record(songId: String, positionMs: Long, durationMs: Long) {
        if (songId.isBlank() || positionMs <= 0L) return
        val completed = PlaybackProgressPolicy.isCompleted(positionMs, durationMs)
        // 只听了零点几秒又拿不到时长，没必要留痕。
        if (!completed && positionMs < PlaybackProgressPolicy.MIN_RECORD_MS && durationMs <= 0L) return
        val storedDuration = durationMs.takeIf { it > 0L }
            ?: dao.get(songId)?.durationMs
            ?: 0L
        dao.upsert(
            PlaybackProgress(
                songId = songId,
                positionMs = if (completed) 0L else positionMs,
                durationMs = storedDuration,
                updatedAt = System.currentTimeMillis(),
                completed = completed,
            )
        )
    }

    /** 可续播的位置；听完的、太短的、没记录的都返回 null。只读，不改表。 */
    suspend fun resumePositionOf(songId: String): Long? {
        val saved = dao.get(songId) ?: return null
        if (saved.completed) return null
        if (saved.positionMs < PlaybackProgressPolicy.MIN_RESUME_MS) return null
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
    /** 不足这个长度不值得跳过去，重听比跳到 8 秒更像用户预期。 */
    const val MIN_RESUME_MS = 15_000L

    /** 不足这个长度不记进度，避免每次误点都往表里写一行。 */
    const val MIN_RECORD_MS = 3_000L

    /** 距结尾不到这个长度视作听完。 */
    const val END_GUARD_MS = 20_000L

    /**
     * 短于这个长度的曲目不做「听完」判定。ExoPlayer 对 VBR 文件的时长估算常常偏小，
     * 几十秒的曲子很容易一开播就「接近结尾」，被判成听完后进度再也存不下来。
     */
    const val MIN_END_TRACK_MS = 60_000L

    /** 位置超过总长说明时长不可信（VBR 估算偏小），此时既不判听完也不算比例。 */
    fun durationTrustworthy(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0L && positionMs <= durationMs

    fun isCompleted(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs < MIN_END_TRACK_MS) return false
        if (!durationTrustworthy(positionMs, durationMs)) return false
        return positionMs >= durationMs - END_GUARD_MS
    }
}
