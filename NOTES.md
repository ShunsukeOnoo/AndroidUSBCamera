# Implementation Notes

## AUSBC Library (libausbc 3.3.6)

### Library handles USB detection automatically

`CameraFragment.onResume()` (the parent class) already:
1. Registers a `USBMonitor` that scans for attached USB devices
2. Calls `requestPermission(device)` for any matching device
3. Checks if permission is already granted (`has permission: true`) and connects immediately without showing a dialog
4. Opens the camera and starts OpenGL rendering to the `TextureView`

**Do not call `openExistingCameraIfNeeded()` or `requestPermission()` manually in `onResume()` or `onSurfaceTextureAvailable()`.**  
The library is already doing this from its own `onResume()` via `super.onResume()`. Duplicate calls race against the library's own connection and produce ERROR -99 (device busy), which causes the library to close the camera immediately after opening it.

### First-launch exception: CAMERA permission

On first install, Android shows the CAMERA runtime permission dialog before any USB permission dialog. While the CAMERA dialog is visible, Android auto-denies any `UsbManager.requestPermission()` call (only one system dialog can be shown at a time). After the user grants CAMERA permission, `onResume()` fires but the USB device permission has already been denied.

Fix: override `onRequestPermissionsResult` and call `cameraFragment.triggerCameraOpen()` after CAMERA permission is granted. `triggerCameraOpen()` posts a 2-second delayed `openExistingCameraIfNeeded()` — the delay lets the library's own `onResume()` scan happen first; if the library already opened the camera, `isCameraOpened()` returns true and the call is skipped.

```kotlin
// MainActivity.kt
override fun onRequestPermissionsResult(requestCode, permissions, grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == REQUEST_CAMERA_PERMISSION &&
        grantResults.isNotEmpty() &&
        grantResults[0] == PackageManager.PERMISSION_GRANTED
    ) {
        cameraFragment.triggerCameraOpen()
    }
}
```

### Rendering

The library renders camera frames directly to the `AspectRatioTextureView` via OpenGL ES (`RenderMode.OPENGL`). No frame callbacks or secondary `ImageView` are needed. Setting `setRawPreviewData(false)` and removing any `onFrame` callback avoids unnecessary byte-array allocations.

### Normal connection log sequence (pre-connected, permission granted)

```
MultiCameraClient: attach device name/pid/vid:/dev/bus/usb/001/003&33&21325
USBMonitor: request permission, has permission: true
MultiCameraClient: connect device name/pid/vid:/dev/bus/usb/001/003&33&21325
DemoFragment: Camera state: OPENED
RenderManager: camera render frame rate is 25 fps-->gl_render
```

If you see ERROR -99 followed by CLOSED after OPENED, a duplicate `openExistingCameraIfNeeded()` call is the likely cause.

## Flip (horizontal / vertical)

Flip is implemented by setting `scaleX = -1f` / `scaleY = -1f` on the `TextureView`. State is persisted in `SharedPreferences` (`flip_h_0`, `flip_v_0`).

`applyTransform()` must be called in two places:
- `onViewCreated()` after loading prefs — applies the saved state before the camera opens, so the first frame is already correctly oriented
- `onCameraState(OPENED)` via `view?.post { applyTransform() }` — re-applies after the library sets up its GL context (which may reset the view transform)

## USB Camera Hardware

- **Device**: AV TO USB2.0 by MACROSILICON
- **VID**: 21325 (0x534D), **PID**: 33 (0x0021)
- **Filter**: `app/src/main/res/xml/device_filter.xml`
- **Physical quirk**: the camera physically flips left-right, so the uncorrected view is a horizontal mirror image. Enabling "Flip Horizontal" in the app restores the correct orientation.
- **Frame rate**: 25–26 fps at 1280×720 MJPEG via OpenGL

## Manifest

- `launchMode="singleTop"` — prevents a second activity instance when the USB_DEVICE_ATTACHED intent arrives while the app is already in the foreground; `onNewIntent()` handles it instead.
- `USB_DEVICE_ATTACHED` intent-filter with `device_filter.xml` meta-data — causes Android to offer this app when the specific camera is connected.

## Build

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew installDebug
```

ADB over WiFi: `~/Library/Android/sdk/platform-tools/adb -s 192.168.11.6:39585`
