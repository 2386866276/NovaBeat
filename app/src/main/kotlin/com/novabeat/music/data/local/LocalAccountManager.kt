package com.novabeat.music.data.local

import android.content.Context
import android.util.Base64
import java.security.MessageDigest

/**
 * 本地邮箱账号管理器
 * 实现注册、登录、密码验证功能，数据存储在 SharedPreferences
 * 密码使用 SHA-256 + salt 加密存储，不保存明文
 */
class LocalAccountManager(context: Context) {

    companion object {
        private const val PREF_NAME = "novabeat_accounts"
        private const val KEY_PREFIX = "account_"
        private const val SALT = "NovaBeat_2024_Salt"
    }

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * 注册新账号
     * @return null=成功, 非null=错误消息
     */
    fun register(email: String, password: String): String? {
        if (!isValidEmail(email)) return "邮箱格式不正确"
        if (password.length < 6) return "密码至少6位"
        if (accountExists(email)) return "该邮箱已注册"

        val hashedPassword = hashPassword(password)
        prefs.edit()
            .putString("${KEY_PREFIX}${email}_password", hashedPassword)
            .putString("${KEY_PREFIX}${email}_nickname", email.substringBefore("@"))
            .apply()
        return null
    }

    /**
     * 登录验证
     * @return null=成功, 非null=错误消息
     */
    fun login(email: String, password: String): String? {
        if (!accountExists(email)) return "该邮箱未注册，请先注册"

        val storedPassword = prefs.getString("${KEY_PREFIX}${email}_password", "") ?: ""
        if (storedPassword != hashPassword(password)) {
            return "邮箱或密码错误"
        }

        return null
    }

    /**
     * 获取用户昵称
     */
    fun getNickname(email: String): String {
        return prefs.getString("${KEY_PREFIX}${email}_nickname", email.substringBefore("@"))
            ?: email.substringBefore("@")
    }

    /**
     * 检查账号是否存在
     */
    fun accountExists(email: String): Boolean {
        return prefs.contains("${KEY_PREFIX}${email}_password")
    }

    private fun hashPassword(password: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest((password + SALT).toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}