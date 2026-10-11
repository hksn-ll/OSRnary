package com.example.gabai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat

class GabAIAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        private var activeInstance: GabAIAccessibilityService? = null

        val isRunning: Boolean
            get() = activeInstance != null

        fun getInstance(): GabAIAccessibilityService? = activeInstance

        fun isServiceEnabledInSettings(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            val expected = ComponentName(context, GabAIAccessibilityService::class.java).flattenToString()
            val shortExpected = ComponentName(context, GabAIAccessibilityService::class.java).flattenToShortString()
            while (colonSplitter.hasNext()) {
                val componentNameString = colonSplitter.next()
                if (componentNameString.equals(expected, ignoreCase = true) ||
                    componentNameString.equals(shortExpected, ignoreCase = true)
                ) {
                    return true
                }
            }
            return false
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            serviceInfo = info
        } catch (_: Exception) {
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (activeInstance == this) {
            activeInstance = null
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (activeInstance == this) {
            activeInstance = null
        }
        super.onDestroy()
    }

    fun captureScreen(callback: (Bitmap?, Int) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            callback(null, -1)
            return
        }
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                ContextCompat.getMainExecutor(applicationContext),
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        var softwareBitmap: Bitmap? = null
                        try {
                            val hardwareBuffer = screenshotResult.hardwareBuffer
                            val colorSpace = screenshotResult.colorSpace
                            val hardwareBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                            if (hardwareBitmap != null) {
                                softwareBitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                                hardwareBitmap.recycle()
                            }
                            hardwareBuffer.close()
                        } catch (e: Throwable) {
                            Log.e("GabAI", "Error processing screenshot hardware buffer: ${e.message}", e)
                        }
                        callback(softwareBitmap, 0)
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.e("GabAI", "takeScreenshot onFailure errorCode: $errorCode")
                        callback(null, errorCode)
                    }
                }
            )
        } catch (e: Throwable) {
            Log.e("GabAI", "takeScreenshot exception: ${e.message}", e)
            callback(null, -1)
        }
    }
}
