# Advanced Threat Detection Design

## Overview

This design document outlines the implementation of advanced threat detection capabilities for the PhishGuard VPN application. The system will integrate multiple detection mechanisms including SQLite database lookups, WHOIS/RDAP domain age verification, SSL certificate validation, and Tranco popularity ranking to provide comprehensive phishing protection while minimizing false positives.

The design focuses on asynchronous, non-blocking operations to ensure network traffic is not impacted by threat analysis. All external API calls will use Kotlin coroutines with appropriate timeouts and fallback mechanisms.

## Architecture

### Component Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    ThreatDetector                            │
│  ┌──────────────────────────────────────────────────────┐  │
│  │           Domain Resolution Layer                     │  │
│  │  - SNI Extraction                                     │  │
│  │  - Reverse DNS Lookup                                 │  │
│  │  - DNS Cache Integration                              │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │           Analysis Orchestrator                       │  │
│  │  - Coordinates all checks                            │  │
│  │  - Manages timeouts                                   │  │
│  │  - Aggregates results                                 │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │           Detection Modules                           │  │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────┐       │  │
│  │  │  SQLite    │ │   WHOIS/   │ │    SSL     │       │  │
│  │  │  Database  │ │   RDAP     │ │ Validator  │       │  │
│  │  └────────────┘ └────────────┘ └────────────┘       │  │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────┐       │  │
│  │  │   Tranco   │ │  Pattern   │ │   Cache    │       │  │
│  │  │   Ranking  │ │  Heuristics│ │  Manager   │       │  │
│  │  └────────────┘ └────────────┘ └────────────┘       │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Data Flow

1. **Input**: IP address or domain name from network traffic
2. **Domain Resolution**: Convert IP to domain name if needed (SNI/reverse DNS)
3. **Cache Check**: Look for existing analysis result
4. **Parallel Analysis**: Execute all detection modules concurrently
5. **Score Aggregation**: Combine results into final verdict
6. **Output**: ThreatAnalysis with verdict, confidence, and reasons

## Components and Interfaces

### 1. BankDatabaseHelper

Manages SQLite database operations for legitimate financial institutions.

```kotlin
class BankDatabaseHelper(context: Context) {
    fun isLegitimateBank(domain: String): Boolean
    fun getAllBankDomains(): List<String>
    private fun copyDatabaseFromAssets()
}
```

**Responsibilities:**
- Copy banks.sqlite from assets to internal storage on first run
- Query database for domain matches
- Handle database errors gracefully
- Support wildcard matching for subdomains

### 2. DomainAgeChecker

Performs WHOIS/RDAP lookups to determine domain registration date.

```kotlin
class DomainAgeChecker {
    suspend fun getDomainAge(domain: String): DomainAgeResult
    private suspend fun queryRDAP(domain: String): RDAPResponse?
    private suspend fun queryWHOIS(domain: String): WHOISResponse?
}

data class DomainAgeResult(
    val domain: String,
    val registrationDate: LocalDate?,
    val ageInDays: Int?,
    val source: String // "RDAP" or "WHOIS"
)
```

**Responsibilities:**
- Try RDAP first (modern, structured API)
- Fall back to WHOIS if RDAP fails
- Parse registration dates from responses
- Handle timeouts (3 seconds max)
- Cache results to avoid repeated lookups

### 3. SSLCertificateValidator

Validates SSL certificates for HTTPS connections.

```kotlin
class SSLCertificateValidator {
    suspend fun validateCertificate(domain: String): CertificateValidation
    private fun checkExpiration(cert: X509Certificate): Boolean
    private fun checkHostname(cert: X509Certificate, domain: String): Boolean
    private fun checkSelfSigned(cert: X509Certificate): Boolean
}

data class CertificateValidation(
    val isValid: Boolean,
    val isSelfSigned: Boolean,
    val isExpired: Boolean,
    val hostnameMatches: Boolean,
    val issuer: String?,
    val expiryDate: LocalDate?
)
```

**Responsibilities:**
- Establish SSL connection to domain
- Extract and validate certificate
- Check expiration, hostname match, self-signed status
- Handle connection failures gracefully
- Timeout after 3 seconds

