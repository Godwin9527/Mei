package com.ljyh.mei.playback.queue

import com.ljyh.mei.data.model.domain.MediaMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** The current song includes its metadata so the mini player is usable before network requests. */
@Serializable
internal data class PlaybackQueueSnapshot(
    val ids: List<String>,
    val currentIndex: Int = 0,
    val positionMs: Long = 0,
    val currentSong: MediaMetadata? = null,
    val isFmMode: Boolean = false,
    /** Null in snapshots written before per-song sources were introduced. */
    val entries: List<QueueEntrySnapshot>? = null,
    /**
     * 播客队列里每个节目的元数据。
     *
     * 播客的 `mainSong.id` 拿去 `/song/detail` 查不到标题和封面（只有时长），
     * 所以恢复时不能像普通歌曲那样只留 id 等接口补全，否则队列里会出现
     * 「没有图标、没有标题、只有一个时长」的空行。整队列一起存下来，
     * 体积约 25KB（161 集），只在播客队列里才有这个字段。
     */
    val podcasts: List<PodcastQueueSong>? = null,
)

@Serializable
internal data class QueueEntrySnapshot(val entryId: String, val source: QueueSource? = null)

/** 恢复队列用的播客节目元数据，字段刻意保持精简。 */
@Serializable
internal data class PodcastQueueSong(
    val id: String,
    val title: String,
    val cover: String = "",
    val artist: String = "",
    val album: String = "",
    val duration: Long = 0,
    val artistId: Long = 0,
)

internal fun PodcastQueueSong.toMediaMetadata(): MediaMetadata {
    val songId = id.toLongOrNull() ?: 0L
    return MediaMetadata(
        id = songId,
        title = title,
        coverUrl = cover,
        artists = listOf(
            MediaMetadata.Artist(id = artistId, name = artist.ifBlank { "网易云播客" })
        ),
        duration = duration,
        album = MediaMetadata.Album(id = 0, title = album),
        isPodcast = true,
        neteaseId = songId,
    )
}

internal object PlaybackQueueSnapshotCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(snapshot: PlaybackQueueSnapshot): String = json.encodeToString(snapshot)

    fun decode(value: String?): PlaybackQueueSnapshot? = runCatching {
        value?.let { json.decodeFromString<PlaybackQueueSnapshot>(it) }
            ?.takeIf { snapshot ->
                snapshot.ids.isNotEmpty() &&
                    snapshot.ids.all { it.isNotBlank() } &&
                    snapshot.currentIndex in snapshot.ids.indices &&
                    snapshot.positionMs >= 0L &&
                    (snapshot.entries == null || (snapshot.entries.size == snapshot.ids.size &&
                        snapshot.entries.all { it.entryId.isNotBlank() })) &&
                    (snapshot.podcasts == null || (snapshot.podcasts.isNotEmpty() &&
                        snapshot.podcasts.all { it.id.isNotBlank() })) &&
                    (snapshot.currentSong == null ||
                        snapshot.currentSong.id.toString() == snapshot.ids[snapshot.currentIndex])
            }
            ?.let { snapshot ->
                // Older local queue snapshots have only an internal numeric song ID.
                // Without an explicitly saved catalogue ID, do not turn it into a public link.
                val local = snapshot.entries?.getOrNull(snapshot.currentIndex)?.source?.type == QueueSource.Type.LOCAL
                val song = snapshot.currentSong
                if (local && song != null &&
                    (json.parseToJsonElement(requireNotNull(value)).jsonObject["currentSong"] as? JsonObject)
                        ?.containsKey("neteaseId") != true
                ) snapshot.copy(currentSong = song.copy(neteaseId = 0L)) else snapshot
            }

    }.getOrNull()
}
