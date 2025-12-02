# PhishGuard: iOS to Android Feature Parity Mapping

## Overview

This document maps each iOS/Swift component to its Android/Kotlin equivalent, ensuring feature parity between platforms while adapting to platform-specific capabilities.

## Core Detection Components

### 1. SSL Certificate Checking

**iOS: SSLChecker.swift**
```swift
class SSLChecker {
    func checkSSL(for url: URL) -> SSLInfo {
        // Uses URLSession and SecTrust
        // Certificate chain validation
        // Issuer reputation check
    }
}
```

**Android: SSLChecker.kt**
```kotlin
class SSLChecker {
    fun checkSSL(domain: String): SSLInfo {
        // Uses javax.net.ssl.SSLSocket
        // Certificate chain validation
        // Issuer reputation check
    }
}
```

**Key Differences:**
- iOS uses `SecTrust` framework
- Android uses `javax.net.ssl` and `java.security.cert`
- Same logic, different APIs

**Libraries:**
- Android: Built-in Java SSL/TLS APIs
- Optional: OkHttp for easier SSL inspection

---

### 2. WHOIS/RDAP Domain Analysis

**iOS: Uses SwiftWhois library**
```swift
import SwiftWhois

class DomainAnalyzer {
    func analyzeDomain(_ domain: String) async -> DomainInfo {
        let whois = try await Whois.lookup(domain)
        return parseDomainInfo(whois)
    }
}
```

**Android: WhoisAnalyzer.kt**
```kotlin
class WhoisAnalyzer {
    suspend fun analyzeDomain(domain: String): DomainInfo {
        // Use RDAP API (modern alternative to WHOIS)
        val response = httpClient.get("https://rdap.org/domain/$domain")
        return parseRDAPResponse(response)
    }
}
```

**Key Differences:**
- iOS: SwiftWhois library (WHOIS protocol)
- Android: RDAP REST API (more reliable)
- Both extract: domain age, registrar, registration date

**Libraries:**
- Android: OkHttp for HTTP requests
- RDAP endpoints: https://rdap.org/

---

### 3. Public Suffix List (PSL)

**iOS: PSL.swift (custom implementation)**
```swift
class PSL {
    func getRegistrableDomain(_ host: String) -> String {
        // Parse PSL rules
        // Extract eTLD+1
    }
}
```

**Android: PublicSuffixList.kt**
```kotlin
class PublicSuffixList {
    fun getRegistrableDomain(host: String): String {
        // Use Guava's InternetDomainName
        return InternetDomainName.from(host)
            .topPrivateDomain()
            .toString()
    }
}
```

**Key Differences:**
- iOS: Custom PSL implementation
- Android: Use Guava library (battle-tested)
- Same output, easier maintenance on Android

**Libraries:**
- Android: `com.google.guava:guava:32.1.3-android`

---

### 4. Tranco Top Sites Ranking

**iOS: RankProvider.swift**
```swift
class RankProvider {
    private let database: SQLite.Connection
    
    func getRank(for domain: String) -> Int? {
        // SQLite query
        return try? database.scalar(
            "SELECT rank FROM tranco WHERE domain = ?", domain
        )
    }
}
```

**Android: TrancoProvider.kt**
```kotlin
class TrancoProvider(context: Context) {
    private val db: SQLiteDatabase
    
    fun getRank(domain: String): Int? {
        return db.query(
            "tranco",
            arrayOf("rank"),
            "domain = ?",
            arrayOf(domain),
            null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else null
        }
    }
}
```

**Key Differences:**
- iOS: SQLite.swift library
- Android: Built-in SQLite APIs or Room
- Same database file can be reused!

**Libraries:**
- Android: Room (recommended) or raw SQLite

---

### 5. HTML Content Parsing

**iOS: Uses SwiftSoup**
```swift
import SwiftSoup

class ContentAnalyzer {
    func analyze(html: String) -> ContentFeatures {
        let doc = try SwiftSoup.parse(html)
        let forms = try doc.select("form")
        let passwordInputs = try doc.select("input[type=password]")
        // ...
    }
}
```

**Android: Uses Jsoup (nearly identical API!)**
```kotlin
import org.jsoup.Jsoup

class ContentAnalyzer {
    fun analyze(html: String): ContentFeatures {
        val doc = Jsoup.parse(html)
        val forms = doc.select("form")
        val passwordInputs = doc.select("input[type=password]")
        // ...
    }
}
```

**Key Differences:**
- **Almost none!** SwiftSoup is a port of Jsoup
- API is 95% identical
- Easy to port code line-by-line

**Libraries:**
- Android: `org.jsoup:jsoup:1.17.1`

---

