package com.novabeat.music.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.novabeat.music.R
import com.novabeat.music.service.PlayerService

class EqualizerFragment : Fragment() {

    companion object {
        private const val TAG = "EqualizerFragment"
    }

    private var playerService: PlayerService? = null
    private var bound = false
    private var rootView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var eqRetryCount = 0

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "服务已连接")
            playerService = (binder as PlayerService.PlayerBinder).getService()
            bound = true
            rootView?.let { initUI(it) }
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

    private fun startService() {
        context?.let { ctx ->
            ctx.startService(Intent(ctx, PlayerService::class.java))
        }
    }

    private fun bindService() {
        context?.let { ctx ->
            ctx.bindService(Intent(ctx, PlayerService::class.java), connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.fragment_equalizer, container, false)
        rootView = v
        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (bound && playerService != null) {
            initUI(view)
        }
    }

    private fun initUI(view: View) {
        if (!isAdded) return
        Log.d(TAG, "初始化均衡器UI")

        val seekBars = listOf(
            view.findViewById<SeekBar>(R.id.eqBand0),
            view.findViewById<SeekBar>(R.id.eqBand1),
            view.findViewById<SeekBar>(R.id.eqBand2),
            view.findViewById<SeekBar>(R.id.eqBand3),
            view.findViewById<SeekBar>(R.id.eqBand4),
            view.findViewById<SeekBar>(R.id.eqBand5),
            view.findViewById<SeekBar>(R.id.eqBand6),
            view.findViewById<SeekBar>(R.id.eqBand7),
            view.findViewById<SeekBar>(R.id.eqBand8),
            view.findViewById<SeekBar>(R.id.eqBand9)
        )
        val valueLabels = listOf(
            view.findViewById<TextView>(R.id.eqVal0),
            view.findViewById<TextView>(R.id.eqVal1),
            view.findViewById<TextView>(R.id.eqVal2),
            view.findViewById<TextView>(R.id.eqVal3),
            view.findViewById<TextView>(R.id.eqVal4),
            view.findViewById<TextView>(R.id.eqVal5),
            view.findViewById<TextView>(R.id.eqVal6),
            view.findViewById<TextView>(R.id.eqVal7),
            view.findViewById<TextView>(R.id.eqVal8),
            view.findViewById<TextView>(R.id.eqVal9)
        )

        val presetChips = listOf(
            view.findViewById<Chip>(R.id.chipDefault),
            view.findViewById<Chip>(R.id.chipPop),
            view.findViewById<Chip>(R.id.chipRock),
            view.findViewById<Chip>(R.id.chipClassical),
            view.findViewById<Chip>(R.id.chipElectronic),
            view.findViewById<Chip>(R.id.chipJazz)
        )

        val presetLabels = listOf("默认", "流行", "摇滚", "古典", "电子", "爵士")

        // 检查均衡器是否可用
        val hasEqualizer = playerService?.equalizer != null
        Log.d(TAG, "均衡器支持: $hasEqualizer, eqRetryCount: $eqRetryCount")

        if (!hasEqualizer && eqRetryCount < 5) {
            // 均衡器可能还没初始化，延迟重试
            eqRetryCount++
            Log.d(TAG, "均衡器未就绪，延迟重试 ($eqRetryCount/5)")
            handler.postDelayed({ rootView?.let { initUI(it) } }, 1000)
            // 显示等待提示
            if (eqRetryCount == 1) {
                context?.let { ctx ->
                    Toast.makeText(ctx, "正在初始化均衡器，请先播放一首歌曲...", Toast.LENGTH_SHORT).show()
                }
            }
            return
        }

        if (!hasEqualizer) {
            Log.w(TAG, "均衡器不可用")
            context?.let { ctx ->
                Toast.makeText(ctx, "请先播放一首歌曲以激活均衡器", Toast.LENGTH_LONG).show()
            }
        }

        // 预设按钮
        presetChips.forEachIndexed { i, chip ->
            chip.isEnabled = hasEqualizer
            chip.setOnClickListener {
                Log.d(TAG, "应用预设: ${presetLabels[i]}")
                playerService?.applyPreset(presetLabels[i])
                updateAllSeekBars(seekBars, valueLabels, presetLabels[i])
            }
        }

        // 频段滑块
        seekBars.forEachIndexed { i, sb ->
            sb.isEnabled = hasEqualizer
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = (progress - 50) / 10f
                    valueLabels[i].text = "%+.1f dB".format(value)
                    if (fromUser && hasEqualizer) {
                        Log.d(TAG, "调整频段 $i: $value dB")
                        playerService?.setEqualizerBand(i, value)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            sb.progress = 50
            valueLabels[i].text = "0.0 dB"
        }

        // 重置按钮
        view.findViewById<MaterialButton>(R.id.btnResetEq).isEnabled = hasEqualizer
        view.findViewById<MaterialButton>(R.id.btnResetEq).setOnClickListener {
            Log.d(TAG, "重置均衡器")
            updateAllSeekBars(seekBars, valueLabels, "默认")
            playerService?.applyPreset("默认")
        }

        // 如果有当前预设，应用到UI
        val currentPreset = playerService?.getCurrentPreset() ?: "默认"
        if (currentPreset != "默认") {
            updateAllSeekBars(seekBars, valueLabels, currentPreset)
            val idx = presetLabels.indexOf(currentPreset)
            if (idx >= 0) presetChips[idx].isChecked = true
        } else {
            presetChips[0].isChecked = true
        }

        Log.d(TAG, "均衡器UI初始化完成")
    }

    private fun updateAllSeekBars(seekBars: List<SeekBar>, labels: List<TextView>, presetName: String) {
        val preset = playerService?.eqPresets?.get(presetName) ?: return
        preset.forEachIndexed { i, v ->
            seekBars.getOrNull(i)?.progress = ((v * 5) + 50).toInt().coerceIn(0, 100)
            labels.getOrNull(i)?.text = "%+.1f dB".format(v)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        rootView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bound) {
            context?.let { ctx ->
                ctx.unbindService(connection)
            }
            bound = false
        }
    }
}