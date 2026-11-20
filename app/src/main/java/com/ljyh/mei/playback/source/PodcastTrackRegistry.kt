package com.ljyh.mei.playback.source

import android.content.Context
import android.content.SharedPreferences

/**
 * 记录「哪些歌曲 id 其实是播客节目」。
 *
 * 播客节目只有一个 192kbps 的 mp3 音源，既没有无损，也不该去 QQ/酷狗等备用音源兜底
 * （那样会白等十几秒再报错）。解析地址时据此直接使用 standard 音质并跳过备用音源。
 *
 * 必须持久化：早先只在内存里记录，冷启动、从「最近播放」进入、通知栏恢复时登记表是空的，
 * 同一集会被当成普通歌曲先请求高音质，拿到的却是另一份文件，两份字节共用缓存 key 后
 * 播放进度和内容就会错开。
 *
 * 容量上限见 [MAX_ENTRIES]，超限时淘汰最早登记的那一批，而不是整表清空：
 * 整表清空的瞬间，历史播客会被重新当成普通歌曲，进度记录也会跟着断掉。
 */
object PodcastTrackRegistry {
    private const val PREFS_NAME = "podcast_tracks"
    private const val KEY_IDS = "ids"
    private const val KEY_ORDERED_IDS = "ids_ordered"
    private const val SEPARATOR = "\n"
    private const val MAX_ENTRIES = 2048
    private const val EVICT_COUNT = 256

    /** 插入顺序即登记顺序，淘汰时从头部取最旧的。 */
    private val ids = LinkedHashSet<String>()

    @Volatile
    private var prefs: SharedPreferences? = null

    /** 在 Application.onCreate 调用一次；之后 [contains] 立即能识别历史播客。 */
    @Synchronized
    fun install(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ordered = store.getString(KEY_ORDERED_IDS, null)
        if (!ordered.isNullOrBlank()) {
            ordered.split(SEPARATOR).forEach { if (it.isNotBlank()) ids.add(it) }
        } else {
            // 旧版本只存了无序集合，首次启动按原样接过来，之后再按登记顺序维护。
            ids.addAll(store.getStringSet(KEY_IDS, emptySet()).orEmpty())
        }
        prefs = store
        if (ids.isNotEmpty()) persist()
    }

    @Synchronized
    fun mark(mediaId: String) {
        if (mediaId.isBlank()) return
        if (mediaId in ids) {
            // 重新登记视作「最近用过」，挪到队尾，避免常听的那几集先被淘汰。
            ids.remove(mediaId)
            ids.add(mediaId)
            return
        }
        while (ids.size >= MAX_ENTRIES) {
            val iterator = ids.iterator()
            var removed = 0
            while (removed < EVICT_COUNT && iterator.hasNext()) {
                iterator.next()
                iterator.remove()
                removed++
            }
            if (removed == 0) break
        }
        ids.add(mediaId)
        persist()
    }

    @Synchronized
    fun contains(mediaId: String): Boolean = mediaId in ids

    @Synchronized
    fun snapshot(): Set<String> = ids.toSet()

    @Synchronized
    fun clear() {
        ids.clear()
        persist()
    }

    @Synchronized
    private fun persist() {
        // 集合可能正被并发修改，写入前先取一份快照。
        prefs?.edit()
            ?.putString(KEY_ORDERED_IDS, ids.joinToString(SEPARATOR))
            ?.remove(KEY_IDS)
            ?.apply()
    }
}