### 6. Website Classification Logic

**iOS: WebsiteClassifier.swift**
```swift
class WebsiteClassifier {
    func classify(url: URL, content: String?) async -> ClassificationResult {
        // Extract features
        let features = extractFeatures(url, content)
        
        // Run ML model
        let prediction = mlModel.prediction(from: features)
        
        // Return result
        return ClassificationResult(
            verdict: prediction.label,
            confidence: prediction.confidence
        )
    }
    
    private func extractFeatures(_ url: URL, _ content: String?) -> Features {
        // URL structure analysis
        // Domain age check
        // SSL validation
        // Content patterns
        // Keyword detection
    }
}
```

**Android: WebsiteClassifier.kt**
```kotlin
class WebsiteClassifier(context: Context) {
    private val tflite = Interpreter(loadModel(context))
    
    suspend fun classify(url: String, content: String? = null): ClassificationResult {
        // Extract features (same logic as iOS)
        val features = extractFeatures(url, content)
        
        // Run ML model
        val output = Array(1) { FloatArray(3) }
        tflite.run(features, output)
        
        // Return result
        return ClassificationResult(
            verdict = parseVerdict(output[0]),
            confidence = output[0].max()
        )
    }
    
    private suspend fun extractFeatures(url: String, content: String?): FloatArray {
        // Same feature extraction logic as iOS
        // URL structure analysis
        // Domain age check
        // SSL validation
        // Content patterns
        // Keyword detection
    }
}
```

**Key Differences:**
- iOS: Core ML framework
- Android: TensorFlow Lite
- **Same model** (convert Core ML → TFLite)
- **Same features** (identical extraction logic)

---

## ML Model Conversion

### iOS Core ML Model → Android TensorFlow Lite

**Conversion Process:**

```bash
# 1. Export Core ML model to ONNX
python -m coremltools.converters.onnx convert \
    --model PhishGuardModel.mlmodel \
    --output phishguard.onnx

# 2. Convert ONNX to TensorFlow
python -m tf2onnx.convert \
    --onnx phishguard.onnx \
    --output phishguard_tf

# 3. Convert TensorFlow to TFLite
python convert_to_tflite.py \
    --input phishguard_tf \
    --output phishguard.tflite
```

**Or train a new TFLite model with same architecture and data**

---

## Platform-Specific Differences

### Architecture Differences

| Aspect | iOS (Safari Extension) | Android (VPN Service) |
|--------|----------------------|----------------------|
| **Entry Point** | Safari extension activated | VPN service always running |
| **Content Access** | Full page HTML/JS via extension | URL/domain only (HTTPS encrypted) |
| **User Interaction** | Extension popup | Notifications + app |
| **Blocking** | Can inject warnings into page | Block at network level |
| **Scope** | Safari browser only | All apps system-wide |

### Content Extraction Strategy

**iOS:**
```swift
// Extension has direct access to page content
webView.evaluateJavaScript("""
    const article = new Readability(document).parse();
    return {
        title: article.title,
        content: article.textContent
    };
""") { result, error in
    // Analyze content
}
```

**Android:**
```kotlin
// VPN service cannot access page content directly
// Two options:

// Option 1: URL-only analysis (90% accuracy)
val result = classifier.analyzeUrl(url)

// Option 2: Fetch content for suspicious URLs
if (result.verdict == "suspicious") {
    val html = httpClient.get(url).body()
    val deepResult = classifier.analyzeContent(url, html)
}
```

---

## Feature Parity Checklist

### Core Detection Features

- [x] **SSL Certificate Validation**
  - iOS: ✅ SecTrust
  - Android: ✅ javax.net.ssl

- [x] **Domain Age Analysis (WHOIS/RDAP)**
  - iOS: ✅ SwiftWhois
  - Android: ✅ RDAP API

- [x] **Public Suffix List**
  - iOS: ✅ Custom PSL
  - Android: ✅ Guava InternetDomainName

- [x] **Tranco Ranking**
  - iOS: ✅ SQLite database
  - Android: ✅ SQLite/Room database

- [x] **HTML Content Parsing**
  - iOS: ✅ SwiftSoup
  - Android: ✅ Jsoup

- [x] **ML Classification**
  - iOS: ✅ Core ML
  - Android: ✅ TensorFlow Lite + Gemini Nano

- [x] **Multilingual Detection**
  - iOS: ✅ 12 languages
  - Android: ✅ Same 12 languages

### Detection Signals

