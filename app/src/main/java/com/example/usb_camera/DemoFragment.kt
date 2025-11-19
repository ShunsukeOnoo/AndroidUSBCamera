package com.example.usb_camera

import android.graphics.Bitmap
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.base.CameraFragment
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.callback.IPreviewDataCallBack
import com.jiangdg.ausbc.camera.CameraUVC
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import com.jiangdg.ausbc.widget.IAspectRatio
import java.nio.ByteBuffer

class DemoFragment(val callback: ((bmp: Bitmap) -> Unit)) : CameraFragment() {
    private var mTextureView: AspectRatioTextureView? = null
    private var mRootView: FrameLayout? = null

    override fun getRootView(inflater: LayoutInflater, container: ViewGroup?): View? {
        if (mRootView == null) {
            mRootView = FrameLayout(requireContext())
            mTextureView = AspectRatioTextureView(requireContext())
            mRootView?.addView(mTextureView, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
        return mRootView
    }

    override fun getCameraView(): IAspectRatio? {
        return mTextureView
    }

    override fun getCameraViewContainer(): ViewGroup? {
        return mRootView
    }

    override fun onCameraState(
        self: MultiCameraClient.ICamera,
        code: ICameraStateCallBack.State,
        msg: String?
    ) {
        Log.d("DemoFragment", "Camera state: $code, msg: $msg")
        when (code) {
            ICameraStateCallBack.State.OPENED -> {
                Log.d("DemoFragment", "Camera opened successfully")
                startPreview()
            }
            ICameraStateCallBack.State.ERROR -> {
                Log.e("DemoFragment", "Camera error: $msg")
            }
            else -> {
                Log.d("DemoFragment", "Camera state changed: $code")
            }
        }
    }

    private fun startPreview() {
        val camera = getCurrentCamera() as? CameraUVC
        if (camera == null) {
            Log.e("DemoFragment", "Camera is null, cannot start preview")
            return
        }

        Log.d("DemoFragment", "Starting preview...")
        camera.addPreviewDataCallBack(object : IPreviewDataCallBack {
            override fun onPreviewData(
                data: ByteArray?,
                width: Int,
                height: Int,
                format: IPreviewDataCallBack.DataFormat
            ) {
                if (data == null) return
                try {
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bmp.copyPixelsFromBuffer(ByteBuffer.wrap(data))
                    callback.invoke(bmp)
                } catch (e: Exception) {
                    Log.e("DemoFragment", "Error processing preview data", e)
                }
            }
        })
    }

    override fun getGravity(): Int = Gravity.TOP
}