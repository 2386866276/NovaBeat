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
 * 酷我音乐音源搜索服务
 * 通过酷我音乐 API 搜索歌曲，获取播放链接
 */
class KuwoApiService(private val cookie: String = "") {

    companion object {
        private const val TAG = "KuwoApi"
        private const val SEARCH_URL = "https://search.kuwo.cn/r.s"
        private const val PLAY_URL = "https://www.kuwo.cn/api/v1/www/music/playUrl"
        private const val METING_URL = "https://api.injahow.cn/meting/?server=kuwo&type=url&id="
    }

    private val headers = mutableMapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        "Referer" to "https://www.kuwo.cn/",
        "csrf" to "xxxx"
    ).also {
        if (cookie.isNotBlank()) it["Cookie"] = cookie
    }

    fun withCookie(cookie: String): KuwoApiService = KuwoApiService(cookie = cookie)
    fun getCookie(): String = cookie

    /**
     * 搜索酷我音乐
     */
    suspend fun searchMusic(keyword: String, page: Int = 1, limit: Int = 20): List<KuwoSong> =
        withContext(Dispatchers.IO) {
            try {
                val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
                val urlStr = "$SEARCH_URL?all=$encodedKeyword&musicVer=1&flac=1&pn=$page&rn=$limit&encoding=utf8&rformat=json&mobi=1"
                Log.d(TAG, "搜索酷我音乐: $keyword")

                val resp = httpGet(urlStr)
                Log.d(TAG, "酷我搜索响应: ${resp.take(200)}")

                val jsonObj = JSONObject(resp)
                val abslist = jsonObj.optJSONArray("abslist") ?: return@withContext emptyList()
                val songs = mutableListOf<KuwoSong>()

                for (i in 0 until abslist.length()) {
                    val item = abslist.getJSONObject(i)
                    val rid = item.optString("MUSICRID", "").removePrefix("MUSIC_")
                    val name = item.optString("SONGNAME", "")
                    val artist = item.optString("ARTIST", "").replace("&", "、")
                    val album = item.optString("ALBUM", "")
                    val durationStr = item.optString("DURATION", "0")
                    val durationMs = durationStr.toLongOrNull()?.times(1000) ?: 0L
                    val webAlbumpicShort = item.optString("web_albumpic_short", "")
                    val artistId = item.optString("ARTISTID", "")

                    val coverUrl = when {
                        webAlbumpicShort.isNotBlank() -> "https://img1.kuwo.cn/star/albumcover/$webAlbumpicShort"
                        artistId.isNotBlank() -> "https://img1.kuwo.cn/star/albumcover/300/$artistId/${artistId}.jpg"
                        else -> ""
                    }

                    if (rid.isNotBlank() && name.isNotBlank()) {
                        songs.add(KuwoSong(
                            rid = rid,
                            name = name,
                            artist = artist,
                            album = album,
                            coverUrl = coverUrl,
                            durationMs = durationMs
                        ))
                    }
                }
                Log.d(TAG, "酷我搜索到 ${songs.size} 条结果")
                songs
            } catch (e: Exception) {
                Log.e(TAG, "酷我搜索失败", e)
                emptyList()
            }
        }

    /**
     * 获取播放链接
     */
    suspend fun getPlayUrl(rid: String): String =
        withContext(Dispatchers.IO) {
            try {
                val urlStr = "$PLAY_URL?mid=$rid&type=music&br=320kflac&httpsStatus=1"
                Log.d(TAG, "获取酷我播放链接: rid=$rid")

                val resp = httpGet(urlStr)
                Log.d(TAG, "酷我播放链接响应: ${resp.take(200)}")

                val jsonObj = JSONObject(resp)
                val code = jsonObj.optInt("code", -1)
                if (code == 200) {
                    val data = jsonObj.optJSONObject("data")
                    val url = data?.optString("url", "") ?: ""
                    if (url.isNotBlank()) {
                        Log.d(TAG, "获取到播放链接: ${url.take(80)}...")
                        return@withContext url
                    }
                }
                // 回退到 Meting API
                tryMetingUrl(rid)
            } catch (e: Exception) {
                Log.e(TAG, "获取酷我播放链接失败", e)
                tryMetingUrl(rid)
            }
        }

    private suspend fun tryMetingUrl(rid: String): String =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("${METING_URL}$rid")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.instanceFollowRedirects = false
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                location ?: ""
            } catch (_: Exception) { "" }
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
}

/**
 * 酷我音乐歌曲数据类
 */
data class KuwoSong(
    val rid: String,
    val name: String,
    val artist: String,
    val album: String = "",
    val coverUrl: String = "",
    val durationMs: Long = 0
) {
    fun toSong(): Song = Song(
        id = "kuwo_$rid",
        title = name,
        artist = artist,
        album = album,
        coverUrl = coverUrl,
        url = "",
        durationMs = durationMs,
        format = "mp3",
        isLocal = false,
        source = "kuwo"
    )
}