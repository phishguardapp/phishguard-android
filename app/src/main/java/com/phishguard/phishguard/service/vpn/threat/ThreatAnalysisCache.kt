package com.phishguard.phishguard.service.vpn.threat

import android.util.Log
import com.phishguard.phishguard.service.vpn.ThreatDetector
import java.util.concurrent.ConcurrentHashMap

/**
 * Caches threat analysis results to improve performance
 * Entries expire after 24 hours
 * Maximum 1000 entries with LRU eviction
 */
class ThreatAnalysisCache {
    
    companion object {
        private const val TAG = "ThreatAnalysisCache"
        private const val MAX_ENTRIES = 1000
        private const val TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
    }
    
    private val cache = ConcurrentHashMap<String, CachedAnalysis>()
    
    /**
     * Get cached analysis if available and not expired
     */
    fun get(domain: String): ThreatDetector.ThreatAnalysis? {
        val cached = cache[domain] ?: return null
        
        val age = System.currentTimeMillis() - cached.timestamp
        if (age > TTL_MS) {
            // Expired, remove it
            cache.remove(domain)
            Log.d(TAG, "Cache expired for $domain")
            return null
        }
        
        Log.d(TAG, "Cache hit for $domain (age: ${age / 1000}s)")
        return cached.analysis
    }
    
    /**
     * Store analysis result in cache
     */
    fun put(domain: String, analysis: ThreatDetector.ThreatAnalysis) {
        // Evict old entries if cache is full
        if (cache.size >= MAX_ENTRIES) {
            evictOldEntries()
        }
        
        cache[domain] = CachedAnalysis(
            analysis = analysis,
            timestamp = System.currentTimeMillis()
        )
        
        Log.d(TAG, "Cached analysis for $domain (cache size: ${cache.size})")
    }
    
    /**
     * Clear all cached entries
     */
    fun clear() {
        cache.clear()
        Log.d(TAG, "Cache cleared")
    }
    
    /**
     * Evict oldest entries when cache is full
     * Removes 10% of entries (100 entries)
     */
    private fun evictOldEntries() {
        val entriesToRemove = MAX_ENTRIES / 10
        
        val sortedEntries = cache.entries
            .sortedBy { it.value.timestamp }
            .take(entriesToRemove)
        
        sortedEntries.forEach { entry ->
            cache.remove(entry.key)
        }
        
        Log.d(TAG, "Evicted $entriesToRemove old entries from cache")
    }
    
    /**
     * Get cache statistics
     */
    fun getStats(): CacheStats {
        val now = System.currentTimeMillis()
        var expired = 0
        
        cache.values.forEach { cached ->
            if (now - cached.timestamp > TTL_MS) {
                expired++
            }
        }
        
        return CacheStats(
            totalEntries = cache.size,
            expiredEntries = expired
        )
    }
    
    data class CachedAnalysis(
        val analysis: ThreatDetector.ThreatAnalysis,
        val timestamp: Long
    )
    
    data class CacheStats(
        val totalEntries: Int,
        val expiredEntries: Int
    )
}
