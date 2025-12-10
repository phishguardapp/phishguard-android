package com.phishguard.phishguard.service.vpn

import android.util.Log
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Local SOCKS5 proxy that intercepts connections to extract domains
 * Tun2socks forwards to this proxy, we extract domain, then forward to real destination
 */
class LocalSocksProxy(
    private val onDomainDetected: (domain: String, port: Int) -> Unit,
    private val onDomainToIpMapping: ((String, String) -> Unit)? = null
) {
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null
    
    companion object {
        private const val TAG = "LocalSocksProxy"
        private const val PROXY_PORT = 1080
        private const val SOCKS_VERSION = 5.toByte()
    }
    
    fun start() {
        if (isRunning) return
        isRunning = true
        
        scope.launch {
            try {
                serverSocket = ServerSocket(PROXY_PORT)
                Log.i(TAG, "SOCKS proxy started on port $PROXY_PORT")
                
                while (isRunning) {
                    try {
                        val client = serverSocket?.accept()
                        if (client != null) {
                            scope.launch {
                                handleClient(client)
                            }
                        }
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.e(TAG, "Error accepting client", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting SOCKS proxy", e)
            }
        }
    }
    
    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket", e)
        }
        scope.cancel()
        Log.i(TAG, "SOCKS proxy stopped")
    }
    
    private suspend fun handleClient(client: Socket) = withContext(Dispatchers.IO) {
        try {
            val input = client.getInputStream()
            val output = client.getOutputStream()
            
            Log.d(TAG, "New SOCKS client connected")
            
            // Read and log all bytes for debugging
            val buffer = ByteArray(256)
            val bytesRead = input.read(buffer)
            Log.d(TAG, "Received $bytesRead bytes: ${buffer.take(bytesRead).joinToString(" ") { "%02x".format(it) }}")
            
            // SOCKS5 handshake
            if (bytesRead < 2 || buffer[0] != SOCKS_VERSION) {
                Log.e(TAG, "Invalid SOCKS version: ${buffer[0]}")
                client.close()
                return@withContext
            }
            
            val nmethods = buffer[1].toInt() and 0xFF
            Log.d(TAG, "SOCKS handshake - version: ${buffer[0]}, nmethods: $nmethods")
            
            // Respond: no authentication required
            output.write(byteArrayOf(SOCKS_VERSION, 0x00))
            output.flush()
            
            // Read connection request
            val requestBytes = input.read(buffer)
            Log.d(TAG, "Request $requestBytes bytes: ${buffer.take(requestBytes).joinToString(" ") { "%02x".format(it) }}")
            
            if (requestBytes < 4) {
                Log.e(TAG, "Invalid request size")
                client.close()
                return@withContext
            }
            
            val requestVersion = buffer[0].toInt() and 0xFF
            val command = buffer[1].toInt() and 0xFF
            val addressType = buffer[3].toInt() and 0xFF
            
            Log.d(TAG, "SOCKS request - version: $requestVersion, command: $command, addressType: $addressType")
            
            // Handle different SOCKS commands
            when (command) {
                0x03 -> { // UDP ASSOCIATE
                    Log.d(TAG, "UDP ASSOCIATE request - sending success response")
                    // Send success response for UDP
                    output.write(byteArrayOf(
                        SOCKS_VERSION,
                        0x00, // Success
                        0x00, // Reserved
                        0x01, // IPv4
                        127, 0, 0, 1, // Bind address (127.0.0.1)
                        0x04, 0x38 // Bind port (1080)
                    ))
                    output.flush()
                    // Keep connection open for UDP association
                    Thread.sleep(100)
                    client.close()
                    return@withContext
                }
                0x01 -> { // CONNECT - this is what we want for domain extraction
                    Log.d(TAG, "TCP CONNECT request")
                }
                else -> {
                    Log.w(TAG, "Unsupported command: $command")
                    output.write(byteArrayOf(SOCKS_VERSION, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                    output.flush()
                    client.close()
                    return@withContext
                }
            }
            
            // Parse address for CONNECT command
            var domain: String? = null
            var port = 0
            var offset = 4 // Start after version, command, reserved, addressType
            
            when (addressType) {
                0x01 -> { // IPv4
                    if (requestBytes < offset + 6) {
                        Log.e(TAG, "Invalid IPv4 request")
                        client.close()
                        return@withContext
                    }
                    domain = "${buffer[offset].toInt() and 0xFF}.${buffer[offset+1].toInt() and 0xFF}.${buffer[offset+2].toInt() and 0xFF}.${buffer[offset+3].toInt() and 0xFF}"
                    port = ((buffer[offset+4].toInt() and 0xFF) shl 8) or (buffer[offset+5].toInt() and 0xFF)
                }
                0x03 -> { // Domain name
                    val length = buffer[offset].toInt() and 0xFF
                    if (requestBytes < offset + 1 + length + 2) {
                        Log.e(TAG, "Invalid domain request")
                        client.close()
                        return@withContext
                    }
                    domain = String(buffer, offset + 1, length, Charsets.UTF_8)
                    port = ((buffer[offset+1+length].toInt() and 0xFF) shl 8) or (buffer[offset+2+length].toInt() and 0xFF)
                }
                0x04 -> { // IPv6
                    Log.d(TAG, "IPv6 not supported")
                    client.close()
                    return@withContext
                }
            }
            
            if (domain != null) {
                Log.d(TAG, "SOCKS request: $domain:$port")
                
                // Determine if this is an IP address or domain name
                val isIpAddress = domain.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))
                
                if (isIpAddress) {
                    // We have an IP - try to find the domain name
                    Log.d(TAG, "Got IP address: $domain")
                    
                    // Try reverse DNS to get domain name
                    try {
                        val addr = java.net.InetAddress.getByName(domain)
                        val hostname = addr.canonicalHostName
                        if (hostname != domain && !hostname.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
                            Log.i(TAG, "✅ Reverse DNS: $domain -> $hostname")
                            // Cache this mapping for future use
                            onDomainToIpMapping?.invoke(hostname, domain)
                            // Report the domain name with port
                            onDomainDetected(hostname, port)
                        } else {
                            Log.w(TAG, "⚠️ No reverse DNS for $domain")
                            // IMPORTANT: Still analyze the IP with advanced checks
                            // The ThreatDetector will use WHOIS/SSL/domain age to determine if it's suspicious
                            // Only show notification if strong indicators are found
                            Log.i(TAG, "🔍 Analyzing IP with advanced checks: $domain")
                            onDomainDetected(domain, port)  // Let ThreatDetector decide
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ Reverse DNS failed for $domain: ${e.message}")
                        // Still analyze with advanced checks
                        Log.i(TAG, "🔍 Analyzing IP with advanced checks: $domain")
                        onDomainDetected(domain, port)  // Let ThreatDetector decide
                    }
                } else {
                    // We have a domain name - this is the ideal case!
                    Log.i(TAG, "✅ Got domain name: $domain")
                    
                    // Resolve it to IP and cache the mapping for later
                    try {
                        val addr = java.net.InetAddress.getByName(domain)
                        val ipAddress = addr.hostAddress
                        if (ipAddress != null) {
                            Log.i(TAG, "📝 Caching DNS mapping: $domain -> $ipAddress")
                            onDomainToIpMapping?.invoke(domain, ipAddress)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to resolve $domain to IP: ${e.message}")
                    }
                    
                    // Report the domain for analysis with port
                    onDomainDetected(domain, port)
                }
                
                // Connect to real destination
                val destination = Socket()
                try {
                    destination.connect(InetSocketAddress(domain, port), 5000)
                    
                    // Send success response
                    output.write(byteArrayOf(
                        SOCKS_VERSION,
                        0x00, // Success
                        0x00, // Reserved
                        0x01, // IPv4
                        0, 0, 0, 0, // Bind address
                        0, 0 // Bind port
                    ))
                    output.flush()
                    
                    // Relay data between client and destination
                    // Pass domain and port for SNI extraction
                    relay(client, destination, domain, port)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error connecting to $domain:$port", e)
                    // Send failure response
                    output.write(byteArrayOf(
                        SOCKS_VERSION,
                        0x01, // General failure
                        0x00,
                        0x01,
                        0, 0, 0, 0,
                        0, 0
                    ))
                    output.flush()
                } finally {
                    destination.close()
                }
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Error handling client", e)
        } finally {
            client.close()
        }
    }
    
    private suspend fun relay(client: Socket, destination: Socket, detectedDomain: String?, port: Int) = coroutineScope {
        val job1 = launch {
            try {
                val clientInput = client.getInputStream()
                val destOutput = destination.getOutputStream()
                
                // Try to extract SNI from first packet (TLS Client Hello) if it's HTTPS
                var sniExtracted = false
                if (!sniExtracted && detectedDomain != null) {
                    val firstPacket = ByteArray(4096)
                    val bytesRead = clientInput.read(firstPacket, 0, firstPacket.size)
                    
                    if (bytesRead > 0) {
                        // Try to extract SNI
                        try {
                            val sniDomain = SniExtractor.extractSni(firstPacket.copyOf(bytesRead))
                            if (sniDomain != null && sniDomain != detectedDomain) {
                                Log.i(TAG, "🔍 SNI extracted: $sniDomain (was: $detectedDomain)")
                                // Cache this mapping and notify with port
                                onDomainToIpMapping?.invoke(sniDomain, detectedDomain)
                                onDomainDetected(sniDomain, port)
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "SNI extraction failed: ${e.message}")
                        }
                        sniExtracted = true
                        
                        // Forward the first packet we just read
                        destOutput.write(firstPacket, 0, bytesRead)
                        destOutput.flush()
                    }
                }
                
                // Continue relaying remaining data
                clientInput.copyTo(destOutput)
            } catch (e: Exception) {
                // Connection closed
            }
        }
        
        val job2 = launch {
            try {
                destination.getInputStream().copyTo(client.getOutputStream())
            } catch (e: Exception) {
                // Connection closed
            }
        }
        
        job1.join()
        job2.join()
    }
}
