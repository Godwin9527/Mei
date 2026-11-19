package com.ljyh.mei.ui.screen.podcast

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ljyh.mei.data.network.Resource
import com.ljyh.mei.ui.component.card.ArtworkCard
import com.ljyh.mei.ui.component.collection.MeiCollectionPage
import com.ljyh.mei.ui.component.utils.rememberDeviceInfo
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.screen.Screen

/** 我关注的播客（电台）列表。 */
@Composable
fun PodcastsScreen(viewModel: PodcastViewModel = hiltViewModel()) {
    val nav = LocalNavController.current
    val radios by viewModel.radios.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadRadios() }

    val list = (radios as? Resource.Success)?.data.orEmpty()
    val isTablet = rememberDeviceInfo().isTablet
    MeiCollectionPage(
        title = "我的播客",
        itemCount = list.size,
        itemKey = { "radio_${list[it].id}" },
        onNavigateBack = { nav.navigateUp() },
        onRetry = { viewModel.loadRadios(force = true) },
        artworkColumns = if (isTablet) GridCells.Adaptive(168.dp) else GridCells.Fixed(2),
        loading = radios is Resource.Loading,
        failed = radios is Resource.Error,
        emptyText = (radios as? Resource.Error)?.message
            ?: "还没有关注的播客，先在网易云音乐里关注一个电台吧",
    ) { index ->
        val radio = list[index]
        ArtworkCard(
            radio.name,
            radio.picUrl.orEmpty(),
            Modifier.fillMaxWidth(),
            subtitle = if (radio.programCount > 0) "${radio.programCount} 个节目" else radio.dj?.nickname.orEmpty(),
        ) {
            Screen.PodcastDetail.navigate(nav) { addPath(radio.id.toString()) }
        }
    }
}
