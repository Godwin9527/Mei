package com.ljyh.mei.ui.screen.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ljyh.mei.ui.component.glass.LocalMeiPageScroll
import com.ljyh.mei.ui.component.glass.MeiLargeTitle
import com.ljyh.mei.ui.component.glass.MeiPinnedPage
import com.ljyh.mei.ui.component.layout.meiPageGutter
import com.ljyh.mei.ui.local.LocalNavController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 设置 → 检查更新。查 GitHub Releases，一键下载并交给系统安装器。 */
@Composable
fun UpdateScreen(viewModel: UpdateViewModel = hiltViewModel()) {
    val nav = LocalNavController.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    MeiPinnedPage(
        title = "检查更新",
        onNavigateBack = nav::navigateUp,
    ) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(LocalMeiPageScroll.current!!)
                .padding(paddingValues)
                .padding(horizontal = meiPageGutter()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MeiLargeTitle("检查更新", startPadding = 0.dp)

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "当前版本",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "v${state.currentVersion}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    when {
                        state.checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("  正在检查…", style = MaterialTheme.typography.bodySmall)
                        }

                        state.upToDate -> Text(
                            "已是最新版本",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        state.release != null -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "发现新版本 v${state.release?.versionName}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            val meta = buildList {
                                state.release?.sizeText?.takeIf { it.isNotBlank() }?.let { add(it) }
                                state.release?.cachedAt?.takeIf { it > 0 }?.let {
                                    add("${clockText(it)} 的查询结果")
                                }
                            }.joinToString(" · ")
                            if (meta.isNotBlank()) {
                                Text(
                                    meta,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        state.error != null -> Text(
                            state.error.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            val release = state.release
            if (release != null && !release.notes.isNullOrBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("更新内容", style = MaterialTheme.typography.titleSmall)
                        Text(
                            release.notes.take(2000),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.downloading) {
                val detail = state.download
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        buildString {
                            append("正在下载 ${state.progress}%")
                            if (detail != null && detail.bytesPerSecond > 0) {
                                append(" · ")
                                append(formatSpeed(detail.bytesPerSecond))
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (detail != null && detail.totalBytes > 0) {
                        Text(
                            buildString {
                                append(formatBytes(detail.downloadedBytes))
                                append(" / ")
                                append(formatBytes(detail.totalBytes))
                                if (detail.resumed) append(" · 接着上次继续")
                                if (detail.source.isNotBlank()) {
                                    append(" · 来源 ")
                                    append(detail.source)
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 失败时如果已经下了一部分，明确告诉用户重试是从断点续传，不会白下。
            if (!state.downloading && state.error != null) {
                val detail = state.download
                if (detail != null && detail.downloadedBytes > 0 && detail.totalBytes > 0) {
                    Text(
                        "已保留 ${formatBytes(detail.downloadedBytes)}（${detail.percent}%），重试会从这里继续",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when {
                state.downloadedFile != null -> Button(
                    onClick = {
                        if (!viewModel.canInstallPackages()) {
                            viewModel.openInstallPermissionSetting()
                        } else {
                            viewModel.install()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("安装新版本") }

                release != null -> Button(
                    onClick = {
                        if (viewModel.canInstallPackages()) viewModel.download()
                        else viewModel.openInstallPermissionSetting()
                    },
                    enabled = !state.downloading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    androidx.compose.material3.Icon(Icons.Rounded.SystemUpdate, null)
                    Text("  下载 v${release.versionName}")
                }
            }

            TextButton(
                onClick = { viewModel.check(force = true) },
                enabled = !state.checking && !state.downloading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                androidx.compose.material3.Icon(Icons.Rounded.Refresh, null)
                Text("  重新检查")
            }

            Text(
                "首次更新需要在系统设置里允许「安装未知应用」。Android 不允许应用静默安装，最后一步的安装确认必须由你点按。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 ->
        String.format(Locale.US, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)

    bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> String.format(Locale.US, "%d KB", bytes / 1024)
    else -> "$bytes B"
}

private fun formatSpeed(bytesPerSecond: Long): String =
    if (bytesPerSecond >= 1024L * 1024) {
        String.format(Locale.US, "%.1f MB/s", bytesPerSecond / 1024.0 / 1024.0)
    } else {
        String.format(Locale.US, "%d KB/s", bytesPerSecond / 1024)
    }

private fun clockText(epochMs: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
