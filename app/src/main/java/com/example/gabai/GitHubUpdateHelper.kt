package com.example.gabai

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

object GitHubUpdateHelper {

    private const val TAG = "GitHubUpdateHelper"

    // Primary endpoint: raw GitHub configuration
    private const val VERSION_URL = "https://raw.githubusercontent.com/hksn-ll/OSRnary/main/app-version.json"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
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
                        message = json.optString("message", "A new version of GabAI is available. Please update to continue."),
                        downloadUrl = json.optString("downloadUrl", "https://github.com/hksn-ll/OSRnary/releases/latest"),
                        apkUrl = json.optString("apkUrl", "https://github.com/hksn-ll/OSRnary/releases/latest/download/app-debug.apk"),
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
     * Supports in-app downloading and triggering the system package installer.
     */
    private fun showForceUpdateDialog(activity: Activity, info: VersionInfo, currentVersionName: String) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_force_update, null)
        dialog.setContentView(view)

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvTitle = view.findViewById<TextView>(R.id.tv_update_title)
        val tvMessage = view.findViewById<TextView>(R.id.tv_update_message)
        val tvCurrentVersion = view.findViewById<TextView>(R.id.tv_current_version)
        val tvNewVersion = view.findViewById<TextView>(R.id.tv_new_version)
        val tvChangelog = view.findViewById<TextView>(R.id.tv_changelog)
        val btnUpdateNow = view.findViewById<MaterialButton>(R.id.btn_update_now)
        val btnExitApp = view.findViewById<TextView>(R.id.btn_exit_app)

        val containerProgress = view.findViewById<LinearLayout>(R.id.container_download_progress)
        val tvStatus = view.findViewById<TextView>(R.id.tv_download_status)
        val tvPercent = view.findViewById<TextView>(R.id.tv_download_percent)
        val progressBar = view.findViewById<ProgressBar>(R.id.progress_bar_download)
        val tvSize = view.findViewById<TextView>(R.id.tv_download_size)

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

        var downloadedApkFile: File? = null

        btnUpdateNow.setOnClickListener {
            if (downloadedApkFile != null && downloadedApkFile!!.exists()) {
                installApk(activity, downloadedApkFile!!)
                return@setOnClickListener
            }

            val targetUrl = when {
                info.apkUrl.isNotBlank() -> info.apkUrl
                info.downloadUrl.isNotBlank() -> info.downloadUrl
                else -> "https://github.com/hksn-ll/OSRnary/releases/latest/download/app-debug.apk"
            }

            // Begin in-app download
            btnUpdateNow.visibility = View.GONE
            containerProgress.visibility = View.VISIBLE
            tvStatus.text = "Connecting to server..."
            progressBar.progress = 0
            tvPercent.text = "0%"
            tvSize.text = "0 MB"

            downloadApkInApp(
                activity = activity,
                url = targetUrl,
                onProgress = { percent, downloadedBytes, totalBytes ->
                    val downloadedMb = downloadedBytes / (1024.0 * 1024.0)
                    val totalMb = totalBytes / (1024.0 * 1024.0)
                    tvStatus.text = "Downloading update..."
                    progressBar.progress = percent
                    tvPercent.text = "$percent%"
                    tvSize.text = String.format(Locale.US, "%.1f MB / %.1f MB", downloadedMb, totalMb)
                },
                onComplete = { apkFile ->
                    downloadedApkFile = apkFile
                    tvStatus.text = "Download complete!"
                    progressBar.progress = 100
                    tvPercent.text = "100%"
                    btnUpdateNow.visibility = View.VISIBLE
                    btnUpdateNow.text = "Install Update Now ➔"
                    installApk(activity, apkFile)
                },
                onError = { errorMsg ->
                    tvStatus.text = "Download failed: $errorMsg"
                    btnUpdateNow.visibility = View.VISIBLE
                    btnUpdateNow.text = "Retry Download"
                }
            )
        }

        btnExitApp.setOnClickListener {
            activity.finishAffinity()
        }

        dialog.show()
    }

    /**
     * Downloads the APK file directly into the application's external downloads directory with progress reporting.
     */
    private fun downloadApkInApp(
        activity: Activity,
        url: String,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit,
        onComplete: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        val request = Request.Builder()
            .url(url)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "In-app download failed: ${e.message}", e)
                Handler(Looper.getMainLooper()).post {
                    onError(e.localizedMessage ?: "Network error")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Handler(Looper.getMainLooper()).post {
                        onError("Server responded with code ${response.code}")
                    }
                    return
                }

                val body = response.body
                if (body == null) {
                    Handler(Looper.getMainLooper()).post {
                        onError("Empty response from server")
                    }
                    return
                }

                try {
                    val downloadDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: activity.cacheDir
                    if (!downloadDir.exists()) {
                        downloadDir.mkdirs()
                    }
                    val apkFile = File(downloadDir, "GabAI-Update.apk")
                    if (apkFile.exists()) {
                        apkFile.delete()
                    }

                    val totalBytes = body.contentLength()
                    var downloadedBytes: Long = 0
                    val buffer = ByteArray(8192)

                    val inputStream = body.byteStream()
                    val outputStream = FileOutputStream(apkFile)

                    var bytesRead: Int
                    var lastReportedPercent = -1

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val percent = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
                        if (percent != lastReportedPercent) {
                            lastReportedPercent = percent
                            Handler(Looper.getMainLooper()).post {
                                onProgress(percent, downloadedBytes, totalBytes)
                            }
                        }
                    }

                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()

                    Handler(Looper.getMainLooper()).post {
                        onComplete(apkFile)
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error saving APK: ${e.message}", e)
                    Handler(Looper.getMainLooper()).post {
                        onError(e.localizedMessage ?: "Failed to save file")
                    }
                }
            }
        })
    }

    /**
     * Triggers the Android Package Installer via FileProvider.
     */
    fun installApk(activity: Activity, apkFile: File) {
        if (!apkFile.exists()) {
            Log.e(TAG, "APK file does not exist at: ${apkFile.absolutePath}")
            return
        }

        try {
            // Android 8.0+ Unknown sources check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${activity.packageName}")
                    }
                    activity.startActivity(intent)
                    return
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            activity.startActivity(installIntent)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer: ${e.message}", e)
        }
    }
}
