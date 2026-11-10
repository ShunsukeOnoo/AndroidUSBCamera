package com.example.usb_camera

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.UserManager
import android.util.Log

/**
 * Receives USB_DEVICE_ATTACHED and forwards it to [MainActivity].
 *
 * A camera that is already plugged in at boot is reported by the system before the user is
 * unlocked. At that moment the system only matches direct-boot-aware activities, so this
 * activity is direct-boot-aware: being matched is what makes the system grant USB permission
 * to the app saved via "Always open", with no dialog. MainActivity itself stays
 * non-direct-boot-aware because it (and the camera library) use credential-protected storage.
 */
class UsbAttachActivity : Activity() {

    companion object {
        private const val TAG = "UsbAttachActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val unlocked = getSystemService(UserManager::class.java).isUserUnlocked
        Log.d(TAG, "onCreate: ${intent?.action} userUnlocked=$unlocked")
        // Before unlock, MainActivity can't start; the permission grant is all that's needed.
        if (unlocked) {
            startActivity(Intent(intent).apply {
                setClass(this@UsbAttachActivity, MainActivity::class.java)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            })
        }
        finish()
    }
}
