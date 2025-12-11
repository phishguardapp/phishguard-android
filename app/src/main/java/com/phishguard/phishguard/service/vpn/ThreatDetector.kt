package com.phishguard.phishguard.service.vpn

import android.content.Context
import android.util.Log
import com.phishguard.phishguard.service.vpn.threat.BankDatabaseHelper
import com.phishguard.phishguard.service.vpn.threat.DomainResolver
import com.phishguard.phishguard.service.vpn.threat.ThreatAnalysisCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Enhanced threat detection engine
 * Phase 1: Pattern-based heuristics + SQLite database + Domain resolution
 * Phase 2+: Will integrate ML models, WHOIS, SSL validation, and Tranco ranking
 */
class ThreatDetector(private val context: Context) {
    
    companion object {
        private const val TAG = "ThreatDetector"
    }
    
    // Core components
    private val bankDatabase: BankDatabaseHelper by lazy { BankDatabaseHelper(context) }
    private val domainResolver: DomainResolver = DomainResolver()
    private val cache: ThreatAnalysisCache = ThreatAnalysisCache(context)
    
    // Advanced components (lazy initialization)
    private val sslValidator: com.phishguard.phishguard.service.vpn.threat.SSLCertificateValidator by lazy {
        com.phishguard.phishguard.service.vpn.threat.SSLCertificateValidator()
    }
    private val trancoChecker: com.phishguard.phishguard.service.vpn.threat.TrancoRankingChecker by lazy {
        com.phishguard.phishguard.service.vpn.threat.TrancoRankingChecker()
    }
    private val domainAgeChecker: com.phishguard.phishguard.service.vpn.threat.DomainAgeChecker by lazy {
        com.phishguard.phishguard.service.vpn.threat.DomainAgeChecker()
    }
    
    data class ThreatAnalysis(
        val domain: String,
        val verdict: Verdict,
        val confidence: Float,
        val reasons: List<String>,
        val metadata: AnalysisMetadata? = null
    )
    
    data class AnalysisMetadata(
        val domainAge: Int?,
        val trancoRank: Int?,
        val sslValid: Boolean?,
        val inBankDatabase: Boolean,
        val analysisTimeMs: Long
    )
    
    enum class Verdict {
        SAFE,
        SUSPICIOUS,
        DANGEROUS
    }
    
    // Legitimate domains that should always be safe (allowlist)
    private val legitimateDomains = setOf(
        // Major tech companies
        "google.com", "googleapis.com", "gstatic.com", "googleusercontent.com",
        "google-analytics.com", "googlevideo.com", "youtube.com", "ytimg.com",
        "googletagmanager.com", "googleadservices.com", "doubleclick.net",
        "1e100.net", // Google infrastructure (e.g., sd-in-f188.1e100.net)
        "gvt1.com", "gvt2.com", "gvt3.com", // Google infrastructure
        "dns.google", // Google Public DNS
        "facebook.com", "fbcdn.net", "facebook.net",
        "apple.com", "icloud.com", "apple-cloudkit.com", "mzstatic.com",
        "microsoft.com", "live.com", "outlook.com", "office.com", "windows.com",
        "amazon.com", "amazonaws.com", "cloudfront.net",
        "github.com", "githubusercontent.com",
        "twitter.com", "twimg.com", "x.com",
        "linkedin.com", "licdn.com",
        "instagram.com", "cdninstagram.com",
        
        // CDNs and infrastructure
        "cloudflare.com", "cloudflare.net", "fastly.net", "fastly-edge.com",
        "akamai.net", "akamaiedge.net", "edgekey.net",
        "akamaitechnologies.com", "edgesuite.net",
        
        // Common services
        "wikipedia.org", "wikimedia.org",
        "reddit.com", "redd.it", "redditstatic.com",
        "stackoverflow.com", "stackexchange.com",
        "mozilla.org", "firefox.com",
        
        // Indian Banks
        "icicibank.com", "icici.bank.in", "hdfcbank.com", "sbi.co.in",
        "axisbank.com", "kotak.com", "yesbank.in", "pnbindia.in",
        "bankofbaroda.in", "bankofindia.co.in", "canarabank.com",
        "unionbankofindia.co.in", "indianbank.in", "idbi.com",
        
        // International Banks
        "chase.com", "bankofamerica.com", "wellsfargo.com", "citi.com",
        "hsbc.com", "barclays.com", "santander.com", "bnpparibas.com",
        "deutschebank.com", "credit-suisse.com", "ubs.com",
        
        // Payment Services
        "paypal.com", "stripe.com", "square.com", "venmo.com",
        "paytm.com", "phonepe.com", "googlepay.com", "amazonpay.com"
    )
    
    // Simple pattern-based detection for Phase 1
    private val suspiciousPatterns = listOf(
        "login",
        "verify",
        "account",
        "update",
        "confirm",
        "banking",
        "sms",
        "otp",
        "validation",
        "authentication"
    )
    
    private val dangerousTlds = setOf(
        ".tk", ".ml", ".ga", ".cf", ".gq", // Free TLDs often used for phishing
        ".xyz", ".top", ".work", ".click", ".biz", ".info", ".online",
        ".site", ".website", ".space", ".tech", ".store", ".pw",
        ".link", ".live", ".club", ".shop" // .shop often used for phishing
    )
    
