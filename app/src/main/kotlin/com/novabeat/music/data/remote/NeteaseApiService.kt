package com.novabeat.music.data.remote

import android.util.Log
import com.novabeat.music.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.random.Random

class NeteaseApiService(private val cookie: String = "") {

    companion object {
        private const val TAG = "NeteaseApi"
        private const val SECRET = "lengyu520"
        
        // 网易云官方API + 备用
        private val API_ENDPOINTS = listOf(
            "https://music.163.com",  // 网易云官方
            "https://api.injahow.cn/meting/"  // Meting
        )
    }

    private val randomTexts = listOf(
        "分享一首好听的歌",
        "音乐是生活的调味剂",
        "希望你喜欢这首歌",
        "深夜治愈系歌单",
        "听见好声音"
    )

    // ---------- 核心算法（复刻接口逻辑） ----------
    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun urlEncode(text: String): String =
        URLEncoder.encode(text, "UTF-8")

    private fun calculateSign(content: String): String =
        md5(SECRET + urlEncode(content))

    // ---------- HTTP 工具 ----------
    private suspend fun httpPostJson(urlStr: String, body: String): String =
        withContext(Dispatchers.IO) {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.doOutput = true
            if (cookie.isNotBlank()) {
                conn.setRequestProperty("Cookie", cookie)
            }
            OutputStreamWriter(conn.outputStream).use { it.write(body) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            BufferedReader(InputStreamReader(stream)).use { it.readText() }
        }
    
    // 尝试多个API端点的辅助方法
    private suspend fun <T> tryEndpoints(block: suspend (apiBase: String) -> T): T {
        var lastException: Exception? = null
        for (endpoint in API_ENDPOINTS) {
            try {
                return block(endpoint)
            } catch (e: Exception) {
                Log.w(TAG, "Endpoint $endpoint failed: ${e.message}")
                lastException = e
            }
        }
        throw lastException ?: Exception("All API endpoints failed")
    }

    private suspend fun httpGet(urlStr: String): String =
        withContext(Dispatchers.IO) {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            if (cookie.isNotBlank()) {
                conn.setRequestProperty("Cookie", cookie)
            }
            conn.setRequestProperty("Referer", "https://music.163.com")
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            BufferedReader(InputStreamReader(stream)).use { it.readText() }
        }

    // ---------- 搜索音乐 ----------
    suspend fun searchMusic(songName: String, offset: Int = 0, limit: Int = 20): List<NeteaseSong> =
        withContext(Dispatchers.IO) {
            try {
                // 优先使用网易云官方API
                val resp = httpGet("https://music.163.com/api/search/get/web?s=${urlEncode(songName)}&type=1&limit=$limit&offset=$offset")
                Log.d(TAG, "搜索API响应: ${resp.take(200)}")
                
                // 解析网易云官方格式
                val jsonObj = org.json.JSONObject(resp)
                val code = jsonObj.optInt("code", -1)
                if (code == 200) {
                    val result = jsonObj.optJSONObject("result")
                    val songsArray = result?.optJSONArray("songs")
                    val songs = mutableListOf<NeteaseSong>()
                    if (songsArray != null) {
                        for (i in 0 until songsArray.length()) {
                            val songObj = songsArray.getJSONObject(i)
                            val id = songObj.optLong("id", 0)
                            val name = songObj.optString("name", "")
                            val artistsArray = songObj.optJSONArray("artists")
                            val artists = mutableListOf<NeteaseArtist>()
                            if (artistsArray != null) {
                                for (j in 0 until artistsArray.length()) {
                                    val artistObj = artistsArray.getJSONObject(j)
                                    artists.add(NeteaseArtist(name = artistObj.optString("name", "")))
                                }
                            }
                            val albumObj = songObj.optJSONObject("album")
                            val album = albumObj?.let {
                                NeteaseAlbum(
                                    name = it.optString("name", ""),
                                    picUrl = it.optString("picUrl", "").ifBlank {
                                        it.optString("img1v1Url", "")
                                    }
                                )
                            }
                            val duration = songObj.optInt("duration", 0)
                            songs.add(NeteaseSong(id = id, name = name, artists = artists, album = album, duration = duration))
                        }
                    }
                    Log.d(TAG, "搜索到 ${songs.size} 首歌曲")
                    songs
                } else {
                    Log.w(TAG, "API返回错误: $code")
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "searchMusic error", e)
                emptyList()
            }
        }

    // ---------- 获取歌曲播放链接 ----------
    suspend fun getMusicUrl(id: String, br: String = "320000"): MusicUrlData =
        withContext(Dispatchers.IO) {
            try {
                // 使用Meting API获取播放链接（会重定向到实际URL）
                val url = URL("https://api.injahow.cn/meting/?server=netease&type=url&id=$id")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.instanceFollowRedirects = false // 不自动跟随重定向
                
                val responseCode = conn.responseCode
                Log.d(TAG, "获取播放链接响应码: $responseCode")
                
                // 获取重定向URL
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                
                if (location != null) {
                    Log.d(TAG, "获取到播放链接: ${location.take(80)}...")
                    MusicUrlData(url = location, code = 200, type = "mp3")
                } else {
                    // 如果没有重定向，可能需要跟随
                    val conn2 = url.openConnection() as HttpURLConnection
                    conn2.requestMethod = "GET"
                    conn2.connectTimeout = 15000
                    conn2.readTimeout = 15000
                    val finalUrl = conn2.url.toString()
                    conn2.disconnect()
                    
                    if (finalUrl.contains("mp3") || finalUrl.contains("audio")) {
                        MusicUrlData(url = finalUrl, code = 200, type = "mp3")
                    } else {
                        MusicUrlData(url = "", code = 404)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "getMusicUrl error", e)
                MusicUrlData(url = "", code = 500)
            }
        }

    private fun calculateSignForMid(mid: String): String =
        md5(SECRET + mid)

    // ---------- 获取专辑封面 ----------
    suspend fun fetchAlbumCover(songId: String, cookie: String = ""): String =
        withContext(Dispatchers.IO) {
            try {
                // 方式1：通过歌曲详情API获取封面
                val resp = httpGet("https://music.163.com/api/song/detail/?ids=$songId")
                val jsonObj = org.json.JSONObject(resp)
                val songs = jsonObj.optJSONArray("songs")
                if (songs != null && songs.length() > 0) {
                    val songObj = songs.getJSONObject(0)
                    val albumObj = songObj.optJSONObject("album")
                    val picUrl = albumObj?.optString("picUrl", "") ?: ""
                    if (picUrl.isNotBlank()) {
                        Log.d(TAG, "获取到封面: ${picUrl.take(60)}")
                        return@withContext picUrl
                    }
                }
                // 方式2：通过Meting API获取封面
                val url = URL("https://api.injahow.cn/meting/?server=netease&type=pic&id=$songId")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.instanceFollowRedirects = false
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location != null) {
                    Log.d(TAG, "Meting封面: ${location.take(60)}")
                    return@withContext location
                }
                ""
            } catch (e: Exception) {
                Log.e(TAG, "fetchAlbumCover error", e)
                ""
            }
        }

    // ---------- 获取歌词 ----------
    suspend fun getLyric(id: String): LyricResult =
        withContext(Dispatchers.IO) {
            try {
                // 使用网易云官方API获取歌词
                val resp = httpGet("https://music.163.com/api/song/lyric?id=$id&lv=1&tv=1")
                Log.d(TAG, "获取歌词响应: ${resp.take(200)}")
                
                val jsonObj = org.json.JSONObject(resp)
                val lrcObj = jsonObj.optJSONObject("lrc")
                val tlyricObj = jsonObj.optJSONObject("tlyric")
                val lrc = lrcObj?.optString("lyric", "") ?: ""
                val tlyric = tlyricObj?.optString("lyric", "") ?: ""
                
                LyricResult(
                    rawLrc = lrc,
                    rawTlyric = tlyric,
                    lines = parseLrc(lrc) + parseLrc(tlyric, isTranslation = true)
                )
            } catch (e: Exception) {
                Log.e(TAG, "getLyric error", e)
                LyricResult()
            }
        }

    // ---------- LRC 解析 ----------
    fun parseLrc(rawLrc: String, isTranslation: Boolean = false): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val timeRegex = Regex("""\[(\d{2}):(\d{2})(?:\.(\d{2,3}))?\](.*)""")
        rawLrc.lines().forEach { line ->
            val match = timeRegex.find(line) ?: return@forEach
            val min = match.groupValues[1].toInt()
            val sec = match.groupValues[2].toInt()
            val ms = match.groupValues[3].let { if (it.length == 2) it.toInt() * 10 else it.toInt() }
            val text = match.groupValues[4].trim()
            if (text.isNotBlank()) {
                lines.add(LyricLine(
                    timeMs = min * 60000L + sec * 1000L + ms,
                    text = text,
                    translation = if (isTranslation) text else ""
                ))
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    // ---------- 登录验证（Cookie 存储） ----------
    fun getCookie(): String = cookie

    fun withCookie(cookie: String): NeteaseApiService =
        NeteaseApiService(cookie = cookie)

    // ---------- 随机文案 ----------
    fun getRandomText(): String =
        randomTexts[Random.nextInt(randomTexts.size)]
}
