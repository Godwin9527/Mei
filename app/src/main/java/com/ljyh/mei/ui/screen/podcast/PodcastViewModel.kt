package com.ljyh.mei.ui.screen.podcast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ljyh.mei.data.model.podcast.DjProgram
import com.ljyh.mei.data.model.podcast.DjRadio
import com.ljyh.mei.data.network.Resource
import com.ljyh.mei.data.repository.PodcastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 节目排序方式。时间排序交给接口（asc），名称排序在本地对已加载的节目重排。 */
enum class PodcastSort(val label: String) {
    NEWEST("最新在前"),
    OLDEST("最早在前"),
    NAME_ASC("名称 A→Z"),
    NAME_DESC("名称 Z→A");

    val isTimeOrder: Boolean get() = this == NEWEST || this == OLDEST
}

data class PodcastProgramsUiState(
    val programs: List<DjProgram> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
    val total: Int = 0,
    val sort: PodcastSort = PodcastSort.NEWEST,
) {
    /** 节目列表里每条都带精简的电台信息，加载出来后用于页头。 */
    val radio: DjRadio? get() = programs.firstOrNull()?.radio
}

@HiltViewModel
class PodcastViewModel @Inject constructor(
    private val repository: PodcastRepository,
) : ViewModel() {

    private val _radios = MutableStateFlow<Resource<List<DjRadio>>>(Resource.Loading)
    val radios: StateFlow<Resource<List<DjRadio>>> = _radios.asStateFlow()

    private val _programs = MutableStateFlow(PodcastProgramsUiState())
    val programs: StateFlow<PodcastProgramsUiState> = _programs.asStateFlow()

    private var rawPrograms: List<DjProgram> = emptyList()
    private var radioId: Long = 0L
    private var sort: PodcastSort = PodcastSort.NEWEST
    private var offset: Int = 0
    private var job: Job? = null

    fun loadRadios(force: Boolean = false) {
        if (!force && _radios.value is Resource.Success) return
        viewModelScope.launch {
            _radios.value = Resource.Loading
            val result = repository.subscribedRadios()
            _radios.value = when (result) {
                is Resource.Success -> Resource.Success(result.data)
                is Resource.Error -> result
                Resource.Loading -> result
            }
        }
    }

    fun openRadio(id: Long) {
        if (radioId == id && rawPrograms.isNotEmpty()) return
        radioId = id
        rawPrograms = emptyList()
        offset = 0
        sort = PodcastSort.NEWEST
        reload()
    }

    fun setSort(next: PodcastSort) {
        if (sort == next) return
        val changedOrder = sort.isTimeOrder || next.isTimeOrder
        sort = next
        if (changedOrder) {
            // 时间顺序由接口决定，翻页才不至于错乱，所以重新拉第一页。
            rawPrograms = emptyList()
            offset = 0
            reload()
        } else {
            emit()
        }
    }

    fun reload() {
        if (radioId <= 0) return
        job?.cancel()
        _programs.value = _programs.value.copy(loading = true, error = null, programs = if (rawPrograms.isEmpty()) emptyList() else _programs.value.programs, sort = sort)
        job = viewModelScope.launch {
            when (val result = repository.programs(radioId, PAGE_SIZE, 0, asc = sort == PodcastSort.OLDEST)) {
                is Resource.Success -> {
                    rawPrograms = repository.playable(result.data.programs)
                    offset = rawPrograms.size
                    _programs.value = PodcastProgramsUiState(
                        programs = sorted(),
                        loading = false,
                        hasMore = result.data.more && result.data.programs.isNotEmpty(),
                        total = result.data.count,
                        sort = sort,
                    )
                }

                is Resource.Error -> _programs.value = _programs.value.copy(
                    loading = false, error = result.message,
                )

                Resource.Loading -> Unit
            }
        }
    }

    fun loadMore() {
        val state = _programs.value
        if (state.loading || state.loadingMore || !state.hasMore || radioId <= 0) return
        _programs.value = state.copy(loadingMore = true)
        viewModelScope.launch {
            when (val result = repository.programs(
                radioId, PAGE_SIZE, offset, asc = sort == PodcastSort.OLDEST,
            )) {
                is Resource.Success -> {
                    val page = repository.playable(result.data.programs)
                    // 接口偶尔重复返回边界条目，按 mainSong.id 去重。
                    val known = rawPrograms.mapNotNull { it.mainSong?.id }.toSet()
                    rawPrograms = rawPrograms + page.filterNot { it.mainSong?.id in known }
                    offset += result.data.programs.size
                    _programs.value = _programs.value.copy(
                        programs = sorted(),
                        loadingMore = false,
                        hasMore = result.data.more && result.data.programs.isNotEmpty(),
                        total = if (result.data.count > 0) result.data.count else _programs.value.total,
                    )
                }

                is Resource.Error -> _programs.value = _programs.value.copy(
                    loadingMore = false, error = result.message,
                )

                Resource.Loading -> Unit
            }
        }
    }

    private fun sorted(): List<DjProgram> = when (sort) {
        PodcastSort.NEWEST -> rawPrograms.sortedByDescending { it.createTime }
        PodcastSort.OLDEST -> rawPrograms.sortedBy { it.createTime }
        PodcastSort.NAME_ASC -> rawPrograms.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) { it.name },
        )
        PodcastSort.NAME_DESC -> rawPrograms.sortedWith(
            compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name },
        )
    }

    private fun emit() {
        _programs.value = _programs.value.copy(programs = sorted(), sort = sort)
    }

    private companion object {
        const val PAGE_SIZE = 30
    }
}
