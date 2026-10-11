package com.example.gabai

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class HelpActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_USER_ROLE = "extra_user_role"
    }

    data class HelpGuide(
        val questKey: String,
        val title: String,
        val description: String,
        @DrawableRes val iconRes: Int,
        val iconTint: Int,
        val iconBgTint: Int
    )

    private val studentGuides = listOf(
        HelpGuide(
            ApprenticeQuestHelper.QUEST_BUBBLE,
            "Activate Floating Bubble",
            "Turn on the screen companion to read and translate words in any app",
            R.drawable.ic_floating_gabai_lens,
            Color.parseColor("#5341CD"),
            Color.parseColor("#EDE9FE")
        ),
        HelpGuide(
            ApprenticeQuestHelper.QUEST_BUBBLE_SCAN,
            "Scan with Floating Bubble",
            "Tap any word on your screen or in PDFs for instant definitions",
            R.drawable.ic_center_focus,
            Color.parseColor("#2563EB"),
            Color.parseColor("#DBEAFE")
        ),
        HelpGuide(
            ApprenticeQuestHelper.QUEST_SCAN,
            "Camera Scanner",
            "Point your camera at printed book pages and worksheets",
            R.drawable.ic_nav_scanner,
            Color.parseColor("#059669"),
            Color.parseColor("#D1FAE5")
        ),
        HelpGuide(
            ApprenticeQuestHelper.QUEST_TEST,
            "Reading Trials & Quizzes",
            "Complete reading passages and test your comprehension to earn XP",
            R.drawable.ic_military_tech,
            Color.parseColor("#D97706"),
            Color.parseColor("#FEF3C7")
        ),
        HelpGuide(
            ApprenticeQuestHelper.QUEST_SAVE,
            "Save Words to Favorites",
            "Star difficult words while reading to practice them later",
            R.drawable.ic_star_filled,
            Color.parseColor("#F59E0B"),
            Color.parseColor("#FEF3C7")
        ),
        HelpGuide(
            ApprenticeQuestHelper.QUEST_HISTORY,
            "Scan & Lookup History",
            "Review past words you scanned or looked up recently",
            R.drawable.ic_clock_outline,
            Color.parseColor("#64748B"),
            Color.parseColor("#F1F5F9")
        ),
        HelpGuide(
            ApprenticeQuestHelper.QUEST_DETAIL,
            "Favorites Collection",
            "Review and practice all your starred vocabulary words",
            R.drawable.ic_favorite,
            Color.parseColor("#E11D48"),
            Color.parseColor("#FFE4E6")
        ),
        HelpGuide(
            ApprenticeQuestHelper.QUEST_LIBRARY,
            "Digital Library",
            "Open reading materials and study PDFs assigned to you",
            R.drawable.ic_menu_book,
            Color.parseColor("#7C3AED"),
            Color.parseColor("#EDE9FE")
        )
    )

    private val teacherGuides = listOf(
        HelpGuide(
            "teacher_qr",
            "Class Join Code & QR",
            "Display your class QR code for quick student enrollment",
            R.drawable.ic_center_focus,
            Color.parseColor("#5341CD"),
            Color.parseColor("#EDE9FE")
        ),
        HelpGuide(
            "teacher_classes",
            "Class Sections & Rosters",
            "Manage class rosters, sections, and student memberships",
            R.drawable.ic_school,
            Color.parseColor("#2563EB"),
            Color.parseColor("#DBEAFE")
        ),
        HelpGuide(
            "teacher_library",
            "Learning Materials",
            "Upload reading PDFs and worksheets for your students",
            R.drawable.ic_menu_book,
            Color.parseColor("#059669"),
            Color.parseColor("#D1FAE5")
        ),
        HelpGuide(
            "teacher_quiz",
            "Comprehension Quizzes",
            "Generate AI assessments to evaluate student understanding",
            R.drawable.ic_psychology,
            Color.parseColor("#D97706"),
            Color.parseColor("#FEF3C7")
        ),
        HelpGuide(
            "teacher_analytics",
            "Learner Analytics",
            "Track student reading accuracy and identify difficult words",
            R.drawable.ic_insights,
            Color.parseColor("#7C3AED"),
            Color.parseColor("#EDE9FE")
        )
    )

    private lateinit var tabStudents: MaterialCardView
    private lateinit var tabTeachers: MaterialCardView
    private lateinit var tvTabStudents: TextView
    private lateinit var tvTabTeachers: TextView
    private lateinit var tvSectionTitle: TextView
    private lateinit var rvHelpItems: RecyclerView
    private lateinit var cardReplayStudentIntro: MaterialCardView
    private lateinit var adapter: HelpGuidesAdapter
    private var isTeacherSelected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GabAIUtils.applyHardwareMaxRefreshRate(this)
        setContentView(R.layout.activity_help)

        val blurHeader = findViewById<FastBlurView>(R.id.blur_header_help)
        val blurTarget = findViewById<FastBlurTarget>(R.id.blur_target_help)
        GabAIUtils.setupBlurView(blurHeader, blurTarget)

        val header = findViewById<View>(R.id.help_header)
        ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 12, v.paddingRight, v.paddingBottom)
            insets
        }

        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        btnBack.setOnClickListener {
            GabAIUtils.performHaptic(it, HapticFeedbackConstants.CLOCK_TICK)
            finish()
        }

        tabStudents = findViewById(R.id.tab_students)
        tabTeachers = findViewById(R.id.tab_teachers)
        tvTabStudents = findViewById(R.id.tv_tab_students)
        tvTabTeachers = findViewById(R.id.tv_tab_teachers)
        tvSectionTitle = findViewById(R.id.tv_section_title)
        rvHelpItems = findViewById(R.id.rv_help_items)
        cardReplayStudentIntro = findViewById(R.id.card_replay_student_intro)

        GabAIUtils.addSpringPressEffect(cardReplayStudentIntro) {
            GabAIUtils.performHaptic(cardReplayStudentIntro, HapticFeedbackConstants.CLOCK_TICK)
            val intent = Intent(this, StudentIntroActivity::class.java).apply {
                putExtra(StudentIntroActivity.EXTRA_REPLAY, true)
            }
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        rvHelpItems.layoutManager = LinearLayoutManager(this)
        adapter = HelpGuidesAdapter { guide ->
            GabAIDialogs.showQuestNavigationDialog(this, guide.questKey)
        }
        rvHelpItems.adapter = adapter

        val cardTabsContainer = findViewById<MaterialCardView>(R.id.card_tabs_container)
        val roleExtra = intent.getStringExtra(EXTRA_USER_ROLE)
        val cachedRole = getSharedPreferences("GabAI_Prefs", MODE_PRIVATE)
            .getString("cached_user_role", "student") ?: "student"
        val effectiveRole = roleExtra ?: cachedRole
        val isUserTeacher = effectiveRole.equals("teacher", ignoreCase = true)

        if (isUserTeacher) {
            cardTabsContainer.visibility = View.VISIBLE
            isTeacherSelected = true
            updateRoleSelection(true)
        } else {
            cardTabsContainer.visibility = View.GONE
            isTeacherSelected = false
            updateRoleSelection(false)
        }

        tabStudents.setOnClickListener {
            GabAIUtils.performHaptic(it, HapticFeedbackConstants.CLOCK_TICK)
            if (isTeacherSelected) {
                isTeacherSelected = false
                updateRoleSelection(false)
            }
        }

        tabTeachers.setOnClickListener {
            if (!isUserTeacher) return@setOnClickListener
            GabAIUtils.performHaptic(it, HapticFeedbackConstants.CLOCK_TICK)
            if (!isTeacherSelected) {
                isTeacherSelected = true
                updateRoleSelection(true)
            }
        }
    }

    private fun updateRoleSelection(isTeacher: Boolean) {
        val activeBg = Color.parseColor("#FFFFFF")
        val activeTextColor = Color.parseColor("#5341CD")
        val inactiveTextColor = Color.parseColor("#64748B")

        if (isTeacher) {
            tabStudents.setCardBackgroundColor(Color.TRANSPARENT)
            tvTabStudents.setTextColor(inactiveTextColor)
            tabTeachers.setCardBackgroundColor(activeBg)
            tvTabTeachers.setTextColor(activeTextColor)
            cardReplayStudentIntro.visibility = View.GONE
            tvSectionTitle.text = "TEACHER GUIDES"
            adapter.submitList(teacherGuides)
        } else {
            tabStudents.setCardBackgroundColor(activeBg)
            tvTabStudents.setTextColor(activeTextColor)
            tabTeachers.setCardBackgroundColor(Color.TRANSPARENT)
            tvTabTeachers.setTextColor(inactiveTextColor)
            cardReplayStudentIntro.visibility = View.VISIBLE
            tvSectionTitle.text = "STUDENT GUIDES"
            adapter.submitList(studentGuides)
        }

        rvHelpItems.post {
            val childViews = (0 until rvHelpItems.childCount).mapNotNull { rvHelpItems.getChildAt(it) }
            if (childViews.isNotEmpty()) {
                GabAIUtils.animateCascade(childViews, baseDelay = 25L)
            }
        }
    }

    private class HelpGuidesAdapter(
        private val onItemClick: (HelpGuide) -> Unit
    ) : RecyclerView.Adapter<HelpGuidesAdapter.GuideViewHolder>() {

        private val items = mutableListOf<HelpGuide>()

        fun submitList(newItems: List<HelpGuide>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GuideViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_help_guide, parent, false)
            return GuideViewHolder(view)
        }

        override fun onBindViewHolder(holder: GuideViewHolder, position: Int) {
            holder.bind(items[position], onItemClick)
        }

        override fun getItemCount(): Int = items.size

        class GuideViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val cardItem: MaterialCardView = itemView.findViewById(R.id.card_guide_item)
            private val cardIconContainer: MaterialCardView = itemView.findViewById(R.id.card_icon_container)
            private val ivIcon: ImageView = itemView.findViewById(R.id.iv_guide_icon)
            private val tvTitle: TextView = itemView.findViewById(R.id.tv_guide_title)
            private val tvDesc: TextView = itemView.findViewById(R.id.tv_guide_desc)

            fun bind(guide: HelpGuide, onClick: (HelpGuide) -> Unit) {
                tvTitle.text = guide.title
                tvDesc.text = guide.description
                ivIcon.setImageResource(guide.iconRes)
                ivIcon.setColorFilter(guide.iconTint)
                cardIconContainer.setCardBackgroundColor(guide.iconBgTint)
                GabAIUtils.addSpringPressEffect(cardItem) {
                    GabAIUtils.performHaptic(cardItem, HapticFeedbackConstants.CLOCK_TICK)
                    onClick(guide)
                }
            }
        }
    }
}
