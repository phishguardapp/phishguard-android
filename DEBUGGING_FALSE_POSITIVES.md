# Debugging False Positives Guide

## Issue: sbi.bank.in showing as suspicious

### Root Cause
The SOCKS proxy is receiving **IP addresses directly** (addressType 0x01) instead of domain names (addressType 0x03). This happens when:
1. The browser/app has already resolved the domain to IP
2. The connection goes directly to the IP
3. Reverse DNS lookup fails to get the domain back

### The Fix Applied

**Before**: 
- SOCKS receives IP `203.116.175.106`
- Tries reverse DNS → fails
- Analyzes IP → flagged as suspicious

**After**:
- SOCKS receives domain name `sbi.bank.in` (if available)
- Resolves to IP `203.116.175.106`
- **Caches the mapping**: `sbi.bank.in` → `203.116.175.106`
- Later, when IP `203.116.175.106` is seen:
  - Checks cache → finds `sbi.bank.in`
  - Checks database → found!
  - Result: SAFE ✅

### How to Debug

#### 1. Check Logcat for SOCKS Traffic

```bash
adb logcat | grep -E "LocalSocksProxy|ThreatDetector|BankDatabase"
```

Look for these patterns:

**Good Flow (Domain Name Received)**:
```
LocalSocksProxy: SOCKS request: sbi.bank.in:443
LocalSocksProxy: Caching DNS mapping: sbi.bank.in -> 203.116.175.106
ThreatDetector: Cached DNS: sbi.bank.in -> 203.116.175.106
PhishGuardVpnService: Domain detected via SOCKS: sbi.bank.in
ThreatDetector: Checking database for: sbi.bank.in
BankDatabaseHelper: Domain sbi.bank.in found in banks database
ThreatDetector: SAFE: sbi.bank.in
```

**Bad Flow (IP Address Received)**:
```
LocalSocksProxy: SOCKS request: 203.116.175.106:443
LocalSocksProxy: Got IP address, attempting reverse DNS...
LocalSocksProxy: No reverse DNS for 203.116.175.106
PhishGuardVpnService: Domain detected via SOCKS: 203.116.175.106
ThreatDetector: Analyzing IP: 203.116.175.106
ThreatDetector: Could not resolve IP to domain
ThreatDetector: SUSPICIOUS: 203.116.175.106 (85%)
```

#### 2. Check Database Contents

```bash
# Check if domain is in database
sqlite3 app/src/main/assets/banks.sqlite "SELECT * FROM banks WHERE tld LIKE '%sbi%';"

# Check all Indian banks
sqlite3 app/src/main/assets/banks.sqlite "SELECT name, tld FROM banks WHERE country='India';"
```

#### 3. Test Specific Domains

Add this to your test code:
```kotlin
// In ComponentTester or MainActivity
val detector = ThreatDetector(context)

// Test with domain name
val result1 = detector.analyze("sbi.bank.in")
Log.d("TEST", "sbi.bank.in: ${result1.verdict}")

// Test with IP (should fail without cache)
val result2 = detector.analyze("203.116.175.106")
Log.d("TEST", "203.116.175.106: ${result2.verdict}")

// Cache the mapping
detector.cacheDnsResolution("sbi.bank.in", "203.116.175.106")

// Test with IP again (should now work)
val result3 = detector.analyze("203.116.175.106")
Log.d("TEST", "203.116.175.106 (cached): ${result3.verdict}")
```

### Why This Happens

Different apps/browsers behave differently:

1. **Chrome/Firefox**: Often send domain names to SOCKS proxy ✅
2. **Some apps**: Pre-resolve DNS and send IPs directly ❌
3. **HTTP/2 connections**: May reuse connections with IPs ❌

### Solutions

#### Short-term (Current Implementation)
- Cache domain-to-IP mappings when we see domain names
- Use cached mappings for subsequent IP lookups
- Fall back to reverse DNS if no cache hit

#### Medium-term (Recommended)
- Implement DNS monitoring to catch ALL DNS queries
- Cache every DNS resolution before SOCKS sees it
- This would catch domains even if SOCKS only sees IPs

#### Long-term (Ideal)
- Implement SNI extraction from TLS handshakes
- This works even when only IPs are used
- Most reliable method for HTTPS traffic

### Testing the Fix

1. **Clear app data** to reset caches
2. **Visit a bank site** (e.g., https://sbi.bank.in)
3. **Check logcat** for the flow
4. **Visit the same site again** - should use cache
5. **Visit a different page** on same site - should use cache

### Expected Behavior After Fix

| Scenario | Before Fix | After Fix |
|----------|-----------|-----------|
| First visit to sbi.bank.in (domain name in SOCKS) | ✅ SAFE | ✅ SAFE |
| First visit to sbi.bank.in (IP in SOCKS) | ❌ SUSPICIOUS | ❌ SUSPICIOUS* |
| Second visit to sbi.bank.in (IP in SOCKS) | ❌ SUSPICIOUS | ✅ SAFE (cached) |
| Different page on sbi.bank.in | ❌ SUSPICIOUS | ✅ SAFE (cached) |

*First visit with IP-only will still fail unless we implement DNS monitoring

### Next Steps to Eliminate All False Positives

1. **Implement DNS Monitoring** (Task 5 from spec)
   - Intercept all DNS queries at the VPN level
   - Cache every DNS resolution
   - This ensures we ALWAYS have domain names

2. **Implement SNI Extraction** (Already have SniExtractor)
   - Extract domain from TLS Client Hello
   - Works even when SOCKS only sees IPs
   - Most reliable for HTTPS

3. **Improve Reverse DNS**
   - Use multiple DNS servers
   - Implement timeout and retry logic
   - Cache negative results to avoid repeated failures

### Monitoring in Production

Add these metrics:
- % of SOCKS requests with domain names vs IPs
- % of successful reverse DNS lookups
- % of cache hits vs misses
- False positive rate by domain

### Quick Fix for Testing

If you keep seeing false positives, you can temporarily:

1. **Disable IP flagging** (not recommended for production):
```kotlin
// In ThreatDetector.kt
if (domainResolver.isIpAddress(domain)) {
    // suspicionScore += 0.5f  // Comment this out
    // reasons.add("Direct IP address instead of domain name")
}
```

2. **Add more aggressive caching**:
```kotlin
// Cache for longer (7 days instead of 24 hours)
private const val TTL_MS = 7 * 24 * 60 * 60 * 1000L
```

3. **Add manual domain-to-IP mappings**:
```kotlin
// In PhishGuardVpnService.onCreate()
threatDetector.cacheDnsResolution("sbi.bank.in", "203.116.175.106")
threatDetector.cacheDnsResolution("icici.bank.in", "103.14.127.19")
// Add more as needed
```

## Summary

The fix improves the situation by caching domain-to-IP mappings when we DO see domain names. However, for complete elimination of false positives, we need to implement DNS monitoring to catch ALL DNS queries before they reach SOCKS.

The current fix will work well for:
- ✅ Repeated visits to the same site
- ✅ Multiple pages on the same domain
- ✅ Sites that send domain names to SOCKS

It may still have issues with:
- ❌ First visit when only IP is sent to SOCKS
- ❌ Apps that never send domain names
- ❌ Direct IP connections without DNS

**Recommendation**: Implement DNS monitoring (Task 5) for complete coverage.
