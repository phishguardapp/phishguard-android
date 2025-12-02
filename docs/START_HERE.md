# START HERE - PhishGuard Android Development

## 👋 Welcome!

This document is your starting point for building PhishGuard for Android. It provides context, priorities, and the exact first steps to take.

## 📋 Project Context

**What is PhishGuard?**
- Real-time phishing and scam detection app
- VPN-based system-wide protection (works in all apps, not just browsers)
- On-device ML classification (TensorFlow Lite, enhanced with Gemini Nano on Android 14+)
- Zero data collection, complete privacy
- Multilingual support (12 languages)
- Supports Android 13+ (~55% market coverage)

**iOS Version Status:**
- ✅ Approved and live on App Store
- ✅ Safari extension for iOS/macOS
- ✅ All detection logic working and tested
- ✅ Full feature documentation available

**Android Version Goal:**
- Port all iOS detection features to Android
- Use VPN service for system-wide protection
- Maintain feature parity with iOS
- Target Android 13+ (API 33+) for broad reach
- Optional Gemini Nano enhancement on Android 14+

## 📚 Key Documents (Read These First)

1. **ANDROID_ARCHITECTURE.md** - Complete technical architecture
2. **FEATURE_PARITY_MAPPING.md** - iOS → Android component mapping
3. **ANDROID_IMPLEMENTATION_PLAN.md** - Step-by-step build guide

## 🎯 Development Priorities

### Phase 1: Foundation (Weeks 1-2)
**Goal:** Get basic VPN service running with simple URL detection

**Tasks:**
1. Set up project dependencies (see ANDROID_IMPLEMENTATION_PLAN.md)
2. Implement VPN service foundation
3. Basic packet inspection and URL extraction
4. Simple notification system
5. Test with known phishing URLs

**Success Criteria:**
- VPN service starts and routes traffic
- Can extract URLs from network packets
- Shows notification when suspicious URL detected

### Phase 2: Detection Engine (Weeks 3-4)
**Goal:** Port all feature extractors from iOS

**Tasks:**
1. SSL certificate checker (port from iOS SSLChecker.swift)
2. WHOIS/RDAP domain analyzer
3. Tranco ranking database integration
4. Content analyzer (Jsoup - similar to SwiftSoup)
5. Public Suffix List implementation

**Success Criteria:**
- All feature extractors working
- Can analyze URLs with same signals as iOS
- Unit tests passing

### Phase 3: ML Integration (Weeks 5-6)
**Goal:** Integrate machine learning models

**Tasks:**
1. Convert/train TensorFlow Lite model
2. Implement hybrid classifier (TFLite + Gemini Nano)
3. Feature extraction pipeline
4. Classification logic
5. Confidence scoring

**Success Criteria:**
- ML model running on-device
- Classification accuracy matches iOS
- Inference time < 100ms

### Phase 4: UI & Polish (Weeks 7-8)
**Goal:** Complete user interface and experience

**Tasks:**
1. Jetpack Compose UI
2. Settings screens
3. Threat history dashboard
4. Rich notifications
5. Onboarding flow

**Success Criteria:**
- Intuitive user interface
- All settings functional
- Smooth user experience

### Phase 5: Testing & Release (Weeks 9-10)
**Goal:** Test thoroughly and submit to Play Store

**Tasks:**
1. Comprehensive testing
2. Performance optimization
3. Battery usage optimization
4. Play Store assets
5. Submit for review

## 🚀 First Steps (Do This Now)

### Step 1: Verify Project Setup

Check that you have:
- [ ] Android Studio Hedgehog or later
- [ ] JDK 17+
- [ ] Android SDK 33+ (minimum) and 34 (target)
- [ ] Project created with correct package name
- [ ] All planning docs in `docs/android/`

### Step 2: Configure Dependencies

Update your `build.gradle.kts` with all required dependencies (see ANDROID_IMPLEMENTATION_PLAN.md section "Step 2: Configure build.gradle.kts")

### Step 3: Create Project Structure

```
app/src/main/java/com/phishguard/android/
├── data/
│   ├── database/
│   ├── repository/
│   └── model/
├── domain/
│   ├── usecase/
│   └── model/
├── presentation/
│   ├── ui/
│   ├── viewmodel/
│   └── navigation/
├── service/
│   ├── vpn/
│   └── notification/
├── ml/
│   ├── classifier/
│   └── feature/
└── util/
```

