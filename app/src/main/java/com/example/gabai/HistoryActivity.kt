package com.example.gabai

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import io.noties.markwon.Markwon

class HistoryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GabAIUtils.applyHardwareMaxRefreshRate(this)
        setContentView(R.layout.activity_history)

        // Fix Status Bar Overlap
        val header = findViewById<View>(R.id.history_header)
        ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 12, v.paddingRight, v.paddingBottom)
            insets
        }

        GabAIUtils.applyFrostedGlass(header)
        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        btnBack?.setOnClickListener { finish() }
        GabAIUtils.addSpringPressEffect(btnBack) { finish() }

        refreshHistoryList()
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .update("quests_completed", FieldValue.arrayUnion("history"))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshHistoryList()
    }

    private fun refreshHistoryList() {
        val container = findViewById<LinearLayout>(R.id.history_list_container)
        val loadingContainer = findViewById<View>(R.id.history_loading_container)
        container.removeAllViews()

        loadingContainer?.visibility = View.VISIBLE
        container.visibility = View.GONE

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            loadingContainer?.visibility = View.GONE
            container.visibility = View.VISIBLE
            return
        }
        val db = FirebaseFirestore.getInstance()
        val markwon = Markwon.create(this)

        // First retrieve existing favorites to show correct star states
        db.collection("users").document(uid).collection("favorites").get()
            .addOnSuccessListener { favDocs ->
                val favWordSet = favDocs.map { it.id }.toHashSet()

                db.collection("users").document(uid).collection("history")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get()
                    .addOnSuccessListener { documents ->
                        loadingContainer?.visibility = View.GONE
                        container.visibility = View.VISIBLE
                        if (documents.isEmpty) {
                            val emptyView = layoutInflater.inflate(R.layout.view_empty_state, container, false)
                            emptyView.findViewById<ImageView>(R.id.iv_empty_icon).setImageResource(R.drawable.ic_insights)
                            emptyView.findViewById<TextView>(R.id.tv_empty_title).text = "No Reading History Yet"
                            emptyView.findViewById<TextView>(R.id.tv_empty_subtitle).text =
                                "Words and sentences you scan and explore with GabAI will appear here for spaced repetition."
                            container.addView(emptyView)
                            return@addOnSuccessListener
                        }

                        for (doc in documents) {
                            val word = doc.getString("word") ?: ""
                            val content = doc.getString("explanation") ?: ""
                            val context = doc.getString("originalContext") ?: word
                            val partOfSpeech = doc.getString("partOfSpeech") ?: ""
                            val phonetics = doc.getString("phonetics") ?: ""
                            val inSentenceRole = doc.getString("inSentenceRole") ?: ""

                            val itemView = layoutInflater.inflate(R.layout.item_history, container, false)
                            itemView.findViewById<TextView>(R.id.history_text).text = word

                            // Bind Part of Speech badge
                            val posBadge = itemView.findViewById<TextView>(R.id.history_pos_badge)
                            if (partOfSpeech.isNotBlank()) {
                                posBadge.text = partOfSpeech.uppercase()
                                posBadge.visibility = View.VISIBLE
                            } else {
                                posBadge.visibility = View.GONE
                            }

                            // Format explanation preview snippet
                            val snippetView = itemView.findViewById<TextView>(R.id.history_snippet)
                            markwon.setMarkdown(snippetView, content)

                            // Bind In-Sentence Role snippet if available (hide if all selected)
                            val isAllSelected = word.trim().equals(context.trim(), ignoreCase = true) || word.split(Regex("\\s+")).size > 6
                            val roleContainer = itemView.findViewById<View>(R.id.history_role_container)
                            val roleView = itemView.findViewById<TextView>(R.id.history_in_sentence_role)
                            if (!isAllSelected && inSentenceRole.isNotBlank()) {
                                roleView.text = inSentenceRole
                                roleContainer.visibility = View.VISIBLE
                            } else {
                                roleContainer.visibility = View.GONE
                            }

                            // Star Button: Toggle Favorite Status directly from History
                            val btnFav = itemView.findViewById<ImageButton>(R.id.btn_add_to_fav)
                            val isFav = favWordSet.contains(word)
                            btnFav.setImageResource(if (isFav) R.drawable.ic_star_filled else R.drawable.ic_star_outline)

                            GabAIUtils.addSpringPressEffect(btnFav) {
                                val currentlyFav = favWordSet.contains(word)
                                if (currentlyFav) {
                                    db.collection("users").document(uid).collection("favorites").document(word)
                                        .delete()
                                        .addOnSuccessListener {
                                            favWordSet.remove(word)
                                            btnFav.setImageResource(R.drawable.ic_star_outline)
                                            GabAIUtils.performHaptic(btnFav, android.view.HapticFeedbackConstants.CLOCK_TICK)
                                            GabAIUtils.showSnackbar(this, "Removed \"$word\" from Favorites")
                                        }
                                } else {
                                    val favEntry = hashMapOf(
                                        "word" to word,
                                        "definition" to content,
                                        "timestamp" to System.currentTimeMillis(),
                                        "originalContext" to context,
                                        "partOfSpeech" to partOfSpeech,
                                        "phonetics" to phonetics,
                                        "inSentenceRole" to inSentenceRole
                                    )
                                    db.collection("users").document(uid).collection("favorites").document(word)
                                        .set(favEntry)
                                        .addOnSuccessListener {
                                            favWordSet.add(word)
                                            btnFav.setImageResource(R.drawable.ic_star_filled)
                                            GabAIUtils.performHaptic(btnFav, android.view.HapticFeedbackConstants.CONFIRM)
                                            GabAIUtils.showSnackbar(this, "Saved \"$word\" to Favorites! ⭐")
                                            QuestManager.addProgress(this, QuestManager.QUEST_SAVE)
                                            db.collection("users").document(uid).update(
                                                "quests_completed",
                                                FieldValue.arrayUnion("save")
                                            )
                                        }
                                }
                            }

                            // Click to View in Full OverviewActivity
                            val clickArea = itemView.findViewById<View>(R.id.history_click_area)
                            GabAIUtils.addSpringPressEffect(clickArea) {
                                val intent = Intent(this, OverviewActivity::class.java).apply {
                                    putExtra("SELECTED_TEXT", word)
                                    putExtra("SURROUNDING_SENTENCE", context)
                                    putExtra("PRELOADED_EXPLANATION", content)
                                    putExtra("PART_OF_SPEECH", partOfSpeech)
                                    putExtra("PHONETICS", phonetics)
                                    putExtra("IN_SENTENCE_ROLE", inSentenceRole)
                                    putExtra("IS_FAVORITE", favWordSet.contains(word))
                                }
                                startActivity(intent)
                            }

                            // Remove from History with 4-second Undo SnackBar
                            val btnRemove = itemView.findViewById<View>(R.id.btn_remove_history)
                            GabAIUtils.addSpringPressEffect(btnRemove) {
                                val docData = doc.data
                                val docRef = doc.reference
                                docRef.delete().addOnSuccessListener {
                                    refreshHistoryList()
                                    GabAIUtils.performHaptic(container, android.view.HapticFeedbackConstants.CLOCK_TICK)
                                    GabAIUtils.showUndoSnackbar(container, "Removed \"$word\" from history", "Undo") {
                                        if (docData != null) {
                                            docRef.set(docData).addOnSuccessListener {
                                                refreshHistoryList()
                                                GabAIUtils.showSnackbar(this, "Restored \"$word\"")
                                            }
                                        }
                                    }
                                }
                            }

                            container.addView(itemView)
                        }
                    }
                    .addOnFailureListener { e ->
                        GabAIUtils.showSnackbar(this, "Failed to load history: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                GabAIUtils.showSnackbar(this, "Failed to load favorites: ${e.message}")
            }
    }
}