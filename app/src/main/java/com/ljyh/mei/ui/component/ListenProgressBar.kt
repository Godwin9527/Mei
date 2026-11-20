package com.ljyh.mei.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 列表里那条 2dp 的收听进度条，播客节目列表和播放队列共用。
 *
 * 只画一条细线，不加高整行：队列行的可用高度本来就只有封面那么高。
 */
@Composable
fun ListenProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
    barHeight: Dp = 2.dp,
) {
    val shape = RoundedCornerShape(barHeight / 2)
    Box(
        modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(shape)
            .background(trackColor),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(shape)
                    .background(color),
            )
        }
    }
}
