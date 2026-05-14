package com.example.gabai

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import io.noties.markwon.Markwon
import java.util.Locale

class FavoriteDetailActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private var isFavorited = false
    private lateinit var tts: TextToSpeech
    private var wordToSpeak = ""
    private var explanationToSpeak = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_overview)

        tts = TextToSpeech(this, this)

        wordToSpeak = intent.getStringExtra("WORD") ?: ""
        explanationToSpeak = intent.getStringExtra("CONTENT") ?: ""
        val source = intent.getStringExtra("SOURCE") ?: "HISTORY"
        val docId = intent.getStringExtra("DOC_ID") ?: ""

        // Adjust UI
        findViewById<ImageButton>(R.id.btn_report_ai).setOnClickListener {
            val word = intent.getStringExtra("WORD") ?: "Unknown Word"
            GabAIUtils.showReportDialog(this, "AI_Error", "Flagged Concept: $word")
        }
        findViewById<View>(R.id.loading_container).visibility = View.GONE
        findViewById<View>(R.id.result_container).visibility = View.VISIBLE

        // NO LONGER HIDING TTS BUTTONS!

        findViewById<TextView>(R.id.selected_text_view).text = "\"$wordToSpeak\""
        Markwon.create(this).setMarkdown(findViewById(R.id.ai_result_text), explanationToSpeak)

        // Setup TTS Clicks
        findViewById<ImageButton>(R.id.btn_speak_selected).setOnClickListener {
            tts.speak(wordToSpeak, TextToSpeech.QUEUE_FLUSH, null, null)
        }
        findViewById<ImageButton>(R.id.btn_speak_explanation).setOnClickListener {
            tts.speak(explanationToSpeak, TextToSpeech.QUEUE_FLUSH, null, null)
        }

        val db = FirebaseFirestore.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("users").document(uid)
            .update("quests_completed", com.google.firebase.firestore.FieldValue.arrayUnion("detail"))
        val btnFav = findViewById<ImageButton>(R.id.btn_favorite)
        val btnDelete = findViewById<ImageButton>(R.id.btn_delete)

        btnDelete.visibility = View.VISIBLE
        btnDelete.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Delete Saved Item")
                .setMessage("Are you sure you want to permanently delete '$wordToSpeak'?")
                .setPositiveButton("Delete") { _, _ ->
                    val collection = if (source == "FAVORITES") "favorites" else "history"
                    db.collection("users").document(uid).collection(collection).document(docId).delete()
                        .addOnSuccessListener {
                            GabAIUtils.showSnackbar(this, "Deleted successfully")
                            finish()
                        }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        val favRef = db.collection("users").document(uid).collection("favorites").document(wordToSpeak)
        favRef.get().addOnSuccessListener { doc ->
            isFavorited = doc.exists()
            btnFav.setImageResource(if (isFavorited) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off)
        }

        btnFav.setOnClickListener {
            if (isFavorited) {
                favRef.delete().addOnSuccessListener {
                    isFavorited = false
                    btnFav.setImageResource(android.R.drawable.btn_star_big_off)
                    GabAIUtils.showSnackbar(this, "Removed from Favorites")
                }
            } else {
                val favEntry = hashMapOf("word" to wordToSpeak, "definition" to explanationToSpeak, "timestamp" to System.currentTimeMillis())
                favRef.set(favEntry).addOnSuccessListener {
                    isFavorited = true
                    btnFav.setImageResource(android.R.drawable.btn_star_big_on)
                    GabAIUtils.showSnackbar(this, "Saved to Favorites! ⭐")
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}