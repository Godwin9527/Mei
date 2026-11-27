package com.ljyh.mei.ui.screen.main.library.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

/**
 * 音乐库首页的统一通栏卡：封面铺满 + 底部渐变压暗 + 左下文字 + 右下播放键。
 *
 * 喜欢、创建歌单、收藏歌单、收藏专辑、播客共用同一个组件，高度由设置统一控制，
 * 所以文字与按钮的尺寸跟着高度走，不写死。
 *
 * [isCurrent] 表示「播放器里正装着的就是这个集合」，此时左侧亮起一条 3dp 竖色条，
 * 播放键变成暂停键且只切换播放状态，不重建队列——否则想暂停一下，队列就被重置了。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LibraryBannerCard(
    title: String,
    subtitle: String?,
    coverUrl: String?,
    fallbackIcon: ImageVector,
    height: Dp,
    isCurrent: Boolean,
    isPlaying: Boolean,
    preparing: Boolean,
    canPlay: Boolean,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onToggle: () -> Unit,
    onPlayFromStart: () -> Unit,
    modifier: Modifier = Modifier,
    titleMaxLines: Int = 1,
) {
    val compact = height < 132.dp
    // 播客集名走两行：按字数降字号，长集名宁可小一点也别一行就砍掉。
    val titleStyle = if (titleMaxLines > 1) {
        val (size, line) = when {
            title.length <= 32 -> 15.sp to 19.sp
            title.length <= 40 -> 13.sp to 17.sp
            else -> 12.sp to 16.sp
        }
        MaterialTheme.typography.titleMedium.copy(fontSize = size, lineHeight = line)
    } else when {
        height >= 170.dp -> MaterialTheme.typography.headlineSmall
        compact -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleLarge
    }
    val buttonSize = if (compact) 40.dp else 48.dp
    val buttonAction = if (isCurrent) onToggle else onPlay
    val playingNow = isCurrent && isPlaying
    val enabled = canPlay && !preparing

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = "打开",
                onClick = onOpen,
            )
    ) {
        Icon(
            fallbackIcon,
            contentDescription = null,
            modifier = Modifier.align(Alignment.Center).size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.10f),
                                Color.Black.copy(alpha = 0.30f),
                                Color.Black.copy(alpha = 0.82f),
                            )
                        )
                    )
            )
        }

        // 正在播放：左缘竖色条。按卡片高度取比例，换高度时不会突然拉满。
        if (isCurrent) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(vertical = height * 0.22f)
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = 16.dp,
                    bottom = 16.dp,
                    top = 16.dp,
                    end = buttonSize + 28.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = titleStyle,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = if (compact) MaterialTheme.typography.labelSmall
                    else MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .size(buttonSize)
                .clip(CircleShape)
                .background(
                    if (enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                )
                .combinedClickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = if (isCurrent) "暂停或继续" else "播放",
                    onLongClickLabel = "从头播放",
                    onLongClick = { if (enabled) onPlayFromStart() },
                    onClick = { if (enabled) buttonAction() },
                )
                .semantics {
                    contentDescription = when {
                        preparing -> "正在准备播放"
                        playingNow -> "暂停播放"
                        isCurrent -> "继续播放"
                        else -> "播放"
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            when {
                preparing -> CircularProgressIndicator(
                    modifier = Modifier.size(buttonSize * 0.45f),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                playingNow -> Icon(
                    Icons.Rounded.Pause,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(buttonSize * 0.55f),
                )
                else -> Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(buttonSize * 0.62f),
                )
            }
        }
    }
}
