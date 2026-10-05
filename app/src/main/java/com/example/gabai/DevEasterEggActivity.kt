package com.example.gabai

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class DevEasterEggActivity : AppCompatActivity() {

    private lateinit var loadingView: GabAiLoadingView
    private lateinit var stageContainer: FrameLayout
    private lateinit var tvAngleIndicator: TextView
    private lateinit var seekScrubber: SeekBar
    private lateinit var btnPlayPause: Button

    private var isPlaying = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dev_easter_egg)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        loadingView = findViewById(R.id.dev_loading_view)
        stageContainer = findViewById(R.id.stage_container)
        tvAngleIndicator = findViewById(R.id.tv_angle_indicator)
        seekScrubber = findViewById(R.id.seek_scrubber)
        btnPlayPause = findViewById(R.id.btn_play_pause)

        setupPlaybackControls()
        setupSpeedControls()
        setupSizeControls()
        setupBackgroundControls()
        setupSimulations()
    }

    private fun setupPlaybackControls() {
        findViewById<Button>(R.id.btn_play_entrance).setOnClickListener {
            loadingView.playEntranceAnimation()
        }

        btnPlayPause.setOnClickListener {
            if (isPlaying) {
                isPlaying = false
                btnPlayPause.text = "Play"
                btnPlayPause.setBackgroundColor(Color.parseColor("#334155"))
                tvAngleIndicator.text = "Paused: ${seekScrubber.progress}°"
                loadingView.setProgressManual(seekScrubber.progress / 180f)
            } else {
                isPlaying = true
                btnPlayPause.text = "Pause"
                btnPlayPause.setBackgroundColor(Color.parseColor("#5341CD"))
                tvAngleIndicator.text = "Auto Playing"
                loadingView.resumeAutoAnimation()
            }
        }

        seekScrubber.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    isPlaying = false
                    btnPlayPause.text = "Play"
                    btnPlayPause.setBackgroundColor(Color.parseColor("#334155"))
                    tvAngleIndicator.text = "Manual Scrub: $progress°"
                    loadingView.setProgressManual(progress / 180f)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupSpeedControls() {
        val btnSlow = findViewById<Button>(R.id.btn_speed_slow)
        val btnNorm = findViewById<Button>(R.id.btn_speed_norm)
        val btnFast = findViewById<Button>(R.id.btn_speed_fast)

        fun updateButtons(activeBtn: Button) {
            listOf(btnSlow, btnNorm, btnFast).forEach {
                it.setBackgroundColor(if (it == activeBtn) Color.parseColor("#5341CD") else Color.parseColor("#334155"))
            }
        }

        btnSlow.setOnClickListener {
            loadingView.cycleDurationMs = 2200L
            updateButtons(btnSlow)
        }
        btnNorm.setOnClickListener {
            loadingView.cycleDurationMs = 1400L
            updateButtons(btnNorm)
        }
        btnFast.setOnClickListener {
            loadingView.cycleDurationMs = 850L
            updateButtons(btnFast)
        }
    }

    private fun setupSizeControls() {
        val btnMicro = findViewById<Button>(R.id.btn_size_micro)
        val btnCompact = findViewById<Button>(R.id.btn_size_compact)
        val btnStd = findViewById<Button>(R.id.btn_size_std)
        val btnHero = findViewById<Button>(R.id.btn_size_hero)

        fun applySize(dp: Int, activeBtn: Button) {
            val px = (dp * resources.displayMetrics.density).toInt()
            loadingView.layoutParams = FrameLayout.LayoutParams(px, px, android.view.Gravity.CENTER)
            listOf(btnMicro, btnCompact, btnStd, btnHero).forEach {
                it.setBackgroundColor(if (it == activeBtn) Color.parseColor("#5341CD") else Color.parseColor("#334155"))
            }
        }

        btnMicro.setOnClickListener { applySize(56, btnMicro) }
        btnCompact.setOnClickListener { applySize(84, btnCompact) }
        btnStd.setOnClickListener { applySize(112, btnStd) }
        btnHero.setOnClickListener { applySize(160, btnHero) }
    }

    private fun setupBackgroundControls() {
        val btnDark = findViewById<Button>(R.id.btn_bg_dark)
        val btnLight = findViewById<Button>(R.id.btn_bg_light)
        val btnAurora = findViewById<Button>(R.id.btn_bg_aurora)

        // Default to Light Canvas
        stageContainer.background = null
        stageContainer.setBackgroundColor(Color.parseColor("#F8FAFC"))
        btnDark.setBackgroundColor(Color.parseColor("#334155"))
        btnLight.setBackgroundColor(Color.parseColor("#5341CD"))
        btnAurora.setBackgroundColor(Color.parseColor("#334155"))

        btnDark.setOnClickListener {
            stageContainer.background = null
            stageContainer.setBackgroundColor(Color.parseColor("#0F172A"))
            btnDark.setBackgroundColor(Color.parseColor("#5341CD"))
            btnLight.setBackgroundColor(Color.parseColor("#334155"))
            btnAurora.setBackgroundColor(Color.parseColor("#334155"))
        }

        btnLight.setOnClickListener {
            stageContainer.background = null
            stageContainer.setBackgroundColor(Color.parseColor("#F8FAFC"))
            btnDark.setBackgroundColor(Color.parseColor("#334155"))
            btnLight.setBackgroundColor(Color.parseColor("#5341CD"))
            btnAurora.setBackgroundColor(Color.parseColor("#334155"))
        }

        btnAurora.setOnClickListener {
            stageContainer.background = ContextCompat.getDrawable(this, R.drawable.bg_splash_aurora)
            btnDark.setBackgroundColor(Color.parseColor("#334155"))
            btnLight.setBackgroundColor(Color.parseColor("#334155"))
            btnAurora.setBackgroundColor(Color.parseColor("#5341CD"))
        }
    }

    private fun setupSimulations() {
        findViewById<Button>(R.id.btn_test_global_overlay).setOnClickListener {
            GabAIUtils.showGlobalLoading(this, "Testing GabAI Micro-Loader...")
            Handler(Looper.getMainLooper()).postDelayed({
                GabAIUtils.hideGlobalLoading(this)
                Toast.makeText(this, "Global Loader Simulation Complete!", Toast.LENGTH_SHORT).show()
            }, 3000)
        }

        findViewById<Button>(R.id.btn_test_ai_quiz).setOnClickListener {
            GabAIUtils.showGlobalLoading(this, "Generating Lesson Quiz from Notes...")
            Handler(Looper.getMainLooper()).postDelayed({
                GabAIUtils.hideGlobalLoading(this)
                Toast.makeText(this, "Lesson Quiz Ready!", Toast.LENGTH_SHORT).show()
            }, 3000)
        }
    }
}
