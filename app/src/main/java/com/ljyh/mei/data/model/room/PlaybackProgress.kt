package com.ljyh.mei.data.model.room

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 单曲播放进度，用于「接着上次听」。
 *
 * 与 [Song] 之间不建外键：播客单集可能只留下一条历史记录，删歌时也不该连带清掉进度。
 * songId 与 ExoPlayer 的 mediaId 一致，因此普通歌曲和播客节目共用一张表。
 */
@Entity(tableName = "playback_progress")
data class PlaybackProgress(
    @PrimaryKey val songId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)
