package com.phishguard.phishguard.service.vpn.threat

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Helper class for managing the SQLite database of legitimate financial institutions.
 * Handles database initialization, asset copying, and domain lookups.
 */
class BankDatabaseHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "BankDatabaseHelper"
        private const val DATABASE_NAME = "banks.sqlite"
        private const val ASSET_DATABASE_PATH = "banks.sqlite"
    }
    
    private var database: SQLiteDatabase? = null
    
    init {
        initializeDatabase()
    }
    
    /**
     * Initialize the database by copying from assets if needed
     */
    private fun initializeDatabase() {
        try {
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            
            // Copy database from assets if it doesn't exist
            if (!dbFile.exists()) {
                Log.d(TAG, "Database not found, copying from assets...")
                copyDatabaseFromAssets(dbFile)
            }
            
            // Open the database
            database = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            
            Log.d(TAG, "Database initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize database", e)
            database = null
        }
    }
    
    /**
     * Copy database from assets to internal storage
     */
    private fun copyDatabaseFromAssets(dbFile: File) {
        try {
            // Create parent directories if they don't exist
            dbFile.parentFile?.mkdirs()
            
            // Copy database from assets
            context.assets.open(ASSET_DATABASE_PATH).use { input ->
                FileOutputStream(dbFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.d(TAG, "Database copied from assets successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy database from assets", e)
            throw e
        }
    }
    
    /**
     * Check if a domain is a legitimate bank
     * Supports exact matching and subdomain matching
     * 
     * @param domain The domain to check (e.g., "icici.bank.in" or "secure.icicibank.com")
     * @return true if the domain is found in the legitimate banks database
     */
    fun isLegitimateBank(domain: String): Boolean {
        if (database == null) {
            Log.w(TAG, "Database not available, cannot check domain: $domain")
            return false
        }
        
        try {
            val lowerDomain = domain.lowercase()
            
            // Query for exact match or subdomain match
            val cursor = database?.rawQuery(
                "SELECT COUNT(*) FROM banks WHERE LOWER(tld) = ? OR LOWER(url) LIKE ?",
                arrayOf(lowerDomain, "%$lowerDomain%")
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    val count = it.getInt(0)
                    val isLegit = count > 0
                    
                    if (isLegit) {
                        Log.d(TAG, "Domain $domain found in banks database")
                    }
                    
                    return isLegit
                }
            }
        } catch (e: SQLiteException) {
            Log.e(TAG, "Database query failed for domain: $domain", e)
        }
        
        return false
    }
    
    /**
     * Get all bank domains from the database
     * Useful for testing and validation
     * 
     * @return List of all bank TLDs in the database
     */
    fun getAllBankDomains(): List<String> {
        if (database == null) {
            Log.w(TAG, "Database not available")
            return emptyList()
        }
        
        val domains = mutableListOf<String>()
        
        try {
            val cursor = database?.rawQuery(
                "SELECT DISTINCT tld FROM banks WHERE tld IS NOT NULL",
                null
            )
            
            cursor?.use {
                while (it.moveToNext()) {
                    val tld = it.getString(0)
                    if (tld.isNotBlank()) {
                        domains.add(tld)
                    }
                }
            }
            
            Log.d(TAG, "Retrieved ${domains.size} bank domains from database")
        } catch (e: SQLiteException) {
            Log.e(TAG, "Failed to retrieve bank domains", e)
        }
        
        return domains
    }
    
    /**
     * Close the database connection
     */
    fun close() {
        try {
            database?.close()
            database = null
            Log.d(TAG, "Database closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing database", e)
        }
    }
}
