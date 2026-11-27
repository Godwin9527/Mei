package com.ljyh.mei.ui.screen.main.library

import com.ljyh.mei.data.model.domain.toMediaItem
import com.ljyh.mei.data.model.response.MiniPlaylistDetail
import com.ljyh.mei.data.model.room.QueueResume
import com.ljyh.mei.playback.queue.ListQueue
import com.ljyh.mei.playback.queue.QueueSource

/**
 * Preserve the complete order even when detail only includes metadata for some tracks.
 *
 * [resume] 是这个歌单上次播到的那一条：按 mediaId 定位，不按序号——开启随机播放后
 * 队列的遍历顺序会变，只有 id 始终指向同一首歌。找不到（歌已经被移出歌单）就从头播。
 */
internal fun buildLibraryPlaylistQueue(
    detail: MiniPlaylistDetail,
    resume: QueueResume? = null,
): ListQueue? {
    val ids = detail.trackIds.ifEmpty { detail.tracks.map { it.id } }.distinct()
    if (detail.id <= 0 || ids.isEmpty()) return null
    val metadata = detail.tracks.associateBy { it.id }
    val queue = ListQueue(
        id = "playlist_${detail.id}", title = detail.name,
        source = QueueSource(QueueSource.Type.PLAYLIST, detail.id.toString(), detail.name),
        items = ids.map { id -> id.toString() to metadata[id]?.toMediaItem() },
        // 歌单只记「哪一首」，不记秒数：整条队列不做单曲续播，
        // 否则定位到第 8 首之后又会自动跳进那一首的中段。
        suppressTrackResume = true,
    )
    val mediaId = resume?.mediaId?.takeIf { it.isNotBlank() } ?: return queue
    return queue.startingAt(mediaId) ?: queue
}