### Step 4: Implement VPN Service Foundation

**Start with this file:** `service/vpn/PhishGuardVpnService.kt`

```kotlin
package com.phishguard.android.service.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream

class PhishGuardVpnService : VpnService() {
    
    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startVpn()
        return START_STICKY
    }
    
    private fun startVpn() {
        // TODO: Implement VPN tunnel setup
        // See ANDROID_ARCHITECTURE.md for details
    }
    
    override fun onDestroy() {
        stopVpn()
        serviceScope.cancel()
        super.onDestroy()
    }
    
    private fun stopVpn() {
        vpnInterface?.close()
        vpnInterface = null
    }
}
```

### Step 5: Add VPN Permission

In `AndroidManifest.xml`:

```xml
<manifest>
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    
    <application>
        <service
            android:name=".service.vpn.PhishGuardVpnService"
            android:permission="android.permission.BIND_VPN_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.net.VpnService" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

## 💡 Development Tips

### When Porting from iOS

1. **Reference the iOS code** - The detection logic is proven and tested
2. **Use FEATURE_PARITY_MAPPING.md** - Shows exact Swift → Kotlin equivalents
3. **Keep the same algorithm** - Don't reinvent, just translate
4. **Test against iOS** - Same inputs should give same outputs

### Performance Targets

- URL analysis: < 50ms
- Deep analysis: < 500ms
- Battery impact: < 5% per day
- Memory usage: < 100MB

### Privacy Principles

- ✅ All processing on-device
- ✅ No data sent to servers
- ✅ No analytics or tracking
- ✅ No browsing history stored
- ✅ Open source detection logic

## 🔗 Useful Resources

### Android Development
- [VpnService Documentation](https://developer.android.com/reference/android/net/VpnService)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)

### ML & AI
- [TensorFlow Lite Android](https://www.tensorflow.org/lite/android)
- [Gemini Nano](https://ai.google.dev/gemini-api/docs/models/gemini#gemini-nano)
- [ML Kit](https://developers.google.com/ml-kit)

### Libraries
- [OkHttp](https://square.github.io/okhttp/)
- [Jsoup](https://jsoup.org/)
- [Room Database](https://developer.android.com/training/data-storage/room)
- [Hilt DI](https://dagger.dev/hilt/)

## 🎬 Suggested First Prompt for Kiro

When you open a new chat in the Android project, start with:

```
I'm building PhishGuard for Android - a VPN-based phishing detection app.

Context:
- iOS version is live and working
- All planning docs are in docs/android/
- Need to implement VPN service foundation first

Let's start by:
1. Setting up the VPN service (PhishGuardVpnService.kt)
2. Implementing basic packet inspection
3. Extracting URLs from network traffic

Reference: #File docs/android/ANDROID_ARCHITECTURE.md
Reference: #File docs/android/START_HERE.md
```

## 📞 Need Help?

If you get stuck:

1. **Check the planning docs** - Most questions are answered there
2. **Reference iOS implementation** - The logic is proven
3. **Use #Codebase** - Let Kiro understand your full project
4. **Ask specific questions** - "How do I extract URLs from packets?" vs "Help me"

## ✅ Checklist Before You Start Coding

- [ ] Read ANDROID_ARCHITECTURE.md
- [ ] Read FEATURE_PARITY_MAPPING.md
- [ ] Project dependencies configured
- [ ] Project structure created
- [ ] VPN permission added to manifest
- [ ] Android Studio project builds successfully
- [ ] Git repo initialized and connected

## 🎯 Success Metrics

**Week 2:** VPN service running, basic URL detection
**Week 4:** All feature extractors ported
**Week 6:** ML classification working
**Week 8:** UI complete, ready for testing
**Week 10:** Submitted to Play Store

## 🚦 Current Status

- [x] iOS app approved and live
- [x] Planning documents created
- [x] Android project initialized
- [ ] VPN service implementation ← **YOU ARE HERE**
- [ ] Feature extractors ported
- [ ] ML integration
- [ ] UI development
- [ ] Testing & optimization
- [ ] Play Store submission

---

## Ready to Start?

Open a new Kiro chat in your Android project and let's build this! 🚀

**First task:** Implement the VPN service foundation (see Step 4 above)

Good luck! 💪
