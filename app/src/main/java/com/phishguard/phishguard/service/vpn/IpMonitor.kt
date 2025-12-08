package com.phishguard.phishguard.service.vpn

import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress

/**
 * Monitors IP addresses from tun2socks logs and performs reverse DNS lookup
 * to extract hostnames for threat analysis
 */
class IpMonitor(
    private val onDomainDetected: (String) -> Unit
) {
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val seenIps = mutableSetOf<String>()
    private val seenDomains = mutableSetOf<String>()
    
    companion object {
        private const val TAG = "IpMonitor"
        
        // Known CDN/Cloud provider IP ranges (simplified)
        private val CDN_PROVIDERS = setOf(
            "cloudflare", "akamai", "fastly", "cloudfront", "cdn"
        )
    }
    
    fun start() {
        if (isRunning) return
        isRunning = true
        
        scope.launch {
            monitorTun2SocksLogs()
        }
        
        Log.i(TAG, "IP monitor started")
    }
    
    fun stop() {
        isRunning = false
        scope.cancel()
        seenIps.clear()
        Log.i(TAG, "IP monitor stopped")
    }
    
    /**
     * Monitor tun2socks logs for both DNS queries and IP connections
     */
    private suspend fun monitorTun2SocksLogs() {
        try {
            // Monitor logcat for tun2socks tunnel logs
            // We want both UDP (DNS) and TCP (connections)
            val process = Runtime.getRuntime().exec(
                arrayOf("logcat", "-v", "brief", "tunnel:I", "*:S")
            )
            
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            Log.i(TAG, "Monitoring tun2socks logs for DNS queries and connections...")
            
            val dnsCache = mutableMapOf<String, String>() // IP -> domain mapping
            
            while (isRunning) {
                val line = reader.readLine() ?: break
                
                // Priority 1: Parse DNS queries (UDP to port 53)
                // Format: [UDP] 10.0.0.2:54321 <-> 8.8.8.8:53
                if (line.contains("UDP") && line.contains(":53")) {
                    // DNS query detected - we need to parse the actual DNS packet
                    // For now, just log it
                    Log.d(TAG, "DNS query: $line")
                }
                
                // Priority 2: Parse TCP/HTTPS connections
                // Format: [TCP] 10.0.0.2:12345 <-> 212.85.28.32:443
                if (line.contains("TCP")) {
                    val ip = extractDestinationIp(line)
                    if (ip != null && !seenIps.contains(ip)) {
                        seenIps.add(ip)
                        Log.d(TAG, "New connection to IP: $ip")
                        
                        // Try reverse DNS lookup
                        scope.launch {
                            val hostname = performReverseDns(ip)
                            if (hostname != null) {
                                Log.i(TAG, "Resolved $ip -> $hostname")
                                dnsCache[ip] = hostname
                                onDomainDetected(hostname)
                            } else {
                                Log.w(TAG, "No reverse DNS for $ip (likely CDN/Cloudflare)")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (isRunning) {
                Log.e(TAG, "Error monitoring logs", e)
            }
        }
    }
    
    /**
     * Extract destination IP from tun2socks log line
     */
    private fun extractDestinationIp(line: String): String? {
        try {
            // Pattern: 10.0.0.2:port <-> DESTINATION_IP:port
            val parts = line.split("<->")
            if (parts.size != 2) return null
            
            val destination = parts[1].trim()
            val ipAndPort = destination.split(":")
            if (ipAndPort.isEmpty()) return null
            
            val ip = ipAndPort[0].trim()
            
            // Filter out DNS servers and local IPs
            if (ip == "8.8.8.8" || ip == "8.8.4.4" || ip.startsWith("10.") || ip.startsWith("192.168.")) {
                return null
            }
            
            // Validate IP format
            if (ip.matches(Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"))) {
                return ip
            }
            
            return null
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * Perform reverse DNS lookup to get hostname from IP
     */
    private suspend fun performReverseDns(ip: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val address = InetAddress.getByName(ip)
                val hostname = address.canonicalHostName
                
                // Check if we got a real hostname (not just the IP back)
                if (hostname != ip && !hostname.matches(Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"))) {
                    hostname
                } else {
                    Log.d(TAG, "No reverse DNS for $ip")
                    null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Reverse DNS failed for $ip: ${e.message}")
                null
            }
        }
    }
}
