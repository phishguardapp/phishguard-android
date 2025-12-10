package com.phishguard.phishguard.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages app preferences using SharedPreferences
 */
class PreferencesManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    
    companion object {
        private const val PREFS_NAME = "phishguard_prefs"
        
        // Keys
        private const val KEY_CACHE_DURATION_HOURS = "cache_duration_hours"
        private const val KEY_THREATS_BLOCKED = "threats_blocked"
        private const val KEY_SITES_ANALYZED = "sites_analyzed"
        private const val KEY_LAST_CACHE_CLEAR = "last_cache_clear"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        
        // Defaults
        private const val DEFAULT_CACHE_DURATION = 24 // hours
    }
    
    /**
     * Cache duration in hours (1-24)
     */
    var cacheDurationHours: Int
        get() = prefs.getInt(KEY_CACHE_DURATION_HOURS, DEFAULT_CACHE_DURATION)
        set(value) = prefs.edit().putInt(KEY_CACHE_DURATION_HOURS, value.coerceIn(1, 24)).apply()
    
    /**
     * Cache duration in milliseconds
     */
    val cacheDurationMs: Long
        get() = cacheDurationHours * 60 * 60 * 1000L
    
    /**
     * Total threats blocked
     */
    var threatsBlocked: Int
        get() = prefs.getInt(KEY_THREATS_BLOCKED, 0)
        set(value) = prefs.edit().putInt(KEY_THREATS_BLOCKED, value).apply()
    
    /**
     * Total sites analyzed
     */
    var sitesAnalyzed: Int
        get() = prefs.getInt(KEY_SITES_ANALYZED, 0)
        set(value) = prefs.edit().putInt(KEY_SITES_ANALYZED, value).apply()
    
    /**
     * Last cache clear timestamp
     */
    var lastCacheClear: Long
        get() = prefs.getLong(KEY_LAST_CACHE_CLEAR, 0)
        set(value) = prefs.edit().putLong(KEY_LAST_CACHE_CLEAR, value).apply()
    
    /**
     * Is this the first launch?
     */
    var isFirstLaunch: Boolean
        get() = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_LAUNCH, value).apply()
    
    /**
     * Increment threats blocked counter
     */
    fun incrementThreatsBlocked() {
        threatsBlocked++
    }
    
    /**
     * Increment sites analyzed counter
     */
    fun incrementSitesAnalyzed() {
        sitesAnalyzed++
    }
    
    /**
     * Reset all statistics
     */
    fun resetStatistics() {
        prefs.edit()
            .putInt(KEY_THREATS_BLOCKED, 0)
            .putInt(KEY_SITES_ANALYZED, 0)
            .apply()
    }
    
    /**
     * Clear all preferences (factory reset)
     */
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
