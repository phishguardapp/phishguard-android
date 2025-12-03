package com.phishguard.phishguard.service.vpn

import android.util.Log
import java.nio.ByteBuffer

/**
 * Parses network packets to extract URLs and domains
 * Handles IPv4, TCP, UDP, DNS, and HTTP/HTTPS protocols
 */
class PacketParser {
    
    companion object {
        private const val TAG = "PacketParser"
        
        // IP Protocol numbers
        private const val PROTOCOL_TCP = 6
        private const val PROTOCOL_UDP = 17
        
        // Well-known ports
        private const val PORT_DNS = 53
        private const val PORT_HTTP = 80
        private const val PORT_HTTPS = 443
    }
    
    data class ParsedPacket(
        val protocol: String,
        val sourceIp: String,
        val destIp: String,
        val sourcePort: Int,
        val destPort: Int,
        val domain: String? = null,
        val url: String? = null
    )
    
    fun parse(packet: ByteArray): ParsedPacket? {
        if (packet.size < 20) return null
        
        val buffer = ByteBuffer.wrap(packet)
        
        // Parse IP header
        val versionAndIHL = buffer.get().toInt() and 0xFF
        val version = versionAndIHL shr 4
        
        if (version != 4) return null // Only IPv4 for now
        
        val ihl = (versionAndIHL and 0x0F) * 4
        
        // Skip to protocol field (byte 9)
        buffer.position(9)
        val protocol = buffer.get().toInt() and 0xFF
        
        // Get source and dest IP (bytes 12-19)
        buffer.position(12)
        val sourceIp = readIpAddress(buffer)
        val destIp = readIpAddress(buffer)
        
        // Move to transport layer
        buffer.position(ihl)
        
        return when (protocol) {
            PROTOCOL_TCP -> parseTcp(buffer, sourceIp, destIp, packet)
            PROTOCOL_UDP -> parseUdp(buffer, sourceIp, destIp, packet)
            else -> null
        }
    }
    
    private fun parseTcp(buffer: ByteBuffer, sourceIp: String, destIp: String, packet: ByteArray): ParsedPacket? {
        if (buffer.remaining() < 20) return null
        
        val sourcePort = buffer.short.toInt() and 0xFFFF
        val destPort = buffer.short.toInt() and 0xFFFF
        
        // Skip sequence and ack numbers
        buffer.position(buffer.position() + 8)
        
        val dataOffsetAndFlags = buffer.get().toInt() and 0xFF
        val dataOffset = (dataOffsetAndFlags shr 4) * 4
        
        // Calculate payload position
        val ipHeaderLength = 20 // Simplified, assuming no options
        val payloadStart = ipHeaderLength + dataOffset
        
        if (payloadStart >= packet.size) {
            return ParsedPacket("TCP", sourceIp, destIp, sourcePort, destPort)
        }
        
        // Extract domain/URL based on port
        val domain = when (destPort) {
            PORT_HTTP -> extractHttpHost(packet, payloadStart)
            PORT_HTTPS -> extractTlsSni(packet, payloadStart)
            else -> null
        }
        
        return ParsedPacket("TCP", sourceIp, destIp, sourcePort, destPort, domain = domain)
    }
    
    private fun parseUdp(buffer: ByteBuffer, sourceIp: String, destIp: String, packet: ByteArray): ParsedPacket? {
        if (buffer.remaining() < 8) return null
        
        val sourcePort = buffer.short.toInt() and 0xFFFF
        val destPort = buffer.short.toInt() and 0xFFFF
        
        // DNS query
        val domain = if (destPort == PORT_DNS) {
            val payloadStart = 28 // IP header (20) + UDP header (8)
            extractDnsDomain(packet, payloadStart)
        } else null
        
        return ParsedPacket("UDP", sourceIp, destIp, sourcePort, destPort, domain = domain)
    }
    
