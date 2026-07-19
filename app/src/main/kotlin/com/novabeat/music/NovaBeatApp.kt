package com.novabeat.music

import android.app.Application
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

class NovaBeatApp : Application() {
    
    companion object {
        private const val TAG = "NovaBeatApp"
        private const val CRASH_LOG_FILE = "crash_log.txt"
    }
    
    private fun getLogFile(): File {
        // 优先使用外部存储的 Documents 目录（用户可访问）
        val externalDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        if (externalDir != null && (externalDir.exists() || externalDir.mkdirs())) {
            val novaBeatDir = File(externalDir, "NovaBeat")
            if (novaBeatDir.exists() || novaBeatDir.mkdirs()) {
                return File(novaBeatDir, CRASH_LOG_FILE)
            }
        }
        // 回退到内部存储
        return File(filesDir, CRASH_LOG_FILE)
    }
    
    override fun onCreate() {
        super.onCreate()
        setupCrashHandler()
        Log.i(TAG, "NovaBeat应用启动")
    }
    
    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e(TAG, "未捕获的异常", throwable)
                saveCrashLog(throwable)
            } catch (e: Exception) {
                Log.e(TAG, "保存崩溃日志失败", e)
            }
            // 调用默认处理器
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
    
    private fun saveCrashLog(throwable: Throwable) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val logFile = getLogFile()
            
            FileWriter(logFile, true).use { writer ->
                PrintWriter(writer).use { printWriter ->
                    printWriter.println("=== 崩溃时间: $timestamp ===")
                    printWriter.println("异常类型: ${throwable.javaClass.simpleName}")
                    printWriter.println("异常信息: ${throwable.message}")
                    printWriter.println("堆栈跟踪:")
                    throwable.printStackTrace(printWriter)
                    printWriter.println()
                    printWriter.println("设备信息:")
                    printWriter.println("Android版本: ${android.os.Build.VERSION.RELEASE}")
                    printWriter.println("SDK版本: ${android.os.Build.VERSION.SDK_INT}")
                    printWriter.println("设备型号: ${android.os.Build.MODEL}")
                    printWriter.println("制造商: ${android.os.Build.MANUFACTURER}")
                    printWriter.println()
                    printWriter.println("=".repeat(50))
                    printWriter.println()
                }
            }
            Log.i(TAG, "崩溃日志已保存到: ${logFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "保存崩溃日志失败", e)
        }
    }
    
    fun getCrashLog(): String {
        return try {
            val logFile = getLogFile()
            if (logFile.exists()) {
                logFile.readText()
            } else {
                "没有崩溃日志"
            }
        } catch (e: Exception) {
            "读取崩溃日志失败: ${e.message}"
        }
    }
    
    fun clearCrashLog() {
        try {
            val logFile = getLogFile()
            if (logFile.exists()) {
                logFile.delete()
                Log.i(TAG, "崩溃日志已清除")
            }
        } catch (e: Exception) {
            Log.e(TAG, "清除崩溃日志失败", e)
        }
    }
}