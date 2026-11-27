package com.ljyh.mei.ui.screen.main.library.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ljyh.mei.constants.LibrarySectionOrderKey
import com.ljyh.mei.utils.preferences.rememberPreference

/** 音乐库中可以上下调整顺序的区块。 */
enum class LibrarySection(val id: String, val label: String) {
    LIKED("liked", "我喜欢的音乐"),
    CREATED("created", "我创建的歌单"),
    COLLECTED("collected", "收藏歌单"),
    ALBUMS("albums", "收藏专辑"),
    PODCASTS("podcasts", "我关注的播客"),
}

/** 解析保存的顺序，缺失的区块补在末尾，保证新增区块不会因为旧数据而消失。 */
fun parseLibrarySectionOrder(raw: String?): List<LibrarySection> {
    val known = LibrarySection.entries.associateBy { it.id }
    val parsed = raw.orEmpty()
        .split(',')
        .mapNotNull { known[it.trim()] }
        .distinct()
    return parsed + LibrarySection.entries.filterNot { it in parsed }
}

fun encodeLibrarySectionOrder(order: List<LibrarySection>): String =
    order.joinToString(",") { it.id }

@Composable
fun rememberLibrarySectionOrder(): List<LibrarySection> {
    val rawOrder = rememberPreference(LibrarySectionOrderKey, "")
    val raw = rawOrder.value
    return remember(raw) { parseLibrarySectionOrder(raw) }
}
