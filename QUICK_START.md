# PhishGuard Android - Quick Start Guide

## 🚀 Get Started in 2 Minutes

### Prerequisites
- Android Studio Hedgehog or later
- Android device/emulator with API 33+ (Android 13+)
- USB debugging enabled (for physical device)

### Step 1: Open Project
```bash
# Clone and open in Android Studio
cd PhishGuard-Android
# File → Open → Select this directory
```

### Step 2: Sync Gradle
- Android Studio will automatically sync
- Wait for "Gradle sync finished" message
- Should complete without errors

### Step 3: Run the App
1. Connect Android device or start emulator (API 33+)
2. Click Run (▶️) or press Shift+F10
3. App installs and launches

### Step 4: Watch the Magic! ✨
**The app automatically tests all components on startup!**

Open Logcat and watch:
```bash
adb logcat | grep ComponentTester
```

You'll see threat detection in action:
```
ComponentTester: ✅ google.com - SAFE
ComponentTester: ⚠️ secure-login-verify.tk - SUSPICIOUS (65%)
ComponentTester: 🛑 phishing-test.com - DANGEROUS (95%)
```

### That's It!

Phase 1 automatically demonstrates:
- ✅ Threat detection works
- ✅ Pattern matching works
- ✅ Scoring algorithm works
- ✅ All logic is functional

### Optional: Test VPN Service

Want to test the VPN service too?

1. Tap "Start Protection" button
2. Grant VPN permission when prompted
3. Watch Logcat:
```bash
adb logcat | grep PhishGuardVpnService
```

**Note:** Internet may not work when VPN is active (Phase 1 limitation). This is expected and will be fixed in Phase 2.

### Step 5: Monitor Activity
```bash
# Watch component tests (recommended)
adb logcat | grep ComponentTester

# Watch all PhishGuard activity
adb logcat | grep PhishGuard

# Watch threat detection
adb logcat | grep ThreatDetector

# Watch VPN service
adb logcat | grep PhishGuardVpnService
```

## 📱 What You'll See

### In the App
- Protection status (Protected/Not Protected)
- Simple toggle button
- Phase progress indicator

### In Notifications
- **Suspicious sites:** Yellow warning with details
- **Dangerous sites:** Red alert with confidence score
- Tap to dismiss

### In Logcat
```
PhishGuardVpnService: VPN tunnel established successfully
PacketParser: Extracted domain: google.com
ThreatDetector: SAFE: google.com
PacketParser: Extracted domain: secure-login-verify.tk
ThreatDetector: SUSPICIOUS: secure-login-verify.tk (65%)
  - Uses suspicious TLD: .tk
  - Contains suspicious keyword: login
```

## 🧪 Testing Scenarios

### Scenario 1: Safe Browsing
1. Start protection
2. Visit google.com, github.com, wikipedia.org
3. No notifications (safe sites)
4. Check Logcat: "SAFE: domain.com"

### Scenario 2: Suspicious Pattern
1. Visit any .tk, .ml, or .xyz domain
2. Receive warning notification
3. Check Logcat for analysis details

### Scenario 3: Multiple Factors
1. Visit domain like: secure-login-verify-account.tk
2. High suspicion score (multiple factors)
3. Detailed notification with reasons

## 🔧 Troubleshooting

### VPN Won't Start
- **Check:** Android 13+ required
- **Check:** VPN permission granted
- **Fix:** Uninstall and reinstall app

### No Notifications
- **Check:** Notification permission granted (Android 13+)
- **Check:** App not in battery optimization
- **Fix:** Settings → Apps → PhishGuard → Notifications → Allow

### No Domains Detected
- **Check:** VPN is actually running (notification visible)
- **Check:** Logcat for errors
- **Try:** Restart VPN service

### Build Errors
- **Check:** Gradle sync completed
- **Check:** Android SDK 33+ installed
- **Fix:** File → Invalidate Caches → Restart

## 📊 What's Working (Phase 1)

✅ VPN tunnel establishment
✅ System-wide traffic interception
✅ DNS query extraction
✅ HTTP Host header extraction
✅ HTTPS TLS SNI extraction
✅ Pattern-based threat detection
✅ Real-time notifications
✅ Clean UI with Material 3

## 🎯 What's Next (Phase 2)

⏳ SSL certificate validation
⏳ WHOIS/domain age lookup
⏳ Tranco top sites database
⏳ HTML content analysis
⏳ Threat history database
⏳ User settings and allowlist

## 📚 Documentation

- **SETUP_COMPLETE.md** - Full setup details
- **PHASE_1_IMPLEMENTATION.md** - Technical deep dive
- **docs/ANDROID_ARCHITECTURE.md** - System architecture
- **docs/START_HERE.md** - Project overview

## 🐛 Known Issues

1. **Hilt DI disabled** - Due to AGP 9.0 beta compatibility (temporary)
2. **TensorFlow Lite disabled** - Will enable in Phase 3
3. **IPv6 not supported** - Coming in Phase 2
4. **No blocking yet** - Phase 1 is detection only

## 💡 Tips

- **Battery:** VPN service is optimized, minimal battery impact
- **Performance:** No noticeable slowdown in browsing
- **Privacy:** All processing on-device, no data sent anywhere
- **Testing:** Use TestUrls.kt for consistent test domains

## 🎉 Success!

If you can:
1. Start the VPN ✅
2. See "Protected" status ✅
3. Receive notifications for suspicious domains ✅
4. See analysis in Logcat ✅

**You're ready to develop Phase 2!** 🚀

---

## Quick Commands

```bash
# Build
./gradlew build

# Install
./gradlew installDebug

# Run tests
./gradlew test

# Clean
./gradlew clean

# Logcat
adb logcat | grep PhishGuard
```

## Need Help?

Check the documentation in `docs/` folder or review the implementation in:
- `service/vpn/PhishGuardVpnService.kt`
- `service/vpn/PacketParser.kt`
- `service/vpn/ThreatDetector.kt`
