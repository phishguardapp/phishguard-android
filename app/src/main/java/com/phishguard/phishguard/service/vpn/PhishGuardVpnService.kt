package com.phishguard.phishguard.service.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.phishguard.phishguard.utils.PreferencesManager
import kotlinx.coroutines.*
import kotlinx.coroutines.delay
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
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
    
    private val threatDetector by lazy { ThreatDetector(this) }
    private val prefsManager by lazy { PreferencesManager(this) }
    private val analyzedDomains = mutableSetOf<String>()
    private val notifiedDomains = mutableMapOf<String, Long>()  // domain -> last notification time
    private var lastNotificationTime = 0L  // Track last notification to suppress resource spam
    private val notificationManager by lazy { 
        getSystemService(NotificationManager::class.java) 
    }
    
    // Track original user-entered domains and their redirects
    private val domainRedirects = mutableMapOf<String, String>()  // redirect -> original
    private val recentDomains = mutableListOf<String>()  // Track order of domains accessed
    private val MAX_RECENT_DOMAINS = 10
    
    private val settingsBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Actions.ACTION_CLEAR_CACHE -> {
                    Log.i(TAG, "Clearing threat analysis cache")
                    threatDetector.clearCache()
                }
                Actions.ACTION_UPDATE_CACHE_DURATION -> {
                    Log.i(TAG, "Cache duration updated to ${prefsManager.cacheDurationHours} hours")
                    // Cache will automatically use new duration on next lookup
                }
            }
        }
    }
    
    private var ipMonitor: IpMonitor? = null
    private var dnsMonitor: DnsMonitor? = null
    private var tun2socksManager: Tun2SocksManager? = null
    private var socksProxy: LocalSocksProxy? = null
    
    companion object {
        private const val TAG = "PhishGuardVpnService"
        private const val VPN_MTU = 1500
        private const val VPN_ADDRESS = "10.0.0.2"
        private const val VPN_ROUTE = "0.0.0.0"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "phishguard_vpn"
        private const val NOTIFICATION_COOLDOWN_MS = 5 * 60 * 1000L  // 5 minutes
        private const val RESOURCE_SUPPRESSION_MS = 30 * 1000L  // 30 seconds - suppress resource notifications
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VPN Service created")
        createNotificationChannel()
        
        // Register broadcast receiver for settings changes
        val filter = IntentFilter().apply {
            addAction(Actions.ACTION_CLEAR_CACHE)
            addAction(Actions.ACTION_UPDATE_CACHE_DURATION)
        }
        registerReceiver(settingsBroadcastReceiver, filter, RECEIVER_NOT_EXPORTED)
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
            
            // Establish VPN tunnel with Tun2Socks forwarding
            // Note: We exclude our own app to prevent routing loops with tun2socks
            vpnInterface = Builder()
                .setSession("PhishGuard")
                .addAddress(VPN_ADDRESS, 32)
                .addRoute(VPN_ROUTE, 0)
                .addDnsServer("8.8.8.8")  // Back to Google DNS - DNS monitor has issues
                .addDnsServer("8.8.4.4")
                .setMtu(VPN_MTU)
                .setBlocking(false)
                .addDisallowedApplication(packageName)  // Exclude ourselves to prevent loops
                .establish()
            
            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface")
                stopSelf()
                return
            }
            
            isRunning = true
            Log.d(TAG, "VPN tunnel established successfully")
            
            // DNS monitoring disabled for now - causes internet connectivity issues
            // The SOCKS proxy + reverse DNS + caching is sufficient for domain resolution
            
            // Start local SOCKS proxy for domain extraction
            socksProxy = LocalSocksProxy(
                onDomainDetected = { detectedDomain, port ->
                    val protocol = when (port) {
                        80 -> "HTTP"
                        443 -> "HTTPS"
                        else -> "port $port"
                    }
                    Log.i(TAG, "🔍 Domain detected via SOCKS: $detectedDomain ($protocol)")
                    
                    // Skip infrastructure domains that are clearly safe
                    if (isInfrastructureDomain(detectedDomain)) {
                        Log.d(TAG, "⏭️ SKIP - infrastructure domain: $detectedDomain")
                        return@LocalSocksProxy
                    }
                    
                    // Track domain access order for redirect detection
                    trackDomainAccess(detectedDomain)
                    
                    // Always analyze (uses cache if already analyzed)
                    // But control notification frequency
                    val isNewDomain = !analyzedDomains.contains(detectedDomain)
                    if (isNewDomain) {
                        analyzedDomains.add(detectedDomain)
                        Log.i(TAG, "✅ NEW domain - analyzing: $detectedDomain")
                    } else {
                        Log.d(TAG, "🔄 Re-analyzing cached domain: $detectedDomain")
                    }
                    
                    // Determine which domain to show in notification
                    val displayDomain = getDisplayDomain(detectedDomain)
                    
                    // Check if using unencrypted HTTP
                    val isHttp = (port == 80)
                    analyzeDomain(detectedDomain, displayDomain, isNewDomain, isHttp)
                },
                onDomainToIpMapping = { domain, ipAddress ->
                    // Cache the DNS resolution for later IP-to-domain lookups
                    Log.d(TAG, "📝 Caching DNS: $domain -> $ipAddress")
                    threatDetector.cacheDnsResolution(domain, ipAddress)
                }
            )
            socksProxy?.start()
            
            // Start Tun2Socks configured to use our SOCKS proxy
            tun2socksManager = Tun2SocksManager(
                vpnService = this,
                onDomainAccessed = { domain -> }
            )
            
            tun2socksManager?.start(vpnInterface!!)
            
            Log.i(TAG, "Tun2Socks started with SOCKS proxy - domain extraction active!")
            
            // Test notification to verify system works
