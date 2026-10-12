package com.example.gabai

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore

class StudentProfileBottomSheet : BottomSheetDialogFragment() {

    private val db = FirebaseFirestore.getInstance()

    companion object {
        private const val ARG_STUDENT_ID = "student_id"
        private const val ARG_STUDENT_NAME = "student_name"
        private const val ARG_GRADE = "grade"
        private const val ARG_SECTION = "section"
        private const val ARG_LEVEL = "level"
        private const val ARG_XP = "xp"
        private const val ARG_RANK = "rank"

        fun newInstance(
            studentId: String,
            studentName: String,
            grade: String,
            section: String,
            level: Int,
            xp: Int = 0,
            rank: Int = -1
        ): StudentProfileBottomSheet {
            val sheet = StudentProfileBottomSheet()
            val args = Bundle().apply {
                putString(ARG_STUDENT_ID, studentId)
                putString(ARG_STUDENT_NAME, studentName)
                putString(ARG_GRADE, grade)
                putString(ARG_SECTION, section)
                putInt(ARG_LEVEL, level)
                putInt(ARG_XP, xp)
                putInt(ARG_RANK, rank)
            }
            sheet.arguments = args
            return sheet
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_student_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val studentId = arguments?.getString(ARG_STUDENT_ID) ?: return
        val studentName = arguments?.getString(ARG_STUDENT_NAME) ?: "Student"
        val grade = arguments?.getString(ARG_GRADE) ?: ""
        val section = arguments?.getString(ARG_SECTION) ?: ""
        val level = arguments?.getInt(ARG_LEVEL, 1) ?: 1
        val xp = arguments?.getInt(ARG_XP, 0) ?: 0
        val rank = arguments?.getInt(ARG_RANK, -1) ?: -1

        val tvName = view.findViewById<TextView>(R.id.tv_profile_full_name)
        val tvClassBadge = view.findViewById<TextView>(R.id.tv_profile_class_badge)
        val tvRankPill = view.findViewById<TextView>(R.id.tv_profile_rank_pill)
        val tvStatXp = view.findViewById<TextView>(R.id.tv_stat_xp)
        val tvStatStreak = view.findViewById<TextView>(R.id.tv_stat_streak)
        val tvStatLevel = view.findViewById<TextView>(R.id.tv_stat_level)
        val tvMaxCombo = view.findViewById<TextView>(R.id.tv_profile_max_combo)
        val tvComboBadge = view.findViewById<TextView>(R.id.tv_profile_combo_streak_badge)
        val layoutTrophies = view.findViewById<LinearLayout>(R.id.layout_trophy_showcase)
        val btnClose = view.findViewById<MaterialButton>(R.id.btn_close_sheet)
        val tvMonogram = view.findViewById<TextView>(R.id.tv_profile_monogram)
        val ivPhoto = view.findViewById<ImageView>(R.id.iv_profile_photo)

        tvName.text = studentName
        val initial = studentName.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "👤"
        tvMonogram.text = initial

        val classLabel = if (grade.isNotBlank() && section.isNotBlank()) {
            "$grade - $section"
        } else if (grade.isNotBlank()) {
            grade
        } else {
            "Student Scholar"
        }
        tvClassBadge.text = classLabel

        if (rank > 0) {
            val rankText = when (rank) {
                1 -> "🥇 #1 Scholar"
                2 -> "🥈 #2 Scholar"
                3 -> "🥉 #3 Scholar"
                else -> "#$rank Rank"
            }
            tvRankPill.text = rankText
            tvRankPill.visibility = View.VISIBLE
        } else {
            tvRankPill.visibility = View.GONE
        }

        tvStatXp.text = "$xp XP"
        tvStatLevel.text = "Lvl $level"
        tvStatStreak.text = "🔥 1 Day"

        btnClose.setOnClickListener { dismiss() }

        val tvMasteryPill = view.findViewById<TextView>(R.id.tv_profile_mastery_pill)
        val tvWordsMastered = view.findViewById<TextView>(R.id.tv_stat_words_mastered)
        val tvAccuracy = view.findViewById<TextView>(R.id.tv_stat_accuracy)

        val btnShare = view.findViewById<MaterialButton>(R.id.btn_share_profile)
        btnShare?.setOnClickListener {
            val slip = buildString {
                appendLine("🌟 GabAI Scholar Reading Slip")
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                appendLine("Learner: $studentName")
                appendLine("Class: $classLabel")
                appendLine("Reading Mastery: ${tvMasteryPill?.text ?: "In Progress"}")
                appendLine("Quiz Accuracy: ${tvAccuracy?.text ?: "--"}")
                appendLine("Words Mastered: ${tvWordsMastered?.text ?: "0 Words"}")
                appendLine("Reading Streak: ${tvStatStreak.text}")
                appendLine("Level & XP: Lvl $level ($xp XP)")
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                appendLine("Verified by Advisory Teacher via GabAI Educational Platform")
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "GabAI Reading Slip - $studentName")
                putExtra(Intent.EXTRA_TEXT, slip)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Scholar Progress Slip"))
        }

        db.collection("users").document(studentId).get().addOnSuccessListener { doc ->
            if (!isAdded || doc == null || !doc.exists()) return@addOnSuccessListener
            val liveXp = doc.getLong("current_xp")?.toInt() ?: xp
            val liveLevel = doc.getLong("level")?.toInt() ?: level
            val streak = (doc.getLong("current_streak") ?: doc.getLong("streak"))?.toInt() ?: 1
            val photoUrl = doc.getString("photoUrl")

            tvStatXp.text = "$liveXp XP"
            tvStatLevel.text = "Lvl $liveLevel"
            tvStatStreak.text = if (streak == 1) "🔥 1 Day" else "🔥 $streak Days"
            GabAIUtils.loadAvatarImage(photoUrl, ivPhoto, tvMonogram)
        }

        // Fetch words mastered from OCR history (interval >= 4)
        db.collection("users").document(studentId)
            .collection("history")
            .whereGreaterThanOrEqualTo("interval", 4)
            .get()
            .addOnSuccessListener { masteredDocs ->
                if (!isAdded) return@addOnSuccessListener
                val count = masteredDocs.size()
                tvWordsMastered?.text = "$count Words"
            }

        // Fetch student's peak combo streak and accuracy from quiz history
        db.collection("users").document(studentId)
            .collection("quiz_history")
            .get()
            .addOnSuccessListener { quizSnaps ->
                if (!isAdded) return@addOnSuccessListener
                var peakCombo = 0
                var totalScore = 0
                var totalAttempts = 0

                for (qDoc in quizSnaps.documents) {
                    val s = qDoc.getLong("finalScore")?.toInt() ?: 0
                    val a = qDoc.getLong("totalAttempts")?.toInt() ?: 0
                    totalScore += s
                    totalAttempts += a

                    val docMax = qDoc.getLong("maxComboStreak")?.toInt() ?: 0
                    if (docMax > peakCombo) peakCombo = docMax

                    @Suppress("UNCHECKED_CAST")
                    val items = qDoc.get("items") as? List<Map<String, Any>>
                    if (items != null) {
                        var run = 0
                        for (item in items) {
                            if (item["isCorrect"] == true) {
                                run++
                                if (run > peakCombo) peakCombo = run
                            } else {
                                run = 0
                            }
                        }
                    }
                }

                val accuracyPct = if (totalAttempts > 0) ((totalScore.toDouble() / totalAttempts) * 100).toInt() else 0
                tvAccuracy?.text = "$accuracyPct%"

                if (totalAttempts == 0) {
                    tvMasteryPill?.text = "🌱 New Scholar"
                    tvMasteryPill?.setTextColor(Color.parseColor("#64748B"))
                    tvMasteryPill?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F1F5F9"))
                } else if (accuracyPct >= 80) {
                    tvMasteryPill?.text = "🟢 Mastery ($accuracyPct%)"
                    tvMasteryPill?.setTextColor(Color.parseColor("#047857"))
                    tvMasteryPill?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#D1FAE5"))
                } else if (accuracyPct >= 60) {
                    tvMasteryPill?.text = "🟡 Proficient ($accuracyPct%)"
                    tvMasteryPill?.setTextColor(Color.parseColor("#D97706"))
                    tvMasteryPill?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF3C7"))
                } else {
                    tvMasteryPill?.text = "🔴 Needs Support ($accuracyPct%)"
                    tvMasteryPill?.setTextColor(Color.parseColor("#BE123C"))
                    tvMasteryPill?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFE4E6"))
                }

                if (peakCombo > 0) {
                    tvMaxCombo.text = "${peakCombo}x Combo"
                    if (peakCombo >= 5) {
                        tvComboBadge.text = "👑 Combo Monarch"
                        tvComboBadge.setTextColor(Color.parseColor("#047857"))
                        tvComboBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#D1FAE5"))
                    } else {
                        tvComboBadge.text = "🔥 Peak Streak"
                        tvComboBadge.setTextColor(Color.parseColor("#D97706"))
                        tvComboBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF3C7"))
                    }
                } else {
                    tvMaxCombo.text = "0x Combo"
                    tvComboBadge.text = "🌱 Getting Started"
                    tvComboBadge.setTextColor(Color.parseColor("#64748B"))
                    tvComboBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F1F5F9"))
                }
            }

        loadAchievementsShowcase(layoutTrophies, studentId)
    }

    private fun loadAchievementsShowcase(container: LinearLayout, studentId: String) {
        val density = resources.displayMetrics.density
        db.collection("users").document(studentId)
            .collection("achievements")
            .limit(5)
            .get()
            .addOnSuccessListener { snaps ->
                if (!isAdded) return@addOnSuccessListener
                container.removeAllViews()
                val unlockedIds = snaps.documents.map { it.id }.toSet()
                val displayBadges = listOf(
                    Triple("Combo Monarch", R.drawable.ic_military_tech, AchievementManager.ACH_COMBO_MONARCH),
                    Triple("Eagle Eye", R.drawable.ic_center_focus, AchievementManager.ACH_EAGLE_EYE),
                    Triple("Glitch Buster", R.drawable.ic_task_alt, AchievementManager.ACH_GLITCH_BUSTER),
                    Triple("Lightning Reflexes", R.drawable.ic_flash_on, AchievementManager.ACH_LIGHTNING_REFLEXES),
                    Triple("Vocabulary Vault", R.drawable.ic_menu_book, AchievementManager.ACH_VOCAB_VAULT)
                )
                for ((title, iconRes, achId) in displayBadges) {
                    val isUnlocked = unlockedIds.contains(achId)
                    val badgeFrame = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        setPadding((6f * density).toInt(), 0, (6f * density).toInt(), 0)
                        alpha = if (isUnlocked) 1f else 0.4f
                    }
                    val iconView = ImageView(requireContext()).apply {
                        setImageResource(iconRes)
                        layoutParams = LinearLayout.LayoutParams((32f * density).toInt(), (32f * density).toInt())
                        setColorFilter(Color.parseColor(if (isUnlocked) "#5341CD" else "#94A3B8"))
                        setBackgroundResource(if (isUnlocked) R.drawable.bg_bento_purple else R.drawable.bg_pill_translucent)
                        val pad = (6f * density).toInt()
                        setPadding(pad, pad, pad, pad)
                    }
                    val labelView = TextView(requireContext()).apply {
                        text = title
                        textSize = 9.5f
                        setTextColor(Color.parseColor(if (isUnlocked) "#0F172A" else "#94A3B8"))
                        textAlignment = View.TEXT_ALIGNMENT_CENTER
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = (4f * density).toInt()
                        }
                    }
                    badgeFrame.addView(iconView)
                    badgeFrame.addView(labelView)
                    container.addView(badgeFrame)
                }
            }
    }
}
