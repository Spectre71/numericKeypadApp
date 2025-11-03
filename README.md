# Numeric Keypad HID App for Android

A Bluetooth HID (Human Interface Device) application that turns your Android device into a wireless numeric keypad for controlling PowerPoint presentations and other applications.

## Features

- **Numeric Keys**: 0-9 for number input
- **Navigation**: Arrow Up and Arrow Down for slide navigation
- **Special Keys**: Return/Enter and Backspace
- **Bluetooth HID**: Acts as a peripheral keyboard device
- **PowerPoint Ready**: Perfect for presentation control

## Requirements

- Android 9.0 (API 28) or higher
- Bluetooth support
- Device with HID Device Profile support

## How to Use

### 1. Build and Install

**Option A: Using Android Studio (Recommended)**
1. Install Android Studio from https://developer.android.com/studio
2. Open this project folder in Android Studio
3. Android Studio will automatically download required SDK components
4. Important: Launch the app once before pairing so it can register as an HID device
5. Click the "Run" button or press Shift+F10

**Option B: Using Command Line**
```bash
# First-time setup: Install Android SDK components (see SDK_SETUP.md for details)
# Then build:
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

**Note:** If you get SDK-related errors, see `SDK_SETUP.md` for complete setup instructions.

### 2. Set Up the Connection

1. **On your Android device:**
   - Open the Numeric Keypad app
   - Grant Bluetooth permissions when prompted
   - The device will become discoverable

2. **On your computer (Windows/Mac/Linux):**
   - Open Bluetooth settings
   - Look for your Android device name
   - Pair with the device AFTER the app is open and says "HID registered"
   - If it was already paired before installing/running the app, unpair and pair again
   - Ensure the computer adds it as a Bluetooth keyboard/HID device

3. **In the app:**
   - Tap "Connect HID Device"
   - Select your computer from the paired devices list
   - Wait for connection confirmation

### 3. Use with PowerPoint

Once connected, the keypad works like a physical keyboard:
- **Arrow Up/Down**: Navigate between slides
- **Numbers 0-9**: Type numbers or use slide shortcuts
- **Return**: Confirm or start presentation
- **Backspace**: Go back or delete

## Technical Details

### HID Implementation

The app uses Android's `BluetoothHidDevice` API to implement a standard USB HID keyboard descriptor. This ensures compatibility with all major operating systems without additional drivers.

### Key Mappings

- Numpad 1-9: USB HID scan codes 0x59-0x61
- Numpad 0: USB HID scan code 0x62
- Numpad Enter: USB HID scan code 0x58
- Backspace: USB HID scan code 0x2A
- Arrow Up: USB HID scan code 0x52
- Arrow Down: USB HID scan code 0x51

Notes:
- The app now sends true Numpad (keypad) codes so hosts treat input as a numeric keypad.
- If you need top-row digits instead, we can add a toggle—open an issue or let us know.

## Troubleshooting

### Connection Issues

1. **Device not appearing**: Make sure Bluetooth is enabled and the device is discoverable
2. **Can't connect**: Unpair and re-pair the devices
3. **Keys not working**: Ensure the HID connection is established (not just Bluetooth paired)

### Permissions

The app requires the following permissions:
- `BLUETOOTH_CONNECT`: To connect to paired devices
- `BLUETOOTH_ADVERTISE`: To make device discoverable
- `BLUETOOTH_SCAN`: To scan for devices (Android 12+)

## Project Structure

```
app/
├── src/main/
│   ├── java/com/example/numerickeypad/
│   │   ├── MainActivity.kt          # Main UI and button handlers
│   │   └── HidService.kt           # Bluetooth HID implementation
│   ├── res/
│   │   └── layout/
│   │       └── activity_main.xml   # Keypad UI layout
│   └── AndroidManifest.xml         # App configuration and permissions
```

## Development

### Prerequisites

- Android Studio Hedgehog or later
- Kotlin 1.9.0+
- Gradle 8.1.1+
- Android SDK 34

### Building

```bash
./gradlew build
```

## License

This project is provided as-is for educational and personal use.

## Notes

- Minimum SDK: Android 9.0 (API 28) - required for HID Device Profile
- The app maintains a persistent connection until manually disconnected
- Battery usage is optimized for extended presentation use

## TODO
- Implement visual buttonPress feedback (COMPLETE)
- Display number input and flush on "ENT" press (COMPLETE)
- Add an elegant, nonintrusive flyout menu with rounded edges containing page entries: "Numpad", "Settings" (COMPLETE)
- Translate Menu entries and settings page
- remove button codes as debug messages (COMPLETE)