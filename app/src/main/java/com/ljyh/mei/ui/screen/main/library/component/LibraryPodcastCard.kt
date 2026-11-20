package com.ljyh.mei.ui.screen.main.library.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ljyh.mei.data.model.domain.RecentPodcastEpisode
import com.ljyh.mei.data.model.room.PlaybackProgress
import com.ljyh.mei.utils.TimeUtils.formatDuration

/**
 * 音乐库里「我关注的播客」那张通栏卡，样式对齐「我喜欢的音乐」：
 * 封面铺满 + 渐变遮罩 + 左下文字 + 右下播放键。
 *
 * - 有最近收听记录时，点整卡进电台节目单，点播放键直接续播那一集
 * - 没有记录时显示「去挑一部」，点卡进关注列表
 */
@Composable
internal fun LibraryPodcastCard(
    recent: RecentPodcastEpisode?,
    progress: PlaybackProgress?,
    playing: Boolean,
    height: Dp,
    modifier: Modifier = Modifier,
    canPlay: Boolean,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onBrowse: () -> Unit,
) {
    val hasEpisode = recent != null
    val cover = recent?.coverUrl.orEmpty()
    // 进度以本地进度表为准，偏好里那份只在表里还没有记录时兜底：
    // 偏好写的是「最近一集」，一旦切换过集数就可能是旧值。
    val completed = progress?.completed == true
    val listenedMs = when {
        completed -> 0L
        progress != null -> progress.positionMs
        else -> recent?.positionMs ?: 0L
    }
    val totalMs = progress?.durationMs?.takeIf { it > 0L } ?: recent?.durationMs ?: 0L
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(
                role = Role.Button,
                onClickLabel = if (hasEpisode) "打开播客节目单" else "浏览我关注的播客",
                onClick = if (hasEpisode) onOpen else onBrowse,
            )
    ) {
        Icon(
            Icons.Rounded.Mic,
            contentDescription = null,
            modifier = Modifier.align(Alignment.Center).size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (cover.isNotBlank()) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (hasEpisode) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.10f),
                                Color.Black.copy(alpha = 0.28f),
                                Color.Black.copy(alpha = 0.82f),
                            )
                        )
                    )
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 16.dp, top = 16.dp, end = 76.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = recent?.radioName?.takeIf { it.isNotBlank() } ?: "我关注的播客",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (recent != null) {
                Text(
                    text = recent.episodeTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (completed) {
                        buildString {
                            append("已听完")
                            if (totalMs > 0L) append(" · ${formatDuration(totalMs)}")
                        }
                    } else {
                        buildString {
                            append("已听 ${formatDuration(listenedMs)}")
                            if (totalMs > 0L) append(" / ${formatDuration(totalMs)}")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                )
            } else {
                Text(
                    text = "还没有收听记录，去挑一部评书",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                )
            }
        }
        FilledIconButton(
            onClick = if (hasEpisode) onPlay else onBrowse,
            enabled = !hasEpisode || (canPlay && !playing),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .size(48.dp)
                .semantics {
                    contentDescription = when {
                        playing -> "正在准备播放队列"
                        hasEpisode -> "继续播放上次的播客"
                        else -> "浏览我关注的播客"
                    }
                },
        ) {
            if (playing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(
                    imageVector = if (hasEpisode) Icons.Rounded.PlayArrow else Icons.Rounded.Mic,
                    contentDescription = null,
                )
            }
        }
    }
}
