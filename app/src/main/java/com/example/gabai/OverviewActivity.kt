package com.example.gabai

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
import com.example.gabai.BuildConfig
import android.speech.tts.TextToSpeech
import java.util.Locale
import com.google.mlkit.nl.languageid.LanguageIdentification

import android.widget.ImageButton
import android.widget.ProgressBar
class OverviewActivity : AppCompatActivity() {

    private var lastAiResult: String = ""
    private lateinit var tts: TextToSpeech
    private var isTtsReady = false


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
            com.example.gabai.GabAIUtils.showSnackbar(this, "No text provided")
        }
        val favoriteBtn = findViewById<android.widget.ImageButton>(R.id.btn_favorite)
        favoriteBtn.setOnClickListener {
            if (lastAiResult.isNotEmpty()) {
                saveToFavorites(scannedText, lastAiResult)
                favoriteBtn.setImageResource(android.R.drawable.btn_star_big_on)
                com.example.gabai.GabAIUtils.showSnackbar(this, "Saved to Favorites!")
            }
        }
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
            }
        }

        // The button for the scanned text at the top
        findViewById<android.widget.ImageButton>(R.id.btn_speak_selected).setOnClickListener {
            // Remove the "quotes" from the text before detecting and speaking
            val rawText = findViewById<TextView>(R.id.selected_text_view).text.toString()
            val cleanText = rawText.replace("\"", "").trim()
            speakWithDetection(cleanText)
        }

// The button for the AI result at the bottom
        findViewById<android.widget.ImageButton>(R.id.btn_speak_explanation).setOnClickListener {
            speakExplanation(lastAiResult)
        }
    }

    override fun onResume() {
        super.onResume()
        // Always try to hide it when this screen is open
        val intent = android.content.Intent(this, FloatingControlService::class.java)
        intent.action = "ACTION_HIDE"
        startService(intent)
    }

    override fun onPause() {
        super.onPause()

        // 1. STOP THE VOICE IMMEDIATELY WHEN LEAVING
        if (::tts.isInitialized) {
            tts.stop()
        }

        // 2. EXISTING CODE: Handle the bubble visibility
        val isEnabled =
            getSharedPreferences("GabAI_Prefs", MODE_PRIVATE).getBoolean("bubble_enabled", false)
        if (isEnabled) {
            val intent = android.content.Intent(this, FloatingControlService::class.java)
            intent.action = "ACTION_SHOW"
            startService(intent)
        }
    }

    private fun loadGoogleImages(webView: android.webkit.WebView, query: String) {
        val visualsContainer = findViewById<View>(R.id.visuals_container)
        visualsContainer.visibility = View.VISIBLE

        // 1. Settings to fix the blank screen and allow scrolling
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 10; Pixel 4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.101 Mobile Safari/537.36"

        // 2. THIS FIXES THE SCROLLING: Stop the parent ScrollView from stealing touches
        webView.setOnTouchListener { v, event ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            false
        }

        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                super.onPageFinished(view, url)

                // 3. Updated JavaScript with your new classes
                view?.evaluateJavascript(
                    """
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
            """.trimIndent(), null
                )
            }

            override fun shouldOverrideUrlLoading(
                view: android.webkit.WebView?,
                request: android.webkit.WebResourceRequest?
            ): Boolean {
                // FIX: Returning false allows the WebView to actually load the URL and follow redirects
                return false
            }
        }
