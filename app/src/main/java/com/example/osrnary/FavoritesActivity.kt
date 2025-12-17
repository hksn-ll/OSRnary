package com.example.osrnary

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.noties.markwon.Markwon

class FavoritesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)

        // 1. Fix Status Bar Overlap (applied to header)
        val favHeader = findViewById<android.view.View>(R.id.fav_header)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(favHeader) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 20, v.paddingRight, v.paddingBottom)
            insets

        }


        // 2. Only call this function. Do NOT add a loop here.
        refreshFavoritesList()
    }

    private fun refreshFavoritesList() {
        val container = findViewById<LinearLayout>(R.id.favorites_list_container)
        container.removeAllViews() // Prevents duplicates

        val sharedPrefs = getSharedPreferences("OSRnary_Favorites", MODE_PRIVATE)
        val allEntries = sharedPrefs.all
        val markwon = Markwon.create(this)

        if (allEntries.isEmpty()) {
            val emptyMsg = TextView(this).apply {
                text = "No favorites saved yet!"
                setPadding(40, 40, 40, 40)
            }
            container.addView(emptyMsg)
            return
        }

        for ((word, definition) in allEntries) {
            val itemView = layoutInflater.inflate(R.layout.item_favorite, container, false)

            val titleView = itemView.findViewById<TextView>(R.id.fav_title)
            val contentView = itemView.findViewById<TextView>(R.id.fav_content)

            titleView.text = word
            markwon.setMarkdown(contentView, definition.toString())

            // THIS IS THE PROCEED LOGIC
            itemView.findViewById<android.view.View>(R.id.item_click_area).setOnClickListener {
                val intent = android.content.Intent(this, FavoriteDetailActivity::class.java)
                intent.putExtra("WORD", word)
                intent.putExtra("CONTENT", definition.toString())
                startActivity(intent)
            }

            // REMOVE OPTION
            itemView.findViewById<android.view.View>(R.id.btn_remove_fav).setOnClickListener {
                sharedPrefs.edit().remove(word).apply()
                refreshFavoritesList()
                Toast.makeText(this, "Removed $word", Toast.LENGTH_SHORT).show()
            }

            container.addView(itemView)
        }
    }
}