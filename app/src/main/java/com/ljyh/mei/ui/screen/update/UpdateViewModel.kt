package com.ljyh.mei.ui.screen.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ljyh.mei.data.network.Resource
import com.ljyh.mei.data.repository.AppRelease
import com.ljyh.mei.data.repository.DownloadProgress
import com.ljyh.mei.data.repository.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class UpdateUiState(
    val currentVersion: String = "",
    val checking: Boolean = false,
    /** 有可用的新版本 */
    val release: AppRelease? = null,
    val upToDate: Boolean = false,
    val downloading: Boolean = false,
    val progress: Int = 0,
    /** 下载明细：速度、当前源、已下载字节。慢网下界面要展示这些。 */
    val download: DownloadProgress? = null,
    val downloadedFile: File? = null,
    val error: String? = null,
)

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val repository: UpdateRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(UpdateUiState(currentVersion = repository.currentVersion))
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    /**
     * 查询最新版本。[force] 用于「重新检查」，但仓库层仍限制 30 秒内不重复请求，
     * 防止连点把 GitHub 的每小时 60 次额度耗光。
     */
    fun check(force: Boolean = false) {
        if (_state.value.checking || _state.value.downloading) return
        _state.value = _state.value.copy(checking = true, error = null, upToDate = false)
        viewModelScope.launch {
            when (val result = repository.latestRelease(force)) {
                is Resource.Success -> {
                    val release = result.data
                    val newer = UpdateRepository.isNewer(release.versionName, repository.currentVersion)
                    _state.value = _state.value.copy(
                        checking = false,
                        release = release.takeIf { newer },
                        upToDate = !newer,
                    )
                }

                is Resource.Error -> _state.value = _state.value.copy(
                    checking = false, error = result.message,
                )

                Resource.Loading -> Unit
            }
        }
    }

    fun download() {
        val release = _state.value.release ?: return
        if (_state.value.downloading) return
        _state.value = _state.value.copy(
            downloading = true, progress = 0, download = null, error = null,
        )
        viewModelScope.launch {
            when (val result = repository.download(release) { detail ->
                // 直接套用回调里的百分比，避免与 progress 两个来源不一致。
                _state.value = _state.value.copy(progress = detail.percent, download = detail)
            }) {
                is Resource.Success -> _state.value = _state.value.copy(
                    downloading = false, progress = 100, downloadedFile = result.data,
                )

                is Resource.Error -> _state.value = _state.value.copy(
                    downloading = false, error = result.message,
                )

                Resource.Loading -> Unit
            }
        }
    }

    fun canInstallPackages(): Boolean = repository.canInstallPackages()

    fun openInstallPermissionSetting() = repository.openInstallPermissionSetting()

    /** 返回 false 表示系统安装器没起来。 */
    fun install(): Boolean {
        val file = _state.value.downloadedFile ?: return false
        return repository.installApk(file)
    }
}
