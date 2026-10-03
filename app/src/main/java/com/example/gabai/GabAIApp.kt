package com.example.gabai

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import kotlin.concurrent.thread

class GabAIApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Offload App Check to background thread so AppBindData runs at maximum speed
        thread(start = true, name = "GabAI-Init") {
            try {
                if (FirebaseApp.getApps(this@GabAIApp).isNotEmpty()) {
                    val firebaseAppCheck = FirebaseAppCheck.getInstance()
                    firebaseAppCheck.installAppCheckProviderFactory(
                        PlayIntegrityAppCheckProviderFactory.getInstance()
                    )
                }
            } catch (e: Exception) {
                Log.w("GabAIApp", "Async AppCheck initialization notice: ${e.message}")
            }
        }
    }
}