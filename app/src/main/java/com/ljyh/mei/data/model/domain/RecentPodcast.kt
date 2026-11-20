package com.ljyh.mei.data.model.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 最近收听的一集播客。
 *
 * 音乐库的「我关注的播客」卡片靠这条记录直接续播：电台 id 取自队列来源
 * （播客队列的 `QueueSource.id` 就是电台 id），集 id 是可播放的 `mainSong.id`，
 * 进度由 `PlaybackProgressRepository` 维护，这里只是跟着更新一份。
 */
@Serializable
data class RecentPodcastEpisode(
    val radioId: Long = 0L,
    val radioName: String = "",
    val coverUrl: String = "",
    val songId: Long = 0L,
    val episodeTitle: String = "",
    val artist: String = "",
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val updatedAt: Long = 0L,
) {
    val progress: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

object RecentPodcastCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(value: RecentPodcastEpisode): String = json.encodeToString(value)

    fun decode(raw: String?): RecentPodcastEpisode? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<RecentPodcastEpisode>(raw) }.getOrNull()
            ?.takeIf { it.radioId > 0L && it.songId > 0L }
    }
}
