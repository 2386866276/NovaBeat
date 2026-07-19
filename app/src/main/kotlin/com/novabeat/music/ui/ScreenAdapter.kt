package com.novabeat.music.ui

import android.content.Context
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.novabeat.music.ui.screens.EqualizerFragment
import com.novabeat.music.ui.screens.LibraryFragment
import com.novabeat.music.ui.screens.PlayerFragment
import com.novabeat.music.ui.screens.SearchFragment

class ScreenAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> PlayerFragment()
        1 -> LibraryFragment()
        2 -> SearchFragment()
        3 -> EqualizerFragment()
        else -> throw IllegalArgumentException("Invalid position")
    }
}