package com.example.gabai

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory cache for AI Overview responses to eliminate redundant Gemini API token consumption
 * when users re-inspect words, go back and forth from scanner, or re-open previous definitions.
 */
object OverviewCache {

    data class CachedOverview(
        val targetWord: String,
        val phonetics: String,
        val partOfSpeech: String,
        val definition: String,
        val inSentenceRole: String,
        val isVisualFeasible: Boolean,
        val visualSearchTerm: String,
        val relatedQuestions: List<String>
    )

    private val cache = ConcurrentHashMap<String, CachedOverview>()

    fun makeKey(text: String, context: String = ""): String {
        return "${text.trim().lowercase()}||${context.trim().lowercase()}"
    }

    fun get(text: String, context: String = ""): CachedOverview? {
        val cleanText = text.trim().lowercase()
        if (cleanText.isEmpty()) return null

        // 1. Exact match with context
        val exactKey = makeKey(text, context)
        val exact = cache[exactKey]
        if (exact != null) return exact

        // 2. Fallback match on target word/phrase to protect against slight context deviations
        for ((k, v) in cache) {
            if (k.startsWith("$cleanText||") || v.targetWord.trim().equals(cleanText, ignoreCase = true)) {
                return v
            }
        }
        return null
    }

    fun put(text: String, context: String = "", overview: CachedOverview) {
        val cleanText = text.trim().lowercase()
        if (cleanText.isEmpty()) return
        cache[makeKey(text, context)] = overview
        // Also index under standalone text for quick fallback lookup
        if (context.isNotBlank()) {
            cache[makeKey(text, "")] = overview
        }
    }

    fun clear() {
        cache.clear()
    }
}
