package com.ljyh.mei.ui.screen.podcast

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ljyh.mei.constants.PodcastSortKey
import com.ljyh.mei.data.model.podcast.DjProgram
import com.ljyh.mei.data.model.podcast.DjRadio
import com.ljyh.mei.data.model.podcast.RadioPrograms
import com.ljyh.mei.data.network.Resource
import com.ljyh.mei.data.repository.PodcastRepository
import com.ljyh.mei.data.model.room.PlaybackProgress
import com.ljyh.mei.di.repository.PlaybackProgressRepository
import com.ljyh.mei.utils.preferences.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 节目排序方式。
 *
 * 「集数」按标题里的数字做自然排序（"第7部 132" 与 "第7部 42" 按 42 < 132 比较），
 * 评书类节目常用数字序号，字典序会把 132 排到 42 前面。
 */
enum class PodcastSort(val label: String) {
    OLDEST("最早在前"),
    NEWEST("最新在前"),
    NUMBER_ASC("集数正序"),
    NUMBER_DESC("集数倒序"),
}

data class PodcastProgramsUiState(
    val radio: DjRadio? = null,
    val programs: List<DjProgram> = emptyList(),
    val loading: Boolean = true,
    /** 后台仍在补全更早/更晚的节目。 */
    val loadingAll: Boolean = false,
    val loaded: Int = 0,
    val total: Int = 0,
    val error: String? = null,
    /** 命中频控后的冷却剩余秒数，大于 0 时不允许再点重试。 */
    val retryInSeconds: Int = 0,
    val query: String = "",
    val sort: PodcastSort = PodcastSort.OLDEST,
    /** songId → 这一集的收听进度，用于列表上标注「已听 32:10 / 已听完」。 */
    val progressBySong: Map<String, PlaybackProgress> = emptyMap(),
    /** 整个电台里已听完的集数（不受搜索框影响），用于头部「已播 M 集」。 */
    val completedCount: Int = 0,
)

