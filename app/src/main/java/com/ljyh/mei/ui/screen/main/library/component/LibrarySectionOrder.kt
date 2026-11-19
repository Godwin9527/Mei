package com.ljyh.mei.ui.screen.main.library.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import com.ljyh.mei.constants.LibrarySectionOrderKey
import com.ljyh.mei.utils.preferences.dataStore
import com.ljyh.mei.utils.preferences.rememberPreference
import kotlinx.coroutines.launch

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

/** 音乐库右上角的「编辑布局」弹窗：用上下箭头调整区块顺序，改完立即生效。 */
@Composable
fun LibraryLayoutDialog(order: List<LibrarySection>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var working by remember(order) { mutableStateOf(order) }

    fun persist(next: List<LibrarySection>) {
        working = next
        scope.launch {
            context.dataStore.edit { it[LibrarySectionOrderKey] = encodeLibrarySectionOrder(next) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("调整音乐库顺序") },
        text = {
            Column {
                Text(
                    "用上下箭头把常用的放前面。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                working.forEachIndexed { index, section ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            section.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            enabled = index > 0,
                            onClick = {
                                val next = working.toMutableList()
                                next.removeAt(index)
                                next.add(index - 1, section)
                                persist(next)
                            },
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "上移")
                        }
                        IconButton(
                            enabled = index < working.lastIndex,
                            onClick = {
                                val next = working.toMutableList()
                                next.removeAt(index)
                                next.add(index + 1, section)
                                persist(next)
                            },
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "下移")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}
