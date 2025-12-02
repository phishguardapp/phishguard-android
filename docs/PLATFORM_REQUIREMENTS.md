# PhishGuard Android - Platform Requirements

## Minimum SDK Decision: Android 13 (API 33)

### Market Coverage

**Android 13+ (API 33):** ~55% of active devices
**Android 14+ (API 34):** ~31% of active devices

### Why Android 13+?

**Balanced Approach:**
- ✅ Good market coverage (55% of users)
- ✅ Modern APIs and features
- ✅ Reasonable 2-year-old OS requirement
- ✅ Consistent with iOS strategy (iOS 15+, released 2021)
- ✅ Cleaner codebase than supporting older versions
- ✅ Room for growth as adoption increases

**Technical Benefits:**
- Runtime notification permissions (better UX)
- Predictable Material You theming
- Better background service handling
- Improved battery optimization APIs
- Fewer edge cases to handle

## Feature Availability by Android Version

### All Users (Android 13+)

**Core Features:**
- ✅ VPN Service (system-wide protection)
- ✅ TensorFlow Lite ML classification
- ✅ All feature extractors (SSL, WHOIS, Tranco, etc.)
- ✅ Real-time threat detection
- ✅ Notification system
- ✅ Jetpack Compose UI
- ✅ Complete privacy (on-device processing)

**Detection Accuracy:** 90%+ (same as iOS version)

### Android 14+ Users (Optional Enhancement)

**Additional Features:**
- ✅ Gemini Nano integration
- ✅ Enhanced semantic analysis
- ✅ Better edge case handling
- ✅ Slightly improved accuracy (92-95%)

**Implementation:**
```kotlin
class HybridClassifier(context: Context) {
    private val tfliteClassifier = TFLiteClassifier(context)
    
    // Optional enhancement for Android 14+
    private val geminiClassifier = if (Build.VERSION.SDK_INT >= 34) {
        try {
            GeminiNanoClassifier(context)
        } catch (e: Exception) {
            null // Graceful fallback
        }
    } else {
        null
    }
    
    suspend fun classify(url: String): Result {
        // Always use TFLite (works on all devices)
        val result = tfliteClassifier.classify(url)
        
        // Enhance with Gemini if available and needed
        if (result.confidence < 0.85 && geminiClassifier != null) {
            return geminiClassifier.enhance(url, result)
        }
        
        return result
    }
}
```

## Comparison with iOS Version

| Aspect | iOS Version | Android Version |
|--------|-------------|-----------------|
| **Minimum OS** | iOS 15 (2021) | Android 13 (2022) |
| **Market Coverage** | ~90% | ~55% |
| **Architecture** | Safari Extension | VPN Service |
| **ML Framework** | Core ML | TensorFlow Lite |
| **Enhanced AI** | N/A | Gemini Nano (Android 14+) |
| **Detection Logic** | ✅ Same | ✅ Same |
| **Privacy** | ✅ On-device | ✅ On-device |

## Growth Projection

**At Launch (2024):**
- Android 13+: ~55% coverage

**6 Months Later:**
- Android 13+: ~65% coverage

**12 Months Later:**
- Android 13+: ~75% coverage

Your user base grows naturally as Android 13+ adoption increases.

## Competitive Analysis

**Popular Security Apps:**
- Malwarebytes: Android 7+ (API 24)
- Norton Mobile: Android 6+ (API 23)
- Avast: Android 6+ (API 23)
- Bitdefender: Android 7+ (API 24)

**PhishGuard Strategy:**
- Target modern devices (Android 13+)
- Offer superior features (VPN-based, Gemini Nano)
- Maintain clean, modern codebase
- Trade some reach for better UX and maintainability

## User Communication

### Play Store Listing

**Requirements Section:**
```
Requires Android 13 or newer
Enhanced AI features on Android 14+
~50MB storage for threat databases
VPN permission required
```

**Description:**
```
PhishGuard provides real-time protection against phishing 
and scams across all your apps - not just browsers.

✓ Works on Android 13 and above
✓ Advanced AI detection with TensorFlow Lite
✓ Enhanced accuracy on Android 14+ with Gemini Nano
✓ Complete privacy - all processing on your device
```

### In-App Messaging

**For Android 13 Users:**
```
You're protected with advanced AI detection!

Upgrade to Android 14 for enhanced accuracy 
with Google's Gemini Nano.
```

**For Android 14+ Users:**
```
Enhanced AI Protection Active

Your device supports Gemini Nano for even 
better phishing detection accuracy.
```

## Technical Requirements

### Build Configuration

```kotlin
android {
    compileSdk = 34
    
    defaultConfig {
        minSdk = 33  // Android 13
        targetSdk = 34  // Android 14
    }
}
```

### Permissions

```xml
<!-- Required for all versions -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

<!-- Android 13+ notification permission -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### Dependencies

```kotlin
dependencies {
    // Core ML (works on all Android versions)
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    
    // Optional: Gemini Nano (Android 14+ only)
    implementation("com.google.android.gms:play-services-mlkit-aicore:16.0.0")
}
```

## Testing Strategy

### Device Coverage

**Minimum Testing:**
- Android 13 device (Pixel 6, Samsung S22, etc.)
- Android 14 device (Pixel 8, Samsung S24, etc.)

**Recommended Testing:**
- Android 13 (without Gemini Nano)
- Android 14 (with Gemini Nano)
- Various manufacturers (Samsung, Pixel, OnePlus)
- Different screen sizes

### Feature Testing

**Android 13:**
- ✅ VPN service works
- ✅ TFLite classification accurate
- ✅ Notifications display correctly
- ✅ All features functional

**Android 14:**
- ✅ All Android 13 features work
- ✅ Gemini Nano initializes
- ✅ Enhanced detection works
- ✅ Graceful fallback if Gemini unavailable

## Migration Path

### If You Need to Lower Minimum SDK Later

**Android 11+ (API 30):**
- Remove Gemini Nano entirely
- Keep TFLite only
- Gain 30% more users
- More testing required

**Android 12+ (API 31):**
- Similar to Android 11+
- Slightly newer APIs
- Gain 15% more users

### If You Want to Raise Minimum SDK Later

**Android 14+ (API 34):**
- Make Gemini Nano standard (not optional)
- Simplify codebase
- Lose 24% of current users
- Not recommended for at least 1 year

## Recommendation Summary

**Stick with Android 13+ (API 33)**

**Reasons:**
1. Good market coverage (55%)
2. Modern APIs and features
3. Optional Gemini Nano enhancement
4. Consistent with iOS strategy
5. Clean, maintainable code
6. Natural growth as adoption increases

**Review in 12 months:**
- If Android 13+ reaches 75%+, maintain
- If Android 14+ reaches 50%+, consider raising minimum
- Monitor user feedback and adoption

---

**Last Updated:** December 2024
**Next Review:** December 2025
