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

        // Wire the preview callback to the ImageView every time the activity is created.
        cameraFragment.onFrame = { bitmap ->
            if (!isFinishing) runOnUiThread { binding.cameraDisplay.setImageBitmap(bitmap) }
        }

        applyFlipToDisplay()

        // If the activity was launched (or resumed) by a USB_DEVICE_ATTACHED event,
        // the UsbDevice is in the intent.  Pass it to the fragment so that it can
        // request USB permission once the surface (and USB monitor) is ready.
        intent?.usbDevice()?.let { device ->
            Log.d(TAG, "onCreate: USB device in intent VID=0x${device.vendorId.toString(16)}")
            cameraFragment.setPendingDevice(device)
        }
    }

    private fun requestCameraPermissionIfNeeded() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
        }
    }

    private fun applyFlipToDisplay() {
        binding.cameraDisplay.scaleX = if (cameraFragment.flipHorizontal) -1f else 1f
        binding.cameraDisplay.scaleY = if (cameraFragment.flipVertical) -1f else 1f
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
                applyFlipToDisplay()
                invalidateOptionsMenu()
                true
            }
            R.id.action_flip_vertical -> {
                cameraFragment.setFlip(cameraFragment.flipHorizontal, !cameraFragment.flipVertical)
                applyFlipToDisplay()
                invalidateOptionsMenu()
                true
            }
            else -> super.onOptionsItemSelected(item)
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
