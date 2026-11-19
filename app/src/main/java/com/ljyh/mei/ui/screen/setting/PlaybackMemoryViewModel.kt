package com.ljyh.mei.ui.screen.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ljyh.mei.di.repository.PlaybackProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** 播放记忆的全局操作，目前只有「全部清除」。 */
@HiltViewModel
class PlaybackMemoryViewModel @Inject constructor(
    private val repository: PlaybackProgressRepository,
) : ViewModel() {

    fun clearAll(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.clearAll()
            onDone()
        }
    }
}
