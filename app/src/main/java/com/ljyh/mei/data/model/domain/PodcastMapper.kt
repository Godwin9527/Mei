package com.ljyh.mei.data.model.domain

import com.ljyh.mei.data.model.podcast.DjProgram

/**
 * 播客节目 → 普通曲目元数据。
 *
 * 播放链路只需要一个可以取到音频的歌曲 id，所以这里把节目转换成一首「歌」：
 * - `id` = `mainSong.id`（唯一能取到音频的 id）
 * - 标题 = 节目名，歌手 = 主播，专辑 = 电台名
 * 这样播放器、队列、通知栏、锁屏、最近播放全部可以原样复用。
 *
 * 无法播放的节目（付费、已下架、缺少 mainSong）返回 null，由列表过滤掉。
 */
fun DjProgram.toMediaMetadata(): MediaMetadata? {
    val song = mainSong ?: return null
    val songId = song.id.takeIf { it > 0 } ?: return null
    val radio = radio
    val host = dj
    val artistName = host?.nickname?.takeIf { it.isNotBlank() }
        ?: radio?.dj?.nickname?.takeIf { it.isNotBlank() }
        ?: "网易云播客"
    val hostId = host?.userId?.takeIf { it > 0 } ?: radio?.dj?.userId ?: 0L
    val cover = coverUrl?.takeIf { it.isNotBlank() }
        ?: radio?.picUrl?.takeIf { it.isNotBlank() }
        ?: song.album?.picUrl.orEmpty()

    return MediaMetadata(
        id = songId,
        title = name.ifBlank { song.name.orEmpty() }.ifBlank { "未命名节目" },
        coverUrl = cover,
        artists = listOf(MediaMetadata.Artist(id = hostId, name = artistName)),
        duration = duration.takeIf { it > 0 } ?: song.duration,
        album = MediaMetadata.Album(id = radio?.id ?: 0, title = radio?.name.orEmpty()),
        isPodcast = true,
        neteaseId = songId,
    )
}