| Signal | iOS | Android | Notes |
|--------|-----|---------|-------|
| URL structure | ✅ | ✅ | Identical logic |
| Domain age | ✅ | ✅ | WHOIS vs RDAP |
| SSL certificate | ✅ | ✅ | Different APIs, same checks |
| Tranco rank | ✅ | ✅ | Same database |
| Hosting provider | ✅ | ✅ | IP/ASN lookup |
| Free hosting detection | ✅ | ✅ | Pattern matching |
| Homoglyph detection | ✅ | ✅ | Unicode analysis |
| Keyword detection | ✅ | ✅ | Multilingual |
| Form analysis | ✅ | ⚠️ | iOS: direct access, Android: fetch if needed |
| Password input detection | ✅ | ⚠️ | iOS: direct access, Android: fetch if needed |
| Brand impersonation | ✅ | ✅ | Keyword + visual similarity |

### User Features

| Feature | iOS | Android | Notes |
|---------|-----|---------|-------|
| Real-time scanning | ✅ | ✅ | |
| Manual scan | ✅ | ✅ | |
| Threat warnings | ✅ | ✅ | iOS: banner, Android: notification |
| Allowlist | ✅ | ✅ | |
| Protection modes | ✅ | ✅ | Warn/Block/Off |
| Threat history | ✅ | ✅ | |
| Statistics | ✅ | ✅ | |
| Settings | ✅ | ✅ | |

### Privacy Features

| Feature | iOS | Android |
|---------|-----|---------|
| On-device processing | ✅ | ✅ |
| No data collection | ✅ | ✅ |
| No analytics | ✅ | ✅ |
| No tracking | ✅ | ✅ |
| Open source logic | ✅ | ✅ |

---

## Shared Resources

### Databases

**Tranco Top Sites Database**
- Format: SQLite
- Size: ~50MB
- Can be shared between iOS and Android
- Update frequency: Monthly

**Financial Institutions Whitelist**
- Format: JSON or SQLite
- Shared between platforms
- Regular updates

### ML Models

**Feature Extraction Logic**
- Can be identical between platforms
- Port Swift → Kotlin line-by-line

**Model Architecture**
- Train once, convert to both formats
- Core ML (iOS) + TFLite (Android)

### Detection Rules

**Keyword Lists**
- Phishing keywords (12 languages)
- Brand names
- Suspicious patterns
- JSON format, shared

**Hosting Provider Patterns**
- Free hosting domains
- Suspicious TLDs
- Shared configuration

---

## Android-Specific Enhancements

### Advantages Over iOS Version

1. **System-Wide Protection**
   - Protects WhatsApp, Telegram, SMS, email
   - Not limited to browser

2. **Gemini Nano Integration**
   - Advanced on-device AI (Android 14+)
   - Semantic content analysis
   - Better accuracy for uncertain cases

3. **Background Protection**
   - Always-on monitoring
   - No need to open browser

4. **Richer Notifications**
   - Actionable notifications
   - Quick actions (block, allow, details)

### Challenges vs iOS Version

1. **No Direct Content Access**
   - Must fetch content separately for suspicious URLs
   - Slightly higher latency for deep analysis

2. **Battery Considerations**
   - VPN service runs continuously
   - Need aggressive optimization

3. **User Education**
   - VPN permission can be confusing
   - Need clear explanation of "local VPN"

---

## Implementation Priority

### Phase 1: Core Parity (Must Have)
1. ✅ SSL checking
2. ✅ Domain age analysis
3. ✅ Tranco ranking
4. ✅ URL structure analysis
5. ✅ Basic ML classification

### Phase 2: Enhanced Detection (Should Have)
1. ✅ Content analysis (Jsoup)
2. ✅ Hosting provider detection
3. ✅ Homoglyph detection
4. ✅ Multilingual keywords

### Phase 3: Advanced Features (Nice to Have)
1. ✅ Gemini Nano integration
2. ✅ Advanced statistics
3. ✅ Threat intelligence updates

---

## Testing Parity

### Test Cases to Port from iOS

1. **SSL Certificate Tests**
   - Valid certificates
   - Self-signed certificates
   - Expired certificates
   - Wrong domain certificates

2. **Domain Analysis Tests**
   - New domains (<30 days)
   - Old domains (>1 year)
   - Privacy-protected WHOIS
   - Invalid domains

3. **Classification Tests**
   - Known phishing sites
   - Legitimate sites
   - Edge cases
   - False positive scenarios

4. **Performance Tests**
   - Analysis latency
   - Memory usage
   - Battery consumption

---

## Next Steps

1. Set up Android project with dependencies
2. Implement feature extractors (SSL, WHOIS, etc.)
3. Port ML model to TensorFlow Lite
4. Build VPN service infrastructure
5. Create UI with Jetpack Compose
6. Test against iOS version for parity
7. Optimize performance
8. Submit to Play Store

