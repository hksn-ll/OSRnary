package com.example.gabai

import android.graphics.Bitmap

/**
 * In-memory holder for passing screenshots and scans directly to ScanResultActivity
 * with 0ms disk write/read latency and 100% uncompressed raw pixel quality for ML Kit OCR.
 */
object ScanImageHolder {
    @Volatile
    var currentBitmap: Bitmap? = null

    fun clear() {
        currentBitmap = null
    }
}
