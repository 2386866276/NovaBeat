package com.novabeat.music.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.audiofx.Equalizer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.novabeat.music.data.model.*
import com.novabeat.music.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PlayerService : Service() {

    companion object {
        const val TAG = "PlayerService"
        const val CHANNEL_ID = "novabeat_player_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY_PAUSE = "action_play_pause"
        const val ACTION_NEXT = "action_next"
        const val ACTION_PREV = "action_prev"
        const val ACTION_STOP = "action_stop"
    }

    private val binder = PlayerBinder()
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var mediaSession: MediaSession

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState

    private val queue = mutableListOf<Song>()
    private var currentIndex = -1
    private var currentSong: Song? = null

    internal var equalizer: Equalizer? = null
        private set

    val eqPresets = mapOf(
        "默认" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "流行" to listOf(2f, 3f, 4f, 1f, -1f, -2f, 0f, 1f, 2f, 2f),
        "摇滚" to listOf(4f, 3f, -1f, -2f, -2f, -1f, 1f, 3f, 4f, 4f),
        "古典" to listOf(3f, 2f, 0f, -1f, -2f, -1f, 0f, 2f, 3f, 3f),
        "电子" to listOf(3f, 2f, 1f, -1f, -2f, -1f, 1f, 3f, 4f, 4f),
        "爵士" to listOf(2f, 1f, 1f, -1f, -2f, -2f, -1f, 0f, 2f, 3f)
    )

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initPlayer()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "NovaBeat 播放器", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "音乐播放控制通知"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun initPlayer() {
        android.util.Log.d(TAG, "初始化播放器")
        try {
            exoPlayer = ExoPlayer.Builder(this)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.CONTENT_TYPE_MUSIC)
                        .build(), true
                )
                .setHandleAudioBecomingNoisy(true)
                .build()
            android.util.Log.d(TAG, "ExoPlayer创建成功")

            exoPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    android.util.Log.d(TAG, "播放状态变化: $playbackState")
                    when (playbackState) {
                        Player.STATE_ENDED -> playNext()
                        Player.STATE_READY -> {}
                    }
                }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    currentSong = currentSongFromQueue(exoPlayer.currentMediaItemIndex)
                    updateState { copy(currentSong = currentSong, currentIndex = exoPlayer.currentMediaItemIndex) }
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateState { copy(isPlaying = isPlaying) }
                }
            })

            mediaSession = MediaSession.Builder(this, exoPlayer).build()
            android.util.Log.d(TAG, "MediaSession创建成功")
            initEqualizer()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "初始化播放器失败", e)
        }
    }

    fun getAudioSessionId(): Int {
        // 尝试多种方式获取音频会话ID
        return try {
            // 方法1: 反射获取 audioSessionId 字段
            val field = exoPlayer.javaClass.getDeclaredField("audioSessionId")
            field.isAccessible = true
            val sessionId = field.getInt(exoPlayer)
            android.util.Log.d(TAG, "通过反射获取音频会话ID: $sessionId")
            sessionId
        } catch (e1: Exception) {
            android.util.Log.w(TAG, "反射获取audioSessionId失败: ${e1.message}")
            try {
                // 方法2: 尝试其他可能的字段名
                val field = exoPlayer.javaClass.getDeclaredField("mAudioSessionId")
                field.isAccessible = true
                val sessionId = field.getInt(exoPlayer)
                android.util.Log.d(TAG, "通过mAudioSessionId字段获取: $sessionId")
                sessionId
            } catch (e2: Exception) {
                android.util.Log.w(TAG, "所有反射方法都失败，返回默认值")
                C.AUDIO_SESSION_ID_UNSET
            }
        }
    }

    private fun initEqualizer() {
        android.util.Log.d(TAG, "初始化均衡器")
        try {
            val sessionId = getAudioSessionId()
            android.util.Log.d(TAG, "音频会话ID: $sessionId")
            if (sessionId > 0 && sessionId != C.AUDIO_SESSION_ID_UNSET) {
                equalizer?.release()
                equalizer = Equalizer(0, sessionId).apply {
                    enabled = true
                    android.util.Log.d(TAG, "均衡器已启用, 频段数: $numberOfBands")
                }
                android.util.Log.d(TAG, "均衡器初始化成功")
            } else {
                android.util.Log.w(TAG, "音频会话ID无效 ($sessionId)，等待播放时初始化")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "均衡器初始化失败", e)
        }
    }

    fun setEqualizerBand(index: Int, value: Float) {
        try {
            equalizer?.setBandLevel(index.toShort(), (value * 50).toInt().coerceIn(-100, 100).toShort())
        } catch (e: Exception) { android.util.Log.e("PlayerService", "EQ set failed", e) }
    }

    fun applyPreset(name: String) {
        eqPresets[name]?.let { bands -> bands.forEachIndexed { i, v -> setEqualizerBand(i, v) } }
    }

    fun setQueue(songs: List<Song>, startIndex: Int = 0) {
        android.util.Log.d(TAG, "设置播放队列，歌曲数量: ${songs.size}")
        if (songs.isEmpty()) {
            android.util.Log.w(TAG, "播放队列为空")
            return
        }
        queue.clear()
        queue.addAll(songs)
        currentIndex = startIndex.coerceIn(songs.indices)
        exoPlayer.setMediaItems(
            songs.map { MediaItem.Builder().setUri(it.url).setMediaId(it.id).build() },
            currentIndex, 0
        )
        exoPlayer.prepare()
        currentSong = songs.getOrNull(currentIndex)
        updateState {
            copy(queue = songs, currentIndex = currentIndex,
                currentSong = currentSong, durationMs = currentSong?.durationMs ?: 0)
        }
        android.util.Log.d(TAG, "播放队列设置完成，当前歌曲: ${currentSong?.title}")
    }

    fun playSong(song: Song) {
        android.util.Log.d(TAG, "播放歌曲: ${song.title} - ${song.artist}")
        android.util.Log.d(TAG, "歌曲URL: ${song.url}")
        android.util.Log.d(TAG, "歌曲ID: ${song.id}")
        
        val idx = queue.indexOfFirst { it.id == song.id }
        val newIndex = if (idx >= 0) idx else { queue.add(song); queue.size - 1 }
        exoPlayer.setMediaItems(
            queue.map { MediaItem.Builder().setUri(it.url).setMediaId(it.id).build() },
            newIndex, 0
        )
        exoPlayer.prepare()
        exoPlayer.play()
        currentSong = song
        currentIndex = newIndex
        updateState {
            copy(currentSong = song, currentIndex = newIndex, isPlaying = true,
                queue = queue.toList(), durationMs = song.durationMs)
        }
        android.util.Log.d(TAG, "播放命令已发送")
        
        // 延迟初始化均衡器（需要等 ExoPlayer 准备好音频会话）
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            initEqualizer()
            // 通知 UI 更新
            _playbackState.value = _playbackState.value.copy()
        }, 500)
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun playNext() {
        if (queue.isEmpty()) return
        playIndex((currentIndex + 1) % queue.size)
    }

    fun playPrev() {
        if (queue.isEmpty()) return
        playIndex(if (currentIndex - 1 < 0) queue.size - 1 else currentIndex - 1)
    }

    fun seekTo(positionMs: Long) { exoPlayer.seekTo(positionMs) }

    private fun playIndex(index: Int) {
        exoPlayer.seekTo(index, 0)
        exoPlayer.play()
        currentIndex = index
        currentSong = currentSongFromQueue(index)
        updateState {
            copy(currentIndex = index, currentSong = currentSong,
                isPlaying = true, durationMs = currentSong?.durationMs ?: 0)
        }
    }

    private fun currentSongFromQueue(index: Int): Song? = queue.getOrNull(index)

    private fun updateState(update: PlaybackState.() -> PlaybackState) {
        _playbackState.value = _playbackState.value.update()
    }

    fun buildNotification(song: Song?): Notification {
        val contentIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(song?.title ?: "NovaBeat")
            .setContentText(song?.artist ?: "")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(exoPlayer.isPlaying)
            .addAction(android.R.drawable.ic_media_previous, "上一首", getPendingIntent(ACTION_PREV))
            .addAction(
                if (exoPlayer.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (exoPlayer.isPlaying) "暂停" else "播放",
                getPendingIntent(ACTION_PLAY_PAUSE)
            )
            .addAction(android.R.drawable.ic_media_next, "下一首", getPendingIntent(ACTION_NEXT))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun getPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, PlayerService::class.java).setAction(action)
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(NOTIFICATION_ID, buildNotification(currentSong))
        } catch (e: Exception) {
            android.util.Log.e("PlayerService", "startForeground failed", e)
            // 如果前台服务启动失败，尝试不带通知启动
            // 但这样在Android 12+可能会被系统停止
        }
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_NEXT -> playNext()
            ACTION_PREV -> playPrev()
            ACTION_STOP -> { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    inner class PlayerBinder : Binder() {
        fun getService(): PlayerService = this@PlayerService
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer.release()
        mediaSession.release()
        equalizer?.release()
    }
}