package com.phishguard.phishguard.service.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.*
import java.net.InetAddress

/**
 * Monitors network connections to detect accessed domains
 * Uses Android's ConnectivityManager to track network activity
 */
class NetworkMonitor(
    private val context: Context,
    private val onDomainDetected: (String) -> Unit
) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val seenAddresses = mutableSetOf<String>()
    
    companion object {
        private const val TAG = "NetworkMonitor"
    }
    
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            Log.d(TAG, "Network available: $network")
        }
        
        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            // Monitor network capabilities changes
        }
    }
    
    fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.registerNetworkCallback(request, networkCallback)
        Log.i(TAG, "Network monitor started")
    }
    
    fun stop() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering callback", e)
        }
        scope.cancel()
        Log.i(TAG, "Network monitor stopped")
    }
    
    /**
     * Manually check a domain (for testing or manual URL entry)
     */
    fun checkDomain(domain: String) {
        if (seenAddresses.contains(domain)) return
        seenAddresses.add(domain)
        onDomainDetected(domain)
    }
}
