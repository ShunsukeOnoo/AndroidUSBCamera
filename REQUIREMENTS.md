# USB Camera App — Requirements

## Version 0 (current target)

### Camera Display
- The app displays the live video feed from a USB camera connected via the USB host port.
- The camera feed fills the screen at its native aspect ratio.

### Image Flip
- The user can flip the camera image horizontally and/or vertically via an options menu.
- Both axes are independent — any combination of flips is valid.
- The current flip state is indicated visually in the menu (checkable items).

### Persistence
- The flip configuration is saved automatically whenever it changes.
- When the app is relaunched, the saved flip state is restored and applied before the first frame is displayed.

### Permissions
- Android requires the `CAMERA` permission for UVC camera access. The app requests this permission once on first launch; the system remembers the grant for all subsequent launches.
- USB device access is configured via `device_filter.xml` so the OS auto-routes the USB device to this app. On most Android devices the first plug-in shows a one-time "always open with this app" dialog; after that, no prompt appears. If a per-session dialog still appears on the target device, this must be suppressed at the OS level.

---

## Version 1 (future, not yet implemented)

### Multi-Camera Support
- The app can display multiple USB cameras simultaneously.

### Layout Configuration
- The user can choose a display layout. Available layouts include at least:
  - **Focus layout:** one camera shown in a large primary view, remaining cameras in smaller secondary views.
  - **Grid layout:** all cameras shown in equal-sized cells.

### Per-Camera Assignment
- The user can choose which camera feed appears in each layout slot.

### Per-Camera Flip
- Each camera slot has its own independent horizontal and vertical flip setting.

### Persistence
- All layout choices, slot-to-camera assignments, and per-camera flip states are saved and restored automatically on relaunch.

---

## Implementation Compatibility Notes

The v0 implementation is designed to be forward-compatible with v1:

- Camera display is encapsulated in a `DemoFragment` that accepts a `cameraIndex` parameter; multiple instances can run side-by-side in the same activity.
- Flip state is stored in `SharedPreferences` with keys namespaced by camera index (`flip_h_0`, `flip_v_0`, …), so per-camera settings require no schema migration.
- The activity layout uses a `FrameLayout` container that can be subdivided into multiple zones to accommodate multi-camera layouts.
