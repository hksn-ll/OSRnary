package com.example.gabai

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class HistoryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        // Fix Status Bar Overlap
        val header = findViewById<android.view.View>(R.id.history_header)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 20, v.paddingRight, v.paddingBottom)
            insets
        }

        refreshHistoryList()
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(uid)
                .update("quests_completed", com.google.firebase.firestore.FieldValue.arrayUnion("history"))
        }
    }

    private fun refreshHistoryList() {
        val container = findViewById<LinearLayout>(R.id.history_list_container)
        container.removeAllViews()

        // 1. Get current user UID
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        // 2. Fetch only THIS user's history from Firestore
        db.collection("users").document(uid).collection("history")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    val word = doc.getString("word") ?: ""
                    val content = doc.getString("explanation") ?: ""

                    val itemView = layoutInflater.inflate(R.layout.item_history, container, false)
                    itemView.findViewById<TextView>(R.id.history_text).text = word

                    // Click to View
                    itemView.findViewById<android.view.View>(R.id.history_click_area).setOnClickListener {
                        val intent = android.content.Intent(this, FavoriteDetailActivity::class.java)
                        intent.putExtra("WORD", word)
                        intent.putExtra("CONTENT", content)
                        startActivity(intent)
                    }

                    // Remove from History (Cloud Delete)
                    itemView.findViewById<android.view.View>(R.id.btn_remove_history).setOnClickListener {
                        doc.reference.delete().addOnSuccessListener { refreshHistoryList() }
                    }

                    container.addView(itemView)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load history: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}