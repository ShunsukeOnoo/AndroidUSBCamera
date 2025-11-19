package com.example.usb_camera

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.usb_camera.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fragment = DemoFragment { bitmap ->
            runOnUiThread {
                binding.usb.setImageBitmap(bitmap)
            }
        }

        supportFragmentManager.beginTransaction()
            .add(R.id.container, fragment)
            .commit()
    }
}