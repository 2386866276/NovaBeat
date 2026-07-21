package com.novabeat.music.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.novabeat.music.data.model.*
import com.novabeat.music.data.remote.BilibiliApiService
import com.novabeat.music.data.remote.KugouApiService
import com.novabeat.music.data.remote.KuwoApiService
import com.novabeat.music.data.remote.NeteaseApiService
import com.novabeat.music.data.remote.QQMusicApiService
import com.novabeat.music.data.remote.QishuiApiService
import com.novabeat.music.data.remote.SpotifyApiService
import com.novabeat.music.data.remote.YouTubeMusicApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class MusicRepository(private val context: Context) {

    companion object {
        private const val TAG = "MusicRepository"
    }

    private val apiService = NeteaseApiService()
    private val biliApi = BilibiliApiService()
    private val qqMusicApi = QQMusicApiService()
    private val kuwoApi = KuwoApiService()
    private val kugouApi = KugouApiService()
    private val qishuiApi = QishuiApiService()
    private val spotifyApi = SpotifyApiService()
    private val youtubeMusicApi = YouTubeMusicApiService()

    // ---------- 本地音乐扫描 ----------
    fun scanLocalMusic(): Flow<List<Song>> = flow {
        val songs = withContext(Dispatchers.IO) {
            val musicList = mutableListOf<Song>()
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.ALBUM_ID
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

            context.contentResolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "未知"
                    val artist = cursor.getString(artistCol) ?: "未知艺术家"
                    val album = cursor.getString(albumCol) ?: ""
                    val durationMs = cursor.getLong(durCol)
                    val albumId = cursor.getLong(albumIdCol)

                    // 构建 content:// URI，兼容 Android 10+（_DATA 列废弃）
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    ).toString()
                    val coverUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()

                    if (durationMs > 5000) {
                        musicList.add(
                            Song(
                                id = "local_$id",
                                title = title,
                                artist = artist,
                                album = album,
                                coverUrl = coverUri,
                                url = contentUri,
                                durationMs = durationMs,
                                format = "MP3",
                                isLocal = true,
                                filePath = contentUri,
                                source = "local"
                            )
                        )
                    }
                }
            }
            musicList
        }
        emit(songs)
    }

    // ---------- 网易云搜索 ----------
    suspend fun searchNetease(songName: String, cookie: String = ""): List<Song> {
        val service = if (cookie.isNotBlank()) apiService.withCookie(cookie) else apiService
        return service.searchMusic(songName).map { it.toSong() }
    }

    // ---------- 获取播放链接 ----------
    suspend fun resolveNeteaseUrl(songId: String, cookie: String = ""): String {
        val service = if (cookie.isNotBlank()) apiService.withCookie(cookie) else apiService
        return service.getMusicUrl(songId).url
    }

    // ---------- 获取专辑封面 ----------
    suspend fun fetchAlbumCover(songId: String, cookie: String = ""): String {
        val service = if (cookie.isNotBlank()) apiService.withCookie(cookie) else apiService
        return service.fetchAlbumCover(songId)
    }

    // ---------- 获取歌词 ----------
    suspend fun fetchLyric(songId: String, cookie: String = ""): LyricResult {
        val service = if (cookie.isNotBlank()) apiService.withCookie(cookie) else apiService
        return service.getLyric(songId)
    }

    // ---------- 随机文案 ----------
    fun getRandomShareText(): String = apiService.getRandomText()

    // ---------- 常用播放列表 ----------
    fun getBuiltInPlaylists(): List<Playlist> = listOf(
        Playlist("favorites", "我喜欢", "", isSystem = true, description = "收藏的歌曲"),
        Playlist("recent", "最近播放", "", isSystem = true, description = "最近收听的歌曲"),
        Playlist("netease_hot", "网易云热歌", "", isSystem = true, description = "网易云音乐热门歌曲")
    )

    // ---------- B站搜索 ----------
    suspend fun searchBilibili(keyword: String, biliCookie: String = ""): List<Song> {
        val service = if (biliCookie.isNotBlank()) biliApi.withCookie(biliCookie) else biliApi
        return service.searchMusic(keyword).map { it.toSong() }
    }

    // ---------- B站播放链接 ----------
    suspend fun resolveBilibiliUrl(songId: String, biliCookie: String = ""): String {
        // songId 格式: bili_BVxxx
        val bvid = songId.removePrefix("bili_")
        val service = if (biliCookie.isNotBlank()) biliApi.withCookie(biliCookie) else biliApi
        return service.getPlayUrl(bvid)
    }

    // ---------- QQ音乐搜索 ----------
    suspend fun searchQQMusic(keyword: String, cookie: String = ""): List<Song> {
        val service = if (cookie.isNotBlank()) qqMusicApi.withCookie(cookie) else qqMusicApi
        return service.searchMusic(keyword).map { it.toSong() }
    }

    // ---------- QQ音乐播放链接 ----------
    suspend fun resolveQQMusicUrl(songId: String, cookie: String = ""): String {
        val songmid = songId.removePrefix("qqmusic_")
        val service = if (cookie.isNotBlank()) qqMusicApi.withCookie(cookie) else qqMusicApi
        return service.getPlayUrl(songmid)
    }

    // ---------- 酷我音乐搜索 ----------
    suspend fun searchKuwo(keyword: String, cookie: String = ""): List<Song> {
        val service = if (cookie.isNotBlank()) kuwoApi.withCookie(cookie) else kuwoApi
        return service.searchMusic(keyword).map { it.toSong() }
    }

    // ---------- 酷我音乐播放链接 ----------
    suspend fun resolveKuwoUrl(songId: String, cookie: String = ""): String {
        val rid = songId.removePrefix("kuwo_")
        val service = if (cookie.isNotBlank()) kuwoApi.withCookie(cookie) else kuwoApi
        return service.getPlayUrl(rid)
    }

    // ---------- 酷狗音乐搜索 ----------
    suspend fun searchKugou(keyword: String, cookie: String = ""): List<Song> {
        val service = if (cookie.isNotBlank()) kugouApi.withCookie(cookie) else kugouApi
        return service.searchMusic(keyword).map { it.toSong() }
    }

    // ---------- 酷狗音乐播放链接 ----------
    suspend fun resolveKugouUrl(songId: String, cookie: String = ""): String {
        val hash = songId.removePrefix("kugou_")
        val service = if (cookie.isNotBlank()) kugouApi.withCookie(cookie) else kugouApi
        return service.getPlayUrl(hash)
    }

    // ---------- 汽水音乐搜索 ----------
    suspend fun searchQishui(keyword: String, cookie: String = ""): List<Song> {
        val service = if (cookie.isNotBlank()) qishuiApi.withCookie(cookie) else qishuiApi
        return service.searchMusic(keyword).map { it.toSong() }
    }

    // ---------- 汽水音乐播放链接 ----------
    suspend fun resolveQishuiUrl(songId: String, cookie: String = ""): String {
        val id = songId.removePrefix("qishui_")
        val service = if (cookie.isNotBlank()) qishuiApi.withCookie(cookie) else qishuiApi
        return service.getPlayUrl(id)
    }

    // ---------- Spotify 搜索 ----------
    suspend fun searchSpotify(keyword: String, token: String = ""): List<Song> {
        val service = if (token.isNotBlank()) spotifyApi.withCookie(token) else spotifyApi
        return service.searchMusic(keyword).map { it.toSong() }
    }

    // ---------- Spotify 播放链接 ----------
    suspend fun resolveSpotifyUrl(songId: String, token: String = ""): String {
        val trackId = songId.removePrefix("spotify_")
        val service = if (token.isNotBlank()) spotifyApi.withCookie(token) else spotifyApi
        return service.getPlayUrl(trackId)
    }

    // ---------- YouTube Music 搜索 ----------
    suspend fun searchYouTubeMusic(
        keyword: String,
        officialApiKey: String = "",
        cookie: String = ""
    ): List<Song> {
        val service = youtubeMusicApi.withCredentials(officialApiKey, cookie)
        return service.searchMusic(keyword).map { it.toSong() }
    }

    // ---------- YouTube Music 播放链接 ----------
    suspend fun resolveYouTubeMusicUrl(
        songId: String,
        officialApiKey: String = "",
        cookie: String = ""
    ): String {
        val videoId = songId.removePrefix("youtube_")
        return youtubeMusicApi.withCredentials(officialApiKey, cookie).getPlayUrl(videoId)
    }
}