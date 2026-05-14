package com.example.gabai

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class SystemLogsActivity : AppCompatActivity() {

    // 🟢 Keep track of which reports haven't been resolved or forwarded yet
    private var openLogs = mutableListOf<DocumentSnapshot>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_system_logs)

        val header = findViewById<View>(R.id.logs_header)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 20, v.paddingRight, v.paddingBottom)
            insets
        }
// 🟢 TEST CRASH LOGIC 🟢
        findViewById<Button>(R.id.btn_test_crash).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Trigger Crash?")
                .setMessage("This will force the app to fail and test your new Global Exception Handler. The app should instantly restart.")
                .setPositiveButton("Crash App") { _, _ ->
                    throw RuntimeException("Manual Test Crash triggered by Developer.")
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        // 🟢 FORWARD TO DEVELOPER LOGIC 🟢
        findViewById<Button>(R.id.btn_forward_dev).setOnClickListener {
            if (openLogs.isEmpty()) {
                GabAIUtils.showSnackbar(this, "No open reports to forward right now!")
                return@setOnClickListener
            }

            MaterialAlertDialogBuilder(this)
                .setTitle("Forward to Developer?")
                .setMessage("Send all ${openLogs.size} open student reports to the GabAI developer team for review?")
                .setPositiveButton("Send All") { _, _ ->
                    forwardLogsToDeveloper()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        loadLogs()
    }

    private fun forwardLogsToDeveloper() {
        GabAIUtils.showGlobalLoading(this, "Forwarding to Dev...")
        var completed = 0
        val total = openLogs.size

        for (doc in openLogs) {
            // Change the status so we know the developer has it now!
            doc.reference.update("status", "Forwarded").addOnSuccessListener {
                completed++
                if (completed == total) {
                    GabAIUtils.hideGlobalLoading(this)
                    GabAIUtils.showSnackbar(this, "Successfully forwarded $total reports to the developer! 🚀")
                }
            }
        }
    }

    private fun loadLogs() {
        val container = findViewById<LinearLayout>(R.id.logs_container)
        val db = FirebaseFirestore.getInstance()

        GabAIUtils.showGlobalLoading(this, "Loading Logs...")

        db.collection("system_logs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                GabAIUtils.hideGlobalLoading(this)
                if (error != null || snapshots == null) return@addSnapshotListener

                container.removeAllViews()
                openLogs.clear() // Reset the list every time the database updates

                if (snapshots.isEmpty) {
                    container.addView(TextView(this).apply {
                        text = "No bug reports or feedback found. System is healthy! ✅"
                        setPadding(40, 40, 40, 40)
                        gravity = android.view.Gravity.CENTER
                    })
                    return@addSnapshotListener
                }

                for (doc in snapshots) {
                    val type = doc.getString("type") ?: "Bug"
                    val reporter = doc.getString("reporterName") ?: "Unknown"
                    val desc = doc.getString("description") ?: ""
                    val ref = doc.getString("reference") ?: ""
                    val status = doc.getString("status") ?: "Open"
                    val timestamp = doc.getLong("timestamp") ?: 0L
                    val dateString = java.text.SimpleDateFormat("MMM dd, yyyy • hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(timestamp))

                    // If it's still open, add it to our forwarding list
                    if (status == "Open") {
                        openLogs.add(doc)
                    }

                    val view = layoutInflater.inflate(R.layout.item_system_log, container, false)

                    val tvTitle = view.findViewById<TextView>(R.id.log_title)
                    val tvReporter = view.findViewById<TextView>(R.id.log_reporter)
                    val tvDesc = view.findViewById<TextView>(R.id.log_description)
                    val btnResolve = view.findViewById<TextView>(R.id.btn_resolve_log)

                    tvTitle.text = if (type == "AI_Error") "🚩 AI Inaccuracy" else "🐛 System Bug"
                    tvReporter.text = "Reported by: $reporter • $dateString"

                    val fullDesc = if (ref.isNotEmpty()) "Context: $ref\n\nIssue: $desc" else desc
                    tvDesc.text = fullDesc

                    // 🟢 Handle the UI based on the new Statuses 🟢
                    when (status) {
                        "Resolved" -> {
                            btnResolve.text = "RESOLVED ✅"
                            btnResolve.setTextColor(Color.parseColor("#00B894"))
                            btnResolve.isEnabled = false
                        }
                        "Forwarded" -> {
                            btnResolve.text = "SENT TO DEV 🚀"
                            btnResolve.setTextColor(Color.parseColor("#0984E3"))
                            btnResolve.isEnabled = false
                        }
                        else -> {
                            // It's still "Open", let the teacher manually resolve it if they fixed it themselves
                            btnResolve.setOnClickListener {
                                MaterialAlertDialogBuilder(this@SystemLogsActivity)
                                    .setTitle("Mark as Resolved?")
                                    .setMessage("Has this issue been reviewed and addressed?")
                                    .setPositiveButton("Yes") { _, _ ->
                                        doc.reference.update("status", "Resolved")
                                    }
                                    .setNegativeButton("Cancel", null)
                                    .show()
                            }
                        }
                    }

                    container.addView(view)
                }

                // Update the Forward button text so the teacher knows how many they are sending
                findViewById<Button>(R.id.btn_forward_dev).text = "Forward ${openLogs.size} Open Reports to Dev 🚀"
            }
    }
}