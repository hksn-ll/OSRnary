package com.example.gabai

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.json.JSONObject
import java.io.InputStream

/**
 * Result data holder for AI content validation of uploaded teacher documents.
 */
data class AIValidationResult(
    val isValid: Boolean,
    val detectedSubject: String,
    val rejectionReason: String
)

/**
 * Enforces strict AI content moderation and subject filtering on teacher-uploaded PDFs.
 * Ensures that GabAI remains strictly focused on Reading Comprehension, Literacy,
 * Literature, and Language materials, while firmly rejecting unrelated subjects
 * like Mathematics worksheets, science calculation sheets, chemistry reactions,
 * physics problem sets, code snippets, and administrative data.
 */
object AIContentValidator {

    private const val TAG = "AIContentValidator"

    /**
     * Extracts text from a document URI safely using PDFBox.
     */
    fun extractTextFromUri(context: Context, uri: Uri): String {
        return try {
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
            } catch (_: Exception) {}

            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val document = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            val text = stripper.getText(document) ?: ""
            document.close()
            inputStream?.close()
            text
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text from PDF: ${e.message}", e)
            ""
        }
    }

    /**
     * Validates a document URI directly.
     */
    suspend fun validateReadingPdf(context: Context, uri: Uri): AIValidationResult {
        val extractedText = extractTextFromUri(context, uri)
        return validateReadingDocument(extractedText)
    }

    /**
     * Validates extracted text content against reading literacy standards using Gemini AI.
     */
    suspend fun validateReadingDocument(extractedText: String): AIValidationResult {
        val trimmed = extractedText.trim()
        if (trimmed.length < 35) {
            return AIValidationResult(
                isValid = false,
                detectedSubject = "Unreadable / Empty Document",
                rejectionReason = "This document does not contain readable digital text (it may be a scanned image or empty). Please upload a document with clear digital text for reading comprehension."
            )
        }

        return try {
            val generativeModel = GenerativeModel(
                modelName = "gemini-3.5-flash-lite",
                apiKey = BuildConfig.GEMINI_API_KEY
            )

            // Extract a representative text sample (first 8,000 characters)
            val sample = trimmed.take(8000)

            val prompt = """
                You are an intelligent educational content validator for GabAI, a dedicated reading comprehension and literacy platform.
                Your task is to inspect the provided text extracted from a teacher's uploaded document and determine if it qualifies as reading comprehension or literacy material.

                CRITERIA FOR ACCEPTANCE (isValid: true):
                - Reading comprehension passages, short stories, novels, folk tales, fables, myths, poetry, dramas, or scripts.
                - Expository essays, informational reading articles, biographies, historical narratives, speeches, and prose articles written for students to read and comprehend.
                - Informational articles with scientific, environmental, or historical topics written in prose for reading comprehension are ACCEPTABLE.
                - English or Filipino language development texts, phonics passages, and contextual vocabulary readings.

                CRITERIA FOR REJECTION (isValid: false):
                - Mathematics: arithmetic problem sets, algebra, geometry, calculus, trigonometry worksheets, equation sheets, number drills, math practice tests.
                - Pure STEM calculation / laboratory materials devoid of reading passages: physics formula banks, chemistry reaction tables, electrical circuit schematics, numerical experiment logs.
                - Computer programming source code, syntax cheat sheets, algorithm data.
                - Pure tabular data, accounting balance sheets, attendance rosters, financial records, administrative forms.

                Text Sample:
                \"\"\"
                $sample
                \"\"\"

                Respond with ONLY a valid raw JSON object. Do not enclose in markdown code blocks or add introductory text.
                Format:
                {
                  "isValid": true,
                  "detectedSubject": "Descriptive label (e.g., 'Grade 5 Reading Passage', 'Algebra Problem Set', 'Physics Formula Sheet')",
                  "rejectionReason": "If isValid is false, provide a polite, clear 1-2 sentence explanation of why this document cannot be accepted, stating the detected subject and reminding the teacher that GabAI only accepts reading comprehension texts. If isValid is true, leave as an empty string."
                }
            """.trimIndent()

            val response = generativeModel.generateContent(prompt)
            val responseText = response.text ?: ""
            parseValidationResponse(responseText)
        } catch (e: Exception) {
            Log.e(TAG, "AI validation error: ${e.message}", e)
            AIValidationResult(
                isValid = false,
                detectedSubject = "Verification Unavailable",
                rejectionReason = "Unable to verify document with AI (${e.localizedMessage ?: "Network error"}). Please check your connection and try again."
            )
        }
    }

    private fun parseValidationResponse(rawText: String): AIValidationResult {
        var clean = rawText.trim()
        if (clean.startsWith("```json")) {
            clean = clean.removePrefix("```json")
        } else if (clean.startsWith("```")) {
            clean = clean.removePrefix("```")
        }
        if (clean.endsWith("```")) {
            clean = clean.removeSuffix("```")
        }
        clean = clean.trim()

        val startIdx = clean.indexOf("{")
        val endIdx = clean.lastIndexOf("}")
        if (startIdx != -1 && endIdx != -1 && endIdx >= startIdx) {
            clean = clean.substring(startIdx, endIdx + 1)
        }

        return try {
            val json = JSONObject(clean)
            val isValid = json.optBoolean("isValid", false)
            val detectedSubject = json.optString("detectedSubject", if (isValid) "Reading Material" else "Non-Reading Content")
            val rejectionReason = json.optString("rejectionReason", "")
            AIValidationResult(
                isValid = isValid,
                detectedSubject = detectedSubject,
                rejectionReason = rejectionReason
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse validation JSON: $rawText", e)
            val lower = rawText.lowercase()
            if (lower.contains("\"isvalid\": false") || lower.contains("\"isvalid\":false")) {
                AIValidationResult(
                    isValid = false,
                    detectedSubject = "Non-Reading Document",
                    rejectionReason = "AI detected non-reading content. GabAI only accepts reading comprehension materials."
                )
            } else if (lower.contains("\"isvalid\": true") || lower.contains("\"isvalid\":true")) {
                AIValidationResult(
                    isValid = true,
                    detectedSubject = "Reading Material",
                    rejectionReason = ""
                )
            } else {
                AIValidationResult(
                    isValid = false,
                    detectedSubject = "Content Review Required",
                    rejectionReason = "Unable to verify document format. Please ensure the document is a readable story or comprehension passage."
                )
            }
        }
    }
}
