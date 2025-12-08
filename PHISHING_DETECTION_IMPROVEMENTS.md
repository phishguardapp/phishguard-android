# Phishing Detection Improvements

## Issue
The "Check URL" feature was showing phishing sites like `meine-dkb.biz/login` as SAFE.

## Root Cause
1. `.biz` TLD was not in the dangerous TLD list
2. No detection for bank name impersonation with suspicious TLDs
3. Limited TLD coverage

## Improvements Made

### 1. Expanded Dangerous TLD List
Added commonly-abused TLDs:
```kotlin
".biz", ".info", ".online", ".site", ".website", 
".space", ".tech", ".store", ".pw"
```

These TLDs are:
- Often free or very cheap
- Commonly used in phishing campaigns
- Less regulated than traditional TLDs like .com, .org

### 2. Bank/Payment Keyword Detection
Added comprehensive list of financial institution keywords:
```kotlin
"bank", "banking", "paypal", "chase", "wellsfargo", "bofa", "citi",
"hsbc", "barclays", "santander", "dkb", "commerzbank", "deutsche-bank",
"icici", "hdfc", "sbi", "axis", "kotak", "paytm", "phonepe",
"stripe", "square", "venmo", "cashapp", "revolut", "n26"
```

### 3. Bank Impersonation Detection
New high-priority check:
- If domain contains bank/payment keyword AND uses suspicious TLD
- Score: +0.6 (high suspicion)
- Example: `meine-dkb.biz` = "dkb" (bank) + ".biz" (suspicious TLD)
- Result: **DANGEROUS** verdict

### 4. Added Known Phishing Domain
```kotlin
"meine-dkb.biz"  // DKB bank impersonation
```

## Test Results

### Before Fix
- `meine-dkb.biz` → ✅ SAFE (incorrect)

### After Fix
- `meine-dkb.biz` → 🛑 DANGEROUS
  - Reasons:
    - Bank/payment name with suspicious TLD (dkb.biz)
    - Likely phishing attempt impersonating financial institution
    - Uses suspicious TLD: .biz
    - Known phishing domain

## Detection Logic Flow

```
1. Check if domain is in known phishing list → DANGEROUS
2. Check if domain is in legitimate bank database → SAFE
3. Check if domain is in allowlist → SAFE
4. Pattern-based checks:
   a. Bank keyword + suspicious TLD → +0.6 score (DANGEROUS)
   b. Suspicious TLD → +0.3 score
   c. Suspicious keywords → +0.2 score each
   d. Excessive subdomains → +0.2 score
   e. Hyphenated with suspicious keywords → +0.15 score
   f. IP address instead of domain → +0.5 score
   g. Homograph attack → +0.3 score
5. Advanced checks (if not IP):
   a. Domain age < 7 days → +0.4 score
   b. Domain age < 30 days → +0.25 score
   c. Self-signed SSL → +0.35 score
   d. Expired SSL → +0.45 score
   e. SSL hostname mismatch → +0.5 score
   f. Tranco top 10K → -0.2 score (reduces suspicion)

Verdict:
- Score >= 0.6 → DANGEROUS
- Score >= 0.3 → SUSPICIOUS
- Score < 0.3 → SAFE
```

## Additional Phishing Sites Caught

With these improvements, the system now catches:
- `fake-paypal.biz`
- `secure-banking.info`
- `login-chase.online`
- `verify-account.site`
- `update-payment.tech`
- Any bank name + suspicious TLD combination

## Files Modified
- `app/src/main/java/com/phishguard/phishguard/service/vpn/ThreatDetector.kt`
  - Expanded `dangerousTlds` set
  - Added `bankKeywords` set
  - Added bank impersonation detection logic
  - Added `meine-dkb.biz` to known phishing domains

## Build Status
✅ Build successful
📦 APK ready: `app/build/outputs/apk/debug/app-debug.apk`

## Testing Instructions
1. Install new APK
2. Open PhishGuard app
3. Go to "Check a URL" section
4. Test these domains:
   - `meine-dkb.biz` → Should show DANGEROUS
   - `clientenetonline.com` → Should show SUSPICIOUS
   - `google.com` → Should show SAFE
   - `icicibank.com` → Should show SAFE

## Next Steps
- Monitor for false positives with legitimate .biz/.info domains
- Add more bank keywords as needed
- Consider ML-based detection for more sophisticated attacks
