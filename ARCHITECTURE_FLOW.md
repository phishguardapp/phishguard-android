# PhishGuard Android - Architecture Flow

## System Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         User's Device                            │
│                                                                   │
│  ┌────────────┐                                                  │
│  │   User     │                                                  │
│  │  Opens App │                                                  │
│  └─────┬──────┘                                                  │
│        │                                                          │
│        ▼                                                          │
│  ┌─────────────────┐                                            │
│  │  MainActivity   │                                            │
│  │  (Compose UI)   │                                            │
│  │                 │                                            │
│  │  [Start/Stop]   │                                            │
│  └────────┬────────┘                                            │
│           │                                                      │
│           │ Start VPN                                           │
│           ▼                                                      │
│  ┌──────────────────────────────────────────────────┐          │
│  │      PhishGuardVpnService                        │          │
│  │                                                   │          │
│  │  1. Establish VPN Tunnel                         │          │
│  │  2. Intercept All Network Traffic                │          │
│  │  3. Process Packets                              │          │
│  └────────┬─────────────────────────────────────────┘          │
│           │                                                      │
│           │ For each packet                                     │
│           ▼                                                      │
│  ┌──────────────────────────────────────────────────┐          │
│  │         PacketParser                             │          │
│  │                                                   │          │
│  │  Parse:                                          │          │
│  │  • IPv4 headers                                  │          │
│  │  • TCP/UDP protocols                             │          │
│  │  • DNS queries (port 53)                         │          │
│  │  • HTTP Host headers (port 80)                   │          │
│  │  • TLS SNI (port 443)                            │          │
│  │                                                   │          │
│  │  Extract: domain name                            │          │
│  └────────┬─────────────────────────────────────────┘          │
│           │                                                      │
│           │ If domain found                                     │
│           ▼                                                      │
│  ┌──────────────────────────────────────────────────┐          │
│  │         ThreatDetector                           │          │
│  │                                                   │          │
│  │  Analyze:                                        │          │
│  │  • Suspicious keywords                           │          │
│  │  • Dangerous TLDs                                │          │
│  │  • Subdomain count                               │          │
│  │  • IP addresses                                  │          │
│  │  • Known phishing domains                        │          │
│  │                                                   │          │
│  │  Calculate:                                      │          │
│  │  • Suspicion score                               │          │
│  │  • Confidence level                              │          │
│  │  • Verdict (SAFE/SUSPICIOUS/DANGEROUS)          │          │
│  └────────┬─────────────────────────────────────────┘          │
│           │                                                      │
│           │ If threat detected                                  │
│           ▼                                                      │
│  ┌──────────────────────────────────────────────────┐          │
│  │    NotificationManager                           │          │
│  │                                                   │          │
│  │  Show:                                           │          │
│  │  • ⚠️  Suspicious Site Warning                   │          │
│  │  • 🛑 Phishing Alert                             │          │
│  │  • Confidence score                              │          │
│  │  • Reasons for detection                         │          │
│  └──────────────────────────────────────────────────┘          │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

## Packet Processing Flow

```
Network Packet Arrives
        │
        ▼
┌───────────────────┐
│  Read from VPN    │
│  Interface        │
└────────┬──────────┘
         │
         ▼
┌───────────────────┐
│  Parse IP Header  │
│  • Version (IPv4) │
│  • Protocol       │
│  • Source IP      │
│  • Dest IP        │
└────────┬──────────┘
         │
         ▼
    ┌────┴────┐
    │Protocol?│
    └────┬────┘
         │
    ┌────┴────────────┐
    │                 │
    ▼                 ▼
┌───────┐      ┌──────────┐
│  TCP  │      │   UDP    │
└───┬───┘      └────┬─────┘
    │               │
    │               ▼
    │        ┌──────────────┐
    │        │ Port 53?     │
    │        │ (DNS)        │
    │        └──────┬───────┘
    │               │
    │               ▼
    │        ┌──────────────┐
    │        │ Extract DNS  │
    │        │ Domain       │
    │        └──────┬───────┘
    │               │
    ▼               │
┌───────────┐       │
│ Port 80?  │       │
│ (HTTP)    │       │
└─────┬─────┘       │
      │             │
      ▼             │
┌──────────────┐    │
│ Extract Host │    │
│ Header       │    │
└──────┬───────┘    │
       │            │
       ▼            │
┌───────────┐       │
│ Port 443? │       │
│ (HTTPS)   │       │
└─────┬─────┘       │
      │             │
      ▼             │
┌──────────────┐    │
│ Parse TLS    │    │
│ Client Hello │    │
└──────┬───────┘    │
       │            │
       ▼            │
┌──────────────┐    │
│ Extract SNI  │    │
│ Extension    │    │
└──────┬───────┘    │
       │            │
       └────┬───────┘
            │
            ▼
     ┌──────────────┐
     │ Domain Found │
     └──────┬───────┘
            │
            ▼
     ┌──────────────┐
     │ Analyze for  │
     │ Threats      │
     └──────┬───────┘
            │
            ▼
     ┌──────────────┐
     │ Forward      │
     │ Packet       │
     └──────────────┘
```

