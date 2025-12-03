package com.phishguard.phishguard.service.vpn

import android.util.Log

/**
 * Basic threat detection engine
 * Phase 1: Simple heuristics and known patterns
 * Phase 2+: Will integrate ML models and advanced feature extraction
 */
class ThreatDetector {
    
    companion object {
        private const val TAG = "ThreatDetector"
    }
    
    data class ThreatAnalysis(
        val domain: String,
        val verdict: Verdict,
        val confidence: Float,
        val reasons: List<String>
    )
    
    enum class Verdict {
        SAFE,
        SUSPICIOUS,
        DANGEROUS
    }
    
    // Legitimate domains that should always be safe (allowlist)
    private val legitimateDomains = setOf(
        // Major tech companies
        "google.com", "googleapis.com", "gstatic.com", "googleusercontent.com",
        "google-analytics.com", "googlevideo.com", "youtube.com", "ytimg.com",
        "facebook.com", "fbcdn.net", "facebook.net",
        "apple.com", "icloud.com", "apple-cloudkit.com", "mzstatic.com",
        "microsoft.com", "live.com", "outlook.com", "office.com", "windows.com",
        "amazon.com", "amazonaws.com", "cloudfront.net",
        "github.com", "githubusercontent.com",
        "twitter.com", "twimg.com",
        "linkedin.com", "licdn.com",
        "instagram.com", "cdninstagram.com",
        
        // CDNs and infrastructure
        "cloudflare.com", "cloudflare.net", "fastly.net", "fastly-edge.com",
        "akamai.net", "akamaiedge.net", "edgekey.net",
        
        // Common services
        "wikipedia.org", "wikimedia.org",
        "reddit.com", "redd.it", "redditstatic.com",
        "stackoverflow.com", "stackexchange.com",
        "mozilla.org", "firefox.com"
    )
    
    // Simple pattern-based detection for Phase 1
    private val suspiciousPatterns = listOf(
        "login",
        "verify",
        "account",
        "update",
        "confirm",
        "banking"
    )
    
    private val dangerousTlds = setOf(
        ".tk", ".ml", ".ga", ".cf", ".gq", // Free TLDs often used for phishing
        ".xyz", ".top", ".work", ".click"
    )
    
    private val knownPhishingDomains = setOf(
        // Add known phishing domains for testing
        "phishing-test.com",
        "fake-bank.com",
        "scam-site.xyz"
    )
    
    fun analyze(domain: String): ThreatAnalysis {
        val reasons = mutableListOf<String>()
        var suspicionScore = 0f
        val lowerDomain = domain.lowercase()
        
        // Check if domain is in legitimate allowlist
        // Check exact match or if it's a subdomain of a legitimate domain
        val isLegitimate = legitimateDomains.any { legitDomain ->
            lowerDomain == legitDomain || lowerDomain.endsWith(".$legitDomain")
        }
        
        if (isLegitimate) {
            return ThreatAnalysis(
                domain = domain,
                verdict = Verdict.SAFE,
                confidence = 0.95f,
                reasons = listOf("Known legitimate domain")
            )
        }
        
        // Check against known phishing domains
        if (knownPhishingDomains.contains(lowerDomain)) {
            return ThreatAnalysis(
                domain = domain,
                verdict = Verdict.DANGEROUS,
                confidence = 0.95f,
                reasons = listOf("Known phishing domain")
            )
        }
        
        // Check for suspicious TLDs
        dangerousTlds.forEach { tld ->
            if (domain.endsWith(tld, ignoreCase = true)) {
                suspicionScore += 0.3f
                reasons.add("Uses suspicious TLD: $tld")
            }
        }
        
        // Check for suspicious keywords
        suspiciousPatterns.forEach { pattern ->
            if (lowerDomain.contains(pattern)) {
                suspicionScore += 0.2f
                reasons.add("Contains suspicious keyword: $pattern")
            }
        }
        
        // Check for excessive subdomains (common in phishing)
        val subdomainCount = domain.count { it == '.' }
        if (subdomainCount > 3) {
            suspicionScore += 0.2f
            reasons.add("Excessive subdomains ($subdomainCount)")
        }
        
        // Check for suspicious characters
        if (domain.contains('-') && suspiciousPatterns.any { lowerDomain.contains(it) }) {
            suspicionScore += 0.15f
            reasons.add("Hyphenated domain with suspicious keywords")
        }
        
        // Check for IP address instead of domain
        if (domain.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
            suspicionScore += 0.4f
            reasons.add("Direct IP address instead of domain")
        }
        
        // Determine verdict based on score
        val verdict = when {
            suspicionScore >= 0.6f -> Verdict.DANGEROUS
            suspicionScore >= 0.3f -> Verdict.SUSPICIOUS
            else -> Verdict.SAFE
        }
        
        val confidence = when (verdict) {
            Verdict.DANGEROUS -> minOf(0.95f, 0.5f + suspicionScore)
            Verdict.SUSPICIOUS -> minOf(0.85f, 0.4f + suspicionScore)
            Verdict.SAFE -> 0.7f
        }
        
        if (verdict != Verdict.SAFE) {
            Log.d(TAG, "Threat detected: $domain - $verdict (${confidence * 100}%)")
            reasons.forEach { Log.d(TAG, "  - $it") }
        }
        
        return ThreatAnalysis(
            domain = domain,
            verdict = verdict,
            confidence = confidence,
            reasons = reasons
        )
    }
}