@HiltViewModel
class PodcastViewModel @Inject constructor(
    private val repository: PodcastRepository,
    private val progressRepository: PlaybackProgressRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _radios = MutableStateFlow<Resource<List<DjRadio>>>(Resource.Loading)
    val radios: StateFlow<Resource<List<DjRadio>>> = _radios.asStateFlow()

    private val _programs = MutableStateFlow(PodcastProgramsUiState())
    val programs: StateFlow<PodcastProgramsUiState> = _programs.asStateFlow()

    private var rawPrograms: List<DjProgram> = emptyList()
    private var radioId: Long = 0L
    private var radio: DjRadio? = null
    private var sort: PodcastSort = PodcastSort.OLDEST
    private var query: String = ""
    private var total: Int = 0
    private var job: Job? = null
    private var cooldownJob: Job? = null
    private var sortLoaded = false

    /** 最近一次的进度快照；切电台重建 UiState 时要带上，否则列表会先闪一下「全部未播放」。 */
    private var progressMap: Map<String, PlaybackProgress> = emptyMap()

    init {
        viewModelScope.launch {
            progressRepository.observeBySong().collectLatest { map ->
                progressMap = map
                _programs.value = _programs.value.copy(
                    progressBySong = map,
                    completedCount = countCompleted(map),
                )
            }
        }
        viewModelScope.launch {
            val saved = context.dataStore.data.first()[PodcastSortKey]
            sortLoaded = true
            val restored = saved?.let { name -> PodcastSort.entries.firstOrNull { it.name == name } }
            if (restored == null || restored == sort) return@launch
            sort = restored
            if (rawPrograms.isNotEmpty()) publish()
        }
    }

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
        radio = null
        query = ""
        total = 0
        _programs.value = PodcastProgramsUiState(
            sort = sort,
            loading = true,
            progressBySong = progressMap,
        )
        reload()
    }

    fun setSort(next: PodcastSort) {
        if (sort == next) return
        sort = next
        viewModelScope.launch { context.dataStore.edit { it[PodcastSortKey] = next.name } }
        if (rawPrograms.isEmpty()) {
            _programs.value = _programs.value.copy(sort = sort)
            return
        }
        publish()
    }

    fun setQuery(text: String) {
        if (query == text) return
        query = text
        if (rawPrograms.isEmpty()) {
            _programs.value = _programs.value.copy(query = text)
            return
        }
        publish()
    }

    fun reload() {
        if (radioId <= 0) return
        if (_programs.value.retryInSeconds > 0) return
        job?.cancel()
        _programs.value = _programs.value.copy(loading = true, error = null)
        job = viewModelScope.launch {
            // 统一按「最早在前」把节目全部拉完：排序和搜索只在本地做，
            // 切换排序不用重新请求，也不会再出现最早的几集翻不到的问题。
            // 一页 500 集：161 集的电台一次就能拉完，避免短时间连发 4 次请求被频控。
            val collected = LinkedHashMap<String, DjProgram>()
            var offset = 0
            var pageIndex = 0
            var failure: String? = null
            while (pageIndex < MAX_PAGES) {
                val result = fetchPage(offset)
                when (result) {
                    is Resource.Success -> {
                        val page = result.data.programs
                        if (result.data.count > 0) total = result.data.count
                        page.forEach { program ->
                            val key = program.mainSong?.id?.takeIf { it > 0 }?.toString()
                                ?: "program_${program.id}"
                            collected[key] = program
                        }
                        rawPrograms = repository.playable(collected.values.toList())
                        radio = rawPrograms.firstOrNull()?.radio ?: radio
                        publish(loadingAll = result.data.more)
                        if (!result.data.more || page.isEmpty()) break
                        offset += page.size
                        pageIndex++
                        delay(PAGE_INTERVAL_MS)
                    }

                    is Resource.Error -> {
                        failure = result.message
                        break
                    }

                    Resource.Loading -> break
                }
            }
            rawPrograms = repository.playable(collected.values.toList())
            radio = rawPrograms.firstOrNull()?.radio ?: radio
            if (failure != null) {
                if (rawPrograms.isEmpty()) {
                    _programs.value = _programs.value.copy(
                        loading = false,
                        loadingAll = false,
                        error = failure,
                    )
                } else {
                    // 已经拉到一部分就先留着，只在底部提示，避免整页被错误面板顶掉。
                    publish(loadingAll = false)
                    _programs.value = _programs.value.copy(error = failure)
                }
                if (isRateLimited(failure)) startCooldown()
            } else {
                publish(loadingAll = false)
            }
        }
    }

    /** 自动补全被中断时，手动再试一次。 */
    fun loadMore() {
        if (job?.isActive == true) return
        if (_programs.value.retryInSeconds > 0) return
        if (rawPrograms.size >= MAX_PAGES * PAGE_SIZE) return
        reload()
    }

    /**
     * 取一页节目。网络类错误退避重试两次——播客页最常见的失败就是移动网络瞬断，
     * 直接弹「加载失败」会让人以为电台挂了。
     */
    private suspend fun fetchPage(offset: Int): Resource<RadioPrograms> {
        var attempt = 0
        while (true) {
            val result = repository.programs(radioId, PAGE_SIZE, offset, asc = true)
            val message = (result as? Resource.Error)?.message
            if (message == null || attempt >= RETRY_DELAYS_MS.size || !isRetryable(message)) {
                return result
            }
            delay(RETRY_DELAYS_MS[attempt])
            attempt++
        }
    }

    private fun isRetryable(message: String): Boolean =
        !isRateLimited(message) && !message.contains("登录") && !message.contains("权限")

    private fun isRateLimited(message: String): Boolean = message.contains("频繁")

    /** 命中频控后进入冷却，冷却期间点重试不发请求，避免把限流拖得更久。 */
    private fun startCooldown() {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            for (left in COOLDOWN_SECONDS downTo 1) {
                _programs.value = _programs.value.copy(retryInSeconds = left)
                delay(1000)
            }
            _programs.value = _programs.value.copy(retryInSeconds = 0)
        }
    }

    /** 清掉这个电台下所有单集的播放记忆。 */
    fun clearRadioProgress(onDone: (Int) -> Unit = {}) {
        val ids = rawPrograms.mapNotNull { it.mainSong?.id?.takeIf { id -> id > 0 }?.toString() }
        if (ids.isEmpty()) {
            onDone(0)
            return
        }
        viewModelScope.launch {
            progressRepository.clearFor(ids)
            onDone(ids.size)
        }
    }

    private fun publish(loadingAll: Boolean = false) {
        val keyword = query.trim()
        val filtered = if (keyword.isBlank()) {
            rawPrograms
        } else {
            rawPrograms.filter { it.name.contains(keyword, ignoreCase = true) }
        }
        _programs.value = _programs.value.copy(
            radio = radio,
            programs = ordered(filtered),
            loading = false,
            loadingAll = loadingAll,
            loaded = rawPrograms.size,
            total = maxOf(total, rawPrograms.size),
            error = null,
            query = query,
            sort = sort,
            completedCount = countCompleted(_programs.value.progressBySong),
        )
    }

    private fun countCompleted(progress: Map<String, PlaybackProgress>): Int =
        rawPrograms.count { program ->
            val id = program.mainSong?.id?.takeIf { it > 0 }?.toString()
            id != null && progress[id]?.completed == true
        }

    private fun ordered(list: List<DjProgram>): List<DjProgram> = when (sort) {
        PodcastSort.OLDEST -> list.sortedBy { it.createTime }
        PodcastSort.NEWEST -> list.sortedByDescending { it.createTime }
        PodcastSort.NUMBER_ASC -> list.sortedWith(Comparator { a, b -> compareNatural(a.name, b.name) })
        PodcastSort.NUMBER_DESC -> list.sortedWith(Comparator { a, b -> compareNatural(b.name, a.name) })
    }

    private companion object {
        /** 一页拉 500 集：161 集的电台一次到位，不用连发多次。 */
        const val PAGE_SIZE = 500
        const val MAX_PAGES = 3
        const val PAGE_INTERVAL_MS = 400L
        val RETRY_DELAYS_MS = longArrayOf(800L, 2_000L)
        const val COOLDOWN_SECONDS = 30
    }
}

/**
 * 数字感知的字符串比较：连续数字按整数大小比较，其余字符忽略大小写。
 * 让 "第7部 042" < "第7部 132" 成立。
 */
internal fun compareNatural(a: String, b: String): Int {
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        val ca = a[i]
        val cb = b[j]
        if (ca.isDigit() && cb.isDigit()) {
            var endA = i
            while (endA < a.length && a[endA].isDigit()) endA++
            var endB = j
            while (endB < b.length && b[endB].isDigit()) endB++
            val digitsA = a.substring(i, endA).trimStart('0')
            val digitsB = b.substring(j, endB).trimStart('0')
            val byLength = digitsA.length - digitsB.length
            if (byLength != 0) return byLength
            val byValue = digitsA.compareTo(digitsB)
            if (byValue != 0) return byValue
            i = endA
            j = endB
        } else {
            val byChar = ca.lowercaseChar().compareTo(cb.lowercaseChar())
            if (byChar != 0) return byChar
            i++
            j++
        }
    }
    return (a.length - i) - (b.length - j)
}
