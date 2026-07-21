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
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.novabeat.music.R
import com.novabeat.music.data.model.Song
import com.novabeat.music.data.remote.BilibiliApiService
import com.novabeat.music.data.repository.MusicRepository
import com.novabeat.music.service.PlayerService
import com.novabeat.music.ui.SongDetailActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchFragment : Fragment() {

    companion object {
        private const val TAG = "SearchFragment"
    }

    private var playerService: PlayerService? = null
    private var bound = false
    private val results = mutableListOf<Song>()
    private val adapter = SearchResultAdapter()
    private lateinit var repository: MusicRepository
    private lateinit var biliApi: BilibiliApiService
    private var currentSource = "netease"

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "服务已连接")
            playerService = (binder as PlayerService.PlayerBinder).getService()
            bound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "服务断开连接")
            bound = false
            playerService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        context?.let { ctx ->
            ctx.startService(Intent(ctx, PlayerService::class.java))
            ctx.bindService(Intent(ctx, PlayerService::class.java), connection, Context.BIND_AUTO_CREATE)
        }
        context?.let { repository = MusicRepository(it) }
        biliApi = BilibiliApiService()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_search, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<RecyclerView>(R.id.rvSearchResults).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@SearchFragment.adapter
        }

        val etQuery = view.findViewById<EditText>(R.id.etSearch)
        val btnSearch = view.findViewById<MaterialButton>(R.id.btnSearch)
        val progress = view.findViewById<CircularProgressIndicator>(R.id.progressSearch)

        // 音源切换
        val chipGroupSource = view.findViewById<ChipGroup>(R.id.chipGroupSource)
        chipGroupSource.check(R.id.chipSourceNetease)
        chipGroupSource.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                currentSource = when (checkedIds[0]) {
                    R.id.chipSourceNetease -> "netease"
                    R.id.chipSourceBilibili -> "bilibili"
                    R.id.chipSourceQQMusic -> "qqmusic"
                    R.id.chipSourceKuwo -> "kuwo"
                    R.id.chipSourceKugou -> "kugou"
                    R.id.chipSourceQishui -> "qishui"
                    R.id.chipSourceSpotify -> "spotify"
                    R.id.chipSourceYouTubeMusic -> "youtube"
                    else -> "netease"
                }
                Log.d(TAG, "音源切换为: $currentSource")
            }
        }

        btnSearch.setOnClickListener {
            val query = etQuery.text.toString().trim()
            if (query.isNotBlank()) {
                doSearch(query, progress)
            }
        }

        view.findViewById<ImageButton>(R.id.btnClear).setOnClickListener {
            etQuery.text?.clear()
            results.clear()
            adapter.updateResults(results)
        }

        // 推荐歌曲按钮
        view.findViewById<MaterialButton>(R.id.btnRecommend).setOnClickListener {
            doSearch("热门华语歌曲", progress, isRecommend = true)
        }
    }

    private fun doSearch(query: String, progress: CircularProgressIndicator, isRecommend: Boolean = false) {
        if (!isAdded) return
        Log.d(TAG, "开始搜索: $query, 音源: $currentSource")
        progress.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (isRecommend) {
                    Toast.makeText(requireContext(), "正在获取推荐歌曲...", Toast.LENGTH_SHORT).show()
                }
                when (currentSource) {
                    "bilibili" -> {
                        // 获取B站Cookie
                        val biliCookie = requireContext()
                            .getSharedPreferences("novabeat", Context.MODE_PRIVATE)
                            .getString("bili_cookie", "") ?: ""
                        val biliResults = withContext(Dispatchers.IO) {
                            Log.d(TAG, "调用B站搜索, cookie存在: ${biliCookie.isNotBlank()}")
                            biliApi.withCookie(biliCookie).searchMusic(query)
                        }
                        Log.d(TAG, "B站搜索结果数量: ${biliResults.size}")
                        results.clear()
                        results.addAll(biliResults.map { it.toSong() })
                        adapter.updateResults(results)
                        if (biliResults.isEmpty() && isAdded) {
                            Toast.makeText(requireContext(), "B站未找到结果，请检查网络或登录B站账号", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "qqmusic" -> {
                        val qqCookie = requireContext()
                            .getSharedPreferences("novabeat", Context.MODE_PRIVATE)
                            .getString("qqmusic_cookie", "") ?: ""
                        val qqResults = withContext(Dispatchers.IO) {
                            Log.d(TAG, "调用QQ音乐搜索")
                            repository.searchQQMusic(query, qqCookie)
                        }
                        Log.d(TAG, "QQ音乐搜索结果数量: ${qqResults.size}")
                        results.clear()
                        results.addAll(qqResults)
                        adapter.updateResults(results)
                        if (qqResults.isEmpty() && isAdded) {
                            Toast.makeText(requireContext(), "QQ音乐未找到结果", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "kuwo" -> {
                        val kuwoCookie = requireContext()
                            .getSharedPreferences("novabeat", Context.MODE_PRIVATE)
                            .getString("kuwo_cookie", "") ?: ""
                        val kuwoResults = withContext(Dispatchers.IO) {
                            Log.d(TAG, "调用酷我音乐搜索")
                            repository.searchKuwo(query, kuwoCookie)
                        }
                        Log.d(TAG, "酷我搜索结果数量: ${kuwoResults.size}")
                        results.clear()
                        results.addAll(kuwoResults)
                        adapter.updateResults(results)
                        if (kuwoResults.isEmpty() && isAdded) {
                            Toast.makeText(requireContext(), "酷我音乐未找到结果", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "kugou" -> {
                        val kugouCookie = requireContext()
                            .getSharedPreferences("novabeat", Context.MODE_PRIVATE)
                            .getString("kugou_cookie", "") ?: ""
                        val kugouResults = withContext(Dispatchers.IO) {
                            Log.d(TAG, "调用酷狗音乐搜索")
                            repository.searchKugou(query, kugouCookie)
                        }
                        Log.d(TAG, "酷狗搜索结果数量: ${kugouResults.size}")
                        results.clear()
                        results.addAll(kugouResults)
                        adapter.updateResults(results)
                        if (kugouResults.isEmpty() && isAdded) {
                            Toast.makeText(requireContext(), "酷狗音乐未找到结果", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "qishui" -> {
                        val qishuiCookie = requireContext()
                            .getSharedPreferences("novabeat", Context.MODE_PRIVATE)
                            .getString("qishui_cookie", "") ?: ""
                        val qishuiResults = withContext(Dispatchers.IO) {
                            Log.d(TAG, "调用汽水音乐搜索")
                            repository.searchQishui(query, qishuiCookie)
                        }
                        Log.d(TAG, "汽水音乐搜索结果数量: ${qishuiResults.size}")
                        results.clear()
                        results.addAll(qishuiResults)
                        adapter.updateResults(results)
                        if (qishuiResults.isEmpty() && isAdded) {
                            Toast.makeText(requireContext(), "汽水音乐未找到结果", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "spotify" -> {
                        val token = requireContext()
                            .getSharedPreferences("novabeat", Context.MODE_PRIVATE)
                            .getString("spotify_token", "") ?: ""
                        val spotifyResults = withContext(Dispatchers.IO) {
                            repository.searchSpotify(query, token)
                        }
                        results.clear()
                        results.addAll(spotifyResults)
                        adapter.updateResults(results)
                        if (spotifyResults.isEmpty() && isAdded) {
                            Toast.makeText(requireContext(), "Spotify 未找到结果，请检查 Access Token", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "youtube" -> {
                        val preferences = requireContext()
                            .getSharedPreferences("novabeat", Context.MODE_PRIVATE)
                        val apiKey = preferences.getString("youtube_api_key", "") ?: ""
                        val youtubeCookie = preferences.getString("youtube_cookie", "") ?: ""
                        val youtubeResults = withContext(Dispatchers.IO) {
                            repository.searchYouTubeMusic(query, apiKey, youtubeCookie)
                        }
                        results.clear()
                        results.addAll(youtubeResults)
                        adapter.updateResults(results)
                        if (youtubeResults.isEmpty() && isAdded) {
                            Toast.makeText(requireContext(), "YouTube Music 未找到结果，请检查网络", Toast.LENGTH_SHORT).show()
                        }
                    }
                    else -> {
                        // 网易云搜索 — 读取网易云 Cookie
                        val neteaseCookie = requireContext()
                            .getSharedPreferences("novabeat", Context.MODE_PRIVATE)
                            .getString("netease_cookie", "") ?: ""
                        val neteaseResults = withContext(Dispatchers.IO) {
                            Log.d(TAG, "调用searchNetease, cookie存在: ${neteaseCookie.isNotBlank()}")
                            repository.searchNetease(query, neteaseCookie)
                        }
                        Log.d(TAG, "搜索结果数量: ${neteaseResults.size}")
                        results.clear()
                        results.addAll(neteaseResults)
                        adapter.updateResults(results)
                        if (neteaseResults.isEmpty() && isAdded) {
                            Toast.makeText(requireContext(), "未找到结果，请检查网络连接", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "搜索失败", e)
                if (isAdded) {
                    Toast.makeText(requireContext(), "搜索失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                progress.visibility = View.GONE
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bound) {
            try { requireContext().unbindService(connection) } catch (_: Exception) {}
            bound = false
        }
    }

    inner class SearchResultAdapter : RecyclerView.Adapter<SearchResultAdapter.VH>() {

        private var items: List<Song> = emptyList()

        fun updateResults(newItems: List<Song>) {
            this.items = newItems
            notifyDataSetChanged()
        }

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
            val tvArtist: TextView = itemView.findViewById(R.id.tvArtist)
            val ivCover: ImageView = itemView.findViewById(R.id.ivCover)
            val btnPlay: MaterialButton = itemView.findViewById(R.id.btnPlay)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_search_result, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val song = items.getOrNull(position) ?: return
            holder.tvTitle.text = song.title
            holder.tvArtist.text = song.artist

            // 加载封面
            if (song.coverUrl.isNotBlank()) {
                Glide.with(holder.itemView)
                    .load(song.coverUrl)
                    .placeholder(R.drawable.ic_music_note)
                    .centerCrop()
                    .into(holder.ivCover)
            } else {
                holder.ivCover.setImageResource(R.drawable.ic_music_note)
                if (song.source != "bilibili") {
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            val coverUrl = withContext(Dispatchers.IO) {
                                repository.fetchAlbumCover(song.id)
                            }
                            if (coverUrl.isNotBlank() && position < items.size) {
                                Glide.with(holder.itemView)
                                    .load(coverUrl)
                                    .centerCrop()
                                    .into(holder.ivCover)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "获取封面失败", e)
                        }
                    }
                }
            }

            // 点击跳转到歌曲详情页
            val openDetail = {
                Log.d(TAG, "跳转详情页: ${song.title}")
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

            holder.btnPlay.setOnClickListener { openDetail() }
            holder.itemView.setOnClickListener { openDetail() }
        }

        override fun getItemCount(): Int = items.size
    }
}