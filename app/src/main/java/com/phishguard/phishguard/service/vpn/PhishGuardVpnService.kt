package com.phishguard.phishguard.service.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

/**
 * PhishGuard VPN Service
 * 
 * Core VPN service that intercepts all network traffic system-wide to detect
 * phishing and scam URLs in real-time. This service:
 * - Establishes a local VPN tunnel
 * - Captures all outgoing network packets
 * - Extracts URLs/domains from packets
 * - Analyzes them for phishing indicators
 * - Blocks or warns based on threat level
 */
class PhishGuardVpnService : VpnService() {
    
    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    
    companion object {
        private const val TAG = "PhishGuardVpnService"
        private const val VPN_MTU = 1500
        private const val VPN_ADDRESS = "10.0.0.2"
        private const val VPN_ROUTE = "0.0.0.0"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "phishguard_vpn"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VPN Service created")
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "VPN Service start command received")
        
        when (intent?.action) {
            Actions.ACTION_START -> startVpn()
            Actions.ACTION_STOP -> stopVpn()
        }
        
        return START_STICKY
    }
    
    private fun startVpn() {
        if (isRunning) {
            Log.d(TAG, "VPN already running")
            return
        }
        
        try {
            // Start as foreground service
            startForeground(NOTIFICATION_ID, createNotification())
            
            // Establish VPN tunnel
            vpnInterface = Builder()
                .setSession("PhishGuard")
                .addAddress(VPN_ADDRESS, 32)
                .addRoute(VPN_ROUTE, 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("8.8.4.4")
                .setMtu(VPN_MTU)
                .setBlocking(false)
                .establish()
            
            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface")
                stopSelf()
                return
            }
            
            isRunning = true
            Log.d(TAG, "VPN tunnel established successfully")
            
            // Start packet processing
            serviceScope.launch {
                processPackets()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN", e)
            stopSelf()
        }
    }
    
    private suspend fun processPackets() = withContext(Dispatchers.IO) {
        val vpnInput = FileInputStream(vpnInterface?.fileDescriptor)
        val vpnOutput = FileOutputStream(vpnInterface?.fileDescriptor)
        val buffer = ByteBuffer.allocate(VPN_MTU)
        
        Log.d(TAG, "Starting packet processing loop")
        
        try {
            while (isRunning && vpnInterface != null) {
                buffer.clear()
                
                // Read packet from VPN interface
                val length = vpnInput.channel.read(buffer)
                if (length > 0) {
                    buffer.flip()
                    
                    // Process the packet
                    val packet = ByteArray(length)
                    buffer.get(packet)
                    
                    // TODO: Parse packet and extract URL/domain
                    // TODO: Analyze for phishing
                    // TODO: Block or allow based on analysis
                    
                    // For now, just forward all packets
                    buffer.rewind()
                    vpnOutput.channel.write(buffer)
                }
                
                // Yield to prevent blocking
                yield()
            }
        } catch (e: Exception) {
            if (isRunning) {
                Log.e(TAG, "Error processing packets", e)
            }
        } finally {
            Log.d(TAG, "Packet processing loop ended")
        }
    }
    
    private fun stopVpn() {
        Log.d(TAG, "Stopping VPN")
        isRunning = false
        
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    override fun onDestroy() {
        Log.d(TAG, "VPN Service destroyed")
        stopVpn()
        serviceScope.cancel()
        super.onDestroy()
    }
    
    override fun onRevoke() {
        Log.d(TAG, "VPN permission revoked")
        stopVpn()
        super.onRevoke()
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "PhishGuard Protection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "PhishGuard VPN service status"
            setShowBadge(false)
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
    
    private fun createNotification(): Notification {
        val stopIntent = Intent(this, PhishGuardVpnService::class.java).apply {
            action = Actions.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("PhishGuard Active")
            .setContentText("Protecting you from phishing and scams")
            .setSmallIcon(android.R.drawable.ic_secure)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_delete,
                "Stop",
                stopPendingIntent
            )
            .build()
    }
    
    object Actions {
        const val ACTION_START = "com.phishguard.action.START_VPN"
        const val ACTION_STOP = "com.phishguard.action.STOP_VPN"
    }
}
