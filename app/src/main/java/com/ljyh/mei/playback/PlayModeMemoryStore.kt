package com.ljyh.mei.playback

import android.content.Context
import android.os.SystemClock
import androidx.datastore.preferences.core.edit
import androidx.media3.common.Player
import com.ljyh.mei.constants.PlayModeMemoryKey
import com.ljyh.mei.utils.preferences.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * 一条队列来源记住的播放模式。
 *
 * 界面上的三态（列表循环 / 单曲循环 / 随机）其实是两个维度，这里存原始值，
 * 展示时再折算，避免以后加模式又要改存储格式。
 */
@Serializable
data class PlayModeSnapshot(
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_ALL,
)

/**
 * 按队列来源记忆播放模式。
 *
 * 为什么不用全局单值：同一个用户可能希望「通勤歌单顺序播、我喜欢的音乐随机播」，
 * 全局值只能记住最后一次的设置，一切歌单就被带跑。
 *
 * 内存里保留一份快照，入队时要同步读（`playQueue` 不是挂起函数），
 * 因此启动时加载一次，之后跟着回调就地更新。
 */
@Singleton
class PlayModeMemoryStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile
    private var memory: Map<String, PlayModeSnapshot> = emptyMap()

    /**
     * 程序自己在入队时设置随机/循环也会触发回调，若不加限制，
     * 切到 B 歌单的那一瞬间会把 A 歌单的模式改写成 B 的。这段时间内不写盘。
     */
    @Volatile
    private var suppressUntilElapsed = 0L

    private val json = playModeJson

    suspend fun load() {
        memory = try {
            decode(context.dataStore.data.first()[PlayModeMemoryKey])
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("PlayModeMemory").e(e, "load failed")
            emptyMap()
        }
    }

    /** 同步读取；没有记录时返回 null，调用方沿用全局默认值。 */
    fun snapshot(queueKey: String?): PlayModeSnapshot? =
        queueKey?.let { memory[it] }

    fun beginExternalChange(windowMs: Long = EXTERNAL_CHANGE_WINDOW_MS) {
        suppressUntilElapsed = SystemClock.elapsedRealtime() + windowMs
    }

    /**
     * 用户切换模式时写入。落在 [suppressUntilElapsed] 窗口内的调用会被丢弃：
     * 那是程序自己在应用记忆值，不该再记成这个队列的新模式。
     */
    suspend fun record(queueKey: String?, shuffle: Boolean, repeat: Int) {
        if (queueKey.isNullOrBlank()) return
        if (SystemClock.elapsedRealtime() < suppressUntilElapsed) return
        val current = memory[queueKey]
        if (current != null && current.shuffle == shuffle && current.repeat == repeat) return
        memory = memory + (queueKey to PlayModeSnapshot(shuffle, repeat))
        persist()
    }

    suspend fun clearAll() {
        memory = emptyMap()
        persist()
    }

    private suspend fun persist() {
        val snapshot = memory
        try {
            context.dataStore.edit { it[PlayModeMemoryKey] = encode(snapshot) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("PlayModeMemory").e(e, "persist failed")
        }
    }

    private fun decode(raw: String?): Map<String, PlayModeSnapshot> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching { json.decodeFromString<Map<String, PlayModeSnapshot>>(raw) }
            .getOrDefault(emptyMap())
    }

    private fun encode(value: Map<String, PlayModeSnapshot>): String =
        json.encodeToString(value)

    private companion object {
        const val EXTERNAL_CHANGE_WINDOW_MS = 600L
    }
}

/**
 * 卡片副标题用的简短文案。三态合成一句，不暴露「随机 + 单曲」这种少见组合。
 */
fun PlayModeSnapshot.shortLabel(): String = when {
    shuffle -> "随机"
    repeat == Player.REPEAT_MODE_ONE -> "单曲循环"
    else -> "顺序"
}

private val playModeJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** 界面侧直接读偏好里的整份记忆，用来给卡片配模式文案。 */
internal fun decodePlayModeMemory(raw: String?): Map<String, PlayModeSnapshot> {
    if (raw.isNullOrBlank()) return emptyMap()
    return runCatching { playModeJson.decodeFromString<Map<String, PlayModeSnapshot>>(raw) }
        .getOrDefault(emptyMap())
}

internal fun encodePlayModeMemory(value: Map<String, PlayModeSnapshot>): String =
    playModeJson.encodeToString(value)
