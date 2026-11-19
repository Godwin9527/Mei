package com.ljyh.mei.ui.component.player.applemusic

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import com.ljyh.mei.constants.DefaultPodcastTextSize
import com.ljyh.mei.constants.PlayerTitleFontSizeKey
import com.ljyh.mei.constants.PodcastTextSizeKey
import com.ljyh.mei.playback.queue.QueueSource
import com.ljyh.mei.playback.queue.queueSource
import com.ljyh.mei.ui.component.player.controls.PlayerControlButton
import com.ljyh.mei.ui.local.LocalPlayerConnection
import com.ljyh.mei.utils.preferences.rememberPreference
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ljyh.mei.ui.component.player.rememberPlayerDisplayTitle

@Composable
fun Title(
    title: String,
    subTitle: String,
    isLiked: Boolean,
    onLikeClick: () -> Unit,
    onMoreClick: () -> Unit,
    onTitleClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.headlineSmall,
    subTitleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    needShadow: Boolean = true,
    titleColor: Color = Color.White,
    subTitleColor: Color = Color.White.copy(alpha = 0.7f),
    iconColor: Color = Color.White,
) {
    val shadowStyle = if (needShadow) Shadow(
        color = Color.Black.copy(alpha = 0.5f),
        offset = Offset(2f, 2f),
        blurRadius = 8f
    ) else null

    // 播客标题普遍很长（评书会带集数序号），单独用更小的字号，并把长标题改成滚动显示。
    val isPodcastTrack = LocalPlayerConnection.current
        ?.player?.currentMediaItem?.queueSource?.type == QueueSource.Type.PODCAST
    val globalTitleSize = rememberPreference(PlayerTitleFontSizeKey, 0).value
    val podcastTitleSize = rememberPreference(PodcastTextSizeKey, DefaultPodcastTextSize).value
    val resolvedTitleSize = when {
        isPodcastTrack -> podcastTitleSize.sp
        globalTitleSize > 0 -> globalTitleSize.sp
        else -> titleStyle.fontSize
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically, // 垂直居中
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. 文字区域：使用 weight(1f) 占据剩余空间
        Column(
            modifier = Modifier.weight(1f)
                .clickable(onClick = onTitleClick),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = rememberPlayerDisplayTitle(title),
                style = titleStyle.copy(
                    fontSize = resolvedTitleSize,
                    shadow = shadowStyle,
                    fontWeight = FontWeight.Bold
                ),
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(),
            )
            Text(
                text = subTitle,
                style = subTitleStyle.copy(
                    shadow = shadowStyle,
                ),
                color = subTitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(),
            )
        }

        // 2. 按钮区域
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlayerControlButton(
                onClick = onLikeClick,
                foreground = iconColor,
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = if (isLiked) "取消喜欢" else "喜欢",
                    tint = iconColor,
                    modifier = Modifier.size(20.dp),
                )
            }

            PlayerControlButton(
                onClick = onMoreClick,
                foreground = iconColor,
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "更多",
                    tint = iconColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
