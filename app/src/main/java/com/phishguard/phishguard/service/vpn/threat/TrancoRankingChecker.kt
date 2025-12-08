package com.phishguard.phishguard.service.vpn.threat

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Checks domain popularity using Tranco ranking
 * Popular domains are less likely to be phishing sites
 */
class TrancoRankingChecker {
    
    companion object {
        private const val TAG = "TrancoRankingChecker"
        private const val TRANCO_API_URL = "https://tranco-list.eu/api/ranks/domain/"
        private const val TIMEOUT_MS = 2000L
        private const val CACHE_TTL_MS = 7 * 24 * 60 * 60 * 1000L // 7 days
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()
    
    private val cache = ConcurrentHashMap<String, CachedRanking>()
    
    data class TrancoRanking(
        val domain: String,
        val rank: Int,
        val isTopTenThousand: Boolean,
        val isTopHundredThousand: Boolean,
        val isTopMillion: Boolean
    )
    
    private data class CachedRanking(
        val ranking: TrancoRanking?,
        val timestamp: Long
    )
    
    /**
     * Get Tranco ranking for a domain
     * Returns null if domain is not ranked or API fails
     */
    suspend fun getRanking(domain: String): TrancoRanking? = withContext(Dispatchers.IO) {
        // Check cache first
        val cached = cache[domain]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
            Log.d(TAG, "Cache hit for $domain: ${cached.ranking?.rank}")
            return@withContext cached.ranking
        }
        
        try {
            withTimeout(TIMEOUT_MS) {
                val ranking = queryTrancoAPI(domain)
                
                // Cache the result (even if null)
                cache[domain] = CachedRanking(ranking, System.currentTimeMillis())
                
                // Evict old entries if cache is too large
                if (cache.size > 1000) {
                    evictOldEntries()
                }
                
                ranking
            }
        } catch (e: Exception) {
            Log.w(TAG, "Tranco API query failed for $domain: ${e.message}")
            // Cache the failure to avoid repeated API calls
            cache[domain] = CachedRanking(null, System.currentTimeMillis())
            null
        }
    }
    
    /**
     * Query Tranco API for domain ranking
     */
    private fun queryTrancoAPI(domain: String): TrancoRanking? {
        return try {
            val request = Request.Builder()
                .url("$TRANCO_API_URL$domain")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    parseTrancoResponse(domain, body)
                } else {
                    null
                }
            } else {
                Log.d(TAG, "Tranco API returned ${response.code} for $domain")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying Tranco API: ${e.message}")
            null
        }
    }
    
    /**
     * Parse Tranco API response
     * Example response: {"ranks":[{"rank":123,"date":"2024-01-01"}]}
     */
    private fun parseTrancoResponse(domain: String, jsonString: String): TrancoRanking? {
        return try {
            val json = JSONObject(jsonString)
            val ranks = json.getJSONArray("ranks")
            
            if (ranks.length() > 0) {
                val latestRank = ranks.getJSONObject(0)
                val rank = latestRank.getInt("rank")
                
                Log.i(TAG, "Tranco ranking for $domain: $rank")
                
                TrancoRanking(
                    domain = domain,
                    rank = rank,
                    isTopTenThousand = rank <= 10_000,
                    isTopHundredThousand = rank <= 100_000,
                    isTopMillion = rank <= 1_000_000
                )
            } else {
                Log.d(TAG, "No ranking found for $domain")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing Tranco response: ${e.message}")
            null
        }
    }
    
    /**
     * Evict old cache entries
     */
    private fun evictOldEntries() {
        val now = System.currentTimeMillis()
        val toRemove = cache.entries
            .filter { now - it.value.timestamp > CACHE_TTL_MS }
            .map { it.key }
        
        toRemove.forEach { cache.remove(it) }
        
        Log.d(TAG, "Evicted ${toRemove.size} old cache entries")
    }
    
    /**
     * Clear all cached rankings
     */
    fun clearCache() {
        cache.clear()
        Log.d(TAG, "Cache cleared")
    }
}
