package com.ljyh.mei.playback.source

/**
 * 播放缓存 key 的约定：`<mediaId>|<实际音质>|full-v2`，LX/解灰来源再追加 `|lx:<identity>`。
 *
 * 音质位必须记录「实际拿到的音质」，不能记录「请求的音质」。高音质解析失败会降级到
 * standard，两个 level 对应的是不同文件、字节数和码率都不同；如果共用同一个 key，
 * 两份字节会混进同一条缓存，播放器按字节偏移 seek 到的就是另一个文件的位置——表现
 * 为进度和内容错开、进度条提前走满、跳转 30 秒却跳了几分钟。
 *
 * v1 的 key 只记了请求音质，存在上述混用，因此升版本让旧条目自动失效。
 */
object PlaybackCacheKey {
    const val VERSION = "full-v2"

    private const val VERSION_V1 = "full-v1"
    private const val VERSION_V2 = "full-v2"

    fun of(mediaId: String, level: String): String = "$mediaId|${level.lowercase()}|$VERSION"

    fun withIdentity(key: String, identity: String): String = "$key|$identity"

    fun parts(key: String): List<String> = key.split('|', limit = 4)

    fun mediaIdOf(key: String): String? =
        parts(key).firstOrNull()?.takeIf { it.isNotBlank() }

    /** 兼容早期只有 `id|quality` 两段的写法。 */
    fun isPlaybackKey(key: String): Boolean {
        val parts = parts(key)
        if (parts.size < 2 || parts[0].isBlank()) return false
        if (parts.size == 2) return true
        return parts[2] == VERSION_V1 || parts[2] == VERSION_V2
    }

    /** 只有当前版本的条目才能被复用；旧条目字节来源不可控，宁可重下一次。 */
    fun isCurrent(key: String): Boolean = parts(key).getOrNull(2) == VERSION

    /** 旧版本号，字节来源不受控，可以安全丢弃。 */
    fun isLegacy(key: String): Boolean = parts(key).getOrNull(2) == VERSION_V1
}
