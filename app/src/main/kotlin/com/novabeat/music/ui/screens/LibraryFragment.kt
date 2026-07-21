package com.novabeat.music.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.novabeat.music.R
import com.novabeat.music.data.model.Song
import com.novabeat.music.data.repository.MusicRepository
import com.novabeat.music.service.PlayerService
import com.novabeat.music.ui.LoginActivity
import com.novabeat.music.ui.SongDetailActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryFragment : Fragment() {

    companion object {
        private const val TAG = "LibraryFragment"
        private const val REQUEST_LOGIN = 1001
    }

    private var playerService: PlayerService? = null
    private var bound = false
    private lateinit var repository: MusicRepository
    private val adapter = LibraryAdapter()
    private var localSongs: List<Song> = emptyList()
    private var rootView: View? = null
    private var dataLoaded = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            playerService = (binder as PlayerService.PlayerBinder).getService()
            bound = true
            // 延迟到 onViewCreated 后执行，避免 viewLifecycleOwner 未初始化
            rootView?.let { loadDataIfReady() }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            playerService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        context?.let { repository = MusicRepository(it) }
        startService()
        bindService()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.fragment_library, container, false)
        rootView = v
        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val recycler = view.findViewById<RecyclerView>(R.id.rvLibrary)
        recycler.adapter = adapter
        recycler.layoutManager = LinearLayoutManager(requireContext())

        // 登录按钮
        view.findViewById<MaterialButton>(R.id.btnLogin).setOnClickListener {
            val intent = Intent(requireContext(), LoginActivity::class.java)
            startActivityForResult(intent, REQUEST_LOGIN)
        }

        val sections = listOf("本地音乐", "我喜欢", "最近播放", "播放队列")
        adapter.updateSections(sections) { section, song ->
            when (section) {
                "本地音乐", "我喜欢", "最近播放" -> {
                    openSongDetail(song)
                }
                "播放队列" -> {
                    playerService?.playSong(song)
                }
                else -> {}
            }
        }

        // 如果服务已连接，加载数据
        if (bound && playerService != null) {
            loadDataIfReady()
        }
    }

    private fun loadDataIfReady() {
        if (dataLoaded || !isAdded) return
        dataLoaded = true
        loadLocalMusic()
        observePlaybackState()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_LOGIN && resultCode == android.app.Activity.RESULT_OK) {
            val nickname = data?.getStringExtra(LoginActivity.EXTRA_LOGIN_NICKNAME) ?: "用户"
            val cookie = data?.getStringExtra(LoginActivity.EXTRA_LOGIN_COOKIE) ?: ""
            val loginType = data?.getStringExtra(LoginActivity.EXTRA_LOGIN_TYPE) ?: ""
            Toast.makeText(requireContext(), "$nickname 登录成功！", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "登录成功, type=$loginType, cookie: ${cookie.take(30)}...")

            val prefs = requireContext().getSharedPreferences("novabeat", Context.MODE_PRIVATE)
            val editor = prefs.edit()

            when (loginType) {
                LoginActivity.LOGIN_BILIBILI -> {
                    editor.putString("bili_cookie", cookie).putString("bili_nickname", nickname)
                }
                LoginActivity.LOGIN_NETEASE -> {
                    editor.putString("netease_cookie", cookie).putString("netease_nickname", nickname)
                }
                LoginActivity.LOGIN_QQ -> {
                    editor.putString("qq_cookie", cookie).putString("qq_nickname", nickname)
                }
                LoginActivity.LOGIN_WECHAT -> {
                    editor.putString("wechat_cookie", cookie).putString("wechat_nickname", nickname)
                }
                LoginActivity.LOGIN_WEIBO -> {
                    editor.putString("weibo_cookie", cookie).putString("weibo_nickname", nickname)
                }
                LoginActivity.LOGIN_GITHUB -> {
                    editor.putString("github_cookie", cookie).putString("github_nickname", nickname)
                }
                LoginActivity.LOGIN_QQMUSIC -> {
                    editor.putString("qqmusic_cookie", cookie).putString("qqmusic_nickname", nickname)
                }
                "kuwo" -> {
                    editor.putString("kuwo_cookie", cookie).putString("kuwo_nickname", nickname)
                }
                "kugou" -> {
                    editor.putString("kugou_cookie", cookie).putString("kugou_nickname", nickname)
                }
                "qishui" -> {
                    editor.putString("qishui_cookie", cookie).putString("qishui_nickname", nickname)
                }
                else -> {
                    editor.putString("cookie", cookie).putString("nickname", nickname)
                }
            }
            editor.apply()
        }
    }

    private fun openSongDetail(song: Song) {
        val intent = Intent(requireContext(), SongDetailActivity::class.java).apply {
            putExtra(SongDetailActivity.EXTRA_SONG_ID, song.id)
            putExtra(SongDetailActivity.EXTRA_SONG_TITLE, song.title)
            putExtra(SongDetailActivity.EXTRA_SONG_ARTIST, song.artist)
            putExtra(SongDetailActivity.EXTRA_SONG_COVER, song.coverUrl)
            putExtra(SongDetailActivity.EXTRA_SONG_DURATION, song.durationMs)
            putExtra(SongDetailActivity.EXTRA_SONG_SOURCE, song.source)
        }
        startActivity(intent)
    }

    private fun loadLocalMusic() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                repository.scanLocalMusic().collect { songs ->
                    localSongs = songs
                    adapter.updateSongs("本地音乐", songs)
                    Log.d(TAG, "本地音乐: ${songs.size}首")
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载本地音乐失败", e)
            }
        }
    }

    private fun observePlaybackState() {
        viewLifecycleOwner.lifecycleScope.launch {
            playerService?.playbackState?.collectLatest { state ->
                // 更新播放队列
                if (state.queue.isNotEmpty()) {
                    adapter.updateSongs("播放队列", state.queue)
                }
                // 更新我喜欢
                val favorites = playerService?.getFavorites() ?: emptyList()
                if (favorites.isNotEmpty()) {
                    adapter.updateSongs("我喜欢", favorites)
                }
            }
        }
    }

    private fun startService() {
        val intent = Intent(requireContext(), PlayerService::class.java)
        requireContext().startService(intent)
    }

    private fun bindService() {
        val intent = Intent(requireContext(), PlayerService::class.java)
        requireContext().bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    inner class LibraryAdapter : RecyclerView.Adapter<LibraryAdapter.VH>() {

        private var sections: List<String> = emptyList()
        private var songsBySection = mutableMapOf<String, List<Song>>()
        private var onItemClick: ((String, Song) -> Unit)? = null

        fun updateSections(sections: List<String>, onClick: (String, Song) -> Unit) {
            this.sections = sections
            this.onItemClick = onClick
            notifyDataSetChanged()
        }

        fun updateSongs(section: String, songs: List<Song>) {
            songsBySection[section] = songs
            notifyDataSetChanged()
        }

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvSection: TextView = itemView.findViewById(R.id.tvSectionTitle)
            val rvSongs: RecyclerView = itemView.findViewById(R.id.rvSectionSongs)
            val tvEmpty: TextView = itemView.findViewById(R.id.tvSectionEmpty)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_library_section, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val section = sections.getOrNull(position) ?: return
            holder.tvSection.text = section
            val songs = songsBySection[section] ?: emptyList()
            if (songs.isEmpty()) {
                holder.tvEmpty.visibility = View.VISIBLE
                holder.tvEmpty.text = "暂无${section}内容"
                holder.rvSongs.visibility = View.GONE
            } else {
                holder.tvEmpty.visibility = View.GONE
                holder.rvSongs.visibility = View.VISIBLE
                val songAdapter = SongAdapter(songs) { song ->
                    onItemClick?.invoke(section, song)
                }
                holder.rvSongs.adapter = songAdapter
                holder.rvSongs.layoutManager =
                    LinearLayoutManager(holder.itemView.context, LinearLayoutManager.HORIZONTAL, false)
            }
        }

        override fun getItemCount(): Int = sections.size
    }

    inner class SongAdapter(
        private val songs: List<Song>,
        private val onClick: (Song) -> Unit
    ) : RecyclerView.Adapter<SongAdapter.SVH>() {

        inner class SVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvTitle: TextView = itemView.findViewById(R.id.tvSongTitle)
            val tvArtist: TextView = itemView.findViewById(R.id.tvSongArtist)
            val ivCover: ImageView = itemView.findViewById(R.id.ivSongCover)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SVH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_song_card, parent, false)
            return SVH(v)
        }

        override fun onBindViewHolder(holder: SVH, position: Int) {
            val song = songs.getOrNull(position) ?: return
            holder.tvTitle.text = song.title
            holder.tvArtist.text = song.artist
            if (song.coverUrl.isNotBlank()) {
                Glide.with(holder.itemView)
                    .load(song.coverUrl)
                    .placeholder(R.drawable.ic_music_note)
                    .centerCrop()
                    .into(holder.ivCover)
            }
            holder.itemView.setOnClickListener { onClick(song) }
        }

        override fun getItemCount(): Int = songs.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        rootView = null
        dataLoaded = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bound) {
            requireContext().unbindService(connection)
            bound = false
        }
    }
}