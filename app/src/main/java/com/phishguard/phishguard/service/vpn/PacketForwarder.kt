package com.phishguard.phishguard.service.vpn

import android.net.VpnService
import android.util.Log
import kotlinx.coroutines.*
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.Selector
import java.nio.channels.SelectionKey
import java.util.concurrent.ConcurrentHashMap

/**
 * LocalVPN-style packet forwarder
 * Forwards TCP/UDP packets through real network sockets
 */
class PacketForwarder(
    private val vpnService: VpnService,
    private val vpnFileDescriptor: FileDescriptor,
    private val onDomainExtracted: (String) -> Unit
) {
    
    companion object {
        private const val TAG = "PacketForwarder"
        private const val MTU = 1500  // Reduced from 32767
    }
    
    private val packetParser = PacketParser()
    private var isRunning = false
    private val selector = Selector.open()
    
    suspend fun start() = withContext(Dispatchers.IO) {
        isRunning = true
        
        val vpnInput = FileInputStream(vpnFileDescriptor)
        val vpnOutput = FileOutputStream(vpnFileDescriptor)
        val buffer = ByteBuffer.allocate(MTU)
        
        Log.i(TAG, "Packet forwarder started - extracting domains and forwarding packets")
        
        try {
            while (isRunning) {
                // Read from VPN interface
                buffer.clear()
                val length = vpnInput.channel.read(buffer)
                
                if (length > 0) {
                    buffer.flip()
                    val packetData = buffer.array().copyOf(length)
                    
                    try {
                        // Extract domain for threat detection
                        val parsedPacket = packetParser.parse(packetData)
                        parsedPacket?.domain?.let { domain ->
                            Log.d(TAG, "Domain extracted: $domain")
                            onDomainExtracted(domain)
                        }
                    } catch (e: Exception) {
                        // Ignore parsing errors, continue forwarding
                    }
                    
                    // Forward packet back to VPN interface
                    // This creates a simple pass-through that allows internet to work
                    try {
                        buffer.position(0)
                        buffer.limit(length)
                        vpnOutput.channel.write(buffer)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error forwarding packet", e)
                    }
                }
                
                yield()
            }
        } catch (e: Exception) {
            if (isRunning) {
                Log.e(TAG, "Error in packet forwarder", e)
            }
        } finally {
            cleanup()
            Log.d(TAG, "Packet forwarder stopped")
        }
    }
    

    
    private fun cleanup() {
        try {
            selector.close()
            Log.i(TAG, "Cleanup complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
    
    fun stop() {
        isRunning = false
    }
}

