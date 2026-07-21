package com.novabeat.music.data.remote

import android.util.Log
import com.novabeat.music.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Spotify 音源搜索服务
 * 通过 Spotify Web API 搜索歌曲，获取播放链接（30秒预览）
 * 需要用户提供 Spotify Access Token（通过 SharedPreferences 存储）
 *
 * 使用方式：
 * 1. 用户在 https://accounts.spotify.com 授权获取 Access Token
 * 2. 将 Token 存入 SharedPreferences("novabeat", "spotify_token")
 * 3. 搜索时使用 Bearer Token 认证
 * 4. 播放时优先使用 preview_url（30秒预览），回退到第三方解析
 */
class SpotifyApiService(private val accessToken: String = "") {

    companion object {
        private const val TAG = "SpotifyApi"
        private const val SEARCH_URL = "https://api.spotify.com/v1/search"
        private const val TRACK_URL = "https://api.spotify.com/v1/tracks"
        // 第三方解析 API（回退方案）
        private const val RESOLVE_URL = "https://api.spotifydown.com/metadata/track/"
    }

    private val headers = mutableMapOf(
        "Accept" to "application/json",
        "Content-Type" to "application/json",
        "User-Agent" to "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    ).also {
        if (accessToken.isNotBlank()) it["Authorization"] = "Bearer $accessToken"
    }

    fun withCookie(token: String): SpotifyApiService = SpotifyApiService(accessToken = token)
    fun getCookie(): String = accessToken

    /**
     * 搜索 Spotify 音乐
     */
    suspend fun searchMusic(keyword: String, limit: Int = 20): List<SpotifySong> =
        withContext(Dispatchers.IO) {
            if (accessToken.isBlank()) {
                Log.w(TAG, "未提供 Spotify Access Token，无法搜索")
                return@withContext emptyList()
            }
            try {
                val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
                val urlStr = "$SEARCH_URL?q=$encodedKeyword&type=track&limit=$limit&market=CN"
                Log.d(TAG, "搜索Spotify: $keyword")

                val resp = httpGet(urlStr)
                Log.d(TAG, "Spotify搜索响应: ${resp.take(200)}")

                val jsonObj = JSONObject(resp)
                val tracksObj = jsonObj.optJSONObject("tracks")
                val items = tracksObj?.optJSONArray("items") ?: return@withContext emptyList()

                val songs = mutableListOf<SpotifySong>()
                for (i in 0 until items.length()) {
                    try {
                        val item = items.getJSONObject(i)
                        val trackId = item.optString("id", "")
                        val name = item.optString("name", "")
                        val durationMs = item.optLong("duration_ms", 0)
                        val previewUrl = item.optString("preview_url", "")
                        val explicit = item.optBoolean("explicit", false)

                        // 艺术家
                        val artistsArr = item.optJSONArray("artists")
                        val artist = if (artistsArr != null && artistsArr.length() > 0) {
                            val artistNames = mutableListOf<String>()
                            for (j in 0 until artistsArr.length()) {
                                artistNames.add(artistsArr.getJSONObject(j).optString("name", ""))
                            }
                            artistNames.joinToString("、")
                        } else "未知"

                        // 专辑
                        val albumObj = item.optJSONObject("album")
                        val albumName = albumObj?.optString("name", "") ?: ""
                        val coverUrl = if (albumObj != null) {
                            val imagesArr = albumObj.optJSONArray("images")
                            if (imagesArr != null && imagesArr.length() > 0) {
                                imagesArr.getJSONObject(0).optString("url", "")
                            } else ""
                        } else ""

                        // 外部链接
                        val externalUrls = item.optJSONObject("external_urls")
                        val spotifyUrl = externalUrls?.optString("spotify", "") ?: ""

                        if (trackId.isNotBlank() && name.isNotBlank()) {
                            songs.add(SpotifySong(
                                trackId = trackId,
                                name = name,
                                artist = artist,
                                album = albumName,
                                coverUrl = coverUrl,
                                durationMs = durationMs,
                                previewUrl = previewUrl,
                                spotifyUrl = spotifyUrl,
                                explicit = explicit
                            ))
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "解析第 $i 条搜索结果失败: ${e.message}")
                    }
                }
                Log.d(TAG, "Spotify搜索到 ${songs.size} 条结果")
                songs
            } catch (e: Exception) {
                Log.e(TAG, "Spotify搜索失败", e)
                emptyList()
            }
        }

    /**
     * 获取播放链接
     * 优先使用搜索时已获取的 preview_url（30秒预览）
     * 回退到第三方 API 解析完整播放链接
     */
    suspend fun getPlayUrl(trackId: String, cachedPreviewUrl: String = ""): String =
        withContext(Dispatchers.IO) {
            // 方式1：使用搜索时已获取的 preview_url
            if (cachedPreviewUrl.isNotBlank() && cachedPreviewUrl.startsWith("http")) {
                Log.d(TAG, "使用Spotify预览链接: ${cachedPreviewUrl.take(80)}...")
                return@withContext cachedPreviewUrl
            }

            // 方式2：通过 Spotify API 获取 track 信息中的 preview_url
            if (accessToken.isNotBlank()) {
                try {
                    val urlStr = "$TRACK_URL/$trackId?market=CN"
                    Log.d(TAG, "获取Spotify Track信息: trackId=$trackId")
                    val resp = httpGet(urlStr)
                    val jsonObj = JSONObject(resp)
                    val previewUrl = jsonObj.optString("preview_url", "")
                    if (previewUrl != "null" && previewUrl.isNotBlank() && previewUrl.startsWith("http")) {
                        Log.d(TAG, "获取到Spotify预览链接: ${previewUrl.take(80)}...")
                        return@withContext previewUrl
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "通过Spotify API获取预览链接失败: ${e.message}")
                }
            }

            // 方式3：第三方 API 解析（尝试获取完整播放链接）
            try {
                val urlStr = "$RESOLVE_URL$trackId"
                Log.d(TAG, "尝试第三方解析Spotify: trackId=$trackId")
                val resp = httpGetNoAuth(urlStr)
                val jsonObj = JSONObject(resp)
                val data = jsonObj.optJSONObject("data")
                val playUrl = data?.optString("url", "") ?: ""
                if (playUrl.isNotBlank() && playUrl.startsWith("http")) {
                    Log.d(TAG, "第三方解析到播放链接: ${playUrl.take(80)}...")
                    return@withContext playUrl
                }
            } catch (e: Exception) {
                Log.w(TAG, "第三方解析Spotify失败: ${e.message}")
            }

            Log.w(TAG, "无法获取Spotify播放链接（可能需要Spotify Premium或Token已过期）")
            ""
        }

    private fun httpGet(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val text = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        conn.disconnect()
        return text
    }

    private fun httpGetNoAuth(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
        conn.setRequestProperty("Accept", "application/json")
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val text = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        conn.disconnect()
        return text
    }
}

/**
 * Spotify 歌曲数据类
 */
data class SpotifySong(
    val trackId: String,
    val name: String,
    val artist: String,
    val album: String = "",
    val coverUrl: String = "",
    val durationMs: Long = 0,
    val previewUrl: String = "",
    val spotifyUrl: String = "",
    val explicit: Boolean = false
) {
    fun toSong(): Song = Song(
        id = "spotify_$trackId",
        title = name,
        artist = artist,
        album = album,
        coverUrl = coverUrl,
        url = previewUrl,
        durationMs = durationMs,
        format = "mp3",
        isLocal = false,
        source = "spotify"
    )
}