// FIX: Properly encode the query so spaces don't break the WebView
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val searchUrl = "https://www.google.com/search?tbm=isch&q=$encodedQuery"

        webView.loadUrl(searchUrl)
    }

    private fun generateAIOverview(inputText: String) {
        val loadingContainer = findViewById<LinearLayout>(R.id.loading_container)
        val resultContainer = findViewById<LinearLayout>(R.id.result_container)
        val resultTextView = findViewById<TextView>(R.id.ai_result_text)

        // 1. GET USER LANGUAGE PREFERENCE
        // 1. GET DETAILED LANGUAGE PREFERENCE
        val prefs = getSharedPreferences("GabAI_Prefs", MODE_PRIVATE)
        val selectedLang = prefs.getString("ai_language_pref", "English")

        val langRequirement = when (selectedLang) {
            "Taglish" -> "- **Tone & Language (CRITICAL):** Explain using 'Taglish' (a natural, conversational mix of Filipino and English). Sound like a smart, helpful local Kuya or Ate tutoring a student. It should be engaging but highly factual."
            "Tagalog" -> "- **Language Preference:** Explain strictly in clear, formal, but easy-to-understand Tagalog (Filipino)."
            else -> "- **Language Preference:** Explain strictly in clear, accessible English suitable for a Grade 10 student."
        }

        // Initialize Markdown
        val markwon = Markwon.create(this)

        // Configure Gemini
        val generativeModel = GenerativeModel(
            modelName = "gemini-2.5-flash-lite",
            apiKey = BuildConfig.GEMINI_API_KEY
        )

        lifecycleScope.launch {
            try {
                // 2. YOUR ORIGINAL PRECISE PROMPT (With language requirement added)
                val prompt = """
                    You are an AI Tutor for an educational app called GabAI, helping high school students (Grade 10).
                    Analyze the following input text: "$inputText"

                    **INSTRUCTIONS:**
                    **CASE 1: If it is a definable single word:**
                    - Provide a **Dictionary Layout** first.
                    - Format:
                      > **Word** (Pronunciation) - *Part of Speech*
                      > Definition: [Clear, objective definition]
                    - Follow immediately with a **Context Overview**.

                    **CASE 2: If it is a phrase, proper noun, event, or topic:**
                    - SKIP the dictionary layout.
                    - Provide ONLY the **Context Overview**.

                    **CONTENT REQUIREMENTS:**
                    $langRequirement
                    - **Context Overview Tone (CRITICAL):** You must be strictly informative, objective, and academic, while remaining accessible to a 10th-grade reading level. 
                    - **BANNED PHRASES:** DO NOT use storytelling framing. Never use phrases like "Imagine...", "Think of it like...", "Picture this...", or conversational filler like "Let's dive into...". 
                    - Deliver historical facts, contextual significance, and explanations directly. Strip away overly complex postgraduate jargon, but absolutely do not talk down to the user. Your voice should mimic a modern, high-quality digital encyclopedia.
                    - **If Non-English:** State Language, Provide English Translation.
                    - **Prioritize Filipino** when there is a word that is non-English.
                    - **If English:** Provide standard definitions and context.

                    **FORMATTING RULES:**
                    - Use **Bold** for headers.
                    - Use > Blockquotes for definitions.
                    - Keep it concise, strictly factual, and visually structured.
                """.trimIndent()
                val response = generativeModel.generateContent(prompt)

                // Update UI
                loadingContainer.visibility = View.GONE
                resultContainer.visibility = View.VISIBLE

                response.text?.let {
                    lastAiResult = it
                    markwon.setMarkdown(resultTextView, it)
                    saveToHistory(inputText, it, inputText)
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
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        // 1. XP LOGIC WITH GATEKEEPER
        if (XPManager.canEarnXP(this)) {
            val leveledUp = XPManager.addXP(this, 10)
            if (leveledUp) {
                com.example.gabai.GabAIUtils.showSnackbar(this, "LEVEL UP! You are now Level ${XPManager.getLevel(this)}! 🎉")
            } else {
                com.example.gabai.GabAIUtils.showSnackbar(this, "Saved! +10 XP gained")
            }
        } else {
            // Rank is locked, just show a normal save message
            com.example.gabai.GabAIUtils.showSnackbar(this, "Saved to Favorites! ⭐")
        }

        // 2. CLOUD SAVE: Use a subcollection for Favorites

        // 2. CLOUD SAVE: Use a subcollection for Favorites
        val favEntry = hashMapOf(
            "word" to word,
            "definition" to definition,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("users").document(uid)
            .collection("favorites").document(word) // Using the word as ID prevents duplicates
            .set(favEntry)
            .addOnSuccessListener {
                android.util.Log.d("GabAI_DB", "Favorite synced to cloud")
                db.collection("users").document(uid).update("quests_completed", com.google.firebase.firestore.FieldValue.arrayUnion("save"))
            }
        db.collection("users").document(uid).update("quests_completed", com.google.firebase.firestore.FieldValue.arrayUnion("save"))
    }

    private fun saveToHistory(text: String, aiResult: String, originalContext: String) { // <-- Added 'originalContext' parameter
        // 1. Firebase setup
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        val timestamp = System.currentTimeMillis()



        // 3. CLOUD SAVE (This creates the subcollection)
        // In OverviewActivity.kt -> saveToHistory()
        val historyEntry = hashMapOf(
            "word" to text,
            "explanation" to aiResult,
            "timestamp" to timestamp,
            "originalContext" to originalContext, // <--- FIXED
            "nextReview" to timestamp,
            "interval" to 1,
            "easeFactor" to 2.5
        )


        db.collection("users").document(uid)
            .collection("history").document(timestamp.toString())
            .set(historyEntry)
            .addOnSuccessListener {
                android.util.Log.d("GabAI_DB", "Cloud save successful!")
                // Optional: Toast for success
                com.example.gabai.GabAIUtils.showSnackbar(this, "Synced to Cloud")
            }
            .addOnFailureListener { e ->
                // NEW: Detailed Debug Dialog
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Firestore Error")
                    .setMessage("Failed to save history: ${e.localizedMessage}\n\nCheck Logcat (GabAI_DB) for details.")
                    .setPositiveButton("OK", null)
                    .show()
                android.util.Log.e("GabAI_DB", "Error: ", e)
            }
    }
    private fun speakWithDetection(text: String) {
        if (!isTtsReady || text.isEmpty()) return

        // 1. SHOW THE LOADING ICON
        val loader = findViewById<ProgressBar>(R.id.progress_tts_selected)
        val speakBtn = findViewById<ImageButton>(R.id.btn_speak_selected)
        loader.visibility = View.VISIBLE
        speakBtn.visibility = View.INVISIBLE // Hide button while loading

        val languageIdentifier = com.google.mlkit.nl.languageid.LanguageIdentification.getClient()
        languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener { languageCode ->
                // 2. HIDE THE LOADING ICON
                loader.visibility = View.GONE
                speakBtn.visibility = View.VISIBLE

                val locale = if (languageCode == "fil" || languageCode == "tl") {
                    java.util.Locale("fil", "PH")
                } else {
                    java.util.Locale.US
                }

                tts.language = locale
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            }
            .addOnFailureListener {
                loader.visibility = View.GONE
                speakBtn.visibility = View.VISIBLE
                tts.language = java.util.Locale.US
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            }
    }
    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()      // Stop talking
            tts.shutdown()  // Turn off the engine
        }
        super.onDestroy()
    }

    private fun speakExplanation(text: String) {
        if (!isTtsReady || text.isEmpty()) return

        // 1. Get the language choice from your Profile Dropdown
        val prefs = getSharedPreferences("GabAI_Prefs", MODE_PRIVATE)
        val selectedLang = prefs.getString("ai_language_pref", "English") ?: "English"

        // 2. Set voice language
        val locale = if (selectedLang == "Tagalog" || selectedLang == "Taglish") {
            java.util.Locale("fil", "PH")
        } else {
            java.util.Locale.US
        }

        tts.language = locale

        // 3. Clean Markdown (Remove #, *, >, and __) so the AI doesn't read symbols
        val cleanText = text.replace(Regex("[#*<>_]"), "")
        tts.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, null)
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