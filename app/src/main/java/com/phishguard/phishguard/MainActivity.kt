package com.phishguard.phishguard

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

import com.phishguard.phishguard.service.vpn.PhishGuardVpnService
import com.phishguard.phishguard.ui.theme.PhishGuardTheme
import com.phishguard.phishguard.util.ComponentTester

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private var isVpnActive by mutableStateOf(false)
    
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Test components on startup (check Logcat)
        ComponentTester.testThreatDetector(this)
        
        setContent {
            PhishGuardTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PhishGuardHomeScreen(
                        isVpnActive = isVpnActive,
                        onToggleVpn = { toggleVpn() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
    
    private fun toggleVpn() {
        if (isVpnActive) {
            stopVpnService()
        } else {
            requestVpnPermission()
        }
    }
    
    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startVpnService()
        }
    }
    
    private fun startVpnService() {
        val intent = Intent(this, PhishGuardVpnService::class.java).apply {
            action = PhishGuardVpnService.Actions.ACTION_START
        }
        startService(intent)
        isVpnActive = true
    }
    
    private fun stopVpnService() {
        val intent = Intent(this, PhishGuardVpnService::class.java).apply {
            action = PhishGuardVpnService.Actions.ACTION_STOP
        }
        startService(intent)
        isVpnActive = false
    }
}

@Composable
fun PhishGuardHomeScreen(
    isVpnActive: Boolean,
    onToggleVpn: () -> Unit,
    modifier: Modifier = Modifier
) {
    var urlToCheck by remember { mutableStateOf("") }
    var checkResult by remember { mutableStateOf<String?>(null) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.weight(0.5f))
        Text(
            text = "🛡️",
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Text(
            text = "PhishGuard",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "Real-time phishing protection",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isVpnActive) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isVpnActive) "Protected" else "Not Protected",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Text(
                    text = if (isVpnActive) {
                        "PhishGuard is actively monitoring your network traffic"
                    } else {
                        "Enable protection to start monitoring"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Button(
            onClick = onToggleVpn,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isVpnActive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        ) {
            Text(
                text = if (isVpnActive) "Stop Protection" else "Start Protection",
                style = MaterialTheme.typography.titleMedium
            )
        }
        
        // Manual URL Checker
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Check a URL",
                    style = MaterialTheme.typography.titleMedium
                )
                
                OutlinedTextField(
                    value = urlToCheck,
                    onValueChange = { urlToCheck = it; checkResult = null },
                    label = { Text("Enter URL or domain") },
                    placeholder = { Text("example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                val scope = rememberCoroutineScope()
                val context = LocalContext.current
                Button(
                    onClick = {
                        val domain = extractDomain(urlToCheck)
                        if (domain.isNotEmpty()) {
                            scope.launch {
                                val detector = com.phishguard.phishguard.service.vpn.ThreatDetector(context)
                                val analysis = detector.analyze(domain)
                                checkResult = when (analysis.verdict) {
                                    com.phishguard.phishguard.service.vpn.ThreatDetector.Verdict.SAFE ->
                                        "✅ SAFE: $domain appears legitimate"
                                    com.phishguard.phishguard.service.vpn.ThreatDetector.Verdict.SUSPICIOUS ->
                                        "⚠️ SUSPICIOUS: $domain (${(analysis.confidence * 100).toInt()}%)\n${analysis.reasons.joinToString("\n")}"
                                    com.phishguard.phishguard.service.vpn.ThreatDetector.Verdict.DANGEROUS ->
                                        "🛑 DANGEROUS: $domain (${(analysis.confidence * 100).toInt()}%)\n${analysis.reasons.joinToString("\n")}"
                                }
                                detector.close()
                            }
                        } else {
                            checkResult = "Please enter a valid URL or domain"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = urlToCheck.isNotBlank()
                ) {
                    Text("Check URL")
                }
                
                // Quick test button
                TextButton(
                    onClick = {
                        urlToCheck = "paywaveebank.com"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Test with paywaveebank.com")
                }
                
                checkResult?.let { result ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                result.startsWith("✅") -> MaterialTheme.colorScheme.primaryContainer
                                result.startsWith("⚠️") -> MaterialTheme.colorScheme.tertiaryContainer
                                else -> MaterialTheme.colorScheme.errorContainer
                            }
                        )
                    ) {
                        Text(
                            text = result,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            text = "✓ Real-time VPN monitoring\n✓ Instant threat notifications\n✓ Manual URL checking",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun extractDomain(input: String): String {
    var domain = input.trim()
    // Remove protocol
    domain = domain.replace(Regex("^https?://"), "")
    // Remove www.
    domain = domain.replace(Regex("^www\\."), "")
    // Remove path
    domain = domain.split("/")[0]
    // Remove port
    domain = domain.split(":")[0]
    return domain.lowercase()
}