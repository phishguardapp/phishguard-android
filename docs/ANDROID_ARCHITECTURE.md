# PhishGuard for Android - Technical Architecture

## Overview

PhishGuard for Android is a VPN-based security service that monitors network traffic in real-time to detect and warn users about phishing and scam websites. Unlike browser extensions, this approach protects users across ALL apps including WhatsApp, Telegram, SMS, email, and any browser.

## Target Platform

- **Minimum SDK:** Android 13 (API 33) - ~55% market coverage
- **Target SDK:** Android 14 (API 34)
- **Language:** Kotlin
- **Architecture:** MVVM + Clean Architecture
- **Enhanced Features:** Gemini Nano on Android 14+ (optional)

## Core Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     PhishGuard Android                       │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │   UI Layer   │  │ Notification │  │   Settings   │      │
│  │  (Compose)   │  │   Manager    │  │   Manager    │      │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘      │
│         │                  │                  │               │
│  ┌──────┴──────────────────┴──────────────────┴───────┐     │
│  │              Application Layer                      │     │
│  │  - ViewModels                                       │     │
│  │  - Use Cases                                        │     │
│  │  - Repositories                                     │     │
│  └──────────────────────┬──────────────────────────────┘     │
│                         │                                     │
│  ┌──────────────────────┴──────────────────────────────┐     │
│  │              VPN Service Layer                      │     │
│  │  - Network Monitor                                  │     │
│  │  - Packet Inspector                                 │     │
│  │  - Traffic Router                                   │     │
│  └──────────────────────┬──────────────────────────────┘     │
│                         │                                     │
│  ┌──────────────────────┴──────────────────────────────┐     │
│  │           Detection Engine Layer                    │     │
│  │  ┌────────────────┐  ┌────────────────┐            │     │
│  │  │  TensorFlow    │  │  Gemini Nano   │            │     │
│  │  │     Lite       │  │   (Optional)   │            │     │
│  │  └────────┬───────┘  └────────┬───────┘            │     │
│  │           │                    │                     │     │
│  │  ┌────────┴────────────────────┴───────┐            │     │
│  │  │      Hybrid Classifier              │            │     │
│  │  └────────┬────────────────────────────┘            │     │
│  │           │                                          │     │
│  │  ┌────────┴────────────────────────────┐            │     │
│  │  │      Feature Extractors             │            │     │
│  │  │  - SSL Checker                      │            │     │
│  │  │  - WHOIS/RDAP Analyzer              │            │     │
│  │  │  - Domain Reputation                │            │     │
│  │  │  - Content Analyzer (Jsoup)         │            │     │
│  │  │  - Tranco Ranking                   │            │     │
│  │  └─────────────────────────────────────┘            │     │
│  └─────────────────────────────────────────────────────┘     │
│                                                               │
│  ┌─────────────────────────────────────────────────────┐     │
│  │              Data Layer                             │     │
│  │  - Room Database (threat history, cache)           │     │
│  │  - SharedPreferences (settings)                    │     │
│  │  - Asset Files (ML models, databases)              │     │
│  └─────────────────────────────────────────────────────┘     │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

## Component Details

### 1. VPN Service Layer

**Purpose:** Intercepts all network traffic system-wide

**Key Components:**

#### NetworkMonitor
```kotlin
class PhishGuardVpnService : VpnService() {
    // Establishes local VPN tunnel
    // Routes all traffic through PhishGuard
    // Monitors packets in real-time
}
```

**Responsibilities:**
- Establish VPN tunnel using Android VpnService API
- Capture all outgoing network packets
- Extract URLs/domains from packets
- Forward packets to detection engine
- Block or allow based on analysis results

**Technical Details:**
- Uses `VpnService.Builder()` to create tunnel
- Non-blocking I/O for performance
- Packet parsing for HTTP/HTTPS/DNS
- TLS SNI extraction for HTTPS domains

### 2. Detection Engine Layer

**Purpose:** Analyzes URLs and determines threat level

#### Hybrid Classifier Architecture

```
URL Request
    ↓
┌───────────────────────────────────┐
│   Quick Analysis (TFLite)         │
│   - URL structure                 │
│   - Domain features               │
│   - SSL info                      │
│   - Reputation check              │
│   Time: 20-50ms                   │
└───────────┬───────────────────────┘
            │
    ┌───────┴────────┐
    │ Confidence?    │
    └───────┬────────┘
            │
    ┌───────┴────────────────────────┐
    │                                │
High (>0.85)                   Low (<0.85)
    │                                │
    ↓                                ↓
Return Result              ┌─────────────────────┐
                          │ Deep Analysis        │
                          │ (Gemini Nano)        │
                          │ - Content analysis   │
                          │ - Semantic check     │
                          │ Time: 200-500ms      │
                          └──────────┬───────────┘
                                     │
                                     ↓
                              Combined Result
```

