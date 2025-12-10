# Enhanced Redirect Detection - Infrastructure Pattern Update

## Issue Reported
When accessing `http://srv244376.hoster-test.ru/`, user saw notification for `dproxy.hoster.ru` instead of the original domain.

## Root Cause Analysis

### What Happened:
1. User accessed `http://srv244376.hoster-test.ru/`
2. Connection to `31.28.24.114:443` failed (timeout)
3. Redirect or subsequent request went to `dproxy.hoster.ru`
4. `dproxy.hoster.ru` was NOT detected as infrastructure domain
5. Notification showed `dproxy.hoster.ru` instead of original domain

### Why It Happened:
The `looksLikeInfrastructure()` function didn't have patterns to detect:
- `dproxy.*` (dynamic proxy servers)
- `proxy.*` or `proxy1.*` (proxy servers)
- `srv123.*` (server naming pattern)

## Fix Implemented

### Updated Infrastructure Patterns
Added to `PhishGuardVpnService.looksLikeInfrastructure()`:

```kotlin
lower.matches(Regex("^srv\\d+\\..*")) ||  // srv123.provider.com
lower.matches(Regex("^dproxy\\..*")) ||  // dproxy.provider.com (dynamic proxy)
lower.matches(Regex("^proxy\\d*\\..*")) ||  // proxy.provider.com or proxy1.provider.com
```

### Complete Pattern List Now Includes:
- `o5044s-259.kagoya.net` - Hosting provider pattern
- `ns123.provider.com` - Name server
- `server123.provider.com` - Server naming
- `srv123.provider.com` - **NEW** Server naming variant
- `vps123.provider.com` - VPS hosting
- `host123.provider.com` - Host naming
- `dproxy.provider.com` - **NEW** Dynamic proxy
- `proxy.provider.com` - **NEW** Proxy server
- `proxy1.provider.com` - **NEW** Numbered proxy
- `anything.ip-1-2-3.provider` - IP-based naming
- Domains with `-cdn.` or `.cdn.`
- All patterns from `isInfrastructureDomain()`

## How It Works Now

### Redirect Detection Flow:
1. User accesses `srv244376.hoster-test.ru`
2. Domain is tracked in `recentDomains` list
3. Redirect happens to `dproxy.hoster.ru`
4. `looksLikeInfrastructure("dproxy.hoster.ru")` returns `true` ✅
5. System looks back in `recentDomains` for non-infrastructure domain
6. Finds `srv244376.hoster-test.ru`
7. Notification shows original domain: `srv244376.hoster-test.ru`

### Example Scenarios:

**Scenario 1: Direct Infrastructure Access**
```
User enters: example.com
Redirect to: dproxy.hoster.ru
Notification: example.com (SUSPICIOUS)
```

**Scenario 2: Multiple Redirects**
```
User enters: phishing-site.com
Redirect 1: srv244376.hoster-test.ru
Redirect 2: dproxy.hoster.ru
Notification: phishing-site.com (DANGEROUS)
```

**Scenario 3: No Redirect**
```
User enters: legitimate-bank.com
No redirect
Notification: legitimate-bank.com (if suspicious)
```

## Additional Notes

### Connection Timeout Issue
The error `failed to connect to /31.28.24.114 (port 443)` suggests:
- Browser tried HTTPS (port 443) even though URL was HTTP
- This is normal browser behavior (HTTPS upgrade attempt)
- Domain is still tracked and analyzed before connection attempt
- Failed connections don't prevent threat detection

### Why No Notification for srv244376.hoster-test.ru?
Possible reasons:
1. **Connection failed before analysis** - If the SOCKS request came in as IP only
2. **Not suspicious enough** - Domain might not have triggered high enough score
3. **Cached as safe** - Previous analysis might have cached it as safe

To debug, check logs for:
```
🔍 Domain detected via SOCKS: srv244376.hoster-test.ru
🔬 Starting analysis for: srv244376.hoster-test.ru
📊 Analysis result: srv244376.hoster-test.ru = [VERDICT]
```

## Testing

### Test Cases:
1. `http://srv244376.hoster-test.ru/` - Should detect infrastructure redirect
2. `http://srv244424.hoster-test.ru/usaa` - Should flag HTTP + banking keyword
3. Any site that redirects through `dproxy.*` or `proxy.*` domains

### Expected Behavior:
- Original user-entered domain shown in notification
- Infrastructure domains detected and hidden from user
- HTTP connections to sensitive sites flagged
- Clear reasons provided for threats

## Build Status
✅ **BUILD SUCCESSFUL** - Ready for testing

## Files Modified
- `app/src/main/java/com/phishguard/phishguard/service/vpn/PhishGuardVpnService.kt`
  - Enhanced `looksLikeInfrastructure()` with 3 new patterns
