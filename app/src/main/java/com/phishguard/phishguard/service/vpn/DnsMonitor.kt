package com.phishguard.phishguard.service.vpn

import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * Local DNS proxy that intercepts DNS queries to monitor domains
 * Runs on VPN's DNS server address to capture all DNS lookups
 * Forwards queries to real DNS while extracting domain names for analysis
 * Also extracts IP addresses from responses to cache domain-to-IP mappings
 */
class DnsMonitor(
    private val onDomainDetected: (String) -> Unit,
    private val onDnsResolution: ((String, String) -> Unit)? = null
) {
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: DatagramSocket? = null
    private val seenDomains = mutableSetOf<String>()
    
    companion object {
        private const val TAG = "DnsMonitor"
        private const val BUFFER_SIZE = 512
        private const val UPSTREAM_PORT = 53
    }
    
    /**
     * Start DNS proxy on the VPN's local address
     * This will be called with the VPN's DNS server IP (10.0.0.1)
     */
    fun start(localDnsAddress: InetAddress, upstreamDns: InetAddress = InetAddress.getByName("8.8.8.8")) {
        if (isRunning) return
        isRunning = true
        
        scope.launch {
            try {
                // Bind to the VPN's DNS address
                socket = DatagramSocket(53, localDnsAddress)
                Log.i(TAG, "DNS proxy started on ${localDnsAddress.hostAddress}:53")
                Log.i(TAG, "Forwarding to upstream DNS: ${upstreamDns.hostAddress}")
                
                val buffer = ByteArray(BUFFER_SIZE)
                
                while (isRunning) {
                    try {
                        // Receive DNS query from client
                        val queryPacket = DatagramPacket(buffer, buffer.size)
                        socket?.receive(queryPacket)
                        
                        // Extract domain from DNS query
                        val domain = parseDnsQuery(queryPacket.data, queryPacket.length)
                        if (domain != null && !seenDomains.contains(domain)) {
                            seenDomains.add(domain)
                            Log.d(TAG, "DNS query detected: $domain")
                            onDomainDetected(domain)
                        }
                        
                        // Forward query to upstream DNS
                        val upstreamSocket = DatagramSocket()
                        val upstreamPacket = DatagramPacket(
                            queryPacket.data,
                            queryPacket.length,
                            upstreamDns,
                            UPSTREAM_PORT
                        )
                        upstreamSocket.send(upstreamPacket)
                        
                        // Receive response from upstream DNS
                        val responseBuffer = ByteArray(BUFFER_SIZE)
                        val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                        upstreamSocket.soTimeout = 5000 // 5 second timeout
                        upstreamSocket.receive(responsePacket)
                        upstreamSocket.close()
                        
                        // Extract IP addresses from DNS response
                        if (domain != null) {
                            val ipAddresses = parseDnsResponse(responsePacket.data, responsePacket.length)
                            ipAddresses.forEach { ipAddress ->
                                Log.i(TAG, "DNS resolution: $domain -> $ipAddress")
                                onDnsResolution?.invoke(domain, ipAddress)
                            }
                        }
                        
                        // Send response back to client
                        val clientPacket = DatagramPacket(
                            responsePacket.data,
                            responsePacket.length,
                            queryPacket.address,
                            queryPacket.port
                        )
                        socket?.send(clientPacket)
                        
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.w(TAG, "Error processing DNS query: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "Fatal error in DNS proxy", e)
                }
            }
        }
    }
    
    fun stop() {
        isRunning = false
        socket?.close()
        scope.cancel()
        seenDomains.clear()
        Log.i(TAG, "DNS proxy stopped")
    }
    
    private fun parseDnsQuery(data: ByteArray, length: Int): String? {
        try {
            if (length < 13) return null
            
            val buffer = ByteBuffer.wrap(data, 0, length)
            buffer.position(12) // Skip DNS header (12 bytes)
            
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
            return if (result.isNotEmpty() && result.length < 253) result else null
            
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * Parse DNS response to extract IP addresses
     * Returns list of IPv4 addresses from A records
     */
    private fun parseDnsResponse(data: ByteArray, length: Int): List<String> {
        val ipAddresses = mutableListOf<String>()
        
        try {
            if (length < 12) return ipAddresses
            
            val buffer = ByteBuffer.wrap(data, 0, length)
            
            // Read DNS header
            buffer.getShort() // Transaction ID
            buffer.getShort() // Flags
            val qdCount = buffer.getShort().toInt() and 0xFFFF // Question count
            val anCount = buffer.getShort().toInt() and 0xFFFF // Answer count
            buffer.getShort() // Authority count
            buffer.getShort() // Additional count
            
            // Skip questions section
            for (i in 0 until qdCount) {
                skipDnsName(buffer)
                buffer.getShort() // Type
                buffer.getShort() // Class
            }
            
            // Parse answers section
            for (i in 0 until anCount) {
                skipDnsName(buffer)
                
                val type = buffer.getShort().toInt() and 0xFFFF
                buffer.getShort() // Class
                buffer.getInt() // TTL
                val rdLength = buffer.getShort().toInt() and 0xFFFF
                
                // Type 1 = A record (IPv4)
                if (type == 1 && rdLength == 4) {
                    val ip = "${buffer.get().toInt() and 0xFF}.${buffer.get().toInt() and 0xFF}.${buffer.get().toInt() and 0xFF}.${buffer.get().toInt() and 0xFF}"
                    ipAddresses.add(ip)
                } else {
                    // Skip other record types
                    buffer.position(buffer.position() + rdLength)
                }
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing DNS response: ${e.message}")
        }
        
        return ipAddresses
    }
    
    /**
     * Skip a DNS name in the buffer (handles compression)
     */
    private fun skipDnsName(buffer: ByteBuffer) {
        var length = buffer.get().toInt() and 0xFF
        
        while (length > 0) {
            // Check for compression (top 2 bits set)
            if ((length and 0xC0) == 0xC0) {
                buffer.get() // Skip second byte of pointer
                return
            }
            
            // Skip label
            buffer.position(buffer.position() + length)
            length = buffer.get().toInt() and 0xFF
        }
    }
}
