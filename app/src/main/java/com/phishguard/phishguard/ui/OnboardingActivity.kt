package com.phishguard.phishguard.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.phishguard.phishguard.R
import com.phishguard.phishguard.MainActivity
import com.phishguard.phishguard.ui.theme.PhishGuardTheme

/**
 * First-time onboarding screen explaining how PhishGuard works
 * Required by Google Play for VPN apps to clearly disclose functionality
 */
class OnboardingActivity : ComponentActivity() {
    
    companion object {
        private const val PREFS_NAME = "phishguard_prefs"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        
        fun isOnboardingComplete(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ONBOARDING_COMPLETE, false)
        }
        
        fun setOnboardingComplete(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ONBOARDING_COMPLETE, true)
                .apply()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            PhishGuardTheme {
                OnboardingScreen(
                    onComplete = {
                        setOnboardingComplete(this)
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var currentPage by remember { mutableStateOf(0) }
    val pages = listOf(
        OnboardingPage.Welcome,
        OnboardingPage.HowItWorks,
        OnboardingPage.VpnExplanation,
        OnboardingPage.Privacy,
        OnboardingPage.GetStarted
    )
    
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Page indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                pages.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(if (index == currentPage) 12.dp else 8.dp)
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (index == currentPage) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Box(modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Page content
            when (pages[currentPage]) {
                OnboardingPage.Welcome -> WelcomePage()
                OnboardingPage.HowItWorks -> HowItWorksPage()
                OnboardingPage.VpnExplanation -> VpnExplanationPage()
                OnboardingPage.Privacy -> PrivacyPage()
                OnboardingPage.GetStarted -> GetStartedPage()
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentPage > 0) {
                    TextButton(onClick = { currentPage-- }) {
                        Text("Back")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
                
                Button(
                    onClick = {
                        if (currentPage < pages.size - 1) {
                            currentPage++
                        } else {
                            onComplete()
                        }
                    }
                ) {
                    Text(if (currentPage < pages.size - 1) "Next" else "Get Started")
                }
            }
        }
    }
}

@Composable
fun WelcomePage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_phishguard_logo),
            contentDescription = "PhishGuard Logo",
            modifier = Modifier.size(120.dp),
            contentScale = ContentScale.Fit
        )
        
        Text(
            text = "Welcome to PhishGuard",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "Protect yourself from phishing scams and fraudulent websites",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("✓ Real-time threat detection", style = MaterialTheme.typography.bodyMedium)
                Text("✓ Manual URL checking", style = MaterialTheme.typography.bodyMedium)
                Text("✓ Privacy-first design", style = MaterialTheme.typography.bodyMedium)
                Text("✓ No data collection", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun HowItWorksPage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "🔍",
            style = MaterialTheme.typography.displayMedium
        )
        
        Text(
            text = "How PhishGuard Works",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        InfoCard(
            number = "1",
            title = "Monitor",
            description = "PhishGuard monitors domain names as you browse"
        )
        
        InfoCard(
            number = "2",
            title = "Analyze",
            description = "Each domain is checked against phishing patterns"
        )
        
        InfoCard(
            number = "3",
            title = "Alert",
            description = "You receive instant notifications for threats"
        )
        
        InfoCard(
            number = "4",
            title = "Protect",
            description = "Stay safe by avoiding dangerous websites"
        )
    }
}

@Composable
fun VpnExplanationPage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "🔒",
            style = MaterialTheme.typography.displayMedium
        )
        
        Text(
            text = "About Our VPN",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "PhishGuard uses a local VPN to monitor network traffic for security purposes.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "What Our VPN Does:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text("✓ Monitors domain names for threats", style = MaterialTheme.typography.bodyMedium)
                Text("✓ Analyzes URLs locally on your device", style = MaterialTheme.typography.bodyMedium)
                Text("✓ Provides real-time security alerts", style = MaterialTheme.typography.bodyMedium)
            }
        }
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "What Our VPN Does NOT Do:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text("✗ Route traffic through external servers", style = MaterialTheme.typography.bodyMedium)
                Text("✗ Collect or store browsing history", style = MaterialTheme.typography.bodyMedium)
                Text("✗ Slow down your internet", style = MaterialTheme.typography.bodyMedium)
                Text("✗ Share data with third parties", style = MaterialTheme.typography.bodyMedium)
            }
        }
        
        Text(
            text = "Your internet connection goes directly to websites - we just monitor for threats.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun PrivacyPage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "🔐",
            style = MaterialTheme.typography.displayMedium
        )
        
        Text(
            text = "Your Privacy Matters",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "PhishGuard is designed with privacy as the top priority.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PrivacyPoint("All analysis happens on your device")
                PrivacyPoint("No data sent to external servers")
                PrivacyPoint("No browsing history collected")
                PrivacyPoint("No tracking or analytics")
                PrivacyPoint("No ads or data selling")
                PrivacyPoint("No account required")
            }
        }
        
        Text(
            text = "We can't see your browsing activity because we don't collect it.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun GetStartedPage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "🚀",
            style = MaterialTheme.typography.displayMedium
        )
        
        Text(
            text = "Ready to Get Started?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "You're all set! Here's what you can do:",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        
        InfoCard(
            number = "1",
            title = "Enable Protection",
            description = "Tap 'Start Protection' to activate real-time monitoring"
        )
        
        InfoCard(
            number = "2",
            title = "Check URLs",
            description = "Use the manual checker for suspicious links"
        )
        
        InfoCard(
            number = "3",
            title = "Stay Safe",
            description = "Browse confidently with PhishGuard watching"
        )
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "💡 Tip",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "You can turn protection on/off anytime. The manual URL checker works even without VPN enabled.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun InfoCard(number: String, title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = number,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PrivacyPoint(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "✓",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

enum class OnboardingPage {
    Welcome,
    HowItWorks,
    VpnExplanation,
    Privacy,
    GetStarted
}
