package com.novabeat.music.data.remote

import android.util.Log
import com.novabeat.music.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * QQ音乐音源搜索服务
 * 通过 QQ音乐 API 搜索歌曲，获取播放链接
 */
class QQMusicApiService(private val cookie: String = "") {

    companion object {
        private const val TAG = "QQMusicApi"
        private const val SEARCH_URL = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp"
        private const val VKEY_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg"
        private const val METING_URL = "https://api.injahow.cn/meting/?server=tencent&type=url&id="
    }

    private val headers = mutableMapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        "Referer" to "https://y.qq.com/"
    ).also {
        if (cookie.isNotBlank()) it["Cookie"] = cookie
    }

    fun withCookie(cookie: String): QQMusicApiService = QQMusicApiService(cookie = cookie)
    fun getCookie(): String = cookie

    /**
     * 搜索QQ音乐歌曲
     */
    suspend fun searchMusic(keyword: String, page: Int = 1, limit: Int = 20): List<QQMusicSong> =
        withContext(Dispatchers.IO) {
            try {
                val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
                val urlStr = "$SEARCH_URL?w=$encodedKeyword&p=$page&n=$limit&format=json&crystal=true&lvqlist=12"
                Log.d(TAG, "搜索QQ音乐: $keyword")

                val resp = httpGet(urlStr)
                Log.d(TAG, "QQ音乐搜索响应: ${resp.take(200)}")

                val jsonObj = JSONObject(resp)
                val data = jsonObj.optJSONObject("data")
                val songObj = data?.optJSONObject("song")
                val list = songObj?.optJSONArray("list") ?: return@withContext emptyList()

                val songs = mutableListOf<QQMusicSong>()
                for (i in 0 until list.length()) {
                    val item = list.getJSONObject(i)
                    val songmid = item.optString("songmid", "")
                    val songname = item.optString("songname", "")
                    val singerArr = item.optJSONArray("singer")
                    val singer = singerArr?.let { arr ->
                        (0 until arr.length()).joinToString("、") { j ->
                            arr.getJSONObject(j).optString("name", "")
                        }
                    } ?: "未知"
                    val albummid = item.optJSONObject("album")?.optString("mid", "") ?: ""
                    val albumname = item.optString("albumname", "")
                    val interval = item.optInt("interval", 0) * 1000L
                    val songid = item.optString("songid", "")

                    if (songmid.isNotBlank() && songname.isNotBlank()) {
                        val coverUrl = if (albummid.isNotBlank())
                            "https://y.gtimg.cn/music/photo_new/T002R300x300M000${albummid}.jpg" else ""
                        songs.add(QQMusicSong(
                            songmid = songmid,
                            songid = songid,
                            name = songname,
                            singer = singer,
                            albumname = albumname,
                            coverUrl = coverUrl,
                            durationMs = interval
                        ))
                    }
                }
                Log.d(TAG, "QQ音乐搜索到 ${songs.size} 条结果")
                songs
            } catch (e: Exception) {
                Log.e(TAG, "QQ音乐搜索失败", e)
                emptyList()
            }
        }

    /**
     * 获取播放链接
     * 通过 VKEY 接口获取可播放的 CDN 链接
     */
    suspend fun getPlayUrl(songmid: String): String =
        withContext(Dispatchers.IO) {
            try {
                val jsonBody = JSONObject().apply {
                    put("req_0", JSONObject().apply {
                        put("module", "vkey.GetVkeyServer")
                        put("method", "CgiGetVkey")
                        put("param", JSONObject().apply {
                            put("guid", "10000")
                            put("songmid", JSONArray().apply { put(songmid) })
                            put("songtype", JSONArray().apply { put(0) })
                            put("uin", "")
                            put("loginflag", 1)
                            put("platform", "20")
                        })
                    })
                }.toString()

                Log.d(TAG, "获取QQ音乐播放链接: songmid=$songmid")
                val resp = httpPostJson(VKEY_URL, jsonBody)
                Log.d(TAG, "VKEY响应: ${resp.take(200)}")

                val jsonObj = JSONObject(resp)
                val req0 = jsonObj.optJSONObject("req_0")
                val data = req0?.optJSONObject("data")
                val midurlinfo = data?.optJSONArray("midurlinfo")
                if (midurlinfo != null && midurlinfo.length() > 0) {
                    val info = midurlinfo.getJSONObject(0)
                    val purl = info.optString("purl", "")
                    if (purl.isNotBlank()) {
                        val playUrl = "https://ws.stream.qqmusic.qq.com/$purl"
                        Log.d(TAG, "获取到播放链接: ${playUrl.take(80)}...")
                        return@withContext playUrl
                    }
                }
                // 回退到 Meting API
                tryMetingUrl(songmid)
            } catch (e: Exception) {
                Log.e(TAG, "获取QQ音乐播放链接失败", e)
                tryMetingUrl(songmid)
            }
        }

    private suspend fun tryMetingUrl(songmid: String): String =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("${METING_URL}$songmid")
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

    private fun httpPostJson(urlStr: String, body: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.doOutput = true
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        OutputStreamWriter(conn.outputStream).use { it.write(body) }
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val text = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        conn.disconnect()
        return text
    }
}

/**
 * QQ音乐歌曲数据类
 */
data class QQMusicSong(
    val songmid: String,
    val songid: String = "",
    val name: String,
    val singer: String,
    val albumname: String = "",
    val coverUrl: String = "",
    val durationMs: Long = 0
) {
    fun toSong(): Song = Song(
        id = "qqmusic_$songmid",
        title = name,
        artist = singer,
        album = albumname,
        coverUrl = coverUrl,
        url = "",
        durationMs = durationMs,
        format = "m4a",
        isLocal = false,
        source = "qqmusic"
    )
}