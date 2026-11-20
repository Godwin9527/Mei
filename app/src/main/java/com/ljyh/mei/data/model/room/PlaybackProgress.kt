package com.ljyh.mei.data.model.room

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 单曲播放进度，用于「接着上次听」和在列表/队列里标注「已听 / 已听完」。
 *
 * 与 [Song] 之间不建外键：播客单集可能只留下一条历史记录，删歌时也不该连带清掉进度。
 * songId 与 ExoPlayer 的 mediaId 一致，因此普通歌曲和播客节目共用一张表。
 *
 * [completed] 表示「这一集已经听完」。听完不再删行——删掉之后就分不清「听完了」和
 * 「一次没点过」，播客列表和播放队列会把已经听完的集数显示成未播放。
 */
@Entity(tableName = "playback_progress")
data class PlaybackProgress(
    @PrimaryKey val songId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val completed: Boolean = false,
) {
    /** 0f..1f 的收听比例，时长未知时按 0 处理。 */
    val fraction: Float
        get() = when {
            completed -> 1f
            durationMs <= 0L -> 0f
            else -> (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        }
}
