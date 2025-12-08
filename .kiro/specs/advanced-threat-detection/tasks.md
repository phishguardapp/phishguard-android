# Implementation Plan

- [x] 1. Set up project structure and dependencies
  - Add Kotlin coroutines dependencies to build.gradle.kts
  - Add Kotest property testing dependencies for testing
  - Add OkHttp for HTTP requests (Tranco API)
  - Create package structure for new components
  - _Requirements: All_

- [x] 2. Implement BankDatabaseHelper for SQLite integration
  - Create BankDatabaseHelper class with database initialization
  - Implement asset copying logic to copy banks.sqlite to internal storage
  - Implement domain matching with exact and wildcard support
  - Add error handling for missing or corrupted database
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

- [x] 2.1 Write property test for database priority
  - **Property 1: Database check priority**
  - **Validates: Requirements 1.2, 1.5**

- [x] 2.2 Write unit tests for BankDatabaseHelper
  - Test database initialization and asset copying
  - Test domain matching with various patterns
  - Test error handling for corrupted database
  - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [x] 3. Implement DomainResolver for IP-to-domain resolution
  - Create DomainResolver class with DNS cache integration
  - Implement SNI extraction from TLS packets
  - Implement reverse DNS lookup functionality
  - Add IP address pattern detection
  - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

- [x] 3.1 Write property test for IP resolution
  - **Property 4: IP address resolution attempt**
  - **Validates: Requirements 5.1**

- [x] 3.2 Write property test for SNI extraction
  - **Property 5: SNI extraction for TLS**
  - **Validates: Requirements 5.2**

- [x] 3.3 Write property test for DNS cache utilization
  - **Property 6: DNS cache utilization**
  - **Validates: Requirements 5.3**

- [x] 3.4 Write property test for IP fallback behavior
  - **Property 7: IP fallback with increased suspicion**
  - **Validates: Requirements 5.4**

- [x] 3.5 Write property test for domain prioritization
  - **Property 8: Domain prioritization over IP**
  - **Validates: Requirements 5.5**

- [x] 4. Implement ThreatAnalysisCache for result caching
  - Create ThreatAnalysisCache class with in-memory storage
  - Implement cache storage with timestamps
  - Implement cache retrieval with TTL checking
  - Implement LRU eviction when exceeding 1000 entries
  - _Requirements: 6.1, 6.2, 6.3, 6.4_

- [x] 4.1 Write property test for cache storage and retrieval
  - **Property 9: Cache storage and retrieval**
  - **Validates: Requirements 6.1, 6.2**

- [x] 4.2 Write property test for cache expiration
  - **Property 10: Cache expiration**
  - **Validates: Requirements 6.3**

- [x] 4.3 Write property test for cache eviction
  - **Property 11: Cache eviction**
  - **Validates: Requirements 6.4**

- [ ] 5. Implement DomainAgeChecker for WHOIS/RDAP lookups
  - Create DomainAgeChecker class with coroutine support
  - Implement RDAP API client with JSON parsing
  - Implement WHOIS socket client with text parsing
  - Add timeout handling (3 seconds)
  - Add result caching to avoid repeated lookups
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

- [ ] 5.1 Write property test for domain age scoring
  - **Property 2: Domain age scoring consistency**
  - **Validates: Requirements 2.2**

- [ ] 5.2 Write property test for very new domain scoring
  - **Property 3: Very new domain scoring**
  - **Validates: Requirements 2.3**

- [ ] 5.3 Write unit tests for DomainAgeChecker
  - Test RDAP response parsing
  - Test WHOIS response parsing
  - Test timeout handling
  - Test date calculation accuracy
  - _Requirements: 2.1, 2.4, 2.5_

- [ ] 6. Implement SSLCertificateValidator for certificate checking
  - Create SSLCertificateValidator class with coroutine support
  - Implement SSL connection establishment
  - Implement certificate expiration checking
  - Implement hostname matching validation
  - Implement self-signed certificate detection
  - Add timeout handling (3 seconds)
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [ ] 6.1 Write unit tests for SSLCertificateValidator
  - Test certificate expiration checking
  - Test hostname matching logic
  - Test self-signed certificate detection
  - Test error handling for connection failures
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [ ] 7. Implement TrancoRankingChecker for popularity ranking
  - Create TrancoRankingChecker class with coroutine support
  - Implement Tranco API client using OkHttp
  - Add JSON response parsing
  - Add timeout handling (2 seconds)
  - Add result caching (rankings change slowly)
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

