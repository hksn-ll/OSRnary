package com.example.gabai

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.barteksc.pdfviewer.PDFView
import java.io.File
import java.net.URL
import kotlin.concurrent.thread

class PdfViewerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)

        val pdfUrl = intent.getStringExtra("PDF_URL") ?: return finish()
        val title = intent.getStringExtra("PDF_TITLE") ?: "Document"

        findViewById<TextView>(R.id.tv_pdf_title).text = title
        findViewById<ImageButton>(R.id.btn_close_pdf).setOnClickListener { finish() }

        val progressBar = findViewById<ProgressBar>(R.id.pdf_loading_bar)
        val pdfView = findViewById<PDFView>(R.id.online_pdf_viewer)

        // Download the PDF in the background
        thread {
            try {
                val input = URL(pdfUrl).openStream()
                val tempFile = File.createTempFile("temp_pdf", ".pdf", cacheDir)
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }

                runOnUiThread {
                    progressBar.visibility = View.GONE
                    pdfView.visibility = View.VISIBLE
                    pdfView.fromFile(tempFile)
                        .enableSwipe(true)
                        .swipeHorizontal(false)
                        .enableDoubletap(true)
                        .load()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    com.example.gabai.GabAIUtils.showSnackbar(this@PdfViewerActivity, "Failed to load PDF")
                    finish()
                }
            }
        }
    }
}