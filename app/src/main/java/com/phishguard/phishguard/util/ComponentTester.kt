package com.phishguard.phishguard.util

import android.util.Log
import com.phishguard.phishguard.service.vpn.ThreatDetector

/**
 * Utility to test PhishGuard components without requiring VPN connectivity
 * Use this to verify threat detection logic works correctly
 */
object ComponentTester {
    
    private const val TAG = "ComponentTester"
    
    /**
     * Test the threat detector with various domains
     * Call this from MainActivity to see results in Logcat
     */
    fun testThreatDetector() {
        Log.i(TAG, "=== Testing Threat Detector ===")
        
        val detector = ThreatDetector()
        
        // Test safe domains
        Log.i(TAG, "\n--- Testing Safe Domains ---")
        testDomain(detector, "google.com")
        testDomain(detector, "github.com")
        testDomain(detector, "wikipedia.org")
        testDomain(detector, "amazon.com")
        
        // Test suspicious domains
        Log.i(TAG, "\n--- Testing Suspicious Domains ---")
        testDomain(detector, "secure-login-verify.tk")
        testDomain(detector, "account-update-required.ml")
        testDomain(detector, "paypal-security-check.xyz")
        testDomain(detector, "verify-account-google.net")
        testDomain(detector, "banking-update-chase.org")
        
        // Test dangerous patterns
        Log.i(TAG, "\n--- Testing Dangerous Patterns ---")
        testDomain(detector, "phishing-test.com")
        testDomain(detector, "fake-bank.com")
        testDomain(detector, "192.168.1.1")
        testDomain(detector, "sub1.sub2.sub3.sub4.example.com")
        testDomain(detector, "secure-login-microsoft-verify.tk")
        
        Log.i(TAG, "\n=== Threat Detector Test Complete ===")
    }
    
    private fun testDomain(detector: ThreatDetector, domain: String) {
        val analysis = detector.analyze(domain)
        
        val verdictEmoji = when (analysis.verdict) {
            ThreatDetector.Verdict.SAFE -> "✅"
            ThreatDetector.Verdict.SUSPICIOUS -> "⚠️"
            ThreatDetector.Verdict.DANGEROUS -> "🛑"
        }
        
        Log.i(TAG, "$verdictEmoji $domain")
        Log.i(TAG, "   Verdict: ${analysis.verdict}")
        Log.i(TAG, "   Confidence: ${(analysis.confidence * 100).toInt()}%")
        
        if (analysis.reasons.isNotEmpty()) {
            Log.i(TAG, "   Reasons:")
            analysis.reasons.forEach { reason ->
                Log.i(TAG, "     • $reason")
            }
        }
    }
    
    /**
     * Get test results as a formatted string for UI display
     */
    fun getTestResults(): String {
        val detector = ThreatDetector()
        val results = StringBuilder()
        
        results.appendLine("PhishGuard Component Test Results\n")
        
        // Test a few key domains
        val testDomains = listOf(
            "google.com" to "Safe",
            "secure-login-verify.tk" to "Suspicious",
            "phishing-test.com" to "Dangerous"
        )
        
        testDomains.forEach { (domain, expected) ->
            val analysis = detector.analyze(domain)
            val verdict = analysis.verdict.toString()
            val confidence = (analysis.confidence * 100).toInt()
            
            results.appendLine("$domain")
            results.appendLine("  Expected: $expected")
            results.appendLine("  Got: $verdict ($confidence%)")
            results.appendLine()
        }
        
        return results.toString()
    }
}
