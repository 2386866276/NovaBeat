package com.novabeat.music.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
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
 * 通用 WebView 登录 Activity
 * 支持所有第三方平台的真实登录，通过 WebView 加载平台登录页，
 * 检测到登录成功 Cookie 后自动返回。
 */
class WebLoginActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WebLogin"
        const val EXTRA_PLATFORM = "platform"
        const val EXTRA_RESULT_COOKIE = "result_cookie"
        const val EXTRA_RESULT_NICKNAME = "result_nickname"
    }

    /** 平台登录配置 */
    private data class LoginConfig(
        val url: String,
        val title: String,
        val cookieDomain: String,
        val successCookieKeys: List<String>,
        val nicknameUrl: String? = null,
        val nicknameCookieKey: String? = null
    )

    private val configs = mapOf(
        "qq" to LoginConfig(
            url = "https://xui.ptlogin2.qq.com/cgi-bin/xlogin?appid=716027609&daid=383&pt_skey_valid=0&style=35&s_url=https%3A%2F%2Fy.qq.com%2F&protocol=https%3A&low_login_hour=0&adparam=offset%3D0%26limit%3D30",
            title = "QQ登录",
            cookieDomain = "https://www.qq.com",
            successCookieKeys = listOf("p_skey", "skey", "uin")
        ),
        "wechat" to LoginConfig(
            url = "https://open.weixin.qq.com/connect/qrconnect?appid=wxbdc5610cc40c2051&redirect_uri=https%3A%2F%2Fy.qq.com%2Fportal%2Fcallback.html&response_type=code&scope=snsapi_login&state=STATE",
            title = "微信登录",
            cookieDomain = "https://open.weixin.qq.com",
            successCookieKeys = listOf("wxuin", "sesskey", "code")
        ),
        "weibo" to LoginConfig(
            url = "https://passport.weibo.com/signin/login?entry=mweibo&r=https%3A%2F%2Fm.weibo.cn",
            title = "微博登录",
            cookieDomain = "https://weibo.com",
            successCookieKeys = listOf("SUB", "ALF", "SSOLoginState")
        ),
        "github" to LoginConfig(
            url = "https://github.com/login",
            title = "GitHub登录",
            cookieDomain = "https://github.com",
            successCookieKeys = listOf("user_session", "logged_in", "dotcom_user"),
            nicknameUrl = "https://api.github.com/user",
            nicknameCookieKey = "user_session"
        ),
        "netease" to LoginConfig(
            url = "https://music.163.com/#/login",
            title = "网易云音乐登录",
            cookieDomain = "https://music.163.com",
            successCookieKeys = listOf("MUSIC_U", "ntes_kaola_ad"),
            nicknameUrl = "https://music.163.com/api/nuser/account/get",
            nicknameCookieKey = "MUSIC_U"
        ),
        "qqmusic" to LoginConfig(
            url = "https://y.qq.com/portal/profile.html",
            title = "QQ音乐登录",
            cookieDomain = "https://y.qq.com",
            successCookieKeys = listOf("p_skey", "skey", "uin")
        ),
        "kuwo" to LoginConfig(
            url = "https://www.kuwo.cn/login",
            title = "酷我音乐登录",
            cookieDomain = "https://www.kuwo.cn",
            successCookieKeys = listOf("Hm_lpvt_", "token", "kw_token", "SESSIONID")
        ),
        "kugou" to LoginConfig(
            url = "https://www.kugou.com/login/",
            title = "酷狗音乐登录",
            cookieDomain = "https://www.kugou.com",
            successCookieKeys = listOf("kg_mid", "kg_dfid", "KugouID", "KugooGuest")
        ),
        "qishui" to LoginConfig(
            url = "https://music.douyin.com/login",
            title = "汽水音乐登录",
            cookieDomain = "https://music.douyin.com",
            successCookieKeys = listOf("sessionid", "sessionid_ss", "sid_tt", "uid_tt")
        ),
        "bilibili" to LoginConfig(
            url = "https://passport.bilibili.com/login",
            title = "哔哩哔哩登录",
            cookieDomain = "https://www.bilibili.com",
            successCookieKeys = listOf("SESSDATA"),
            nicknameUrl = "https://api.bilibili.com/x/web-interface/nav",
            nicknameCookieKey = "SESSDATA"
        )
    )

    private lateinit var webView: WebView
    private lateinit var config: LoginConfig
    private var loginCompleted = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_login)

        val platform = intent.getStringExtra(EXTRA_PLATFORM) ?: run {
            finish()
            return
        }
        config = configs[platform] ?: run {
            Toast.makeText(this, "不支持的平台: $platform", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 顶部栏
        findViewById<MaterialButton>(R.id.btnBackWebLogin).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvWebLoginTitle).text = config.title
        findViewById<TextView>(R.id.tvWebLoginHint).text = "请在下方页面登录${config.title}\n登录成功后自动返回"

        webView = findViewById(R.id.webViewLogin)

        // 清除旧 Cookie，确保全新登录
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

                // 检查 Cookie 是否包含登录成功标志
                val cookies = CookieManager.getInstance().getCookie(config.cookieDomain)
                Log.d(TAG, "页面加载完成: $url, cookies: ${cookies?.take(80)}")

                val successKey = config.successCookieKeys.firstOrNull { key ->
                    extractCookieValue(cookies, key) != null
                }

                if (successKey != null) {
                    loginCompleted = true
                    onLoginSuccess(cookies ?: "")
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }

        webView.loadUrl(config.url)
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

    private fun onLoginSuccess(fullCookie: String) {
        lifecycleScope.launch {
            var nickname = "${config.title}用户"
            try {
                nickname = withContext(Dispatchers.IO) {
                    fetchNickname(fullCookie)
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取用户信息失败", e)
            }

            val resultIntent = Intent().apply {
                putExtra(EXTRA_RESULT_COOKIE, fullCookie)
                putExtra(EXTRA_RESULT_NICKNAME, nickname)
                putExtra(EXTRA_PLATFORM, intent.getStringExtra(EXTRA_PLATFORM))
            }
            setResult(RESULT_OK, resultIntent)
            Toast.makeText(this@WebLoginActivity, "${config.title}成功！$nickname", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /**
     * 通过平台 API 获取用户昵称
     */
    private fun fetchNickname(fullCookie: String): String {
        val nicknameUrl = config.nicknameUrl ?: return "${config.title}用户"
        val cookieKey = config.nicknameCookieKey ?: return "${config.title}用户"
        val cookieValue = extractCookieValue(fullCookie, cookieKey) ?: return "${config.title}用户"

        val conn = URL(nicknameUrl).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.setRequestProperty("Cookie", "$cookieKey=$cookieValue")
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
        conn.setRequestProperty("Referer", config.cookieDomain)

        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val text = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        conn.disconnect()

        Log.d(TAG, "用户信息API响应: ${text.take(200)}")
        val json = JSONObject(text)

        // B站 nav API
        if (json.optInt("code", -1) == 0) {
            val data = json.optJSONObject("data")
            return data?.optString("uname", "${config.title}用户")?.takeIf { it.isNotBlank() }
                ?: "${config.title}用户"
        }

        // 网易云 account API
        val account = json.optJSONObject("account")
        if (account != null) {
            return account.optString("nickname", "${config.title}用户").takeIf { it.isNotBlank() }
                ?: "${config.title}用户"
        }

        // GitHub API
        val login = json.optString("login", "")
        val name = json.optString("name", "")
        if (login.isNotBlank()) {
            return name.ifBlank { login }
        }

        return "${config.title}用户"
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