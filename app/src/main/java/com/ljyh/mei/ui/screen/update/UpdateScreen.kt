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
                            val size = state.release?.sizeText.orEmpty()
                            if (size.isNotBlank()) {
                                Text(
                                    size,
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("正在下载 ${state.progress}%", style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
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
                onClick = { viewModel.check() },
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
