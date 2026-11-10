package com.example.usb_camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.example.usb_camera.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val FRAGMENT_TAG = "camera_0"
        private const val REQUEST_CAMERA_PERMISSION = 100
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraFragment: DemoFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        requestCameraPermissionIfNeeded()

        cameraFragment = if (savedInstanceState == null) {
            DemoFragment().also { fragment ->
                supportFragmentManager.beginTransaction()
                    .add(R.id.container, fragment, FRAGMENT_TAG)
                    .commitNow()
            }
        } else {
            supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) as DemoFragment
        }

        // If the activity was launched (or resumed) by a USB_DEVICE_ATTACHED event,
        // the UsbDevice is in the intent.  Pass it to the fragment so that it can
        // request USB permission once the surface (and USB monitor) is ready.
        intent?.usbDevice()?.let { device ->
            Log.d(TAG, "onCreate: USB device in intent VID=0x${device.vendorId.toString(16)}")
            cameraFragment.setPendingDevice(device)
        }
    }

    private fun requestCameraPermissionIfNeeded() {
        // RECORD_AUDIO is never used for recording; holding it is what lets Android offer
        // "Always" for this audio-capable USB device, so later attaches need no dialog.
        val missing = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), REQUEST_CAMERA_PERMISSION)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_flip_horizontal)?.isChecked = cameraFragment.flipHorizontal
        menu.findItem(R.id.action_flip_vertical)?.isChecked = cameraFragment.flipVertical
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_flip_horizontal -> {
                cameraFragment.setFlip(!cameraFragment.flipHorizontal, cameraFragment.flipVertical)
                invalidateOptionsMenu()
                true
            }
            R.id.action_flip_vertical -> {
                cameraFragment.setFlip(cameraFragment.flipHorizontal, !cameraFragment.flipVertical)
                invalidateOptionsMenu()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val cameraIndex = permissions.indexOf(Manifest.permission.CAMERA)
        if (requestCode == REQUEST_CAMERA_PERMISSION &&
            cameraIndex >= 0 &&
            grantResults.getOrNull(cameraIndex) == PackageManager.PERMISSION_GRANTED
        ) {
            cameraFragment.triggerCameraOpen()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent: ${intent.action}")
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED == intent.action) {
            intent.usbDevice()?.let { device ->
                Log.d(TAG, "USB device attached: VID=0x${device.vendorId.toString(16)}")
                cameraFragment.requestPermissionForDevice(device)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.usbDevice(): UsbDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
}
