package com.phishguard.phishguard.service.vpn

import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.nio.ByteBuffer

/**
 * Monitors packets from VPN interface to extract domains
 * Works in production without requiring logcat access
 * 
 * Extracts domains from:
 * 1. DNS queries (UDP port 53)
 * 2. SNI in TLS Client Hello (HTTPS)
 */
class PacketMonitor(
    private val vpnFileDescriptor: ParcelFileDescriptor,
    private val onDomainDetected: (String) -> Unit
) {
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val seenDomains = mutableSetOf<String>()
    
    companion object {
        private const val TAG = "PacketMonitor"
        private const val BUFFER_SIZE = 4096
    }
    
    fun start() {
        if (isRunning) return
        isRunning = true
        
        scope.launch {
            monitorPackets()
        }
        
        Log.i(TAG, "Packet monitor started")
    }
    
    fun stop() {
        isRunning = false
        scope.cancel()
        seenDomains.clear()
        Log.i(TAG, "Packet monitor stopped")
    }
    
    /**
     * Read packets from VPN interface and extract domains
     */
    private suspend fun monitorPackets() {
        val inputStream = FileInputStream(vpnFileDescriptor.fileDescriptor)
        val buffer = ByteBuffer.allocate(BUFFER_SIZE)
        
        try {
            Log.i(TAG, "Monitoring packets for DNS queries and SNI...")
            
            while (isRunning) {
                buffer.clear()
                val length = inputStream.read(buffer.array())
                
                if (length > 0) {
                    buffer.limit(length)
                    
                    // Try to extract domain from this packet
                    val domain = extractDomainFromPacket(buffer)
                    if (domain != null && !seenDomains.contains(domain)) {
                        seenDomains.add(domain)
                        Log.i(TAG, "Domain detected: $domain")
                        onDomainDetected(domain)
                    }
                }
            }
        } catch (e: Exception) {
            if (isRunning) {
                Log.e(TAG, "Error monitoring packets", e)
            }
        }
    }
    
    /**
     * Extract domain from packet (DNS or SNI)
     */
    private fun extractDomainFromPacket(buffer: ByteBuffer): String? {
        try {
            buffer.position(0)
            
            // Check IP version
            val versionAndHeaderLength = buffer.get(0).toInt() and 0xFF
            val version = (versionAndHeaderLength shr 4) and 0x0F
            
            if (version != 4) return null // Only IPv4 for now
            
            val ipHeaderLength = (versionAndHeaderLength and 0x0F) * 4
            val protocol = buffer.get(9).toInt() and 0xFF
            
            // Check for UDP (DNS queries)
            if (protocol == 17) { // UDP
                val destPort = buffer.getShort(ipHeaderLength + 2).toInt() and 0xFFFF
                if (destPort == 53) { // DNS
                    return extractDomainFromDns(buffer, ipHeaderLength + 8)
                }
            }
            
            // Check for TCP (HTTPS with SNI)
            if (protocol == 6) { // TCP
                val destPort = buffer.getShort(ipHeaderLength + 2).toInt() and 0xFFFF
                if (destPort == 443) { // HTTPS
                    val tcpHeaderLength = ((buffer.get(ipHeaderLength + 12).toInt() shr 4) and 0x0F) * 4
                    val dataOffset = ipHeaderLength + tcpHeaderLength
                    
                    if (buffer.remaining() > dataOffset) {
                        buffer.position(dataOffset)
                        val remainingData = ByteArray(buffer.remaining())
                        buffer.get(remainingData)
                        return SniExtractor.extractSni(remainingData)
                    }
                }
            }
            
            return null
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * Extract domain from DNS query packet
     */
    private fun extractDomainFromDns(buffer: ByteBuffer, dnsOffset: Int): String? {
        try {
            buffer.position(dnsOffset + 12) // Skip DNS header
            
            val domain = StringBuilder()
            var labelLength = buffer.get().toInt() and 0xFF
            
            while (labelLength > 0 && buffer.remaining() > 0) {
                if (domain.isNotEmpty()) {
                    domain.append('.')
                }
                
                for (i in 0 until labelLength) {
                    if (buffer.remaining() == 0) break
                    val char = buffer.get().toInt() and 0xFF
                    if (char in 32..126) { // Printable ASCII
                        domain.append(char.toChar())
                    }
                }
                
                if (buffer.remaining() == 0) break
                labelLength = buffer.get().toInt() and 0xFF
            }
            
            val result = domain.toString()
            return if (result.isNotEmpty() && result.length < 253) {
                Log.d(TAG, "Extracted DNS query: $result")
                result
            } else null
            
        } catch (e: Exception) {
            return null
        }
    }
}
