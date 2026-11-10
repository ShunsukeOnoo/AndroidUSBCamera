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

### Library bug: USB permission result is lost on Android 12+

`USBMonitor.register()` creates its permission `PendingIntent` with `FLAG_IMMUTABLE` on API 31+. The system cannot fill in `EXTRA_DEVICE` / `EXTRA_PERMISSION_GRANTED`, so the library's receiver sees `device=null, granted=false` and treats "Allow" as a denial. Its 1 s device poll only reacts to newly attached devices, so it never retries.

Symptom: after a reboot (USB permission is per attach), launching the app and tapping "Allow" leaves the screen black. Re-plugging works because the system USB chooser grants permission itself.

Workaround in `DemoFragment`: `onPause()` records whether a video-class device lacked permission (the permission dialog is an activity, so it pauses us). `onResume()` then waits 1.5 s and, if the camera is still not open but `UsbManager.hasPermission()` is now true, calls `requestPermission(device)` again. The library takes its "has permission" branch and connects. The delay leaves room for the library's own path on Android ≤ 11, where the extras do arrive, to finish first; this avoids the ERROR -99 race described above. Log line to look for: `USB permission granted but camera not opened — reconnecting`.

### "Always" option greyed out → RECORD_AUDIO

The MS2109 capture device has a USB audio interface as well as a video one. AOSP disables "Always" in the USB chooser (`ResolverActivity.setAlwaysButtonEnabled`) and hides the "Always open" checkbox in `UsbConfirmActivity` / `UsbPermissionActivity` when the app lacks `RECORD_AUDIO` and the device can capture audio. The app therefore declares `RECORD_AUDIO` and requests it together with `CAMERA`. It never records audio.

Once "Always" is ticked, the system stores the app as the default handler. On attach it grants USB permission and launches the app directly, with no dialogs (`UsbProfileGroupSettingsManager.resolveActivity`).

- The chooser ("Choose an app for the USB device") appears only when **2+ apps** match the device. With a single match, Android shows a simpler confirm dialog. Check for other installed UVC apps.
- If the system microphone privacy toggle is on, Android refuses USB access to audio-capable devices entirely.
- `android:directBootAware` was removed from `MainActivity`. Launched before unlock, it would crash on credential-encrypted `SharedPreferences`.

### "Always open" forgotten after every reboot → direct-boot-aware attach activity

USB permission grants live in memory only and are lost on reboot. The saved "Always open" default (in `/data/system/users/0/usb_device_manager.xml`) is what re-grants permission at attach time. But a camera plugged in at boot is enumerated at `PHASE_ACTIVITY_MANAGER_READY` (`UsbService.systemReady` → `UsbHostManager.monitorUsbHostBus`), **before user 0 is unlocked**. `UsbUserSettingsManager.queryIntentActivities` passes no `MATCH_DIRECT_BOOT_*` flags, so while the user is locked, only direct-boot-aware activities match. A non-aware activity gives zero matches, so `resolveActivity` returns without granting, and the app's own `requestPermission` then shows the dialog again.

Fix: `UsbAttachActivity` (direct-boot-aware, `Theme.NoDisplay`) owns the `USB_DEVICE_ATTACHED` filter. At boot it is matched as the saved default, so the system grants permission. If the user is already unlocked, it forwards the intent to `MainActivity` (`NEW_TASK | CLEAR_TOP | SINGLE_TOP`, so a running instance gets `onNewIntent`); otherwise it just finishes. The default is stored per package, so moving the filter to a new activity keeps an existing "Always" choice. Check it with `adb shell dumpsys usb` (device preferences section).

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
