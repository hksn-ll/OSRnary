package com.example.gabai

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object GabAIUtils {

    private const val TAG = "GabAI_Debug"

    // 1. SNACKBAR
    fun showSnackbar(context: Context?, message: String) {
        if (context == null) return
        var activity: Activity? = context as? Activity
        if (activity == null && context is ContextWrapper) activity = context.baseContext as? Activity
        val rootView = activity?.findViewById<View>(android.R.id.content) ?: return
        val snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_LONG)
        val textView = snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView.maxLines = 10
        snackbar.show()
    }

    // 2. LOADING SPINNER
    fun showGlobalLoading(context: Context?, message: String = "Loading...") {
        if (context == null) return
        var activity: Activity? = context as? Activity
        if (activity == null && context is ContextWrapper) activity = context.baseContext as? Activity
        val rootLayout = activity?.findViewById<ViewGroup>(android.R.id.content) ?: return

        if (rootLayout.findViewWithTag<View>("gabai_global_loader") != null) {
            rootLayout.findViewWithTag<TextView>("gabai_global_loader_text")?.text = message
            return
        }

        val container = FrameLayout(activity!!).apply {
            tag = "gabai_global_loader"
            setBackgroundColor(Color.parseColor("#99000000"))
            isClickable = true
            isFocusable = true
        }

        val inner = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(ProgressBar(activity).apply {
                indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#6C5CE7"))
            })
            addView(TextView(activity).apply {
                tag = "gabai_global_loader_text"
                text = message
                setTextColor(Color.WHITE)
                setPadding(0, 30, 0, 0)
                gravity = Gravity.CENTER
            })
        }
        container.addView(inner, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        rootLayout.addView(container, -1, -1)
    }

    fun hideGlobalLoading(context: Context?) {
        if (context == null) return
        var activity: Activity? = context as? Activity
        if (activity == null && context is ContextWrapper) activity = context.baseContext as? Activity
        val loader = activity?.findViewById<View>(android.R.id.content)?.findViewWithTag<View>("gabai_global_loader")
        if (loader != null) (loader.parent as? ViewGroup)?.removeView(loader)
    }

    // 3. REPORT DIALOG
    fun showReportDialog(context: Context, type: String, referenceText: String = "") {
        val input = EditText(context).apply {
            hint = "Describe the issue..."
            minLines = 3
            gravity = Gravity.TOP
            setPadding(40, 40, 40, 40)
        }
        val layout = LinearLayout(context).apply {
            setPadding(50, 20, 50, 20)
            addView(input, LinearLayout.LayoutParams(-1, -2))
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(if (type == "AI_Error") "Report AI Inaccuracy 🚩" else "Report a Bug 🐛")
            .setView(layout)
            .setPositiveButton("Submit") { _, _ ->
                val desc = input.text.toString().trim()
                if (desc.isNotEmpty()) {
                    showGlobalLoading(context, "Submitting...")
                    val logData = hashMapOf(
                        "reporterId" to (FirebaseAuth.getInstance().currentUser?.uid ?: "Guest"),
                        "reporterName" to "User Report",
                        "type" to type,
                        "description" to desc,
                        "reference" to referenceText,
                        "timestamp" to System.currentTimeMillis(),
                        "status" to "Open"
                    )
                    FirebaseFirestore.getInstance().collection("system_logs").add(logData).addOnSuccessListener {
                        hideGlobalLoading(context)
                        showSnackbar(context, "Report sent to teacher!")
                    }
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    // 4. 🟢 THE CRASH CHECKER (NOW WITH STATUS & DIALOG) 🟢
    fun checkForCrashes(activity: Activity) {
        val prefs = activity.getSharedPreferences("GabAI_Prefs", Context.MODE_PRIVATE)
        val errorLog = prefs.getString("pending_crash_log", null)

        if (errorLog == null) {
            Log.d(TAG, "No pending crash found.")
            return
        }

        Log.d(TAG, "Crash detected! Showing dialog...")

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 30)
        }

        val tvStatus = TextView(activity).apply {
            text = "App recovered from a crash. Details below:"
            textSize = 14f
            setPadding(0, 0, 0, 20)
        }

        val scroll = ScrollView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 500)
            setBackgroundColor(Color.parseColor("#F1F2F6"))
            addView(TextView(activity).apply {
                text = errorLog
                textSize = 10f
                setPadding(20, 20, 20, 20)
                typeface = Typeface.MONOSPACE
                setTextColor(Color.RED)
            })
        }

        container.addView(tvStatus)
        container.addView(scroll)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle("Diagnostic Report 🤖")
            .setView(container)
            .setCancelable(false)
            .setPositiveButton("Send to Dev", null) // Set null to handle manually
            .setNegativeButton("Dismiss") { _, _ ->
                prefs.edit().remove("pending_crash_log").apply()
            }
            .create()

        dialog.show()

        // Handle the "Send" button without closing the dialog immediately
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) {
                Toast.makeText(activity, "Login first to send reports!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                return@setOnClickListener
            }

            tvStatus.text = "⏳ Sending to developer..."
            tvStatus.setTextColor(Color.BLUE)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false

            val data = hashMapOf(
                "reporterId" to user.uid,
                "reporterName" to "Auto-Crash Reporter",
                "type" to "Crash",
                "description" to "Fatal Exception intercepted",
                "reference" to errorLog,
                "timestamp" to System.currentTimeMillis(),
                "status" to "Forwarded"
            )

            FirebaseFirestore.getInstance().collection("system_logs").add(data)
                .addOnSuccessListener {
                    prefs.edit().remove("pending_crash_log").apply()
                    tvStatus.text = "✅ Crash reported! Thank you."
                    tvStatus.setTextColor(Color.parseColor("#27AE60"))
                    Handler(Looper.getMainLooper()).postDelayed({ dialog.dismiss() }, 1500)
                }
                .addOnFailureListener {
                    tvStatus.text = "❌ Failed: ${it.message}"
                    tvStatus.setTextColor(Color.RED)
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                }
        }
    }
}