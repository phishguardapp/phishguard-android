package com.phishguard.phishguard.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.phishguard.phishguard.R
import com.phishguard.phishguard.service.vpn.PhishGuardVpnService
import com.phishguard.phishguard.utils.PreferencesManager
import java.text.SimpleDateFormat
import java.util.*

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var prefsManager: PreferencesManager
    
    private lateinit var threatsBlockedCount: TextView
    private lateinit var sitesAnalyzedCount: TextView
    private lateinit var resetStatsButton: Button
    
    private lateinit var cacheDurationSlider: SeekBar
    private lateinit var cacheDurationValue: TextView
    private lateinit var clearCacheButton: Button
    private lateinit var lastCacheClearText: TextView
    
    private lateinit var reportFalsePositiveButton: Button
    private lateinit var contactSupportButton: Button
    
    private lateinit var privacyPolicyButton: Button
    private lateinit var openSourceLicensesButton: Button
    
    private lateinit var versionText: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        prefsManager = PreferencesManager(this)
        
        // Initialize views
        initializeViews()
        
        // Load current values
        loadStatistics()
        loadCacheSettings()
        loadVersion()
        
        // Setup listeners
        setupListeners()
        
        // Setup toolbar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"
    }
    
    private fun initializeViews() {
        // Statistics
        threatsBlockedCount = findViewById(R.id.threatsBlockedCount)
        sitesAnalyzedCount = findViewById(R.id.sitesAnalyzedCount)
        resetStatsButton = findViewById(R.id.resetStatsButton)
        
        // Cache
        cacheDurationSlider = findViewById(R.id.cacheDurationSlider)
        cacheDurationValue = findViewById(R.id.cacheDurationValue)
        clearCacheButton = findViewById(R.id.clearCacheButton)
        lastCacheClearText = findViewById(R.id.lastCacheClearText)
        
        // Support
        reportFalsePositiveButton = findViewById(R.id.reportFalsePositiveButton)
        contactSupportButton = findViewById(R.id.contactSupportButton)
        
        // Legal
        privacyPolicyButton = findViewById(R.id.privacyPolicyButton)
        openSourceLicensesButton = findViewById(R.id.openSourceLicensesButton)
        
        // About
        versionText = findViewById(R.id.versionText)
    }
    
    private fun loadStatistics() {
        threatsBlockedCount.text = prefsManager.threatsBlocked.toString()
        sitesAnalyzedCount.text = prefsManager.sitesAnalyzed.toString()
    }
    
    private fun loadCacheSettings() {
        val duration = prefsManager.cacheDurationHours
        cacheDurationSlider.progress = duration - 1  // SeekBar is 0-based, we want 1-24
        updateCacheDurationText(duration)
        updateLastCacheClearText()
    }
    
    private fun loadVersion() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            versionText.text = packageInfo.versionName
        } catch (e: Exception) {
            versionText.text = "Unknown"
        }
    }
    
    private fun setupListeners() {
        // Statistics
        resetStatsButton.setOnClickListener {
            showResetStatsDialog()
        }
        
        // Cache duration slider
        cacheDurationSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val hours = progress + 1  // Convert 0-23 to 1-24
                    prefsManager.cacheDurationHours = hours
                    updateCacheDurationText(hours)
                    
                    // Notify VPN service of cache duration change
                    notifyVpnServiceOfCacheChange()
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Clear cache
        clearCacheButton.setOnClickListener {
            showClearCacheDialog()
        }
        
        // Support
        reportFalsePositiveButton.setOnClickListener {
            sendFalsePositiveEmail()
        }
        
        contactSupportButton.setOnClickListener {
            sendSupportEmail()
        }
        
        // Legal
        privacyPolicyButton.setOnClickListener {
            openPrivacyPolicy()
        }
        
        openSourceLicensesButton.setOnClickListener {
            showOpenSourceLicenses()
        }
    }
    
    private fun updateCacheDurationText(hours: Int) {
        cacheDurationValue.text = if (hours == 1) "1 hour" else "$hours hours"
    }
    
    private fun updateLastCacheClearText() {
        val lastClear = prefsManager.lastCacheClear
        if (lastClear == 0L) {
            lastCacheClearText.text = "Last cleared: Never"
        } else {
            val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            val dateStr = dateFormat.format(Date(lastClear))
            lastCacheClearText.text = "Last cleared: $dateStr"
        }
    }
    
    private fun showResetStatsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Reset Statistics")
            .setMessage("Are you sure you want to reset all statistics? This cannot be undone.")
            .setPositiveButton("Reset") { _, _ ->
                prefsManager.resetStatistics()
                loadStatistics()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showClearCacheDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear Cache")
            .setMessage("This will clear all cached threat analysis results. The app will re-analyze sites as you visit them.")
            .setPositiveButton("Clear") { _, _ ->
                clearCache()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun clearCache() {
        // Send broadcast to VPN service to clear cache
        val intent = Intent(PhishGuardVpnService.Actions.ACTION_CLEAR_CACHE)
        sendBroadcast(intent)
        
        // Update timestamp
        prefsManager.lastCacheClear = System.currentTimeMillis()
        updateLastCacheClearText()
        
        // Show confirmation
        AlertDialog.Builder(this)
            .setTitle("Cache Cleared")
            .setMessage("Threat analysis cache has been cleared successfully.")
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun notifyVpnServiceOfCacheChange() {
        val intent = Intent(PhishGuardVpnService.Actions.ACTION_UPDATE_CACHE_DURATION)
        sendBroadcast(intent)
    }
    
    private fun sendFalsePositiveEmail() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("support@phishguard.app"))
            putExtra(Intent.EXTRA_SUBJECT, "PhishGuard - False Positive Report")
            putExtra(Intent.EXTRA_TEXT, """
                Please describe the false positive:
                
                Domain/URL:
                
                Expected Result: (Safe/Suspicious/Dangerous)
                
                Actual Result: (Safe/Suspicious/Dangerous)
                
                Additional Details:
                
                
                ---
                App Version: ${versionText.text}
            """.trimIndent())
        }
        
        try {
            startActivity(Intent.createChooser(intent, "Send Email"))
        } catch (e: Exception) {
            showEmailError()
        }
    }
    
    private fun sendSupportEmail() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("support@phishguard.app"))
            putExtra(Intent.EXTRA_SUBJECT, "PhishGuard - Support Request")
            putExtra(Intent.EXTRA_TEXT, """
                Please describe your issue or question:
                
                
                
                ---
                App Version: ${versionText.text}
                Threats Blocked: ${threatsBlockedCount.text}
                Sites Analyzed: ${sitesAnalyzedCount.text}
            """.trimIndent())
        }
        
        try {
            startActivity(Intent.createChooser(intent, "Send Email"))
        } catch (e: Exception) {
            showEmailError()
        }
    }
    
    private fun showEmailError() {
        AlertDialog.Builder(this)
            .setTitle("No Email App")
            .setMessage("Please install an email app to send feedback.")
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun openPrivacyPolicy() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://phishguard.app/privacy"))
        try {
            startActivity(intent)
        } catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Privacy Policy")
                .setMessage("PhishGuard Privacy Policy\n\nPhishGuard does not collect, store, or transmit any personal data. All threat analysis is performed locally on your device.\n\nFor the full privacy policy, visit: https://phishguard.app/privacy")
                .setPositiveButton("OK", null)
                .show()
        }
    }
    
    private fun showOpenSourceLicenses() {
        AlertDialog.Builder(this)
            .setTitle("Open Source Licenses")
            .setMessage("""
                PhishGuard uses the following open source libraries:
                
                • Kotlin Coroutines (Apache 2.0)
                • AndroidX Libraries (Apache 2.0)
                • Material Components (Apache 2.0)
                • Hilt/Dagger (Apache 2.0)
                • Tun2Socks (BSD 3-Clause)
                
                Full license texts available at:
                https://phishguard.app/licenses
            """.trimIndent())
            .setPositiveButton("OK", null)
            .show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh statistics when returning to settings
        loadStatistics()
    }
}
