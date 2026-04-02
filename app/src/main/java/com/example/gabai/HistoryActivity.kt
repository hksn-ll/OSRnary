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
    }

    private fun refreshHistoryList() {
        val container = findViewById<LinearLayout>(R.id.history_list_container)
        container.removeAllViews()

        val historyPrefs = getSharedPreferences("OSRnary_History", MODE_PRIVATE)
        val favPrefs = getSharedPreferences("OSRnary_Favorites", MODE_PRIVATE)

        // Get all keys and filter for the timestamp IDs
        val keys = historyPrefs.all.keys.map { it.substringBefore("_") }.distinct().sortedDescending()

        for (timestamp in keys) {
            val word = historyPrefs.getString("${timestamp}_word", "") ?: ""
            val content = historyPrefs.getString("${timestamp}_content", "") ?: ""
            if (word.isEmpty()) continue

            val itemView = layoutInflater.inflate(R.layout.item_history, container, false)
            itemView.findViewById<TextView>(R.id.history_text).text = word

            // 1. CLICK TO VIEW (Opens detail instantly without using AI)
            itemView.findViewById<android.view.View>(R.id.history_click_area).setOnClickListener {
                val intent = android.content.Intent(this, FavoriteDetailActivity::class.java) // Reusing Detail Activity
                intent.putExtra("WORD", word)
                intent.putExtra("CONTENT", content)
                startActivity(intent)
            }

            // 2. ADD TO FAVORITES
            itemView.findViewById<android.view.View>(R.id.btn_add_to_fav).setOnClickListener {
                favPrefs.edit().putString(word, content).apply()
                Toast.makeText(this, "Added to Favorites!", Toast.LENGTH_SHORT).show()
            }

            // 3. REMOVE FROM HISTORY
            itemView.findViewById<android.view.View>(R.id.btn_remove_history).setOnClickListener {
                historyPrefs.edit().remove("${timestamp}_word").remove("${timestamp}_content").apply()
                refreshHistoryList()
            }

            container.addView(itemView)
        }
    }
}