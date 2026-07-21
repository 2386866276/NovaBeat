package com.novabeat.music.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.novabeat.music.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 哔哩哔哩真实登录 Activity
 * 通过 WebView 加载 B站登录页，登录成功后提取 SESSDATA 等 Cookie
 */
class BilibiliLoginActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BiliLogin"
        const val EXTRA_BILI_COOKIE = "bili_cookie"
        const val EXTRA_BILI_NICKNAME = "bili_nickname"
        private const val BILI_LOGIN_URL = "https://passport.bilibili.com/login"
    }

    private lateinit var webView: WebView
    private var loginCompleted = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bilibili_login)

        findViewById<MaterialButton>(R.id.btnBackBiliLogin).setOnClickListener { finish() }

        webView = findViewById(R.id.webViewBiliLogin)

        // 先清除旧 Cookie，确保是全新登录
        CookieManager.getInstance().removeAllCookies { }
        CookieManager.getInstance().flush()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (loginCompleted) return

                // 检查多个域名的 Cookie
                val cookiesWww = CookieManager.getInstance().getCookie("https://www.bilibili.com")
                val cookiesPassport = CookieManager.getInstance().getCookie("https://passport.bilibili.com")

                val sessdata = extractCookieValue(cookiesWww, "SESSDATA")
                    ?: extractCookieValue(cookiesPassport, "SESSDATA")

                Log.d(TAG, "页面加载完成: $url, SESSDATA存在: ${sessdata != null}")

                if (sessdata != null && sessdata.isNotBlank()) {
                    loginCompleted = true
                    val fullCookie = cookiesWww ?: cookiesPassport ?: ""
                    onLoginSuccess(sessdata, fullCookie)
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }

        webView.loadUrl(BILI_LOGIN_URL)
    }

    private fun extractCookieValue(cookieString: String?, key: String): String? {
        if (cookieString.isNullOrBlank()) return null
        val parts = cookieString.split(";")
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.startsWith("$key=")) {
                return trimmed.substringAfter("$key=")
            }
        }
        return null
    }

    private fun onLoginSuccess(sessdata: String, fullCookie: String) {
        lifecycleScope.launch {
            // 获取用户昵称
            var nickname = "哔哩哔哩用户"
            try {
                nickname = withContext(Dispatchers.IO) {
                    fetchUserInfo(sessdata)
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取用户信息失败", e)
            }

            val resultIntent = Intent().apply {
                putExtra(EXTRA_BILI_COOKIE, fullCookie)
                putExtra(EXTRA_BILI_NICKNAME, nickname)
            }
            setResult(RESULT_OK, resultIntent)
            Toast.makeText(
                this@BilibiliLoginActivity,
                "哔哩哔哩登录成功！$nickname",
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }

    /**
     * 通过 B站 nav API 获取用户信息（昵称）
     */
    private fun fetchUserInfo(sessdata: String): String {
        val conn = URL("https://api.bilibili.com/x/web-interface/nav").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.setRequestProperty("Cookie", "SESSDATA=$sessdata")
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")

        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val text = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        conn.disconnect()

        val json = JSONObject(text)
        val code = json.optInt("code", -1)
        if (code == 0) {
            val data = json.optJSONObject("data")
            return data?.optString("uname", "哔哩哔哩用户") ?: "哔哩哔哩用户"
        }
        return "哔哩哔哩用户"
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