## Threat Detection Flow

```
Domain Extracted
      │
      ▼
┌─────────────────┐
│ Check if        │
│ Already         │
│ Analyzed?       │
└────┬────────────┘
     │
     ▼
┌─────────────────┐
│ Initialize      │
│ Score = 0       │
│ Reasons = []    │
└────┬────────────┘
     │
     ▼
┌─────────────────────────────┐
│ Check Known Phishing List   │
│ If match → DANGEROUS (95%)  │
└────┬────────────────────────┘
     │
     ▼
┌─────────────────────────────┐
│ Check TLD                   │
│ .tk, .ml, .xyz, etc.        │
│ If match → Score +0.3       │
└────┬────────────────────────┘
     │
     ▼
┌─────────────────────────────┐
│ Check Keywords              │
│ login, verify, secure, etc. │
│ Each match → Score +0.2     │
└────┬────────────────────────┘
     │
     ▼
┌─────────────────────────────┐
│ Count Subdomains            │
│ If >3 → Score +0.2          │
└────┬────────────────────────┘
     │
     ▼
┌─────────────────────────────┐
│ Check for Hyphens           │
│ With keywords → Score +0.15 │
└────┬────────────────────────┘
     │
     ▼
┌─────────────────────────────┐
│ Check if IP Address         │
│ If yes → Score +0.4         │
└────┬────────────────────────┘
     │
     ▼
┌─────────────────────────────┐
│ Calculate Verdict           │
│ Score ≥0.6 → DANGEROUS      │
│ Score ≥0.3 → SUSPICIOUS     │
│ Score <0.3 → SAFE           │
└────┬────────────────────────┘
     │
     ▼
┌─────────────────────────────┐
│ Calculate Confidence        │
│ Based on score and verdict  │
└────┬────────────────────────┘
     │
     ▼
┌─────────────────────────────┐
│ If SUSPICIOUS or DANGEROUS  │
│ → Show Notification         │
└─────────────────────────────┘
```

## TLS SNI Extraction (HTTPS)

```
TLS Client Hello Packet
         │
         ▼
┌────────────────────┐
│ Check byte 0       │
│ Is 0x16?           │
│ (Handshake)        │
└────┬───────────────┘
     │
     ▼
┌────────────────────┐
│ Check byte 5       │
│ Is 0x01?           │
│ (Client Hello)     │
└────┬───────────────┘
     │
     ▼
┌────────────────────┐
│ Skip to byte 43    │
│ Read Session ID    │
│ Length             │
└────┬───────────────┘
     │
     ▼
┌────────────────────┐
│ Skip Session ID    │
└────┬───────────────┘
     │
     ▼
┌────────────────────┐
│ Read Cipher        │
│ Suites Length      │
└────┬───────────────┘
     │
     ▼
┌────────────────────┐
│ Skip Cipher        │
│ Suites             │
└────┬───────────────┘
     │
     ▼
┌────────────────────┐
│ Read Compression   │
│ Methods Length     │
└────┬───────────────┘
     │
     ▼
┌────────────────────┐
│ Skip Compression   │
│ Methods            │
└────┬───────────────┘
     │
     ▼
┌────────────────────┐
│ Read Extensions    │
│ Length             │
└────┬───────────────┘
     │
     ▼
┌────────────────────┐
│ Loop Through       │
│ Extensions         │
└────┬───────────────┘
     │
     ▼
┌────────────────────┐
│ Find Extension     │
│ Type 0 (SNI)       │
└────┬───────────────┘
     │
     ▼
┌────────────────────┐
│ Parse SNI          │
│ Extension          │
└────┬───────────────┘
     │
     ▼
┌────────────────────┐
│ Extract Hostname   │
│ (Domain)           │
└────────────────────┘
```

