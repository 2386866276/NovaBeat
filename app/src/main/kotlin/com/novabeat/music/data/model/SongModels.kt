package com.novabeat.music.data.model

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val coverUrl: String,
    val url: String,
    val durationMs: Long,
    val format: String = "mp3",
    val bitrate: Int = 320,
    val isLocal: Boolean = false,
    val filePath: String = "",
    val source: String = "local"
)

data class Playlist(
    val id: String,
    val name: String,
    val coverUrl: String,
    val songs: List<Song> = emptyList(),
    val songCount: Int = songs.size,
    val isSystem: Boolean = false,
    val description: String = ""
)

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val translation: String = ""
)

data class LyricResult(
    val lines: List<LyricLine> = emptyList(),
    val rawLrc: String = "",
    val rawTlyric: String = ""
)

data class NeteaseSearchResult(
    val code: Int = 0,
    val result: NeteaseResultData? = null
)

data class NeteaseResultData(
    val songs: List<NeteaseSong>? = emptyList(),
    val songCount: Int = 0
)

data class NeteaseSong(
    val id: Long = 0,
    val name: String = "",
    val artists: List<NeteaseArtist>? = emptyList(),
    val album: NeteaseAlbum? = null,
    val duration: Int = 0
) {
    fun toSong(cookie: String = ""): Song = Song(
        id = id.toString(),
        title = name,
        artist = artists?.joinToString("、") { it.name } ?: "未知",
        album = album?.name ?: "",
        coverUrl = album?.picUrl ?: "",
        url = "",
        durationMs = duration.toLong(),
        source = "netease",
        isLocal = false
    )
}

data class NeteaseArtist(
    val name: String = ""
)

data class NeteaseAlbum(
    val name: String = "",
    val picUrl: String = ""
)

data class MusicUrlResult(
    val code: Int = 0,
    val data: List<MusicUrlData>? = emptyList()
)

data class MusicUrlData(
    val url: String = "",
    val code: Int = 200,
    val type: String = "mp3"
)

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentSong: Song? = null,
    val currentPositionMs: Long = 0,
    val durationMs: Long = 0,
    val playbackMode: PlaybackMode = PlaybackMode.SEQUENCE,
    val isShuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1
)

enum class PlaybackMode {
    SEQUENCE, LOOP_ONE, LOOP_ALL, SHUFFLE
}

enum class RepeatMode {
    OFF, ONE, ALL
}

data class EqualizerPreset(
    val name: String,
    val bands: List<Float>
)

data class VisualizerConfig(
    val type: VisualizerType = VisualizerType.BARS,
    val color: Int = 0xFF6200EE.toInt(),
    val isEnabled: Boolean = true
)

enum class VisualizerType {
    BARS, WAVE, CIRCLE, NONE
}

data class RemoteServer(
    val id: String,
    val name: String,
    val type: ServerType,
    val host: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
    val path: String = "",
    val isActive: Boolean = false
)

enum class ServerType {
    WEBDAV, SMB, LOCAL
}