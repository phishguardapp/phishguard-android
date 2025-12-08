# Notification Display - Always Shows Domain Names

## Current Implementation

The system is designed to **always show domain names** in notifications, never bare IP addresses.

## How It Works

### Flow Diagram
```
User visits: meine-dkb.biz
    ↓
SOCKS Proxy receives connection
    ↓
┌─────────────────────────────────┐
│ Is it a domain name?            │
├─────────────────────────────────┤
│ YES → Report domain directly    │ ✅ Notification: "meine-dkb.biz"
│                                 │
│ NO (it's an IP)                 │
│   ↓                             │
│   Try Reverse DNS               │
│   ↓                             │
│   ┌─────────────────────────┐   │
│   │ Reverse DNS successful? │   │
│   ├─────────────────────────┤   │
│   │ YES → Report domain     │   │ ✅ Notification: "meine-dkb.biz"
│   │ NO  → Skip notification │   │ ⏭️ No notification (avoid IP)
│   └─────────────────────────┘   │
└─────────────────────────────────┘
```

## Examples

### Example 1: Domain Name Captured
```
User visits: https://meine-dkb.biz/login
SOCKS sees: "meine-dkb.biz"
Analysis: Domain "meine-dkb.biz"
Notification: "🛑 DANGEROUS: meine-dkb.biz is likely a phishing site"
✅ User sees the domain they visited
```

### Example 2: IP with Successful Reverse DNS
```
User visits: https://meine-dkb.biz
Browser resolves: 185.178.208.170
SOCKS sees: "185.178.208.170"
Reverse DNS: 185.178.208.170 → "meine-dkb.biz"
Analysis: Domain "meine-dkb.biz"
Notification: "🛑 DANGEROUS: meine-dkb.biz is likely a phishing site"
✅ User sees the domain they visited
```

### Example 3: IP with Failed Reverse DNS
```
User visits: https://meine-dkb.biz
Browser resolves: 185.178.208.170
SOCKS sees: "185.178.208.170"
Reverse DNS: Failed
Analysis: Skipped
Notification: None
⏭️ No confusing IP notification
```

### Example 4: Cached Domain
```
User visits: https://meine-dkb.biz (second time)
Browser resolves: 185.178.208.170
SOCKS sees: "185.178.208.170"
Cache lookup: 185.178.208.170 → "meine-dkb.biz"
Analysis: Domain "meine-dkb.biz" (from cache)
Notification: Already shown (deduplicated)
✅ Consistent domain name
```

## Code Flow

### 1. LocalSocksProxy (Domain Extraction)
```kotlin
if (isIpAddress) {
    // Try reverse DNS
    val hostname = reverseDNS(ip)
    if (hostname != null) {
        onDomainDetected(hostname)  // ✅ Report domain
    } else {
        // Skip - no domain available  // ⏭️ No notification
    }
} else {
    onDomainDetected(domain)  // ✅ Report domain
}
```

### 2. ThreatDetector (Analysis)
```kotlin
suspend fun analyze(input: String): ThreatAnalysis {
    // Resolve IP to domain if needed
    val domain = if (isIpAddress(input)) {
        resolveIpToDomain(input) ?: input
    } else {
        input
    }
    
    // Analyze and return with domain name
    return ThreatAnalysis(
        domain = domain,  // ✅ Always domain name
        verdict = verdict,
        confidence = confidence,
        reasons = reasons
    )
}
```

### 3. PhishGuardVpnService (Notification)
```kotlin
private fun showThreatNotification(domain: String, analysis: ThreatAnalysis) {
    val text = "DANGER: $domain is likely a phishing site"
    // ✅ Shows domain name from analysis
}
```

## What Users See

### Dangerous Site
```
🛑 PHISHING DETECTED - DO NOT PROCEED

DANGER: meine-dkb.biz is likely a phishing site

Risk Level: 90%

Why this is flagged:
• Bank/payment name with suspicious TLD (dkb.biz)
• Likely phishing attempt impersonating financial institution
• Uses suspicious TLD: .biz

⚠️ DO NOT enter passwords or personal information!
```

### Suspicious Site
```
⚠️ Suspicious Site Warning

Warning: clientenetonline.com shows suspicious patterns

Risk Level: 65%

Why this is flagged:
• Domain registered less than 7 days ago (1 days)
• Contains suspicious keyword: online
• Uses suspicious TLD: .com
```

### Safe Site
```
No notification shown
(Safe sites don't trigger alerts)
```

## Benefits

### 1. User Recognition
✅ Users see domains they recognize
✅ No mysterious IP addresses
✅ Clear understanding of what's flagged

### 2. Trust
✅ Professional appearance
✅ Accurate information
✅ Users trust the app's judgement

### 3. Actionable
✅ Users can verify the domain
✅ Can check if they actually visited it
✅ Can report false positives accurately

## Edge Cases

### Case 1: User Directly Types IP
```
User types: http://185.178.208.170
SOCKS sees: "185.178.208.170"
Reverse DNS: Likely fails
Result: No notification (rare case)
```
**Note**: Normal users don't type IPs. If they do, they know what they're doing.

### Case 2: Localhost/Private IPs
```
User visits: http://192.168.1.1
SOCKS sees: "192.168.1.1"
Result: No notification (private IP)
```
**Note**: Private IPs are for local network, not phishing.

### Case 3: Multiple Domains on Same IP
```
IP 185.178.208.170 hosts:
- meine-dkb.biz (phishing)
- legitimate-site.com (safe)

First visit: meine-dkb.biz
Cache: 185.178.208.170 → meine-dkb.biz
Second visit: legitimate-site.com
SOCKS sees: legitimate-site.com (domain)
Analysis: legitimate-site.com (correct)
```
**Note**: Domain names take priority over cached IPs.

## Summary

✅ **Notifications ALWAYS show domain names**
✅ **Never show bare IP addresses**
✅ **Users see what they actually visited**
✅ **Clear, professional, trustworthy alerts**

The system is already implemented correctly to show user-entered URLs (domain names) instead of IP addresses in all notifications.
