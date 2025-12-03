package com.phishguard.phishguard

import android.app.Application
import android.util.Log

/**
 * PhishGuard Application class
 * 
 * Future: Will be annotated with @HiltAndroidApp when Hilt is re-enabled
 * after AGP 9.0 beta compatibility issues are resolved
 */
class PhishGuardApplication : Application() {
    
    companion object {
        private const val TAG = "PhishGuardApp"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "PhishGuard application initialized")
    }
}