    // Website builders and hosting platforms often abused for phishing
    private val suspiciousHostingDomains = setOf(
        // Vercel
        "vercel.app", "vercel.dev", "now.sh",
        // Netlify
        "netlify.app", "netlify.com", "netlifycdn.com",
        // Git hosting pages
        "github.io", "gitlab.io",
        // Cloudflare
        "pages.dev", "workers.dev", "cloudflareapps.com",
        // Firebase/Google
        "web.app", "firebaseapp.com", "sites.google.com", "googlesites.com",
        // Render & Heroku
        "onrender.com", "herokuapp.com", "herokudns.com",
        // AWS Amplify
        "amplifyapp.com",
        // Website builders
        "wixsite.com", "editorx.io", "weebly.com", "weeblysite.com",
        "squarespace.com", "square.site", "webflow.io", "myshopify.com",
        "carrd.co", "strikingly.com", "wordpress.com", "blogspot.com",
        // Notion
        "notion.so", "notion.site",
        // Other builders
        "tilda.ws", "jimdosite.com", "dorik.io", "framer.ai", "framer.website",
        "surge.sh", "neocities.org",
        // Dev platforms
        "denodeploy.app", "repl.co", "replit.dev", "replit.app",
        "fly.dev", "railway.app", "qovery.io", "kinsta.page",
        "codesandbox.io", "stackblitz.io", "glitch.me",
        // No-code platforms
        "bubbleapps.io", "typedream.site", "webnode.page", "site123.me",
        // Documentation platforms (often abused)
        "gitbook.io", "gitbook.com", "readthedocs.io", "readthedocs.org",
        // Simple website builders
        "makeweb.co", "000webhostapp.com", "freewebhostmost.com"
    )
    
    private val knownPhishingDomains = setOf(
        // Add known phishing domains for testing
        "phishing-test.com",
        "fake-bank.com",
        "scam-site.xyz",
        "paywaveebank.com",  // Suspicious banking site
        "meine-dkb.biz"  // DKB bank impersonation
    )
    
    // Common bank names used in phishing (without TLD)
    // Includes international terms
    private val bankKeywords = setOf(
        // English
        "bank", "banking", "paypal", "payu", "chase", "wellsfargo", "bofa", "citi",
        "hsbc", "barclays", "santander", "dkb", "commerzbank", "deutsche-bank",
        // Major US banks
        "usaa", "capitalone", "pnc", "truist", "usbank", "ally", "discover",
        "americanexpress", "amex", "schwab", "fidelity", "vanguard",
        // Indian banks
        "icici", "hdfc", "sbi", "axis", "kotak", "paytm", "phonepe",
        // Payment services
        "stripe", "square", "venmo", "cashapp", "revolut", "n26", "payment", "pay",
        "zelle", "wise", "transferwise",
        // Spanish/Portuguese
        "banco", "bancario", "credito", "creditos", "pago", "pagos", "beneficios",
        "solicitacao", "solicitação", "corporativo", "empresarial", "financeiro",
        // French
        "banque", "bancaire", "crédit", "paiement",
        // German
        "sparkasse", "volksbank", "raiffeisenbank", "zahlung",
        // Italian
        "banca", "bancario", "pagamento",
        // Generic financial terms
        "finance", "financial", "finanzas", "financeiro", "secure", "account",
        // Premium/VIP financial scam terms
        "blackcard", "platinumcard", "goldcard", "premiumcard", "vipcard",
        "prime", "premium", "elite", "exclusive", "benefits", "rewards"
    )
    
    // Crypto/Web3 brands commonly impersonated
    private val cryptoKeywords = setOf(
        "metamask", "metmask", "coinbase", "binance", "kraken", "gemini",
        "blockchain", "crypto", "wallet", "ledger", "trezor", "exodus",
        "trustwallet", "phantom", "uniswap", "opensea", "rarible",
        "subwallet", "polkadot", "kusama", "eigenlayer", "ethereum",
        "defi", "nft", "web3", "dapp", "token", "swap"
    )
    
    // Tech/Cloud brands commonly impersonated
    private val techBrandKeywords = setOf(
        // Microsoft
        "microsoft", "msn", "outlook", "office", "office365", "onedrive",
        "sharepoint", "teams", "azure", "windows", "xbox",
        // Google
        "google", "gmail", "gdrive", "googledrive", "gsuite",
        // Apple
        "apple", "icloud", "iphone", "ipad", "appstore",
        // Amazon
        "amazon", "aws", "prime", "kindle",
        // Other major tech
        "dropbox", "zoom", "slack", "discord", "telegram",
        "whatsapp", "facebook", "instagram", "twitter", "linkedin"
    )
    
    // Suspicious keyword combinations (brand + action words)
    private val suspiciousCombinations = listOf(
        Pair("secure", setOf("metamask", "metmask", "coinbase", "paypal", "bank")),
        Pair("verify", setOf("account", "identity", "wallet", "payment")),
        Pair("update", setOf("security", "payment", "wallet", "account")),
        Pair("confirm", setOf("identity", "payment", "transaction", "wallet"))
    )
    
