package com.phishguard.phishguard.service.vpn

import java.nio.ByteBuffer

/**
 * Packet Parser
 * 
 * Parses network packets to extract URLs and domains for analysis.
 * Handles IP, TCP, UDP, and DNS protocols.
 */
object PacketParser {
    
    private const val TAG = "PacketParser"
    
    // IP Protocol numbers
    private const val PROTOCOL_TCP = 6
    private const val PROTOCOL_UDP = 17
    
    // DNS port
    private const val DNS_PORT = 53
    
    /**
     * Parse an IP packet and extract domain/URL information
     */
    fun parsePacket(packet: ByteArray): PacketInfo? {
        if (packet.size < 20) return null
        
        val buffer = ByteBuffer.wrap(packet)
        
        // Parse IP header
        val versionAndIHL = buffer.get().toInt() and 0xFF
        val version = versionAndIHL shr 4
        val ihl = (versionAndIHL and 0x0F) * 4
        
        if (version != 4) return null // Only IPv4 for now
        
        // Skip to protocol field
        buffer.position(9)
        val protocol = buffer.get().toInt() and 0xFF
        
        // Skip to source and destination IPs
        buffer.position(12)
        val sourceIp = readIpAddress(buffer)
        val destIp = readIpAddress(buffer)
        
        // Move to transport layer
        buffer.position(ihl)
        
        return when (protocol) {
            PROTOCOL_TCP -> parseTcpPacket(buffer, sourceIp, destIp)
            PROTOCOL_UDP -> parseUdpPacket(buffer, sourceIp, destIp)
            else -> null
        }
    }
    
    private fun parseTcpPacket(buffer: ByteBuffer, sourceIp: String, destIp: String): PacketInfo? {
        if (buffer.remaining() < 20) return null
        
        val sourcePort = buffer.short.toInt() and 0xFFFF
        val destPort = buffer.short.toInt() and 0xFFFF
        
        // TODO: Parse HTTP/HTTPS headers for URLs
        // For HTTPS, we need to extract SNI from TLS ClientHello
        
        return PacketInfo(
            protocol = "TCP",
            sourceIp = sourceIp,
            destIp = destIp,
            sourcePort = sourcePort,
            destPort = destPort,
            domain = null,
            url = null
        )
    }
    
    private fun parseUdpPacket(buffer: ByteBuffer, sourceIp: String, destIp: String): PacketInfo? {
        if (buffer.remaining() < 8) return null
        
        val sourcePort = buffer.short.toInt() and 0xFFFF
        val destPort = buffer.short.toInt() and 0xFFFF
        
        // Check if this is a DNS query
        if (destPort == DNS_PORT) {
            val domain = parseDnsQuery(buffer)
            return PacketInfo(
                protocol = "DNS",
                sourceIp = sourceIp,
                destIp = destIp,
                sourcePort = sourcePort,
                destPort = destPort,
                domain = domain,
                url = null
            )
        }
        
        return PacketInfo(
            protocol = "UDP",
            sourceIp = sourceIp,
            destIp = destIp,
            sourcePort = sourcePort,
            destPort = destPort,
            domain = null,
            url = null
        )
    }
    
    private fun parseDnsQuery(buffer: ByteBuffer): String? {
        try {
            // Skip DNS header (12 bytes)
            if (buffer.remaining() < 12) return null
            buffer.position(buffer.position() + 12)
            
            // Parse domain name from question section
            val domain = StringBuilder()
            var length = buffer.get().toInt() and 0xFF
            
            while (length > 0 && buffer.hasRemaining()) {
                if (domain.isNotEmpty()) {
                    domain.append('.')
                }
                
                for (i in 0 until length) {
                    if (!buffer.hasRemaining()) break
                    domain.append(buffer.get().toInt().toChar())
                }
                
                if (!buffer.hasRemaining()) break
                length = buffer.get().toInt() and 0xFF
            }
            
            return if (domain.isNotEmpty()) domain.toString() else null
        } catch (e: Exception) {
            return null
        }
    }
    
    private fun readIpAddress(buffer: ByteBuffer): String {
        val bytes = ByteArray(4)
        buffer.get(bytes)
        return bytes.joinToString(".") { (it.toInt() and 0xFF).toString() }
    }
    
    /**
     * Extract SNI (Server Name Indication) from TLS ClientHello
     * This allows us to see the domain even for HTTPS connections
     */
    fun extractSniFromTls(packet: ByteArray): String? {
        // TODO: Implement TLS ClientHello parsing to extract SNI
        // This is crucial for HTTPS traffic analysis
        return null
    }
}

/**
 * Parsed packet information
 */
data class PacketInfo(
    val protocol: String,
    val sourceIp: String,
    val destIp: String,
    val sourcePort: Int,
    val destPort: Int,
    val domain: String?,
    val url: String?
)
