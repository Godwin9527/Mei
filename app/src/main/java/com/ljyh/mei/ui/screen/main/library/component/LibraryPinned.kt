package com.ljyh.mei.ui.screen.main.library.component

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 首页可以勾选条目的三个区块。顺序本身就是展示顺序。 */
enum class PinnableSection(val id: String, val label: String) {
    CREATED("created", "我创建的歌单"),
    COLLECTED("collected", "收藏歌单"),
    ALBUMS("albums", "收藏专辑"),
    ;

    companion object {
        fun fromId(id: String): PinnableSection? = entries.firstOrNull { it.id == id }
    }
}

/**
 * 各区块勾选上首页的条目与顺序。
 *
 * 空列表和「没有配置」是两件事：前者是用户主动全部取消，区块应当整个隐藏；
 * 后者是升级后第一次打开，按默认（智能排序前 4 个）给一份。
 * 因此 [LibraryPinnedCodec.decode] 返回可空值。
 */
@Immutable
@Serializable
data class LibraryPinned(
    val created: List<String> = emptyList(),
    val collected: List<String> = emptyList(),
    val albums: List<String> = emptyList(),
) {
    fun idsOf(section: PinnableSection): List<String> = when (section) {
        PinnableSection.CREATED -> created
        PinnableSection.COLLECTED -> collected
        PinnableSection.ALBUMS -> albums
    }

    fun with(section: PinnableSection, ids: List<String>): LibraryPinned = when (section) {
        PinnableSection.CREATED -> copy(created = ids)
        PinnableSection.COLLECTED -> copy(collected = ids)
        PinnableSection.ALBUMS -> copy(albums = ids)
    }
}

object LibraryPinnedCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** 返回 null 表示从未配置过（旧版本升级上来）。 */
    fun decode(raw: String?): LibraryPinned? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<LibraryPinned>(raw) }.getOrNull()
    }

    fun encode(value: LibraryPinned): String = json.encodeToString(value)
}

/** 首次打开时的默认：按现有智能排序取前 4 个，避免一上来看到一堆空块。 */
fun defaultPinned(created: List<String>, collected: List<String>, albums: List<String>): LibraryPinned =
    LibraryPinned(
        created = created.take(DEFAULT_PINNED_COUNT),
        collected = collected.take(DEFAULT_PINNED_COUNT),
        albums = albums.take(DEFAULT_PINNED_COUNT),
    )

const val DEFAULT_PINNED_COUNT = 4

/** 卡片高度：默认 148，限制在一屏能容纳的合理区间。 */
const val DEFAULT_LIBRARY_CARD_HEIGHT = 148
const val MIN_LIBRARY_CARD_HEIGHT = 112
const val MAX_LIBRARY_CARD_HEIGHT = 200

fun coerceLibraryCardHeight(value: Int): Int =
    value.coerceIn(MIN_LIBRARY_CARD_HEIGHT, MAX_LIBRARY_CARD_HEIGHT)
