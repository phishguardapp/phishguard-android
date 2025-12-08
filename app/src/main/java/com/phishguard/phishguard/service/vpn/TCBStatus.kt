package com.phishguard.phishguard.service.vpn

/**
 * TCP Connection Block Status
 * Tracks the state of TCP connections
 */
enum class TCBStatus {
    SYN_SENT,
    SYN_RECEIVED,
    ESTABLISHED,
    CLOSE_WAIT,
    LAST_ACK,
    CLOSED
}
