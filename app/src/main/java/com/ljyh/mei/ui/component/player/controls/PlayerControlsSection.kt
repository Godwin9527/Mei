package com.ljyh.mei.ui.component.player.controls

import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Player.STATE_BUFFERING
import com.ljyh.mei.constants.PlayerHorizontalPadding
import com.ljyh.mei.constants.ProgressBarStyle
import com.ljyh.mei.constants.ProgressBarStyleKey
import com.ljyh.mei.playback.queue.QueueSource
import com.ljyh.mei.playback.queue.queueSource
import com.ljyh.mei.ui.component.player.LocalPlayerForegroundColor
import com.ljyh.mei.ui.model.PlayerAction
import com.ljyh.mei.ui.model.PlayerActionProfile
import com.ljyh.mei.playback.PlayerConnection
import com.ljyh.mei.utils.preferences.rememberEnumPreference

@OptIn(UnstableApi::class)
@Composable
fun PlayerControlsSection(
    sliderPosition: Float,
    duration: Long,
    isPlaying: Boolean,
    playbackState: Int,
    playerConnection: PlayerConnection,
    onLyricClick: () -> Unit,
    onAction: (PlayerAction) -> Unit,
    onPositionPreview: (Long) -> Unit,
    onPositionChange: (Long) -> Unit,
    onMusicQualityClick: () -> Unit,
    portraitMotionStyle: Boolean = false,
    isCompact: Boolean = false,
    horizontalPadding: Dp = PlayerHorizontalPadding,
    includeNavigationBarPadding: Boolean = true,
    actionProfile: PlayerActionProfile = PlayerActionProfile.Phone,
    selectedAction: PlayerAction? = null,
    tightControls: Boolean = false,
) {
    // 紧凑模式下间距减半
    val spacerHeight = if (isCompact) 8.dp else 16.dp

    val (progressBarStyle, _) = rememberEnumPreference(
        key = ProgressBarStyleKey,
        defaultValue = ProgressBarStyle.LINEAR
    )

    val isFM = playerConnection.isFMMode.collectAsState()
    val isBuffering = isPlaying && playbackState == STATE_BUFFERING
    // 播客单集动辄一小时，额外给一组时间跳转；普通歌曲不显示，避免挤占界面。
    val isPodcast = playerConnection.player.currentMediaItem?.queueSource?.type == QueueSource.Type.PODCAST

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        if (progressBarStyle == ProgressBarStyle.LINEAR) {
            FluidProgressSlider(
                position = sliderPosition.toLong(),
                duration = duration,
                isPlaying = isPlaying,
                playbackSpeed = playerConnection.player.playbackParameters.speed,
                mediaKey = playerConnection.player.currentMediaItem?.mediaId,
                onPositionPreview = onPositionPreview,
                onPositionChange = onPositionChange,
                onMusicQualityClick = onMusicQualityClick,
                isBuffering = isBuffering,
                showMusicQuality = true,
                showRemainingTime = portraitMotionStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
            )
        } else {
            PlayerProgressSlider(
                position = sliderPosition.toLong(),
                duration = duration,
                onPositionPreview = onPositionPreview,
                isPlaying = isPlaying, // 波浪进度条需要这个参数
                onPositionChange = onPositionChange,
                onMusicQualityClick = onMusicQualityClick,
                isBuffering = isBuffering,
                showMusicQuality = true,
                showRemainingTime = portraitMotionStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
            )
        }


        Spacer(Modifier.height(spacerHeight))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
        ) {
            PlayerControls(
                playerConnection = playerConnection,
                canSkipPrevious = !isFM.value || playerConnection.player.hasPreviousMediaItem(),
                canSkipNext = playerConnection.player.hasNextMediaItem(),
                isPlaying = isPlaying,
                playbackState = playbackState,
                tight = tightControls,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 紧凑模式下可能需要隐藏底部 Toolbar 或者减小间距
        if (!isCompact) {
            if (isPodcast) {
                Spacer(Modifier.height(4.dp))
                PodcastSeekRow(
                    playerConnection = playerConnection,
                    modifier = Modifier.padding(horizontal = horizontalPadding),
                )
            }
            Spacer(Modifier.height(spacerHeight))
            PlayerActionToolbar(
                modifier = Modifier.padding(horizontal = horizontalPadding),
                onLyricClick = onLyricClick,
                onAction = onAction,
                profile = actionProfile,
                selectedAction = selectedAction,
            )
            if (includeNavigationBarPadding) Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        } else {
            // 紧凑模式底部留白少一点
            if (!tightControls) Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * 播客专用的一行时间跳转：保持上一首/下一首不变，另外给快退快进。
 * 评书单集通常一小时以上，只靠拖动进度条找位置太费劲。
 */
@Composable
private fun PodcastSeekRow(
    playerConnection: PlayerConnection,
    modifier: Modifier = Modifier,
) {
    val foreground = LocalPlayerForegroundColor.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(-30, -5, 5, 30).forEach { seconds ->
            PodcastSeekButton(seconds = seconds, foreground = foreground) {
                val player = playerConnection.player
                val raw = player.currentPosition + seconds * 1000L
                val duration = player.duration
                val target = if (duration > 0) raw.coerceIn(0L, duration) else raw.coerceAtLeast(0L)
                player.seekTo(target)
            }
        }
    }
}

@Composable
private fun PodcastSeekButton(seconds: Int, foreground: Color, onClick: () -> Unit) {
    val label = if (seconds > 0) "+${seconds}s" else "${seconds}s"
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = foreground.copy(alpha = 0.85f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
