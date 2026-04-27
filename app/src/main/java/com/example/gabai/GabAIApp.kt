package com.example.gabai

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class GabAIApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // 1. Initialize Firebase
        FirebaseApp.initializeApp(this)

        // 2. Install App Check
        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        firebaseAppCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )
    }
}