- [ ] 7.1 Write unit tests for TrancoRankingChecker
  - Test API response parsing
  - Test ranking threshold logic
  - Test timeout handling
  - Test error handling for API failures
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

- [x] 8. Enhance ThreatDetector with new components
  - Integrate BankDatabaseHelper into ThreatDetector
  - Integrate DomainResolver for IP-to-domain conversion
  - Integrate ThreatAnalysisCache for result caching
  - Update analyze() method to use DomainResolver first
  - Add cache check before performing analysis
  - _Requirements: 1.1, 5.1, 5.2, 5.3, 6.1, 6.2_

- [ ] 9. Implement parallel analysis orchestration
  - Create analyzeWithAllChecks() method using coroutines
  - Launch DomainAgeChecker, SSLCertificateValidator, and TrancoRankingChecker concurrently
  - Implement timeout enforcement for all checks
  - Collect results from all checks
  - _Requirements: 2.1, 3.1, 4.1, 7.5_

- [ ] 9.1 Write property test for timeout enforcement
  - **Property 12: Timeout enforcement**
  - **Validates: Requirements 2.5, 3.5, 4.5, 7.5**

- [ ] 10. Implement score aggregation and verdict logic
  - Create aggregateScores() method to combine all check results
  - Implement database check priority (override other checks if in database)
  - Apply domain age score adjustments
  - Apply SSL validation score adjustments
  - Apply Tranco ranking score adjustments
  - Combine with existing pattern-based scores
  - _Requirements: 1.5, 2.2, 2.3, 3.2, 3.3, 3.4, 4.2, 4.3_

- [ ] 11. Implement comprehensive reason reporting
  - Update ThreatAnalysis to include AnalysisMetadata
  - Collect reasons from all detection modules
  - Add database check results to reasons
  - Add domain age information to reasons
  - Add SSL certificate issues to reasons
  - Add Tranco ranking to reasons
  - _Requirements: 8.1, 8.2, 8.3, 8.4_

- [ ] 11.1 Write property test for reason completeness
  - **Property 13: Reason completeness**
  - **Validates: Requirements 8.1**

- [ ] 11.2 Write property test for domain age reason inclusion
  - **Property 14: Domain age reason inclusion**
  - **Validates: Requirements 8.2**

- [ ] 11.3 Write property test for SSL issue reason inclusion
  - **Property 15: SSL issue reason inclusion**
  - **Validates: Requirements 8.3**

- [ ] 11.4 Write property test for database check reason inclusion
  - **Property 16: Database check reason inclusion**
  - **Validates: Requirements 8.4**

- [ ] 12. Implement top reasons selection for notifications
  - Create method to rank reasons by score contribution
  - Select top 3 most significant reasons
  - Update notification display to show selected reasons
  - _Requirements: 8.5_

- [ ] 12.1 Write property test for top reasons selection
  - **Property 17: Top reasons selection**
  - **Validates: Requirements 8.5**

- [x] 13. Update PhishGuardVpnService integration
  - Update PacketMonitor to pass domain names to ThreatDetector
  - Update LocalSocksProxy to extract and pass SNI information
  - Ensure DnsMonitor cache is accessible to DomainResolver
  - Update notification display to show detailed reasons
  - _Requirements: 5.2, 5.3, 8.5_

- [x] 14. Add banks.sqlite to assets
  - Copy banks.sqlite file to app/src/main/assets/ directory
  - Verify database schema matches expected format
  - Test database can be opened and queried
  - _Requirements: 1.4_

- [x] 15. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 16. Integration testing
  - Test end-to-end flow from IP detection to verdict
  - Test with known legitimate banks (icici.bank.in, hdfcbank.com)
  - Test with known phishing domains
  - Test with new domains (< 30 days old)
  - Test with domains having SSL issues
  - Test cache behavior across multiple analyses
  - Verify no false positives for legitimate sites
  - _Requirements: All_

- [ ] 17. Performance testing
  - Measure analysis time for typical cases
  - Verify timeouts are enforced correctly
  - Verify concurrent checks execute in parallel
  - Verify cache improves performance for repeated analyses
  - _Requirements: 7.4, 7.5_
