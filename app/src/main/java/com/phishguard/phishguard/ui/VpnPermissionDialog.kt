package com.phishguard.phishguard.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Window
import android.widget.Button
import android.widget.TextView
import com.phishguard.phishguard.R

/**
 * Dialog explaining VPN permission requirements before requesting permission
 * Required by Google Play Store policies
 */
class VpnPermissionDialog(
    context: Context,
    private val onAccept: () -> Unit,
    private val onDecline: () -> Unit
) : Dialog(context) {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_vpn_permission)
        setCancelable(false)
        
        findViewById<Button>(R.id.acceptButton).setOnClickListener {
            dismiss()
            onAccept()
        }
        
        findViewById<Button>(R.id.declineButton).setOnClickListener {
            dismiss()
            onDecline()
        }
    }
}
