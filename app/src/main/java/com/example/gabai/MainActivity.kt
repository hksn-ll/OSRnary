package com.example.gabai

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private lateinit var bubbleSwitch: MaterialSwitch
    private lateinit var projectionManager: MediaProjectionManager

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startFloatingService(result.resultCode, result.data!!)
            bubbleSwitch.isChecked = true
            moveTaskToBack(true)
        } else {
            Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
            bubbleSwitch.isChecked = false
        }
    }

    override fun onResume() {
        super.onResume()
        updateLevelUI()
        // We are inside the app -> HIDE the bubble
        if (isServiceRunning()) {
            val intent = Intent(this, FloatingControlService::class.java)
            intent.action = "ACTION_HIDE"
            startService(intent)
        }
    }

    override fun onPause() {
        super.onPause()
        // We are leaving the app -> SHOW the bubble (if switch is ON)
        if (isServiceRunning() && bubbleSwitch.isChecked) {
            val intent = Intent(this, FloatingControlService::class.java)
            intent.action = "ACTION_SHOW"
            startService(intent)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        // Inside MainActivity.kt onCreate
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        // Inside MainActivity.kt onCreate
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }
        if (user == null) {
            // No one is logged in -> Send them to a new Login/Register screen
            // val intent = Intent(this, AuthActivity::class.java)
            // startActivity(intent)
            // finish()
        } else {
            // User is logged in -> We can now pull their name/role from Firestore
            Toast.makeText(this, "Welcome back to GabAI!", Toast.LENGTH_SHORT).show()
        }
        super.onCreate(savedInstanceState)
        // ... inside onCreate ...
        setContentView(R.layout.activity_main)

        // FIX: Handle Window Insets (Padding) Manually
        // This pushes the content down exactly the height of the status bar
        val mainContainer = findViewById<android.view.View>(R.id.main_container)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(mainContainer) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                v.paddingLeft,
                systemBars.top + 40, // Add system bar height + 40px extra breathing room
                v.paddingRight,
                v.paddingBottom
            )
            insets
        }

        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // 1. Bind the Toggle Switch (Top Right)
        bubbleSwitch = findViewById(R.id.bubble_switch)
        bubbleSwitch.isChecked = isServiceRunning()

        bubbleSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Please allow 'Display over other apps'", Toast.LENGTH_LONG).show()
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    startActivity(intent)
                    bubbleSwitch.isChecked = false
                } else {
                    if (!isServiceRunning()) {
                        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
                    }
                }
            } else {
                stopService(Intent(this, FloatingControlService::class.java))
            }
        }

        // 2. Bind Dashboard Buttons (Placeholders for now)
        // Inside onCreate in MainActivity.kt
        findViewById<Button>(R.id.btn_start_quiz).setOnClickListener {
            val intent = Intent(this, QuizActivity::class.java)
            startActivity(intent)
        }
        findViewById<Button>(R.id.btn_favs).setOnClickListener {
            val intent = Intent(this, FavoritesActivity::class.java)
            startActivity(intent)
        }
        // Inside onCreate in MainActivity.kt
        findViewById<Button>(R.id.btn_history).setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }
        updateLevelUI()
    }

    private fun startFloatingService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, FloatingControlService::class.java)
        serviceIntent.putExtra("RESULT_CODE", resultCode)
        serviceIntent.putExtra("DATA", data)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (FloatingControlService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }
    private fun updateLevelUI() {
        val level = XPManager.getLevel(this)
        val xp = XPManager.getXP(this)

        findViewById<android.widget.TextView>(R.id.tv_level_label).text = "LEVEL $level"
        findViewById<android.widget.TextView>(R.id.tv_xp_label).text = "$xp / 100 XP"
        findViewById<android.widget.ProgressBar>(R.id.xp_progress_bar).progress = xp
    }
}