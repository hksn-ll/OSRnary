package com.example.gabai

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlin.system.exitProcess

class GlobalExceptionHandler(
    private val defaultHandler: Thread.UncaughtExceptionHandler?,
    private val context: Context
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, exception: Throwable) {
        val stackTrace = Log.getStackTraceString(exception)

        // 1. SAVE SYNCHRONOUSLY (CRITICAL)
        context.getSharedPreferences("GabAI_Prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("pending_crash_log", stackTrace)
            .commit() // MUST be commit() for crashes

        // 2. RESTART
        val intent = Intent(context, SplashActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)

        // 3. KILL PROCESS
        android.os.Process.killProcess(android.os.Process.myPid())
        exitProcess(2)
    }
}