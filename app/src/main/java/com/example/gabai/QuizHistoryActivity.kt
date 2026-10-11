package com.example.gabai

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Html
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class QuizHistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GabAIUtils.applyHardwareMaxRefreshRate(this)
        setContentView(R.layout.activity_quiz_history)

        val blurHeader = findViewById<FastBlurView>(R.id.blur_header_quiz_history)
        val blurTarget = findViewById<FastBlurTarget>(R.id.blur_target_quiz_history)
        GabAIUtils.setupBlurView(blurHeader, blurTarget)

        val header = findViewById<View>(R.id.history_header)
        val scrollContent = findViewById<View>(R.id.scroll_quiz_history)
        GabAIUtils.applyHeaderAndScrollInsets(header, scrollContent, extraBufferDp = 16)

        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        btnBack?.setOnClickListener {
            GabAIUtils.performHaptic(it, android.view.HapticFeedbackConstants.CLOCK_TICK)
            finish()
        }

        loadQuizHistory()
    }

    private fun loadQuizHistory() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val container = findViewById<LinearLayout>(R.id.quiz_history_container)

        GabAIUtils.showGlobalLoading(this)

        db.collection("users").document(uid).collection("quiz_history")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshots ->
                GabAIUtils.hideGlobalLoading(this)
                container.removeAllViews()

                if (snapshots.isEmpty) {
                    val emptyMsg = TextView(this).apply {
                        text = "No quiz sessions recorded yet.\nComplete a vocabulary quest to track your growth!"
                        textSize = 15f
                        setTextColor(Color.parseColor("#64748B"))
                        gravity = android.view.Gravity.CENTER
                        setPadding(0, 100, 0, 0)
                    }
                    container.addView(emptyMsg)
                    return@addOnSuccessListener
                }

                val dateFormat = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
                val fontJakarta = try {
                    ResourcesCompat.getFont(this, R.font.font_plus_jakarta_sans)
                } catch (_: Exception) {
                    null
                }

                val density = resources.displayMetrics.density

                val totalSessions = snapshots.size()
                var totalQuestionsAll = 0
                var totalCorrectAll = 0
                var totalXpAll = 0

                for (doc in snapshots.documents) {
                    val score = doc.getLong("finalScore")?.toInt() ?: 0
                    val items = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                    val totalQ = if (items.isNotEmpty()) items.size else (doc.getLong("totalAttempts")?.toInt() ?: 1).coerceAtLeast(1)
                    val xp = doc.getLong("xpEarned")?.toInt() ?: 0
                    totalQuestionsAll += totalQ
                    totalCorrectAll += score
                    totalXpAll += xp
                }
                val avgAccuracy = if (totalQuestionsAll > 0) ((totalCorrectAll.toFloat() / totalQuestionsAll) * 100f).toInt() else 0

                val statsRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 0, 0, (18 * density).toInt()) }
                }

                fun createStatCard(label: String, value: String, icon: String, valueColor: String): View {
                    return LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = android.view.Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                            setMargins((4 * density).toInt(), 0, (4 * density).toInt(), 0)
                        }
                        setBackgroundResource(R.drawable.bg_bento_white)
                        setPadding((10 * density).toInt(), (14 * density).toInt(), (10 * density).toInt(), (14 * density).toInt())

                        val tvVal = TextView(this@QuizHistoryActivity).apply {
                            text = "$icon $value"
                            textSize = 14.5f
                            setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                            setTextColor(Color.parseColor(valueColor))
                            gravity = android.view.Gravity.CENTER
                        }
                        val tvLbl = TextView(this@QuizHistoryActivity).apply {
                            text = label
                            textSize = 10f
                            setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                            setTextColor(Color.parseColor("#94A3B8"))
                            gravity = android.view.Gravity.CENTER
                            letterSpacing = 0.04f
                            setPadding(0, (4 * density).toInt(), 0, 0)
                        }
                        addView(tvVal)
                        addView(tvLbl)
                    }
                }

                statsRow.addView(createStatCard("QUESTS", totalSessions.toString(), "🏆", "#5341CD"))
                statsRow.addView(createStatCard("ACCURACY", "$avgAccuracy%", "🎯", if (avgAccuracy >= 75) "#059669" else "#D97706"))
                statsRow.addView(createStatCard("QUIZ XP", "+$totalXpAll", "⚡", "#7C3AED"))
                container.addView(statsRow)

                val sectionTitle = TextView(this).apply {
                    text = "Challenge History"
                    textSize = 13.5f
                    setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                    setTextColor(Color.parseColor("#475569"))
                    setPadding((4 * density).toInt(), 0, 0, (12 * density).toInt())
                }
                container.addView(sectionTitle)

                val cardViews = mutableListOf<View>()

                for (doc in snapshots.documents) {
                    val timestamp = doc.getLong("timestamp") ?: 0L
                    val score = doc.getLong("finalScore")?.toInt() ?: 0
                    val attempts = doc.getLong("totalAttempts")?.toInt() ?: 0
                    val dateString = dateFormat.format(Date(timestamp))

                    val items = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                    val totalQuestions = if (items.isNotEmpty()) items.size else attempts.coerceAtLeast(1)
                    val percent = if (totalQuestions > 0) ((score.toFloat() / totalQuestions) * 100f).toInt() else 0
                    val xpEarned = doc.getLong("xpEarned")?.toInt() ?: 0

                    val card = com.google.android.material.card.MaterialCardView(this).apply {
                        radius = 20 * density
                        cardElevation = 0f
                        strokeWidth = (1.2 * density).toInt()
                        strokeColor = Color.parseColor("#E2E8F0")
                        setCardBackgroundColor(Color.WHITE)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(0, 0, 0, (14 * density).toInt()) }
                    }

                    val contentLayout = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding((18 * density).toInt(), (18 * density).toInt(), (18 * density).toInt(), (18 * density).toInt())
                    }

                    val quizType = doc.getString("quizType") ?: "recall"
                    val isMaterialQuiz = (quizType == "material")

                    // Top Row: Squircle Icon, Date, Score & XP Pills
                    val topRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                    }

                    val iconFrame = FrameLayout(this).apply {
                        setBackgroundResource(if (isMaterialQuiz) R.drawable.bg_bento_mint else R.drawable.bg_bento_purple)
                        val sizePx = (42 * density).toInt()
                        layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                            setMargins(0, 0, (12 * density).toInt(), 0)
                        }
                    }

                    val iconView = ImageView(this).apply {
                        setImageResource(if (isMaterialQuiz) R.drawable.ic_menu_book else R.drawable.ic_school)
                        setColorFilter(Color.parseColor(if (isMaterialQuiz) "#00875A" else "#5341CD"))
                        val pad = (10 * density).toInt()
                        setPadding(pad, pad, pad, pad)
                        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                    }
                    iconFrame.addView(iconView)

                    val textCol = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    val dateTitle = TextView(this).apply {
                        text = dateString
                        textSize = 14.5f
                        setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                        setTextColor(Color.parseColor("#0F172A"))
                    }

                    val scoreSubtitle = TextView(this).apply {
                        text = if (isMaterialQuiz) "$score / $totalQuestions Questions • Reading Material" else "$score / $totalQuestions Questions Correct"
                        textSize = 12.5f
                        setTypeface(fontJakarta ?: typeface, Typeface.NORMAL)
                        setTextColor(Color.parseColor("#64748B"))
                        setPadding(0, (2 * density).toInt(), 0, 0)
                    }

                    textCol.addView(dateTitle)
                    textCol.addView(scoreSubtitle)

                    val pillCol = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = android.view.Gravity.END
                    }

                    val xpPill = TextView(this).apply {
                        text = "+$xpEarned XP"
                        textSize = 11f
                        setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                        setTextColor(Color.parseColor("#7C3AED"))
                        setBackgroundResource(R.drawable.bg_pill_translucent)
                        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F3E8FF"))
                        setPadding((8 * density).toInt(), (3 * density).toInt(), (8 * density).toInt(), (3 * density).toInt())
                    }

                    val scorePill = TextView(this).apply {
                        text = "$percent%"
                        textSize = 11f
                        setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                        setBackgroundResource(R.drawable.bg_pill_translucent)
                        val isHigh = percent >= 75
                        setTextColor(Color.parseColor(if (isHigh) "#059669" else "#D97706"))
                        backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (isHigh) "#ECFDF5" else "#FEF3C7"))
                        setPadding((8 * density).toInt(), (3 * density).toInt(), (8 * density).toInt(), (3 * density).toInt())
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(0, (4 * density).toInt(), 0, 0) }
                    }

                    pillCol.addView(xpPill)
                    pillCol.addView(scorePill)

                    topRow.addView(iconFrame)
                    topRow.addView(textCol)
                    topRow.addView(pillCol)
                    contentLayout.addView(topRow)

                    // Mechanics Tags Row
                    val mechanicsSet = mutableSetOf<String>()
                    if (isMaterialQuiz) {
                        mechanicsSet.add("📖 Reading Comprehension")
                    } else {
                        for (item in items) {
                            val m = item["mechanic"] as? String ?: "CLOZE"
                            when (m) {
                                "CLOZE" -> mechanicsSet.add("✏️ Cloze")
                                "CONTEXT_MEANING", "DETECTIVE" -> mechanicsSet.add("🔍 Context")
                                "IMPOSTOR" -> mechanicsSet.add("👾 Glitch")
                                "GRAMMAR_LANDMINE", "LANDMINE" -> mechanicsSet.add("💣 Defusal")
                                "RAPID_MATCH" -> mechanicsSet.add("⚡ Rapid")
                            }
                        }
                        if (mechanicsSet.isEmpty()) mechanicsSet.add("✏️ Cloze")
                    }

                    val mechRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(0, (12 * density).toInt(), 0, 0) }
                    }
                    for (mechTag in mechanicsSet) {
                        val tagView = TextView(this).apply {
                            text = mechTag
                            textSize = 11f
                            setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                            setTextColor(Color.parseColor("#475569"))
                            setBackgroundResource(R.drawable.bg_pill_translucent)
                            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F1F5F9"))
                            setPadding((8 * density).toInt(), (3 * density).toInt(), (8 * density).toInt(), (3 * density).toInt())
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { setMargins(0, 0, (6 * density).toInt(), 0) }
                        }
                        mechRow.addView(tagView)
                    }
                    contentLayout.addView(mechRow)

                    // Bottom Row: Review Action
                    val actionRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(0, (12 * density).toInt(), 0, 0) }
                    }

                    val actionText = TextView(this).apply {
                        text = "Review $totalQuestions Questions ➔"
                        textSize = 13f
                        setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                        setTextColor(Color.parseColor("#5341CD"))
                    }
                    actionRow.addView(actionText)

                    contentLayout.addView(actionRow)
                    card.addView(contentLayout)

                    GabAIUtils.addSpringPressEffect(card) {
                        showReviewDialog(dateString, score, totalQuestions, xpEarned, items)
                    }

                    container.addView(card)
                    cardViews.add(card)
                }

                // Bento Back to Home Navigation Button at Bottom
                val btnBackHome = com.google.android.material.button.MaterialButton(this).apply {
                    text = "Back to Home 🏠"
                    textSize = 14f
                    setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#5341CD"))
                    cornerRadius = (16 * density).toInt()
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (50 * density).toInt()
                    ).apply { setMargins(0, (14 * density).toInt(), 0, (24 * density).toInt()) }
                }
                GabAIUtils.addSpringPressEffect(btnBackHome) {
                    val intent = Intent(this@QuizHistoryActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    startActivity(intent)
                    finish()
                }
                container.addView(btnBackHome)
                cardViews.add(btnBackHome)

                GabAIUtils.animateCascade(cardViews, 35L)
            }
            .addOnFailureListener { e ->
                GabAIUtils.hideGlobalLoading(this)
                GabAIUtils.showSnackbar(this, "Failed to load history: ${e.message}")
            }
    }

    private fun showReviewDialog(dateStr: String, score: Int, totalQuestions: Int, xpEarned: Int, items: List<Map<String, Any>>) {
        val density = resources.displayMetrics.density
        val fontJakarta = try {
            ResourcesCompat.getFont(this, R.font.font_plus_jakarta_sans)
        } catch (_: Exception) {
            null
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (14 * density).toInt(), (16 * density).toInt(), (14 * density).toInt())
        }

        for ((index, item) in items.withIndex()) {
            val question = item["question"] as? String ?: "Question"
            val targetWord = (item["targetWord"] as? String)?.takeIf { it.isNotBlank() }
                ?: (item["word"] as? String)
                ?: ""
            val rawUser = (item["userAnswer"] as? String)
                ?: (item["selectedText"] as? String)
                ?: ""
            val rawCorrect = (item["correctAnswer"] as? String)
                ?: (item["correctText"] as? String)
                ?: (item["genuineSentence"] as? String)
                ?: targetWord
            val cleanUser = rawUser.replace("*", "").trim().removeSuffix(".")
            val cleanCorrect = rawCorrect.replace("*", "").trim().removeSuffix(".")
            val isCorrect = item["isCorrect"] as? Boolean ?: false
            val explanation = item["explanation"] as? String ?: ""
            val mech = (item["mechanic"] as? String ?: "CLOZE").uppercase()

            val rawOptions = item["options"]
            val optionsList = when (rawOptions) {
                is List<*> -> rawOptions.mapNotNull { it?.toString() }
                is JSONArray -> {
                    val list = mutableListOf<String>()
                    for (k in 0 until rawOptions.length()) list.add(rawOptions.getString(k))
                    list
                }
                else -> emptyList()
            }
            val contextSentence = item["contextSentence"] as? String ?: ""
            val genuineSentence = item["genuineSentence"] as? String ?: ""
            val impostorSentence = item["impostorSentence"] as? String ?: ""

            val mechBadgeText = when {
                mech.contains("CLOZE") -> "SENTENCE CLOZE ✏️"
                mech.contains("LANDMINE") -> "GRAMMAR LANDMINE 💣"
                mech.contains("CONTEXT") || mech.contains("DETECTIVE") -> "CONTEXT MEANING 🔍"
                mech.contains("IMPOSTOR") -> "SPOT THE IMPOSTOR 👾"
                mech.contains("RAPID") -> "RAPID MATCH ⚡"
                mech.contains("WEEKLY") -> "WEEKLY ASSESSMENT 📝"
                mech.contains("READING") || mech.contains("MATERIAL") -> "READING COMPREHENSION 📖"
                else -> "VOCABULARY CHALLENGE 🎯"
            }

            val qCard = com.google.android.material.card.MaterialCardView(this).apply {
                radius = 20 * density
                cardElevation = 0f
                strokeWidth = (1.5 * density).toInt()
                strokeColor = Color.parseColor(if (isCorrect) "#A7F3D0" else "#FECDD3")
                setCardBackgroundColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, (16 * density).toInt()) }
            }

            val qLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((16 * density).toInt(), (14 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
            }

            // --- 1. Header Row (Mechanic Pill + Result Status) ---
            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            val tvBadge = TextView(this).apply {
                text = "Q${index + 1} • $mechBadgeText"
                textSize = 10.5f
                setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#5341CD"))
                setBackgroundResource(R.drawable.bg_pill_translucent)
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EEF2FF"))
                setPadding((8 * density).toInt(), (3 * density).toInt(), (8 * density).toInt(), (3 * density).toInt())
            }

            val spacer = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
            }

            val tvStatus = TextView(this).apply {
                text = if (isCorrect) "✔ CORRECT" else "✘ MISSED"
                textSize = 10.5f
                setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                setTextColor(Color.parseColor(if (isCorrect) "#059669" else "#DC2626"))
                setBackgroundResource(R.drawable.bg_pill_translucent)
                backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (isCorrect) "#ECFDF5" else "#FEF2F2"))
                setPadding((8 * density).toInt(), (3 * density).toInt(), (8 * density).toInt(), (3 * density).toInt())
            }

            headerRow.addView(tvBadge)
            headerRow.addView(spacer)
            headerRow.addView(tvStatus)
            qLayout.addView(headerRow)

            // --- 2. MECHANIC-SPECIFIC IN-GAME RENDERER ---
            when {
                // ==========================================
                // SPOT THE IMPOSTOR MECHANIC
                // ==========================================
                mech.contains("IMPOSTOR") -> {
                    if (targetWord.isNotBlank()) {
                        val tvTarget = TextView(this).apply {
                            text = "Target Word: \"$targetWord\""
                            textSize = 14f
                            setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                            setTextColor(Color.parseColor("#0F172A"))
                            setPadding(0, (10 * density).toInt(), 0, (2 * density).toInt())
                        }
                        val tvPrompt = TextView(this).apply {
                            text = "Which sentence uses the word correctly?"
                            textSize = 12f
                            setTextColor(Color.parseColor("#64748B"))
                            setPadding(0, 0, 0, (10 * density).toInt())
                        }
                        qLayout.addView(tvTarget)
                        qLayout.addView(tvPrompt)
                    }

                    val genText = genuineSentence.ifBlank { if (isCorrect) cleanUser else cleanCorrect }
                    val glitchText = impostorSentence.ifBlank { if (!isCorrect) cleanUser else "" }

                    // Genuine Card
                    val genuineCard = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setBackgroundResource(R.drawable.bg_cloze_slot_filled)
                        setPadding((14 * density).toInt(), (12 * density).toInt(), (14 * density).toInt(), (12 * density).toInt())
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            setMargins(0, 0, 0, (8 * density).toInt())
                        }
                    }
                    val genuineBadgeRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                    }
                    val tvGenBadge = TextView(this).apply {
                        text = "✔ GENUINE USAGE"
                        textSize = 10f
                        setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                        setTextColor(Color.parseColor("#065F46"))
                        setBackgroundResource(R.drawable.bg_pill_translucent)
                        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#D1FAE5"))
                        setPadding((6 * density).toInt(), (2 * density).toInt(), (6 * density).toInt(), (2 * density).toInt())
                    }
                    genuineBadgeRow.addView(tvGenBadge)
                    if (cleanUser.equals(genText, ignoreCase = true) || isCorrect) {
                        val tvYourPick = TextView(this).apply {
                            text = " • Your Choice ✔"
                            textSize = 10f
                            setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                            setTextColor(Color.parseColor("#059669"))
                        }
                        genuineBadgeRow.addView(tvYourPick)
                    }
                    val tvGenText = TextView(this).apply {
                        text = genText
                        textSize = 13.5f
                        setTextColor(Color.parseColor("#065F46"))
                        setLineSpacing(2.5f * density, 1f)
                        setPadding(0, (6 * density).toInt(), 0, 0)
                    }
                    genuineCard.addView(genuineBadgeRow)
                    genuineCard.addView(tvGenText)
                    qLayout.addView(genuineCard)

                    // Glitch Card
                    if (glitchText.isNotBlank()) {
                        val glitchCard = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            setBackgroundResource(R.drawable.bg_cloze_slot)
                            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF2F2"))
                            setPadding((14 * density).toInt(), (12 * density).toInt(), (14 * density).toInt(), (12 * density).toInt())
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                                setMargins(0, 0, 0, (8 * density).toInt())
                            }
                        }
                        val glitchBadgeRow = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = android.view.Gravity.CENTER_VERTICAL
                        }
                        val tvGlitchBadge = TextView(this).apply {
                            text = "👾 ABSURD GLITCH"
                            textSize = 10f
                            setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                            setTextColor(Color.parseColor("#991B1B"))
                            setBackgroundResource(R.drawable.bg_pill_translucent)
                            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEE2E2"))
                            setPadding((6 * density).toInt(), (2 * density).toInt(), (6 * density).toInt(), (2 * density).toInt())
                        }
                        glitchBadgeRow.addView(tvGlitchBadge)
                        if (!isCorrect && cleanUser.equals(glitchText, ignoreCase = true)) {
                            val tvYourPick = TextView(this).apply {
                                text = " • Your Choice ✘"
                                textSize = 10f
                                setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                                setTextColor(Color.parseColor("#DC2626"))
                            }
                            glitchBadgeRow.addView(tvYourPick)
                        }
                        val tvGlitchText = TextView(this).apply {
                            text = glitchText
                            textSize = 13.5f
                            setTextColor(Color.parseColor("#991B1B"))
                            setLineSpacing(2.5f * density, 1f)
                            setPadding(0, (6 * density).toInt(), 0, 0)
                        }
                        glitchCard.addView(glitchBadgeRow)
                        glitchCard.addView(tvGlitchText)
                        qLayout.addView(glitchCard)
                    }
                }

                // ==========================================
                // RAPID MATCH MECHANIC
                // ==========================================
                mech.contains("RAPID") -> {
                    val tvRapidHeader = TextView(this).apply {
                        text = "Speed Vocabulary Pairing"
                        textSize = 14f
                        setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                        setTextColor(Color.parseColor("#0F172A"))
                        setPadding(0, (10 * density).toInt(), 0, (4 * density).toInt())
                    }
                    val tvRapidSub = TextView(this).apply {
                        text = "Match each target word to its accurate definition under speed pressure."
                        textSize = 12f
                        setTextColor(Color.parseColor("#64748B"))
                        setPadding(0, 0, 0, (10 * density).toInt())
                    }
                    qLayout.addView(tvRapidHeader)
                    qLayout.addView(tvRapidSub)

                    val matchString = cleanCorrect.ifBlank { cleanUser }
                    val pairTokens = matchString.split(";").map { it.trim() }.filter { it.isNotBlank() }

                    for (pairToken in pairTokens) {
                        val parts = pairToken.split("➔").map { it.trim() }
                        val wordPart = parts.getOrNull(0) ?: ""
                        val defPart = parts.getOrNull(1) ?: ""

                        val matchCard = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = android.view.Gravity.CENTER_VERTICAL
                            setBackgroundResource(R.drawable.bg_bento_mint)
                            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F0FDF4"))
                            setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                                setMargins(0, 0, 0, (6 * density).toInt())
                            }
                        }
                        val tvW = TextView(this).apply {
                            text = wordPart
                            textSize = 13.5f
                            setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                            setTextColor(Color.parseColor("#4338CA"))
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                                setMargins(0, 0, (8 * density).toInt(), 0)
                            }
                        }
                        val tvArrow = TextView(this).apply {
                            text = "➔"
                            textSize = 12f
                            setTextColor(Color.parseColor("#94A3B8"))
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                                setMargins(0, 0, (8 * density).toInt(), 0)
                            }
                        }
                        val tvDef = TextView(this).apply {
                            text = defPart
                            textSize = 12.5f
                            setTypeface(fontJakarta ?: typeface, Typeface.NORMAL)
                            setTextColor(Color.parseColor("#1E293B"))
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        }
                        val tvMatchedBadge = TextView(this).apply {
                            text = "✔"
                            textSize = 12f
                            setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                            setTextColor(Color.parseColor("#059669"))
                            setPadding((6 * density).toInt(), 0, 0, 0)
                        }
                        matchCard.addView(tvW)
                        matchCard.addView(tvArrow)
                        matchCard.addView(tvDef)
                        matchCard.addView(tvMatchedBadge)
                        qLayout.addView(matchCard)
                    }
                }

                // ==========================================
                // GRAMMAR LANDMINE (SENTENCE DEFUSAL) MECHANIC
                // ==========================================
                mech.contains("LANDMINE") -> {
                    val lSentence = (item["landmineSentence"] as? String)?.ifBlank { question } ?: question
                    val lCorrection = (item["landmineCorrection"] as? String) ?: ""

                    val warningCard = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setBackgroundResource(R.drawable.bg_pill_translucent)
                        backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (isCorrect) "#ECFDF5" else "#FEF2F2"))
                        setPadding((14 * density).toInt(), (12 * density).toInt(), (14 * density).toInt(), (12 * density).toInt())
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            setMargins(0, (8 * density).toInt(), 0, (10 * density).toInt())
                        }
                    }
                    val tvWarnTitle = TextView(this).apply {
                        text = if (isCorrect) "🛡️ BOMB DEFUSED! Faulty wire was snipped" else "💥 DETONATED! Safe wire was snipped"
                        textSize = 11.5f
                        setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                        setTextColor(Color.parseColor(if (isCorrect) "#065F46" else "#991B1B"))
                    }
                    val tvWarnSentence = TextView(this).apply {
                        text = "\"$lSentence\""
                        textSize = 13.5f
                        setTypeface(fontJakarta ?: typeface, Typeface.NORMAL)
                        setTextColor(Color.parseColor("#1E293B"))
                        setLineSpacing(2.5f * density, 1f)
                        setPadding(0, (4 * density).toInt(), 0, 0)
                    }
                    warningCard.addView(tvWarnTitle)
                    warningCard.addView(tvWarnSentence)
                    qLayout.addView(warningCard)

                    val tvPrompt = TextView(this).apply {
                        text = if (lCorrection.isNotBlank()) "Faulty Wire: \"$cleanCorrect\" ➔ Fixed: \"$lCorrection\"" else "Faulty Wire: \"$cleanCorrect\""
                        textSize = 12.5f
                        setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                        setTextColor(Color.parseColor("#4338CA"))
                        setPadding(0, 0, 0, (8 * density).toInt())
                    }
                    qLayout.addView(tvPrompt)

                    renderOptionChips(qLayout, optionsList, cleanUser, cleanCorrect, isCorrect, density, fontJakarta)
                }

                // ==========================================
                // CONTEXT MEANING (DETECTIVE) MECHANIC
                // ==========================================
                mech.contains("CONTEXT") || mech.contains("DETECTIVE") -> {
                    if (targetWord.isNotBlank()) {
                        val tvTarget = TextView(this).apply {
                            text = "🎯 Target Word: \"$targetWord\""
                            textSize = 14f
                            setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                            setTextColor(Color.parseColor("#0F172A"))
                            setPadding(0, (10 * density).toInt(), 0, (4 * density).toInt())
                        }
                        qLayout.addView(tvTarget)
                    }

                    // Context Excerpt Box
                    val passageCard = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setBackgroundResource(R.drawable.bg_cloze_slot)
                        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F8FAFC"))
                        setPadding((14 * density).toInt(), (12 * density).toInt(), (14 * density).toInt(), (12 * density).toInt())
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            setMargins(0, (6 * density).toInt(), 0, (10 * density).toInt())
                        }
                    }
                    val tvPassageLabel = TextView(this).apply {
                        text = "📖 CONTEXT EXCERPT"
                        textSize = 10f
                        setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                        setTextColor(Color.parseColor("#6366F1"))
                        letterSpacing = 0.05f
                    }
                    val rawPassage = contextSentence.ifBlank { question }
                    val passageHtml = if (targetWord.isNotBlank()) {
                        rawPassage.replace(Regex("(?i)\\b(${Regex.escape(targetWord)})\\b"), "<font color='#5341CD'><b><u>$1</u></b></font>")
                    } else rawPassage
                    val tvPassage = TextView(this).apply {
                        text = Html.fromHtml(passageHtml, Html.FROM_HTML_MODE_COMPACT)
                        textSize = 13.5f
                        setTextColor(Color.parseColor("#1E293B"))
                        setLineSpacing(2.5f * density, 1f)
                        setPadding(0, (4 * density).toInt(), 0, 0)
                    }
                    passageCard.addView(tvPassageLabel)
                    passageCard.addView(tvPassage)
                    qLayout.addView(passageCard)

                    val tvPrompt = TextView(this).apply {
                        text = "In this sentence context, what does \"$targetWord\" mean?"
                        textSize = 13f
                        setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                        setTextColor(Color.parseColor("#334155"))
                        setPadding(0, 0, 0, (8 * density).toInt())
                    }
                    qLayout.addView(tvPrompt)

                    // Options chips
                    renderOptionChips(qLayout, optionsList, cleanUser, cleanCorrect, isCorrect, density, fontJakarta)
                }

                // ==========================================
                // SENTENCE CLOZE MECHANIC
                // ==========================================
                mech.contains("CLOZE") -> {
                    if (targetWord.isNotBlank()) {
                        val tvTarget = TextView(this).apply {
                            text = "🎯 Target: \"$targetWord\""
                            textSize = 14f
                            setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                            setTextColor(Color.parseColor("#0F172A"))
                            setPadding(0, (10 * density).toInt(), 0, (4 * density).toInt())
                        }
                        qLayout.addView(tvTarget)
                    }

                    val tvQ = TextView(this).apply {
                        text = question
                        textSize = 14f
                        setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                        setTextColor(Color.parseColor("#0F172A"))
                        setLineSpacing(3f * density, 1f)
                        setPadding(0, (4 * density).toInt(), 0, (8 * density).toInt())
                    }
                    qLayout.addView(tvQ)

                    // Cloze Slot Pill
                    val clozeSlot = TextView(this).apply {
                        if (isCorrect) {
                            text = "✔ Filled: $cleanCorrect"
                            setTextColor(Color.parseColor("#059669"))
                            setBackgroundResource(R.drawable.bg_cloze_slot_filled)
                        } else {
                            text = "✘ Your Fill: $cleanUser  |  ✔ Correct: $cleanCorrect"
                            setTextColor(Color.parseColor("#991B1B"))
                            setBackgroundResource(R.drawable.bg_cloze_slot)
                            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF2F2"))
                        }
                        textSize = 13f
                        setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                        gravity = android.view.Gravity.CENTER
                        setPadding((14 * density).toInt(), (10 * density).toInt(), (14 * density).toInt(), (10 * density).toInt())
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            setMargins(0, 0, 0, (10 * density).toInt())
                        }
                    }
                    qLayout.addView(clozeSlot)

                    // Options chips
                    renderOptionChips(qLayout, optionsList, cleanUser, cleanCorrect, isCorrect, density, fontJakarta)
                }

                // ==========================================
                // READING COMPREHENSION / WEEKLY ASSESSMENT / OTHER
                // ==========================================
                else -> {
                    val tvQ = TextView(this).apply {
                        text = question
                        textSize = 14f
                        setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                        setTextColor(Color.parseColor("#0F172A"))
                        setLineSpacing(3f * density, 1f)
                        setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
                    }
                    qLayout.addView(tvQ)

                    renderOptionChips(qLayout, optionsList, cleanUser, cleanCorrect, isCorrect, density, fontJakarta)
                }
            }

            // --- 3. Educational Insight Pill (for all mechanics) ---
            if (explanation.isNotBlank()) {
                val expBox = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundResource(R.drawable.bg_bento_purple)
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F8FAFC"))
                    setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(0, (10 * density).toInt(), 0, 0)
                    }
                }
                val tvExpLabel = TextView(this).apply {
                    text = "💡 EDUCATIONAL INSIGHT"
                    textSize = 10f
                    setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                    setTextColor(Color.parseColor("#5341CD"))
                    letterSpacing = 0.05f
                }
                val tvExp = TextView(this).apply {
                    text = explanation
                    textSize = 12f
                    setTextColor(Color.parseColor("#475569"))
                    setLineSpacing(2f * density, 1f)
                    setPadding(0, (3 * density).toInt(), 0, 0)
                }
                expBox.addView(tvExpLabel)
                expBox.addView(tvExp)
                qLayout.addView(expBox)
            }

            qCard.addView(qLayout)
            container.addView(qCard)
        }

        val scrollView = ScrollView(this).apply {
            addView(container)
            isFillViewport = true
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.65).toInt()
            )
        }

        val pct = if (totalQuestions > 0) ((score.toFloat() / totalQuestions) * 100f).toInt() else 0
        GabAIDialogs.showCustomDialog(
            context = this,
            title = "Session Review",
            subtitle = "Score: $score / $totalQuestions Questions ($pct%) • +$xpEarned XP",
            customView = scrollView,
            confirmText = "Close Review",
            cancelText = null,
            badgeIcon = "📝",
            onConfirm = { dialog ->
                dialog.dismiss()
            }
        )
    }

    private fun renderOptionChips(
        parent: LinearLayout,
        optionsList: List<String>,
        cleanUser: String,
        cleanCorrect: String,
        isCorrect: Boolean,
        density: Float,
        fontJakarta: Typeface?
    ) {
        if (optionsList.isEmpty()) {
            val tvAns = TextView(this).apply {
                text = if (isCorrect) "✔ Your Answer: $cleanUser" else "✘ Your Answer: $cleanUser\n✔ Correct Answer: $cleanCorrect"
                textSize = 12.5f
                setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                setTextColor(Color.parseColor(if (isCorrect) "#065F46" else "#991B1B"))
                setBackgroundResource(R.drawable.bg_pill_translucent)
                backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (isCorrect) "#ECFDF5" else "#FEF2F2"))
                setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            parent.addView(tvAns)
            return
        }

        for ((optIdx, optText) in optionsList.withIndex()) {
            val letter = ('A' + optIdx)
            val cleanOpt = optText.replace("*", "").trim().removeSuffix(".")
            val isThisCorrect = cleanOpt.equals(cleanCorrect, ignoreCase = true)
            val isThisUserChoice = cleanOpt.equals(cleanUser, ignoreCase = true)

            val optCard = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding((12 * density).toInt(), (9 * density).toInt(), (12 * density).toInt(), (9 * density).toInt())
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, (6 * density).toInt())
                }
                when {
                    isThisCorrect -> {
                        setBackgroundResource(R.drawable.bg_bento_mint)
                        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#ECFDF5"))
                    }
                    isThisUserChoice && !isCorrect -> {
                        setBackgroundResource(R.drawable.bg_bento_white)
                        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF2F2"))
                    }
                    else -> {
                        setBackgroundResource(R.drawable.bg_bento_white)
                    }
                }
            }

            val tvLetter = TextView(this).apply {
                text = "$letter"
                textSize = 11.5f
                setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                setTextColor(if (isThisCorrect) Color.parseColor("#065F46") else if (isThisUserChoice) Color.parseColor("#991B1B") else Color.parseColor("#5341CD"))
                setBackgroundResource(R.drawable.bg_pill_translucent)
                backgroundTintList = ColorStateList.valueOf(if (isThisCorrect) Color.parseColor("#D1FAE5") else if (isThisUserChoice) Color.parseColor("#FEE2E2") else Color.parseColor("#EEF2FF"))
                gravity = android.view.Gravity.CENTER
                val sPx = (24 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(sPx, sPx).apply { setMargins(0, 0, (10 * density).toInt(), 0) }
            }

            val tvOpt = TextView(this).apply {
                text = cleanOpt
                textSize = 13f
                setTypeface(fontJakarta ?: typeface, if (isThisCorrect || isThisUserChoice) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (isThisCorrect) Color.parseColor("#065F46") else if (isThisUserChoice) Color.parseColor("#991B1B") else Color.parseColor("#1E293B"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvIndicator = TextView(this).apply {
                text = when {
                    isThisCorrect && isThisUserChoice -> "✔ Correct"
                    isThisCorrect -> "✔ Answer"
                    isThisUserChoice -> "✘ Your Pick"
                    else -> ""
                }
                textSize = 11f
                setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                setTextColor(if (isThisCorrect) Color.parseColor("#059669") else Color.parseColor("#EF4444"))
            }

            optCard.addView(tvLetter)
            optCard.addView(tvOpt)
            optCard.addView(tvIndicator)
            parent.addView(optCard)
        }
    }
}