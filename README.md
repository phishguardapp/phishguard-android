# PhishGuard for Android

Real-time phishing and scam detection for Android using VPN-based system-wide protection.

## 🎯 Project Status

**Phase 1: VPN Service Foundation** ✅ In Progress

- ✅ Project setup with all dependencies
- ✅ VPN service implementation
- ✅ Basic packet processing infrastructure
- ⏳ URL/domain extraction (next step)
- ⏳ Phishing detection engine (coming soon)

## 🏗️ Architecture

PhishGuard uses a VPN service to intercept all network traffic system-wide, providing protection across all apps (not just browsers).

### Key Components

1. **VPN Service** (`PhishGuardVpnService.kt`)
   - Establishes local VPN tunnel
   - Captures all outgoing packets
   - Routes traffic through detection engine

2. **Packet Parser** (`PacketParser.kt`)
   - Parses IP/TCP/UDP packets
   - Extracts DNS queries
   - Will extract TLS SNI for HTTPS domains

3. **Detection Engine** (coming in Phase 2)
   - Feature extractors (SSL, WHOIS, content analysis)
   - TensorFlow Lite ML model
   - Optional Gemini Nano for deep analysis

## 🚀 Getting Started

### Prerequisites

- Android Studio Hedgehog or later
- JDK 17+
- Android SDK 33+ (minimum) and 34+ (target)

### Build & Run

1. Clone the repository
2. Open in Android Studio
3. Sync Gradle dependencies
4. Run on device or emulator (Android 13+)

### Testing the VPN

1. Launch the app
2. Tap "Start Protection"
3. Grant VPN permission when prompted
4. VPN service will start and show a persistent notification

## 📚 Documentation

All planning and architecture docs are in the `docs/` folder:

- `START_HERE.md` - Quick start guide
- `ANDROID_ARCHITECTURE.md` - Complete technical architecture
- `ANDROID_IMPLEMENTATION_PLAN.md` - Step-by-step build plan
- `FEATURE_PARITY_MAPPING.md` - iOS to Android mapping

## 🔧 Tech Stack

- **Language:** Kotlin
- **Min SDK:** Android 13 (API 33)
- **Target SDK:** Android 14 (API 34)
- **UI:** Jetpack Compose
- **DI:** Hilt
- **Database:** Room
- **ML:** TensorFlow Lite + Gemini Nano (optional)
- **Networking:** OkHttp, Jsoup

## 📝 Next Steps

### Immediate (Phase 1 completion)
1. Implement TLS SNI extraction for HTTPS domains
2. Complete DNS query parsing
3. Add basic URL detection and logging
4. Test with known phishing URLs

### Phase 2 (Detection Engine)
1. Port SSL certificate checker from iOS
2. Implement WHOIS/RDAP analyzer
3. Add Tranco ranking database
4. Build content analyzer with Jsoup
5. Create feature extraction pipeline

### Phase 3 (ML Integration)
1. Train/convert TensorFlow Lite model
2. Integrate Gemini Nano for Android 14+
3. Implement hybrid classifier
4. Add confidence scoring

## 🔒 Privacy

- ✅ All processing on-device
- ✅ No data sent to servers
- ✅ No browsing history stored
- ✅ No analytics or tracking
- ✅ Open source detection logic

## 📄 License

[Add your license here]

## 🤝 Contributing

This is a port of the iOS version which is live on the App Store. We're maintaining feature parity while adapting to Android's VPN-based architecture.
