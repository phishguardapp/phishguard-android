# Advanced Threat Checks - Debugging Guide

## Issue
Notifications are working, but SSL/Domain age information is not showing up in alerts.

## What's Implemented
All advanced checks are fully implemented:
1. **Domain Age Checker** - Uses RDAP API to check registration date
2. **SSL Certificate Validator** - Validates HTTPS certificates
3. **Tranco Ranking Checker** - Checks domain popularity

## Test Case: clientenetonline.com
- **Registered**: 2025-12-06 (1 day old!)
- **RDAP API works**: Confirmed via curl
- **Expected**: Should show "Domain registered less than 7 days ago"

## Enhanced Logging Added
The new build includes detailed logging:

```
🔍 Running advanced checks for: domain.com
  Checking domain age...
  ✅ Domain age: X days (source: RDAP)
  Checking SSL certificate...
  ✅ SSL: valid=true, selfSigned=false, expired=false
  Checking Tranco ranking...
  ⚠️ Tranco: Not in top 1M sites
🏁 Advanced checks complete
```

## Potential Issues

### 1. VPN Routing Loop
Even though we use `addDisallowedApplication(packageName)`, the VPN might still interfere with our own API calls.

**Solution**: The checks have 3-second timeouts and fail gracefully.

### 2. Network Connectivity
The advanced checks require internet access to external APIs:
- RDAP servers (rdap.verisign.com, etc.)
- SSL certificate validation (connects to domain)
- Tranco API

**Check**: Look for timeout errors in logs.

### 3. API Rate Limiting
RDAP servers might rate-limit requests.

**Mitigation**: Results are cached for 30 days.

## Testing Instructions

1. **Install new APK**: `app/build/outputs/apk/debug/app-debug.apk`

2. **Clear logcat**: `adb logcat -c`

3. **Start logging**: `adb logcat | grep ThreatDetector`

4. **Visit test site**: https://clientenetonline.com/

5. **Check logs for**:
   - `🔍 Running advanced checks`
   - `✅ Domain age: 1 days`
   - Any `❌` error messages

## Expected Results

For clientenetonline.com (registered yesterday):
```
⚠️ Suspicious Site Warning
Risk Level: 65%

Why this is flagged:
• Domain registered less than 7 days ago (1 days)
• Contains suspicious keyword: online
• [Other pattern-based reasons]
```

## If Advanced Checks Fail

The system will still work with pattern-based detection:
- Suspicious keywords (login, verify, account, etc.)
- Dangerous TLDs (.tk, .ml, .xyz, etc.)
- Excessive subdomains
- Homograph attacks
- IP addresses instead of domains

## Next Steps

1. Install and test with enhanced logging
2. Share logcat output showing the advanced checks section
3. If checks are timing out, we may need to:
   - Increase timeout from 3s to 5s
   - Run checks in background after showing initial notification
   - Add fallback to cached/offline data

## Files Modified
- `app/src/main/java/com/phishguard/phishguard/service/vpn/ThreatDetector.kt`
  - Added detailed logging for all advanced checks
  - Shows success/failure for each check
  - Logs actual values returned

## Build Status
✅ Build successful
📦 APK ready: `app/build/outputs/apk/debug/app-debug.apk`
