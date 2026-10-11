package com.example.gabai

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.SparseArray
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import java.util.Locale
import kotlin.math.abs

class StudentIntroActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        const val EXTRA_REPLAY = "extra_replay"
        const val PREF_HAS_SEEN_INTRO = "has_seen_student_intro"
    }

    private data class SlideTheme(
        val chapterLabel: String,
        val stepTitle: String,
        val accentHex: String,
        val badgeBgHex: String
    )

    private data class SampleWordData(
        val word: String,
        val pos: String,
        val phonetic: String,
        val definition: String,
        val contextRole: String
    )

    private val slideThemes = listOf(
        SlideTheme("OVERVIEW", "Interactive Tour • 1 of 5", "#5341CD", "#EEF2FF"),
        SlideTheme("FLOATING LENS", "Interactive Tour • 2 of 5", "#5341CD", "#EDE9FE"),
        SlideTheme("SMART SCANNER", "Interactive Tour • 3 of 5", "#059669", "#ECFDF5"),
        SlideTheme("QUESTS & XP", "Interactive Tour • 4 of 5", "#B45309", "#FEF3C7"),
        SlideTheme("LIBRARY & QUIZ", "Interactive Tour • 5 of 5", "#0D9488", "#CCFBF1")
    )

    private val bubbleWords = listOf(
        SampleWordData(
            word = "cascade",
            pos = "noun",
            phonetic = "/kæˈskeɪd/",
            definition = "A steep, rapid outpouring or succession of elements moving in graceful, continuous descent.",
            contextRole = "Functions as a vivid visual metaphor emphasizing the abundance, motion, and luster of Della's hair."
        ),
        SampleWordData(
            word = "luminescent",
            pos = "adjective",
            phonetic = "/ˌluː.mɪˈnɛs.ənt/",
            definition = "Emitting a soft, radiant glow of light not caused by intense heat; softly shining.",
            contextRole = "Establishes a serene, dreamlike atmosphere around the twilight setting of the scene."
        ),
        SampleWordData(
            word = "resilient",
            pos = "adjective",
            phonetic = "/rɪˈzɪl.jənt/",
            definition = "Able to withstand or recover quickly from difficult conditions, adversity, or emotional hardship.",
            contextRole = "Highlights the protagonist's enduring inner strength despite personal sacrifice."
        )
    )

    private val ocrWords = listOf(
        SampleWordData(
            word = "photosynthesis",
            pos = "noun",
            phonetic = "/ˌfoʊ.toʊˈsɪn.θə.sɪs/",
            definition = "The biochemical process in which green plants transform solar radiation into storable chemical energy.",
            contextRole = "Primary energy conversion pathway in autotrophic organisms."
        ),
        SampleWordData(
            word = "chloroplasts",
            pos = "noun (plural)",
            phonetic = "/ˈklɔːr.ə.plæsts/",
            definition = "Specialized plant cell organelles containing chlorophyll where light energy is captured and converted.",
            contextRole = "Acts as the microscopic solar engine inside green plant tissues."
        ),
        SampleWordData(
            word = "metabolism",
            pos = "noun",
            phonetic = "/məˈtæb.ə.lɪz.əm/",
            definition = "The complete network of chemical reactions within a living organism that sustain life and cellular growth.",
            contextRole = "Frames the broader biological context in which cellular energy conversion occurs."
        )
    )

    private lateinit var viewPager: ViewPager2
    private lateinit var dotsContainer: LinearLayout
    private lateinit var storySegmentsContainer: LinearLayout
    private lateinit var btnPrev: TextView
    private lateinit var btnNext: TextView
    private lateinit var btnSkip: TextView
    private lateinit var tvStepCounter: TextView
    private lateinit var tvChapterBadge: TextView

    private val totalSlides = 5
    private var isReplayMode = false
    private val boundHolders = SparseArray<IntroSlideViewHolder>()

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GabAIUtils.applyHardwareMaxRefreshRate(this)
        setContentView(R.layout.activity_student_intro)

        isReplayMode = intent.getBooleanExtra(EXTRA_REPLAY, false)

        try {
            tts = TextToSpeech(this, this)
        } catch (_: Exception) {
            // Device without TTS service
        }

        val root = findViewById<View>(R.id.intro_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            findViewById<View>(R.id.header_intro)?.setPadding(
                20.dpToPx(),
                systemBars.top + 12.dpToPx(),
                20.dpToPx(),
                8.dpToPx()
            )
            findViewById<View>(R.id.bottom_panel_intro)?.setPadding(
                18.dpToPx(),
                6.dpToPx(),
                18.dpToPx(),
                systemBars.bottom + 14.dpToPx()
            )
            insets
        }

        viewPager = findViewById(R.id.view_pager_intro)
        dotsContainer = findViewById(R.id.ll_dots_container)
        storySegmentsContainer = findViewById(R.id.ll_story_segments)
        btnPrev = findViewById(R.id.btn_intro_prev)
        btnNext = findViewById(R.id.btn_intro_next)
        btnSkip = findViewById(R.id.btn_intro_skip)
        tvStepCounter = findViewById(R.id.tv_intro_step_counter)
        tvChapterBadge = findViewById(R.id.tv_intro_chapter_badge)

        setupStorySegments()
        setupDots()
        setupViewPager()
        setupListeners()
        updateUiState(0)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setSpeechRate(0.92f)
            isTtsReady = true
        }
    }

    private fun speakWord(word: String, buttonLabel: TextView?, defaultText: String) {
        if (isTtsReady && tts != null) {
            tts?.stop()
            buttonLabel?.text = "Speaking..."
            tts?.speak(word, TextToSpeech.QUEUE_FLUSH, null, "intro_tts_$word")
            buttonLabel?.postDelayed({
                buttonLabel.text = defaultText
            }, 1100L)
        } else {
            buttonLabel?.text = "Playing..."
            buttonLabel?.postDelayed({
                buttonLabel.text = defaultText
            }, 900L)
        }
    }

    private fun setupStorySegments() {
        storySegmentsContainer.removeAllViews()
        for (i in 0 until totalSlides) {
            val seg = View(this)
            val lp = LinearLayout.LayoutParams(0, 4.dpToPx(), 1f).apply {
                val marginEnd = if (i < totalSlides - 1) 6.dpToPx() else 0
                setMargins(0, 0, marginEnd, 0)
            }
            seg.layoutParams = lp
            seg.background = createCapsuleDrawable("#CBD5E1")
            storySegmentsContainer.addView(seg)
        }
    }

    private fun setupDots() {
        dotsContainer.removeAllViews()
        for (i in 0 until totalSlides) {
            val dot = View(this)
            val layoutParams = LinearLayout.LayoutParams(6.dpToPx(), 6.dpToPx()).apply {
                setMargins(3.dpToPx(), 0, 3.dpToPx(), 0)
            }
            dot.layoutParams = layoutParams
            dot.background = createCapsuleDrawable("#CBD5E1")
            dotsContainer.addView(dot)
        }
    }

    private fun setupViewPager() {
        viewPager.adapter = IntroSlideAdapter()
        viewPager.offscreenPageLimit = 4
        viewPager.setPageTransformer { page, position ->
            val absPos = abs(position)
            if (position < -1f || position > 1f) {
                page.alpha = 0f
            } else {
                page.alpha = 1f - (0.32f * absPos)
                val scale = ((1f - absPos) * 0.05f) + 0.95f
                page.scaleX = scale
                page.scaleY = scale
            }
        }
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                GabAIUtils.performHaptic(viewPager, HapticFeedbackConstants.CLOCK_TICK)
                updateUiState(position)
                boundHolders.get(position)?.onSlideFocused()
            }
        })
    }

    private fun updateUiState(position: Int) {
        val theme = slideThemes.getOrElse(position) { slideThemes.first() }
        val accentColor = Color.parseColor(theme.accentHex)

        tvStepCounter.text = theme.stepTitle
        tvChapterBadge.text = theme.chapterLabel
        tvChapterBadge.setTextColor(accentColor)
        tvChapterBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor(theme.badgeBgHex))

        btnPrev.visibility = if (position > 0) View.VISIBLE else View.INVISIBLE

        if (position == totalSlides - 1) {
            btnNext.text = "Launch GabAI  →"
            btnNext.setBackgroundResource(R.drawable.bg_tools_library_gradient)
            btnSkip.visibility = View.INVISIBLE
        } else {
            btnNext.text = "Continue  →"
            btnNext.setBackgroundResource(R.drawable.bg_btn_gradient_quest)
            btnSkip.visibility = View.VISIBLE
        }

        // Update Top Story Progress Segments
        for (i in 0 until storySegmentsContainer.childCount) {
            val seg = storySegmentsContainer.getChildAt(i)
            val colorHex = when {
                i < position -> theme.accentHex
                i == position -> theme.accentHex
                else -> "#E2E8F0"
            }
            seg.background = createCapsuleDrawable(colorHex)
            seg.alpha = if (i < position) 0.55f else 1f
        }

        // Update Bottom Dock Indicator Dots
        for (i in 0 until dotsContainer.childCount) {
            val dot = dotsContainer.getChildAt(i)
            val isSelected = i == position
            val targetWidth = (if (isSelected) 22 else 6).dpToPx()
            val anim = ValueAnimator.ofInt(dot.layoutParams.width, targetWidth).apply {
                duration = 240L
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { va ->
                    dot.layoutParams.width = va.animatedValue as Int
                    dot.requestLayout()
                }
            }
            anim.start()
            dot.background = createCapsuleDrawable(if (isSelected) theme.accentHex else "#CBD5E1")
        }
    }

    private fun createCapsuleDrawable(colorHex: String): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 10f.dpToPx().toFloat()
            setColor(Color.parseColor(colorHex))
        }
    }

    private fun setupListeners() {
        GabAIUtils.addSpringPressEffect(btnNext) {
            val currentPos = viewPager.currentItem
            if (currentPos < totalSlides - 1) {
                viewPager.setCurrentItem(currentPos + 1, true)
            } else {
                finishIntroduction()
            }
        }
        GabAIUtils.addSpringPressEffect(btnPrev) {
            val currentPos = viewPager.currentItem
            if (currentPos > 0) {
                viewPager.setCurrentItem(currentPos - 1, true)
            }
        }
        GabAIUtils.addSpringPressEffect(btnSkip) {
            finishIntroduction()
        }
    }

    private fun finishIntroduction() {
        getSharedPreferences("GabAI_Prefs", MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_HAS_SEEN_INTRO, true)
            .apply()
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onDestroy() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    private inner class IntroSlideAdapter : RecyclerView.Adapter<IntroSlideViewHolder>() {
        override fun getItemCount(): Int = totalSlides

        override fun getItemViewType(position: Int): Int = position

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IntroSlideViewHolder {
            val layoutId = when (viewType) {
                0 -> R.layout.item_intro_slide_logo
                1 -> R.layout.item_intro_slide_bubble
                2 -> R.layout.item_intro_slide_scanner
                3 -> R.layout.item_intro_slide_quests
                else -> R.layout.item_intro_slide_library
            }
            val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
            return IntroSlideViewHolder(view, viewType)
        }

        override fun onBindViewHolder(holder: IntroSlideViewHolder, position: Int) {
            boundHolders.put(position, holder)
            holder.bind()
            if (position == viewPager.currentItem) {
                holder.onSlideFocused()
            }
        }
    }

    private inner class IntroSlideViewHolder(
        itemView: View,
        val slideType: Int
    ) : RecyclerView.ViewHolder(itemView) {

        private var activeBubbleWordIndex = 0
        private var isVaultSaved = true
        private var activeOcrWordIndex = 0
        private var isQuestClaimed = false

        fun bind() {
            when (slideType) {
                0 -> bindSlideLogo()
                1 -> bindSlideBubble()
                2 -> bindSlideScanner()
                3 -> bindSlideQuests()
                4 -> bindSlideLibrary()
            }
        }

        fun onSlideFocused() {
            when (slideType) {
                0 -> {
                    val logoView = itemView.findViewById<GabAiAnimatedLogoView>(R.id.intro_animated_logo)
                    logoView?.post { logoView.startChoreography() }
                    staggerCards(
                        itemView.findViewById(R.id.card_s1_feat_1),
                        itemView.findViewById(R.id.card_s1_feat_2),
                        itemView.findViewById(R.id.card_s1_feat_3)
                    )
                }
                1 -> {
                    staggerCards(
                        itemView.findViewById(R.id.card_bubble_passage),
                        itemView.findViewById(R.id.card_bubble_verdict)
                    )
                }
                2 -> {
                    val resultCard = itemView.findViewById<View>(R.id.card_ocr_result_sheet)
                    if (resultCard != null) {
                        resultCard.alpha = 0f
                        resultCard.translationY = 24f.dpToPx().toFloat()
                        resultCard.animate()
                            .alpha(1f)
                            .translationY(0f)
                            .setInterpolator(OvershootInterpolator(0.8f))
                            .setDuration(420L)
                            .start()
                    }
                }
                3 -> {
                    val pb = itemView.findViewById<ProgressBar>(R.id.pb_quest_xp_track)
                    val target = if (isQuestClaimed) 90 else 65
                    if (pb != null) {
                        ObjectAnimator.ofInt(pb, "progress", 0, target).apply {
                            duration = 950L
                            interpolator = AccelerateDecelerateInterpolator()
                            start()
                        }
                    }
                }
                4 -> {
                    staggerCards(itemView.findViewById(R.id.card_intro_quiz))
                }
            }
        }

        private fun staggerCards(vararg views: View?) {
            views.filterNotNull().forEachIndexed { idx, v ->
                v.alpha = 0f
                v.translationY = 16f.dpToPx().toFloat()
                v.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(idx * 90L)
                    .setDuration(340L)
                    .setInterpolator(OvershootInterpolator(0.6f))
                    .start()
            }
        }

        private fun bindSlideLogo() {
            val prefs = getSharedPreferences("GabAI_Prefs", MODE_PRIVATE)
            val firstName = prefs.getString("cached_first_name", "")?.trim().orEmpty()
            val welcomePill = itemView.findViewById<TextView>(R.id.tv_slide1_welcome_pill)
            if (firstName.isNotEmpty()) {
                welcomePill?.text = "WELCOME, ${firstName.uppercase(Locale.US)} • AI COMPANION"
            }

            val logoView = itemView.findViewById<GabAiAnimatedLogoView>(R.id.intro_animated_logo)
            logoView?.setOnClickListener {
                GabAIUtils.performHaptic(it, HapticFeedbackConstants.CLOCK_TICK)
                logoView.startChoreography()
            }

            itemView.findViewById<View>(R.id.badge_orbit_1)?.let {
                startBobbingAnimation(it, "translationY", 0f, -5f.dpToPx().toFloat(), 1400L)
            }
            itemView.findViewById<View>(R.id.badge_orbit_2)?.let {
                startBobbingAnimation(it, "translationY", 0f, 5f.dpToPx().toFloat(), 1650L)
            }
            itemView.findViewById<View>(R.id.badge_orbit_3)?.let {
                startBobbingAnimation(it, "translationY", 0f, -4f.dpToPx().toFloat(), 1500L)
            }
        }

        private fun bindSlideBubble() {
            val bubble = itemView.findViewById<View>(R.id.frame_floating_bubble)
            if (bubble != null) {
                startBobbingAnimation(bubble, "translationY", 0f, -6f.dpToPx().toFloat(), 1100L)
                bubble.setOnClickListener {
                    GabAIUtils.performHaptic(it, HapticFeedbackConstants.CLOCK_TICK)
                    val nextIdx = (activeBubbleWordIndex + 1) % bubbleWords.size
                    selectBubbleWord(nextIdx)
                }
            }

            val chip1 = itemView.findViewById<TextView>(R.id.chip_bubble_word_1)
            val chip2 = itemView.findViewById<TextView>(R.id.chip_bubble_word_2)
            val chip3 = itemView.findViewById<TextView>(R.id.chip_bubble_word_3)

            chip1?.setOnClickListener {
                GabAIUtils.performHaptic(it, HapticFeedbackConstants.CLOCK_TICK)
                selectBubbleWord(0)
            }
            chip2?.setOnClickListener {
                GabAIUtils.performHaptic(it, HapticFeedbackConstants.CLOCK_TICK)
                selectBubbleWord(1)
            }
            chip3?.setOnClickListener {
                GabAIUtils.performHaptic(it, HapticFeedbackConstants.CLOCK_TICK)
                selectBubbleWord(2)
            }

            val btnListen = itemView.findViewById<View>(R.id.btn_bubble_listen)
            val tvListenLabel = itemView.findViewById<TextView>(R.id.tv_bubble_listen_label)
            btnListen?.let {
                GabAIUtils.addSpringPressEffect(it) {
                    GabAIUtils.performHaptic(it, HapticFeedbackConstants.CLOCK_TICK)
                    speakWord(bubbleWords[activeBubbleWordIndex].word, tvListenLabel, "Listen")
                }
            }

            val btnStar = itemView.findViewById<View>(R.id.btn_bubble_star)
            val tvStarLabel = itemView.findViewById<TextView>(R.id.tv_bubble_star_label)
            val ivStarIcon = itemView.findViewById<ImageView>(R.id.iv_bubble_star_icon)
            btnStar?.let {
                GabAIUtils.addSpringPressEffect(it) {
                    GabAIUtils.performHaptic(it, HapticFeedbackConstants.CLOCK_TICK)
                    isVaultSaved = !isVaultSaved
                    if (isVaultSaved) {
                        btnStar.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF3C7"))
                        tvStarLabel?.text = "Saved to Vault"
                        tvStarLabel?.setTextColor(Color.parseColor("#B45309"))
                        ivStarIcon?.let { icon ->
                            ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(Color.parseColor("#D97706")))
                        }
                    } else {
                        btnStar.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F1F5F9"))
                        tvStarLabel?.text = "Save to Vault"
                        tvStarLabel?.setTextColor(Color.parseColor("#64748B"))
                        ivStarIcon?.let { icon ->
                            ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(Color.parseColor("#94A3B8")))
                        }
                    }
                }
            }
        }

        private fun selectBubbleWord(index: Int) {
            activeBubbleWordIndex = index
            val data = bubbleWords[index]
            val chips = listOf(
                itemView.findViewById<TextView>(R.id.chip_bubble_word_1),
                itemView.findViewById<TextView>(R.id.chip_bubble_word_2),
                itemView.findViewById<TextView>(R.id.chip_bubble_word_3)
            )
            chips.forEachIndexed { idx, chip ->
                if (chip != null) {
                    val isSelected = idx == index
                    chip.backgroundTintList = ColorStateList.valueOf(
                        Color.parseColor(if (isSelected) "#5341CD" else "#F1F5F9")
                    )
                    chip.setTextColor(Color.parseColor(if (isSelected) "#FFFFFF" else "#475569"))
                }
            }

            val body = itemView.findViewById<View>(R.id.container_bubble_verdict_body)
            body?.animate()?.alpha(0.3f)?.setDuration(90L)?.withEndAction {
                itemView.findViewById<TextView>(R.id.tv_bubble_active_word)?.text = data.word
                itemView.findViewById<TextView>(R.id.tv_bubble_active_pos)?.text = data.pos
                itemView.findViewById<TextView>(R.id.tv_bubble_active_phonetic)?.text = data.phonetic
                itemView.findViewById<TextView>(R.id.tv_bubble_active_def)?.text = data.definition
                itemView.findViewById<TextView>(R.id.tv_bubble_active_role)?.text = data.contextRole
                body.animate().alpha(1f).setDuration(160L).start()
            }?.start()
        }

        private fun bindSlideScanner() {
            val laser = itemView.findViewById<View>(R.id.view_scanner_laser)
            if (laser != null) {
                ObjectAnimator.ofFloat(laser, "translationY", 0f, 72f.dpToPx().toFloat()).apply {
                    duration = 1800L
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    interpolator = AccelerateDecelerateInterpolator()
                    start()
                }
            }

            val chip1 = itemView.findViewById<TextView>(R.id.tv_scanned_highlighted_word)
            val chip2 = itemView.findViewById<TextView>(R.id.chip_ocr_word_2)
            val chip3 = itemView.findViewById<TextView>(R.id.chip_ocr_word_3)

            chip1?.setOnClickListener {
                GabAIUtils.performHaptic(it, HapticFeedbackConstants.CLOCK_TICK)
                selectOcrWord(0)
            }
            chip2?.setOnClickListener {
                GabAIUtils.performHaptic(it, HapticFeedbackConstants.CLOCK_TICK)
                selectOcrWord(1)
            }
            chip3?.setOnClickListener {
                GabAIUtils.performHaptic(it, HapticFeedbackConstants.CLOCK_TICK)
                selectOcrWord(2)
            }

            val btnOcrListen = itemView.findViewById<View>(R.id.btn_ocr_listen)
            val tvOcrListenLabel = itemView.findViewById<TextView>(R.id.tv_ocr_listen_label)
            btnOcrListen?.let {
                GabAIUtils.addSpringPressEffect(it) {
                    GabAIUtils.performHaptic(it, HapticFeedbackConstants.CLOCK_TICK)
                    speakWord(ocrWords[activeOcrWordIndex].word, tvOcrListenLabel, "Pronounce")
                }
            }
        }

        private fun selectOcrWord(index: Int) {
            activeOcrWordIndex = index
            val data = ocrWords[index]
            val chips = listOf(
                itemView.findViewById<TextView>(R.id.tv_scanned_highlighted_word),
                itemView.findViewById<TextView>(R.id.chip_ocr_word_2),
                itemView.findViewById<TextView>(R.id.chip_ocr_word_3)
            )
            chips.forEachIndexed { idx, chip ->
                if (chip != null) {
                    val isSelected = idx == index
                    chip.backgroundTintList = ColorStateList.valueOf(
                        Color.parseColor(if (isSelected) "#FEF08A" else "#0F172A")
                    )
                    chip.setTextColor(Color.parseColor(if (isSelected) "#0F172A" else "#A5B4FC"))
                }
            }

            val body = itemView.findViewById<View>(R.id.container_ocr_result_body)
            body?.animate()?.alpha(0.3f)?.setDuration(90L)?.withEndAction {
                itemView.findViewById<TextView>(R.id.tv_ocr_active_word)?.text = data.word
                itemView.findViewById<TextView>(R.id.tv_ocr_active_phonetic)?.text = data.phonetic
                itemView.findViewById<TextView>(R.id.tv_ocr_active_def)?.text = data.definition
                body.animate().alpha(1f).setDuration(160L).start()
            }?.start()
        }

        private fun bindSlideQuests() {
            val rowInteractive = itemView.findViewById<View>(R.id.row_interactive_quest)
            val ivIcon = itemView.findViewById<ImageView>(R.id.iv_interactive_quest_icon)
            val tvTitle = itemView.findViewById<TextView>(R.id.tv_interactive_quest_title)
            val tvAction = itemView.findViewById<TextView>(R.id.tv_interactive_quest_action)
            val tvQuestBadge = itemView.findViewById<TextView>(R.id.tv_intro_quest_badge)
            val tvXpCounter = itemView.findViewById<TextView>(R.id.tv_intro_xp_counter)
            val pbXp = itemView.findViewById<ProgressBar>(R.id.pb_quest_xp_track)

            rowInteractive?.let { row ->
                GabAIUtils.addSpringPressEffect(row) {
                    GabAIUtils.performHaptic(row, HapticFeedbackConstants.CONFIRM)
                    isQuestClaimed = !isQuestClaimed
                    if (isQuestClaimed) {
                        row.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#ECFDF5"))
                        ivIcon?.setImageResource(R.drawable.ic_verified_teal)
                        ivIcon?.let { ImageViewCompat.setImageTintList(it, null) }
                        tvTitle?.setTextColor(Color.parseColor("#065F46"))
                        tvAction?.text = "Claimed +25 XP ✓"
                        tvAction?.setTextColor(Color.parseColor("#059669"))
                        tvAction?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#D1FAE5"))
                        tvQuestBadge?.text = "4 of 8 Done"
                        tvQuestBadge?.setTextColor(Color.parseColor("#059669"))
                        tvQuestBadge?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#ECFDF5"))
                        tvXpCounter?.text = "90 / 100 XP"
                        if (pbXp != null) {
                            ObjectAnimator.ofInt(pbXp, "progress", pbXp.progress, 90).apply {
                                duration = 450L
                                interpolator = AccelerateDecelerateInterpolator()
                                start()
                            }
                        }
                    } else {
                        row.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFFBEB"))
                        ivIcon?.setImageResource(R.drawable.ic_star_filled)
                        ivIcon?.let {
                            ImageViewCompat.setImageTintList(it, ColorStateList.valueOf(Color.parseColor("#D97706")))
                        }
                        tvTitle?.setTextColor(Color.parseColor("#92400E"))
                        tvAction?.text = "Tap to Claim +25 XP"
                        tvAction?.setTextColor(Color.parseColor("#92400E"))
                        tvAction?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FDE68A"))
                        tvQuestBadge?.text = "3 of 8 Done"
                        tvQuestBadge?.setTextColor(Color.parseColor("#B45309"))
                        tvQuestBadge?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF3C7"))
                        tvXpCounter?.text = "65 / 100 XP"
                        if (pbXp != null) {
                            ObjectAnimator.ofInt(pbXp, "progress", pbXp.progress, 65).apply {
                                duration = 450L
                                interpolator = AccelerateDecelerateInterpolator()
                                start()
                            }
                        }
                    }
                }
            }
        }

        private fun bindSlideLibrary() {
            val optA = itemView.findViewById<View>(R.id.btn_intro_quiz_opt_a)
            val optB = itemView.findViewById<View>(R.id.btn_intro_quiz_opt_b)
            val badgeA = itemView.findViewById<TextView>(R.id.tv_quiz_badge_a)
            val badgeB = itemView.findViewById<TextView>(R.id.tv_quiz_badge_b)
            val textA = itemView.findViewById<TextView>(R.id.tv_quiz_text_a)
            val textB = itemView.findViewById<TextView>(R.id.tv_quiz_text_b)
            val xpA = itemView.findViewById<View>(R.id.tv_quiz_xp_a)
            val status = itemView.findViewById<TextView>(R.id.tv_intro_quiz_status)

            optA?.let {
                GabAIUtils.addSpringPressEffect(it) {
                    GabAIUtils.performHaptic(it, HapticFeedbackConstants.CONFIRM)
                    optA.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#ECFDF5"))
                    badgeA?.text = "✓"
                    badgeA?.setTextColor(Color.WHITE)
                    badgeA?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#059669"))
                    textA?.setTextColor(Color.parseColor("#065F46"))
                    xpA?.visibility = View.VISIBLE

                    optB?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F8FAFC"))
                    badgeB?.text = "B"
                    badgeB?.setTextColor(Color.parseColor("#475569"))
                    badgeB?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E2E8F0"))
                    textB?.setTextColor(Color.parseColor("#64748B"))

                    status?.text = "Correct! +50 XP Earned"
                    status?.setTextColor(Color.parseColor("#059669"))
                }
            }

            optB?.let {
                GabAIUtils.addSpringPressEffect(it) {
                    GabAIUtils.performHaptic(it, HapticFeedbackConstants.CLOCK_TICK)
                    optB.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF3C7"))
                    badgeB?.text = "!"
                    badgeB?.setTextColor(Color.WHITE)
                    badgeB?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#D97706"))
                    textB?.setTextColor(Color.parseColor("#92400E"))

                    status?.text = "Not quite — try Option A!"
                    status?.setTextColor(Color.parseColor("#D97706"))
                }
            }
        }

        private fun startBobbingAnimation(
            view: View,
            property: String,
            from: Float,
            to: Float,
            durationMs: Long
        ) {
            ObjectAnimator.ofFloat(view, property, from, to).apply {
                duration = durationMs
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
    private fun Float.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
