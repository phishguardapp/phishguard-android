package com.phishguard.phishguard.service.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import engine.Engine
import engine.Key

/**
 * Tun2Socks integration using gomobile-generated library
 * Handles packet forwarding through native tun2socks implementation
 */
class Tun2SocksManager(
    private val vpnService: VpnService,
    private val onDomainAccessed: (String) -> Unit
) {
    
    companion object {
        private const val TAG = "Tun2SocksManager"
        
        init {
            try {
                System.loadLibrary("gojni")
                Log.i(TAG, "Loaded gojni native library")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load gojni library", e)
            }
        }
    }
    
    private var isRunning = false
    
    /**
     * Start Tun2Socks with the VPN interface
     */
    fun start(vpnInterface: ParcelFileDescriptor) {
        if (isRunning) {
            Log.w(TAG, "Tun2Socks already running")
            return
        }
        
        try {
            Log.i(TAG, "Starting Tun2Socks engine...")
            
            // Create configuration key with VPN file descriptor
            val key = Key()
            key.setDevice("fd://${vpnInterface.fd}")
            key.setLogLevel("info")  // Valid levels: debug, info, warn, error, fatal, panic
            key.setProxy("socks5://127.0.0.1:1080")  // Use our local SOCKS proxy
            key.setMTU(1500L)
            
            // Insert configuration and start engine (static methods)
            Engine.insert(key)
            Engine.start()
            
            isRunning = true
            Log.i(TAG, "Tun2Socks started successfully - Internet should work now!")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Tun2Socks", e)
            throw e
        }
    }
    
    /**
     * Stop Tun2Socks
     */
    fun stop() {
        if (!isRunning) return
        
        try {
            Log.i(TAG, "Stopping Tun2Socks...")
            Engine.stop()
            isRunning = false
            Log.i(TAG, "Tun2Socks stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Tun2Socks", e)
        }
    }
    
    fun isRunning(): Boolean = isRunning
}
