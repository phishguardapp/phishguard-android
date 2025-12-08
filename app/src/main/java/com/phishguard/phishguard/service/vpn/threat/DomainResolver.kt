package com.phishguard.phishguard.service.vpn.threat

import android.util.Log
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves IP addresses to domain names using multiple methods:
 * 1. DNS cache (from DnsMonitor)
 * 2. SNI extraction (from TLS handshakes)
 * 3. Reverse DNS lookup (fallback)
 */
class DomainResolver {
    
    companion object {
        private const val TAG = "DomainResolver"
        private val IP_REGEX = Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")
    }
    
    // Cache of IP -> Domain mappings from DNS queries
    private val dnsCache = ConcurrentHashMap<String, String>()
    
    // Cache of IP -> Domain mappings from SNI
    private val sniCache = ConcurrentHashMap<String, String>()
    
    /**
     * Check if input is an IP address
     */
    fun isIpAddress(input: String): Boolean {
        return IP_REGEX.matches(input)
    }
    
    /**
     * Add a DNS resolution to the cache
     * Called by DnsMonitor when it sees DNS queries
     */
    fun cacheDnsResolution(domain: String, ipAddress: String) {
        dnsCache[ipAddress] = domain
        Log.d(TAG, "Cached DNS: $ipAddress -> $domain")
    }
    
    /**
     * Add an SNI extraction to the cache
     * Called when SNI is extracted from TLS handshakes
     */
    fun cacheSniResolution(domain: String, ipAddress: String) {
        sniCache[ipAddress] = domain
        Log.d(TAG, "Cached SNI: $ipAddress -> $domain")
    }
    
    /**
     * Get domain name from cache (DNS or SNI)
     */
    fun getDomainFromCache(ipAddress: String): String? {
        // Try SNI cache first (more reliable for HTTPS)
        sniCache[ipAddress]?.let {
            Log.d(TAG, "Found in SNI cache: $ipAddress -> $it")
            return it
        }
        
        // Try DNS cache
        dnsCache[ipAddress]?.let {
            Log.d(TAG, "Found in DNS cache: $ipAddress -> $it")
            return it
        }
        
        return null
    }
    
    /**
     * Resolve IP address to domain name using all available methods
     * 
     * @param ipAddress The IP address to resolve
     * @return Domain name if found, null otherwise
     */
    fun resolveIpToDomain(ipAddress: String): String? {
        if (!isIpAddress(ipAddress)) {
            // Already a domain name
            return ipAddress
        }
        
        // Check cache first
        getDomainFromCache(ipAddress)?.let {
            return it
        }
        
        // Try reverse DNS lookup as fallback
        return performReverseDnsLookup(ipAddress)
    }
    
    /**
     * Perform reverse DNS lookup
     * This is a blocking operation and should be called from a background thread
     */
    private fun performReverseDnsLookup(ipAddress: String): String? {
        return try {
            val address = InetAddress.getByName(ipAddress)
            val hostname = address.canonicalHostName
            
            // Check if we got a real hostname (not just the IP back)
            if (hostname != ipAddress && !isIpAddress(hostname)) {
                Log.d(TAG, "Reverse DNS: $ipAddress -> $hostname")
                // Cache the result
                dnsCache[ipAddress] = hostname
                hostname
            } else {
                Log.d(TAG, "Reverse DNS failed for $ipAddress")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Reverse DNS lookup failed for $ipAddress: ${e.message}")
            null
        }
    }
    
    /**
     * Extract SNI from TLS packet
     * Delegates to SniExtractor
     */
    fun extractSniFromTls(packet: ByteArray): String? {
        return try {
            com.phishguard.phishguard.service.vpn.SniExtractor.extractSni(packet)
        } catch (e: Exception) {
            Log.w(TAG, "SNI extraction failed: ${e.message}")
            null
        }
    }
    
    /**
     * Clear all caches
     */
    fun clearCache() {
        dnsCache.clear()
        sniCache.clear()
        Log.d(TAG, "Caches cleared")
    }
    
    /**
     * Get cache statistics for debugging
     */
    fun getCacheStats(): CacheStats {
        return CacheStats(
            dnsEntries = dnsCache.size,
            sniEntries = sniCache.size
        )
    }
    
    data class CacheStats(
        val dnsEntries: Int,
        val sniEntries: Int
    )
}
