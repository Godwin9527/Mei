package com.ljyh.mei.playback.source

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * 记录「哪些歌曲 id 其实是播客节目」。
 *
 * 播客节目只有一个 192kbps 的 mp3 音源，既没有无损，也不该去 QQ/酷狗等备用音源兜底
 * （那样会白等十几秒再报错）。解析地址时据此直接使用 standard 音质并跳过备用音源。
 *
 * 只在内存里记录：入队、恢复队列时都会经过 [com.ljyh.mei.data.model.domain.toMediaItem]，
 * 从而重新写入。冷启动后未重新入队的旧条目退化为普通歌曲行为，不影响正确性。
 */
object PodcastTrackRegistry {
    private const val MAX_ENTRIES = 512
    private val ids: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    fun mark(mediaId: String) {
        if (mediaId.isBlank()) return
        if (ids.size >= MAX_ENTRIES && mediaId !in ids) ids.clear()
        ids.add(mediaId)
    }

    fun contains(mediaId: String): Boolean = mediaId in ids

    fun clear() = ids.clear()
}
