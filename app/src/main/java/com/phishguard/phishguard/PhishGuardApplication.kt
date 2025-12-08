package com.phishguard.phishguard

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

/**
 * PhishGuard Application class with Hilt DI
 */
@HiltAndroidApp
class PhishGuardApplication : Application() {
    
    companion object {
        private const val TAG = "PhishGuardApp"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "PhishGuard application initialized")
    }
}
