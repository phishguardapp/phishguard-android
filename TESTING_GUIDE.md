# PhishGuard Android - Testing Guide

## Phase 1 Testing Limitations

### Important Note About Internet Connectivity

**Phase 1 uses a simplified VPN implementation** that focuses on packet inspection and domain extraction. Due to the complexity of implementing full packet forwarding with network sockets, Phase 1 has the following limitation:

- ✅ VPN tunnel establishes successfully
- ✅ Packets are captured and parsed
- ✅ Domains are extracted from DNS/HTTP/HTTPS
- ✅ Threat detection works
- ⚠️ **Internet connectivity may be limited** (packets loop back to VPN interface)

This is a known Phase 1 limitation that will be resolved in Phase 2 with proper network socket forwarding.

## Testing Options

### Option 1: Test with Simulated Traffic (Recommended for Phase 1)

Since full packet forwarding isn't implemented yet, the best way to test Phase 1 is to:

1. **Review the code** - All components are implemented and compile
2. **Check Logcat** - See packet parsing and domain extraction in logs
3. **Unit test the components** - Test PacketParser and ThreatDetector directly

### Option 2: Test DNS Monitoring Only

You can test DNS query extraction:

1. Start the VPN
2. The VPN will capture DNS queries
3. Check Logcat for extracted domains:
   ```bash
   adb logcat | grep "Extracted domain"
   ```

### Option 3: Disable VPN for Connectivity Testing

To test the UI and service lifecycle without blocking internet:

1. Comment out the VPN tunnel establishment
2. Test the service start/stop
3. Test the UI interactions
4. Test notification system with mock data

## What You CAN Test in Phase 1

### 1. VPN Service Lifecycle
```bash
# Start the app
# Tap "Start Protection"
# Check Logcat:
adb logcat | grep PhishGuardVpnService

# You should see:
# "VPN Service created"
# "VPN tunnel established successfully"
# "Starting packet processing loop"
```

### 2. Packet Parser (Unit Test)
Create a test file to verify packet parsing:

```kotlin
@Test
fun testDnsPacketParsing() {
    val parser = PacketParser()
    // Create mock DNS packet
    val packet = createMockDnsPacket("google.com")
    val result = parser.parse(packet)
    assertEquals("google.com", result?.domain)
}
```

### 3. Threat Detector (Unit Test)
Test threat detection logic:

```kotlin
@Test
fun testThreatDetection() {
    val detector = ThreatDetector()
    
    // Test safe domain
    val safe = detector.analyze("google.com")
    assertEquals(ThreatDetector.Verdict.SAFE, safe.verdict)
    
    // Test suspicious domain
    val suspicious = detector.analyze("secure-login-verify.tk")
    assertEquals(ThreatDetector.Verdict.SUSPICIOUS, suspicious.verdict)
    
    // Test dangerous domain
    val dangerous = detector.analyze("phishing-test.com")
    assertEquals(ThreatDetector.Verdict.DANGEROUS, dangerous.verdict)
}
```

### 4. UI Components
- ✅ App launches
- ✅ VPN permission dialog appears
- ✅ Start/Stop button works
- ✅ Status updates correctly
- ✅ Notifications appear (can test with mock data)

## Workaround for Full Testing

If you need to test with actual internet connectivity, you have two options:

### Workaround 1: Add allowBypass()

Temporarily modify the VPN builder to allow apps to bypass:

```kotlin
vpnInterface = Builder()
    .setSession("PhishGuard")
    .addAddress(VPN_ADDRESS, 32)
    .addRoute(VPN_ROUTE, 0)
    .addDnsServer("8.8.8.8")
    .addDnsServer("8.8.4.4")
    .setMtu(VPN_MTU)
    .setBlocking(false)
    .allowBypass()  // Add this line
    .establish()
```

This allows apps to bypass the VPN for connectivity while still capturing some DNS queries.

### Workaround 2: Test Individual Components

Test each component separately without the VPN:

