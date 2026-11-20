package com.ljyh.mei.playback.source

import android.content.Context
import android.content.SharedPreferences
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * 记录「哪些歌曲 id 其实是播客节目」。
 *
 * 播客节目只有一个 192kbps 的 mp3 音源，既没有无损，也不该去 QQ/酷狗等备用音源兜底
 * （那样会白等十几秒再报错）。解析地址时据此直接使用 standard 音质并跳过备用音源。
 *
 * 必须持久化：早先只在内存里记录，冷启动、从「最近播放」进入、通知栏恢复时登记表是空的，
 * 同一集会被当成普通歌曲先请求高音质，拿到的却是另一份文件，两份字节共用缓存 key 后
 * 播放进度和内容就会错开。
 */
object PodcastTrackRegistry {
    private const val PREFS_NAME = "podcast_tracks"
    private const val KEY_IDS = "ids"
    private const val MAX_ENTRIES = 512

    private val ids: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    @Volatile
    private var prefs: SharedPreferences? = null

    /** 在 Application.onCreate 调用一次；之后 [contains] 立即能识别历史播客。 */
    fun install(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val store = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ids.addAll(store.getStringSet(KEY_IDS, emptySet()).orEmpty())
            prefs = store
        }
    }

    fun mark(mediaId: String) {
        if (mediaId.isBlank()) return
        if (ids.size >= MAX_ENTRIES && mediaId !in ids) ids.clear()
        if (ids.add(mediaId)) persist()
    }

    fun contains(mediaId: String): Boolean = mediaId in ids

    fun snapshot(): Set<String> = ids.toSet()

    fun clear() {
        ids.clear()
        persist()
    }

    private fun persist() {
        // 集合可能正被并发修改，写入前先取一份快照。
        prefs?.edit()?.putStringSet(KEY_IDS, ids.toSet())?.apply()
    }
}
