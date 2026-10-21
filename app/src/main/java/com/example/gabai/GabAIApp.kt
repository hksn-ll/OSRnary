package com.example.gabai

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import kotlin.concurrent.thread

class GabAIApp : Application() {
    companion object {
        const val DRIVE_API_URL = "https://script.google.com/macros/s/AKfycbxmlWtZXkpYqbgQU8wZ6Qdga9ImIHhlP5kMUSdujH8y2Db9SdP_DLswqoTO1-FDcf9CaQ/exec"
    }

    override fun onCreate() {
        super.onCreate()
        // Prioritize IPv4 to prevent blackholing on devices with broken cellular/VoWiFi IPv6 tunnels
        System.setProperty("java.net.preferIPv4Stack", "true")
        System.setProperty("java.net.preferIPv6Addresses", "false")

        bindToValidInternetNetwork()

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // Initialize centralized GitHub update monitor with lifecycle tracking and idle poller
        GitHubUpdateHelper.init(this)
        OfflineLockoutHelper.init(this)

        // Offload App Check to background thread so AppBindData runs at maximum speed
        thread(start = true, name = "GabAI-Init") {
            try {
                if (FirebaseApp.getApps(this@GabAIApp).isNotEmpty()) {
                    val firebaseAppCheck = FirebaseAppCheck.getInstance()
                    if (BuildConfig.DEBUG) {
                        firebaseAppCheck.installAppCheckProviderFactory(
                            com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory.getInstance()
                        )
                    } else {
                        firebaseAppCheck.installAppCheckProviderFactory(
                            PlayIntegrityAppCheckProviderFactory.getInstance()
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w("GabAIApp", "Async AppCheck initialization notice: ${e.message}")
            }
        }
    }

    private fun bindToValidInternetNetwork() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        try {
            // Find active Wi-Fi or valid internet network that isn't a restricted carrier IMS/VoWiFi tunnel
            val validNetwork = cm.allNetworks.firstOrNull { net ->
                val caps = cm.getNetworkCapabilities(net) ?: return@firstOrNull false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            } ?: cm.activeNetwork

            if (validNetwork != null) {
                cm.bindProcessToNetwork(validNetwork)
                Log.i("GabAIApp", "Successfully bound app network process to validated interface: $validNetwork")
            }

            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .build()

            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {

                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                            cm.bindProcessToNetwork(network)
                            Log.i("GabAIApp", "Dynamic Wi-Fi interface validated & bound: $network")
                        } else {
                            // Wi-Fi has no internet; unbind process network so Android can use cellular data
                            cm.bindProcessToNetwork(null)
                            Log.i("GabAIApp", "Dynamic Wi-Fi unvalidated; unbound process network: $network")
                        }

                    }
                }

                override fun onLost(network: Network) {
                    cm.bindProcessToNetwork(null)
                }
            })
        } catch (e: Exception) {
            Log.w("GabAIApp", "Could not bind process to network: ${e.message}")
        }
    }
}