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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import io.noties.markwon.Markwon

class FavoritesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GabAIUtils.applyHardwareMaxRefreshRate(this)
        setContentView(R.layout.activity_favorites)

        // Fix Status Bar Overlap
        val favHeader = findViewById<View>(R.id.fav_header)
        ViewCompat.setOnApplyWindowInsetsListener(favHeader) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 12, v.paddingRight, v.paddingBottom)
            insets
        }

        GabAIUtils.applyFrostedGlass(favHeader)
        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        btnBack?.setOnClickListener { finish() }
        GabAIUtils.addSpringPressEffect(btnBack) { finish() }

        refreshFavoritesList()
    }

    override fun onResume() {
        super.onResume()
        refreshFavoritesList()
    }

    private fun refreshFavoritesList() {
        val container = findViewById<LinearLayout>(R.id.favorites_list_container)
        val loadingContainer = findViewById<View>(R.id.fav_loading_container)
        container.removeAllViews()
        loadingContainer?.visibility = View.VISIBLE

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            loadingContainer?.visibility = View.GONE
            return
        }
        val db = FirebaseFirestore.getInstance()
        val markwon = Markwon.create(this)

        db.collection("users").document(uid).collection("favorites")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                loadingContainer?.visibility = View.GONE
                if (documents.isEmpty) {
                    val emptyView = layoutInflater.inflate(R.layout.view_empty_state, container, false)
                    emptyView.findViewById<ImageView>(R.id.iv_empty_icon).setImageResource(R.drawable.ic_star_filled)
                    emptyView.findViewById<TextView>(R.id.tv_empty_title).text = "No Saved Favorites"
                    emptyView.findViewById<TextView>(R.id.tv_empty_subtitle).text =
                        "Tap the star icon while reading to save important words and definitions here."
                    container.addView(emptyView)
                    return@addOnSuccessListener
                }

                for (doc in documents) {
                    val word = doc.getString("word") ?: ""
                    val definition = doc.getString("definition") ?: ""
                    val context = doc.getString("originalContext") ?: word
                    val partOfSpeech = doc.getString("partOfSpeech") ?: ""
                    val phonetics = doc.getString("phonetics") ?: ""
                    val inSentenceRole = doc.getString("inSentenceRole") ?: ""

                    val itemView = layoutInflater.inflate(R.layout.item_favorite, container, false)
                    val titleView = itemView.findViewById<TextView>(R.id.fav_title)
                    val contentView = itemView.findViewById<TextView>(R.id.fav_content)
                    val posBadge = itemView.findViewById<TextView>(R.id.fav_pos_badge)
                    val roleContainer = itemView.findViewById<View>(R.id.fav_role_container)
                    val roleView = itemView.findViewById<TextView>(R.id.fav_in_sentence_role)

                    titleView.text = word
                    markwon.setMarkdown(contentView, definition)

                    // Bind Part of Speech badge
                    if (partOfSpeech.isNotBlank()) {
                        posBadge.text = partOfSpeech.uppercase()
                        posBadge.visibility = View.VISIBLE
                    } else {
                        posBadge.visibility = View.GONE
                    }

                    // Bind In-Sentence Role snippet if available (hide if all selected)
                    val isAllSelected = word.trim().equals(context.trim(), ignoreCase = true) || word.split(Regex("\\s+")).size > 6
                    if (!isAllSelected && inSentenceRole.isNotBlank()) {
                        roleView.text = inSentenceRole
                        roleContainer.visibility = View.VISIBLE
                    } else {
                        roleContainer.visibility = View.GONE
                    }

                    // Open Full Overview with Audio, Diagrams, and Preloaded Explanation
                    val clickArea = itemView.findViewById<View>(R.id.item_click_area)
                    GabAIUtils.addSpringPressEffect(clickArea) {
                        val intent = Intent(this, OverviewActivity::class.java).apply {
                            putExtra("SELECTED_TEXT", word)
                            putExtra("SURROUNDING_SENTENCE", context)
                            putExtra("PRELOADED_EXPLANATION", definition)
                            putExtra("PART_OF_SPEECH", partOfSpeech)
                            putExtra("PHONETICS", phonetics)
                            putExtra("IN_SENTENCE_ROLE", inSentenceRole)
                            putExtra("IS_FAVORITE", true)
                        }
                        startActivity(intent)
                    }

                    // Remove with 4-second Undo SnackBar
                    val btnRemove = itemView.findViewById<View>(R.id.btn_remove_fav)
                    GabAIUtils.addSpringPressEffect(btnRemove) {
                        val docData = doc.data
                        val docRef = doc.reference
                        docRef.delete().addOnSuccessListener {
                            refreshFavoritesList()
                            GabAIUtils.performHaptic(container, android.view.HapticFeedbackConstants.CLOCK_TICK)
                            GabAIUtils.showUndoSnackbar(container, "Removed \"$word\"", "Undo") {
                                if (docData != null) {
                                    docRef.set(docData).addOnSuccessListener {
                                        refreshFavoritesList()
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
                loadingContainer?.visibility = View.GONE
                GabAIUtils.showSnackbar(this, "Error loading favorites: ${e.message}")
            }
    }
}