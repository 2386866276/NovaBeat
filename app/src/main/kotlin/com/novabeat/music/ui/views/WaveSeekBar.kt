package com.novabeat.music.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.sin

/**
 * 带波浪动画效果的进度条
 * 支持拖拽和进度回调
 */
class WaveSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnWaveProgressChangeListener {
        fun onProgressChanged(progress: Int, fromUser: Boolean)
        fun onStartTrackingTouch()
        fun onStopTrackingTouch()
    }

    private var listener: OnWaveProgressChangeListener? = null

    fun setOnProgressChangeListener(l: OnWaveProgressChangeListener) {
        listener = l
    }

    private var max = 100
    private var progress = 0
    private var isDragging = false

    // 波浪参数
    private var wavePhase = 0f
    private val waveAmplitude = 12f  // 波峰高度
    private val waveFrequency = 0.04f  // 波浪密度
    private val waveSpeed = 0.08f

    // 颜色
    private val playedColor: Int
    private val unplayedColor: Int
    private val waveColor: Int

    private val waveAnimator: ValueAnimator?

    private val wavePath = Path()
    private val bgPath = Path()

    private val playedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val unplayedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val trackHeight = 6f * resources.displayMetrics.density
    private val cornerRadius = trackHeight / 2f

    private var viewWidth = 0
    private var viewHeight = 0

    init {
        val density = resources.displayMetrics.density

        // 使用动态主题色，如果获取不到则用默认值
        val typedValue = android.util.TypedValue()
        val resolved = context.theme.resolveAttribute(
            android.R.attr.colorPrimary, typedValue, true
        )
        playedColor = if (resolved) typedValue.data else Color.parseColor("#6750A4")

        val resolved2 = context.theme.resolveAttribute(
            android.R.attr.colorControlNormal, typedValue, true
        )
        unplayedColor = if (resolved2) typedValue.data else Color.parseColor("#E7E0EC")

        waveColor = playedColor
        thumbPaint.color = playedColor

        playedPaint.color = playedColor
        unplayedPaint.color = unplayedColor

        // 启动波浪动画
        waveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                wavePhase += waveSpeed
                invalidate()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        waveAnimator?.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        waveAnimator?.cancel()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w
        viewHeight = h

        // 设置渐变着色器
        wavePaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), 0f,
            intArrayOf(playedColor, adjustAlpha(playedColor, 0.6f)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt()
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    fun setMax(m: Int) {
        max = m.coerceAtLeast(1)
        invalidate()
    }

    fun setProgress(p: Int) {
        if (!isDragging) {
            progress = p.coerceIn(0, max)
            invalidate()
        }
    }

    fun getProgress(): Int = progress

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (viewWidth <= 0 || viewHeight <= 0) return

        val centerY = viewHeight / 2f
        val progressRatio = progress.toFloat() / max.toFloat()
        val progressX = viewWidth * progressRatio

        // 绘制未播放部分（灰色背景轨道）
        unplayedPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(
            0f, centerY - cornerRadius,
            viewWidth.toFloat(), centerY + cornerRadius,
            cornerRadius, cornerRadius, unplayedPaint
        )

        // 绘制已播放部分（主题色轨道）
        if (progressX > 0) {
            playedPaint.style = Paint.Style.FILL
            canvas.drawRoundRect(
                0f, centerY - cornerRadius,
                progressX, centerY + cornerRadius,
                cornerRadius, cornerRadius, playedPaint
            )
        }

        // 绘制波浪效果 — 在已播放部分上方
        if (progressX > 4f && progress > 0) {
            wavePath.reset()
            wavePath.moveTo(0f, centerY)

            val segments = 60
            val segWidth = progressX / segments
            for (i in 0..segments) {
                val x = i * segWidth
                val waveOffset = sin((x * waveFrequency + wavePhase).toDouble()).toFloat() * waveAmplitude
                wavePath.lineTo(x, centerY + waveOffset)
            }
            // 回到底部，形成封闭区域
            wavePath.lineTo(progressX, centerY + cornerRadius)
            wavePath.lineTo(0f, centerY + cornerRadius)
            wavePath.close()
            canvas.drawPath(wavePath, wavePaint)

            // 上半部分波浪
            wavePath.reset()
            wavePath.moveTo(0f, centerY)
            for (i in 0..segments) {
                val x = i * segWidth
                val waveOffset = sin((x * waveFrequency + wavePhase + 3.14f).toDouble()).toFloat() * waveAmplitude
                wavePath.lineTo(x, centerY + waveOffset)
            }
            wavePath.lineTo(progressX, centerY - cornerRadius)
            wavePath.lineTo(0f, centerY - cornerRadius)
            wavePath.close()
            canvas.drawPath(wavePath, wavePaint)
        }

        // 绘制拖拽圆点（thumb）
        val thumbRadius = if (isDragging) 10f * resources.displayMetrics.density
                          else 7f * resources.displayMetrics.density
        canvas.drawCircle(progressX, centerY, thumbRadius, thumbPaint)

        // 拖拽时画一个外圈光晕
        if (isDragging) {
            val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = adjustAlpha(playedColor, 0.2f)
                style = Paint.Style.FILL
            }
            canvas.drawCircle(progressX, centerY, thumbRadius * 2f, haloPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                listener?.onStartTrackingTouch()
                updateProgressFromTouch(event.x)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateProgressFromTouch(event.x)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                updateProgressFromTouch(event.x)
                listener?.onStopTrackingTouch()
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateProgressFromTouch(touchX: Float) {
        val ratio = (touchX / viewWidth).coerceIn(0f, 1f)
        progress = (ratio * max).toInt()
        listener?.onProgressChanged(progress, true)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (40 * resources.displayMetrics.density).toInt()
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(desiredHeight, heightSize)
            else -> desiredHeight
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }
}
