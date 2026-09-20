package com.example.gabai

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
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

    private var lastBackgroundCheckTime = 0L

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
     * @param isBackground true if called during lifecycle transitions (debounced every 30s)
     * @param forceShow true if explicitly requested by user (shows feedback toast if up-to-date)
     * @param onProceed callback invoked if no update is required or if offline (allow app use)
     */
    fun checkUpdate(
        activity: Activity,
        isBackground: Boolean = false,
        forceShow: Boolean = false,
        onProceed: () -> Unit = {}
    ) {
        val now = System.currentTimeMillis()
        if (isBackground && (now - lastBackgroundCheckTime < 30_000)) {
            onProceed()
            return
        }
        lastBackgroundCheckTime = now

        val currentVersionCode = BuildConfig.VERSION_CODE
        val currentVersionName = BuildConfig.VERSION_NAME

        if (forceShow) {
            Toast.makeText(activity, "Checking for latest build...", Toast.LENGTH_SHORT).show()
        }

        val requestUrl = "$VERSION_URL?t=$now"
        val request = Request.Builder()
            .url(requestUrl)
            .header("Cache-Control", "no-cache")
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "Failed to check update from GitHub: ${e.message}")
                Handler(Looper.getMainLooper()).post {
                    if (forceShow && !activity.isFinishing && !activity.isDestroyed) {
                        Toast.makeText(activity, "Update check failed: Check connection", Toast.LENGTH_SHORT).show()
                    }
                    onProceed()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyString = response.body?.string()
                if (!response.isSuccessful || bodyString.isNullOrBlank()) {
                    Log.w(TAG, "Update check returned unsuccessful response: ${response.code}")
                    Handler(Looper.getMainLooper()).post {
                        if (forceShow && !activity.isFinishing && !activity.isDestroyed) {
                            Toast.makeText(activity, "Server returned no update info", Toast.LENGTH_SHORT).show()
                        }
                        onProceed()
                    }
                    return
                }

                try {
                    val json = JSONObject(bodyString)
                    val info = VersionInfo(
                        versionCode = json.optInt("versionCode", 1),
                        versionName = json.optString("versionName", "1.0.0"),
                        minRequiredVersionCode = json.optInt("minRequiredVersionCode", 1),
                        forceUpdate = json.optBoolean("forceUpdate", false),
                        title = json.optString("title", "New Build Available"),
                        message = json.optString("message", "A new build of GabAI is available. Update to get the latest features!"),
                        downloadUrl = json.optString("downloadUrl", "https://github.com/hksn-ll/OSRnary/releases/latest"),
                        apkUrl = json.optString("apkUrl", "https://github.com/hksn-ll/OSRnary/releases/latest/download/app-debug.apk"),
                        changelog = json.optString("changelog", "")
                    )

                    // Nightly build update detection:
                    // Trigger if versionCode is greater, OR if versionName differs and code >= current
                    val isNewerVersion = (info.versionCode > currentVersionCode) ||
                            (info.versionName.isNotBlank() && !info.versionName.equals(currentVersionName, ignoreCase = true) && info.versionCode >= currentVersionCode)

                    if (!isNewerVersion) {
                        Handler(Looper.getMainLooper()).post {
                            if (forceShow && !activity.isFinishing && !activity.isDestroyed) {
                                Toast.makeText(activity, "You are on the latest build (v$currentVersionName)!", Toast.LENGTH_SHORT).show()
                            }
                            onProceed()
                        }
                        return
                    }

                    // 🟢 CRITICAL: Verify that the APK release asset is ACTUALLY published on GitHub
                    // Avoids locking out the user or triggering an update loop while GitHub Actions is compiling in the cloud!
                    val expectedApkUrl = if (info.versionName.isNotBlank()) {
                        "https://github.com/hksn-ll/OSRnary/releases/download/${info.versionName}/app-debug.apk"
                    } else {
                        info.apkUrl.ifBlank { "https://github.com/hksn-ll/OSRnary/releases/latest/download/app-debug.apk" }
                    }

                    val headRequest = Request.Builder()
                        .url(expectedApkUrl)
                        .head()
                        .build()

                    httpClient.newCall(headRequest).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            Log.w(TAG, "Failed to verify release asset: ${e.message}. Allowing user to proceed.")
                            Handler(Looper.getMainLooper()).post { onProceed() }
                        }

                        override fun onResponse(call: Call, response: Response) {
                            // GitHub returns 302 Found redirecting to release-assets on success, or 200 OK.
                            val isAssetLive = response.isSuccessful || response.code in 300..399
                            response.close()

                            Handler(Looper.getMainLooper()).post {
                                if (isAssetLive && !activity.isFinishing && !activity.isDestroyed) {
                                    val verifiedInfo = info.copy(apkUrl = expectedApkUrl)
                                    showUpdateDialog(activity, verifiedInfo, currentVersionName)
                                } else {
                                    // New version exists in code but GitHub Actions has not finished uploading APK yet!
                                    Log.i(TAG, "Release asset for v${info.versionName} is not yet available (HTTP ${response.code}). Build is in progress.")
                                    if (forceShow && !activity.isFinishing && !activity.isDestroyed) {
                                        Toast.makeText(
                                            activity,
                                            "v${info.versionName} is currently compiling on GitHub Actions. Please check back in a minute.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    onProceed()
                                }
                            }
                        }
                    })

                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing update json: ${e.message}", e)
                    Handler(Looper.getMainLooper()).post {
                        if (forceShow && !activity.isFinishing && !activity.isDestroyed) {
                            Toast.makeText(activity, "Error parsing update data", Toast.LENGTH_SHORT).show()
                        }
                        onProceed()
                    }
                }
            }
        })
    }

    /**
     * Displays a strictly mandatory, un-dismissible update dialog with in-app download.
     */
    private fun showUpdateDialog(
        activity: Activity,
        info: VersionInfo,
        currentVersionName: String
    ) {
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

        btnExitApp.text = "Exit Application"
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

                        if (totalBytes > 0) {
                            val percent = ((downloadedBytes * 100) / totalBytes).toInt()
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                Handler(Looper.getMainLooper()).post {
                                    onProgress(percent, downloadedBytes, totalBytes)
                                }
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
                    Log.e(TAG, "File write error during APK download: ${e.message}", e)
                    Handler(Looper.getMainLooper()).post {
                        onError(e.localizedMessage ?: "Storage write error")
                    }
                }
            }
        })
    }

    /**
     * Triggers the Android package installer Intent using a secure FileProvider URI.
     */
    private fun installApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists()) {
                Log.e(TAG, "Cannot install: APK file does not exist at ${apkFile.absolutePath}")
                return
            }

            // Android 8.0+ (Oreo): Check if permission to install unknown apps is granted
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer: ${e.message}", e)
        }
    }
}
