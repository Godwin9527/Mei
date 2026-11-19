package com.ljyh.mei.ui.screen.podcast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ljyh.mei.constants.CommonImageRadius
import com.ljyh.mei.constants.DefaultPodcastTextSize
import com.ljyh.mei.constants.PodcastTextSizeKey
import com.ljyh.mei.data.model.podcast.DjRadio
import com.ljyh.mei.data.network.Resource
import com.ljyh.mei.ui.component.collection.MeiCollectionPage
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.screen.Screen
import com.ljyh.mei.utils.preferences.rememberPreference

/** 我关注的播客（电台）列表。用列表行而不是封面墙：评书类电台名普遍很长。 */
@Composable
fun PodcastsScreen(viewModel: PodcastViewModel = hiltViewModel()) {
    val nav = LocalNavController.current
    val radios by viewModel.radios.collectAsStateWithLifecycle()
    val (textSize, _) = rememberPreference(PodcastTextSizeKey, DefaultPodcastTextSize)
    var query by rememberSaveable { mutableStateOf("") }    LaunchedEffect(Unit) { viewModel.loadRadios() }

    val all = (radios as? Resource.Success)?.data.orEmpty()
    val filtered = remember(all, query) {
        val keyword = query.trim()
        if (keyword.isBlank()) all else all.filter { it.name.contains(keyword, ignoreCase = true) }
    }

    val emptyText = (radios as? Resource.Error)?.message ?: "还没有关注的播客，先在网易云音乐里关注一个电台吧"

    MeiCollectionPage(
        title = "我的播客",
        itemCount = filtered.size,
        itemKey = { "radio_${filtered[it].id}" },
        onNavigateBack = { nav.navigateUp() },
        onRetry = { viewModel.loadRadios(force = true) },
        artworkColumns = null,
        verticalItemSpacing = 0.dp,
        loading = radios is Resource.Loading,
        failed = radios is Resource.Error,
        emptyText = if (query.isNotBlank() && all.isNotEmpty()) "没有匹配的播客" else emptyText,
        emptyActionLabel = if (query.isNotBlank()) null else "重新加载",
        controls = {
            PodcastSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "搜索我关注的播客",
                modifier = Modifier.padding(bottom = 8.dp),
            )
        },
    ) { index ->
        val radio = filtered[index]
        PodcastRadioRow(
            radio = radio,
            textSize = textSize,
            onClick = { Screen.PodcastDetail.navigate(nav) { addPath(radio.id.toString()) } },
        )
        HorizontalDivider(
            modifier = Modifier.padding(start = 64.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            thickness = 0.5.dp,
        )
    }
}

@Composable
private fun PodcastRadioRow(radio: DjRadio, textSize: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = radio.picUrl.orEmpty(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(CommonImageRadius)),
        )
        Spacer(Modifier.width(12.dp))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(
                radio.name,
                fontSize = textSize.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(
                radio.dj?.nickname?.takeIf { it.isNotBlank() },
                radio.programCount.takeIf { it > 0 }?.let { "$it 个节目" },
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    meta,
                    fontSize = (textSize - 3).coerceAtLeast(10).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 播客列表与节目列表共用的轻量搜索框。 */
@Composable
fun PodcastSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )
        if (value.isNotEmpty()) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "清除",
                modifier = Modifier
                    .size(17.dp)
                    .clickable { onValueChange("") },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