#### Feature Extractors

**SSLChecker.kt**
```kotlin
class SSLChecker {
    fun analyze(domain: String): SSLInfo {
        // Certificate validation
        // Issuer reputation
        // Expiry date check
        // Self-signed detection
    }
}
```

**WhoisAnalyzer.kt**
```kotlin
class WhoisAnalyzer {
    suspend fun lookup(domain: String): DomainInfo {
        // RDAP API queries
        // Domain age calculation
        // Registrar information
        // Registration patterns
    }
}
```

**TrancoProvider.kt**
```kotlin
class TrancoProvider(context: Context) {
    fun getRank(domain: String): Int? {
        // SQLite database lookup
        // Top 1M sites ranking
        // Legitimate site detection
    }
}
```

**ContentAnalyzer.kt**
```kotlin
class ContentAnalyzer {
    fun analyze(html: String): ContentFeatures {
        // Jsoup HTML parsing
        // Form detection
        // Password input detection
        // Brand keyword extraction
        // Suspicious patterns
    }
}
```

### 3. ML Models

#### TensorFlow Lite Model

**Input Features (50+ dimensions):**
- URL structure (length, special chars, subdomains)
- Domain age and registration info
- SSL certificate details
- Hosting provider patterns
- Keyword presence (login, verify, secure, etc.)
- Tranco ranking
- DNS information
- Homoglyph detection

**Output:**
- Probability distribution: [legit, suspicious, phishing]
- Confidence score

**Model Size:** ~5-10MB
**Inference Time:** 20-50ms

#### Gemini Nano Integration (Android 14+ Only - Optional Enhancement)

**Purpose:** Deep semantic analysis for uncertain cases on newer devices

**Availability:** Only on Android 14+ with AICore support

```kotlin
class GeminiNanoClassifier(context: Context) {
    private val aiCore = if (Build.VERSION.SDK_INT >= 34) {
        AICore.getInstance(context)
    } else {
        null
    }
    
    suspend fun analyzeUrl(url: String, features: Features): Result {
        val prompt = """
            Analyze this URL for phishing indicators:
            
            URL: $url
            Domain Age: ${features.domainAge} days
            SSL Issuer: ${features.sslIssuer}
            Hosting: ${features.hostingProvider}
            
            Suspicious patterns detected:
            ${features.suspiciousPatterns.joinToString("\n- ", "- ")}
            
            Is this legitimate, suspicious, or phishing?
            Provide confidence score and reasoning.
        """.trimIndent()
        
        return aiCore.generateText(prompt).parse()
    }
}
```

### 4. Notification System

**Three-Tier Notification Strategy:**

#### Tier 1: Safe Sites
- No notification
- Silent allow

#### Tier 2: Suspicious Sites
```
┌─────────────────────────────────┐
│ ⚠️ PhishGuard Warning           │
│ Suspicious Site Detected        │
│                                 │
│ example-suspicious.com          │
│ Risk: Possible scam             │
│                                 │
│ [View Details] [Block Site]    │
└─────────────────────────────────┘
```

#### Tier 3: Dangerous Sites
```
┌─────────────────────────────────┐
│ 🛑 PHISHING ALERT               │
│ High Risk Site Detected         │
│                                 │
│ fake-bank-login.xyz             │
│                                 │
│ This site is impersonating      │
│ Chase Bank. Do not enter        │
│ credentials.                    │
│                                 │
│ Confidence: 94%                 │
│                                 │
│ [Close Page] [Details] [Ignore] │
└─────────────────────────────────┘
```

### 5. User Settings & Controls

**Protection Modes:**

```kotlin
enum class ProtectionMode {
    WARN_ONLY,          // Default: Never block, only warn
    BLOCK_DANGEROUS,    // Block high-confidence threats
    OFF                 // Monitoring disabled
}
```

**User Controls:**
- Protection mode selection
- Allowlist management (trusted domains)
- Notification preferences
- Deep analysis toggle (Gemini Nano)
- Statistics and history

### 6. Data Storage

#### Room Database Schema

```kotlin
@Entity(tableName = "threats")
data class ThreatRecord(
    @PrimaryKey val id: String,
    val url: String,
    val domain: String,
    val verdict: String,
    val confidence: Float,
    val timestamp: Long,
    val wasBlocked: Boolean,
    val sourceApp: String?,
    val reasons: String  // JSON array
)

@Entity(tableName = "allowlist")
data class AllowlistEntry(
    @PrimaryKey val domain: String,
    val addedAt: Long,
    val addedBy: String  // "user" or "auto"
)

@Entity(tableName = "cache")
data class AnalysisCache(
    @PrimaryKey val domain: String,
    val result: String,  // JSON
    val timestamp: Long,
    val ttl: Long = 600000  // 10 minutes
)
```

