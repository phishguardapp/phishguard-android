package com.phishguard.phishguard.service.vpn

import android.util.Log
import kotlinx.coroutines.*
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.Selector
import java.nio.channels.SelectionKey

/**
 * Forwards packets between VPN interface and real network
 * This is a simplified forwarder for Phase 1 - just passes traffic through
 */
class PacketForwarder(
    private val vpnFileDescriptor: FileDescriptor,
    private val onDomainExtracted: (String) -> Unit
) {
    
    companion object {
        private const val TAG = "PacketForwarder"
        private const val MTU = 1500
    }
    
    private val packetParser = PacketParser()
    private var isRunning = false
    
    suspend fun start() = withContext(Dispatchers.IO) {
        isRunning = true
        
        val vpnInput = FileInputStream(vpnFileDescriptor)
        val vpnOutput = FileOutputStream(vpnFileDescriptor)
        
        val buffer = ByteBuffer.allocate(MTU)
        
        Log.d(TAG, "Packet forwarder started")
        
        try {
            while (isRunning) {
                buffer.clear()
                
                // Read from VPN interface
                val length = vpnInput.channel.read(buffer)
                
                if (length > 0) {
                    buffer.flip()
                    
                    // Extract packet data
                    val packet = ByteArray(length)
                    buffer.get(packet)
                    
                    // Parse and extract domain (for monitoring only)
                    try {
                        val parsedPacket = packetParser.parse(packet)
                        parsedPacket?.domain?.let { domain ->
                            onDomainExtracted(domain)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing packet", e)
                    }
                    
                    // Write packet back (loopback for now - Phase 1 limitation)
                    // In Phase 2, we'll implement proper forwarding with network sockets
                    buffer.rewind()
                    vpnOutput.channel.write(buffer)
                }
                
                yield()
            }
        } catch (e: Exception) {
            if (isRunning) {
                Log.e(TAG, "Error in packet forwarder", e)
            }
        } finally {
            Log.d(TAG, "Packet forwarder stopped")
        }
    }
    
    fun stop() {
        isRunning = false
    }
}