//            serviceScope.launch {
//                delay(2000) // Wait 2 seconds
//                val testAnalysis = ThreatDetector.ThreatAnalysis(
//                    domain = "test-notification.com",
//                    verdict = ThreatDetector.Verdict.SUSPICIOUS,
//                    confidence = 0.75f,
//                    reasons = listOf("This is a test notification to verify the system works")
//                )
//                showThreatNotification("test-notification.com", testAnalysis, false)
//                Log.i(TAG, "Test notification sent")
//            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN", e)
            stopSelf()
        }
    }
    

    
    /**
     * Track domain access for redirect detection
     */
    private fun trackDomainAccess(domain: String) {
        recentDomains.add(0, domain)
        if (recentDomains.size > MAX_RECENT_DOMAINS) {
            recentDomains.removeAt(recentDomains.size - 1)
        }
    }
    
    /**
     * Determine which domain to display in notification
     * If this looks like a redirect (infrastructure/hosting domain), show the original user domain
     */
    private fun getDisplayDomain(detectedDomain: String): String {
        // If this domain looks like infrastructure/hosting reverse DNS
        if (looksLikeInfrastructure(detectedDomain)) {
            // Find the most recent non-infrastructure domain
            val originalDomain = recentDomains.firstOrNull { !looksLikeInfrastructure(it) }
            if (originalDomain != null && originalDomain != detectedDomain) {
                Log.d(TAG, "🔀 Redirect detected: $originalDomain -> $detectedDomain")
                domainRedirects[detectedDomain] = originalDomain
                return originalDomain
            }
        }
        
        // Check if we've seen this as a redirect before
        domainRedirects[detectedDomain]?.let { original ->
            Log.d(TAG, "🔀 Known redirect: using original domain $original")
            return original
        }
        
        return detectedDomain
    }
    
    /**
     * Check if domain looks like infrastructure/hosting (more lenient than isInfrastructureDomain)
     */
    private fun looksLikeInfrastructure(domain: String): Boolean {
        val lower = domain.lowercase()
        
        // Patterns that suggest infrastructure
        return lower.matches(Regex("^[a-z]\\d+[a-z]?-\\d+\\..*")) ||  // o5044s-259.kagoya.net
               lower.matches(Regex("^ns\\d+\\..*")) ||  // ns123.provider.com
               lower.matches(Regex("^server\\d+\\..*")) ||  // server123.provider.com
               lower.matches(Regex("^srv\\d+\\..*")) ||  // srv123.provider.com
               lower.matches(Regex("^vps\\d+\\..*")) ||  // vps123.provider.com
               lower.matches(Regex("^host\\d+\\..*")) ||  // host123.provider.com
               lower.matches(Regex("^dproxy\\..*")) ||  // dproxy.provider.com (dynamic proxy)
               lower.matches(Regex("^proxy\\d*\\..*")) ||  // proxy.provider.com or proxy1.provider.com
               lower.matches(Regex(".*\\.ip-[\\d-]+\\..*")) ||  // anything.ip-1-2-3.provider
               lower.contains("-cdn.") ||
               lower.contains(".cdn.") ||
               isInfrastructureDomain(domain)  // Use existing comprehensive check
    }
    
    private fun analyzeDomain(analyzedDomain: String, displayDomain: String, isNewDomain: Boolean, isHttp: Boolean) {
        serviceScope.launch {
            try {
                Log.i(TAG, "🔬 Starting analysis for: $analyzedDomain (HTTP: $isHttp)")
                if (analyzedDomain != displayDomain) {
                    Log.i(TAG, "   📱 Will display as: $displayDomain")
                }
                
                val analysis = threatDetector.analyze(analyzedDomain, isHttp)
                Log.i(TAG, "📊 Analysis result: $analyzedDomain = ${analysis.verdict} (${(analysis.confidence * 100).toInt()}%)")
                
                // Increment sites analyzed counter
                prefsManager.incrementSitesAnalyzed()
                
                when (analysis.verdict) {
                    ThreatDetector.Verdict.DANGEROUS -> {
                        Log.w(TAG, "🚨 DANGEROUS: $analyzedDomain (${(analysis.confidence * 100).toInt()}%)")
                        Log.w(TAG, "   Reasons: ${analysis.reasons.joinToString(", ")}")
                        
                        // Increment threats blocked counter
                        prefsManager.incrementThreatsBlocked()
                        
                        // Check notification cooldown using display domain
                        if (shouldShowNotification(displayDomain, isNewDomain)) {
                            showThreatNotification(displayDomain, analysis, true)
                            notifiedDomains[displayDomain] = System.currentTimeMillis()
                            lastNotificationTime = System.currentTimeMillis()
                            Log.w(TAG, "   ✅ Notification sent for DANGEROUS domain: $displayDomain")
                        } else {
                            Log.d(TAG, "   ⏭️ Notification skipped (cooldown period)")
                        }
                    }
                    ThreatDetector.Verdict.SUSPICIOUS -> {
                        Log.i(TAG, "⚠️ SUSPICIOUS: $analyzedDomain (${(analysis.confidence * 100).toInt()}%)")
                        Log.i(TAG, "   Reasons: ${analysis.reasons.joinToString(", ")}")
                        
                        // Increment threats blocked counter
                        prefsManager.incrementThreatsBlocked()
                        
                        // Check notification cooldown using display domain
                        if (shouldShowNotification(displayDomain, isNewDomain)) {
                            showThreatNotification(displayDomain, analysis, false)
                            notifiedDomains[displayDomain] = System.currentTimeMillis()
                            lastNotificationTime = System.currentTimeMillis()
                            Log.i(TAG, "   ✅ Notification sent for SUSPICIOUS domain: $displayDomain")
                        } else {
                            Log.d(TAG, "   ⏭️ Notification skipped (cooldown period)")
                        }
                    }
                    ThreatDetector.Verdict.SAFE -> {
                        Log.d(TAG, "✅ SAFE: $analyzedDomain")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error analyzing domain: $analyzedDomain", e)
            }
        }
    }
    
    /**
     * Check if we should show a notification for this domain
     * Returns true if:
     * 1. First time seeing this domain (isNewDomain = true), OR
     * 2. Cooldown period has passed since last notification
     * 3. NOT a bare IP (we never show notifications for IPs, only domain names)
     */
    private fun shouldShowNotification(domain: String, isNewDomain: Boolean): Boolean {
        // Check if this is a bare IP (likely a resource, not main page)
        val isIpAddress = domain.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))
        
        // NEVER show notifications for bare IPs
        // Users should only see domain names in notifications for clarity
        // IPs are analyzed for security but notifications are suppressed
        if (isIpAddress) {
            Log.d(TAG, "   ⏭️ Suppressing IP notification (showing domain names only)")
            return false
        }
        
        if (isNewDomain) {
            Log.d(TAG, "   ✅ First time - showing notification")
            return true
        }
        
        val lastDomainNotificationTime = notifiedDomains[domain]
        if (lastDomainNotificationTime == null) {
            Log.d(TAG, "   ✅ No previous notification - showing")
            return true
        }
        
        val timeSinceLastNotification = System.currentTimeMillis() - lastDomainNotificationTime
        val cooldownRemaining = NOTIFICATION_COOLDOWN_MS - timeSinceLastNotification
        
        if (timeSinceLastNotification >= NOTIFICATION_COOLDOWN_MS) {
            Log.d(TAG, "   ✅ Cooldown expired - showing notification")
            return true
        } else {
            val minutesRemaining = (cooldownRemaining / 1000 / 60).toInt()
            Log.d(TAG, "   ⏱️ Cooldown active - ${minutesRemaining}m remaining")
            return false
        }
    }
    
    private fun showThreatNotification(domain: String, analysis: ThreatDetector.ThreatAnalysis, isDangerous: Boolean) {
        Log.i(TAG, "📢 showThreatNotification called for: $domain (dangerous=$isDangerous)")
        val notificationId = domain.hashCode()
        
        // Create a high-priority channel for threats
        val threatChannelId = if (isDangerous) "phishguard_danger" else "phishguard_warning"
        val threatChannel = NotificationChannel(
            threatChannelId,
            if (isDangerous) "Phishing Alerts" else "Security Warnings",
            if (isDangerous) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alerts for detected threats"
            enableVibration(true)
            enableLights(true)
        }
        notificationManager.createNotificationChannel(threatChannel)
        Log.d(TAG, "   Channel created: $threatChannelId")
        
        val title = if (isDangerous) {
            "🛑 PHISHING DETECTED - DO NOT PROCEED"
        } else {
            "⚠️ Suspicious Site Warning"
        }
        
        val text = if (isDangerous) {
            "DANGER: $domain is likely a phishing site"
        } else {
            "Warning: $domain shows suspicious patterns"
        }
        
        val detailsText = buildString {
            append(text)
            append("\n\n")
            append("Risk Level: ${(analysis.confidence * 100).toInt()}%")
            append("\n\n")
            append("Why this is flagged:")
            analysis.reasons.forEach { reason ->
                append("\n• $reason")
            }
            if (isDangerous) {
                append("\n\n⚠️ DO NOT enter passwords or personal information!")
            }
        }
        
        Log.d(TAG, "   Building notification...")
        val notification = Notification.Builder(this, threatChannelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setStyle(Notification.BigTextStyle().bigText(detailsText))
            .setAutoCancel(true)
            .setPriority(if (isDangerous) Notification.PRIORITY_MAX else Notification.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC) // Show on lock screen
            .setColorized(true)
            .setColor(if (isDangerous) 0xFFFF0000.toInt() else 0xFFFFA500.toInt())
            .build()
        
        Log.d(TAG, "   Sending notification ID: $notificationId")
        notificationManager.notify(notificationId, notification)
        
        Log.w(TAG, "✅ THREAT NOTIFICATION SENT: $title - $domain (ID: $notificationId)")
    }
    
    /**
     * Check if domain is infrastructure/CDN/hosting that should be skipped
     */
    private fun isInfrastructureDomain(domain: String): Boolean {
        val lowerDomain = domain.lowercase()
        
        // Google infrastructure
        if (lowerDomain.endsWith(".1e100.net")) return true
        if (lowerDomain.endsWith(".googlevideo.com")) return true
        if (lowerDomain.endsWith(".gvt1.com")) return true
        
        // CDN domains
        if (lowerDomain.endsWith(".cloudfront.net")) return true
        if (lowerDomain.endsWith(".akamaiedge.net")) return true
        if (lowerDomain.endsWith(".fastly.net")) return true
        if (lowerDomain.endsWith(".fastlylb.net")) return true
        if (lowerDomain.endsWith(".edgekey.net")) return true
        if (lowerDomain.endsWith(".edgesuite.net")) return true
        
        // More CDN providers
        if (lowerDomain.endsWith(".cloudflaressl.com")) return true
        if (lowerDomain.endsWith(".cloudflare-dns.com")) return true
        
        // Facebook/Meta infrastructure
        if (lowerDomain.endsWith(".fbcdn.net")) return true
        if (lowerDomain.endsWith(".facebook.net")) return true
        
        // Other common infrastructure
        if (lowerDomain.endsWith(".cloudflare.net")) return true
        if (lowerDomain.endsWith(".amazonaws.com")) return true
        
        // Hosting provider reverse DNS patterns
        // These are generic hosting names, not actual user-facing domains
        
        // Pattern: ns*.ip-*-*-*.* (e.g., ns3227016.ip-57-128-74.eu)
        if (lowerDomain.matches(Regex("^ns\\d+\\.ip-[\\d-]+\\."))) return true
        
        // Pattern: *.ip-*-*-*.* (generic IP-based hostnames)
        if (lowerDomain.matches(Regex("^[a-z0-9-]+\\.ip-[\\d-]+\\."))) return true
        
        // Pattern: vps-*.* or vps*.* (VPS hostnames)
        if (lowerDomain.matches(Regex("^vps-?\\d+\\."))) return true
        
        // Pattern: server-*.* or srv-*.* (server hostnames)
        if (lowerDomain.matches(Regex("^(server|srv)-?\\d+\\."))) return true
        
        // Pattern: host-*.* or hostname-*.* (generic host names)
        if (lowerDomain.matches(Regex("^host(name)?-?\\d+\\."))) return true
        
        // Pattern: [letter][number]s-[number].[provider].net (e.g., o5044s-259.kagoya.net)
        if (lowerDomain.matches(Regex("^[a-z]\\d+s-\\d+\\."))) return true
        
        // Pattern: *-*-*-*.compute.amazonaws.com (AWS EC2)
        if (lowerDomain.matches(Regex(".*\\.compute\\.amazonaws\\.com$"))) return true
        
        // Pattern: *.cloudapp.net (Azure)
        if (lowerDomain.endsWith(".cloudapp.net")) return true
        if (lowerDomain.endsWith(".cloudapp.azure.com")) return true
        if (lowerDomain.endsWith(".azureedge.net")) return true
        if (lowerDomain.endsWith(".blob.core.windows.net")) return true
        if (lowerDomain.endsWith(".azure.com")) return true
        
        // Pattern: *.googleusercontent.com (Google Cloud)
        if (lowerDomain.endsWith(".googleusercontent.com")) return true
        
        // Pattern: *.awsglobalaccelerator.com (AWS Global Accelerator)
        if (lowerDomain.endsWith(".awsglobalaccelerator.com")) return true
        
        // Pattern: *.elb.amazonaws.com (AWS Elastic Load Balancer)
        if (lowerDomain.endsWith(".elb.amazonaws.com")) return true
        
        // Specific hosting providers (by domain)
        val hostingProviders = listOf(
            ".ovh.", ".ovhcloud.com",  // OVH
            ".linode.", ".linode.com",  // Linode
            ".digitalocean.", ".digitaloceanspaces.com",  // DigitalOcean
            ".vultr.", ".vultr.com",  // Vultr
            ".hetzner.", ".hetzner.com", ".hetzner.de",  // Hetzner
            ".contabo.", ".contabo.com", ".contabo.net",  // Contabo
            ".scaleway.", ".scaleway.com",  // Scaleway
            ".rackspace.", ".rackspace.com",  // Rackspace
            ".dreamhost.", ".dreamhost.com",  // DreamHost
            ".hostgator.", ".hostgator.com",  // HostGator
            ".bluehost.", ".bluehost.com",  // Bluehost
            ".godaddy.", ".godaddy.com",  // GoDaddy
            ".namecheap.", ".namecheap.com",  // Namecheap
            ".ionos.", ".ionos.com",  // IONOS
            ".1and1.", "1and1.com",  // 1&1
            ".kagoya.", ".kagoya.net", ".kagoya.com"  // Kagoya (Japanese hosting)
        )
        
        if (hostingProviders.any { lowerDomain.contains(it) }) return true
        
        return false
    }
    
    private fun stopVpn() {
        Log.d(TAG, "Stopping VPN")
        isRunning = false
        
        try {
            dnsMonitor?.stop()
            dnsMonitor = null
            
            socksProxy?.stop()
            socksProxy = null
            
            ipMonitor?.stop()
            ipMonitor = null
            
            tun2socksManager?.stop()
            tun2socksManager = null
            
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
        threatDetector.close()
        serviceScope.cancel()
        
        try {
            unregisterReceiver(settingsBroadcastReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        
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
            .setContentText("Tun2Socks forwarding - Internet working")
            .setSmallIcon(android.R.drawable.ic_secure)
            .setOngoing(true)
            .setStyle(Notification.BigTextStyle()
                .bigText("PhishGuard Active\n\n✓ Tun2Socks packet forwarding\n✓ Monitoring all traffic\n✓ Threat detection active\n✓ Internet: Working")
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
        const val ACTION_CLEAR_CACHE = "com.phishguard.action.CLEAR_CACHE"
        const val ACTION_UPDATE_CACHE_DURATION = "com.phishguard.action.UPDATE_CACHE_DURATION"
    }
}
