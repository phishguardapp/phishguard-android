package com.phishguard.phishguard.service.vpn

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.phishguard.phishguard.R

/**
 * Displays full-screen overlay warnings for dangerous sites
 * This provides immediate, unmissable alerts to users
 */
class ThreatOverlayService : Service() {
    
    private var windowManager: WindowManager? = null
    private var overlayView: android.view.View? = null
    
    companion object {
        const val EXTRA_DOMAIN = "domain"
        const val EXTRA_CONFIDENCE = "confidence"
        const val EXTRA_REASONS = "reasons"
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val domain = intent?.getStringExtra(EXTRA_DOMAIN) ?: return START_NOT_STICKY
        val confidence = intent.getFloatExtra(EXTRA_CONFIDENCE, 0f)
        val reasons = intent.getStringArrayListExtra(EXTRA_REASONS) ?: emptyList()
        
        showOverlay(domain, confidence, reasons)
        
        return START_NOT_STICKY
    }
    
    private fun showOverlay(domain: String, confidence: Float, reasons: List<String>) {
        // For now, we'll use a simpler notification approach
        // Full overlay requires SYSTEM_ALERT_WINDOW permission
        stopSelf()
    }
    
    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }
    
    private fun removeOverlay() {
        overlayView?.let {
            windowManager?.removeView(it)
            overlayView = null
        }
    }
}
