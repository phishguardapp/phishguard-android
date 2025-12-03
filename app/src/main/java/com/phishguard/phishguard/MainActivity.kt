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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.phishguard.phishguard.service.vpn.PhishGuardVpnService
import com.phishguard.phishguard.ui.theme.PhishGuardTheme

// @AndroidEntryPoint - temporarily disabled due to AGP 9.0 beta compatibility
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
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
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Phase 1: VPN Service Foundation\n" +
                    "✓ VPN service implemented\n" +
                    "⏳ Packet inspection in progress\n" +
                    "⏳ URL extraction coming next",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 32.dp)
        )
    }
}