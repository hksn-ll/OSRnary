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
import io.noties.markwon.Markwon

class FavoritesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)

        val favHeader = findViewById<View>(R.id.fav_header)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(favHeader) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 20, v.paddingRight, v.paddingBottom)
            insets
        }

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
    }

    // Moved to onResume so it updates automatically when returning!
    override fun onResume() {
        super.onResume()
        refreshFavoritesList()
    }

    private fun refreshFavoritesList() {
        val container = findViewById<LinearLayout>(R.id.favorites_list_container)
        container.removeAllViews()

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val markwon = Markwon.create(this)

        db.collection("users").document(uid).collection("favorites")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    val emptyMsg = TextView(this).apply {
                        text = "No favorites saved yet! Tap the star icon when scanning a word to save it here."
                        setPadding(40, 40, 40, 40)
                    }
                    container.addView(emptyMsg)
                    return@addOnSuccessListener
                }

                for (doc in documents) {
                    val word = doc.getString("word") ?: ""
                    val definition = doc.getString("definition") ?: ""

                    // FIX: Extract and format the timestamp
                    val timestamp = doc.getLong("timestamp") ?: 0L
                    val dateString = java.text.SimpleDateFormat("MMM dd, yyyy • hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(timestamp))

                    val itemView = layoutInflater.inflate(R.layout.item_favorite, container, false)
                    val titleView = itemView.findViewById<TextView>(R.id.fav_title)
                    val dateView = itemView.findViewById<TextView>(R.id.fav_date) // Get the new TextView
                    val contentView = itemView.findViewById<TextView>(R.id.fav_content)

                    titleView.text = word
                    dateView.text = dateString // Set the formatted date
                    markwon.setMarkdown(contentView, definition)

                    // OPEN DETAIL
                    itemView.findViewById<View>(R.id.item_click_area).setOnClickListener {
                        val intent = android.content.Intent(this, FavoriteDetailActivity::class.java)
                        intent.putExtra("WORD", word)
                        intent.putExtra("CONTENT", definition)
                        intent.putExtra("SOURCE", "FAVORITES")
                        intent.putExtra("DOC_ID", doc.id)
                        startActivity(intent)
                    }

                    // DELETE WITH CONFIRMATION
                    itemView.findViewById<View>(R.id.btn_remove_fav).setOnClickListener {
                        MaterialAlertDialogBuilder(this)
                            .setTitle("Delete Favorite")
                            .setMessage("Are you sure you want to remove '$word' from your favorites?")
                            .setPositiveButton("Delete") { _, _ ->
                                doc.reference.delete().addOnSuccessListener {
                                    refreshFavoritesList()
                                    GabAIUtils.showSnackbar(this, "Removed '$word'")
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                    container.addView(itemView)
                }
            }
            .addOnFailureListener { e ->
                GabAIUtils.showSnackbar(this, "Error loading favorites: ${e.message}")
            }
    }
}