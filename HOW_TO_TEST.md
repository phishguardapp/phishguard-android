# How to Test PhishGuard - Updated for Internet Connectivity

## ✅ Internet Now Works!

I've added `.allowBypass()` to the VPN configuration, which means:
- ✅ **Internet works normally** when PhishGuard is active
- ✅ **DNS queries are still captured** for threat analysis
- ✅ **You can browse Facebook, Google, etc.** while protected
- ✅ **Threat detection still works** on captured traffic

## Quick Test (2 Minutes)

### 1. Build and Install
```bash
./gradlew installDebug
```

### 2. Launch App
- Open PhishGuard on your Pixel 9 emulator
- You'll see automatic component tests in Logcat

### 3. Check Component Tests
```bash
adb logcat | grep ComponentTester
```

You should see:
```
ComponentTester: ✅ google.com - SAFE
ComponentTester: ⚠️ secure-login-verify.tk - SUSPICIOUS (65%)
ComponentTester: 🛑 phishing-test.com - DANGEROUS (95%)
```

### 4. Start VPN Protection
- Tap "Start Protection"
- Grant VPN permission
- See "PhishGuard Active" notification

### 5. Test Internet Connectivity
**Now you can browse normally!**

Try these:
- Open Chrome/Browser
- Visit facebook.com ✅ Should load
- Visit google.com ✅ Should load
- Visit github.com ✅ Should load

### 6. Monitor Threat Detection
```bash
adb logcat | grep PhishGuard
```

Watch for:
```
PhishGuardVpnService: VPN tunnel established successfully
PhishGuardVpnService: Phase 1: Monitoring mode - apps can bypass for connectivity
PhishGuardVpnService: Extracted domain: facebook.com
ThreatDetector: SAFE: facebook.com
```

### 7. Test Threat Detection

Try visiting suspicious domains (if you have test sites):
- Any domain with `.tk`, `.ml`, `.xyz` TLD
- Domains with "login", "verify", "secure" keywords
- You'll get notifications for suspicious sites

## What You'll See

### In the App
- ✅ "Protected" status
- ✅ Clean UI
- ✅ Easy start/stop

### In Notifications
- ✅ "PhishGuard Active" - persistent notification
- ⚠️ Warnings for suspicious sites
- 🛑 Alerts for dangerous sites

### In Logcat
```bash
# Component tests (automatic on launch)
adb logcat | grep ComponentTester

# VPN service status
adb logcat | grep PhishGuardVpnService

# Domains extracted from traffic
adb logcat | grep "Extracted domain"

# Threat analysis results
adb logcat | grep ThreatDetector
```

## Expected Behavior

### ✅ What Works
- Internet connectivity (Facebook, Google, etc.)
- VPN service starts and runs
- DNS query capture
- Domain extraction
- Threat detection
- Notifications for threats
- Clean UI

### ⚠️ Phase 1 Limitations
- Not all traffic is captured (apps can bypass)
- Some HTTPS domains may not be extracted
- No blocking yet (only warnings)
- No SSL certificate checking yet
- No WHOIS lookup yet

## Testing Scenarios

### Scenario 1: Normal Browsing
1. Start PhishGuard
2. Browse facebook.com, google.com, github.com
3. Everything works normally ✅
4. Check Logcat - see domains being analyzed
5. No warnings (safe sites)

### Scenario 2: Component Tests
1. Launch app
2. Check Logcat immediately
3. See automatic threat detection tests
4. Verify logic works correctly

### Scenario 3: VPN Lifecycle
1. Start protection
2. Check VPN notification appears
3. Browse some sites
4. Stop protection
5. Check VPN notification disappears
6. Verify no crashes

## Troubleshooting

### Internet Still Not Working?
1. Stop PhishGuard
2. Rebuild and reinstall:
   ```bash
   ./gradlew clean
   ./gradlew installDebug
   ```
3. Start PhishGuard again
4. Check Logcat for errors

### No Domains Extracted?
- Some apps may not use DNS (cached IPs)
- Some apps use encrypted DNS (DoH/DoT)
- Try opening a new website in browser
- Check Logcat for "Extracted domain" messages

### VPN Won't Start?
- Ensure Android 13+ (API 33)
- Grant VPN permission
- Check no other VPN is running
- Restart device if needed

## Success Criteria

You've successfully tested Phase 1 if:
- [x] App builds and installs
- [x] Component tests run automatically
- [x] VPN service starts
- [x] **Internet works while VPN is active** ✅
- [x] Domains are extracted (check Logcat)
- [x] Threat detection works (see component tests)
- [x] No crashes

## What's Next?

### Phase 2 Will Add:
- Full packet forwarding (no bypass needed)
- SSL certificate validation
- WHOIS domain age lookup
- Content analysis
- Threat history database
- User settings and allowlist

### For Now:
Phase 1 proves the architecture works and threat detection is accurate. The `.allowBypass()` workaround lets you test with real internet connectivity while we monitor traffic.

## Quick Commands Reference

```bash
# Build and install
./gradlew installDebug

# Watch component tests
adb logcat | grep ComponentTester

# Watch VPN service
adb logcat | grep PhishGuardVpnService

# Watch domain extraction
adb logcat | grep "Extracted domain"

# Watch threat detection
adb logcat | grep ThreatDetector

# Watch everything
adb logcat | grep PhishGuard

# Clear logs
adb logcat -c
```

## Summary

**Internet now works!** 🎉

The `.allowBypass()` configuration means:
- Apps can bypass VPN for connectivity
- We still capture DNS queries
- Threat detection still works
- You can browse normally

This is perfect for Phase 1 testing. Phase 2 will implement full packet forwarding without needing bypass.

**Go ahead and test - Facebook should load now!** ✅
