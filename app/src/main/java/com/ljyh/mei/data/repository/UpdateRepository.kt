package com.ljyh.mei.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.ljyh.mei.BuildConfig
import com.ljyh.mei.data.network.Resource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class AppRelease(
    val tag: String,
    val versionName: String,
    val notes: String,
    val assetName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    /** 数据取自本地缓存时的抓取时间（毫秒）；0 表示本次实时查询。 */
    val cachedAt: Long = 0,
) {
    val sizeText: String
        get() = if (sizeBytes <= 0) "" else String.format("%.1f MB", sizeBytes / 1024.0 / 1024.0)

    val fromCache: Boolean get() = cachedAt > 0
}

/** 下载进度。慢网下界面需要看到速度和当前源，否则用户分不清是在走还是卡死。 */
data class DownloadProgress(
    val percent: Int,
    val bytesPerSecond: Long,
    val source: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    /** 本地已有部分文件，本次是接着下。 */
    val resumed: Boolean = false,
)

/**
 * 自动更新：查 GitHub Releases 最新版本、下载 APK、交给系统安装器。
 *
 * 这里刻意用独立的 OkHttpClient，不能复用注入的那个——它带网易云的拦截器，
 * 会把 GET 请求改写成 POST 并加密，GitHub 接口会直接失败。
 *
 * 国内直连 GitHub 下载 Release 资产通常只有 15~30 KB/s，22MB 要十几分钟，
 * 所以下载拆成两件事：
 * 1. **多源**：先并发探测几个公共加速前缀，按实测吞吐排序，从最快的开始；
 *    中途速度过低自动换下一个源，已下载字节保留。
 * 2. **续传**：用 `Range` 写 `.part` 文件，断网或退出后再进来接着下，不再从 0 开始。
 *
 * 查询接口另有额度限制（未登录 60 次/小时/IP），所以结果缓存 10 分钟，并读取
 * `X-RateLimit-Reset` 把「请稍后再试」换成具体时间。
 */
