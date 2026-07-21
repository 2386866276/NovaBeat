package com.novabeat.music.ui

import android.app.Dialog
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.appbar.MaterialToolbar
import com.novabeat.music.R

/**
 * 关于页面
 * 展示应用信息、功能特性、支持音源、捐赠收款码、开发者信息、法律协议等
 */
class AboutActivity : AppCompatActivity() {

    /** 收款码图片资产路径 */
    private val donationAssets = mapOf(
        R.id.ivDonateWechat to "donations/wechat.png",
        R.id.ivDonateAlipay to "donations/alipay.png",
        R.id.ivDonateWechatAppreciation to "donations/wechatappreciationcode.png"
    )

    /** 收款码标题 */
    private val donationTitles = mapOf(
        R.id.ivDonateWechat to "微信收款码",
        R.id.ivDonateAlipay to "支付宝收款码",
        R.id.ivDonateWechatAppreciation to "微信赞赏码"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_about)

        // 顶部栏返回按钮
        findViewById<MaterialToolbar>(R.id.toolbarAbout).setNavigationOnClickListener {
            finish()
        }

        // 读取版本号
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            findViewById<TextView>(R.id.tvAboutVersion).text = "版本 $versionName"
        } catch (_: Exception) {
            // 保持默认 "版本 26.1.0"
        }

        // 加载真实收款码图片 + 点击放大
        donationAssets.forEach { (viewId, assetPath) ->
            val imageView = findViewById<ImageView>(viewId)
            loadDonationImage(imageView, assetPath)
            imageView.setOnClickListener {
                showDonationDialog(donationTitles[viewId]!!, assetPath)
            }
        }
    }

    /**
     * 从 assets 目录加载收款码图片（绕过 AAPT 编译）
     * 如果 assets 中没有，回退到 drawable 中的占位图
     */
    private fun loadDonationImage(imageView: ImageView, assetPath: String) {
        try {
            val asset = assets.open(assetPath)
            val bitmap = BitmapFactory.decodeStream(asset)
            asset.close()
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
                return
            }
        } catch (_: Exception) {
            // assets 中没有该文件，使用 drawable 占位图
        }
        // 回退：使用 drawable 中的 XML 占位图
        val drawableName = assetPath.substringAfterLast("/").substringBeforeLast(".")
        val resId = resources.getIdentifier(drawableName, "drawable", packageName)
        if (resId != 0) {
            imageView.setImageResource(resId)
        }
    }

    /**
     * 弹出全屏放大收款码对话框
     */
    private fun showDonationDialog(title: String, assetPath: String) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)

        val titleView = TextView(this).apply {
            text = title
            setTextColor(android.graphics.Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 48, 0, 24)
        }

        val imageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            // 优先从 assets 加载真实收款码
            try {
                val asset = assets.open(assetPath)
                val bitmap = BitmapFactory.decodeStream(asset)
                asset.close()
                if (bitmap != null) {
                    setImageBitmap(bitmap)
                } else {
                    val drawableName = assetPath.substringAfterLast("/").substringBeforeLast(".")
                    val resId = resources.getIdentifier(drawableName, "drawable", packageName)
                    if (resId != 0) setImageResource(resId)
                }
            } catch (_: Exception) {
                val drawableName = assetPath.substringAfterLast("/").substringBeforeLast(".")
                val resId = resources.getIdentifier(drawableName, "drawable", packageName)
                if (resId != 0) setImageResource(resId)
            }
            setOnClickListener { dialog.dismiss() }
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(android.graphics.Color.BLACK)
            addView(titleView)
            addView(imageView, WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        }

        dialog.setContentView(container)
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        dialog.show()
    }
}