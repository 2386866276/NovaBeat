package com.novabeat.music.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import com.novabeat.music.R

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val ivLogo = findViewById<View>(R.id.iv_logo)
        val tvAppName = findViewById<View>(R.id.tv_app_name)
        val tvSubtitle = findViewById<View>(R.id.tv_subtitle)

        // 初始状态：透明且缩小
        ivLogo.alpha = 0f
        ivLogo.scaleX = 0.5f
        ivLogo.scaleY = 0.5f
        tvAppName.alpha = 0f
        tvAppName.translationY = 50f
        tvSubtitle.alpha = 0f
        tvSubtitle.translationY = 50f

        // 动画集
        val logoScaleX = ObjectAnimator.ofFloat(ivLogo, "scaleX", 0.5f, 1f)
        val logoScaleY = ObjectAnimator.ofFloat(ivLogo, "scaleY", 0.5f, 1f)
        val logoAlpha = ObjectAnimator.ofFloat(ivLogo, "alpha", 0f, 1f)
        val logoAnimSet = AnimatorSet()
        logoAnimSet.playTogether(logoScaleX, logoScaleY, logoAlpha)
        logoAnimSet.duration = 800
        logoAnimSet.interpolator = OvershootInterpolator(1.5f)

        val titleAlpha = ObjectAnimator.ofFloat(tvAppName, "alpha", 0f, 1f)
        val titleTransY = ObjectAnimator.ofFloat(tvAppName, "translationY", 50f, 0f)
        val titleAnimSet = AnimatorSet()
        titleAnimSet.playTogether(titleAlpha, titleTransY)
        titleAnimSet.duration = 600
        titleAnimSet.startDelay = 400

        val subtitleAlpha = ObjectAnimator.ofFloat(tvSubtitle, "alpha", 0f, 1f)
        val subtitleTransY = ObjectAnimator.ofFloat(tvSubtitle, "translationY", 50f, 0f)
        val subtitleAnimSet = AnimatorSet()
        subtitleAnimSet.playTogether(subtitleAlpha, subtitleTransY)
        subtitleAnimSet.duration = 600
        subtitleAnimSet.startDelay = 600

        val mainAnimSet = AnimatorSet()
        mainAnimSet.playSequentially(logoAnimSet, titleAnimSet, subtitleAnimSet)
        mainAnimSet.start()

        // 2秒后跳转
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            val options = ActivityOptionsCompat.makeCustomAnimation(
                this,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            startActivity(intent, options.toBundle())
            finish()
        }, 2500)
    }
}