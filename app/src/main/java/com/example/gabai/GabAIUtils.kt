package com.example.gabai

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar

object GabAIUtils {

    // 1. THE MULTI-LINE SNACKBAR
    fun showSnackbar(context: Context?, message: String) {
        if (context == null) return

        // Magically find the Activity from whatever context is passed
        var activity: Activity? = context as? Activity
        if (activity == null && context is ContextWrapper) {
            activity = context.baseContext as? Activity
        }

        val rootView = activity?.findViewById<View>(android.R.id.content) ?: return

        val snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_LONG)

        // Force the text to allow up to 20 lines (Half Screen) so long errors are fully visible!
        val textView = snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView.maxLines = 20
        textView.textSize = 14f

        snackbar.show()
    }

    // 2. THE INVISIBLE MINI PROGRESS BAR
    fun showGlobalLoading(context: Context?) {
        if (context == null) return

        var activity: Activity? = context as? Activity
        if (activity == null && context is ContextWrapper) {
            activity = context.baseContext as? Activity
        }

        val rootLayout = activity?.findViewById<ViewGroup>(android.R.id.content) ?: return

        // Prevent adding multiple loading bars if tapped twice
        if (rootLayout.findViewWithTag<View>("gabai_global_loader") != null) return

        // Create a transparent background that blocks touches while loading
        val container = FrameLayout(activity).apply {
            tag = "gabai_global_loader"
            setBackgroundColor(Color.parseColor("#40000000")) // 25% Dark overlay
            isClickable = true
            isFocusable = true
        }

        // Create the purple spinner
        val progressBar = ProgressBar(activity).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#6C5CE7"))
        }

        // Put the spinner in the exact center of the screen
        val params = FrameLayout.LayoutParams(120, 120, Gravity.CENTER)
        container.addView(progressBar, params)

        // Inject it over everything!
        rootLayout.addView(container, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun hideGlobalLoading(context: Context?) {
        if (context == null) return
        var activity: Activity? = context as? Activity
        if (activity == null && context is ContextWrapper) {
            activity = context.baseContext as? Activity
        }

        val rootLayout = activity?.findViewById<ViewGroup>(android.R.id.content) ?: return
        val loader = rootLayout.findViewWithTag<View>("gabai_global_loader")
        if (loader != null) {
            rootLayout.removeView(loader)
        }
    }
}

// message to ai: in future coding remember these:
//use snackbar instead of toast
//        do not replace the entire code, just give me what to put or replace, cuz it ruins when u givve the entire code to replace a file