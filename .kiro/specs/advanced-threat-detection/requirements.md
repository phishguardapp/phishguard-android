# Requirements Document

## Introduction

This document specifies the requirements for implementing advanced threat detection capabilities in the PhishGuard VPN application. The system currently uses basic pattern-based heuristics but needs to integrate multiple advanced detection mechanisms including SQLite database lookups, domain age verification, SSL certificate validation, and popularity ranking checks to reduce false positives and improve detection accuracy.

## Glossary

- **ThreatDetector**: The core component responsible for analyzing domains and determining their safety level
- **SQLite Database**: Local database containing legitimate financial institution domains
- **WHOIS**: Protocol for querying domain registration information
- **RDAP**: Registration Data Access Protocol, modern replacement for WHOIS
- **SSL Certificate**: Digital certificate that authenticates a website's identity
- **Tranco**: Research-oriented top sites ranking service
- **SNI**: Server Name Indication, TLS extension that reveals the hostname during connection
- **DNS Resolution**: Process of converting domain names to IP addresses
- **Reverse DNS**: Process of converting IP addresses back to domain names
- **False Positive**: Legitimate website incorrectly flagged as suspicious

## Requirements

### Requirement 1

**User Story:** As a user visiting legitimate banking websites, I want the system to recognize them from a trusted database, so that I don't receive false security warnings.

#### Acceptance Criteria

1. WHEN the system analyzes a domain THEN the ThreatDetector SHALL query the SQLite banks database before applying heuristic checks
2. WHEN a domain matches an entry in the banks database THEN the system SHALL mark it as SAFE with high confidence
3. WHEN the banks database is unavailable or corrupted THEN the system SHALL log an error and continue with other detection methods
4. WHEN the application starts THEN the system SHALL copy the banks.sqlite file from assets to internal storage if not already present
5. WHERE the banks database contains a domain entry THEN the system SHALL prioritize this check over pattern-based heuristics

### Requirement 2

**User Story:** As a user, I want the system to identify newly registered domains, so that I can be warned about potentially suspicious sites that haven't established legitimacy.

#### Acceptance Criteria

1. WHEN analyzing a domain THEN the system SHALL query WHOIS or RDAP services to determine the domain registration date
2. WHEN a domain is less than 30 days old THEN the system SHALL increase the suspicion score by 0.25
3. WHEN a domain is less than 7 days old THEN the system SHALL increase the suspicion score by 0.40
4. WHEN WHOIS/RDAP queries fail or timeout THEN the system SHALL continue analysis without domain age information
5. WHEN WHOIS/RDAP queries take longer than 3 seconds THEN the system SHALL timeout and proceed with other checks

### Requirement 3

**User Story:** As a user, I want the system to validate SSL certificates, so that I can be warned about sites using invalid or self-signed certificates that may indicate phishing attempts.

#### Acceptance Criteria

1. WHEN analyzing a domain accessed via HTTPS THEN the system SHALL validate the SSL certificate
2. WHEN a certificate is self-signed THEN the system SHALL increase the suspicion score by 0.35
3. WHEN a certificate is expired THEN the system SHALL increase the suspicion score by 0.45
4. WHEN a certificate hostname does not match the domain THEN the system SHALL increase the suspicion score by 0.50
5. WHEN SSL validation fails due to network errors THEN the system SHALL continue analysis without SSL information

### Requirement 4

**User Story:** As a user, I want the system to check domain popularity rankings, so that well-established popular sites are less likely to be flagged incorrectly.

#### Acceptance Criteria

1. WHEN analyzing a domain THEN the system SHALL query the Tranco top sites ranking
2. WHEN a domain is in the Tranco top 10,000 THEN the system SHALL reduce the suspicion score by 0.20
3. WHEN a domain is in the Tranco top 100,000 THEN the system SHALL reduce the suspicion score by 0.10
4. WHEN Tranco API is unavailable THEN the system SHALL continue analysis without ranking information
5. WHEN Tranco queries take longer than 2 seconds THEN the system SHALL timeout and proceed with other checks

### Requirement 5

**User Story:** As a user visiting websites, I want the system to properly identify domain names instead of just IP addresses, so that legitimate sites are correctly recognized.

#### Acceptance Criteria

1. WHEN the system detects an IP address in network traffic THEN the ThreatDetector SHALL perform reverse DNS lookup to obtain the domain name
2. WHEN SNI information is available in TLS handshakes THEN the system SHALL extract and use the domain name
3. WHEN DNS queries are monitored THEN the system SHALL cache IP-to-domain mappings for subsequent lookups
4. WHEN reverse DNS lookup fails THEN the system SHALL analyze the IP address directly with increased suspicion
5. WHEN both domain name and IP address are available THEN the system SHALL analyze the domain name and include IP information as context

### Requirement 6

**User Story:** As a user, I want the system to cache threat analysis results, so that repeated visits to the same site don't cause unnecessary delays or redundant API calls.

#### Acceptance Criteria

1. WHEN a domain is analyzed THEN the system SHALL cache the result for 24 hours
2. WHEN a cached result exists and is less than 24 hours old THEN the system SHALL return the cached result
3. WHEN a cached result is older than 24 hours THEN the system SHALL perform a fresh analysis
4. WHEN the cache exceeds 1000 entries THEN the system SHALL remove the oldest entries
5. WHEN the application restarts THEN the system SHALL clear the in-memory cache

### Requirement 7

**User Story:** As a developer, I want all external API calls to be asynchronous and non-blocking, so that threat detection doesn't slow down network traffic.

#### Acceptance Criteria

1. WHEN performing WHOIS/RDAP lookups THEN the system SHALL execute them asynchronously using coroutines
2. WHEN performing Tranco API calls THEN the system SHALL execute them asynchronously using coroutines
3. WHEN performing SSL validation THEN the system SHALL execute it asynchronously using coroutines
4. WHEN multiple checks are needed THEN the system SHALL execute them concurrently where possible
5. WHEN any check takes longer than its timeout THEN the system SHALL cancel it and continue with available results

### Requirement 8

**User Story:** As a user, I want the system to provide detailed reasons for threat verdicts, so that I can understand why a site was flagged.

#### Acceptance Criteria

1. WHEN a domain is flagged as SUSPICIOUS or DANGEROUS THEN the system SHALL include all contributing factors in the analysis result
2. WHEN domain age contributes to the verdict THEN the system SHALL include the registration date in the reasons
3. WHEN SSL issues contribute to the verdict THEN the system SHALL include specific certificate problems in the reasons
4. WHEN database checks contribute to the verdict THEN the system SHALL indicate whether the domain was found in legitimate databases
5. WHEN displaying threat notifications THEN the system SHALL show the top 3 most significant reasons to the user
