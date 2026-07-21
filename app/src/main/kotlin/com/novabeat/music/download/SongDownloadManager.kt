package com.novabeat.music.download

import android.content.Context
import android.os.Environment
import android.util.Log
import com.novabeat.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 歌曲下载管理器
 */
class SongDownloadManager(private val context: Context) {

    companion object {
        private const val TAG = "DownloadManager"
    }

    /**
     * 下载歌曲到 Download 目录
     * @return 下载的文件路径，失败返回 null
     */
    suspend fun downloadSong(song: Song, url: String): String? =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "开始下载: ${song.title}")

                // 创建下载目录
                val downloadDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    "NovaBeat"
                )
                if (!downloadDir.exists()) downloadDir.mkdirs()

                // 生成文件名
                val safeTitle = song.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val safeArtist = song.artist.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val ext = if (song.format.isNotBlank()) song.format else "mp3"
                val fileName = "${safeArtist} - ${safeTitle}.${ext}"
                val outputFile = File(downloadDir, fileName)

                // 如果文件已存在，直接返回
                if (outputFile.exists() && outputFile.length() > 0) {
                    Log.d(TAG, "文件已存在: ${outputFile.absolutePath}")
                    return@withContext outputFile.absolutePath
                }

                // 开始下载
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 30000
                conn.readTimeout = 30000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13)")
                conn.instanceFollowRedirects = true

                val responseCode = conn.responseCode
                if (responseCode !in 200..299) {
                    Log.e(TAG, "下载失败, HTTP $responseCode")
                    conn.disconnect()
                    return@withContext null
                }

                val inputStream: InputStream = conn.inputStream
                val outputStream = FileOutputStream(outputFile)
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytes = 0L
                val contentLength = conn.contentLength.toLong()

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytes += bytesRead
                    if (contentLength > 0) {
                        val progress = (totalBytes * 100 / contentLength).toInt()
                        Log.d(TAG, "下载进度: $progress%")
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()
                conn.disconnect()

                Log.d(TAG, "下载完成: ${outputFile.absolutePath}, 大小: ${outputFile.length()}")
                outputFile.absolutePath
            } catch (e: Exception) {
                Log.e(TAG, "下载失败", e)
                null
            }
        }

    /**
     * 检查歌曲是否已下载
     */
    fun isDownloaded(song: Song): Boolean {
        val downloadDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "NovaBeat"
        )
        val safeTitle = song.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val safeArtist = song.artist.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val ext = if (song.format.isNotBlank()) song.format else "mp3"
        val fileName = "${safeArtist} - ${safeTitle}.${ext}"
        val file = File(downloadDir, fileName)
        return file.exists() && file.length() > 0
    }

    /**
     * 获取已下载歌曲列表
     */
    fun getDownloadedSongs(): List<File> {
        val downloadDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "NovaBeat"
        )
        if (!downloadDir.exists()) return emptyList()
        return downloadDir.listFiles()?.toList() ?: emptyList()
    }
}