@Singleton
class UpdateRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 探测专用：短超时，避免某个死源把排序拖到半分钟。 */
    private val probeClient = client.newBuilder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    // ---------------------------------------------------------------- 查询版本

    suspend fun latestRelease(force: Boolean = false): Resource<AppRelease> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cache = readCache()
        if (cache != null && cache.tag.isNotBlank()) {
            val age = now - cache.fetchedAt
            // 10 分钟内一律用缓存；点「重新检查」也至少间隔 30 秒，避免连点耗光额度。
            val usable = when {
                age < 0 -> false
                !force -> age < CACHE_TTL_MS
                else -> age < MIN_FORCE_INTERVAL_MS
            }
            if (usable) return@withContext cache.toRelease(cached = true)
        }

        try {
            val request = Request.Builder()
                .url("$API_BASE/repos/$OWNER/$REPO/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Resource.Error(describeHttpError(response))
                }
                val dto = json.decodeFromString<ReleaseDto>(response.body.string())
                if (pickAsset(dto.assets) == null) {
                    return@withContext Resource.Error("最新版本里没有可安装的 APK")
                }
                val snapshot = UpdateCache(
                    fetchedAt = System.currentTimeMillis(),
                    tag = dto.tag_name.orEmpty(),
                    notes = dto.body.orEmpty().trim(),
                    assets = dto.assets,
                )
                writeCache(snapshot)
                snapshot.toRelease(cached = false)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Timber.tag(TAG).w("Check failed: %s", e.javaClass.simpleName)
            // 查询失败但有缓存时退回缓存，至少让用户看到上次的结果。
            if (cache != null && cache.tag.isNotBlank()) {
                return@withContext cache.toRelease(cached = true)
            }
            Resource.Error("检查更新失败，请检查网络")
        }
    }

    /** 把 HTTP 状态码翻译成用户能照着做的提示。 */
    private fun describeHttpError(response: okhttp3.Response): String = when (response.code) {
        403, 429 -> {
            val resetSeconds = response.header("X-RateLimit-Reset")?.toLongOrNull()
            val remaining = response.header("X-RateLimit-Remaining")
            if (resetSeconds != null || remaining == "0") {
                val resetAt = (resetSeconds ?: 0L) * 1000
                if (resetAt > System.currentTimeMillis()) {
                    "GitHub 接口额度已用完（每小时 60 次），${formatClock(resetAt)} 后可重试"
                } else {
                    "GitHub 接口额度刚刚恢复，请再试一次"
                }
            } else {
                "GitHub 暂时拒绝了请求（HTTP 403），换网络或稍后再试"
            }
        }

        404 -> "还没有发布版本"
        else -> "检查更新失败（HTTP ${response.code}）"
    }

    private fun formatClock(epochMs: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))

    /** 按设备 ABI 选包，优先 arm64。 */
    private fun pickAsset(assets: List<ReleaseAssetDto>): ReleaseAssetDto? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (apks.isEmpty()) return null
        val preferred = when {
            Build.SUPPORTED_ABIS.any { it == "arm64-v8a" } -> "arm64-v8a"
            Build.SUPPORTED_ABIS.any { it == "x86_64" } -> "x86_64"
            else -> null
        }
        return apks.firstOrNull { preferred != null && it.name.contains(preferred) } ?: apks.first()
    }

    // ---------------------------------------------------------------- 下载

    /**
     * 下载 APK。多源自动切换 + 断点续传：未下完的内容保留成 `.part`，下次调用接着下；
     * 只有长度校验通过后才改成正式文件名，并清理历史安装包。
     */
    suspend fun download(
        release: AppRelease,
        onProgress: (DownloadProgress) -> Unit,
    ): Resource<File> = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "updates").apply { mkdirs() }
        val target = File(dir, "mei-${release.tag}.apk")
        val part = File(dir, "mei-${release.tag}.apk.part")

        if (isComplete(target, release.sizeBytes)) {
            onProgress(
                DownloadProgress(100, 0, "本地已有安装包", target.length(), target.length())
            )
            return@withContext Resource.Success(target)
        }
        // 上次已经下完但还没改名（例如进程在 rename 前被杀），直接收尾。
        if (isComplete(part, release.sizeBytes)) {
            if (target.exists()) target.delete()
            part.renameTo(target)
            cleanupOldPackages(dir, keep = target)
            onProgress(
                DownloadProgress(100, 0, "已下载完成", target.length(), target.length())
            )
            return@withContext Resource.Success(target)
        }

        try {
            val sources = rankSources(sourcesFor(release))
            var lastError = "没有可用的下载源"
            sources.forEachIndexed { index, source ->
                Timber.tag(TAG).i("download #%d/%d via %s", index + 1, sources.size, source.label)
                when (val attempt = trySource(source, part, release, index, sources.size, onProgress)) {
                    TryResult.Done -> {
                        if (!isComplete(part, release.sizeBytes)) {
                            lastError = "下载不完整（${part.length()} / ${release.sizeBytes} 字节）"
                            return@forEachIndexed
                        }
                        if (target.exists()) target.delete()
                        if (!part.renameTo(target)) {
                            part.copyTo(target, overwrite = true)
                            part.delete()
                        }
                        cleanupOldPackages(dir, keep = target)
                        onProgress(
                            DownloadProgress(
                                percent = 100,
                                bytesPerSecond = 0,
                                source = source.label,
                                downloadedBytes = target.length(),
                                totalBytes = target.length(),
                            )
                        )
                        return@withContext Resource.Success(target)
                    }

                    is TryResult.Switch -> {
                        lastError = attempt.reason
                        Timber.tag(TAG).i("switch away from %s: %s", source.label, attempt.reason)
                    }
                }
            }
            Resource.Error(lastError)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Timber.tag(TAG).w("Download failed: %s", e.javaClass.simpleName)
            Resource.Error("下载失败：${e.message ?: "未知错误"}（已下载部分会保留，可重试续传）")
        }
    }

    /** 下载源：若干公共加速前缀 + 官方直连，官方始终留在最后兜底。 */
    private fun sourcesFor(release: AppRelease): List<Source> =
        MIRRORS.map { Source(it.label, it.prefix + release.downloadUrl) } +
            Source("直连 GitHub", release.downloadUrl)

    /**
     * 并发探测各源实际吞吐，按实测速率从高到低排序；探测失败的排到最后
     * （仍会尝试，可能只是瞬时抖动）。
     */
    private suspend fun rankSources(sources: List<Source>): List<Source> = coroutineScope {
        val results = sources.map { source ->
            async(Dispatchers.IO) { source to probe(source) }
        }.awaitAll()
        Timber.tag(TAG).i(
            "probe: %s",
            results.joinToString { "${it.first.label}=${it.second}B/s" },
        )
        val alive = results.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
        val dead = results.filter { it.second <= 0 }.map { it.first }
        alive + dead
    }

    /**
     * 最多读 [PROBE_BYTES]，返回实测速率（字节/秒）。
     *
     * 不能直接拿「读到的字节数」当度量：只要没触到 6 秒超时，15 KB/s 的源也能读满窗口，
     * 排序就失去区分度。所以这里统计耗时折算成 B/s。
     */
    private suspend fun probe(source: Source): Long = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(source.url)
                .header("User-Agent", USER_AGENT)
                .header("Range", "bytes=0-${PROBE_BYTES - 1}")
                .build()
            probeClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext 0L
                val startedAt = System.currentTimeMillis()
                var got = 0L
                val buffer = ByteArray(16 * 1024)
                response.body.byteStream().use { input ->
                    while (got < PROBE_BYTES) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        got += read
                    }
                }
                val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(1)
                // 读到的是错误页（体积远小于探测窗口）时视为不可用。
                if (got < MIN_PROBE_BYTES) 0L else got * 1000 / elapsed
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            0L
        }
    }

    private sealed interface TryResult {
        data object Done : TryResult
        data class Switch(val reason: String) : TryResult
    }

    /**
     * 用单个源写 `.part`。已有内容时带 `Range` 续传；每 [SPEED_WINDOW_MS] 统计一次速度，
     * 低于阈值就返回 [TryResult.Switch] 让上层换源（已下字节保留）。
     */
    private suspend fun trySource(
        source: Source,
        part: File,
        release: AppRelease,
        index: Int,
        sourceCount: Int,
        onProgress: (DownloadProgress) -> Unit,
    ): TryResult = withContext(Dispatchers.IO) {
        var existing = if (part.exists()) part.length() else 0L
        val expected = release.sizeBytes
        if (expected > 0 && existing > expected) {
            part.delete()
            existing = 0
        }
        val builder = Request.Builder()
            .url(source.url)
            .header("User-Agent", USER_AGENT)
        if (existing > 0) builder.header("Range", "bytes=$existing-")

        try {
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext TryResult.Switch("HTTP ${response.code}")
                }
                val append = response.code == 206 && existing > 0
                val remaining = response.body.contentLength()
                val totalBytes = when {
                    expected > 0 -> expected
                    append && remaining > 0 -> existing + remaining
                    else -> remaining
                }
                var written = if (append) existing else 0L

                val raf = RandomAccessFile(part, "rw")
                try {
                    if (append) raf.seek(existing) else raf.setLength(0L)
                    var windowAt = System.currentTimeMillis()
                    var windowBytes = 0L
                    var lastPercent = -1
                    var lastSpeed = 0L

                    response.body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            raf.write(buffer, 0, read)
                            written += read
                            windowBytes += read

                            val percent =
                                if (totalBytes > 0) ((written * 100) / totalBytes).toInt().coerceIn(0, 100)
                                else 0
                            val now = System.currentTimeMillis()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(
                                    DownloadProgress(
                                        percent = percent,
                                        bytesPerSecond = lastSpeed,
                                        source = source.label,
                                        downloadedBytes = written,
                                        totalBytes = totalBytes,
                                        resumed = append,
                                    )
                                )
                            }

                            val elapsed = now - windowAt
                            if (elapsed >= SPEED_WINDOW_MS) {
                                val speed = windowBytes * 1000 / elapsed.coerceAtLeast(1)
                                lastSpeed = speed
                                onProgress(
                                    DownloadProgress(
                                        percent = percent,
                                        bytesPerSecond = speed,
                                        source = source.label,
                                        downloadedBytes = written,
                                        totalBytes = totalBytes,
                                        resumed = append,
                                    )
                                )
                                // 还有别的源可试且当前明显过慢 —— 换源，已下字节保留。
                                val hasFallback = index < sourceCount - 1
                                val unfinished = totalBytes <= 0 || written < totalBytes
                                if (hasFallback && unfinished && speed < MIN_SPEED_BPS) {
                                    return@withContext TryResult.Switch(
                                        "当前源过慢（${speed / 1024} KB/s）"
                                    )
                                }
                                windowAt = now
                                windowBytes = 0
                            }
                        }
                    }
                } finally {
                    raf.close()
                }

                if (totalBytes > 0 && written < totalBytes) {
                    return@withContext TryResult.Switch("连接中断（$written / $totalBytes）")
                }
                TryResult.Done
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: IOException) {
            TryResult.Switch("网络中断：${e.message ?: "IO 异常"}")
        }
    }

    private fun isComplete(file: File, expected: Long): Boolean =
        file.exists() && file.length() > 0 && (expected <= 0 || file.length() == expected)

    /** 只有下载成功后才清理历史安装包，避免把还能续传的 `.part` 顺手删掉。 */
    private fun cleanupOldPackages(dir: File, keep: File) {
        dir.listFiles()?.forEach { file ->
            if (file.name == keep.name) return@forEach
            if (file.name.endsWith(".apk") || file.name.endsWith(".apk.part")) {
                runCatching { file.delete() }
            }
        }
    }

    // ---------------------------------------------------------------- 缓存

    private fun readCache(): UpdateCache? = runCatching {
        val file = cacheFile()
        if (!file.exists()) return@runCatching null
        json.decodeFromString<UpdateCache>(file.readText())
    }.getOrNull()

    private fun writeCache(cache: UpdateCache) {
        runCatching {
            cacheFile().writeText(json.encodeToString(cache))
        }.onFailure { Timber.tag(TAG).w(it, "write cache failed") }
    }

    private fun cacheFile() = File(context.filesDir, "update-cache.json")

    private fun UpdateCache.toRelease(cached: Boolean): Resource<AppRelease> {
        val asset = pickAsset(assets)
            ?: return Resource.Error("最新版本里没有可安装的 APK")
        return Resource.Success(
            AppRelease(
                tag = tag,
                versionName = tag.removePrefix("v").removePrefix("V"),
                notes = notes,
                assetName = asset.name,
                downloadUrl = asset.browser_download_url,
                sizeBytes = asset.size,
                cachedAt = if (cached) fetchedAt else 0,
            )
        )
    }

    // ---------------------------------------------------------------- 安装

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSetting() {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { Timber.tag(TAG).w("Cannot open install settings") }
    }

    /** 调起系统安装器。Android 不允许普通应用静默安装，最后一次确认必须由用户点。 */
    fun installApk(file: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }.onFailure { Timber.tag(TAG).w(it, "Install intent failed") }.isSuccess

    private data class Source(val label: String, val url: String)

    private data class Mirror(val label: String, val prefix: String)

    companion object {
        private const val TAG = "AppUpdate"
        private const val OWNER = "Godwin9527"
        private const val REPO = "Mei"
        private const val API_BASE = "https://api.github.com"
        private const val USER_AGENT = "Mei-Android"

        /** 探测窗口：读这么多字节来估速度。 */
        private const val PROBE_BYTES = 48L * 1024
        private const val MIN_PROBE_BYTES = 8L * 1024

        /** 速度统计窗口，太短会被 TCP 慢启动干扰。 */
        private const val SPEED_WINDOW_MS = 8_000L

        /** 低于这个速度就换下一个源（12 KB/s）。 */
        private const val MIN_SPEED_BPS = 12L * 1024

        /** 查询结果缓存 10 分钟；手动重查的最小间隔 30 秒。 */
        private const val CACHE_TTL_MS = 10 * 60 * 1000L
        private const val MIN_FORCE_INTERVAL_MS = 30 * 1000L

        /**
         * 公共加速反代。都是社区服务，随时可能失效，所以顺序由探测决定、
         * 失败会自动跳下一个，官方直连始终在最后兜底。
         */
        private val MIRRORS = listOf(
            Mirror("ghfast.top", "https://ghfast.top/"),
            Mirror("ghproxy.net", "https://ghproxy.net/"),
            Mirror("gh-proxy.com", "https://gh-proxy.com/"),
            Mirror("moeyy.xyz", "https://github.moeyy.xyz/"),
        )

        /** 判断 [remote] 是否比 [current] 新。 */
        fun isNewer(remote: String, current: String): Boolean {
            val a = remote.trim().removePrefix("v").removePrefix("V").split('.', '-', '+')
            val b = current.trim().removePrefix("v").removePrefix("V").split('.', '-', '+')
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrNull(i)?.takeWhile(Char::isDigit)?.toIntOrNull() ?: 0
                val y = b.getOrNull(i)?.takeWhile(Char::isDigit)?.toIntOrNull() ?: 0
                if (x != y) return x > y
            }
            return false
        }
    }
}

@Serializable
private data class ReleaseDto(
    val tag_name: String? = null,
    val body: String? = null,
    val assets: List<ReleaseAssetDto> = emptyList(),
)

@Serializable
private data class ReleaseAssetDto(
    val name: String = "",
    val size: Long = 0,
    val browser_download_url: String = "",
)

@Serializable
private data class UpdateCache(
    val fetchedAt: Long = 0,
    val tag: String = "",
    val notes: String = "",
    val assets: List<ReleaseAssetDto> = emptyList(),
)
