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
 * Bilibili 音源搜索服务
 * 通过 B站 API 搜索视频/音频内容，提取音频流
 */
class BilibiliApiService(private val cookie: String = "") {

    companion object {
        private const val TAG = "BilibiliApi"
        private const val SEARCH_URL = "https://api.bilibili.com/x/web-interface/wbi/search/type"
        private const val PLAY_URL_API = "https://api.bilibili.com/x/player/playurl"
    }

    private val headers = mutableMapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        "Referer" to "https://www.bilibili.com"
    ).also {
        if (cookie.isNotBlank()) {
            it["Cookie"] = cookie
        }
    }

    /**
     * 使用指定的 Cookie 创建新的实例
     */
    fun withCookie(cookie: String): BilibiliApiService =
        BilibiliApiService(cookie = cookie)

    /**
     * 获取当前 Cookie
     */
    fun getCookie(): String = cookie

    /**
     * 搜索 B站 音频内容
     */
    suspend fun searchMusic(keyword: String, page: Int = 1): List<BilibiliSong> =
        withContext(Dispatchers.IO) {
            try {
                val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
                // search_type=video 搜索视频，后续提取音频
                val urlStr = "$SEARCH_URL?search_type=video&keyword=$encodedKeyword&page=$page&order=totalrank"
                Log.d(TAG, "搜索B站: $urlStr")

                val resp = httpGet(urlStr)
                Log.d(TAG, "B站搜索响应: ${resp.take(200)}")

                val jsonObj = JSONObject(resp)
                val code = jsonObj.optInt("code", -1)
                if (code != 0) {
                    Log.w(TAG, "B站API返回错误: $code, ${jsonObj.optString("message")}")
                    return@withContext emptyList()
                }

                val data = jsonObj.optJSONObject("data")
                val results = data?.optJSONArray("result") ?: return@withContext emptyList()
                val songs = mutableListOf<BilibiliSong>()

                for (i in 0 until results.length()) {
                    val item = results.getJSONObject(i)
                    val bvid = item.optString("bvid", "")
                    val title = item.optString("title", "")
                        .replace("<em class=\"keyword\">", "")
                        .replace("</em>", "")
                    val author = item.optString("author", "")
                    val cover = item.optString("pic", "")
                    val duration = item.optString("duration", "0:00")
                    val durationMs = parseDurationStr(duration)
                    val aid = item.optLong("aid", 0)

                    if (bvid.isNotBlank() && title.isNotBlank()) {
                        songs.add(BilibiliSong(
                            bvid = bvid,
                            aid = aid,
                            title = title,
                            author = author,
                            coverUrl = if (cover.startsWith("//")) "https:$cover" else cover,
                            durationMs = durationMs
                        ))
                    }
                }

                Log.d(TAG, "B站搜索到 ${songs.size} 条结果")
                songs
            } catch (e: Exception) {
                Log.e(TAG, "B站搜索失败", e)
                emptyList()
            }
        }

    /**
     * 获取视频播放链接（音频流）
     */
    suspend fun getPlayUrl(bvid: String, cid: Long = 0): String =
        withContext(Dispatchers.IO) {
            try {
                // 先获取 cid
                val actualCid = if (cid > 0) cid else getCid(bvid)
                if (actualCid <= 0) {
                    Log.w(TAG, "无法获取 cid for bvid=$bvid")
                    return@withContext ""
                }

                // 请求音频流 URL (fnval=16 即 dash 格式)
                val urlStr = "$PLAY_URL_API?bvid=$bvid&cid=$actualCid&fnval=16&fnver=0&qn=64"
                Log.d(TAG, "获取播放链接: $urlStr")

                val resp = httpGet(urlStr)
                val jsonObj = JSONObject(resp)
                val code = jsonObj.optInt("code", -1)
                if (code != 0) {
                    Log.w(TAG, "播放链接API错误: $code")
                    return@withContext ""
                }

                val data = jsonObj.optJSONObject("data") ?: return@withContext ""
                val dash = data.optJSONObject("dash") ?: return@withContext ""

                // 优先取音频流
                val audioArray = dash.optJSONArray("audio")
                if (audioArray != null && audioArray.length() > 0) {
                    // 取第一个音频流
                    val audioObj = audioArray.getJSONObject(0)
                    val backupUrl = audioObj.optString("backupUrl", "")
                    val baseUrl = audioObj.optString("baseUrl", "")
                    val audioUrl = if (baseUrl.isNotBlank()) baseUrl else backupUrl
                    Log.d(TAG, "获取到音频流: ${audioUrl.take(80)}...")
                    audioUrl
                } else {
                    // 退而求其次取视频流中的音频
                    val videoArray = dash.optJSONArray("video")
                    if (videoArray != null && videoArray.length() > 0) {
                        val videoObj = videoArray.getJSONObject(0)
                        val baseUrl = videoObj.optString("baseUrl", "")
                        Log.d(TAG, "获取到视频流(含音频): ${baseUrl.take(80)}...")
                        baseUrl
                    } else {
                        ""
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取B站播放链接失败", e)
                ""
            }
        }

    /**
     * 通过 bvid 获取 cid
     */
    private suspend fun getCid(bvid: String): Long =
        withContext(Dispatchers.IO) {
            try {
                val urlStr = "https://api.bilibili.com/x/web-interface/view?bvid=$bvid"
                val resp = httpGet(urlStr)
                val jsonObj = JSONObject(resp)
                val data = jsonObj.optJSONObject("data")
                val cid = data?.optLong("cid", 0) ?: 0
                Log.d(TAG, "bvid=$bvid cid=$cid")
                cid
            } catch (e: Exception) {
                Log.e(TAG, "获取cid失败", e)
                0
            }
        }

    private fun parseDurationStr(duration: String): Long {
        return try {
            val parts = duration.split(":")
            when (parts.size) {
                2 -> (parts[0].toLong() * 60 + parts[1].toLong()) * 1000
                3 -> (parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()) * 1000
                else -> 0
            }
        } catch (_: Exception) { 0 }
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
 * B站歌曲数据类
 */
data class BilibiliSong(
    val bvid: String,
    val aid: Long = 0,
    val title: String,
    val author: String,
    val coverUrl: String = "",
    val durationMs: Long = 0
) {
    fun toSong(): Song = Song(
        id = "bili_$bvid",
        title = title,
        artist = author,
        album = "Bilibili",
        coverUrl = coverUrl,
        url = "",
        durationMs = durationMs,
        format = "dash",
        isLocal = false,
        source = "bilibili"
    )
}