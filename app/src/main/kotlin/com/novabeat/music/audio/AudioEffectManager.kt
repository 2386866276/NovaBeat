package com.novabeat.music.audio

import android.media.audiofx.PresetReverb
import android.media.audiofx.EnvironmentalReverb
import android.util.Log

/**
 * 音频效果管理器
 * 提供去伴奏（人声提取模拟）、空灵感（混响+EQ）、低音增强等效果
 */
class AudioEffectManager {

    companion object {
        private const val TAG = "AudioEffectManager"
    }

    private var environmentalReverb: EnvironmentalReverb? = null
    private var presetReverb: PresetReverb? = null
    private var currentEffect = AudioEffectType.NONE

    enum class AudioEffectType(val displayName: String) {
        NONE("原声"),
        VOCAL_REMOVER("去伴奏"),    // 去伴奏效果
        ETHEREAL("空灵感"),         // 空灵感混响
        BASS_BOOST("低音增强"),     // 低音增强
        STUDIO("录音棚"),           // 录音棚效果
        CONCERT("演唱会")           // 演唱会效果
    }

    /**
     * 初始化音频效果，需要音频会话ID
     */
    fun init(audioSessionId: Int) {
        if (audioSessionId <= 0) {
            Log.w(TAG, "无效的音频会话ID: $audioSessionId")
            return
        }
        try {
            // 先释放旧的
            release()

            // 创建环境混响（用于空灵感等效果）
            environmentalReverb = EnvironmentalReverb(0, audioSessionId).apply {
                enabled = false  // 默认关闭
            }

            // 创建预设混响
            presetReverb = PresetReverb(0, audioSessionId).apply {
                enabled = false
            }

            Log.d(TAG, "音频效果初始化成功, sessionId=$audioSessionId")
        } catch (e: Exception) {
            Log.e(TAG, "音频效果初始化失败", e)
        }
    }

    /**
     * 应用音频效果
     */
    fun applyEffect(effect: AudioEffectType) {
        currentEffect = effect
        try {
            // 先关闭所有效果
            environmentalReverb?.enabled = false
            presetReverb?.enabled = false

            when (effect) {
                AudioEffectType.NONE -> {
                    Log.d(TAG, "应用效果: 原声")
                }

                AudioEffectType.VOCAL_REMOVER -> {
                    // 去伴奏效果：通过 EQ 配置实现
                    // 人声通常在中间声道，降低低频和高频，突出中频
                    // 这里用 EQ 预设来模拟，实际效果由 PlayerService 的 Equalizer 配合
                    Log.d(TAG, "应用效果: 去伴奏（人声提取模式）")
                    // 配置 EQ 为人声突出模式
                    applyVocalRemoverEQ()
                }

                AudioEffectType.ETHEREAL -> {
                    // 空灵感：大量混响 + 适度延迟
                    Log.d(TAG, "应用效果: 空灵感")
                    environmentalReverb?.apply {
                        // 大空间混响，营造空灵感
                        setRoomLevel((-2000).toShort())  // 较大空间
                        setRoomHFLevel((-1000).toShort())
                        setDecayTime(8000)  // 长混响时间
                        setDecayHFRatio((500).toShort())
                        setReflectionsLevel((-1500).toShort())
                        setReflectionsDelay(100)
                        setReverbLevel((500).toShort())
                        setReverbDelay(150)
                        setDiffusion((800).toShort())
                        setDensity((800).toShort())
                        enabled = true
                    }
                    applyEtherealEQ()
                }

                AudioEffectType.BASS_BOOST -> {
                    // 低音增强：增强低频混响
                    Log.d(TAG, "应用效果: 低音增强")
                    environmentalReverb?.apply {
                        setRoomLevel((-3000).toShort())
                        setDecayTime(3000)
                        setReverbLevel((300).toShort())
                        enabled = true
                    }
                    applyBassBoostEQ()
                }

                AudioEffectType.STUDIO -> {
                    // 录音棚效果：轻微混响
                    Log.d(TAG, "应用效果: 录音棚")
                    presetReverb?.apply {
                        preset = PresetReverb.PRESET_SMALLROOM
                        enabled = true
                    }
                }

                AudioEffectType.CONCERT -> {
                    // 演唱会效果：大厅混响
                    Log.d(TAG, "应用效果: 演唱会")
                    presetReverb?.apply {
                        preset = PresetReverb.PRESET_LARGEHALL
                        enabled = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "应用音频效果失败", e)
        }
    }

    fun getCurrentEffect(): AudioEffectType = currentEffect

    /**
     * 获取去伴奏效果的 EQ 参数（供 PlayerService 使用）
     * 去伴奏原理：人声通常在中频段（200Hz-4kHz），通过提升中频、降低极低频和极高频来模拟
     */
    private fun applyVocalRemoverEQ() {
        // EQ 参数交由 PlayerService 的 Equalizer 处理
        // 这里设置标志，PlayerService 会读取
        vocalRemoverActive = true
    }

    private fun applyEtherealEQ() {
        // 空灵感的 EQ：提升高频，营造通透感
        etherealActive = true
    }

    private fun applyBassBoostEQ() {
        bassBoostActive = true
    }

    // 状态标志供 PlayerService 查询
    var vocalRemoverActive = false
        private set
    var etherealActive = false
        private set
    var bassBoostActive = false
        private set

    /**
     * 获取效果对应的 EQ 预设值（10段）
     */
    fun getEQPresetForEffect(effect: AudioEffectType): List<Float>? {
        return when (effect) {
            AudioEffectType.VOCAL_REMOVER -> listOf(
                -5f, -3f, 0f, 3f, 5f, 5f, 4f, 2f, -2f, -4f  // 突出中频
            )
            AudioEffectType.ETHEREAL -> listOf(
                -1f, 0f, 1f, 2f, 2f, 3f, 4f, 5f, 5f, 4f  // 提升高频，通透感
            )
            AudioEffectType.BASS_BOOST -> listOf(
                6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 1f, 2f  // 增强低频
            )
            AudioEffectType.STUDIO -> null  // 使用预设混响，不改 EQ
            AudioEffectType.CONCERT -> null
            AudioEffectType.NONE -> null
        }
    }

    fun release() {
        try {
            environmentalReverb?.release()
            environmentalReverb = null
            presetReverb?.release()
            presetReverb = null
            vocalRemoverActive = false
            etherealActive = false
            bassBoostActive = false
        } catch (e: Exception) {
            Log.e(TAG, "释放音频效果失败", e)
        }
    }
}