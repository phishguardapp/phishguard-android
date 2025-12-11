package com.phishguard.phishguard.service.vpn.threat

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Validates SSL certificates for HTTPS connections
 * Checks for expired, self-signed, and hostname mismatches
 */
class SSLCertificateValidator {
    
    companion object {
        private const val TAG = "SSLCertificateValidator"
        private const val TIMEOUT_MS = 3000L
    }
    
    data class CertificateValidation(
        val isValid: Boolean,
        val isSelfSigned: Boolean,
        val isExpired: Boolean,
        val hostnameMatches: Boolean,
        val issuer: String?,
        val subject: String?,
        val subjectAlternativeNames: List<String>?,
        val expiryDate: Date?,
        val daysUntilExpiry: Int?,
        val validityPeriodDays: Int?,
        val hasShortValidityPeriod: Boolean
    )
    
    /**
     * Validate SSL certificate for a domain
     * Returns null if validation fails or times out
     */
    suspend fun validateCertificate(domain: String): CertificateValidation? = withContext(Dispatchers.IO) {
        try {
            withTimeout(TIMEOUT_MS) {
                val url = java.net.URL("https://$domain")
                val connection = url.openConnection() as HttpsURLConnection
                
                // Create a trust manager that captures the certificate
                var capturedCert: X509Certificate? = null
                val trustManager = object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                        if (chain.isNotEmpty()) {
                            capturedCert = chain[0]
                        }
                    }
                    
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }
                
                // Set up SSL context with our trust manager
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, arrayOf<TrustManager>(trustManager), null)
                connection.sslSocketFactory = sslContext.socketFactory
                
                // Disable hostname verification to capture cert even if it doesn't match
                connection.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                
                connection.connectTimeout = TIMEOUT_MS.toInt()
                connection.readTimeout = TIMEOUT_MS.toInt()
                
                try {
                    connection.connect()
                    connection.disconnect()
                } catch (e: Exception) {
                    // Connection might fail, but we may have captured the cert
                    Log.d(TAG, "Connection failed but may have cert: ${e.message}")
                }
                
                val cert = capturedCert
                if (cert != null) {
                    analyzeCertificate(cert, domain)
                } else {
                    Log.w(TAG, "Could not capture certificate for $domain")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "SSL validation failed for $domain: ${e.message}")
            null
        }
    }
    
    /**
     * Analyze a captured certificate
     */
    private fun analyzeCertificate(cert: X509Certificate, domain: String): CertificateValidation {
        val now = Date()
        
        // Check if expired
        val isExpired = try {
            cert.checkValidity(now)
            false
        } catch (e: Exception) {
            true
        }
        
        // Check if self-signed
        val isSelfSigned = try {
            cert.issuerDN.name == cert.subjectDN.name
        } catch (e: Exception) {
            false
        }
        
        // Check hostname match
        val hostnameMatches = try {
            val subjectDN = cert.subjectDN.name
            subjectDN.contains("CN=$domain", ignoreCase = true) ||
            subjectDN.contains("CN=*.$domain", ignoreCase = true) ||
            checkSubjectAlternativeNames(cert, domain)
        } catch (e: Exception) {
            false
        }
        
        // Get issuer
        val issuer = try {
            cert.issuerDN.name
        } catch (e: Exception) {
            null
        }
        
        // Get subject (contains the domain name)
        val subject = try {
            cert.subjectDN.name
        } catch (e: Exception) {
            null
        }
        
        // Get Subject Alternative Names
        val sanList = try {
            val sans = cert.subjectAlternativeNames
            sans?.mapNotNull { san ->
                if (san.size >= 2 && san[0] == 2) { // Type 2 = DNS name
                    san[1].toString()
                } else null
            }
        } catch (e: Exception) {
            null
        }
        
        // Get expiry date and days until expiry
        val expiryDate = try {
            cert.notAfter
        } catch (e: Exception) {
            null
        }
        
        val daysUntilExpiry = if (expiryDate != null) {
            val diff = expiryDate.time - now.time
            (diff / (1000 * 60 * 60 * 24)).toInt()
        } else {
            null
        }
        
        // Calculate certificate validity period (issued date to expiry date)
        val validityPeriodDays = try {
            val issuedDate = cert.notBefore
            if (expiryDate != null && issuedDate != null) {
                val diff = expiryDate.time - issuedDate.time
                (diff / (1000 * 60 * 60 * 24)).toInt()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
        
        // Check for suspiciously short validity period
        // Industry standard: 398 days max (since 2020), moving toward 90 days
        // Phishing sites often use very short certificates (< 30 days)
        // Flag certificates shorter than 30 days as highly suspicious
        val hasShortValidityPeriod = validityPeriodDays != null && validityPeriodDays < 30
        
        val isValid = !isExpired && !isSelfSigned && hostnameMatches
        
        Log.d(TAG, "Certificate analysis for $domain:")
        Log.d(TAG, "  Valid: $isValid")
        Log.d(TAG, "  Self-signed: $isSelfSigned")
        Log.d(TAG, "  Expired: $isExpired")
        Log.d(TAG, "  Hostname matches: $hostnameMatches")
        Log.d(TAG, "  Subject: $subject")
        Log.d(TAG, "  SANs: $sanList")
        Log.d(TAG, "  Days until expiry: $daysUntilExpiry")
        Log.d(TAG, "  Validity period: $validityPeriodDays days")
        Log.d(TAG, "  Short validity period: $hasShortValidityPeriod")
        
        return CertificateValidation(
            isValid = isValid,
            isSelfSigned = isSelfSigned,
            isExpired = isExpired,
            hostnameMatches = hostnameMatches,
            issuer = issuer,
            subject = subject,
            subjectAlternativeNames = sanList,
            expiryDate = expiryDate,
            daysUntilExpiry = daysUntilExpiry,
            validityPeriodDays = validityPeriodDays,
            hasShortValidityPeriod = hasShortValidityPeriod
        )
    }
    
    /**
     * Check Subject Alternative Names for hostname match
     */
    private fun checkSubjectAlternativeNames(cert: X509Certificate, domain: String): Boolean {
        return try {
            val sans = cert.subjectAlternativeNames
            if (sans != null) {
                for (san in sans) {
                    if (san.size >= 2 && san[0] == 2) { // Type 2 = DNS name
                        val dnsName = san[1].toString()
                        if (dnsName.equals(domain, ignoreCase = true) ||
                            (dnsName.startsWith("*.") && domain.endsWith(dnsName.substring(2), ignoreCase = true))) {
                            return true
                        }
                    }
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }
}
