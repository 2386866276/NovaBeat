package com.novabeat.music.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.novabeat.music.R
import com.novabeat.music.data.model.Song
import com.novabeat.music.service.PlayerService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LibraryFragment : Fragment() {

    private var playerService: PlayerService? = null
    private var bound = false
    private val adapter = LibraryAdapter()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            playerService = (binder as PlayerService.PlayerBinder).getService()
            bound = true
            observePlaybackState()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            playerService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startService()
        bindService()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_library, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val sections = listOf("本地音乐", "我喜欢", "最近播放", "播放列表")
        val recycler = view.findViewById<RecyclerView>(R.id.rvLibrary)
        recycler.adapter = adapter
        recycler.layoutManager = LinearLayoutManager(requireContext())

        adapter.updateSections(sections) { section, song ->
            when (section) {
                "本地音乐" -> {
                    // 本地音乐：由 PlaybackState 提供当前队列
                    playerService?.playSong(song)
                }
                else -> {}
            }
        }
    }

    private fun observePlaybackState() {
        viewLifecycleOwner.lifecycleScope.launch {
            playerService?.playbackState?.collectLatest { state ->
                if (state.queue.isNotEmpty()) {
                    adapter.updateSongs("本地音乐", state.queue)
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
            val songAdapter = SongAdapter(songs) { song ->
                onItemClick?.invoke(section, song)
            }
            holder.rvSongs.adapter = songAdapter
            holder.rvSongs.layoutManager =
                LinearLayoutManager(holder.itemView.context, LinearLayoutManager.HORIZONTAL, false)
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
            holder.itemView.setOnClickListener { onClick(song) }
        }

        override fun getItemCount(): Int = songs.size
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bound) {
            requireContext().unbindService(connection)
            bound = false
        }
    }
}