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
 * 汽水音乐音源搜索服务
 * 通过 ByteDance/抖音音乐 API 搜索歌曲，获取播放链接
 * 汽水音乐是字节跳动旗下音乐 App，与抖音音乐库共享
 */
class QishuiApiService(private val cookie: String = "") {

    companion object {
        private const val TAG = "QishuiApi"
        private const val SEARCH_URL = "https://api.douyin.com/aweme/v1/web/general/search/single/"
        private const val MUSIC_SEARCH_URL = "https://api.amemv.com/aweme/v1/music/search/"
        private const val QISHUI_SEARCH_URL = "https://music.91q.com/v1/search"
        private const val QISHUI_PLAY_URL = "https://music.91q.com/v1/song/tracklink"
    }

    private val headers = mutableMapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        "Referer" to "https://music.91q.com/"
    ).also {
        if (cookie.isNotBlank()) it["Cookie"] = cookie
    }

    fun withCookie(cookie: String): QishuiApiService = QishuiApiService(cookie = cookie)
    fun getCookie(): String = cookie

    /**
     * 搜索汽水音乐
     * 尝试多个 API 端点，按可用性依次回退
     */
    suspend fun searchMusic(keyword: String, page: Int = 1, limit: Int = 20): List<QishuiSong> =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "搜索汽水音乐: $keyword")
            // 尝试方式1：汽水音乐 Web API
            val result1 = tryQishuiWebSearch(keyword, page, limit)
            if (result1.isNotEmpty()) return@withContext result1
            // 尝试方式2：抖音音乐 API
            val result2 = tryDouyinMusicSearch(keyword, page, limit)
            if (result2.isNotEmpty()) return@withContext result2
            emptyList()
        }

    /**
     * 方式1：通过汽水音乐 Web API 搜索
     */
    private suspend fun tryQishuiWebSearch(keyword: String, page: Int, limit: Int): List<QishuiSong> =
        withContext(Dispatchers.IO) {
            try {
                val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
                val pageNo = if (page > 0) page else 1
                val urlStr = "$QISHUI_SEARCH_URL?keyword=$encodedKeyword&pageNo=$pageNo&pageSize=$limit&type=1"
                Log.d(TAG, "汽水音乐Web搜索: $urlStr")

                val resp = httpGet(urlStr)
                Log.d(TAG, "汽水搜索响应: ${resp.take(200)}")

                val jsonObj = JSONObject(resp)
                val data = jsonObj.optJSONObject("data")
                val typeList = data?.optJSONArray("typeList")
                if (typeList != null && typeList.length() > 0) {
                    val firstType = typeList.getJSONObject(0)
                    val list = firstType.optJSONArray("list") ?: return@withContext emptyList()
                    return@withContext parseQishuiSearchResults(list)
                }
                // 有些版本直接返回 list
                val list = data?.optJSONArray("list")
                if (list != null) {
                    return@withContext parseQishuiSearchResults(list)
                }
                emptyList()
            } catch (e: Exception) {
                Log.w(TAG, "汽水音乐Web搜索失败: ${e.message}")
                emptyList()
            }
        }

    private fun parseQishuiSearchResults(list: org.json.JSONArray): List<QishuiSong> {
        val songs = mutableListOf<QishuiSong>()
        for (i in 0 until list.length()) {
            try {
                val item = list.getJSONObject(i)
                val songId = item.optString("songId", item.optString("id", ""))
                val name = item.optString("name", item.optString("title", ""))
                val artist = item.optString("artist", item.optString("singer", ""))
                    .let { if (it.isBlank()) "未知" else it }
                val album = item.optString("album", "")
                val coverUrl = item.optString("cover", item.optString("pic", ""))
                val duration = item.optLong("duration", 0) * 1000L

                if (songId.isNotBlank() && name.isNotBlank()) {
                    songs.add(QishuiSong(
                        songId = songId,
                        name = name,
                        artist = artist,
                        album = album,
                        coverUrl = coverUrl,
                        durationMs = duration
                    ))
                }
            } catch (_: Exception) { }
        }
        Log.d(TAG, "汽水音乐搜索到 ${songs.size} 条结果")
        return songs
    }

    /**
     * 方式2：通过抖音音乐 API 搜索（移动端接口）
     */
    private suspend fun tryDouyinMusicSearch(keyword: String, page: Int, limit: Int): List<QishuiSong> =
        withContext(Dispatchers.IO) {
            try {
                val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
                val offset = (page - 1) * limit
                val urlStr = "$MUSIC_SEARCH_URL?keyword=$encodedKeyword&count=$limit&offset=$offset&device_platform=android&device_type=Pixel+4&version_code=250000"
                Log.d(TAG, "抖音音乐搜索: $urlStr")

                val mobileHeaders = headers.toMutableMap().apply {
                    put("User-Agent", "com.ss.android.ugc.aweme/250000 (Linux; U; Android 13; zh_CN; Pixel 4; Build/TQ3A.230705.001; Cronet/58.0.2991.0)")
                }
                val resp = httpGetWithHeaders(urlStr, mobileHeaders)
                Log.d(TAG, "抖音音乐搜索响应: ${resp.take(200)}")

                val jsonObj = JSONObject(resp)
                val musicList = jsonObj.optJSONArray("data")
                    ?: jsonObj.optJSONArray("musics")
                    ?: return@withContext emptyList()

                val songs = mutableListOf<QishuiSong>()
                for (i in 0 until musicList.length()) {
                    val item = musicList.getJSONObject(i)
                    val songId = item.optString("music_id", item.optString("id", ""))
                    val name = item.optString("title", item.optString("name", ""))
                    val author = item.optJSONArray("author_list")?.let { arr ->
                        if (arr.length() > 0) arr.getJSONObject(0).optString("name", "") else ""
                    } ?: item.optString("author", "未知")
                    val coverUrl = item.optString("cover_large", item.optString("cover", ""))
                    val duration = item.optLong("duration", 0) * 1000L
                    val playUrl = item.optString("play_url", item.optString("url", ""))

                    if (songId.isNotBlank() && name.isNotBlank()) {
                        songs.add(QishuiSong(
                            songId = songId,
                            name = name,
                            artist = author.ifBlank { "未知" },
                            album = "汽水音乐",
                            coverUrl = coverUrl,
                            durationMs = duration,
                            playUrl = playUrl
                        ))
                    }
                }
                Log.d(TAG, "抖音音乐搜索到 ${songs.size} 条结果")
                songs
            } catch (e: Exception) {
                Log.w(TAG, "抖音音乐搜索失败: ${e.message}")
                emptyList()
            }
        }

    /**
     * 获取播放链接
     */
    suspend fun getPlayUrl(songId: String, cachedPlayUrl: String = ""): String =
        withContext(Dispatchers.IO) {
            // 如果搜索时已获取到播放链接，直接返回
            if (cachedPlayUrl.isNotBlank() && cachedPlayUrl.startsWith("http")) {
                Log.d(TAG, "使用搜索时的播放链接: ${cachedPlayUrl.take(80)}...")
                return@withContext cachedPlayUrl
            }
            try {
                val urlStr = "$QISHUI_PLAY_URL?songId=$songId"
                Log.d(TAG, "获取汽水播放链接: songId=$songId")

                val resp = httpGet(urlStr)
                Log.d(TAG, "汽水播放链接响应: ${resp.take(200)}")

                val jsonObj = JSONObject(resp)
                val data = jsonObj.optJSONObject("data")
                val url = data?.optString("url", "") ?: ""
                if (url.isNotBlank() && url.startsWith("http")) {
                    Log.d(TAG, "获取到播放链接: ${url.take(80)}...")
                    return@withContext url
                }
                ""
            } catch (e: Exception) {
                Log.e(TAG, "获取汽水播放链接失败", e)
                ""
            }
        }

    private fun httpGet(urlStr: String): String = httpGetWithHeaders(urlStr, headers)

    private fun httpGetWithHeaders(urlStr: String, hdrs: Map<String, String>): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        hdrs.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val text = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        conn.disconnect()
        return text
    }
}

/**
 * 汽水音乐歌曲数据类
 */
data class QishuiSong(
    val songId: String,
    val name: String,
    val artist: String,
    val album: String = "",
    val coverUrl: String = "",
    val durationMs: Long = 0,
    val playUrl: String = ""
) {
    fun toSong(): Song = Song(
        id = "qishui_$songId",
        title = name,
        artist = artist,
        album = album,
        coverUrl = coverUrl,
        url = "",
        durationMs = durationMs,
        format = "mp3",
        isLocal = false,
        source = "qishui"
    )
}