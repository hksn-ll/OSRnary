package com.example.gabai

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class HistoryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)


        val header = findViewById<View>(R.id.history_header)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 20, v.paddingRight, v.paddingBottom)
            insets
        }

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .update("quests_completed", com.google.firebase.firestore.FieldValue.arrayUnion("history"))
        }
    }

    // Moved to onResume so it reloads automatically when returning from the detail screen!
    override fun onResume() {
        super.onResume()
        refreshHistoryList()
    }

    private fun refreshHistoryList() {
        val container = findViewById<LinearLayout>(R.id.history_list_container)
        container.removeAllViews()

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        // 1. Fetch Favorites FIRST to sync the stars
        db.collection("users").document(uid).collection("favorites").get().addOnSuccessListener { favSnaps ->
            val favoritedWords = favSnaps.documents.map { it.id }.toMutableSet()

            // 2. Fetch History
            db.collection("users").document(uid).collection("history")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener { documents ->
                    for (doc in documents) {
                        val word = doc.getString("word") ?: ""
                        val content = doc.getString("explanation") ?: ""

                        val itemView = layoutInflater.inflate(R.layout.item_history, container, false)
                        itemView.findViewById<TextView>(R.id.history_text).text = word
                        // Add this right under: itemView.findViewById<TextView>(R.id.history_text).text = word

                        val timestamp = doc.getLong("timestamp") ?: 0L
                        val dateString = java.text.SimpleDateFormat("MMM dd, yyyy • hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
                        itemView.findViewById<TextView>(R.id.history_date).text = dateString

                        // OPEN DETAIL VIEW
                        itemView.findViewById<View>(R.id.history_click_area).setOnClickListener {
                            val intent = android.content.Intent(this, FavoriteDetailActivity::class.java)
                            intent.putExtra("WORD", word)
                            intent.putExtra("CONTENT", content)
                            intent.putExtra("SOURCE", "HISTORY")
                            intent.putExtra("DOC_ID", doc.id)
                            startActivity(intent)
                        }

                        // TOGGLE FAVORITE DIRECTLY FROM LIST
                        val btnFav = itemView.findViewById<ImageButton>(R.id.btn_add_to_fav)
                        var isFavorited = favoritedWords.contains(word)

                        // FIX: Dynamically update the color filter so "off" is gray and "on" is yellow
                        fun updateStarUI(active: Boolean) {
                            btnFav.setImageResource(if (active) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off)
                            btnFav.setColorFilter(android.graphics.Color.parseColor(if (active) "#FDCB6E" else "#B2BEC3"))
                        }

                        updateStarUI(isFavorited)

                        btnFav.setOnClickListener {
                            val favRef = db.collection("users").document(uid).collection("favorites").document(word)
                            if (isFavorited) {
                                favRef.delete().addOnSuccessListener {
                                    isFavorited = false
                                    favoritedWords.remove(word)
                                    updateStarUI(false) // Turn gray
                                    GabAIUtils.showSnackbar(this, "Removed from Favorites")
                                }
                            } else {
                                val favEntry = hashMapOf("word" to word, "definition" to content, "timestamp" to System.currentTimeMillis())
                                favRef.set(favEntry).addOnSuccessListener {
                                    isFavorited = true
                                    favoritedWords.add(word)
                                    updateStarUI(true) // Turn yellow
                                    GabAIUtils.showSnackbar(this, "Saved to Favorites! ⭐")

                                    // 🟢 FIX: Trigger the "Save a Word" quest from History!
                                    db.collection("users").document(uid).update("quests_completed", com.google.firebase.firestore.FieldValue.arrayUnion("save"))
                                }
                            }
                        }

                        // DELETE HISTORY ITEM
                        itemView.findViewById<View>(R.id.btn_remove_history).setOnClickListener {
                            MaterialAlertDialogBuilder(this)
                                .setTitle("Delete History")
                                .setMessage("Are you sure you want to remove '$word' from your history?")
                                .setPositiveButton("Delete") { _, _ ->
                                    doc.reference.delete().addOnSuccessListener {
                                        refreshHistoryList()
                                        GabAIUtils.showSnackbar(this, "Removed '$word'")
                                    }
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }

                        container.addView(itemView)
                    }
                }
        }
    }
}