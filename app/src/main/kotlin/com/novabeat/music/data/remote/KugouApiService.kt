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
 * 酷狗音乐音源搜索服务
 * 通过酷狗音乐 API 搜索歌曲，获取播放链接
 */
class KugouApiService(private val cookie: String = "") {

    companion object {
        private const val TAG = "KugouApi"
        private const val SEARCH_URL = "https://mobilecdn.kugou.com/api/v3/search/song"
        private const val PLAY_URL = "https://m.kugou.com/app/i/getSongInfo.php"
        private const val METING_URL = "https://api.injahow.cn/meting/?server=kugou&type=url&id="
    }

    private val headers = mutableMapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        "Referer" to "https://www.kugou.com/"
    ).also {
        if (cookie.isNotBlank()) it["Cookie"] = cookie
    }

    fun withCookie(cookie: String): KugouApiService = KugouApiService(cookie = cookie)
    fun getCookie(): String = cookie

    /**
     * 搜索酷狗音乐
     */
    suspend fun searchMusic(keyword: String, page: Int = 1, limit: Int = 20): List<KugouSong> =
        withContext(Dispatchers.IO) {
            try {
                val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
                val urlStr = "$SEARCH_URL?keyword=$encodedKeyword&page=$page&pagesize=$limit&format=json"
                Log.d(TAG, "搜索酷狗音乐: $keyword")

                val resp = httpGet(urlStr)
                Log.d(TAG, "酷狗搜索响应: ${resp.take(200)}")

                val jsonObj = JSONObject(resp)
                val data = jsonObj.optJSONObject("data")
                val infoList = data?.optJSONArray("info") ?: return@withContext emptyList()
                val songs = mutableListOf<KugouSong>()

                for (i in 0 until infoList.length()) {
                    val item = infoList.getJSONObject(i)
                    val hash = item.optString("hash", "")
                    val name = item.optString("songname", "")
                    val singer = item.optString("singername", "")
                    val albumId = item.optString("album_id", "")
                    val duration = item.optInt("duration", 0) * 1000L
                    val albumpic = item.optString("albumpic", "")
                    val albumImg = item.optString("album_img", "").let {
                        if (it.isNotBlank()) it.replace("/{size}", "/300") else ""
                    }

                    val coverUrl = albumpic.ifBlank { albumImg }

                    if (hash.isNotBlank() && name.isNotBlank()) {
                        songs.add(KugouSong(
                            hash = hash,
                            name = name,
                            singer = singer,
                            albumId = albumId,
                            coverUrl = coverUrl,
                            durationMs = duration
                        ))
                    }
                }
                Log.d(TAG, "酷狗搜索到 ${songs.size} 条结果")
                songs
            } catch (e: Exception) {
                Log.e(TAG, "酷狗搜索失败", e)
                emptyList()
            }
        }

    /**
     * 获取播放链接
     */
    suspend fun getPlayUrl(hash: String, albumId: String = ""): String =
        withContext(Dispatchers.IO) {
            try {
                val urlStr = "$PLAY_URL?cmd=playInfo&hash=$hash"
                Log.d(TAG, "获取酷狗播放链接: hash=$hash")

                val resp = httpGet(urlStr)
                Log.d(TAG, "酷狗播放链接响应: ${resp.take(200)}")

                val jsonObj = JSONObject(resp)
                val playUrl = jsonObj.optString("url", "")
                if (playUrl.isNotBlank() && playUrl.startsWith("http")) {
                    Log.d(TAG, "获取到播放链接: ${playUrl.take(80)}...")
                    return@withContext playUrl
                }
                // 回退到 Meting API
                tryMetingUrl(hash)
            } catch (e: Exception) {
                Log.e(TAG, "获取酷狗播放链接失败", e)
                tryMetingUrl(hash)
            }
        }

    private suspend fun tryMetingUrl(hash: String): String =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("${METING_URL}$hash")
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
 * 酷狗音乐歌曲数据类
 */
data class KugouSong(
    val hash: String,
    val name: String,
    val singer: String,
    val albumId: String = "",
    val coverUrl: String = "",
    val durationMs: Long = 0
) {
    fun toSong(): Song = Song(
        id = "kugou_$hash",
        title = name,
        artist = singer,
        album = "",
        coverUrl = coverUrl,
        url = "",
        durationMs = durationMs,
        format = "mp3",
        isLocal = false,
        source = "kugou"
    )
}