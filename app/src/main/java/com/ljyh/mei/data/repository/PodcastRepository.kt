package com.ljyh.mei.data.repository

import com.ljyh.mei.data.model.podcast.DjRadio
import com.ljyh.mei.data.model.podcast.DjProgram
import com.ljyh.mei.data.model.podcast.RadioPrograms
import com.ljyh.mei.data.network.Resource
import com.ljyh.mei.data.network.api.WeApiService
import com.ljyh.mei.data.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

/** 播客（电台）数据源。只依赖 weapi，必须已登录。 */
@Singleton
class PodcastRepository @Inject constructor(
    private val weApiService: WeApiService,
) {
    suspend fun subscribedRadios(limit: Int = 100, offset: Int = 0): Resource<List<DjRadio>> =
        safeApiCall {
            val response = weApiService.getSubscribedRadios(limit = limit, offset = offset)
            if (response.code == 200 || response.code == 0) {
                response.items.filter { it.id > 0 }
            } else {
                throw IllegalStateException(
                    if (response.code == 301) "请先登录网易云账号" else "获取播客列表失败 (${response.code})"
                )
            }
        }

    suspend fun programs(
        radioId: Long,
        limit: Int = 30,
        offset: Int = 0,
        asc: Boolean = false,
    ): Resource<RadioPrograms> = safeApiCall {
        val response = weApiService.getRadioPrograms(
            radioId = radioId, limit = limit, offset = offset, asc = asc,
        )
        if (response.code == 200 || response.code == 0) response
        else throw IllegalStateException("获取节目列表失败 (${response.code})")
    }

    /** 只保留能真正播放的节目：必须有 mainSong.id。 */
    fun playable(programs: List<DjProgram>): List<DjProgram> =
        programs.filter { (it.mainSong?.id ?: 0) > 0 }
}
