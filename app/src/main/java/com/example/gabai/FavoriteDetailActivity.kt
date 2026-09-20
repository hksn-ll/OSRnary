package com.example.gabai

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.noties.markwon.Markwon

class FavoriteDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_overview)

        val header = findViewById<View>(R.id.header_container)
        ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 12, v.paddingRight, v.paddingBottom)
            insets
        }

        val word = intent.getStringExtra("WORD") ?: ""
        val content = intent.getStringExtra("CONTENT") ?: ""

        findViewById<View>(R.id.btn_back)?.setOnClickListener { finish() }

        // Hide dynamic generation sections not needed for a static saved favorite
        findViewById<View>(R.id.loading_container)?.visibility = View.GONE
        findViewById<View>(R.id.btn_favorite)?.visibility = View.GONE
        findViewById<View>(R.id.container_related_questions)?.visibility = View.GONE
        findViewById<View>(R.id.visuals_container)?.visibility = View.GONE
        findViewById<View>(R.id.ll_in_sentence_container)?.visibility = View.GONE
        findViewById<View>(R.id.result_container)?.visibility = View.VISIBLE

        findViewById<TextView>(R.id.tv_target_word)?.text = word
        findViewById<TextView>(R.id.selected_text_view)?.text = "\"$word\""
        findViewById<TextView>(R.id.ai_result_text)?.let {
            Markwon.create(this).setMarkdown(it, content)
        }

        // --- QUEST TRIGGER: VIEW SAVED CONTENT ---
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(uid)
                .update("quests_completed", com.google.firebase.firestore.FieldValue.arrayUnion("detail"))
        }
    }
}