# Phase 1: VPN Service Foundation - Checklist

## Overview
Get basic VPN service running with simple URL detection and notification system.

## Progress Tracker

### Week 1: Setup & Infrastructure ✅
- [x] Create Android project structure
- [x] Configure Gradle dependencies
- [x] Set up version catalog
- [x] Add VPN permissions to manifest
- [x] Create basic UI with Jetpack Compose
- [x] Implement PhishGuardApplication class

### Week 1-2: VPN Service Core ✅ In Progress
- [x] Implement PhishGuardVpnService class
- [x] VPN tunnel establishment
- [x] Foreground service with notification
- [x] Start/Stop controls
- [x] Basic packet reading loop
- [ ] **NEXT: TLS SNI extraction** ⏳
- [ ] DNS query parsing completion
- [ ] HTTP URL extraction
- [ ] Packet forwarding logic

### Week 2: URL Detection & Logging
- [ ] Extract domains from DNS queries
- [ ] Extract SNI from HTTPS ClientHello
- [ ] Extract URLs from HTTP requests
- [ ] Log all detected URLs/domains
- [ ] Test with real browser traffic
- [ ] Verify all apps' traffic is captured

### Week 2: Basic Notification System
- [ ] Create notification channel
- [ ] Show notification for detected URLs
- [ ] Add notification actions (View, Block)
- [ ] Test notification display
- [ ] Handle notification clicks

### Week 2: Testing & Validation
- [ ] Test VPN connection stability
- [ ] Test with multiple apps (Chrome, WhatsApp, etc.)
- [ ] Verify no traffic leaks
- [ ] Test VPN reconnection after network change
- [ ] Test battery usage
- [ ] Test with known phishing URLs

## Success Criteria

### Must Have
- ✅ VPN service starts and routes traffic
- [ ] Can extract URLs from HTTP traffic
- [ ] Can extract domains from DNS queries
- [ ] Can extract SNI from HTTPS traffic
- [ ] Shows notification when URL detected
- [ ] VPN remains stable during use
- [ ] No significant battery drain

### Nice to Have
- [ ] URL deduplication
- [ ] Traffic statistics
- [ ] Connection status indicator
- [ ] Quick settings tile

## Technical Debt / Known Issues

1. **Hilt DI Disabled**: Temporarily disabled due to AGP 9.0 beta compatibility
   - Impact: Manual dependency management for now
   - Resolution: Re-enable when AGP stable or downgrade AGP

2. **TensorFlow Lite Disabled**: Namespace conflict in AGP 9.0 beta
   - Impact: No ML model yet (not needed for Phase 1)
   - Resolution: Configure properly in Phase 3

3. **Packet Forwarding**: Currently just reading, not properly forwarding
   - Impact: Traffic might not flow correctly
   - Resolution: Implement proper packet forwarding logic

## Code Locations

### VPN Service
- `app/src/main/java/com/phishguard/phishguard/service/vpn/PhishGuardVpnService.kt`
- `app/src/main/java/com/phishguard/phishguard/service/vpn/PacketParser.kt`

### UI
- `app/src/main/java/com/phishguard/phishguard/MainActivity.kt`

### Configuration
- `app/build.gradle.kts` - Dependencies
- `gradle/libs.versions.toml` - Version catalog
- `app/src/main/AndroidManifest.xml` - Permissions & service

## Next Session Goals

1. **Implement TLS SNI Extraction**
   - Parse TLS ClientHello packets
   - Extract Server Name Indication
   - Log extracted domains

2. **Complete DNS Parsing**
   - Test DNS query extraction
   - Handle multiple questions
   - Log all DNS queries

3. **Add URL Logging**
   - Create simple logging system
   - Log all detected URLs/domains
   - Display in UI or Logcat

## Testing Commands

```bash
# Build project
./gradlew build

# Install on device
./gradlew installDebug

# View logs
adb logcat | grep PhishGuard

# Check VPN status
adb shell dumpsys connectivity | grep VPN
```

## Resources

- [Android VpnService Documentation](https://developer.android.com/reference/android/net/VpnService)
- [TLS ClientHello Format](https://datatracker.ietf.org/doc/html/rfc5246#section-7.4.1.2)
- [DNS Packet Format](https://datatracker.ietf.org/doc/html/rfc1035#section-4.1)
- iOS Implementation: Reference for detection logic

## Notes

- VPN service must run as foreground service (Android 8+)
- Packet parsing is performance-critical - keep it efficient
- Test on real device for accurate network behavior
- Consider battery optimization from the start

---

**Current Focus**: Implement TLS SNI extraction to detect HTTPS domains