1. **PacketParser**: Create unit tests with mock packets
2. **ThreatDetector**: Test with various domain strings
3. **UI**: Test with mock VPN states
4. **Notifications**: Trigger manually with test data

## Recommended Testing Approach for Phase 1

```
1. Code Review ✅
   - Review PhishGuardVpnService.kt
   - Review PacketParser.kt
   - Review ThreatDetector.kt
   - Verify logic is sound

2. Unit Tests ✅
   - Test ThreatDetector with various domains
   - Test PacketParser with mock packets
   - Verify scoring algorithms

3. Service Lifecycle ✅
   - Test VPN service starts
   - Test VPN tunnel establishes
   - Test service stops cleanly
   - Check for memory leaks

4. UI Testing ✅
   - Test permission flow
   - Test start/stop button
   - Test status display
   - Test notification appearance

5. Logcat Monitoring ✅
   - Watch for packet capture
   - Watch for domain extraction
   - Watch for threat analysis
   - Verify no crashes
```

## Phase 2 Will Include

- ✅ Proper network socket forwarding
- ✅ Full packet routing to real network
- ✅ Complete internet connectivity
- ✅ Production-ready VPN implementation

## Testing Commands

```bash
# Build the app
./gradlew build

# Install on device
./gradlew installDebug

# Watch all PhishGuard logs
adb logcat | grep PhishGuard

# Watch VPN service logs
adb logcat | grep PhishGuardVpnService

# Watch packet parsing
adb logcat | grep PacketParser

# Watch threat detection
adb logcat | grep ThreatDetector

# Clear logs
adb logcat -c

# Check for crashes
adb logcat | grep AndroidRuntime
```

## Expected Logcat Output

When VPN starts successfully:
```
PhishGuardVpnService: VPN Service created
PhishGuardVpnService: VPN Service start command received
PhishGuardVpnService: VPN tunnel established successfully
PhishGuardVpnService: Starting packet processing loop
PhishGuardVpnService: Note: Phase 1 uses simple packet inspection
```

When domain is extracted:
```
PhishGuardVpnService: Extracted domain: google.com
ThreatDetector: SAFE: google.com
```

When threat is detected:
```
PhishGuardVpnService: Extracted domain: secure-login-verify.tk
ThreatDetector: SUSPICIOUS: secure-login-verify.tk (65%)
PhishGuardVpnService: SUSPICIOUS: secure-login-verify.tk (65%)
```

## Troubleshooting

### No Internet After Starting VPN
**Expected in Phase 1** - This is the known limitation. Options:
1. Stop the VPN to restore connectivity
2. Add `.allowBypass()` to VPN builder (see workaround above)
3. Wait for Phase 2 implementation

### No Domains Extracted
- Check Logcat for "Starting packet processing loop"
- Verify VPN tunnel established
- Try making a network request (even if it fails)
- Check for parsing errors in logs

### App Crashes
- Check Logcat for stack traces
- Verify all permissions granted
- Check for null pointer exceptions
- Verify VPN permission granted

### VPN Won't Start
- Ensure Android 13+ (API 33)
- Grant VPN permission when prompted
- Check for other VPN apps running
- Restart device if needed

## Summary

Phase 1 focuses on:
- ✅ VPN service architecture
- ✅ Packet parsing logic
- ✅ Domain extraction
- ✅ Threat detection algorithms
- ✅ Notification system
- ✅ Clean code structure

**Internet connectivity limitation is expected and will be resolved in Phase 2.**

For now, test individual components, verify the architecture, and prepare for Phase 2 where we'll implement proper network forwarding.

## Next Steps

When you're ready for Phase 2:
1. Implement network socket forwarding
2. Add proper packet routing
3. Test with full internet connectivity
4. Add SSL certificate checking
5. Add WHOIS lookup
6. Implement content analysis

See `docs/ANDROID_IMPLEMENTATION_PLAN.md` for Phase 2 details.
