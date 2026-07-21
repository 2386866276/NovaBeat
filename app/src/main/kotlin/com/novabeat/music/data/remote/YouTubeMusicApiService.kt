package com.novabeat.music.data.remote

import android.util.Log
import com.novabeat.music.data.model.Song
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
 * YouTube Music source. Innertube is used first, while the official Data API
 * remains an optional search fallback when an API key is configured.
 */
class YouTubeMusicApiService(
    private val officialApiKey: String = "",
    private val cookie: String = ""
) {
    companion object {
        private const val TAG = "YouTubeMusicApi"
        private const val MUSIC_ORIGIN = "https://music.youtube.com"
        private const val INNERTUBE_SEARCH = "$MUSIC_ORIGIN/youtubei/v1/search?prettyPrint=false"
        private const val INNERTUBE_PLAYER = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
        private const val OFFICIAL_SEARCH = "https://www.googleapis.com/youtube/v3/search"

        private val PIPED_INSTANCES = listOf(
            "https://pipedapi.adminforge.de",
            "https://pipedapi.reallyaweso.me",
            "https://pipedapi.kavin.rocks"
        )
    }

    fun withCredentials(apiKey: String, cookie: String = ""): YouTubeMusicApiService =
        YouTubeMusicApiService(apiKey, cookie)

    suspend fun searchMusic(keyword: String, limit: Int = 20): List<YouTubeMusicSong> =
        withContext(Dispatchers.IO) {
            val unofficial = runCatching { searchInnertube(keyword, limit) }
                .onFailure { Log.w(TAG, "Innertube search failed", it) }
                .getOrDefault(emptyList())
            if (unofficial.isNotEmpty()) return@withContext unofficial

            if (officialApiKey.isNotBlank()) {
                return@withContext runCatching { searchOfficial(keyword, limit) }
                    .onFailure { Log.w(TAG, "Official search fallback failed", it) }
                    .getOrDefault(emptyList())
            }
            emptyList()
        }

    suspend fun getPlayUrl(videoId: String): String = withContext(Dispatchers.IO) {
        runCatching { resolveInnertubePlayer(videoId) }
            .onFailure { Log.w(TAG, "Innertube player failed", it) }
            .getOrDefault("")
            .takeIf { it.isNotBlank() }
            ?: resolveWithPiped(videoId)
    }

    private fun searchInnertube(keyword: String, limit: Int): List<YouTubeMusicSong> {
        val body = JSONObject()
            .put("context", webMusicContext())
            .put("query", keyword)
            .put("params", "EgWKAQIIAWoKEAMQBBAJEAoQBQ==")

        val response = JSONObject(httpPost(INNERTUBE_SEARCH, body.toString(), musicHeaders()))
        val renderers = mutableListOf<JSONObject>()
        collectObjects(response, "musicResponsiveListItemRenderer", renderers)

        return renderers.mapNotNull { parseMusicRenderer(it) }
            .distinctBy { it.videoId }
            .take(limit)
    }

    private fun parseMusicRenderer(renderer: JSONObject): YouTubeMusicSong? {
        val videoId = renderer.optJSONObject("playlistItemData")?.optString("videoId")
            .orEmpty().ifBlank { findFirstString(renderer, "videoId") }
        if (videoId.isBlank()) return null

        val columns = renderer.optJSONArray("flexColumns") ?: return null
        val titleRuns = columnRuns(columns, 0)
        val detailRuns = columnRuns(columns, 1)
        val title = titleRuns.optJSONObject(0)?.optString("text").orEmpty()
        if (title.isBlank()) return null

        val details = (0 until detailRuns.length())
            .mapNotNull { detailRuns.optJSONObject(it)?.optString("text") }
            .filter { it.isNotBlank() && it != " • " }
        val durationText = details.lastOrNull { it.matches(Regex("\\d{1,2}:\\d{2}(:\\d{2})?")) }.orEmpty()
        val cleanDetails = details.filterNot { it == durationText || it == "Song" || it == "歌曲" }
        val artist = cleanDetails.firstOrNull().orEmpty().ifBlank { "Unknown artist" }
        val album = cleanDetails.drop(1).firstOrNull().orEmpty()

        val thumbnail = renderer.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?.let { thumbnails -> thumbnails.optJSONObject(thumbnails.length() - 1)?.optString("url") }
            .orEmpty()

        return YouTubeMusicSong(videoId, title, artist, album, thumbnail, parseDuration(durationText))
    }

    private fun searchOfficial(keyword: String, limit: Int): List<YouTubeMusicSong> {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "$OFFICIAL_SEARCH?part=snippet&type=video&videoCategoryId=10&maxResults=$limit&q=$encoded&key=$officialApiKey"
        val items = JSONObject(httpGet(url, defaultHeaders())).optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val videoId = item.optJSONObject("id")?.optString("videoId").orEmpty()
            val snippet = item.optJSONObject("snippet") ?: return@mapNotNull null
            if (videoId.isBlank()) return@mapNotNull null
            val thumbnails = snippet.optJSONObject("thumbnails")
            val cover = thumbnails?.optJSONObject("high")?.optString("url")
                ?: thumbnails?.optJSONObject("medium")?.optString("url").orEmpty()
            YouTubeMusicSong(
                videoId = videoId,
                title = decodeHtml(snippet.optString("title")),
                artist = decodeHtml(snippet.optString("channelTitle")),
                coverUrl = cover
            )
        }
    }

    private fun resolveInnertubePlayer(videoId: String): String {
        val client = JSONObject()
            .put("clientName", "ANDROID")
            .put("clientVersion", "19.44.38")
            .put("androidSdkVersion", 35)
            .put("hl", "en")
            .put("gl", "US")
        val body = JSONObject()
            .put("context", JSONObject().put("client", client))
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)

        val response = JSONObject(httpPost(INNERTUBE_PLAYER, body.toString(), defaultHeaders()))
        val streamingData = response.optJSONObject("streamingData") ?: return ""
        return bestAudioUrl(streamingData.optJSONArray("adaptiveFormats"))
            .ifBlank { bestAudioUrl(streamingData.optJSONArray("formats")) }
    }

    private fun resolveWithPiped(videoId: String): String {
        for (instance in PIPED_INSTANCES) {
            val url = runCatching {
                val response = JSONObject(httpGet("$instance/streams/$videoId", defaultHeaders()))
                val streams = response.optJSONArray("audioStreams")
                bestPipedAudioUrl(streams)
            }.onFailure { Log.w(TAG, "Piped instance failed: $instance", it) }
                .getOrDefault("")
            if (url.isNotBlank()) return url
        }
        return ""
    }

    private fun bestAudioUrl(formats: JSONArray?): String {
        if (formats == null) return ""
        return (0 until formats.length()).mapNotNull { formats.optJSONObject(it) }
            .filter { it.optString("mimeType").startsWith("audio/") && it.optString("url").startsWith("http") }
            .maxByOrNull { it.optInt("bitrate", 0) }
            ?.optString("url").orEmpty()
    }

    private fun bestPipedAudioUrl(streams: JSONArray?): String {
        if (streams == null) return ""
        return (0 until streams.length()).mapNotNull { streams.optJSONObject(it) }
            .filter { it.optString("url").startsWith("http") }
            .maxByOrNull { it.optInt("bitrate", 0) }
            ?.optString("url").orEmpty()
    }

    private fun webMusicContext(): JSONObject = JSONObject().put(
        "client",
        JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", "1.20250219.01.00")
            .put("hl", "zh-CN")
            .put("gl", "CN")
    )

    private fun musicHeaders(): Map<String, String> = defaultHeaders() + mapOf(
        "Origin" to MUSIC_ORIGIN,
        "Referer" to "$MUSIC_ORIGIN/"
    ) + if (cookie.isNotBlank()) mapOf("Cookie" to cookie) else emptyMap()

    private fun defaultHeaders(): Map<String, String> = mapOf(
        "User-Agent" to "com.google.android.youtube/19.44.38 (Linux; U; Android 15) gzip",
        "Accept" to "application/json",
        "Content-Type" to "application/json"
    )

    private fun httpGet(url: String, headers: Map<String, String>): String = request(url, "GET", null, headers)

    private fun httpPost(url: String, body: String, headers: Map<String, String>): String =
        request(url, "POST", body, headers)

    private fun request(url: String, method: String, body: String?, headers: Map<String, String>): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            if (body != null) {
                connection.doOutput = true
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.let { BufferedReader(InputStreamReader(it)).use(BufferedReader::readText) }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("HTTP $code: ${text.take(160)}")
            return text
        } finally {
            connection.disconnect()
        }
    }

    private fun columnRuns(columns: JSONArray, index: Int): JSONArray = columns.optJSONObject(index)
        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
        ?.optJSONObject("text")
        ?.optJSONArray("runs") ?: JSONArray()

    private fun collectObjects(node: Any?, key: String, output: MutableList<JSONObject>) {
        when (node) {
            is JSONObject -> {
                node.optJSONObject(key)?.let(output::add)
                val keys = node.keys()
                while (keys.hasNext()) collectObjects(node.opt(keys.next()), key, output)
            }
            is JSONArray -> for (i in 0 until node.length()) collectObjects(node.opt(i), key, output)
        }
    }

    private fun findFirstString(node: Any?, key: String): String {
        when (node) {
            is JSONObject -> {
                val direct = node.optString(key)
                if (direct.isNotBlank()) return direct
                val keys = node.keys()
                while (keys.hasNext()) {
                    val result = findFirstString(node.opt(keys.next()), key)
                    if (result.isNotBlank()) return result
                }
            }
            is JSONArray -> for (i in 0 until node.length()) {
                val result = findFirstString(node.opt(i), key)
                if (result.isNotBlank()) return result
            }
        }
        return ""
    }

    private fun parseDuration(value: String): Long = runCatching {
        value.split(":").fold(0L) { total, part -> total * 60 + part.toLong() } * 1000
    }.getOrDefault(0L)

    private fun decodeHtml(value: String): String {
        var result = value
        result = result.replace("&", "&")
        result = result.replace(String(charArrayOf('&','q','u','o','t',';')), String(charArrayOf('"')))
        result = result.replace("&#39;", "'")
        result = result.replace("<", "<")
        result = result.replace(">", ">")
        return result
    }
}

data class YouTubeMusicSong(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val coverUrl: String = "",
    val durationMs: Long = 0
) {
    fun toSong(): Song = Song(
        id = "youtube_$videoId",
        title = title,
        artist = artist,
        album = album,
        coverUrl = coverUrl,
        url = "",
        durationMs = durationMs,
        format = "webm/m4a",
        isLocal = false,
        source = "youtube"
    )
}
