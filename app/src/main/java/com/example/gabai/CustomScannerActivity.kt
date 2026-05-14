package com.example.gabai

import android.os.Bundle
import android.widget.ImageButton
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView

class CustomScannerActivity : CaptureActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No need to set content view here, ZXing handles it via overriding methods below

        findViewById<ImageButton>(R.id.btn_back_scanner).setOnClickListener {
            finish()
        }
    }

    // Tell the library to use our beautiful custom layout

    override fun initializeContent(): DecoratedBarcodeView {
        // We MUST set the layout here before trying to find the scanner view!
        setContentView(R.layout.activity_qr_scanner)
        return findViewById(R.id.zxing_barcode_scanner)
    }
}