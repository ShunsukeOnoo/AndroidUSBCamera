package com.example.usb_camera

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.graphics.YuvImage
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.base.CameraFragment
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.callback.IPreviewDataCallBack
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import com.jiangdg.ausbc.widget.IAspectRatio
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class DemoFragment : CameraFragment() {

    private var mTextureView: AspectRatioTextureView? = null
    private var mRootView: FrameLayout? = null
    private lateinit var prefs: SharedPreferences

    var onFrame: ((Bitmap) -> Unit)? = null
    private var pendingDevice: UsbDevice? = null

    var flipHorizontal = false
        private set
    var flipVertical = false
        private set

    private var usbDiagReceiver: BroadcastReceiver? = null

    companion object {
        private const val TAG = "DemoFragment"
        private const val PREFS_NAME = "camera_settings"
        private const val CAMERA_INDEX = 0
        private fun prefKeyH() = "flip_h_$CAMERA_INDEX"
        private fun prefKeyV() = "flip_v_$CAMERA_INDEX"
    }

    override fun getRootView(inflater: LayoutInflater, container: ViewGroup?): View? {
        if (mRootView == null) {
            mRootView = FrameLayout(requireContext())
            mTextureView = AspectRatioTextureView(requireContext())
            mRootView?.addView(
                mTextureView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        return mRootView
    }

    override fun getCameraView(): IAspectRatio? = mTextureView
    override fun getCameraViewContainer(): ViewGroup? = mRootView

    override fun getCameraRequest(): CameraRequest =
        CameraRequest.Builder()
            .setPreviewWidth(1280)
            .setPreviewHeight(720)
            .setRenderMode(CameraRequest.RenderMode.OPENGL)
            .setDefaultRotateType(com.jiangdg.ausbc.render.env.RotateType.ANGLE_0)
            .setAudioSource(CameraRequest.AudioSource.SOURCE_SYS_MIC)
            .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_MJPEG)
            .setAspectRatioShow(true)
            .setCaptureRawImage(false)
            .setRawPreviewData(true)
            .create()

    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        flipHorizontal = prefs.getBoolean(prefKeyH(), false)
        flipVertical = prefs.getBoolean(prefKeyV(), false)

        registerUsbDiagReceiver()
        logAllUsbDevices("onViewCreated")

        val libListener = mTextureView?.surfaceTextureListener
        mTextureView?.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, w: Int, h: Int) {
                libListener?.onSurfaceTextureAvailable(surface, w, h)
                openExistingCameraIfNeeded()
            }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, w: Int, h: Int) {
                libListener?.onSurfaceTextureSizeChanged(surface, w, h)
            }
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean =
                libListener?.onSurfaceTextureDestroyed(surface) ?: true
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                libListener?.onSurfaceTextureUpdated(surface)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        usbDiagReceiver?.let { requireContext().unregisterReceiver(it) }
        usbDiagReceiver = null
    }

    private fun registerUsbDiagReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action
                Log.d(TAG, "USB broadcast received: $action")
                @Suppress("DEPRECATION")
                val device: UsbDevice? =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                if (device != null) {
                    logDevice("broadcast", device)
                }
                logAllUsbDevices("after $action")
            }
        }
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            requireContext().registerReceiver(receiver, filter)
        }
        usbDiagReceiver = receiver
        Log.d(TAG, "USB diag BroadcastReceiver registered")
    }

    private fun logAllUsbDevices(context: String) {
        val usbManager = requireContext().getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceMap = usbManager.deviceList
        Log.d(TAG, "[$context] UsbManager.getDeviceList() -> ${deviceMap.size} device(s)")
        if (deviceMap.isEmpty()) {
            Log.d(TAG, "[$context]   (no USB devices visible)")
        }
        deviceMap.values.forEach { logDevice(context, it) }

        val libList = getDeviceList() ?: emptyList()
        Log.d(TAG, "[$context] library getDeviceList() -> ${libList.size} device(s)")
        libList.forEach { d ->
            Log.d(TAG, "[$context]   lib: ${d.deviceName} VID=0x${d.vendorId.toString(16)} PID=0x${d.productId.toString(16)}")
        }
    }

    private fun logDevice(ctx: String, d: UsbDevice) {
        Log.d(TAG, "[$ctx] device: ${d.deviceName}")
        Log.d(TAG, "[$ctx]   VID=0x${d.vendorId.toString(16)} (${d.vendorId})  PID=0x${d.productId.toString(16)} (${d.productId})")
        Log.d(TAG, "[$ctx]   class=${d.deviceClass}  subclass=${d.deviceSubclass}  protocol=${d.deviceProtocol}")
        Log.d(TAG, "[$ctx]   product=${d.productName}  manufacturer=${d.manufacturerName}")
        for (i in 0 until d.interfaceCount) {
            val iface = d.getInterface(i)
            Log.d(TAG, "[$ctx]   iface[$i]: class=${iface.interfaceClass} subclass=${iface.interfaceSubclass} protocol=${iface.interfaceProtocol}")
        }
    }

    fun setPendingDevice(device: UsbDevice) {
        pendingDevice = device
    }

    fun requestPermissionForDevice(device: UsbDevice) {
        if (!isCameraOpened()) {
            Log.d(TAG, "requestPermissionForDevice: VID=0x${device.vendorId.toString(16)}")
            requestPermission(device)
        }
    }

    private fun openExistingCameraIfNeeded() {
        if (isCameraOpened()) return

        val fromList = getDeviceList() ?: emptyList()
        val fromIntent = pendingDevice.also { pendingDevice = null }

        val devices = (fromList + listOfNotNull(fromIntent)).distinctBy { it.deviceId }

        Log.d(TAG, "openExistingCameraIfNeeded: ${fromList.size} from lib list, ${if (fromIntent != null) 1 else 0} from intent")
        if (devices.isEmpty()) {
            Log.d(TAG, "openExistingCameraIfNeeded: no devices — will wait for USB_DEVICE_ATTACHED")
            // Also try raw UsbManager scan
            val usbManager = requireContext().getSystemService(Context.USB_SERVICE) as UsbManager
            val raw = usbManager.deviceList
            Log.d(TAG, "openExistingCameraIfNeeded: raw UsbManager has ${raw.size} device(s)")
            raw.values.forEach { device ->
                Log.d(TAG, "  raw device VID=0x${device.vendorId.toString(16)} PID=0x${device.productId.toString(16)} — requesting permission")
                requestPermission(device)
            }
            return
        }
        devices.forEach { device ->
            Log.d(TAG, "  requestPermission: VID=0x${device.vendorId.toString(16)} PID=0x${device.productId.toString(16)}")
            requestPermission(device)
        }
    }

    fun setFlip(horizontal: Boolean, vertical: Boolean) {
        flipHorizontal = horizontal
        flipVertical = vertical
        prefs.edit()
            .putBoolean(prefKeyH(), horizontal)
            .putBoolean(prefKeyV(), vertical)
            .apply()
        applyTransform()
    }

    private fun applyTransform() {
        mTextureView?.scaleX = if (flipHorizontal) -1f else 1f
        mTextureView?.scaleY = if (flipVertical) -1f else 1f
    }

    override fun onCameraState(
        self: MultiCameraClient.ICamera,
        code: ICameraStateCallBack.State,
        msg: String?
    ) {
        Log.d(TAG, "Camera state: $code${if (msg != null) " msg=$msg" else ""}")
        when (code) {
            ICameraStateCallBack.State.OPENED -> {
                Log.d(TAG, "Camera opened – starting preview")
                startPreview()
                view?.post { applyTransform() }
            }
            ICameraStateCallBack.State.ERROR -> Log.e(TAG, "Camera error: $msg")
            ICameraStateCallBack.State.CLOSED -> Log.w(TAG, "Camera closed: $msg")
            else -> {}
        }
    }

    private fun startPreview() {
        addPreviewDataCallBack(object : IPreviewDataCallBack {
            override fun onPreviewData(
                data: ByteArray?,
                width: Int,
                height: Int,
                format: IPreviewDataCallBack.DataFormat
            ) {
                if (data == null) return
                val bitmap = try {
                    when (format) {
                        IPreviewDataCallBack.DataFormat.NV21 -> {
                            val yuv = YuvImage(data, ImageFormat.NV21, width, height, null)
                            val out = ByteArrayOutputStream()
                            yuv.compressToJpeg(Rect(0, 0, width, height), 90, out)
                            BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
                        }
                        IPreviewDataCallBack.DataFormat.RGBA -> {
                            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                                copyPixelsFromBuffer(ByteBuffer.wrap(data))
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Frame conversion error", e)
                    null
                } ?: return
                onFrame?.invoke(bitmap)
            }
        })
    }

    override fun getGravity(): Int = Gravity.TOP
}