### 4. TrancoRankingChecker

Queries Tranco API for domain popularity ranking.

```kotlin
class TrancoRankingChecker {
    suspend fun getRanking(domain: String): TrancoRanking?
    private suspend fun queryTrancoAPI(domain: String): Int?
}

data class TrancoRanking(
    val domain: String,
    val rank: Int,
    val isTopTenThousand: Boolean,
    val isTopHundredThousand: Boolean
)
```

**Responsibilities:**
- Query Tranco API for domain ranking
- Cache results (rankings change slowly)
- Handle API failures gracefully
- Timeout after 2 seconds

### 5. DomainResolver

Resolves IP addresses to domain names using multiple methods.

```kotlin
class DomainResolver(private val dnsMonitor: DnsMonitor) {
    fun resolveIpToDomain(ipAddress: String): String?
    fun extractSniFromTls(packet: ByteArray): String?
    fun getDomainFromCache(ipAddress: String): String?
}
```

**Responsibilities:**
- Check DNS cache first (from DnsMonitor)
- Extract SNI from TLS handshakes
- Perform reverse DNS lookup as fallback
- Return null if resolution fails

### 6. ThreatAnalysisCache

Caches threat analysis results to improve performance.

```kotlin
class ThreatAnalysisCache {
    fun get(domain: String): ThreatAnalysis?
    fun put(domain: String, analysis: ThreatAnalysis)
    fun clear()
    private fun evictOldEntries()
}

data class CachedAnalysis(
    val analysis: ThreatAnalysis,
    val timestamp: Long
)
```

**Responsibilities:**
- Store analysis results with timestamps
- Return cached results if less than 24 hours old
- Evict oldest entries when cache exceeds 1000 items
- Clear cache on application restart

### 7. Enhanced ThreatDetector

Orchestrates all detection modules and produces final verdict.

```kotlin
class ThreatDetector(
    private val context: Context,
    private val dnsMonitor: DnsMonitor
) {
    private val bankDatabase: BankDatabaseHelper
    private val domainAgeChecker: DomainAgeChecker
    private val sslValidator: SSLCertificateValidator
    private val trancoChecker: TrancoRankingChecker
    private val domainResolver: DomainResolver
    private val cache: ThreatAnalysisCache
    
    suspend fun analyze(input: String): ThreatAnalysis
    private suspend fun analyzeWithAllChecks(domain: String): ThreatAnalysis
    private fun aggregateScores(results: AnalysisResults): ThreatAnalysis
}
```

## Data Models

### ThreatAnalysis

```kotlin
data class ThreatAnalysis(
    val domain: String,
    val verdict: Verdict,
    val confidence: Float,
    val reasons: List<String>,
    val metadata: AnalysisMetadata? = null
)

enum class Verdict {
    SAFE,
    SUSPICIOUS,
    DANGEROUS
}

data class AnalysisMetadata(
    val domainAge: Int?,
    val trancoRank: Int?,
    val sslValid: Boolean?,
    val inBankDatabase: Boolean,
    val analysisTimeMs: Long
)
```

### AnalysisResults

Internal data structure for aggregating results from all checks.

