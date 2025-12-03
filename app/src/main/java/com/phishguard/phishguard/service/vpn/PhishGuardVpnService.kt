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
import java.nio.ByteBuffer

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
    
    private val packetParser = PacketParser()
    private val threatDetector = ThreatDetector()
    private val analyzedDomains = mutableSetOf<String>()
    private val notificationManager by lazy { 
        getSystemService(NotificationManager::class.java) 
    }
    
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
            // Phase 1: Simplified configuration for testing
            // We route only DNS traffic (8.8.8.8) to capture queries
            // All other traffic bypasses the VPN for normal internet connectivity
            vpnInterface = Builder()
                .setSession("PhishGuard")
                .addAddress(VPN_ADDRESS, 32)
                // Only route DNS server traffic through VPN
                .addRoute("8.8.8.8", 32)
                .addRoute("8.8.4.4", 32)
                .addDnsServer("8.8.8.8")
                .addDnsServer("8.8.4.4")
                .setMtu(VPN_MTU)
                .setBlocking(false)
                // Allow apps to bypass for all other traffic
                .allowBypass()
                .establish()
            
            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface")
                stopSelf()
                return
            }
            
            isRunning = true
            Log.d(TAG, "VPN tunnel established successfully")
            Log.i(TAG, "Phase 1: Monitoring mode - VPN active but not intercepting traffic")
            Log.i(TAG, "Internet connectivity maintained, threat detection via component tests")
            
            // Phase 1: Don't process packets to avoid blocking traffic
            // Packet processing requires proper network forwarding (Phase 2)
            // For now, threat detection is demonstrated via ComponentTester
            // serviceScope.launch {
            //     processPackets()
            // }
            
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
        Log.i(TAG, "Monitoring DNS queries and analyzing domains for threats")
        
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
                    
                    // Parse packet and extract domain (for monitoring)
                    try {
                        val parsedPacket = packetParser.parse(packet)
                        
                        // Analyze domain if present
                        parsedPacket?.domain?.let { domain ->
                            if (!analyzedDomains.contains(domain)) {
                                analyzedDomains.add(domain)
                                Log.d(TAG, "Extracted domain: $domain")
                                analyzeDomain(domain)
                            }
                        }
                    } catch (e: Exception) {
                        // Don't let parsing errors stop packet forwarding
                        Log.w(TAG, "Error parsing packet: ${e.message}")
                    }
                    
                    // Forward packet back to VPN interface
                    // Note: This is a simplified approach for Phase 1
                    // A production VPN would forward to actual network sockets
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
    
    private fun analyzeDomain(domain: String) {
        serviceScope.launch {
            try {
                val analysis = threatDetector.analyze(domain)
                
                when (analysis.verdict) {
                    ThreatDetector.Verdict.DANGEROUS -> {
                        Log.w(TAG, "DANGEROUS: $domain (${analysis.confidence * 100}%)")
                        showThreatNotification(domain, analysis, true)
                    }
                    ThreatDetector.Verdict.SUSPICIOUS -> {
                        Log.i(TAG, "SUSPICIOUS: $domain (${analysis.confidence * 100}%)")
                        showThreatNotification(domain, analysis, false)
                    }
                    ThreatDetector.Verdict.SAFE -> {
                        Log.d(TAG, "SAFE: $domain")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing domain: $domain", e)
            }
        }
    }
    
    private fun showThreatNotification(domain: String, analysis: ThreatDetector.ThreatAnalysis, isDangerous: Boolean) {
        val notificationId = domain.hashCode()
        
        val title = if (isDangerous) {
            "🛑 PHISHING ALERT"
        } else {
            "⚠️ Suspicious Site Detected"
        }
        
        val text = if (isDangerous) {
            "High risk site detected: $domain"
        } else {
            "Suspicious activity: $domain"
        }
        
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setStyle(Notification.BigTextStyle()
                .bigText("$text\n\nConfidence: ${(analysis.confidence * 100).toInt()}%\n\nReasons:\n${analysis.reasons.joinToString("\n• ", "• ")}")
            )
            .setAutoCancel(true)
            .setPriority(if (isDangerous) Notification.PRIORITY_HIGH else Notification.PRIORITY_DEFAULT)
            .build()
        
        notificationManager.notify(notificationId, notification)
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
            .setContentText("Ready to protect - Internet working normally")
            .setSmallIcon(android.R.drawable.ic_secure)
            .setOngoing(true)
            .setStyle(Notification.BigTextStyle()
                .bigText("PhishGuard is active\n\nPhase 1: Threat detection demonstrated via component tests\nInternet connectivity: Normal\nNo traffic blocking")
            )
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
