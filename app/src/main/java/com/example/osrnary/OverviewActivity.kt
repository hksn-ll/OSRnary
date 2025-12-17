package com.example.osrnary

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.ai.client.generativeai.GenerativeModel
import io.noties.markwon.Markwon
import kotlinx.coroutines.launch
import android.webkit.WebView
import android.webkit.WebViewClient
class OverviewActivity : AppCompatActivity() {

    private var lastAiResult: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_overview)
        android.webkit.WebView.setWebContentsDebuggingEnabled(true)
        // FIX: Handle Window Insets for the new layout
        val header = findViewById<View>(R.id.header_container)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val systemBars =
                insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 24, v.paddingRight, v.paddingBottom)
            insets
        }

        val scannedText = intent.getStringExtra("SELECTED_TEXT") ?: ""

// Show the selected text immediately
        val selectedTextView = findViewById<TextView>(R.id.selected_text_view)
        selectedTextView.text = "\"" + scannedText + "\""



        if (scannedText.isNotEmpty()) {
            // Only call each function ONCE
            generateAIOverview(scannedText)

            val imageWebView = findViewById<WebView>(R.id.image_webview)
            loadGoogleImages(imageWebView, scannedText)
        } else {
            Toast.makeText(this, "No text provided", Toast.LENGTH_SHORT).show()
        }
        val favoriteBtn = findViewById<android.widget.ImageButton>(R.id.btn_favorite)
        favoriteBtn.setOnClickListener {
            if (lastAiResult.isNotEmpty()) {
                saveToFavorites(scannedText, lastAiResult)
                favoriteBtn.setImageResource(android.R.drawable.btn_star_big_on)
                Toast.makeText(this, "Saved to Favorites!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val intent = android.content.Intent(this, FloatingControlService::class.java)
        intent.action = "ACTION_HIDE"
        startService(intent)
    }

    override fun onPause() {
        super.onPause()
        val intent = android.content.Intent(this, FloatingControlService::class.java)
        intent.action = "ACTION_SHOW"
        startService(intent)
    }

    private fun loadGoogleImages(webView: android.webkit.WebView, query: String) {
        val visualsContainer = findViewById<View>(R.id.visuals_container)
        visualsContainer.visibility = View.VISIBLE

        // 1. Settings to fix the blank screen and allow scrolling
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; Pixel 4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.101 Mobile Safari/537.36"

        // 2. THIS FIXES THE SCROLLING: Stop the parent ScrollView from stealing touches
        webView.setOnTouchListener { v, event ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            false
        }

        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                super.onPageFinished(view, url)

                // 3. Updated JavaScript with your new classes
                view?.evaluateJavascript("""
                (function() {
                    function hide(selector) {
                        var elements = document.querySelectorAll(selector);
                        for (var i = 0; i < elements.length; i++) {
                            elements[i].style.display = 'none';
                        }
                    }
                    
                    // Hiding your specific classes
                    hide('.eK9Ieb'); 
                    hide('.dmFHw'); 

                    // Keep interaction blocked but allow scrolling
                    document.addEventListener('click', function(e) {
                        e.stopImmediatePropagation();
                        e.preventDefault();
                        return false;
                    }, true);
                })();
            """.trimIndent(), null)
            }

            override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                return true
            }
        }

        val searchUrl = "https://www.google.com/search?tbm=isch&q=${query}"
        webView.loadUrl(searchUrl)
    }

    private fun generateAIOverview(inputText: String) {
        val loadingContainer = findViewById<LinearLayout>(R.id.loading_container)
        val resultContainer = findViewById<LinearLayout>(R.id.result_container)
        val resultTextView = findViewById<TextView>(R.id.ai_result_text)

        // Initialize Markdown
        val markwon = Markwon.create(this)

        // Configure Gemini
        val generativeModel = GenerativeModel(
            modelName = "gemini-2.5-flash-lite",
            apiKey = "AIzaSyB8_5UwKb9B8T0HriQj87as0q-1Z1eIPjA" // <--- CHECK YOUR API KEY!
        )

        lifecycleScope.launch {
            try {
                // Your Advanced Prompt
                val prompt = """
                    You are an AI Tutor for a student app called OSRnary.
                    Analyze the following input text: "$inputText"

                    **INSTRUCTIONS:**
                    **CASE 1: If it is a definable single word:**
                    - Provide a **Dictionary Layout** first.
                    - Format:
                      > **Word** (Pronunciation) - *Part of Speech*
                      > Definition: [Simple definition]
                    - Follow immediately with a **Context Overview**.

                    **CASE 2: If it is a phrase, proper noun, event, or topic:**
                    - SKIP the dictionary layout.
                    - Provide ONLY the **Context Overview**.

                    **CONTENT REQUIREMENTS:**
                    - **If Non-English:** State Language, Provide English Translation.
                    - **Prioritize Filipino when there is a word that is non english
                    - **If English:** Provide standard definitions and context.

                    **FORMATTING RULES:**
                    - Use **Bold** for headers.
                    - Use > Blockquotes for definitions.
                    - Keep it concise.
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)

                // Update UI
                loadingContainer.visibility = View.GONE
                resultContainer.visibility = View.VISIBLE

                response.text?.let {
                    lastAiResult = it
                    markwon.setMarkdown(resultTextView, it)
                    saveToHistory(inputText, it)
                } ?: run {
                    resultTextView.text = "Sorry, no result generated."
                }

            } catch (e: Exception) {
                loadingContainer.visibility = View.GONE
                resultTextView.text = "Connection Error: ${e.localizedMessage}"
                resultContainer.visibility = View.VISIBLE
            }
        }
    }
    private fun saveToFavorites(word: String, definition: String) {
        val leveledUp = XPManager.addXP(this, 10)
        if (leveledUp) {
            Toast.makeText(this, "LEVEL UP! You are now Level ${XPManager.getLevel(this)}! 🎉", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Saved! +10 XP gained", Toast.LENGTH_SHORT).show()
        }// +10 XP for saving a word
        val sharedPrefs = getSharedPreferences("OSRnary_Favorites", android.content.Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()
        // Saves the exact definition using the word as the key
        editor.putString(word, definition)
        editor.apply()
    }
    private fun saveToHistory(text: String, aiResult: String) {
        val sharedPrefs = getSharedPreferences("OSRnary_History", android.content.Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()
        val timestamp = System.currentTimeMillis().toString()

        // Save both the word and the exact AI explanation
        editor.putString("${timestamp}_word", text)
        editor.putString("${timestamp}_content", aiResult)
        editor.apply()
    }
}

//
//    // 1. Add this helper class at the bottom of OverviewActivity.kt
//    inner class PuterJavaScriptInterface(private val markwon: io.noties.markwon.Markwon) {
//        @android.webkit.JavascriptInterface
//        fun onResult(aiResponse: String) {
//            runOnUiThread {
//                val loadingContainer = findViewById<android.widget.LinearLayout>(R.id.loading_container)
//                val resultContainer = findViewById<android.widget.LinearLayout>(R.id.result_container)
//                val resultTextView = findViewById<android.widget.TextView>(R.id.ai_result_text)
//
//                loadingContainer.visibility = android.view.View.GONE
//                resultContainer.visibility = android.view.View.VISIBLE
//                markwon.setMarkdown(resultTextView, aiResponse)
//            }
//        }
//
//        @android.webkit.JavascriptInterface
//        fun onError(error: String) {
//            runOnUiThread {
//                android.widget.Toast.makeText(this@OverviewActivity, "AI Error: $error", android.widget.Toast.LENGTH_LONG).show()
//            }
//        }
//    }
//
//    // 2. Update the generateAIOverview function
//    private fun generateAIOverview(inputText: String) {
//        val puterWebView = findViewById<android.webkit.WebView>(R.id.puter_webview)
//        val markwon = io.noties.markwon.Markwon.create(this)
//
//        puterWebView.settings.javaScriptEnabled = true
//        puterWebView.settings.domStorageEnabled = true
//        puterWebView.addJavascriptInterface(PuterJavaScriptInterface(markwon), "AndroidInterface")
//        // Settings for Puter.js and Debugging
//        puterWebView.settings.javaScriptEnabled = true
//        puterWebView.settings.domStorageEnabled = true
//        puterWebView.settings.useWideViewPort = true
//        puterWebView.settings.loadWithOverviewMode = true
//
//        // Optional: Enable zooming while debugging
//        puterWebView.settings.builtInZoomControls = true
//        puterWebView.settings.displayZoomControls = false
//        puterWebView.webViewClient = object : android.webkit.WebViewClient() {
//            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
//                // Construct the prompt
//                val rawPrompt = """
//                    You are an AI Tutor for OSRnary. Analyze: "$inputText".
//                    Dictionary Layout if one word, otherwise Context Overview.
//                    Keep it short and visually appealing.
//                """.trimIndent()
//
//                // SECURE STRING: Remove line breaks and escape quotes to prevent JS crashes
//                val safePrompt = rawPrompt.replace("\n", " ").replace("'", "\\'")
//
//                // Call the JS function directly
//                puterWebView.loadUrl("javascript:askGemini('$safePrompt')")
//            }
//        }
//
//        // Use your live GitHub Pages URL
//        puterWebView.loadUrl("https://puter-bridge.vercel.app/")
//    }
//}