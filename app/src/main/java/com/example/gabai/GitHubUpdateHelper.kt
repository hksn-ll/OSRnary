package com.example.gabai

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object GitHubUpdateHelper {

    private const val TAG = "GitHubUpdateHelper"

    // Primary endpoint: raw GitHub configuration
    private const val VERSION_URL = "https://raw.githubusercontent.com/hksn-ll/OSRnary/main/app-version.json"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    data class VersionInfo(
        val versionCode: Int,
        val versionName: String,
        val minRequiredVersionCode: Int,
        val forceUpdate: Boolean,
        val title: String,
        val message: String,
        val downloadUrl: String,
        val apkUrl: String,
        val changelog: String
    )

    /**
     * Checks GitHub for updates asynchronously.
     * @param activity current active activity
     * @param onProceed callback invoked if no update is required or if offline (allow app use)
     */
    fun checkUpdate(activity: Activity, onProceed: () -> Unit) {
        val currentVersionCode = BuildConfig.VERSION_CODE
        val currentVersionName = BuildConfig.VERSION_NAME

        val requestUrl = "$VERSION_URL?t=${System.currentTimeMillis()}"
        val request = Request.Builder()
            .url(requestUrl)
            .header("Cache-Control", "no-cache")
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "Failed to check update from GitHub: ${e.message}")
                // Offline or GitHub unreachable: let user continue
                Handler(Looper.getMainLooper()).post { onProceed() }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyString = response.body?.string()
                if (!response.isSuccessful || bodyString.isNullOrBlank()) {
                    Log.w(TAG, "Update check returned unsuccessful response: ${response.code}")
                    Handler(Looper.getMainLooper()).post { onProceed() }
                    return
                }

                try {
                    val json = JSONObject(bodyString)
                    val info = VersionInfo(
                        versionCode = json.optInt("versionCode", 1),
                        versionName = json.optString("versionName", "1.0.0"),
                        minRequiredVersionCode = json.optInt("minRequiredVersionCode", 1),
                        forceUpdate = json.optBoolean("forceUpdate", false),
                        title = json.optString("title", "Update Required"),
                        message = json.optString("message", "A new version of GabAI is available on GitHub. Please update to continue."),
                        downloadUrl = json.optString("downloadUrl", "https://github.com/hksn-ll/OSRnary/releases/latest"),
                        apkUrl = json.optString("apkUrl", ""),
                        changelog = json.optString("changelog", "")
                    )

                    val isUpdateRequired = (currentVersionCode < info.minRequiredVersionCode) ||
                            (info.forceUpdate && currentVersionCode < info.versionCode)

                    Handler(Looper.getMainLooper()).post {
                        if (isUpdateRequired && !activity.isFinishing && !activity.isDestroyed) {
                            showForceUpdateDialog(activity, info, currentVersionName)
                        } else {
                            onProceed()
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing update json: ${e.message}", e)
                    Handler(Looper.getMainLooper()).post { onProceed() }
                }
            }
        })
    }

    /**
     * Displays an un-dismissible, full-fidelity modal dialog requiring the user to update.
     */
    private fun showForceUpdateDialog(activity: Activity, info: VersionInfo, currentVersionName: String) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_force_update, null)
        dialog.setContentView(view)

        // Make background transparent so rounded card corners display cleanly
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvTitle = view.findViewById<TextView>(R.id.tv_update_title)
        val tvMessage = view.findViewById<TextView>(R.id.tv_update_message)
        val tvCurrentVersion = view.findViewById<TextView>(R.id.tv_current_version)
        val tvNewVersion = view.findViewById<TextView>(R.id.tv_new_version)
        val tvChangelog = view.findViewById<TextView>(R.id.tv_changelog)
        val btnUpdateNow = view.findViewById<MaterialButton>(R.id.btn_update_now)
        val btnExitApp = view.findViewById<TextView>(R.id.btn_exit_app)

        tvTitle.text = info.title
        tvMessage.text = info.message
        tvCurrentVersion.text = "Current: v$currentVersionName"
        tvNewVersion.text = "New: v${info.versionName}"

        if (info.changelog.isNotBlank()) {
            tvChangelog.visibility = View.VISIBLE
            tvChangelog.text = info.changelog
        } else {
            tvChangelog.visibility = View.GONE
        }

        btnUpdateNow.setOnClickListener {
            val targetUrl = when {
                info.apkUrl.isNotBlank() -> info.apkUrl
                info.downloadUrl.isNotBlank() -> info.downloadUrl
                else -> "https://github.com/hksn-ll/OSRnary/releases/latest"
            }
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                activity.startActivity(browserIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to open update URL: $targetUrl", e)
            }
        }

        btnExitApp.setOnClickListener {
            activity.finishAffinity()
        }

        dialog.show()
    }
}
