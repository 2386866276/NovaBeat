package com.novabeat.music.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.audiofx.Visualizer
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.novabeat.music.R
import com.novabeat.music.data.model.*
import com.novabeat.music.service.PlayerService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlayerFragment : Fragment() {

    private var playerService: PlayerService? = null
    private var bound = false
    private var visualizer: Visualizer? = null
    private val adapter = QueueAdapter()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            playerService = (binder as PlayerService.PlayerBinder).getService()
            bound = true
            initVisualizer()
            observeState()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            playerService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindService()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_player, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<RecyclerView>(R.id.rvQueue).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@PlayerFragment.adapter
        }

        view.findViewById<MaterialButton>(R.id.btnPrev).setOnClickListener { playerService?.playPrev() }
        view.findViewById<MaterialButton>(R.id.btnNext).setOnClickListener { playerService?.playNext() }
        view.findViewById<MaterialButton>(R.id.btnPlayPause).setOnClickListener {
            playerService?.togglePlayPause()
        }

        // 分享按钮 — 显示文字而非真正跳转
        view.findViewById<MaterialButton>(R.id.btnShareMain).setOnClickListener {
            val state = playerService?.playbackState?.value
            val song = state?.currentSong
            if (song != null) {
                val shareText = "我在 NovaBeat 听「${song.title}」- ${song.artist}，一起来听吧！"
                val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("NovaBeat分享", shareText))
                Toast.makeText(requireContext(), shareText, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), "请先播放一首歌曲", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<SeekBar>(R.id.seekBar).apply {
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        playerService?.seekTo(progress.toLong())
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
    }

    private fun bindService() {
        val intent = Intent(requireContext(), PlayerService::class.java)
        requireContext().startService(intent) // 确保服务启动、ExoPlayer 初始化
        requireContext().bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun initVisualizer() {
        try {
            val service = playerService ?: return
            visualizer = Visualizer(service.getAudioSessionId()).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int
                    ) {}
                    override fun onFftDataCapture(
                        visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int
                    ) {
                        view?.post { updateVisualizerBars(fft ?: return@post) }
                    }
                }, Visualizer.getMaxCaptureRate(), true, false)
                enabled = true
            }
        } catch (e: Exception) {
            android.util.Log.e("PlayerFragment", "Visualizer init failed", e)
        }
    }

    private fun updateVisualizerBars(fft: ByteArray) {
        val view = view ?: return
        val bars = listOf(
            view.findViewById<View>(R.id.vizBar1),
            view.findViewById<View>(R.id.vizBar2),
            view.findViewById<View>(R.id.vizBar3),
            view.findViewById<View>(R.id.vizBar4),
            view.findViewById<View>(R.id.vizBar5),
            view.findViewById<View>(R.id.vizBar6),
            view.findViewById<View>(R.id.vizBar7)
        )
        val step = fft.size / bars.size
        bars.forEachIndexed { i, bar ->
            val value = kotlin.math.abs(fft[i * step].toInt())
            val height = (value / 128.0 * 200).toInt().coerceAtLeast(4)
            bar.layoutParams = (bar.layoutParams).apply { this.height = height }
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            playerService?.playbackState?.collectLatest { state ->
                view?.let { v ->
                    val currentSong = state.currentSong
                    if (currentSong != null) {
                        v.findViewById<TextView>(R.id.tvTitle).text = currentSong.title
                        v.findViewById<TextView>(R.id.tvArtist).text = currentSong.artist
                        v.findViewById<ImageView>(R.id.ivCover).also { iv ->
                            val cover = if (currentSong.coverUrl.isNotBlank()) {
                                Glide.with(this@PlayerFragment)
                                    .load(currentSong.coverUrl)
                                    .placeholder(R.drawable.ic_music_note)
                                    .centerCrop()
                                    .into(iv)
                            } else {
                                Glide.with(this@PlayerFragment)
                                    .load(R.drawable.ic_music_note)
                                    .centerCrop()
                                    .into(iv)
                            }
                        }
                        v.findViewById<MaterialButton>(R.id.btnPlayPause).setIconResource(
                            if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                        )
                        v.findViewById<TextView>(R.id.tvDuration).text =
                            formatTime(state.durationMs)
                        adapter.updateQueue(state.queue, state.currentIndex)

                        // 实时刷新当前播放位置
                        v.findViewById<SeekBar>(R.id.seekBar).apply {
                            max = state.durationMs.toInt().coerceAtLeast(1)
                            progress = state.currentPositionMs.toInt().coerceAtLeast(0)
                        }
                        v.findViewById<TextView>(R.id.tvCurrentPos).text =
                            formatTime(state.currentPositionMs)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        visualizer?.release()
        visualizer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bound) {
            requireContext().unbindService(connection)
            bound = false
        }
    }

    inner class QueueAdapter :
        RecyclerView.Adapter<QueueAdapter.VH>() {

        private var items: List<Song> = emptyList()
        private var currentIdx: Int = -1

        fun updateQueue(queue: List<Song>, index: Int) {
            this.items = queue
            this.currentIdx = index
            notifyDataSetChanged()
        }

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvTitle: TextView = itemView.findViewById(R.id.tvItemTitle)
            val tvArtist: TextView = itemView.findViewById(R.id.tvItemArtist)
            val indicator: View = itemView.findViewById(R.id.playingIndicator)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_queue_song, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val song = items.getOrNull(position) ?: return
            holder.tvTitle.text = song.title
            holder.tvArtist.text = song.artist
            holder.indicator.visibility = if (position == currentIdx) View.VISIBLE else View.GONE
            holder.itemView.setOnClickListener {
                playerService?.playSong(song)
            }
        }

        override fun getItemCount(): Int = items.size
    }

    private fun formatTime(ms: Long): String {
        val s = (ms / 1000).toInt()
        return "%d:%02d".format(s / 60, s % 60)
    }
}