package com.example.gabai

import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class QuestDetailsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quest_details)

        // Fix Status Bar overlap for the dark header
        val header = findViewById<View>(R.id.quest_header)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 30, v.paddingRight, v.paddingBottom)
            insets
        }

        val btnAction = findViewById<Button>(R.id.btn_action_read)
        val btnClose = findViewById<Button>(R.id.btn_close_details)

        btnClose.setOnClickListener { finish() }

        // Fetch live quest data to configure the UI
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val completedQuests = doc.get("quests_completed") as? List<String> ?: listOf()

                // 1. UPDATE THE INDIVIDUAL CARDS (ALL 8 PERFECTLY MAPPED)
                updateQuestCardState(completedQuests.contains("bubble"), R.id.bg_bubble, R.id.icon_bubble, R.id.title_bubble)
                updateQuestCardState(completedQuests.contains("read"), R.id.bg_read, R.id.icon_read, R.id.title_read)
                updateQuestCardState(completedQuests.contains("test"), R.id.bg_test, R.id.icon_test, R.id.title_test)
                updateQuestCardState(completedQuests.contains("scan"), R.id.bg_scan, R.id.icon_scan, R.id.title_scan)
                updateQuestCardState(completedQuests.contains("save"), R.id.bg_save, R.id.icon_save, R.id.title_save)
                updateQuestCardState(completedQuests.contains("history"), R.id.bg_history, R.id.icon_history, R.id.title_history)
                updateQuestCardState(completedQuests.contains("detail"), R.id.bg_detail, R.id.icon_detail, R.id.title_detail)
                updateQuestCardState(completedQuests.contains("library"), R.id.bg_library, R.id.icon_library, R.id.title_library)

                // 2. UPDATE THE MAIN ACTION BUTTON
                val hasTest = completedQuests.contains("test")
                val hasBubble = completedQuests.contains("bubble")

                if (hasTest) {
                    btnAction.text = "Initiation Test Completed! ✅"
                    btnAction.setBackgroundColor(Color.parseColor("#00B894")) // Mint Green Success
                    btnAction.isEnabled = false
                } else {
                    btnAction.text = "Embark on the Trials 📜"
                    btnAction.setBackgroundColor(Color.parseColor("#6C5CE7")) // GabAI Purple
                    btnAction.isEnabled = true

                    btnAction.setOnClickListener {
                        // Check if the bubble is ACTUALLY turned on right now
                        val isBubbleActive = getSharedPreferences("GabAI_Prefs", MODE_PRIVATE)
                            .getBoolean("bubble_enabled", false)

                        if (isBubbleActive) {
                            startActivity(Intent(this, InitiationActivity::class.java))
                            finish()
                        } else {
                            showCompanionRequiredDialog()
                        }
                    }
                }
            }
    }
    private fun showCompanionRequiredDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Companion Required 🧚‍♂️")
            .setMessage("You cannot face the reading trials alone! \n\nPlease return to your dashboard and activate the GabAI Floating Bubble. Your AI guide is essential for deciphering these texts.")
            .setPositiveButton("Understood") { dialog, _ ->
                dialog.dismiss()
                finish() // Automatically takes them back to the dashboard to turn it on!
            }
            .show()
    }

    // Helper function to dynamically change the Material 3 colors based on completion
    private fun updateQuestCardState(isCompleted: Boolean, bgId: Int, iconId: Int, titleId: Int) {
        val bgLayout = findViewById<LinearLayout>(bgId)
        val icon = findViewById<TextView>(iconId)
        val title = findViewById<TextView>(titleId)

        if (isCompleted) {
            // SUCCESS STATE: Soft mint background, Green text, Strikethrough, Checkmark Emoji
            bgLayout.setBackgroundColor(Color.parseColor("#E8F8F5"))
            title.setTextColor(Color.parseColor("#00B894"))
            icon.text = "✅"
            title.paintFlags = title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            // PENDING STATE: Pure white surface, Dark text, Pending Emoji
            bgLayout.setBackgroundColor(Color.parseColor("#FFFFFF"))
            title.setTextColor(Color.parseColor("#2D3436"))
            icon.text = "⏳" // Hourglass to signify it's waiting for them
            title.paintFlags = title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
    }
}