package com.example.gabai

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.noties.markwon.Markwon

class FavoriteDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Reuse your Overview layout!
        setContentView(R.layout.activity_overview)

        val word = intent.getStringExtra("WORD") ?: ""
        val content = intent.getStringExtra("CONTENT") ?: ""

        // 1. Hide things not needed for a static favorite
        findViewById<android.view.View>(R.id.loading_container).visibility = android.view.View.GONE
        findViewById<android.view.View>(R.id.btn_favorite).visibility = android.view.View.GONE
        findViewById<android.view.View>(R.id.result_container).visibility = android.view.View.VISIBLE

        // 2. Set the data
        findViewById<TextView>(R.id.selected_text_view).text = "\"$word\""
        Markwon.create(this).setMarkdown(findViewById(R.id.ai_result_text), content)
        // --- QUEST TRIGGER: VIEW SAVED CONTENT ---
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(uid)
                .update("quests_completed", com.google.firebase.firestore.FieldValue.arrayUnion("detail"))
        }
    }
}