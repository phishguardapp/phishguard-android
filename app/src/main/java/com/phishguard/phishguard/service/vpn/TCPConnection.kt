package com.phishguard.phishguard.service.vpn

import android.util.Log
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel

/**
 * Represents a TCP connection being forwarded
 * Based on LocalVPN implementation
 */
class TCPConnection(
    val sourceAddress: String,
    val sourcePort: Int,
    val destinationAddress: String,
    val destinationPort: Int
) {
    companion object {
        private const val TAG = "TCPConnection"
    }
    
    var channel: SocketChannel? = null
    var selectionKey: SelectionKey? = null
    var status: TCBStatus = TCBStatus.SYN_SENT
    
    val sendBuffer: ByteBuffer = ByteBuffer.allocate(8192)  // Reduced from 32768
    val receiveBuffer: ByteBuffer = ByteBuffer.allocate(8192)  // Reduced from 32768
    
    var sequenceNumber: Long = 0
    var acknowledgementNumber: Long = 0
    var lastSequenceSent: Long = 0
    
    fun connect(): Boolean {
        return try {
            channel = SocketChannel.open()
            channel?.configureBlocking(false)
            channel?.connect(InetSocketAddress(destinationAddress, destinationPort))
            Log.d(TAG, "Connecting to $destinationAddress:$destinationPort")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Error connecting", e)
            false
        }
    }
    
    fun finishConnect(): Boolean {
        return try {
            if (channel?.finishConnect() == true) {
                status = TCBStatus.ESTABLISHED
                Log.d(TAG, "Connected to $destinationAddress:$destinationPort")
                true
            } else {
                false
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error finishing connect", e)
            close()
            false
        }
    }
    
    fun read(): Int {
        return try {
            channel?.read(receiveBuffer) ?: -1
        } catch (e: IOException) {
            Log.e(TAG, "Error reading", e)
            -1
        }
    }
    
    fun write(): Int {
        return try {
            channel?.write(sendBuffer) ?: -1
        } catch (e: IOException) {
            Log.e(TAG, "Error writing", e)
            -1
        }
    }
    
    fun close() {
        try {
            channel?.close()
            selectionKey?.cancel()
            status = TCBStatus.CLOSED
            Log.d(TAG, "Closed connection to $destinationAddress:$destinationPort")
        } catch (e: IOException) {
            Log.e(TAG, "Error closing", e)
        }
    }
    
    fun getKey(): String {
        return "$sourceAddress:$sourcePort-$destinationAddress:$destinationPort"
    }
}
