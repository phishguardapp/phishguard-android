package com.phishguard.phishguard.service.vpn

import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * Represents an IP packet with TCP/UDP data
 * Based on LocalVPN implementation
 */
class Packet(val buffer: ByteBuffer) {
    
    // IP Header fields
    var ipVersion: Byte = 0
    var ipHeaderLength: Int = 0
    var protocol: Int = 0
    var sourceAddress: InetAddress? = null
    var destinationAddress: InetAddress? = null
    
    // TCP Header fields
    var sourcePort: Int = 0
    var destinationPort: Int = 0
    var tcpSequenceNumber: Long = 0
    var tcpAcknowledgementNumber: Long = 0
    var tcpHeaderLength: Int = 0
    var tcpFlags: Byte = 0
    
    // UDP Header fields
    var udpLength: Int = 0
    
    // Payload
    var payloadOffset: Int = 0
    var payloadSize: Int = 0
    
    companion object {
        const val TCP_FIN: Byte = 0x01
        const val TCP_SYN: Byte = 0x02
        const val TCP_RST: Byte = 0x04
        const val TCP_PSH: Byte = 0x08
        const val TCP_ACK: Byte = 0x10
        
        const val PROTOCOL_TCP = 6
        const val PROTOCOL_UDP = 17
    }
    
    init {
        parseIPHeader()
        when (protocol) {
            PROTOCOL_TCP -> parseTCPHeader()
            PROTOCOL_UDP -> parseUDPHeader()
        }
    }
    
    private fun parseIPHeader() {
        buffer.position(0)
        val versionAndIHL = buffer.get().toInt() and 0xFF
        ipVersion = (versionAndIHL shr 4).toByte()
        ipHeaderLength = (versionAndIHL and 0x0F) * 4
        
        buffer.position(9)
        protocol = buffer.get().toInt() and 0xFF
        
        buffer.position(12)
        val sourceBytes = ByteArray(4)
        buffer.get(sourceBytes)
        sourceAddress = InetAddress.getByAddress(sourceBytes)
        
        val destBytes = ByteArray(4)
        buffer.get(destBytes)
        destinationAddress = InetAddress.getByAddress(destBytes)
    }
    
    private fun parseTCPHeader() {
        buffer.position(ipHeaderLength)
        sourcePort = buffer.short.toInt() and 0xFFFF
        destinationPort = buffer.short.toInt() and 0xFFFF
        
        tcpSequenceNumber = buffer.int.toLong() and 0xFFFFFFFFL
        tcpAcknowledgementNumber = buffer.int.toLong() and 0xFFFFFFFFL
        
        val dataOffsetAndFlags = buffer.short.toInt() and 0xFFFF
        tcpHeaderLength = ((dataOffsetAndFlags shr 12) and 0x0F) * 4
        tcpFlags = (dataOffsetAndFlags and 0x3F).toByte()
        
        payloadOffset = ipHeaderLength + tcpHeaderLength
        payloadSize = buffer.limit() - payloadOffset
    }
    
    private fun parseUDPHeader() {
        buffer.position(ipHeaderLength)
        sourcePort = buffer.short.toInt() and 0xFFFF
        destinationPort = buffer.short.toInt() and 0xFFFF
        udpLength = buffer.short.toInt() and 0xFFFF
        
        payloadOffset = ipHeaderLength + 8
        payloadSize = udpLength - 8
    }
    
    fun isTCP(): Boolean = protocol == PROTOCOL_TCP
    fun isUDP(): Boolean = protocol == PROTOCOL_UDP
    
    fun isSYN(): Boolean = (tcpFlags.toInt() and TCP_SYN.toInt()) != 0
    fun isACK(): Boolean = (tcpFlags.toInt() and TCP_ACK.toInt()) != 0
    fun isFIN(): Boolean = (tcpFlags.toInt() and TCP_FIN.toInt()) != 0
    fun isRST(): Boolean = (tcpFlags.toInt() and TCP_RST.toInt()) != 0
    
    fun updateTCPChecksum() {
        // Simplified - set to 0 and let OS handle it
        buffer.putShort(ipHeaderLength + 16, 0)
    }
    
    fun updateIPChecksum() {
        // Simplified - set to 0 and let OS handle it
        buffer.putShort(10, 0)
    }
    
    fun swapSourceAndDestination() {
        // Swap IP addresses
        val tempAddress = sourceAddress
        sourceAddress = destinationAddress
        destinationAddress = tempAddress
        
        buffer.position(12)
        buffer.put(sourceAddress!!.address)
        buffer.put(destinationAddress!!.address)
        
        // Swap ports
        val tempPort = sourcePort
        sourcePort = destinationPort
        destinationPort = tempPort
        
        buffer.position(ipHeaderLength)
        buffer.putShort(sourcePort.toShort())
        buffer.putShort(destinationPort.toShort())
    }
}
