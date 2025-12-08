package com.phishguard.phishguard.service.vpn.threat

import android.content.Context
import android.content.res.AssetManager
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for BankDatabaseHelper
 * Tests database initialization, domain matching, and error handling
 */
class BankDatabaseHelperTest {
    
    private lateinit var context: Context
    private lateinit var assetManager: AssetManager
    private lateinit var dbFile: File
    
    @Before
    fun setup() {
        context = mockk(relaxed = true)
        assetManager = mockk(relaxed = true)
        dbFile = mockk(relaxed = true)
        
        every { context.assets } returns assetManager
        every { context.getDatabasePath(any()) } returns dbFile
        every { dbFile.exists() } returns true
        every { dbFile.absolutePath } returns "/data/data/com.phishguard/databases/banks.sqlite"
        every { dbFile.parentFile } returns mockk(relaxed = true)
    }
    
    @After
    fun tearDown() {
        clearAllMocks()
    }
    
    @Test
    fun `test database initialization when file exists`() {
        // Given: Database file exists
        every { dbFile.exists() } returns true
        
        mockkStatic(SQLiteDatabase::class)
        val mockDb = mockk<SQLiteDatabase>(relaxed = true)
        every { 
            SQLiteDatabase.openDatabase(any<String>(), any(), any<Int>()) 
        } returns mockDb
        
        // When: Helper is created
        val helper = BankDatabaseHelper(context)
        
        // Then: Database should be opened but not copied
        verify(exactly = 0) { assetManager.open(any()) }
        verify { SQLiteDatabase.openDatabase(any<String>(), any(), any<Int>()) }
        
        helper.close()
        unmockkStatic(SQLiteDatabase::class)
    }
    
    @Test
    fun `test database copied from assets when not exists`() {
        // Given: Database file doesn't exist
        every { dbFile.exists() } returns false
        every { dbFile.parentFile?.mkdirs() } returns true
        
        val mockInputStream = ByteArrayInputStream("mock database content".toByteArray())
        every { assetManager.open("banks.sqlite") } returns mockInputStream
        
        mockkStatic(SQLiteDatabase::class)
        val mockDb = mockk<SQLiteDatabase>(relaxed = true)
        every { 
            SQLiteDatabase.openDatabase(any<String>(), any(), any<Int>()) 
        } returns mockDb
        
        // When: Helper is created
        val helper = BankDatabaseHelper(context)
        
        // Then: Database should be copied from assets
        verify { assetManager.open("banks.sqlite") }
        
        helper.close()
        unmockkStatic(SQLiteDatabase::class)
    }
    
    @Test
    fun `test isLegitimateBank returns true for exact match`() {
        // Given: Database with ICICI bank
        mockkStatic(SQLiteDatabase::class)
        val mockDb = mockk<SQLiteDatabase>(relaxed = true)
        val mockCursor = mockk<Cursor>(relaxed = true)
        
        every { 
            SQLiteDatabase.openDatabase(any<String>(), any(), any<Int>()) 
        } returns mockDb
        
        every { 
            mockDb.rawQuery(any<String>(), any()) 
        } returns mockCursor
        
        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.getInt(0) } returns 1 // Found in database
        every { mockCursor.close() } just Runs
        
        val helper = BankDatabaseHelper(context)
        
        // When: Checking ICICI bank domain
        val result = helper.isLegitimateBank("icici.bank.in")
        
        // Then: Should return true
        assertTrue(result)
        
        helper.close()
        unmockkStatic(SQLiteDatabase::class)
    }
    
    @Test
    fun `test isLegitimateBank returns false for unknown domain`() {
        // Given: Database without the domain
        mockkStatic(SQLiteDatabase::class)
        val mockDb = mockk<SQLiteDatabase>(relaxed = true)
        val mockCursor = mockk<Cursor>(relaxed = true)
        
        every { 
            SQLiteDatabase.openDatabase(any<String>(), any(), any<Int>()) 
        } returns mockDb
        
        every { 
            mockDb.rawQuery(any<String>(), any()) 
        } returns mockCursor
        
        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.getInt(0) } returns 0 // Not found
        every { mockCursor.close() } just Runs
        
        val helper = BankDatabaseHelper(context)
        
        // When: Checking unknown domain
        val result = helper.isLegitimateBank("phishing-site.com")
        
        // Then: Should return false
        assertFalse(result)
        
        helper.close()
        unmockkStatic(SQLiteDatabase::class)
    }
    
    @Test
    fun `test isLegitimateBank handles database errors gracefully`() {
        // Given: Database that throws exception
        mockkStatic(SQLiteDatabase::class)
        val mockDb = mockk<SQLiteDatabase>(relaxed = true)
        
        every { 
            SQLiteDatabase.openDatabase(any<String>(), any(), any<Int>()) 
        } returns mockDb
        
        every { 
            mockDb.rawQuery(any<String>(), any()) 
        } throws Exception("Database error")
        
        val helper = BankDatabaseHelper(context)
        
        // When: Checking domain with database error
        val result = helper.isLegitimateBank("icici.bank.in")
        
        // Then: Should return false and not crash
        assertFalse(result)
        
        helper.close()
        unmockkStatic(SQLiteDatabase::class)
    }
    
    @Test
    fun `test getAllBankDomains returns list of domains`() {
        // Given: Database with multiple banks
        mockkStatic(SQLiteDatabase::class)
        val mockDb = mockk<SQLiteDatabase>(relaxed = true)
        val mockCursor = mockk<Cursor>(relaxed = true)
        
        every { 
            SQLiteDatabase.openDatabase(any<String>(), any(), any<Int>()) 
        } returns mockDb
        
        every { 
            mockDb.rawQuery(any<String>(), any()) 
        } returns mockCursor
        
        every { mockCursor.moveToNext() } returnsMany listOf(true, true, true, false)
        every { mockCursor.getString(0) } returnsMany listOf(
            "icici.bank.in",
            "hdfcbank.com",
            "sbi.co.in"
        )
        every { mockCursor.close() } just Runs
        
        val helper = BankDatabaseHelper(context)
        
        // When: Getting all bank domains
        val domains = helper.getAllBankDomains()
        
        // Then: Should return all domains
        assertEquals(3, domains.size)
        assertTrue(domains.contains("icici.bank.in"))
        assertTrue(domains.contains("hdfcbank.com"))
        assertTrue(domains.contains("sbi.co.in"))
        
        helper.close()
        unmockkStatic(SQLiteDatabase::class)
    }
    
    @Test
    fun `test close releases database resources`() {
        // Given: Open database
        mockkStatic(SQLiteDatabase::class)
        val mockDb = mockk<SQLiteDatabase>(relaxed = true)
        
        every { 
            SQLiteDatabase.openDatabase(any<String>(), any(), any<Int>()) 
        } returns mockDb
        
        every { mockDb.close() } just Runs
        
        val helper = BankDatabaseHelper(context)
        
        // When: Closing helper
        helper.close()
        
        // Then: Database should be closed
        verify { mockDb.close() }
        
        unmockkStatic(SQLiteDatabase::class)
    }
}
