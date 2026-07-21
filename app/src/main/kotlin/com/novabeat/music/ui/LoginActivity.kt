package com.novabeat.music.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.app.Activity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.novabeat.music.R
import com.novabeat.music.data.local.LocalAccountManager

class LoginActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LOGIN_TYPE = "login_type"
        const val EXTRA_LOGIN_COOKIE = "login_cookie"
        const val EXTRA_LOGIN_NICKNAME = "login_nickname"

        const val LOGIN_QQ = "qq"
        const val LOGIN_WECHAT = "wechat"
        const val LOGIN_WEIBO = "weibo"
        const val LOGIN_GITHUB = "github"
        const val LOGIN_NETEASE = "netease"
        const val LOGIN_QQMUSIC = "qqmusic"
        const val LOGIN_EMAIL = "email"
        const val LOGIN_BILIBILI = "bilibili"
        const val LOGIN_KUWO = "kuwo"
        const val LOGIN_KUGOU = "kugou"
        const val LOGIN_QISHUI = "qishui"

        private const val REQUEST_WEB_LOGIN = 3001
    }

    private lateinit var accountManager: LocalAccountManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        accountManager = LocalAccountManager(this)

        findViewById<MaterialButton>(R.id.btnBackLogin).setOnClickListener { finish() }

        // 所有第三方登录按钮 — 通过 WebView 真实登录
        val webLoginButtons = mapOf(
            R.id.btnLoginQQ to LOGIN_QQ,
            R.id.btnLoginWeChat to LOGIN_WECHAT,
            R.id.btnLoginWeibo to LOGIN_WEIBO,
            R.id.btnLoginGitHub to LOGIN_GITHUB,
            R.id.btnLoginNetease to LOGIN_NETEASE,
            R.id.btnLoginQQMusic to LOGIN_QQMUSIC,
            R.id.btnLoginBilibili to LOGIN_BILIBILI,
            R.id.btnLoginKuwo to LOGIN_KUWO,
            R.id.btnLoginKugou to LOGIN_KUGOU,
            R.id.btnLoginQishui to LOGIN_QISHUI
        )

        webLoginButtons.forEach { (buttonId, platform) ->
            findViewById<MaterialButton>(buttonId).setOnClickListener {
                val intent = Intent(this, WebLoginActivity::class.java).apply {
                    putExtra(WebLoginActivity.EXTRA_PLATFORM, platform)
                }
                startActivityForResult(intent, REQUEST_WEB_LOGIN)
            }
        }

        // 邮箱登录 — 展开输入区
        val emailContainer = findViewById<View>(R.id.emailLoginContainer)
        findViewById<MaterialButton>(R.id.btnLoginEmail).setOnClickListener {
            if (emailContainer.visibility == View.VISIBLE) {
                emailContainer.visibility = View.GONE
            } else {
                emailContainer.visibility = View.VISIBLE
            }
        }

        // 邮箱登录确认 — 真实验证
        findViewById<MaterialButton>(R.id.btnEmailConfirm).setOnClickListener {
            val email = findViewById<TextInputEditText>(R.id.etEmail).text?.toString()?.trim() ?: ""
            val password = findViewById<TextInputEditText>(R.id.etEmailPassword).text?.toString()?.trim() ?: ""

            if (email.isBlank()) {
                Toast.makeText(this, "请输入邮箱地址", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.isBlank()) {
                Toast.makeText(this, "请输入密码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "邮箱格式不正确", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 真实验证邮箱账号
            val error = accountManager.login(email, password)
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 登录成功
            val nickname = accountManager.getNickname(email)
            onLoginSuccess(LOGIN_EMAIL, "email_session=${System.currentTimeMillis()}", nickname)
        }

        // 邮箱注册按钮
        findViewById<MaterialButton>(R.id.btnEmailRegister).setOnClickListener {
            val email = findViewById<TextInputEditText>(R.id.etEmail).text?.toString()?.trim() ?: ""
            val password = findViewById<TextInputEditText>(R.id.etEmailPassword).text?.toString()?.trim() ?: ""

            if (email.isBlank()) {
                Toast.makeText(this, "请输入邮箱地址", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.isBlank()) {
                Toast.makeText(this, "请输入密码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "邮箱格式不正确", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val error = accountManager.register(email, password)
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Toast.makeText(this, "注册成功，请点击确认登录", Toast.LENGTH_SHORT).show()
        }

        // 显示当前登录类型说明
        val loginType = intent.getStringExtra(EXTRA_LOGIN_TYPE) ?: ""
        val tvHint = findViewById<TextView>(R.id.tvLoginHint)
        if (loginType.isNotBlank()) {
            tvHint.text = "选择登录方式以解锁VIP歌曲"
            tvHint.visibility = View.VISIBLE
        }
    }

    private fun onLoginSuccess(type: String, cookie: String, nickname: String) {
        findViewById<View>(R.id.loginProgress).visibility = View.VISIBLE
        window.decorView.postDelayed({
            findViewById<View>(R.id.loginProgress).visibility = View.GONE

            val resultIntent = Intent().apply {
                putExtra(EXTRA_LOGIN_TYPE, type)
                putExtra(EXTRA_LOGIN_COOKIE, cookie)
                putExtra(EXTRA_LOGIN_NICKNAME, nickname)
            }
            setResult(RESULT_OK, resultIntent)
            Toast.makeText(this, "$nickname 登录成功！", Toast.LENGTH_SHORT).show()
            finish()
        }, 500)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_WEB_LOGIN) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val cookie = data.getStringExtra(WebLoginActivity.EXTRA_RESULT_COOKIE) ?: ""
                val nickname = data.getStringExtra(WebLoginActivity.EXTRA_RESULT_NICKNAME) ?: "用户"
                val platform = data.getStringExtra(WebLoginActivity.EXTRA_PLATFORM) ?: ""

                val resultIntent = Intent().apply {
                    putExtra(EXTRA_LOGIN_TYPE, platform)
                    putExtra(EXTRA_LOGIN_COOKIE, cookie)
                    putExtra(EXTRA_LOGIN_NICKNAME, nickname)
                }
                setResult(RESULT_OK, resultIntent)
                Toast.makeText(this, "$nickname 登录成功！", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}