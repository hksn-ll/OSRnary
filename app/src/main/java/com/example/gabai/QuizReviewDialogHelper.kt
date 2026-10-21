package com.example.gabai

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.Html
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import org.json.JSONArray

object QuizReviewDialogHelper {

    fun showReviewDialog(
        context: Context,
        title: String = "Session Review",
        subtitle: String,
        score: Int,
        totalQuestions: Int,
        xpEarned: Int = 0,
        items: List<Map<String, Any>>,
        badgeIcon: String = "📝"
    ) {
        val density = context.resources.displayMetrics.density
        val fontJakarta = try {
            ResourcesCompat.getFont(context, R.font.font_plus_jakarta_sans)
        } catch (_: Exception) {
            null
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14 * density).toInt(), (12 * density).toInt(), (14 * density).toInt(), (12 * density).toInt())
        }

        if (items.isEmpty()) {
            val emptyTv = TextView(context).apply {
                text = "Detailed question breakdown is not available for this session."
                textSize = 13.5f
                setTextColor(Color.parseColor("#64748B"))
                gravity = android.view.Gravity.CENTER
                setPadding(0, (24 * density).toInt(), 0, (24 * density).toInt())
            }
            container.addView(emptyTv)
        } else {
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
                val mech = (item["mechanic"] as? String ?: "COMPREHENSION").uppercase()

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
                    else -> "MULTIPLE CHOICE 🎯"
                }

                val qCard = com.google.android.material.card.MaterialCardView(context).apply {
                    radius = 18 * density
                    cardElevation = 0f
                    strokeWidth = (1.2 * density).toInt()
                    strokeColor = Color.parseColor(if (isCorrect) "#A7F3D0" else "#FECDD3")
                    setCardBackgroundColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 0, 0, (14 * density).toInt()) }
                }

                val qLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding((14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt())
                }

                // Header Row
                val headerRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                }

                val tvBadge = TextView(context).apply {
                    text = "Q${index + 1} • $mechBadgeText"
                    textSize = 10f
                    setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                    setTextColor(Color.parseColor("#5341CD"))
                    setBackgroundResource(R.drawable.bg_pill_translucent)
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EEF2FF"))
                    setPadding((8 * density).toInt(), (3 * density).toInt(), (8 * density).toInt(), (3 * density).toInt())
                }

                val spacer = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
                }

                val tvStatus = TextView(context).apply {
                    text = if (isCorrect) "✔ CORRECT" else "✘ MISSED"
                    textSize = 10f
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

                // Question Text
                val tvQ = TextView(context).apply {
                    text = question
                    textSize = 13.5f
                    setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                    setTextColor(Color.parseColor("#0F172A"))
                    setLineSpacing(2.5f * density, 1f)
                    setPadding(0, (8 * density).toInt(), 0, (10 * density).toInt())
                }
                qLayout.addView(tvQ)

                // Choices or Answer Breakdown
                if (optionsList.isNotEmpty()) {
                    renderOptionChips(context, qLayout, optionsList, cleanUser, cleanCorrect, isCorrect, density, fontJakarta)
                } else {
                    // Direct Answer Summary Box
                    val ansBox = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setBackgroundResource(R.drawable.bg_pill_translucent)
                        backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (isCorrect) "#F0FDF4" else "#FFF1F2"))
                        setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    }

                    val tvStudentAns = TextView(context).apply {
                        text = "Submitted Choice: ${if (cleanUser.isNotBlank()) cleanUser else "(No answer)"}"
                        textSize = 12f
                        setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                        setTextColor(Color.parseColor(if (isCorrect) "#059669" else "#E11D48"))
                    }
                    ansBox.addView(tvStudentAns)

                    if (!isCorrect && cleanCorrect.isNotBlank()) {
                        val tvCorrectAns = TextView(context).apply {
                            text = "Correct Answer: $cleanCorrect"
                            textSize = 12f
                            setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                            setTextColor(Color.parseColor("#059669"))
                            setPadding(0, (4 * density).toInt(), 0, 0)
                        }
                        ansBox.addView(tvCorrectAns)
                    }
                    qLayout.addView(ansBox)
                }

                // Explanation
                if (explanation.isNotBlank()) {
                    val expBox = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setBackgroundResource(R.drawable.bg_bento_purple)
                        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F8FAFC"))
                        setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            setMargins(0, (10 * density).toInt(), 0, 0)
                        }
                    }
                    val tvExpLabel = TextView(context).apply {
                        text = "💡 INSIGHT & EXPLANATION"
                        textSize = 10f
                        setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                        setTextColor(Color.parseColor("#5341CD"))
                        letterSpacing = 0.05f
                    }
                    val tvExp = TextView(context).apply {
                        text = explanation
                        textSize = 11.5f
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
        }

        val maxDialogHeight = (context.resources.displayMetrics.heightPixels * 0.65).toInt()
        val scrollView = ScrollView(context).apply {
            addView(container)
            isFillViewport = true
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                maxDialogHeight
            )
        }

        GabAIDialogs.showCustomDialog(
            context = context,
            title = title,
            subtitle = subtitle,
            customView = scrollView,
            confirmText = null,
            cancelText = "Done",
            badgeIcon = badgeIcon
        )
    }

    private fun renderOptionChips(
        context: Context,
        parent: LinearLayout,
        options: List<String>,
        userAns: String,
        correctAns: String,
        isCorrect: Boolean,
        density: Float,
        fontJakarta: Typeface?
    ) {
        val cleanUser = userAns.replace("*", "").trim().removeSuffix(".")
        val cleanCorrect = correctAns.replace("*", "").trim().removeSuffix(".")

        for ((idx, opt) in options.withIndex()) {
            val cleanOpt = opt.replace("*", "").trim().removeSuffix(".")
            val isOptUser = cleanOpt.equals(cleanUser, ignoreCase = true)
            val isOptCorrect = cleanOpt.equals(cleanCorrect, ignoreCase = true)

            val (bgColor, strokeColor, textColor, tagText) = when {
                isOptCorrect -> Quad("#ECFDF5", "#A7F3D0", "#065F46", if (isOptUser) "✔ Chosen & Correct" else "✔ Correct Answer")
                isOptUser && !isCorrect -> Quad("#FEF2F2", "#FECDD3", "#991B1B", "✘ Chosen")
                else -> Quad("#FFFFFF", "#E2E8F0", "#475569", null)
            }

            val card = com.google.android.material.card.MaterialCardView(context).apply {
                radius = 12 * density
                cardElevation = 0f
                strokeWidth = (1.2 * density).toInt()
                setStrokeColor(ColorStateList.valueOf(Color.parseColor(strokeColor)))
                setCardBackgroundColor(Color.parseColor(bgColor))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, (6 * density).toInt()) }
            }

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding((12 * density).toInt(), (9 * density).toInt(), (12 * density).toInt(), (9 * density).toInt())
            }

            val prefixLetter = ('A' + idx).toString()
            val tvPrefix = TextView(context).apply {
                text = "$prefixLetter."
                textSize = 12f
                setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                setTextColor(Color.parseColor(textColor))
                setPadding(0, 0, (8 * density).toInt(), 0)
            }

            val tvOpt = TextView(context).apply {
                text = cleanOpt
                textSize = 12.5f
                setTypeface(fontJakarta ?: typeface, if (isOptUser || isOptCorrect) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(Color.parseColor(textColor))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            row.addView(tvPrefix)
            row.addView(tvOpt)

            if (tagText != null) {
                val tvTag = TextView(context).apply {
                    text = tagText
                    textSize = 10f
                    setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                    setTextColor(Color.parseColor(textColor))
                    setPadding((6 * density).toInt(), 0, 0, 0)
                }
                row.addView(tvTag)
            }

            card.addView(row)
            parent.addView(card)
        }
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