## Data Flow Example

### Example 1: Safe Site (google.com)

```
User opens Chrome → google.com
         │
         ▼
DNS Query: google.com
         │
         ▼
PacketParser extracts: "google.com"
         │
         ▼
ThreatDetector analyzes:
  • No suspicious keywords
  • Legitimate TLD (.com)
  • No excessive subdomains
  • Not an IP address
  Score: 0.0
         │
         ▼
Verdict: SAFE
         │
         ▼
No notification
Packet forwarded
```

### Example 2: Suspicious Site

```
User clicks link → secure-login-verify.tk
         │
         ▼
DNS Query: secure-login-verify.tk
         │
         ▼
PacketParser extracts: "secure-login-verify.tk"
         │
         ▼
ThreatDetector analyzes:
  • Keywords: "secure" (+0.2), "login" (+0.2), "verify" (+0.2)
  • Dangerous TLD: .tk (+0.3)
  • Hyphens with keywords (+0.15)
  Score: 1.05
         │
         ▼
Verdict: DANGEROUS (95% confidence)
         │
         ▼
Notification: "🛑 PHISHING ALERT"
  "High risk site detected"
  "Confidence: 95%"
  "Reasons:
   • Uses suspicious TLD: .tk
   • Contains suspicious keyword: secure
   • Contains suspicious keyword: login
   • Contains suspicious keyword: verify
   • Hyphenated domain with suspicious keywords"
         │
         ▼
Packet forwarded (no blocking in Phase 1)
```

### Example 3: HTTPS Site

```
User visits https://example.com
         │
         ▼
TLS Client Hello sent
         │
         ▼
PacketParser:
  1. Detects port 443 (HTTPS)
  2. Identifies TLS handshake (0x16)
  3. Finds Client Hello (0x01)
  4. Navigates TLS structure
  5. Locates SNI extension (type 0)
  6. Extracts hostname: "example.com"
         │
         ▼
ThreatDetector analyzes: "example.com"
         │
         ▼
Verdict: SAFE
         │
         ▼
Packet forwarded
```

## Component Interaction

```
┌──────────────┐
│  MainActivity│
└──────┬───────┘
       │
       │ startService()
       ▼
┌──────────────────────┐
│ PhishGuardVpnService │◄─────┐
└──────┬───────────────┘      │
       │                       │
       │ parse()              │
       ▼                       │
┌──────────────┐              │
│ PacketParser │              │
└──────┬───────┘              │
       │                       │
       │ analyze()            │
       ▼                       │
┌──────────────┐              │
│ThreatDetector│              │
└──────┬───────┘              │
       │                       │
       │ notify()             │
       ▼                       │
┌──────────────────┐          │
│NotificationManager│          │
└───────────────────┘          │
                               │
       User sees notification  │
       Packet forwarded ───────┘
```

## Concurrency Model

```
Main Thread
    │
    ├─ UI (Compose)
    │  └─ MainActivity
    │
    └─ Service Lifecycle
       └─ PhishGuardVpnService.onCreate()

IO Dispatcher (Coroutines)
    │
    ├─ Packet Processing Loop
    │  └─ Read → Parse → Forward
    │
    └─ Domain Analysis
       └─ Threat Detection → Notification
```

## Summary

The architecture is designed for:
- **Performance:** Non-blocking I/O, efficient parsing
- **Reliability:** Proper error handling, graceful degradation
- **Maintainability:** Clean separation of concerns
- **Extensibility:** Ready for Phase 2 enhancements

Each component has a single responsibility:
- **VpnService:** Tunnel management and orchestration
- **PacketParser:** Protocol parsing and domain extraction
- **ThreatDetector:** Threat analysis and scoring
- **MainActivity:** User interface and controls

This clean architecture makes it easy to add Phase 2 features like SSL checking, WHOIS lookup, and ML models.
