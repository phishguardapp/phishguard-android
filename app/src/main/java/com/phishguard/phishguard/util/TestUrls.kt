package com.phishguard.phishguard.util

/**
 * Test URLs for validating PhishGuard detection
 * Use these to test the VPN service and threat detection
 */
object TestUrls {
    
    // Safe domains (should pass without warnings)
    val SAFE_DOMAINS = listOf(
        "google.com",
        "github.com",
        "stackoverflow.com",
        "wikipedia.org",
        "amazon.com"
    )
    
    // Suspicious domains (should trigger warnings)
    val SUSPICIOUS_DOMAINS = listOf(
        "secure-login-verify.tk",
        "account-update-required.ml",
        "paypal-security-check.xyz",
        "amazon-prime-renewal.click",
        "apple-id-verification.top"
    )
    
    // Known phishing test domains
    val PHISHING_DOMAINS = listOf(
        "phishing-test.com",
        "fake-bank.com",
        "scam-site.xyz"
    )
    
    // Domains with suspicious patterns
    val PATTERN_TEST_DOMAINS = listOf(
        "secure-login-microsoft.com",
        "verify-account-google.net",
        "banking-update-chase.org",
        "192.168.1.1", // IP address
        "sub1.sub2.sub3.sub4.example.com" // Excessive subdomains
    )
}