    private fun extractDnsDomain(packet: ByteArray, offset: Int): String? {
        try {
            if (offset + 12 >= packet.size) return null
            
            // Skip DNS header (12 bytes)
            var pos = offset + 12
            val domain = StringBuilder()
            
            while (pos < packet.size) {
                val length = packet[pos].toInt() and 0xFF
                if (length == 0) break
                if (length > 63) return null // Invalid label length
                
                pos++
                if (pos + length > packet.size) return null
                
                if (domain.isNotEmpty()) domain.append('.')
                domain.append(String(packet, pos, length, Charsets.US_ASCII))
                pos += length
            }
            
            return if (domain.isNotEmpty()) domain.toString() else null
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting DNS domain", e)
            return null
        }
    }
    
    private fun extractHttpHost(packet: ByteArray, offset: Int): String? {
        try {
            if (offset >= packet.size) return null
            
            val payload = String(packet, offset, packet.size - offset, Charsets.US_ASCII)
            
            // Look for Host header
            val hostPattern = Regex("Host:\\s*([^\\r\\n]+)", RegexOption.IGNORE_CASE)
            val match = hostPattern.find(payload)
            
            return match?.groupValues?.get(1)?.trim()
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting HTTP host", e)
            return null
        }
    }
    
    private fun extractTlsSni(packet: ByteArray, offset: Int): String? {
        try {
            if (offset + 5 >= packet.size) return null
            
            // Check for TLS handshake (0x16)
            if (packet[offset].toInt() and 0xFF != 0x16) return null
            
            // Check for Client Hello (0x01)
            if (offset + 5 < packet.size && packet[offset + 5].toInt() and 0xFF != 0x01) return null
            
            // Parse TLS extensions to find SNI
            var pos = offset + 43 // Skip to session ID length
            if (pos >= packet.size) return null
            
            val sessionIdLength = packet[pos].toInt() and 0xFF
            pos += 1 + sessionIdLength
            
            if (pos + 2 >= packet.size) return null
            
            // Skip cipher suites
            val cipherSuitesLength = ((packet[pos].toInt() and 0xFF) shl 8) or (packet[pos + 1].toInt() and 0xFF)
            pos += 2 + cipherSuitesLength
            
            if (pos >= packet.size) return null
            
            // Skip compression methods
            val compressionLength = packet[pos].toInt() and 0xFF
            pos += 1 + compressionLength
            
            if (pos + 2 >= packet.size) return null
            
            // Extensions length
            val extensionsLength = ((packet[pos].toInt() and 0xFF) shl 8) or (packet[pos + 1].toInt() and 0xFF)
            pos += 2
            
            val extensionsEnd = pos + extensionsLength
            
            // Parse extensions
            while (pos + 4 < extensionsEnd && pos + 4 < packet.size) {
                val extensionType = ((packet[pos].toInt() and 0xFF) shl 8) or (packet[pos + 1].toInt() and 0xFF)
                val extensionLength = ((packet[pos + 2].toInt() and 0xFF) shl 8) or (packet[pos + 3].toInt() and 0xFF)
                pos += 4
                
                // SNI extension (type 0)
                if (extensionType == 0 && pos + extensionLength <= packet.size) {
                    return parseSniExtension(packet, pos, extensionLength)
                }
                
                pos += extensionLength
            }
            
            return null
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting TLS SNI", e)
            return null
        }
    }
    
    private fun parseSniExtension(packet: ByteArray, offset: Int, length: Int): String? {
        try {
            var pos = offset
            
            // Skip SNI list length (2 bytes)
            pos += 2
            
            if (pos >= packet.size) return null
            
            // SNI type (should be 0 for hostname)
            val sniType = packet[pos].toInt() and 0xFF
            pos++
            
            if (sniType != 0 || pos + 2 >= packet.size) return null
            
            // Hostname length
            val hostnameLength = ((packet[pos].toInt() and 0xFF) shl 8) or (packet[pos + 1].toInt() and 0xFF)
            pos += 2
            
            if (pos + hostnameLength > packet.size) return null
            
            return String(packet, pos, hostnameLength, Charsets.US_ASCII)
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing SNI extension", e)
            return null
        }
    }
    
    private fun readIpAddress(buffer: ByteBuffer): String {
        val bytes = ByteArray(4)
        buffer.get(bytes)
        return bytes.joinToString(".") { (it.toInt() and 0xFF).toString() }
    }
}
