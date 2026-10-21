package com.example.gabai

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.graphics.Typeface
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ProgressBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.google.android.material.bottomsheet.BottomSheetBehavior
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
        private const val ARG_IS_TEACHER_VIEW = "is_teacher_view"

        fun newInstance(
            studentId: String,
            studentName: String,
            grade: String,
            section: String,
            level: Int,
            xp: Int = 0,
            rank: Int = -1,
            isTeacherView: Boolean = false
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
                putBoolean(ARG_IS_TEACHER_VIEW, isTeacherView)
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

    override fun onStart() {
        super.onStart()
        dialog?.let { d ->
            val bottomSheet = d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                sheet.background = null
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.skipCollapsed = true
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
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
        val isTeacherViewArg = arguments?.getBoolean(ARG_IS_TEACHER_VIEW, false) ?: false

        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val prefs = requireContext().getSharedPreferences("GabAI_Prefs", android.content.Context.MODE_PRIVATE)
        val cachedRole = prefs.getString("cached_user_role", null) ?: prefs.getString("user_role", null)

        val isActuallyTeacher = (cachedRole == "teacher")
        val isTeacherView = isTeacherViewArg && isActuallyTeacher
        val isSelf = (currentUid.isNotBlank() && currentUid == studentId)
        val canViewAcademicDetails = isTeacherView || isSelf

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

        btnClose.setOnClickListener {
            GabAIUtils.performHaptic(it)
            dismiss()
        }
        GabAIUtils.addSpringPressEffect(btnClose)

        val tvMasteryPill = view.findViewById<TextView>(R.id.tv_profile_mastery_pill)
        val tvPhilIriPill = view.findViewById<TextView>(R.id.tv_profile_phil_iri_pill)
        val tvWordsMastered = view.findViewById<TextView>(R.id.tv_stat_words_mastered)
        val tvAccuracy = view.findViewById<TextView>(R.id.tv_stat_accuracy)
        var cachedPhilIriTier: String? = null

        val btnShare = view.findViewById<MaterialButton>(R.id.btn_share_profile)
        btnShare?.visibility = if (canViewAcademicDetails) View.VISIBLE else View.GONE
        btnShare?.let { GabAIUtils.addSpringPressEffect(it) }
        btnShare?.setOnClickListener {
            GabAIUtils.performHaptic(it)
            val slip = buildString {
                appendLine("🌟 GabAI Scholar Reading Slip")
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                appendLine("Learner: $studentName")
                appendLine("Class: $classLabel")
                appendLine("Reading Mastery: ${tvMasteryPill?.text ?: "In Progress"}")
                if (!cachedPhilIriTier.isNullOrEmpty()) {
                    appendLine("Phil-IRI Diagnostic: $cachedPhilIriTier")
                }
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
            val philIriTier = doc.getString("phil_iri_tier") ?: doc.getString("diagnostic_tier")
            cachedPhilIriTier = philIriTier

            if (isTeacherView && !philIriTier.isNullOrEmpty()) {
                tvPhilIriPill?.visibility = View.VISIBLE
                tvPhilIriPill?.text = "Phil-IRI: $philIriTier"
                val (pBg, pTxt) = when (philIriTier.lowercase()) {
                    "independent" -> Pair("#E0E7FF", "#4338CA")
                    "instructional" -> Pair("#FEF3C7", "#D97706")
                    else -> Pair("#FEE2E2", "#DC2626")
                }
                tvPhilIriPill?.backgroundTintList = ColorStateList.valueOf(Color.parseColor(pBg))
                tvPhilIriPill?.setTextColor(Color.parseColor(pTxt))
            } else {
                tvPhilIriPill?.visibility = View.GONE
            }

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

                if (canViewAcademicDetails) {
                    tvMasteryPill?.visibility = View.VISIBLE
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
                } else {
                    tvMasteryPill?.visibility = View.GONE
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

        val layoutQuizzes = view.findViewById<View>(R.id.layout_student_quizzes_section)
        if (canViewAcademicDetails) {
            layoutQuizzes?.visibility = View.VISIBLE
            loadStudentQuizHistory(view, studentId)
        } else {
            layoutQuizzes?.visibility = View.GONE
        }
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

    private fun loadStudentQuizHistory(view: View, studentId: String) {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val prefs = context?.getSharedPreferences("GabAI_Prefs", android.content.Context.MODE_PRIVATE)
        val role = prefs?.getString("cached_user_role", null) ?: prefs?.getString("user_role", null)

        // Strict security block: Only educator or the student themselves can ever load quiz history
        if (role != "teacher" && currentUid != studentId) {
            view.findViewById<View>(R.id.layout_student_quizzes_section)?.visibility = View.GONE
            return
        }

        val container = view.findViewById<LinearLayout>(R.id.container_student_quiz_items) ?: return
        val pbLoading = view.findViewById<ProgressBar>(R.id.pb_loading_quizzes)
        val tvEmpty = view.findViewById<TextView>(R.id.tv_empty_quizzes)
        val tvCountBadge = view.findViewById<TextView>(R.id.tv_quiz_count_badge)

        val density = resources.displayMetrics.density
        val fontJakarta = try {
            androidx.core.content.res.ResourcesCompat.getFont(requireContext(), R.font.font_plus_jakarta_sans)
        } catch (_: Exception) {
            null
        }
        val dateFormat = SimpleDateFormat("MMM dd • hh:mm a", Locale.getDefault())

        db.collection("users").document(studentId)
            .collection("quiz_history")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(10)
            .get()
            .addOnSuccessListener { snaps ->
                if (!isAdded) return@addOnSuccessListener
                pbLoading?.visibility = View.GONE
                container.removeAllViews()

                if (snaps == null || snaps.isEmpty) {
                    tvEmpty?.visibility = View.VISIBLE
                    tvCountBadge?.text = "0 Quizzes"
                    return@addOnSuccessListener
                }

                tvEmpty?.visibility = View.GONE
                tvCountBadge?.text = "${snaps.size()} Quizzes"

                val rowViews = mutableListOf<View>()

                for (doc in snaps.documents) {
                    val quizType = doc.getString("quizType") ?: "recall"
                    val score = doc.getLong("finalScore")?.toInt() ?: 0
                    val totalAttempts = doc.getLong("totalAttempts")?.toInt() ?: 0
                    val docTotalQ = doc.getLong("totalQuestions")?.toInt() ?: 0
                    @Suppress("UNCHECKED_CAST")
                    val items = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                    val totalQ = maxOf(docTotalQ, totalAttempts, items.size, score, 1)
                    val percent = ((score.toFloat() / totalQ) * 100f).toInt().coerceIn(0, 100)
                    val isPassed = doc.getBoolean("isPassed") ?: (percent >= 70)
                    val timestamp = doc.getLong("timestamp") ?: 0L
                    val xp = doc.getLong("xpEarned")?.toInt() ?: 0

                    val titleText = when (quizType) {
                        "material" -> doc.getString("title") ?: "Reading Comprehension Quiz"
                        "weekly_assessment" -> doc.getString("title") ?: "Weekly Assessment"
                        else -> doc.getString("title") ?: "Vocabulary Quest"
                    }

                    val dateString = if (timestamp > 0) dateFormat.format(Date(timestamp)) else "Recent Session"

                    val card = com.google.android.material.card.MaterialCardView(requireContext()).apply {
                        radius = 16 * density
                        cardElevation = 0f
                        strokeWidth = (1.2 * density).toInt()
                        setStrokeColor(ColorStateList.valueOf(Color.parseColor("#E2E8F0")))
                        setCardBackgroundColor(Color.WHITE)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(0, 0, 0, (8 * density).toInt()) }
                    }

                    val row = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        val p = (12 * density).toInt()
                        setPadding(p, p, p, p)
                    }

                    val iconFrame = FrameLayout(requireContext()).apply {
                        val s = (38 * density).toInt()
                        layoutParams = LinearLayout.LayoutParams(s, s).apply {
                            marginEnd = (10 * density).toInt()
                        }
                        val bgRes = when (quizType) {
                            "material" -> R.drawable.bg_bento_mint
                            "weekly_assessment" -> R.drawable.bg_bento_orange
                            else -> R.drawable.bg_bento_purple
                        }
                        setBackgroundResource(bgRes)
                    }

                    val ivIcon = ImageView(requireContext()).apply {
                        val iconRes = when (quizType) {
                            "material" -> R.drawable.ic_menu_book
                            "weekly_assessment" -> R.drawable.ic_task_alt
                            else -> R.drawable.ic_school
                        }
                        val tintColor = when (quizType) {
                            "material" -> Color.parseColor("#00875A")
                            "weekly_assessment" -> Color.parseColor("#D97706")
                            else -> Color.parseColor("#5341CD")
                        }
                        setImageResource(iconRes)
                        setColorFilter(tintColor)
                        val pad = (8 * density).toInt()
                        setPadding(pad, pad, pad, pad)
                        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                    }
                    iconFrame.addView(ivIcon)

                    val textCol = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    val tvTitle = TextView(requireContext()).apply {
                        text = titleText
                        textSize = 13.5f
                        setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                        setTextColor(Color.parseColor("#0F172A"))
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }

                    val tvMeta = TextView(requireContext()).apply {
                        text = "$dateString • +$xp XP"
                        textSize = 11f
                        setTextColor(Color.parseColor("#64748B"))
                        setPadding(0, (2 * density).toInt(), 0, 0)
                    }

                    textCol.addView(tvTitle)
                    textCol.addView(tvMeta)

                    val pillScore = TextView(requireContext()).apply {
                        text = "$score / $totalQ ($percent%)"
                        textSize = 11.5f
                        setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                        val (bColor, tColor) = if (isPassed) Pair("#ECFDF5", "#059669") else Pair("#FFF1F2", "#E11D48")
                        background = android.graphics.drawable.GradientDrawable().apply {
                            cornerRadius = 8 * density
                            setColor(Color.parseColor(bColor))
                        }
                        setTextColor(Color.parseColor(tColor))
                        setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
                    }

                    row.addView(iconFrame)
                    row.addView(textCol)
                    row.addView(pillScore)
                    card.addView(row)

                    if (items.isNotEmpty()) {
                        GabAIUtils.addSpringPressEffect(card) {
                            GabAIUtils.performHaptic(card)
                            QuizReviewDialogHelper.showReviewDialog(
                                context = requireContext(),
                                title = titleText,
                                subtitle = "$dateString • Score: $score / $totalQ ($percent%)",
                                score = score,
                                totalQuestions = totalQ,
                                xpEarned = xp,
                                items = items,
                                badgeIcon = if (isPassed) "🟢" else "🔴"
                            )
                        }
                    }

                    container.addView(card)
                    rowViews.add(card)
                }

                GabAIUtils.animateCascade(rowViews, 20L)
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                pbLoading?.visibility = View.GONE
                tvEmpty?.visibility = View.VISIBLE
                tvEmpty?.text = "Unable to load quiz history."
            }
    }
}
