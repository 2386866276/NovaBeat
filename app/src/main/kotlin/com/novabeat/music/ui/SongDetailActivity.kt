package com.novabeat.music.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
import com.novabeat.music.R
import com.novabeat.music.audio.AudioEffectManager
import com.novabeat.music.data.model.LyricLine
import com.novabeat.music.data.model.Song
import com.novabeat.music.data.repository.MusicRepository
import com.novabeat.music.download.SongDownloadManager
import com.novabeat.music.service.PlayerService
import com.novabeat.music.ui.views.WaveSeekBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SongDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SONG_ID = "song_id"
        const val EXTRA_SONG_TITLE = "song_title"
        const val EXTRA_SONG_ARTIST = "song_artist"
        const val EXTRA_SONG_COVER = "song_cover"
        const val EXTRA_SONG_DURATION = "song_duration"
        const val EXTRA_SONG_SOURCE = "song_source"
        private const val TAG = "SongDetailActivity"
        private const val REQUEST_LOGIN = 1001

        // 歌词动画类型
        private const val LYRIC_SCROLL = 0
        private const val LYRIC_FADE = 1
        private const val LYRIC_SLIDE = 2
        private const val LYRIC_ZOOM = 3
        private const val LYRIC_KARAOKE = 4
    }

    private var playerService: PlayerService? = null
    private var bound = false
    private var song: Song? = null
    private lateinit var repository: MusicRepository
    private lateinit var downloadManager: SongDownloadManager
    private var isPlaying = false
    private var currentSpeed = 1.0f
    private var useWaveSeekBar = false

    // 歌词相关
    private var lyricsView: TextView? = null
    private var lyricsContainer: View? = null
    private var lyricsScrollView: ScrollView? = null
    private var lyricLines: List<LyricLine> = emptyList()
    private var currentLyricIndex = -1
    private var lyricStyle = LYRIC_SCROLL
    private var hasStartedPlaying = false

    // 音频效果
    private var audioEffectManager: AudioEffectManager? = null

    // 进度条
    private var waveSeekBar: WaveSeekBar? = null
    private var normalSeekBar: SeekBar? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            playerService = (binder as PlayerService.PlayerBinder).getService()
            bound = true
            observePlayback()
            // 更新收藏状态
            updateFavoriteIcon()
            // 初始化音频效果
            val sessionId = playerService?.getAudioSessionId() ?: 0
            audioEffectManager = AudioEffectManager()
            audioEffectManager?.init(sessionId)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            playerService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_song_detail)

        repository = MusicRepository(this)
        downloadManager = SongDownloadManager(this)

        val songId = intent.getStringExtra(EXTRA_SONG_ID) ?: ""
        val title = intent.getStringExtra(EXTRA_SONG_TITLE) ?: "未知歌曲"
        val artist = intent.getStringExtra(EXTRA_SONG_ARTIST) ?: "未知艺术家"
        val cover = intent.getStringExtra(EXTRA_SONG_COVER) ?: ""
        val duration = intent.getLongExtra(EXTRA_SONG_DURATION, 0L)
        val source = intent.getStringExtra(EXTRA_SONG_SOURCE) ?: "netease"

        song = Song(
            id = songId, title = title, artist = artist, album = "",
            coverUrl = cover, url = "", durationMs = duration, source = source
        )

        initViews(title, artist, cover)
        bindService()
    }

    private fun initViews(title: String, artist: String, cover: String) {
        findViewById<TextView>(R.id.tvDetailTitle).text = title
        findViewById<TextView>(R.id.tvDetailArtist).text = artist

        // 封面加载
        val ivCover = findViewById<ImageView>(R.id.ivDetailCover)
        if (cover.isNotBlank()) {
            Glide.with(this).load(cover).placeholder(R.drawable.ic_music_note).centerCrop().into(ivCover)
        } else {
            // 从网易云获取封面
            lifecycleScope.launch {
                try {
                    val coverUrl = withContext(Dispatchers.IO) {
                        repository.fetchAlbumCover(song!!.id)
                    }
                    if (coverUrl.isNotBlank()) {
                        Glide.with(this@SongDetailActivity).load(coverUrl)
                            .placeholder(R.drawable.ic_music_note).centerCrop().into(ivCover)
                        // 更新song对象
                        song = song?.copy(coverUrl = coverUrl)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "获取封面失败", e)
                }
            }
        }

        // 返回按钮
        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }

        // 播放按钮
        findViewById<MaterialButton>(R.id.btnDetailPlay).setOnClickListener {
            if (hasStartedPlaying && isPlaying) {
                playerService?.togglePlayPause()
            } else if (hasStartedPlaying && !isPlaying) {
                playerService?.togglePlayPause()
            } else {
                playSong()
            }
        }

        // 收藏按钮
        findViewById<MaterialButton>(R.id.btnFavorite).setOnClickListener {
            song?.let { s ->
                val fav = playerService?.toggleFavorite(s.id) ?: false
                Toast.makeText(this, if (fav) "已收藏" else "取消收藏", Toast.LENGTH_SHORT).show()
                updateFavoriteIcon()
            }
        }

        // 分享按钮 — 显示文字而非真正跳转
        findViewById<MaterialButton>(R.id.btnShare).setOnClickListener {
            song?.let { s ->
                val shareText = "我在 NovaBeat 听「${s.title}」- ${s.artist}，一起来听吧！"
                // 不调用系统分享，直接显示文字
                val shareDisplay = findViewById<TextView>(R.id.tvShareText)
                shareDisplay.text = shareText
                shareDisplay.visibility = View.VISIBLE
                // 3秒后自动隐藏
                shareDisplay.postDelayed({ shareDisplay.visibility = View.GONE }, 5000)
                Toast.makeText(this, "已复制分享文案到剪贴板", Toast.LENGTH_SHORT).show()
                // 复制到剪贴板
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("NovaBeat分享", shareText))
            }
        }

        // 下载按钮
        findViewById<MaterialButton>(R.id.btnDownload).setOnClickListener {
            song?.let { s ->
                if (s.url.isNotBlank()) {
                    downloadSong(s, s.url)
                } else {
                    // 先获取URL再下载
                    Toast.makeText(this, "正在获取下载链接...", Toast.LENGTH_SHORT).show()
                    lifecycleScope.launch {
                        try {
                            val url = withContext(Dispatchers.IO) {
                                when (s.source) {
                                    "bilibili" -> {
                                        val biliCookie = getSharedPreferences("novabeat", Context.MODE_PRIVATE)
                                            .getString("bili_cookie", "") ?: ""
                                        repository.resolveBilibiliUrl(s.id, biliCookie)
                                    }
                                    "qqmusic" -> {
                                        val qqCookie = getSharedPreferences("novabeat", Context.MODE_PRIVATE)
                                            .getString("qqmusic_cookie", "") ?: ""
                                        repository.resolveQQMusicUrl(s.id, qqCookie)
                                    }
                                    "kuwo" -> {
                                        val kuwoCookie = getSharedPreferences("novabeat", Context.MODE_PRIVATE)
                                            .getString("kuwo_cookie", "") ?: ""
                                        repository.resolveKuwoUrl(s.id, kuwoCookie)
                                    }
                                    "kugou" -> {
                                        val kugouCookie = getSharedPreferences("novabeat", Context.MODE_PRIVATE)
                                            .getString("kugou_cookie", "") ?: ""
                                        repository.resolveKugouUrl(s.id, kugouCookie)
                                    }
                                    "qishui" -> {
                                        val qishuiCookie = getSharedPreferences("novabeat", Context.MODE_PRIVATE)
                                            .getString("qishui_cookie", "") ?: ""
                                        repository.resolveQishuiUrl(s.id, qishuiCookie)
                                    }
                                    "spotify" -> {
                                        val token = getSharedPreferences("novabeat", Context.MODE_PRIVATE)
                                            .getString("spotify_token", "") ?: ""
                                        repository.resolveSpotifyUrl(s.id, token)
                                    }
                                    "youtube" -> {
                                        val preferences = getSharedPreferences("novabeat", Context.MODE_PRIVATE)
                                        repository.resolveYouTubeMusicUrl(
                                            s.id,
                                            preferences.getString("youtube_api_key", "") ?: "",
                                            preferences.getString("youtube_cookie", "") ?: ""
                                        )
                                    }
                                    else -> {
                                        val neteaseCookie = getSharedPreferences("novabeat", Context.MODE_PRIVATE)
                                            .getString("netease_cookie", "") ?: ""
                                        repository.resolveNeteaseUrl(s.id, neteaseCookie)
                                    }
                                }
                            }
                            if (url.isNotBlank()) {
                                downloadSong(s, url)
                            } else {
                                Toast.makeText(this@SongDetailActivity, "获取下载链接失败", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this@SongDetailActivity, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        // 倍速选择
        val speedSlider = findViewById<Slider>(R.id.speedSlider)
        val tvSpeed = findViewById<TextView>(R.id.tvSpeed)
        speedSlider.value = 1.0f
        speedSlider.addOnChangeListener { _, value, _ ->
            currentSpeed = value
            tvSpeed.text = String.format("%.1fx", value)
            playerService?.setPlaybackSpeed(value)
        }

        // 歌词容器
        lyricsView = findViewById(R.id.tvLyrics)
        lyricsContainer = findViewById(R.id.lyricsContainer)
        lyricsScrollView = findViewById(R.id.lyricsScrollView)

        // 歌词动画效果选择
        val chipGroupLyric = findViewById<ChipGroup>(R.id.chipGroupLyricStyle)
        chipGroupLyric.check(R.id.chipLyricScroll)
        chipGroupLyric.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                lyricStyle = when (checkedIds[0]) {
                    R.id.chipLyricScroll -> LYRIC_SCROLL
                    R.id.chipLyricFade -> LYRIC_FADE
                    R.id.chipLyricSlide -> LYRIC_SLIDE
                    R.id.chipLyricZoom -> LYRIC_ZOOM
                    R.id.chipLyricKaraoke -> LYRIC_KARAOKE
                    else -> LYRIC_SCROLL
                }
                Log.d(TAG, "歌词动画切换为: $lyricStyle")
                // 重新渲染歌词以应用新动画
                renderLyrics()
            }
        }

        // 音频效果选择
        val chipGroupEffect = findViewById<ChipGroup>(R.id.chipGroupAudioEffect)
        chipGroupEffect.check(R.id.chipEffectNone)
        chipGroupEffect.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val effect = when (checkedIds[0]) {
                    R.id.chipEffectNone -> AudioEffectManager.AudioEffectType.NONE
                    R.id.chipEffectVocal -> AudioEffectManager.AudioEffectType.VOCAL_REMOVER
                    R.id.chipEffectEthereal -> AudioEffectManager.AudioEffectType.ETHEREAL
                    R.id.chipEffectBass -> AudioEffectManager.AudioEffectType.BASS_BOOST
                    R.id.chipEffectStudio -> AudioEffectManager.AudioEffectType.STUDIO
                    R.id.chipEffectConcert -> AudioEffectManager.AudioEffectType.CONCERT
                    else -> AudioEffectManager.AudioEffectType.NONE
                }
                audioEffectManager?.applyEffect(effect)
                // 同时应用 EQ 预设
                audioEffectManager?.getEQPresetForEffect(effect)?.let { preset ->
                    preset.forEachIndexed { i, v -> playerService?.setEqualizerBand(i, v) }
                }
                Toast.makeText(this, "已切换: ${effect.displayName}", Toast.LENGTH_SHORT).show()
            }
        }

        // 进度条 — 支持波浪/普通切换
        waveSeekBar = findViewById(R.id.waveSeekBar)
        normalSeekBar = findViewById(R.id.seekBarDetail)
        val cbWaveProgress = findViewById<CheckBox>(R.id.cbWaveProgress)
        cbWaveProgress.setOnCheckedChangeListener { _, isChecked ->
            useWaveSeekBar = isChecked
            if (isChecked) {
                waveSeekBar?.visibility = View.VISIBLE
                normalSeekBar?.visibility = View.GONE
            } else {
                waveSeekBar?.visibility = View.GONE
                normalSeekBar?.visibility = View.VISIBLE
            }
        }

        // 普通进度条
        normalSeekBar?.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) playerService?.seekTo(progress.toLong())
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }
        )

        // 波浪进度条
        waveSeekBar?.setOnProgressChangeListener(object : WaveSeekBar.OnWaveProgressChangeListener {
            override fun onProgressChanged(progress: Int, fromUser: Boolean) {
                if (fromUser) playerService?.seekTo(progress.toLong())
            }
            override fun onStartTrackingTouch() {}
            override fun onStopTrackingTouch() {}
        })
    }

    private fun downloadSong(song: Song, url: String) {
        Toast.makeText(this, "开始下载: ${song.title}", Toast.LENGTH_SHORT).show()
        val btnDownload = findViewById<MaterialButton>(R.id.btnDownload)
        btnDownload.isEnabled = false
        lifecycleScope.launch {
            try {
                val path = withContext(Dispatchers.IO) { downloadManager.downloadSong(song, url) }
                if (path != null) {
                    Toast.makeText(this@SongDetailActivity, "下载完成: $path", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@SongDetailActivity, "下载失败，请检查网络", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SongDetailActivity, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                btnDownload.isEnabled = true
            }
        }
    }

    private fun updateFavoriteIcon() {
        val s = song ?: return
        val isFav = playerService?.isFavorite(s.id) ?: false
        findViewById<MaterialButton>(R.id.btnFavorite).icon = if (isFav)
            ContextCompat.getDrawable(this, android.R.drawable.btn_star_big_on)
        else
            ContextCompat.getDrawable(this, android.R.drawable.btn_star_big_off)
    }

    private fun playSong() {
        val s = song ?: return
        Toast.makeText(this, "正在获取播放链接...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val url = withContext(Dispatchers.IO) {
                    when (s.source) {
                        "bilibili" -> {
                            // B站歌曲 — 使用B站API获取播放链接（需要SESSDATA Cookie）
                            val biliCookie = getSharedPreferences("novabeat", Context.MODE_PRIVATE)
                                .getString("bili_cookie", "") ?: ""
                            if (biliCookie.isBlank()) {
                                Log.w(TAG, "未登录B站，播放可能失败")
                            }
                            repository.resolveBilibiliUrl(s.id, biliCookie)
                        }
                        "qqmusic" -> {
                            val qqCookie = getSharedPreferences("novabeat", Context.MODE_PRIVATE)
                                .getString("qqmusic_cookie", "") ?: ""
                            repository.resolveQQMusicUrl(s.id, qqCookie)
                        }
                        "kuwo" -> {
                            val kuwoCookie = getSharedPreferences("novabeat", Context.MODE_PRIVATE)
                                .getString("kuwo_cookie", "") ?: ""
                            repository.resolveKuwoUrl(s.id, kuwoCookie)
                        }
                        "kugou" -> {
                            val kugouCookie = getSharedPreferences("novabeat", Context.MODE_PRIVATE)
                                .getString("kugou_cookie", "") ?: ""
                            repository.resolveKugouUrl(s.id, kugouCookie)
                        }
                        "qishui" -> {
                            val qishuiCookie = getSharedPreferences("novabeat", Context.MODE_PRIVATE)
                                .getString("qishui_cookie", "") ?: ""
                            repository.resolveQishuiUrl(s.id, qishuiCookie)
                        }
                        "spotify" -> {
                            val token = getSharedPreferences("novabeat", Context.MODE_PRIVATE)
                                .getString("spotify_token", "") ?: ""
                            repository.resolveSpotifyUrl(s.id, token)
                        }
                        "youtube" -> {
                            val preferences = getSharedPreferences("novabeat", Context.MODE_PRIVATE)
                            repository.resolveYouTubeMusicUrl(
                                s.id,
                                preferences.getString("youtube_api_key", "") ?: "",
                                preferences.getString("youtube_cookie", "") ?: ""
                            )
                        }
                        else -> {
                            // 网易云歌曲 — 使用网易云 Cookie
                            val neteaseCookie = getSharedPreferences("novabeat", Context.MODE_PRIVATE)
                                .getString("netease_cookie", "") ?: ""
                            repository.resolveNeteaseUrl(s.id, neteaseCookie)
                        }
                    }
                }
                Log.d(TAG, "播放链接: ${url.take(50)}")
                if (url.isNotBlank()) {
                    val playable = s.copy(url = url)
                    song = playable
                    playerService?.playSong(playable)
                    hasStartedPlaying = true
                    loadLyrics(s.id)
                    // 重新初始化音频效果
                    val sessionId = playerService?.getAudioSessionId() ?: 0
                    audioEffectManager?.init(sessionId)
                } else {
                    val hint = when (s.source) {
                        "bilibili" -> "获取B站播放链接失败，请先登录哔哩哔哩账号"
                        "qqmusic" -> "获取QQ音乐播放链接失败，请先登录QQ音乐账号"
                        "kuwo" -> "获取酷我音乐播放链接失败"
                        "kugou" -> "获取酷狗音乐播放链接失败"
                        "qishui" -> "获取汽水音乐播放链接失败"
                        "spotify" -> "获取 Spotify 播放链接失败，请检查 Access Token"
                        "youtube" -> "获取 YouTube Music 播放链接失败，请检查网络或 Cookie"
                        else -> "获取播放链接失败，可能需要登录"
                    }
                    Toast.makeText(this@SongDetailActivity, hint, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "播放失败", e)
                Toast.makeText(this@SongDetailActivity, "播放失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadLyrics(songId: String) {
        lifecycleScope.launch {
            try {
                val lyric = withContext(Dispatchers.IO) { repository.fetchLyric(songId) }
                lyricLines = lyric.lines
                if (lyricLines.isNotEmpty()) {
                    lyricsContainer?.visibility = View.VISIBLE
                    renderLyrics()
                } else {
                    lyricsView?.text = "暂无歌词"
                    lyricsContainer?.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取歌词失败", e)
                lyricsView?.text = "获取歌词失败"
                lyricsContainer?.visibility = View.VISIBLE
            }
        }
    }

    private fun renderLyrics() {
        if (lyricLines.isEmpty()) return
        val sb = SpannableStringBuilder()
        lyricLines.forEachIndexed { index, line ->
            sb.append(line.text)
            if (index < lyricLines.size - 1) sb.append("\n")
        }
        lyricsView?.text = sb
        updateLyricsHighlight(0)
    }

    private fun observePlayback() {
        lifecycleScope.launch {
            playerService?.playbackState?.collectLatest { state ->
                val tvPos = findViewById<TextView>(R.id.tvCurrentPosDetail)
                val tvDur = findViewById<TextView>(R.id.tvDurationDetail)
                val btnPlay = findViewById<MaterialButton>(R.id.btnDetailPlay)

                isPlaying = state.isPlaying
                btnPlay.setIconResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)

                val dur = state.durationMs.toInt().coerceAtLeast(1)
                val pos = state.currentPositionMs.toInt().coerceIn(0, dur)

                // 同步两个进度条
                normalSeekBar?.max = dur
                normalSeekBar?.progress = pos
                waveSeekBar?.setMax(dur)
                waveSeekBar?.setProgress(pos)

                tvPos.text = formatTime(state.currentPositionMs)
                tvDur.text = formatTime(state.durationMs)

                // 更新歌词
                updateLyricsHighlight(state.currentPositionMs)
            }
        }
    }

    private fun updateLyricsHighlight(currentPos: Long) {
        if (lyricLines.isEmpty()) return

        // 找到当前歌词行
        var newIndex = -1
        for (i in lyricLines.indices) {
            if (lyricLines[i].timeMs <= currentPos) {
                newIndex = i
            } else {
                break
            }
        }

        if (newIndex == currentLyricIndex) return
        currentLyricIndex = newIndex
        if (newIndex < 0) return

        val sb = SpannableStringBuilder()
        val primaryColor = ContextCompat.getColor(this, R.color.purple_500)
        val normalColor = Color.parseColor("#888888")

        lyricLines.forEachIndexed { index, line ->
            val start = sb.length
            sb.append(line.text)
            val end = sb.length

            if (index == newIndex) {
                // 当前行高亮
                sb.setSpan(ForegroundColorSpan(primaryColor), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(StyleSpan(android.graphics.Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(AbsoluteSizeSpan(18, true), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (index == newIndex + 1 || index == newIndex - 1) {
                // 临近行稍亮
                sb.setSpan(ForegroundColorSpan(Color.parseColor("#AAAAAA")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(AbsoluteSizeSpan(16, true), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                // 其他行变暗
                sb.setSpan(ForegroundColorSpan(normalColor), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(AbsoluteSizeSpan(14, true), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            if (index < lyricLines.size - 1) sb.append("\n")
        }

        lyricsView?.text = sb

        // 应用动画效果
        applyLyricAnimation(newIndex)
    }

    private fun applyLyricAnimation(lyricIndex: Int) {
        val view = lyricsView ?: return
        view.clearAnimation()

        when (lyricStyle) {
            LYRIC_SCROLL -> {
                // 滚动到当前歌词行位置
                val lineCount = lyricLines.size
                if (lineCount > 0) {
                    val scrollView = lyricsScrollView ?: return
                    val textViewHeight = view.height
                    val lineHeight = if (lineCount > 0) textViewHeight / lineCount else 0
                    val targetY = (lyricIndex * lineHeight - scrollView.height / 2 + lineHeight / 2).coerceAtLeast(0)
                    scrollView.post { scrollView.smoothScrollTo(0, targetY) }
                }
            }
            LYRIC_FADE -> {
                // 淡入淡出
                val anim = AlphaAnimation(0.3f, 1.0f).apply {
                    duration = 500
                    fillAfter = true
                }
                view.startAnimation(anim)
            }
            LYRIC_SLIDE -> {
                // 滑入
                val anim = TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, 0.3f,
                    Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 0f
                ).apply {
                    duration = 400
                    fillAfter = true
                }
                view.startAnimation(anim)
            }
            LYRIC_ZOOM -> {
                // 缩放效果
                val anim = android.view.animation.ScaleAnimation(
                    0.9f, 1.0f, 0.9f, 1.0f,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f
                ).apply {
                    duration = 400
                    fillAfter = true
                }
                view.startAnimation(anim)
            }
            LYRIC_KARAOKE -> {
                // 卡拉OK：当前行文字颜色渐变（通过闪烁效果模拟）
                val anim = AlphaAnimation(0.7f, 1.0f).apply {
                    duration = 300
                    repeatMode = Animation.REVERSE
                    repeatCount = 1
                    fillAfter = true
                }
                view.startAnimation(anim)
            }
        }

        // 同时滚动ScrollView到当前行
        val scrollView = lyricsScrollView ?: return
        val lineCount = lyricLines.size
        if (lineCount > 0) {
            view.post {
                val totalHeight = view.height
                val lineHeight = if (lineCount > 0) totalHeight / lineCount else 0
                val targetY = (lyricIndex * lineHeight - scrollView.height / 3).coerceAtLeast(0)
                scrollView.smoothScrollTo(0, targetY)
            }
        }
    }

    private fun formatTime(ms: Long): String {
        val s = (ms / 1000).toInt()
        return "%d:%02d".format(s / 60, s % 60)
    }

    private fun bindService() {
        val intent = Intent(this, PlayerService::class.java)
        startService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        audioEffectManager?.release()
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }

}