## Performance Considerations

### Latency Targets

- **URL Analysis:** < 50ms (TFLite)
- **Deep Analysis:** < 500ms (Gemini Nano)
- **Total Overhead:** < 100ms for 90% of requests

### Battery Optimization

- Efficient packet processing
- Batch database operations
- Cache frequently accessed domains
- Suspend deep analysis when battery low

### Memory Management

- LRU cache for analysis results
- Lazy loading of ML models
- Periodic cleanup of old records

## Security & Privacy

### Privacy Guarantees

- ✅ All processing on-device
- ✅ No data sent to remote servers
- ✅ No browsing history collected
- ✅ No analytics or tracking
- ✅ Open source detection logic

### Security Measures

- Certificate pinning for RDAP queries
- Secure storage for sensitive data
- Regular model updates via Play Store
- Tamper detection

## Technology Stack

### Core Technologies

- **Language:** Kotlin 1.9+
- **Min SDK:** Android 14 (API 34)
- **UI:** Jetpack Compose
- **Architecture:** MVVM + Clean Architecture
- **Dependency Injection:** Hilt
- **Async:** Coroutines + Flow

### Key Libraries

```gradle
dependencies {
    // ML
    implementation 'org.tensorflow:tensorflow-lite:2.14.0'
    implementation 'com.google.android.gms:play-services-mlkit-aicore:16.0.0'
    
    // Networking
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    implementation 'org.jsoup:jsoup:1.17.1'
    
    // Database
    implementation 'androidx.room:room-runtime:2.6.1'
    implementation 'androidx.room:room-ktx:2.6.1'
    
    // Domain parsing
    implementation 'com.google.guava:guava:32.1.3-android'
    
    // UI
    implementation 'androidx.compose.ui:ui:1.5.4'
    implementation 'androidx.compose.material3:material3:1.1.2'
    
    // DI
    implementation 'com.google.dagger:hilt-android:2.48'
    
    // Coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
}
```

## Deployment

### Build Variants

- **Debug:** Full logging, test data
- **Release:** Optimized, ProGuard enabled
- **Beta:** Crashlytics, limited logging

### Distribution

- Google Play Store (primary)
- APK direct download (secondary)
- F-Droid (future consideration)

## Testing Strategy

### Unit Tests
- Feature extractors
- ML model inference
- Classification logic
- Database operations

### Integration Tests
- VPN service lifecycle
- Notification delivery
- Settings persistence

### UI Tests
- User flows (Compose testing)
- Settings screens
- Threat history

### Performance Tests
- Latency benchmarks
- Battery consumption
- Memory usage

## Future Enhancements

### Phase 2 Features
- Real-time threat intelligence updates
- Community-reported threats
- Browser extension for desktop sync
- Family protection (multiple devices)

### Phase 3 Features
- VPN server option (optional remote protection)
- Advanced analytics dashboard
- Custom detection rules
- Enterprise features

## Comparison with iOS Version

| Feature | iOS (Safari Extension) | Android (VPN Service) |
|---------|----------------------|----------------------|
| **Architecture** | App Extension + Native Host | VPN Service |
| **Scope** | Safari browser only | All apps system-wide |
| **Content Access** | Full page content | URL/domain only* |
| **ML Framework** | Core ML | TensorFlow Lite + Gemini Nano |
| **Detection Logic** | ✅ Same algorithms | ✅ Same algorithms |
| **Privacy** | ✅ On-device | ✅ On-device |
| **User Control** | Extension popup | App + notifications |

*Can fetch content for suspicious URLs if needed

## Development Timeline

### Phase 1: Core Infrastructure (2-3 weeks)
- VPN service implementation
- Basic packet inspection
- TensorFlow Lite integration
- Simple UI

### Phase 2: Detection Engine (2-3 weeks)
- Port all feature extractors
- SSL checker
- WHOIS/RDAP integration
- Tranco database
- Content analyzer

### Phase 3: ML & Intelligence (1-2 weeks)
- Train/port TensorFlow Lite model
- Gemini Nano integration
- Hybrid classifier logic

### Phase 4: UI & Polish (1-2 weeks)
- Jetpack Compose UI
- Notification system
- Settings screens
- Threat history

### Phase 5: Testing & Release (1-2 weeks)
- Comprehensive testing
- Performance optimization
- Play Store submission

**Total Estimated Time:** 7-12 weeks

---

## Next Steps

1. Review this architecture document
2. Set up Android project structure
3. Implement VPN service foundation
4. Port feature extractors from iOS
5. Integrate ML models
6. Build UI layer
7. Test and optimize
8. Submit to Play Store

