package com.ljyh.mei.data.model.podcast

import kotlinx.serialization.Serializable

/**
 * 播客（电台）相关响应模型。
 *
 * 两个 id 的区别非常重要：
 * - [DjProgram.id] 是「节目 id」，只用于节目页与分享链接（`/program?id=`）。
 * - [DjProgramMainSong.id] 是「全局歌曲 id」，只有它能取到音频地址。
 * 播放队列里一律使用 mainSong.id。
 */
@Serializable
data class SubscribedRadios(
    val code: Int = 0,
    val count: Int = 0,
    val hasMore: Boolean = false,
    val djRadios: List<DjRadio> = emptyList(),
)

@Serializable
data class DjRadio(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String? = null,
    val desc: String? = null,
    val programCount: Int = 0,
    val subCount: Long = 0,
    val shareCount: Long = 0,
    val category: String? = null,
    val lastProgramCreateTime: Long = 0,
    val dj: DjUser? = null,
)

@Serializable
data class DjUser(
    val userId: Long = 0,
    val nickname: String = "",
    val avatarUrl: String? = null,
)

@Serializable
data class RadioPrograms(
    val code: Int = 0,
    val count: Int = 0,
    val more: Boolean = false,
    val programs: List<DjProgram> = emptyList(),
)

@Serializable
data class DjProgram(
    val id: Long = 0,
    val name: String = "",
    val coverUrl: String? = null,
    /** 节目时长，毫秒 */
    val duration: Long = 0,
    val createTime: Long = 0,
    val serialNum: Int? = null,
    val listenerCount: Long = 0,
    val mainSong: DjProgramSong? = null,
    val radio: DjRadio? = null,
    val dj: DjUser? = null,
)

@Serializable
data class DjProgramSong(
    val id: Long = 0,
    val name: String? = null,
    val duration: Long = 0,
    /** 0 免费、1 VIP、4 购买专辑、8 试听等，播客通常为 0 */
    val fee: Int = 0,
    val album: DjProgramAlbum? = null,
)

@Serializable
data class DjProgramAlbum(
    val id: Long = 0,
    val name: String? = null,
    val picUrl: String? = null,
)
