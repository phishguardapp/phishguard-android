package com.phishguard.phishguard.service.vpn.threat

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Checks domain registration age using RDAP (Registration Data Access Protocol)
 * Newly registered domains are more suspicious
 */
class DomainAgeChecker {
    
    companion object {
        private const val TAG = "DomainAgeChecker"
        private const val TIMEOUT_MS = 3000L
        private const val CACHE_TTL_MS = 30 * 24 * 60 * 60 * 1000L // 30 days
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()
    
    private val cache = ConcurrentHashMap<String, CachedAge>()
    
    data class DomainAgeResult(
        val domain: String,
        val registrationDate: Date?,
        val ageInDays: Int?,
        val source: String // "RDAP" or "WHOIS"
    )
    
    private data class CachedAge(
        val result: DomainAgeResult?,
        val timestamp: Long
    )
    
    /**
     * Get domain age
     * Returns null if age cannot be determined
     */
    suspend fun getDomainAge(domain: String): DomainAgeResult? = withContext(Dispatchers.IO) {
        // Check cache first
        val cached = cache[domain]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
            Log.d(TAG, "Cache hit for $domain: ${cached.result?.ageInDays} days")
            return@withContext cached.result
        }
        
        try {
            withTimeout(TIMEOUT_MS) {
                // Try RDAP first (modern, structured API)
                val result = queryRDAP(domain)
                
                // Cache the result
                cache[domain] = CachedAge(result, System.currentTimeMillis())
                
                // Evict old entries if cache is too large
                if (cache.size > 1000) {
                    evictOldEntries()
                }
                
                result
            }
        } catch (e: Exception) {
            Log.w(TAG, "Domain age check failed for $domain: ${e.message}")
            // Cache the failure
            cache[domain] = CachedAge(null, System.currentTimeMillis())
            null
        }
    }
    
    /**
     * Query RDAP for domain information
     * RDAP is the modern replacement for WHOIS with structured JSON responses
     */
    private fun queryRDAP(domain: String): DomainAgeResult? {
        return try {
            // Extract TLD to determine RDAP server
            val tld = domain.substringAfterLast('.')
            val rdapUrl = getRDAPServerForTLD(tld)
            
            if (rdapUrl == null) {
                Log.d(TAG, "No RDAP server known for TLD: $tld")
                return null
            }
            
            val request = Request.Builder()
                .url("$rdapUrl/domain/$domain")
                .build()
            
            val response = client.newCall(request).execute()
            
            response.use {
                if (it.isSuccessful) {
                    val body = it.body?.string()
                    if (body != null) {
                        parseRDAPResponse(domain, body)
                    } else {
                        null
                    }
                } else {
                    Log.d(TAG, "RDAP query returned ${it.code} for $domain")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "RDAP query failed: ${e.message}")
            null
        }
    }
    
    /**
     * Get RDAP server URL for a TLD
     */
    private fun getRDAPServerForTLD(tld: String): String? {
        return when (tld.lowercase()) {
            "com", "net" -> "https://rdap.verisign.com/com/v1"
            "org" -> "https://rdap.publicinterestregistry.org"
            "in" -> "https://rdap.registry.in"
            "uk" -> "https://rdap.nominet.uk"
            "de" -> "https://rdap.denic.de"
            "fr" -> "https://rdap.nic.fr"
            "au" -> "https://rdap.ausregistry.net.au"
            "ca" -> "https://rdap.ca.fury.ca"
            "io" -> "https://rdap.nic.io"
            "co" -> "https://rdap.nic.co"
            else -> "https://rdap.org/domain" // Generic RDAP bootstrap
        }
    }
    
    /**
     * Parse RDAP JSON response
     */
    private fun parseRDAPResponse(domain: String, jsonString: String): DomainAgeResult? {
        return try {
            val json = JSONObject(jsonString)
            
            // Look for registration date in events array
            val events = json.optJSONArray("events")
            var registrationDate: Date? = null
            
            if (events != null) {
                for (i in 0 until events.length()) {
                    val event = events.getJSONObject(i)
                    val eventAction = event.optString("eventAction")
                    
                    if (eventAction == "registration") {
                        val dateString = event.optString("eventDate")
                        registrationDate = parseISODate(dateString)
                        break
                    }
                }
            }
            
            if (registrationDate != null) {
                val ageInDays = calculateAgeInDays(registrationDate)
                
                Log.i(TAG, "Domain $domain registered on $registrationDate ($ageInDays days ago)")
                
                DomainAgeResult(
                    domain = domain,
                    registrationDate = registrationDate,
                    ageInDays = ageInDays,
                    source = "RDAP"
                )
            } else {
                Log.d(TAG, "No registration date found in RDAP response for $domain")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing RDAP response: ${e.message}")
            null
        }
    }
    
    /**
     * Parse ISO 8601 date string
     */
    private fun parseISODate(dateString: String): Date? {
        return try {
            val formats = listOf(
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
                SimpleDateFormat("yyyy-MM-dd", Locale.US)
            )
            
            for (format in formats) {
                try {
                    return format.parse(dateString)
                } catch (e: Exception) {
                    // Try next format
                }
            }
            
            null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Calculate age in days from registration date
     */
    private fun calculateAgeInDays(registrationDate: Date): Int {
        val now = Date()
        val diffInMillis = now.time - registrationDate.time
        return (diffInMillis / (1000 * 60 * 60 * 24)).toInt()
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
     * Clear all cached results
     */
    fun clearCache() {
        cache.clear()
        Log.d(TAG, "Cache cleared")
    }
}
