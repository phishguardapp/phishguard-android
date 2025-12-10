# Detection Diagnostic Guide

## If a phishing site is NOT detected, check these in logcat:

### 1. Is the domain being seen?
Look for:
```
🔍 Domain detected via SOCKS: [domain]
✅ NEW domain - analyzing: [domain]
🔬 Starting analysis for: [domain]
```

**If NOT found**: The domain isn't reaching our analysis
- Check if SNI extraction is working
- Check if reverse DNS is returning wrong domain
- Check if infrastructure filtering is blocking it

### 2. Is it being analyzed?
Look for:
```
🔬 Starting analysis for: subbwollet.gitbook.io
```

**If found**: Continue to step 3
**If NOT found**: Domain is being filtered before analysis

### 3. What's the analysis result?
Look for:
```
📊 Analysis result: subbwollet.gitbook.io = [VERDICT] ([SCORE]%)
```

**Possible verdicts**:
- `SAFE` - Not detected (score too low)
- `SUSPICIOUS` - Should show notification
- `DANGEROUS` - Should show notification

### 4. What's the score breakdown?
Look for reason lines:
```
- Impersonating crypto/wallet brand: wallet
- Brand name on free hosting platform (gitbook.io)
- Legitimate brands use their own domains
```

### 5. Is notification sent?
Look for:
```
📢 showThreatNotification called for: [domain]
✅ THREAT NOTIFICATION SENT: [title]
```

## For `subbwollet.gitbook.io`

### Expected Detection Path:

1. **SNI Extraction**:
```
🔍 SNI extracted: subbwollet.gitbook.io
```

2. **Analysis Start**:
```
🔬 Starting analysis for: subbwollet.gitbook.io
```

3. **Pattern Matching**:
```
✅ Contains crypto keyword: wallet
✅ On free hosting: gitbook.io
✅ Score: 0.6+ (brand + free hosting)
```

4. **Verdict**:
```
📊 Analysis result: subbwollet.gitbook.io = SUSPICIOUS (75%)
Reasons:
- Impersonating crypto/wallet brand: wallet
- Brand name on free hosting platform (gitbook.io)
```

5. **Notification**:
```
📢 showThreatNotification called for: subbwollet.gitbook.io
✅ THREAT NOTIFICATION SENT
```

## Common Issues

### Issue 1: Domain not seen
**Symptom**: No "Domain detected via SOCKS" log
**Cause**: SNI extraction failed or domain resolved before SOCKS
**Fix**: Check SNI extraction logs, verify TLS handshake

### Issue 2: Wrong domain analyzed
**Symptom**: Analyzing IP or reverse DNS domain instead
**Example**: Analyzing `151.101.65.91` instead of `subbwollet.gitbook.io`
**Fix**: SNI extraction should override reverse DNS

### Issue 3: Score too low
**Symptom**: Verdict is SAFE despite suspicious patterns
**Cause**: Keywords not matching or threshold too high
**Fix**: Add more keywords, adjust thresholds

### Issue 4: Filtered as infrastructure
**Symptom**: "SKIP - infrastructure domain" log
**Cause**: Domain matches infrastructure patterns
**Fix**: Review infrastructure filtering rules

### Issue 5: Notification not shown
**Symptom**: Analysis shows SUSPICIOUS but no notification
**Cause**: Cooldown period or notification filtering
**Fix**: Check notification cooldown logic

## Testing Commands

### Check if domain is in logs:
```bash
adb logcat | grep "subbwollet"
```

### Check analysis results:
```bash
adb logcat | grep "Analysis result"
```

### Check notifications:
```bash
adb logcat | grep "THREAT NOTIFICATION"
```

### Full diagnostic:
```bash
adb logcat | grep -E "(Domain detected|Starting analysis|Analysis result|THREAT NOTIFICATION)"
```

## Enhanced Keywords (Latest Build)

### Crypto Keywords:
- metamask, coinbase, binance, kraken, gemini
- blockchain, crypto, wallet, ledger, trezor, exodus
- trustwallet, phantom, uniswap, opensea, rarible
- **subwallet** (NEW), polkadot, kusama, eigenlayer, ethereum
- defi, nft, web3, dapp, token, swap

### Free Hosting Platforms:
- vercel.app, netlify.app, github.io, gitlab.io
- **gitbook.io** (NEW), gitbook.com, readthedocs.io
- pages.dev, workers.dev, web.app, firebaseapp.com
- And 40+ more...

## Expected Behavior

### Phishing Sites (Should be detected):
- ✅ `wallet-eigenlayerr.gitbook.io` - crypto + gitbook
- ✅ `subbwollet.gitbook.io` - wallet + gitbook
- ✅ `ledger-us-live-login.vercel.app` - ledger + vercel
- ✅ `coinbase-com-sing.framer.ai` - coinbase + framer

### Legitimate Sites (Should NOT be flagged):
- ✅ `docs.company.com` on GitBook - no brand keywords
- ✅ `google.com` - in legitimate domains list
- ✅ `github.com` - in legitimate domains list
- ✅ CDN IPs (Fastly, Cloudflare) - infrastructure filtering

## Troubleshooting Steps

1. **Clear app data** and restart VPN
2. **Check logcat** for the specific domain
3. **Verify SNI extraction** is working
4. **Check score calculation** in logs
5. **Verify notification** is sent
6. **Check cooldown** hasn't blocked notification

## Build Info
- Latest build includes enhanced crypto keywords
- GitBook added to suspicious hosting platforms
- CDN infrastructure filtering improved
- SNI extraction active