    /**
     * Analyze a domain or IP address for threats
     * Now supports IP-to-domain resolution and database checks
     * @param input Domain name or IP address to analyze
     * @param isHttp Whether the connection is using unencrypted HTTP (port 80)
     */
    suspend fun analyze(input: String, isHttp: Boolean = false): ThreatAnalysis = withContext(Dispatchers.IO) {
        // Step 1: Resolve IP to domain if needed
        val domain = if (domainResolver.isIpAddress(input)) {
            val resolved = domainResolver.resolveIpToDomain(input)
            if (resolved != null) {
                Log.d(TAG, "Resolved IP $input to domain $resolved")
                resolved
            } else {
                Log.d(TAG, "Could not resolve IP $input, analyzing as IP")
                input
            }
        } else {
            input
        }
        
        // Step 2: Check cache
        cache.get(domain)?.let {
            Log.d(TAG, "Returning cached analysis for $domain")
            return@withContext it
        }
        
        // Step 3: Perform analysis (pass isHttp parameter)
        val analysis = analyzeInternal(domain, input, isHttp)
        
        // Step 4: Cache result
        cache.put(domain, analysis)
        
        analysis
    }
    
    /**
     * Internal analysis method with all advanced checks
     */
    private suspend fun analyzeInternal(domain: String, originalInput: String, isHttp: Boolean = false): ThreatAnalysis {
        val startTime = System.currentTimeMillis()
        val reasons = mutableListOf<String>()
        var suspicionScore = 0f
        val lowerDomain = domain.lowercase()
        
        // Track metadata
        var domainAge: Int? = null
        var trancoRank: Int? = null
        var sslValid: Boolean? = null
        var inBankDatabase = false
        
        // PRIORITY CHECK: Database lookup (Requirements 1.1, 1.2, 1.5)
        if (bankDatabase.isLegitimateBank(lowerDomain)) {
            inBankDatabase = true
            val analysisTime = System.currentTimeMillis() - startTime
            return ThreatAnalysis(
                domain = domain,
                verdict = Verdict.SAFE,
                confidence = 0.95f,
                reasons = listOf("Verified legitimate financial institution from database"),
                metadata = AnalysisMetadata(
                    domainAge = null,
                    trancoRank = null,
                    sslValid = null,
                    inBankDatabase = true,
                    analysisTimeMs = analysisTime
                )
            )
        }
        
        // Check for HTTP on sensitive sites (financial/login pages)
        val hasSensitiveKeyword = bankKeywords.any { lowerDomain.contains(it) } ||
                                  suspiciousPatterns.any { lowerDomain.contains(it) }
        if (isHttp && hasSensitiveKeyword) {
            suspicionScore += 0.5f
            reasons.add("Using unencrypted HTTP for sensitive site")
            reasons.add("Legitimate financial sites always use HTTPS")
            Log.d(TAG, "⚠️ HTTP detected on sensitive site - adding 0.5 to score")
        }
        
        // Check if domain is in legitimate allowlist
        val isLegitimate = legitimateDomains.any { legitDomain ->
            lowerDomain == legitDomain || lowerDomain.endsWith(".$legitDomain")
        }
        
        if (isLegitimate) {
            return ThreatAnalysis(
                domain = domain,
                verdict = Verdict.SAFE,
                confidence = 0.95f,
                reasons = listOf("Known legitimate domain")
            )
        }
        
        // Check against known phishing domains
        if (knownPhishingDomains.contains(lowerDomain)) {
            return ThreatAnalysis(
                domain = domain,
                verdict = Verdict.DANGEROUS,
                confidence = 0.95f,
                reasons = listOf("Known phishing domain")
            )
        }
        
        // === PATTERN-BASED CHECKS ===
        
        // Check for crypto brand impersonation (e.g., secure-metmaskio-eng.framer.ai)
        val hasCryptoKeyword = cryptoKeywords.any { lowerDomain.contains(it) }
        if (hasCryptoKeyword) {
            val matchedCrypto = cryptoKeywords.first { lowerDomain.contains(it) }
            Log.d(TAG, "  ✅ Crypto keyword found: $matchedCrypto")
            
            // Check if it's NOT the official domain
            val isOfficialCrypto = when (matchedCrypto) {
                "metamask" -> lowerDomain == "metamask.io" || lowerDomain.endsWith(".metamask.io")
                "coinbase" -> lowerDomain == "coinbase.com" || lowerDomain.endsWith(".coinbase.com")
                "binance" -> lowerDomain == "binance.com" || lowerDomain.endsWith(".binance.com")
                "ledger" -> lowerDomain == "ledger.com" || lowerDomain.endsWith(".ledger.com")
                "subwallet" -> lowerDomain == "subwallet.js.org" || lowerDomain.endsWith(".subwallet.js.org")
                else -> false
            }
            
            if (!isOfficialCrypto) {
                suspicionScore += 0.7f
                reasons.add("Impersonating crypto/wallet brand: $matchedCrypto")
                reasons.add("Likely phishing attempt targeting crypto users")
                Log.d(TAG, "  ⚠️ Not official domain - adding 0.7 to score (now: $suspicionScore)")
            } else {
                Log.d(TAG, "  ✅ Official domain - no penalty")
            }
        }
        
        // Check for tech brand impersonation (e.g., online-sharepointmsn-live.weebly.com)
        val hasTechBrand = techBrandKeywords.any { lowerDomain.contains(it) }
        if (hasTechBrand) {
            val matchedBrand = techBrandKeywords.first { lowerDomain.contains(it) }
            // Check if it's NOT the official domain
            val isOfficialTech = when (matchedBrand) {
                "microsoft", "msn" -> lowerDomain.endsWith(".microsoft.com") || lowerDomain.endsWith(".msn.com") || 
                                      lowerDomain.endsWith(".live.com") || lowerDomain.endsWith(".outlook.com")
                "sharepoint" -> lowerDomain.endsWith(".sharepoint.com") || lowerDomain.endsWith(".microsoft.com")
                "office", "office365" -> lowerDomain.endsWith(".office.com") || lowerDomain.endsWith(".microsoft.com")
                "google", "gmail" -> lowerDomain.endsWith(".google.com") || lowerDomain.endsWith(".gmail.com")
                "apple", "icloud" -> lowerDomain.endsWith(".apple.com") || lowerDomain.endsWith(".icloud.com")
                "amazon", "aws" -> lowerDomain.endsWith(".amazon.com") || lowerDomain.endsWith(".aws.amazon.com")
                "dropbox" -> lowerDomain.endsWith(".dropbox.com")
                "zoom" -> lowerDomain.endsWith(".zoom.us") || lowerDomain.endsWith(".zoom.com")
                else -> false
            }
            
            if (!isOfficialTech) {
                suspicionScore += 0.7f
                reasons.add("Impersonating tech/cloud brand: $matchedBrand")
                reasons.add("Likely phishing attempt targeting users")
            }
        }
        
        // Check for bank name impersonation with suspicious TLD
        val hasBankKeyword = bankKeywords.any { lowerDomain.contains(it) }
        val hasSuspiciousTld = dangerousTlds.any { domain.endsWith(it, ignoreCase = true) }
        
        if (hasBankKeyword && hasSuspiciousTld) {
            suspicionScore += 0.6f
            val matchedBank = bankKeywords.first { lowerDomain.contains(it) }
            val matchedTld = dangerousTlds.first { domain.endsWith(it, ignoreCase = true) }
            reasons.add("Bank/payment name with suspicious TLD (${matchedBank}${matchedTld})")
            reasons.add("Likely phishing attempt impersonating financial institution")
        }
        
        // Check for suspicious patterns in domain names (common in phishing)
        // Patterns like: admin.*, secure.*, login.*, account.*, verify.*, update.*
        val suspiciousSubdomains = listOf("admin", "secure", "login", "account", "verify", "update", "confirm", "validate")
        val hasAdminSubdomain = suspiciousSubdomains.any { subdomain ->
            lowerDomain.startsWith("$subdomain.") || lowerDomain.contains(".$subdomain.")
        }
        
        if (hasAdminSubdomain && hasBankKeyword) {
            suspicionScore += 0.5f
            val matchedSubdomain = suspiciousSubdomains.first { subdomain ->
                lowerDomain.startsWith("$subdomain.") || lowerDomain.contains(".$subdomain.")
            }
            val matchedBank = bankKeywords.first { lowerDomain.contains(it) }
            reasons.add("Suspicious subdomain pattern: '$matchedSubdomain' with financial keyword '$matchedBank'")
            reasons.add("Common phishing tactic to appear legitimate")
        }
        
        // Check for suspicious keyword combinations (e.g., "secure-metamask")
        suspiciousCombinations.forEach { (prefix, brands) ->
            if (lowerDomain.contains(prefix)) {
                brands.forEach { brand ->
                    if (lowerDomain.contains(brand)) {
                        suspicionScore += 0.4f
                        reasons.add("Suspicious combination: '$prefix' + '$brand'")
                    }
                }
            }
        }
        
        // Check for sites on free hosting platforms
        // Strategy: ANY site on free hosting gets flagged, then advanced checks determine legitimacy
        val onSuspiciousHosting = suspiciousHostingDomains.any { domain.endsWith(it, ignoreCase = true) }
        
        // Also check for hosting provider patterns in domain name
        val hasHostingPattern = lowerDomain.contains("host") || 
                                lowerDomain.contains("server") || 
                                lowerDomain.contains("vps") ||
                                lowerDomain.matches(Regex(".*srv\\d+.*"))
        
        if (onSuspiciousHosting || (hasHostingPattern && hasBankKeyword)) {
            val platform = if (onSuspiciousHosting) {
                suspiciousHostingDomains.first { domain.endsWith(it, ignoreCase = true) }
            } else {
                "hosting provider"
            }
            Log.d(TAG, "  ✅ On suspicious hosting: $platform")
            
            // Check if it has ANY brand-like keywords (not just our known list)
            val hasBrandName = (hasBankKeyword || hasCryptoKeyword || hasTechBrand)
            
            // Check for long subdomain (common in phishing)
            val subdomain = if (onSuspiciousHosting) {
                domain.substringBeforeLast(".$platform")
            } else {
                domain.substringBefore(".")
            }
            val isLongSubdomain = subdomain.length > 20
            
            // Check for multiple hyphens (common in phishing)
            val hasMultipleHyphens = subdomain.count { it == '-' } >= 2
            
            if (hasBrandName) {
                // Known brand on hosting platform - very suspicious
                suspicionScore += 0.7f
                reasons.add("Financial/brand name on hosting platform ($platform)")
                reasons.add("Legitimate brands use their own domains")
                Log.d(TAG, "  ⚠️ Brand on hosting - adding 0.7 to score (now: $suspicionScore)")
            } else if (isLongSubdomain || hasMultipleHyphens) {
                // Suspicious subdomain pattern on hosting
                suspicionScore += 0.3f
                reasons.add("Suspicious subdomain on hosting platform ($platform)")
                Log.d(TAG, "⚠️ Hosting with suspicious pattern - will rely on advanced checks")
            } else if (onSuspiciousHosting) {
                // Generic free hosting - flag but rely heavily on advanced checks
                suspicionScore += 0.1f
                Log.d(TAG, "ℹ️ Site on free hosting ($platform) - advanced checks will determine legitimacy")
            }
        }
        
        // Check for suspicious TLDs
        dangerousTlds.forEach { tld ->
            if (domain.endsWith(tld, ignoreCase = true)) {
                suspicionScore += 0.3f
                reasons.add("Uses suspicious TLD: $tld")
            }
        }
        
        // Check for suspicious keywords
        suspiciousPatterns.forEach { pattern ->
            if (lowerDomain.contains(pattern)) {
                suspicionScore += 0.2f
                reasons.add("Contains suspicious keyword: $pattern")
            }
        }
        
        // Check for excessive subdomains (common in phishing)
        val subdomainCount = domain.count { it == '.' }
        if (subdomainCount > 3) {
            suspicionScore += 0.2f
            reasons.add("Excessive subdomains ($subdomainCount)")
        }
        
        // Check for suspicious characters
        if (domain.contains('-') && suspiciousPatterns.any { lowerDomain.contains(it) }) {
            suspicionScore += 0.15f
            reasons.add("Hyphenated domain with suspicious keywords")
        }
        
        // Check for IP address instead of domain (Requirements 5.4)
        // NOTE: We analyze IPs but rely heavily on advanced checks (SSL, domain age, etc.)
        // to avoid false positives. Only flag if we have strong evidence.
        val isAnalyzingIp = domainResolver.isIpAddress(domain)
        if (isAnalyzingIp) {
            // Check if this is a known CDN/infrastructure IP range
            if (isKnownCdnIp(domain)) {
                Log.d(TAG, "✅ Known CDN/infrastructure IP - marking as SAFE")
                return ThreatAnalysis(
                    domain = domain,
                    verdict = Verdict.SAFE,
                    confidence = 0.95f,
                    reasons = listOf("Known CDN/infrastructure IP address"),
                    metadata = AnalysisMetadata(
                        domainAge = null,
                        trancoRank = null,
                        sslValid = null,
                        inBankDatabase = false,
                        analysisTimeMs = System.currentTimeMillis() - startTime
                    )
                )
            }
            
            // Lower score for bare IPs - we'll rely on advanced checks
            suspicionScore += 0.2f  // Reduced from 0.5f
            reasons.add("Using IP address instead of domain name")
            Log.d(TAG, "⚠️ Analyzing bare IP - will rely on advanced checks for verdict")
        }
        
        // Check for long compound domains with multiple suspicious keywords
        val suspiciousKeywordCount = (bankKeywords + suspiciousPatterns).count { keyword ->
            lowerDomain.contains(keyword)
        }
        
        if (suspiciousKeywordCount >= 2 && lowerDomain.length > 20) {
            suspicionScore += 0.35f
            reasons.add("Multiple suspicious keywords in long domain name")
            reasons.add("Common phishing tactic to appear legitimate")
        }
        
        // Check for homograph attacks (lookalike characters)
        if (containsSuspiciousCharacters(domain)) {
            suspicionScore += 0.3f
            reasons.add("Contains lookalike characters (possible homograph attack)")
        }
        
        // Check for suspicious character set mixing (common in phishing)
        val characterMismatch = detectSuspiciousCharacterMixing(lowerDomain)
        if (characterMismatch != null) {
            suspicionScore += 0.35f
            reasons.add("Suspicious character mixing: ${characterMismatch}")
            reasons.add("Mixed scripts often indicate phishing attempts")
        }
        
        // === ADVANCED CHECKS (Async) ===
        
        // For IPs, try to extract domain from SSL certificate first
        var actualDomain = domain
        var certDomainExtracted: String? = null
        if (domainResolver.isIpAddress(domain)) {
            Log.d(TAG, "🔍 IP detected - attempting to extract domain from SSL certificate")
            try {
                val sslResult = sslValidator.validateCertificate(domain)
                if (sslResult != null) {
                    // Try to extract domain from certificate CN or SAN
                    val certDomain = extractDomainFromCertificate(sslResult)
                    if (certDomain != null && certDomain != domain) {
                        Log.i(TAG, "✅ Extracted domain from SSL cert: $domain -> $certDomain")
                        certDomainExtracted = certDomain
                        actualDomain = certDomain
                        
                        // Check if certificate hostname matches (it won't for IPs, but check anyway)
                        if (!sslResult.hostnameMatches) {
                            Log.w(TAG, "⚠️ SSL certificate hostname mismatch - potential phishing!")
                            suspicionScore += 0.5f
                            reasons.add("SSL certificate does not match the accessed site")
                        }
                        
                        // Now analyze the actual domain instead of the IP
                        // Recursively call analyze with the domain name
                        Log.d(TAG, "🔄 Recursively analyzing extracted domain: $certDomain")
                        val domainAnalysis = analyze(certDomain)
                        Log.i(TAG, "📊 Domain analysis result: $certDomain = ${domainAnalysis.verdict} (${(domainAnalysis.confidence * 100).toInt()}%)")
                        
                        // If the domain itself is suspicious/dangerous, return that verdict
                        if (domainAnalysis.verdict != Verdict.SAFE) {
                            return domainAnalysis
                        }
                        
                        // If domain is SAFE and it's a CDN/infrastructure domain, trust it completely
                        // This prevents false positives for CDN IPs like Fastly, Cloudflare, etc.
                        val certDomainLower = certDomain.lowercase()
                        val isCdnOrInfrastructure = listOf(
                            ".fastly.net", ".fastlylb.net", ".cloudflare.net", ".cloudfront.net",
                            ".akamaiedge.net", ".edgekey.net", ".edgesuite.net",
                            ".amazonaws.com", ".cloudapp.net", ".googleusercontent.com",
                            ".azureedge.net", ".blob.core.windows.net", ".azure.com"
                        ).any { certDomainLower.endsWith(it) }
                        
                        if (isCdnOrInfrastructure) {
                            Log.d(TAG, "✅ Extracted domain is CDN/infrastructure - marking IP as SAFE")
                            return ThreatAnalysis(
                                domain = domain,
                                verdict = Verdict.SAFE,
                                confidence = 0.95f,
                                reasons = listOf("CDN/Infrastructure domain: $certDomain"),
                                metadata = AnalysisMetadata(
                                    domainAge = null,
                                    trancoRank = null,
                                    sslValid = sslResult.isValid,
                                    inBankDatabase = false,
                                    analysisTimeMs = System.currentTimeMillis() - startTime
                                )
                            )
                        }
                        
                        // If domain is SAFE but we have certificate mismatch, still flag it
                        if (!sslResult.hostnameMatches && suspicionScore >= 0.5f) {
                            Log.w(TAG, "⚠️ Domain is safe but certificate mismatch detected - flagging as suspicious")
                            // Continue with current analysis that includes the mismatch penalty
                        }
                    } else {
                        Log.d(TAG, "⚠️ Could not extract domain from certificate")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "❌ Failed to extract domain from SSL: ${e.message}")
            }
        }
        
        // Check domain age (if not an IP)
        if (!domainResolver.isIpAddress(actualDomain)) {
            Log.d(TAG, "🔍 Running advanced checks for: $actualDomain")
            
            // Domain Age Check
            try {
                Log.d(TAG, "  Checking domain age...")
                val ageResult = domainAgeChecker.getDomainAge(domain)
                if (ageResult != null) {
                    domainAge = ageResult.ageInDays
                    Log.i(TAG, "  ✅ Domain age: ${ageResult.ageInDays} days (source: ${ageResult.source})")
                    
                    when {
                        ageResult.ageInDays!! < 7 -> {
                            suspicionScore += 0.40f
                            reasons.add("Domain registered less than 7 days ago (${ageResult.ageInDays} days)")
                        }
                        ageResult.ageInDays < 30 -> {
                            suspicionScore += 0.25f
                            reasons.add("Domain registered less than 30 days ago (${ageResult.ageInDays} days)")
                        }
                        ageResult.ageInDays < 90 -> {
                            suspicionScore += 0.10f
                            reasons.add("Domain registered recently (${ageResult.ageInDays} days ago)")
                        }
                    }
                } else {
                    Log.d(TAG, "  ⚠️ Domain age: No data available")
                }
            } catch (e: Exception) {
                Log.w(TAG, "  ❌ Domain age check failed: ${e.message}", e)
            }
            
            // SSL Certificate Check
            try {
                Log.d(TAG, "  Checking SSL certificate...")
                val sslResult = sslValidator.validateCertificate(domain)
                if (sslResult != null) {
                    sslValid = sslResult.isValid
                    Log.i(TAG, "  ✅ SSL: valid=${sslResult.isValid}, selfSigned=${sslResult.isSelfSigned}, expired=${sslResult.isExpired}, hostnameMatch=${sslResult.hostnameMatches}, shortValidity=${sslResult.hasShortValidityPeriod}")
                    
                    when {
                        sslResult.isSelfSigned -> {
                            suspicionScore += 0.35f
                            reasons.add("Uses self-signed SSL certificate")
                        }
                        sslResult.isExpired -> {
                            suspicionScore += 0.45f
                            reasons.add("SSL certificate is expired")
                        }
                        !sslResult.hostnameMatches -> {
                            suspicionScore += 0.50f
                            reasons.add("SSL certificate hostname does not match domain")
                        }
                        sslResult.hasShortValidityPeriod -> {
                            suspicionScore += 0.50f
                            val days = sslResult.validityPeriodDays ?: 0
                            reasons.add("SSL certificate has very short validity period (${days} days)")
                            reasons.add("Extremely short certificates often indicate phishing")
                        }
                        sslResult.validityPeriodDays != null && sslResult.validityPeriodDays < 90 -> {
                            suspicionScore += 0.25f
                            val days = sslResult.validityPeriodDays
                            reasons.add("SSL certificate has short validity period (${days} days)")
                            reasons.add("Unusually short for legitimate sites")
                        }
                        sslResult.daysUntilExpiry != null && sslResult.daysUntilExpiry < 7 -> {
                            suspicionScore += 0.15f
                            reasons.add("SSL certificate expires soon (${sslResult.daysUntilExpiry} days)")
                        }
                    }
                } else {
                    Log.d(TAG, "  ⚠️ SSL: No certificate data available")
                    // SSL connection failure can indicate suspicious sites
                    if (hasBankKeyword || hasCryptoKeyword) {
                        suspicionScore += 0.20f
                        reasons.add("SSL certificate unavailable for financial/sensitive site")
                        reasons.add("Legitimate financial sites should have valid SSL")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "  ❌ SSL validation failed: ${e.message}", e)
            }
            
            // Tranco Ranking Check
            try {
                Log.d(TAG, "  Checking Tranco ranking...")
                val ranking = trancoChecker.getRanking(domain)
                if (ranking != null) {
                    trancoRank = ranking.rank
                    Log.i(TAG, "  ✅ Tranco rank: ${ranking.rank}")
                    
                    when {
                        ranking.isTopTenThousand -> {
                            suspicionScore -= 0.20f
                            reasons.add("Highly popular site (Tranco rank: ${ranking.rank})")
                        }
                        ranking.isTopHundredThousand -> {
                            suspicionScore -= 0.10f
                            reasons.add("Popular site (Tranco rank: ${ranking.rank})")
                        }
                    }
                } else {
                    Log.d(TAG, "  ⚠️ Tranco: Not in top 1M sites")
                }
            } catch (e: Exception) {
                Log.w(TAG, "  ❌ Tranco ranking check failed: ${e.message}", e)
            }
            
            Log.d(TAG, "🏁 Advanced checks complete for: $domain")
        } else {
            Log.d(TAG, "⏭️ Skipping advanced checks (IP address): $domain")
        }
        
        // Determine verdict based on score
        // For bare IPs, require higher threshold since we don't have domain context
        val verdict = if (isAnalyzingIp) {
            // Higher threshold for IPs - need strong evidence from advanced checks
            when {
                suspicionScore >= 0.8f -> Verdict.DANGEROUS  // Increased from 0.6f
                suspicionScore >= 0.5f -> Verdict.SUSPICIOUS  // Increased from 0.3f
                else -> Verdict.SAFE
            }
        } else {
            // Normal thresholds for domains
            when {
                suspicionScore >= 0.6f -> Verdict.DANGEROUS
                suspicionScore >= 0.3f -> Verdict.SUSPICIOUS
                else -> Verdict.SAFE
            }
        }
        
        val confidence = when (verdict) {
            Verdict.DANGEROUS -> minOf(0.95f, 0.5f + suspicionScore)
            Verdict.SUSPICIOUS -> minOf(0.85f, 0.4f + suspicionScore)
            Verdict.SAFE -> maxOf(0.7f, 1.0f - suspicionScore)
        }
        
        // For bare IPs with SAFE verdict, log but don't show notification
        if (isAnalyzingIp && verdict == Verdict.SAFE) {
            Log.d(TAG, "✅ IP analysis: SAFE (score: $suspicionScore) - no notification needed")
        }
        
        val analysisTime = System.currentTimeMillis() - startTime
        
        if (verdict != Verdict.SAFE) {
            Log.d(TAG, "Threat detected: $domain - $verdict (${confidence * 100}%)")
            reasons.forEach { Log.d(TAG, "  - $it") }
        }
        
        Log.d(TAG, "Analysis complete for $domain in ${analysisTime}ms")
        
        // Select top 3 most significant reasons for notification
        val topReasons = selectTopReasons(reasons, 3)
        
        return ThreatAnalysis(
            domain = domain,
            verdict = verdict,
            confidence = confidence,
            reasons = topReasons,
            metadata = AnalysisMetadata(
                domainAge = domainAge,
                trancoRank = trancoRank,
                sslValid = sslValid,
                inBankDatabase = inBankDatabase,
                analysisTimeMs = analysisTime
            )
        )
    }
    
    /**
     * Select top N most significant reasons
     * Prioritizes critical security issues
     */
    private fun selectTopReasons(reasons: List<String>, count: Int): List<String> {
        if (reasons.size <= count) return reasons
        
        // Priority keywords for sorting
        val priorityKeywords = listOf(
            "phishing", "dangerous", "expired", "self-signed", "hostname",
            "IP address", "registered", "suspicious TLD"
        )
        
        return reasons.sortedByDescending { reason ->
            priorityKeywords.count { keyword -> 
                reason.contains(keyword, ignoreCase = true)
            }
        }.take(count)
    }
    
    /**
     * Check for homograph attacks using lookalike characters
     * e.g., paypal.com vs pаypal.com (Cyrillic 'а')
     */
    private fun containsSuspiciousCharacters(domain: String): Boolean {
        // Check for non-ASCII characters that look like ASCII
        val suspiciousChars = setOf(
            'а', 'е', 'о', 'р', 'с', 'у', 'х', // Cyrillic lookalikes
            'ο', 'ν', 'ρ', 'τ', 'υ', 'χ'      // Greek lookalikes
        )
        return domain.any { it in suspiciousChars }
    }
    
    /**
     * Check if IP belongs to known CDN/infrastructure providers
     */
    private fun isKnownCdnIp(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        
        try {
            val octet1 = parts[0].toInt()
            val octet2 = parts[1].toInt()
            val octet3 = parts[2].toInt()
            
            // Cloudflare IP ranges (104.16.0.0/13, 104.24.0.0/14, etc.)
            if (octet1 == 104 && octet2 in 16..31) return true
            if (octet1 == 172 && octet2 in 64..79) return true
            if (octet1 == 173 && octet2 == 245) return true
            
            // Google infrastructure (216.239.32.0/19, etc.)
            if (octet1 == 216 && octet2 == 239 && octet3 in 32..63) return true
            if (octet1 == 172 && octet2 in 217..219) return true
            if (octet1 == 142 && octet2 == 250) return true
            if (octet1 == 142 && octet2 == 251) return true
            
            // Fastly (151.101.0.0/16, 199.232.0.0/16)
            if (octet1 == 151 && octet2 == 101) return true
            if (octet1 == 199 && octet2 == 232) return true
            
            // Akamai (various ranges)
            if (octet1 == 23 && octet2 in 32..63) return true
            if (octet1 == 104 && octet2 in 64..127) return true
            
            // Azure (13.64.0.0/11, 13.104.0.0/14, etc.)
            if (octet1 == 13 && octet2 in 64..127) return true
            if (octet1 == 13 && octet2 in 104..107) return true
            if (octet1 == 20 && octet2 in 33..255) return true
            if (octet1 == 40 && octet2 in 64..127) return true
            if (octet1 == 52 && octet2 in 224..255) return true
            
        } catch (e: Exception) {
            return false
        }
        
        return false
    }
    
    /**
     * Extract domain name from SSL certificate
     * Parses CN (Common Name) and SAN (Subject Alternative Names)
     */
    private fun extractDomainFromCertificate(sslResult: com.phishguard.phishguard.service.vpn.threat.SSLCertificateValidator.CertificateValidation): String? {
        // First try Subject Alternative Names (most reliable)
        val sans = sslResult.subjectAlternativeNames
        if (!sans.isNullOrEmpty()) {
            // Get the first non-wildcard SAN, or first SAN if all are wildcards
            val domain = sans.firstOrNull { !it.startsWith("*.") } ?: sans.first()
            val cleanDomain = if (domain.startsWith("*.")) domain.substring(2) else domain
            
            // Validate it looks like a domain
            if (cleanDomain.contains(".") && !cleanDomain.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
                Log.d(TAG, "  📝 Extracted from SAN: $cleanDomain")
                return cleanDomain
            }
        }
        
        // Fallback to CN (Common Name) from subject
        val subject = sslResult.subject
        if (subject != null) {
            val cnPattern = Regex("CN=([^,]+)")
            val match = cnPattern.find(subject)
            if (match != null) {
                val cn = match.groupValues[1].trim()
                // Remove wildcard prefix if present
                val domain = if (cn.startsWith("*.")) {
                    cn.substring(2)
                } else {
                    cn
                }
                
                // Validate it looks like a domain
                if (domain.contains(".") && !domain.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
                    Log.d(TAG, "  📝 Extracted from CN: $domain")
                    return domain
                }
            }
        }
        
        Log.d(TAG, "  ❌ Could not extract valid domain from certificate")
        return null
    }
    
    /**
     * Cache DNS resolution for domain resolver
     * Called by DnsMonitor when DNS queries are seen
     */
    fun cacheDnsResolution(domain: String, ipAddress: String) {
        domainResolver.cacheDnsResolution(domain, ipAddress)
    }
    
    /**
     * Cache SNI resolution for domain resolver
     * Called when SNI is extracted from TLS handshakes
     */
    fun cacheSniResolution(domain: String, ipAddress: String) {
        domainResolver.cacheSniResolution(domain, ipAddress)
    }
    
    /**
     * Get domain resolver for external use
     */
    fun getDomainResolver(): DomainResolver = domainResolver
    
    /**
     * Clear the threat analysis cache
     */
    fun clearCache() {
        cache.clear()
        Log.i(TAG, "Threat analysis cache cleared")
    }
    
    /**
     * Detect suspicious character set mixing in domain names
     * Uses Unicode character analysis to detect mixed scripts without hardcoding languages
     */
    private fun detectSuspiciousCharacterMixing(domain: String): String? {
        // Analyze character scripts in the domain
        val scripts = mutableSetOf<Character.UnicodeScript>()
        val nonAsciiChars = mutableListOf<Char>()
        
        for (char in domain) {
            val script = Character.UnicodeScript.of(char.code)
            scripts.add(script)
            
            // Track non-ASCII characters
            if (char.code > 127) {
                nonAsciiChars.add(char)
            }
        }
        
        // Check for mixed scripts (excluding common ones like punctuation)
        val significantScripts = scripts.filter { script ->
            script != Character.UnicodeScript.COMMON && 
            script != Character.UnicodeScript.INHERITED
        }
        
        // Flag if we have multiple significant scripts
        if (significantScripts.size > 1) {
            val scriptNames = significantScripts.map { it.name }.joinToString(", ")
            return "Mixed character scripts: $scriptNames"
        }
        
        // Check for non-Latin characters in domains with Latin-looking structure
        if (nonAsciiChars.isNotEmpty()) {
            val hasLatinStructure = domain.contains('.') && 
                                   domain.matches(Regex(".*[a-z].*")) &&
                                   !domain.matches(Regex("^[\\u0000-\\u007F]*$")) // Not pure ASCII
            
            if (hasLatinStructure) {
                val suspiciousChars = nonAsciiChars.take(3).joinToString("")
                return "Non-Latin characters in Latin-structured domain: '$suspiciousChars'"
            }
        }
        
        // Check for suspicious character patterns that look like Latin but aren't
        val suspiciousLookalikes = mapOf(
            'а' to 'a', 'е' to 'e', 'о' to 'o', 'р' to 'p', 'с' to 'c', 'у' to 'y', 'х' to 'x', // Cyrillic
            'ο' to 'o', 'ν' to 'v', 'ρ' to 'p', 'τ' to 't', 'υ' to 'u', 'χ' to 'x' // Greek
        )
        
        val foundLookalikes = domain.filter { it in suspiciousLookalikes.keys }
        if (foundLookalikes.isNotEmpty()) {
            val examples = foundLookalikes.take(2).map { char ->
                "${char}→${suspiciousLookalikes[char]}"
            }.joinToString(", ")
            return "Lookalike characters detected: $examples"
        }
        
        return null
    }
    
    /**
     * Clean up resources
     */
    fun close() {
        bankDatabase.close()
    }
}