```kotlin
data class AnalysisResults(
    val domain: String,
    val bankCheck: BankCheckResult,
    val domainAge: DomainAgeResult?,
    val sslValidation: CertificateValidation?,
    val trancoRanking: TrancoRanking?,
    val patternScore: Float,
    val patternReasons: List<String>
)

data class BankCheckResult(
    val isLegitimateBank: Boolean,
    val matchedDomain: String?
)
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*


### Property 1: Database check priority
*For any* domain that exists in the banks database, analyzing it should return SAFE verdict regardless of pattern-based heuristics that would otherwise flag it.
**Validates: Requirements 1.2, 1.5**

### Property 2: Domain age scoring consistency
*For any* domain with a known registration date, if the domain is less than 30 days old, the suspicion score should be increased by at least 0.25.
**Validates: Requirements 2.2**

### Property 3: Very new domain scoring
*For any* domain less than 7 days old, the suspicion score should be increased by at least 0.40.
**Validates: Requirements 2.3**

### Property 4: IP address resolution attempt
*For any* input that matches an IP address pattern, the system should attempt reverse DNS lookup or check DNS cache before analysis.
**Validates: Requirements 5.1**

### Property 5: SNI extraction for TLS
*For any* TLS handshake packet containing SNI extension, the system should successfully extract the domain name.
**Validates: Requirements 5.2**

### Property 6: DNS cache utilization
*For any* IP address that has been seen in DNS queries, subsequent lookups should return the cached domain name without performing reverse DNS.
**Validates: Requirements 5.3**

### Property 7: IP fallback with increased suspicion
*For any* IP address where reverse DNS lookup fails, analyzing it should result in higher suspicion score than analyzing a resolved domain name.
**Validates: Requirements 5.4**

### Property 8: Domain prioritization over IP
*For any* input where both domain name and IP address are available, the analysis should be performed on the domain name.
**Validates: Requirements 5.5**

### Property 9: Cache storage and retrieval
*For any* domain analyzed, performing a second analysis within 24 hours should return the cached result without re-executing external checks.
**Validates: Requirements 6.1, 6.2**

### Property 10: Cache expiration
*For any* cached analysis older than 24 hours, a new analysis should be performed and the cache should be updated.
**Validates: Requirements 6.3**

### Property 11: Cache eviction
*For any* cache state with 1000 entries, adding a new entry should result in the oldest entry being removed.
**Validates: Requirements 6.4**

### Property 12: Timeout enforcement
*For any* external check (WHOIS/RDAP/SSL/Tranco) that exceeds its timeout, the system should cancel it and continue with other checks.
**Validates: Requirements 2.5, 3.5, 4.5, 7.5**

### Property 13: Reason completeness
*For any* domain flagged as SUSPICIOUS or DANGEROUS, the analysis result should include all factors that contributed to the verdict.
**Validates: Requirements 8.1**

### Property 14: Domain age reason inclusion
*For any* domain where age contributes to increased suspicion, the reasons should include the registration date or age in days.
**Validates: Requirements 8.2**

### Property 15: SSL issue reason inclusion
*For any* domain with SSL certificate problems, the reasons should include specific certificate issues (expired, self-signed, hostname mismatch).
**Validates: Requirements 8.3**

### Property 16: Database check reason inclusion
*For any* domain analyzed, the reasons should indicate whether it was found in the legitimate banks database.
**Validates: Requirements 8.4**

### Property 17: Top reasons selection
*For any* analysis with more than 3 reasons, the notification should display the 3 most significant reasons based on their score contribution.
**Validates: Requirements 8.5**

## Error Handling

### Database Errors
- **Missing Database**: If banks.sqlite is not found in assets, log error and continue with other checks
- **Corrupted Database**: If database cannot be opened, log error and continue with other checks
- **Query Failures**: If database queries fail, treat as "not found" and continue

### Network Errors
- **WHOIS/RDAP Failures**: Log warning, continue analysis without domain age information
- **SSL Connection Failures**: Log warning, continue analysis without certificate validation
- **Tranco API Failures**: Log warning, continue analysis without ranking information
- **Timeout Handling**: Cancel long-running operations, log timeout, continue with available data

### Resolution Errors
- **Reverse DNS Failures**: Treat IP as suspicious, increase score by 0.5
- **SNI Extraction Failures**: Fall back to reverse DNS or IP analysis
- **DNS Cache Misses**: Perform reverse DNS lookup

### General Principles
1. **Fail Open**: If all checks fail, default to pattern-based analysis
2. **Graceful Degradation**: Each check failure should not prevent other checks
3. **Logging**: All errors should be logged with context for debugging
4. **User Experience**: Network failures should not cause long delays or crashes

## Testing Strategy

### Unit Testing

The system will use JUnit 4 and MockK for unit testing. Key areas to test:

**BankDatabaseHelper:**
- Database initialization and asset copying
- Domain matching with exact and wildcard patterns
- Error handling for corrupted databases

**DomainAgeChecker:**
- RDAP response parsing
- WHOIS response parsing
- Timeout handling
- Date calculation accuracy

**SSLCertificateValidator:**
- Certificate expiration checking
- Hostname matching logic
- Self-signed certificate detection
- Error handling for connection failures

**TrancoRankingChecker:**
- API response parsing
- Ranking threshold logic
- Timeout handling

**DomainResolver:**
- IP address pattern matching
- SNI extraction from TLS packets
- Reverse DNS lookup
- Cache integration

**ThreatAnalysisCache:**
- Cache storage and retrieval
- TTL expiration logic
- Eviction when exceeding size limit

**ThreatDetector:**
- Score aggregation logic
- Verdict determination
- Reason compilation
- Integration of all modules

### Property-Based Testing

The system will use Kotest Property Testing for verifying universal properties. The framework will be configured to run a minimum of 100 iterations per property test.

**Property Test Configuration:**
```kotlin
class ThreatDetectorPropertyTest : StringSpec({
    // Configure property testing
    PropertyTesting.defaultIterationCount = 100
    
    // Property tests will be implemented here
})
```

Each property-based test will:
1. Generate random inputs using Kotest generators
2. Execute the system under test
3. Verify the property holds for all generated inputs
4. Report any counterexamples that violate the property

**Generators Needed:**
- Domain name generator (valid DNS format)
- IP address generator (valid IPv4 format)
- TLS packet generator (with SNI extension)
- Domain age generator (various registration dates)
- SSL certificate generator (valid, expired, self-signed)

### Integration Testing

Integration tests will verify:
- End-to-end analysis flow from IP/domain input to verdict
- Interaction between DomainResolver and ThreatDetector
- Database initialization on first run
- Cache behavior across multiple analyses
- Concurrent execution of multiple checks

### Performance Testing

Performance tests will verify:
- Analysis completes within 5 seconds for typical cases
- Timeouts are enforced correctly
- Cache improves performance for repeated analyses
- Concurrent checks execute in parallel

## Implementation Notes

### Android-Specific Considerations

**Database Location:**
- Source: `app/src/main/assets/banks.sqlite`
- Destination: `context.getDatabasePath("banks.sqlite")`

**Network Permissions:**
- Already granted via VPN service
- No additional permissions needed

**Background Execution:**
- All analysis runs in VPN service context
- Use coroutines with appropriate dispatchers
- Avoid blocking the main thread

### External APIs

**RDAP:**
- Base URL: `https://rdap.org/domain/{domain}`
- Response format: JSON
- No authentication required

