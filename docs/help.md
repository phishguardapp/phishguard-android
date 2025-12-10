# PhishGuard Android - Help & FAQ

## What is PhishGuard?

PhishGuard is an Android VPN app that protects you from phishing websites, scams, and fraudulent content across all your apps. It analyzes network traffic in real-time using multiple detection methods including domain age checking, SSL certificate validation, and suspicious pattern detection.

---

## How It Works

### What does PhishGuard check?

PhishGuard analyzes multiple signals from your network traffic:

- **Domain Age**: Very new domains (< 7 days) are often used for phishing
- **SSL Certificate**: Checks if the certificate is valid and from a trusted authority
- **HTTP vs HTTPS**: Warns when sensitive sites use unencrypted connections
- **Suspicious Keywords**: Looks for banking, crypto, and financial terms on suspicious domains
- **Brand Impersonation**: Detects fake sites pretending to be banks or companies
- **Free Hosting**: Identifies sites hosted on free platforms often used for phishing
- **Infrastructure Domains**: Detects redirects through hosting provider domains

### What do the different notifications mean?

- **No Notification**: Site is safe, no suspicious indicators detected
- **⚠️ Suspicious**: Yellow notification - some concerning signals, proceed with caution
- **🚨 Dangerous**: Red notification - multiple red flags detected, likely fraudulent
- **🛡️ Protected**: VPN is active and monitoring your traffic system-wide

---

## Common Questions

### Why is a legitimate site flagged as suspicious?

PhishGuard may occasionally flag legitimate sites (false positives) for several reasons:

- The site is very new (< 30 days old)
- The site uses an uncommon SSL certificate provider
- The site contains keywords that are commonly used in scams
- The site is hosted on a free platform (Weebly, Netlify, etc.)

**What to do**: If you trust the site, you can report the false positive through the app's support feature.

### Why didn't PhishGuard detect a phishing site?

No phishing detector is 100% accurate. PhishGuard may miss some phishing sites if:

- The site is very sophisticated and mimics legitimate sites well
- The site is brand new and not yet in any databases
- The site uses advanced evasion techniques

**Always practice safe browsing**: Verify URLs carefully, don't click suspicious links, and never share passwords or financial information unless you're certain the site is legitimate.

### How do I manage PhishGuard settings?

1. Open the PhishGuard app
2. Tap the settings icon in the top-right corner
3. Adjust cache duration (1-24 hours)
4. View statistics (sites analyzed, threats blocked)
5. Clear cache or contact support if needed

### Does PhishGuard collect my browsing data?

PhishGuard analyzes network traffic locally on your device using a VPN. We do not:

- Store your browsing history
- Track which sites you visit
- Share your data with third parties
- Route your traffic through our servers

The app only makes external requests to:

- WHOIS servers (to check domain age)
- RDAP servers (for domain information)

These requests contain only domain names, not your full browsing activity or personal information.

### Why does analysis take time sometimes?

Classification can take a few seconds because PhishGuard:

- Queries WHOIS servers to check domain age (can take 5-10 seconds)
- Validates SSL certificates
- Analyzes network patterns
- Checks multiple threat databases

**Performance tip**: Results are cached for 1-24 hours (configurable), so revisiting the same domain is instant.

---

## Tips for Staying Safe Online

### 🔍 Always verify URLs
Check that the URL matches the legitimate site. Phishers often use similar-looking domains like "paypa1.com" instead of "paypal.com".

### 🔒 Look for HTTPS
Legitimate sites use HTTPS (padlock icon). Never enter passwords or payment info on HTTP sites.

### 📧 Be skeptical of urgent emails
Phishing emails often create urgency ("Your account will be closed!"). Legitimate companies don't pressure you like this.

### 🎣 Don't click suspicious links
Hover over links to see where they really go. If it looks suspicious, don't click it.

### 💳 Use unique passwords
Use a password manager and unique passwords for each site. If one site is compromised, others stay safe.

---

## Troubleshooting

### The VPN isn't working

1. Check that VPN permission was granted when you first started the app
2. Make sure no other VPN apps are running (only one VPN can be active)
3. Try toggling VPN protection off and on again
4. Restart the PhishGuard app
5. If issues persist, try reinstalling the app

### No notifications appearing

This can happen if:

- Notification permissions aren't granted - check Android settings
- The site is actually safe (no notification means it's secure)
- Analysis is still in progress - wait a few seconds
- Your internet connection is slow or blocked

---

## Need More Help?

If you have questions, found a bug, or want to report a false positive:

- **Email**: phishhguard.app@gmail.com


We appreciate your feedback and use it to improve PhishGuard!

---

**PhishGuard** - Protecting you from online threats  
© 2025 PhishGuard. All rights reserved.
