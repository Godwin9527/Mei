package com.ljyh.mei.data.model.room

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 队列级「上次播到哪一首」。
 *
 * 与 [PlaybackProgress] 分工：这张表只回答「这个歌单 / 专辑 / 电台上次停在列表里的哪一条」，
 * 单曲播到几分几秒仍由 `playback_progress` 负责，两者不互相覆盖。
 *
 * 定位一律用 [mediaId]，不用 [indexInList]：开启随机播放后队列顺序会被打乱，
 * 序号在两次播放之间没有可比性，只有 id 始终指向同一首歌。序号只用于界面上的「第 8 首」。
 *
 * [queueKey] 形如 `playlist:123` / `album:456` / `podcast:789` / `daily` / `local`。
 */
@Entity(tableName = "queue_resume")
data class QueueResume(
    @PrimaryKey val queueKey: String,
    val mediaId: String,
    val indexInList: Int,
    val updatedAt: Long,
)