**WHOIS:**
- Use socket connection to whois.iana.org:43
- Parse text response for registration date
- Format varies by TLD

**Tranco:**
- Base URL: `https://tranco-list.eu/api/ranks/domain/{domain}`
- Response format: JSON
- No authentication required
- Rate limits: Unknown, implement caching

**SSL Certificate:**
- Use `SSLSocketFactory` to establish connection
- Extract certificate chain
- Validate using `X509Certificate` APIs

### Performance Optimization

1. **Parallel Execution**: Use `async`/`await` to run checks concurrently
2. **Caching**: Cache all external API results
3. **Timeouts**: Aggressive timeouts prevent slow checks from blocking
4. **Database Indexing**: Ensure banks.sqlite has index on `tld` column
5. **Lazy Initialization**: Initialize components only when needed

### Security Considerations

1. **SQL Injection**: Use parameterized queries for database access
2. **Certificate Validation**: Don't trust self-signed certificates
3. **API Response Validation**: Validate all external API responses
4. **Timeout Enforcement**: Prevent DoS via slow external services
5. **Error Information**: Don't leak sensitive information in error messages

## Future Enhancements

1. **Machine Learning**: Integrate ML model for advanced pattern detection
2. **Threat Intelligence Feeds**: Subscribe to real-time phishing databases
3. **User Feedback**: Allow users to report false positives/negatives
4. **Reputation Scoring**: Build historical reputation for domains
5. **Certificate Transparency**: Check CT logs for certificate history
6. **DNSSEC Validation**: Verify DNSSEC signatures for domains
7. **Behavioral Analysis**: Detect phishing based on user interaction patterns
