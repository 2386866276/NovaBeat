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
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.novabeat.music.R
import com.novabeat.music.data.model.Song
import com.novabeat.music.data.repository.MusicRepository
import com.novabeat.music.service.PlayerService
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
    }

    private fun doSearch(query: String, progress: CircularProgressIndicator) {
        if (!isAdded) return
        Log.d(TAG, "开始搜索: $query")
        progress.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val neteaseResults = withContext(Dispatchers.IO) {
                    Log.d(TAG, "调用searchNetease")
                    repository.searchNetease(query)
                }
                Log.d(TAG, "搜索结果数量: ${neteaseResults.size}")
                results.clear()
                results.addAll(neteaseResults)
                adapter.updateResults(results)
                if (neteaseResults.isEmpty() && isAdded) {
                    Toast.makeText(requireContext(), "未找到结果，请检查网络连接", Toast.LENGTH_SHORT).show()
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
            holder.btnPlay.setOnClickListener {
                Log.d(TAG, "点击播放: ${song.title}")
                if (!isAdded) return@setOnClickListener
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val resolvedUrl = withContext(Dispatchers.IO) {
                            repository.resolveNeteaseUrl(song.id)
                        }
                        Log.d(TAG, "解析结果: ${resolvedUrl.take(50)}")
                        if (resolvedUrl.isNotBlank()) {
                            val playable = song.copy(url = resolvedUrl)
                            val idx = results.indexOfFirst { it.id == song.id }
                            if (idx >= 0) results[idx] = playable
                            playerService?.playSong(playable)
                        } else if (isAdded) {
                            Toast.makeText(requireContext(), "播放链接获取失败", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "播放失败", e)
                        if (isAdded) {
                            Toast.makeText(requireContext(), "播放失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            holder.itemView.setOnClickListener { holder.btnPlay.performClick() }
        }

        override fun getItemCount(): Int = items.size
    }
}