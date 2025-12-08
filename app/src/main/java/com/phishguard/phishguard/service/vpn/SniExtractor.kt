package com.phishguard.phishguard.service.vpn

import android.util.Log
import java.nio.ByteBuffer

/**
 * Extracts Server Name Indication (SNI) from TLS Client Hello packets
 * This allows us to see the actual domain name even for CDN-hosted sites
 */
object SniExtractor {
    
    private const val TAG = "SniExtractor"
    
    /**
     * Extract SNI hostname from a TLS Client Hello packet
     * Returns null if not a TLS Client Hello or SNI not found
     */
    fun extractSni(packet: ByteArray): String? {
        try {
            val buffer = ByteBuffer.wrap(packet)
            
            // Check if this is a TLS handshake
            if (buffer.remaining() < 43) return null
            
            // TLS record header
            val contentType = buffer.get().toInt() and 0xFF
            if (contentType != 0x16) return null // 0x16 = Handshake
            
            // TLS version
            buffer.getShort() // Skip version
            
            // Record length
            val recordLength = buffer.getShort().toInt() and 0xFFFF
            if (buffer.remaining() < recordLength) return null
            
            // Handshake type
            val handshakeType = buffer.get().toInt() and 0xFF
            if (handshakeType != 0x01) return null // 0x01 = Client Hello
            
            // Handshake length
            val handshakeLength = ((buffer.get().toInt() and 0xFF) shl 16) or
                                 ((buffer.get().toInt() and 0xFF) shl 8) or
                                 (buffer.get().toInt() and 0xFF)
            
            // Client version
            buffer.getShort()
            
            // Random (32 bytes)
            buffer.position(buffer.position() + 32)
            
            // Session ID
            val sessionIdLength = buffer.get().toInt() and 0xFF
            buffer.position(buffer.position() + sessionIdLength)
            
            // Cipher suites
            val cipherSuitesLength = buffer.getShort().toInt() and 0xFFFF
            buffer.position(buffer.position() + cipherSuitesLength)
            
            // Compression methods
            val compressionMethodsLength = buffer.get().toInt() and 0xFF
            buffer.position(buffer.position() + compressionMethodsLength)
            
            // Extensions
            if (buffer.remaining() < 2) return null
            val extensionsLength = buffer.getShort().toInt() and 0xFFFF
            
            val extensionsEnd = buffer.position() + extensionsLength
            
            // Parse extensions looking for SNI (type 0x0000)
            while (buffer.position() < extensionsEnd && buffer.remaining() >= 4) {
                val extensionType = buffer.getShort().toInt() and 0xFFFF
                val extensionLength = buffer.getShort().toInt() and 0xFFFF
                
                if (extensionType == 0x0000) { // SNI extension
                    // Server Name List Length
                    if (buffer.remaining() < 2) return null
                    buffer.getShort()
                    
                    // Server Name Type (0 = hostname)
                    if (buffer.remaining() < 1) return null
                    val nameType = buffer.get().toInt() and 0xFF
                    
                    if (nameType == 0) {
                        // Server Name Length
                        if (buffer.remaining() < 2) return null
                        val nameLength = buffer.getShort().toInt() and 0xFFFF
                        
                        // Server Name
                        if (buffer.remaining() < nameLength) return null
                        val nameBytes = ByteArray(nameLength)
                        buffer.get(nameBytes)
                        
                        val hostname = String(nameBytes, Charsets.UTF_8)
                        Log.d(TAG, "Extracted SNI: $hostname")
                        return hostname
                    }
                } else {
                    // Skip this extension
                    if (buffer.remaining() < extensionLength) return null
                    buffer.position(buffer.position() + extensionLength)
                }
            }
            
            return null
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting SNI: ${e.message}")
            return null
        }
    }
}
