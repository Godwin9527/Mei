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
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
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
) {
    val sizeText: String
        get() = if (sizeBytes <= 0) "" else String.format("%.1f MB", sizeBytes / 1024.0 / 1024.0)
}

/**
 * 自动更新：查 GitHub Releases 最新版本、下载 APK、交给系统安装器。
 *
 * 注意：这里用独立的 OkHttpClient，不能复用注入的那个——它带网易云的拦截器，
 * 会把 GET 请求改写成 POST 并加密，GitHub 接口会直接失败。
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

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    suspend fun latestRelease(): Resource<AppRelease> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$API_BASE/repos/$OWNER/$REPO/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Mei-Android")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Resource.Error(
                        when (response.code) {
                            403, 429 -> "查询过于频繁，请稍后再试"
                            404 -> "还没有发布版本"
                            else -> "检查更新失败（HTTP ${response.code}）"
                        }
                    )
                }
                val body = response.body.string()
                val dto = json.decodeFromString<ReleaseDto>(body)
                val asset = pickAsset(dto.assets)
                    ?: return@withContext Resource.Error("最新版本里没有可安装的 APK")
                val tag = dto.tag_name.orEmpty()
                Resource.Success(
                    AppRelease(
                        tag = tag,
                        versionName = tag.removePrefix("v").removePrefix("V"),
                        notes = dto.body.orEmpty().trim(),
                        assetName = asset.name,
                        downloadUrl = asset.browser_download_url,
                        sizeBytes = asset.size,
                    )
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Timber.tag("AppUpdate").w("Check failed: %s", e.javaClass.simpleName)
            Resource.Error("检查更新失败，请检查网络")
        }
    }

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

    /** 下载到应用私有目录（FileProvider 已覆盖 filesDir），返回本地文件。 */
    suspend fun download(release: AppRelease, onProgress: (Int) -> Unit): Resource<File> =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.filesDir, "updates").apply { mkdirs() }
                // 清掉旧包，避免占空间。
                dir.listFiles()?.forEach { if (it.name.endsWith(".apk")) it.delete() }
                val target = File(dir, "mei-${release.tag}.apk")
                val request = Request.Builder()
                    .url(release.downloadUrl)
                    .header("User-Agent", "Mei-Android")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Resource.Error("下载失败（HTTP ${response.code}）")
                    }
                    val body = response.body
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var written = 0L
                            var lastPercent = -1
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                written += read
                                if (total > 0) {
                                    val percent = (written * 100 / total).toInt()
                                    if (percent != lastPercent) {
                                        lastPercent = percent
                                        onProgress(percent)
                                    }
                                }
                            }
                        }
                    }
                    if (total > 0 && target.length() < total) {
                        target.delete()
                        return@withContext Resource.Error("下载不完整，请重试")
                    }
                    onProgress(100)
                    Resource.Success(target)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Timber.tag("AppUpdate").w("Download failed: %s", e.javaClass.simpleName)
                Resource.Error("下载失败，请重试")
            }
        }

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSetting() {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { Timber.tag("AppUpdate").w("Cannot open install settings") }
    }

    /** 调起系统安装器。Android 不允许普通应用静默安装，这一次确认必须由用户点。 */
    fun installApk(file: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }.onFailure { Timber.tag("AppUpdate").w(it, "Install intent failed") }.isSuccess

    companion object {
        private const val OWNER = "Godwin9527"
        private const val REPO = "Mei"
        private const val API_BASE = "https://api.github.com"

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
