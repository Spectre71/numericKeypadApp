package com.example.numerickeypad
// test change
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors

class HidService(private val context: Context) {
    
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null
    // Track the last device we attempted to connect to and a small retry budget
    private var lastTargetDevice: BluetoothDevice? = null
    private var connectRetryCount: Int = 0
    private var connectTimeoutRunnable: Runnable? = null
    // Persist explicit user disconnect state so auto-reconnect does not occur after they choose to disconnect.
    @Volatile private var userRequestedDisconnect: Boolean = false
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var registered = false
    // Track if an auto-reconnect is pending (e.g. scheduled after a delay) so we don't spam connects
    @Volatile private var pendingAutoReconnect: Boolean = false
    // Timestamp of when app went to background (set externally by activity if desired)
    @Volatile var lastBackgroundTimestamp: Long = 0L
    // Allow a slightly more robust staged backoff for long background gaps
    private var autoReconnectAttemptCount: Int = 0

    interface Listener {
        fun onStatus(message: String)
    }
    var listener: Listener? = null
    
    companion object {
        private const val TAG = "HidService"
        
        // HID Descriptor for keyboard + mouse combo
        private val HID_REPORT_DESC = byteArrayOf(
            // Keyboard Report (Report ID 1)
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
            0x09.toByte(), 0x06.toByte(), // Usage (Keyboard)
            0xA1.toByte(), 0x01.toByte(), // Collection (Application)
            0x85.toByte(), 0x01.toByte(), //   Report ID (1)
            
            // Input report: modifier byte
            0x05.toByte(), 0x07.toByte(), //   Usage Page (Key Codes)
            0x19.toByte(), 0xE0.toByte(), //   Usage Minimum (224)
            0x29.toByte(), 0xE7.toByte(), //   Usage Maximum (231)
            0x15.toByte(), 0x00.toByte(), //   Logical Minimum (0)
            0x25.toByte(), 0x01.toByte(), //   Logical Maximum (1)
            0x75.toByte(), 0x01.toByte(), //   Report Size (1)
            0x95.toByte(), 0x08.toByte(), //   Report Count (8)
            0x81.toByte(), 0x02.toByte(), //   Input (Data, Variable, Absolute) - Modifier byte
            
            // Input report: reserved byte
            0x95.toByte(), 0x01.toByte(), //   Report Count (1)
            0x75.toByte(), 0x08.toByte(), //   Report Size (8)
            0x81.toByte(), 0x01.toByte(), //   Input (Constant) - Reserved byte
            
            // Input report: 6 key array
            0x95.toByte(), 0x06.toByte(), //   Report Count (6)
            0x75.toByte(), 0x08.toByte(), //   Report Size (8)
            0x15.toByte(), 0x00.toByte(), //   Logical Minimum (0)
            0x25.toByte(), 0x65.toByte(), //   Logical Maximum (101)
            0x05.toByte(), 0x07.toByte(), //   Usage Page (Key Codes)
            0x19.toByte(), 0x00.toByte(), //   Usage Minimum (0)
            0x29.toByte(), 0x65.toByte(), //   Usage Maximum (101)
            0x81.toByte(), 0x00.toByte(), //   Input (Data, Array) - Key array

            // Output report: LED indicators (Num Lock, Caps Lock, Scroll Lock, Compose, Kana)
            0x05.toByte(), 0x08.toByte(), //   Usage Page (LEDs)
            0x19.toByte(), 0x01.toByte(), //   Usage Minimum (1)
            0x29.toByte(), 0x05.toByte(), //   Usage Maximum (5)
            0x95.toByte(), 0x05.toByte(), //   Report Count (5)
            0x75.toByte(), 0x01.toByte(), //   Report Size (1)
            0x91.toByte(), 0x02.toByte(), //   Output (Data, Variable, Absolute)
            0x95.toByte(), 0x01.toByte(), //   Report Count (1)
            0x75.toByte(), 0x03.toByte(), //   Report Size (3)
            0x91.toByte(), 0x01.toByte(), //   Output (Constant) - padding
            
            0xC0.toByte(),                  // End Collection
            
            // Mouse Report (Report ID 2)
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
            0x09.toByte(), 0x02.toByte(), // Usage (Mouse)
            0xA1.toByte(), 0x01.toByte(), // Collection (Application)
            0x85.toByte(), 0x02.toByte(), //   Report ID (2)
            0x09.toByte(), 0x01.toByte(), //   Usage (Pointer)
            0xA1.toByte(), 0x00.toByte(), //   Collection (Physical)
            
            0x05.toByte(), 0x09.toByte(), //     Usage Page (Buttons)
            0x19.toByte(), 0x01.toByte(), //     Usage Minimum (1)
            0x29.toByte(), 0x03.toByte(), //     Usage Maximum (3)
            0x15.toByte(), 0x00.toByte(), //     Logical Minimum (0)
            0x25.toByte(), 0x01.toByte(), //     Logical Maximum (1)
            0x95.toByte(), 0x03.toByte(), //     Report Count (3) - 3 buttons
            0x75.toByte(), 0x01.toByte(), //     Report Size (1)
            0x81.toByte(), 0x02.toByte(), //     Input (Data, Variable, Absolute) - Button bits
            
            0x95.toByte(), 0x01.toByte(), //     Report Count (1)
            0x75.toByte(), 0x05.toByte(), //     Report Size (5)
            0x81.toByte(), 0x01.toByte(), //     Input (Constant) - Padding
            
            0x05.toByte(), 0x01.toByte(), //     Usage Page (Generic Desktop)
            0x09.toByte(), 0x30.toByte(), //     Usage (X)
            0x09.toByte(), 0x31.toByte(), //     Usage (Y)
            0x15.toByte(), 0x81.toByte(), //     Logical Minimum (-127)
            0x25.toByte(), 0x7F.toByte(), //     Logical Maximum (127)
            0x75.toByte(), 0x08.toByte(), //     Report Size (8)
            0x95.toByte(), 0x02.toByte(), //     Report Count (2)
            0x81.toByte(), 0x06.toByte(), //     Input (Data, Variable, Relative) - X & Y
            
            0xC0.toByte(),                  //   End Collection (Physical)
            0xC0.toByte()                   // End Collection (Application)
        )
        
        // USB HID Keyboard scan codes (subset; aligned with USB HID Usage Tables 1.11)
        const val KEY_NONE: Byte = 0x00
        const val KEY_RETURN: Byte = 0x28
        const val KEY_BACKSPACE: Byte = 0x2A
        const val KEY_UP_ARROW: Byte = 0x52
        const val KEY_DOWN_ARROW: Byte = 0x51
        const val KEY_LEFT_ARROW: Byte = 0x50
        const val KEY_os7_ARROW: Byte = 0x4F
        const val KEY_TAB: Byte = 0x2B
        const val KEY_SPACE: Byte = 0x2C
        const val KEY_ESC: Byte = 0x29
        const val KEY_CAPS_LOCK: Byte = 0x39
        
    // Top-row number keys (0-9)
    const val KEY_1: Byte = 0x1E
    const val KEY_2: Byte = 0x1F
    const val KEY_3: Byte = 0x20
    const val KEY_4: Byte = 0x21
    const val KEY_5: Byte = 0x22
    const val KEY_6: Byte = 0x23
    const val KEY_7: Byte = 0x24
    const val KEY_8: Byte = 0x25
    const val KEY_9: Byte = 0x26
    const val KEY_0: Byte = 0x27

    // Keypad (numpad) number keys and Enter
    // HID Usage IDs per USB HID Usage Tables, Usage Page 0x07
    const val KEY_NUM_LOCK: Byte = 0x53
    const val KEY_KP_ENTER: Byte = 0x58
    const val KEY_KP_1: Byte = 0x59
    const val KEY_KP_2: Byte = 0x5A
    const val KEY_KP_3: Byte = 0x5B
    const val KEY_KP_4: Byte = 0x5C
    const val KEY_KP_5: Byte = 0x5D
    const val KEY_KP_6: Byte = 0x5E
    const val KEY_KP_7: Byte = 0x5F
    const val KEY_KP_8: Byte = 0x60
    const val KEY_KP_9: Byte = 0x61
    const val KEY_KP_0: Byte = 0x62
        const val KEY_KP_DOT: Byte = 0x63
        const val KEY_KP_SLASH: Byte = 0x54
        const val KEY_KP_ASTERISK: Byte = 0x55
        const val KEY_KP_MINUS: Byte = 0x56
        const val KEY_KP_PLUS: Byte = 0x57
        
        // Mouse button constants
        const val MOUSE_BTN_NONE: Byte = 0x00
        const val MOUSE_BTN_LEFT: Byte = 0x01
        const val MOUSE_BTN_RIGHT: Byte = 0x02
        const val MOUSE_BTN_MIDDLE: Byte = 0x04

        // Modifier byte masks
        const val MOD_LCTRL: Byte = 0x01
        const val MOD_LSHIFT: Byte = 0x02
        const val MOD_LALT: Byte = 0x04
        const val MOD_LMETA: Byte = 0x08
        const val MOD_RCTRL: Byte = 0x10
        const val MOD_RSHIFT: Byte = 0x20
        const val MOD_RALT: Byte = 0x40
        const val MOD_RMETA: Byte = 0x80.toByte()
    }
    
    private fun hasBtConnectPerm(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            // On <= Android 11, classic Bluetooth permissions are install-time; treat as granted.
            true
        }
    }
    
    private val hidDeviceCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "onAppStatusChanged: registered=$registered")
            this@HidService.registered = registered
            if (registered) {
                Log.d(TAG, "HID Device registered successfully")
                listener?.onStatus("HID registered. Ready to connect.")
                // If an auto-reconnect was pending while we were unregistered, trigger it now
                if (pendingAutoReconnect) {
                    handler.postDelayed({ attemptAutoReconnect(force = true) }, 300)
                }
            } else {
                Log.d(TAG, "HID Device unregistered")
                listener?.onStatus("HID unregistered")
                connectedDevice = null
            }
        }
        
        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            Log.d(TAG, "onConnectionStateChanged: state=$state")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    // Reset retries on success
                    connectRetryCount = 0
                    // Cancel any pending connect timeout
                    connectTimeoutRunnable?.let { handler.removeCallbacks(it) }
                    // Clear manual disconnect flag – a fresh user connection re-enables auto reconnect
                    userRequestedDisconnect = false
                    val deviceName = try {
                        if (hasBtConnectPerm()) device?.name ?: "device" else "device"
                    } catch (se: SecurityException) {
                        "device"
                    }
                    Log.d(TAG, "Connected to $deviceName")
                    listener?.onStatus("Connected to $deviceName")
                    
                    // Send initial "all keys up" report to ensure clean state
                    handler.postDelayed({
                        sendClearKeyboardState()
                    }, 200)
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    val deviceName = try {
                        if (hasBtConnectPerm()) device?.name ?: "device" else "device"
                    } catch (se: SecurityException) { "device" }
                    listener?.onStatus("Connecting to $deviceName…")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedDevice = null
                    // Cancel any pending connect timeout
                    connectTimeoutRunnable?.let { handler.removeCallbacks(it) }
                    val deviceName = try {
                        if (hasBtConnectPerm()) device?.name ?: "device" else "device"
                    } catch (se: SecurityException) {
                        "device"
                    }
                    Log.d(TAG, "Disconnected from $deviceName")
                    listener?.onStatus("Disconnected")

                    // Lightweight auto-retry: some hosts reject the first attempt right after pairing
                    // Only retry if we still have a target (user didn't explicitly disconnect)
                    lastTargetDevice?.let { target ->
                        if (userRequestedDisconnect) {
                            Log.d(TAG, "Auto-reconnect suppressed: user requested disconnect")
                            return@let
                        }
                        if (connectRetryCount < 2) {
                            val backoffMs = if (connectRetryCount == 0) 1200L else 3000L
                            connectRetryCount += 1
                            Log.d(TAG, "Scheduling reconnect attempt #$connectRetryCount in ${backoffMs}ms")
                            listener?.onStatus("Reconnect attempt #$connectRetryCount in ${backoffMs / 1000}s…")
                            handler.postDelayed({
                                if (registered && hasBtConnectPerm() && lastTargetDevice != null) {
                                    try {
                                        val ok = hidDevice?.connect(target) ?: false
                                        Log.d(TAG, "auto-reconnect connect(${target.address}) -> $ok")
                                    } catch (se: SecurityException) {
                                        Log.e(TAG, "auto-reconnect SecurityException: ${se.message}")
                                    }
                                }
                            }, backoffMs)
                        } else {
                            Log.d(TAG, "Reconnect attempts exhausted")
                        }
                    }
                }
                BluetoothProfile.STATE_DISCONNECTING -> {
                    val deviceName = try {
                        if (hasBtConnectPerm()) device?.name ?: "device" else "device"
                    } catch (se: SecurityException) { "device" }
                    listener?.onStatus("Disconnecting from $deviceName…")
                }
            }
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice?) {
            // Host is logically unplugging us; clear state and notify
            Log.d(TAG, "onVirtualCableUnplug from ${try { if (hasBtConnectPerm()) device?.address else "device" } catch (_: SecurityException) { "device" }}")
            connectedDevice = null
            connectTimeoutRunnable?.let { handler.removeCallbacks(it) }
            listener?.onStatus("Virtual cable unplugged by host")
            // Attempt reconnect if user did not explicitly disconnect
            if (!userRequestedDisconnect && lastTargetDevice != null && hasBtConnectPerm()) {
                handler.postDelayed({
                    try {
                        hidDevice?.connect(lastTargetDevice)
                    } catch (se: SecurityException) {
                        Log.e(TAG, "reconnect after virtual cable unplug SecurityException: ${se.message}")
                    }
                }, 1500)
            }
        }
        
        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            Log.d(TAG, "onGetReport type=$type id=$id bufferSize=$bufferSize")
            if (device == null || hidDevice == null) return
            try {
                // Provide a minimal LED state (all off) for Output GET_REPORT requests if asked.
                // Type 2 is OUTPUT in HID (per Android constants), but we simply reply with zeros for safety.
                val empty = byteArrayOf(0x00)
                hidDevice?.replyReport(device, type, id, empty)
            } catch (se: SecurityException) {
                Log.e(TAG, "replyReport SecurityException: ${se.message}")
            }
        }

        override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
            Log.d(TAG, "onSetReport type=$type id=$id dataLen=${data?.size ?: 0}")
            // If LEDs are being set by the host, we could parse and expose this via listener/UI.
            // Bit 0: NumLock, Bit 1: CapsLock, Bit 2: ScrollLock, etc.
        }
    }
    
    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            Log.d(TAG, "Proxy connected for HID Device profile")
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as BluetoothHidDevice
                registerHidDevice()
            }
        }
        
        override fun onServiceDisconnected(profile: Int) {
            Log.d(TAG, "Proxy disconnected for HID Device profile")
            if (profile == BluetoothProfile.HID_DEVICE) {
                // Mark as not ready and try to rebind the profile proxy so we can re-register shortly.
                hidDevice = null
                registered = false
                bluetoothAdapter?.getProfileProxy(
                    context,
                    this, // rebind using this ServiceListener instance to avoid self-reference to property during init
                    BluetoothProfile.HID_DEVICE
                )
            }
        }
    }
    
    fun initialize(): Boolean {
        val btMgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = btMgr.adapter
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth not supported on this device")
            return false
        }
        
        if (!bluetoothAdapter!!.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled")
            return false
        }
        
        return bluetoothAdapter!!.getProfileProxy(
            context,
            profileListener,
            BluetoothProfile.HID_DEVICE
        )
    }
    
    private fun registerHidDevice() {
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "Numeric Keypad",
            "Android HID Numeric Keypad",
            "NumericKeypadApp",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            HID_REPORT_DESC
        )

        // Conservative QoS settings to improve compatibility
        val inQos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800, 9, 0, 11250, BluetoothHidDeviceAppQosSettings.MAX
        )
        val outQos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800, 9, 0, 11250, BluetoothHidDeviceAppQosSettings.MAX
        )

        executor.execute {
            if (!hasBtConnectPerm()) {
                Log.w(TAG, "registerApp skipped: BLUETOOTH_CONNECT permission not granted")
                return@execute
            }
            val regOk = try {
                hidDevice?.registerApp(
                    sdp,
                    inQos,
                    outQos,
                    executor,
                    hidDeviceCallback
                ) ?: false
            } catch (se: SecurityException) {
                Log.e(TAG, "registerApp SecurityException: ${se.message}")
                false
            }
            Log.d(TAG, "registerApp result: $regOk")
        }
    }

    /**
     * Ensure the HID profile is registered and ready after lifecycle changes.
     * If the proxy is missing, rebinds it; if not registered, registers the app.
     */
    fun ensureRegistered() {
        if (hidDevice == null) {
            val ok = bluetoothAdapter?.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE) ?: false
            Log.d(TAG, "ensureRegistered: getProfileProxy -> $ok")
            return
        }
        if (!registered) {
            Log.d(TAG, "ensureRegistered: re-registering HID app")
            registerHidDevice()
            // If we were waiting to auto-reconnect and HID just became registered, attempt now
            if (pendingAutoReconnect) {
                handler.postDelayed({ attemptAutoReconnect(force = true) }, 400)
            }
        }
    }
    
    fun sendKeyPress(keyCode: Byte) {
        sendKeyPressMod(0x00, keyCode)
    }

    /**
     * Send a single key press with optional modifier (e.g., SHIFT).
     * modifier: bitmask using MOD_* constants.
     */
    fun sendKeyPressMod(modifier: Byte, keyCode: Byte) {
        if (!registered) {
            Log.e(TAG, "Cannot send key: HID not registered")
            listener?.onStatus("HID not registered")
            return
        }
        if (connectedDevice == null || hidDevice == null) {
            Log.e(TAG, "Cannot send key: Not connected")
            listener?.onStatus("Not connected")
            return
        }
        
        // Explicitly verify modifier is what we expect (should be 0x00 for normal keys)
        val modValue = modifier.toInt() and 0xFF
        val keyValue = keyCode.toInt() and 0xFF
        Log.d(TAG, "sendKeyPress key=0x%02X mod=0x%02X (mod should be 0 for digits)".format(keyValue, modValue))

        // Send key press (key down) - Keyboard report payload WITHOUT Report ID
        // Layout: [modifier(1), reserved(1), keys(6)] => total 8 bytes
        val reportPress = byteArrayOf(
            modifier, // Modifier keys (MUST be 0x00 for normal digit keys)
            0x00.toByte(), // Reserved (MUST be 0x00)
            keyCode, // Key 1
            0x00.toByte(), // Key 2
            0x00.toByte(), // Key 3
            0x00.toByte(), // Key 4
            0x00.toByte(), // Key 5
            0x00.toByte()  // Key 6
        )

        // Pass Report ID as the second argument; do NOT include it in the payload
        if (!hasBtConnectPerm()) {
            Log.w(TAG, "sendKeyPress skipped: missing BLUETOOTH_CONNECT")
            return
        }
        try {
            Log.d(TAG, "Sending keyboard report ID=1 payload=[0x%02X, 0x%02X, 0x%02X, ...]".format(
                reportPress[0].toInt() and 0xFF,
                reportPress[1].toInt() and 0xFF,
                reportPress[2].toInt() and 0xFF
            ))
            hidDevice?.sendReport(connectedDevice, 1, reportPress)
        } catch (se: SecurityException) {
            Log.e(TAG, "sendReport (key press) SecurityException: ${se.message}")
            return
        }

        // Send key release (key up) after a short delay
        handler.postDelayed({
            val reportRelease = byteArrayOf(
                0x00.toByte(), // Modifier released
                0x00.toByte(), // Reserved
                0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte() // 6 empty keys
            )
            if (!hasBtConnectPerm()) return@postDelayed
            try {
                Log.d(TAG, "Sending keyboard release ID=1 (all zeros)")
                hidDevice?.sendReport(connectedDevice, 1, reportRelease)
            } catch (se: SecurityException) {
                Log.e(TAG, "sendReport (key release) SecurityException: ${se.message}")
            }
        }, 100)
    }

    fun isReady(): Boolean = registered && hidDevice != null
    
    /**
     * Send an all-keys-up report to clear any stuck modifier or key state
     */
    private fun sendClearKeyboardState() {
        if (!registered || connectedDevice == null || hidDevice == null) {
            Log.w(TAG, "sendClearKeyboardState: not ready")
            return
        }
        
        val clearReport = byteArrayOf(
            0x00, // Modifier: none
            0x00, // Reserved
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00 // All keys up
        )
        
        Log.d(TAG, "Sending clear keyboard state")
        if (!hasBtConnectPerm()) return
        try {
            hidDevice?.sendReport(connectedDevice, 1, clearReport)
        } catch (se: SecurityException) {
            Log.e(TAG, "sendClearKeyboardState SecurityException: ${se.message}")
        }
    }
    
    fun sendMouseMove(deltaX: Int, deltaY: Int) {
        if (!registered) {
            Log.e(TAG, "Cannot send mouse: HID not registered")
            return
        }
        if (connectedDevice == null || hidDevice == null) {
            Log.e(TAG, "Cannot send mouse: Not connected")
            return
        }
        
        // Clamp values to -127..127
        val x = deltaX.coerceIn(-127, 127).toByte()
        val y = deltaY.coerceIn(-127, 127).toByte()
        
        // Mouse report payload WITHOUT Report ID
        // Layout: [buttons(1), x(1), y(1)]
        val report = byteArrayOf(
            0x00, // Buttons (none pressed)
            x,    // X movement
            y     // Y movement
        )

        Log.d(TAG, "sendMouseMove id=2 btn=0x00 x=${x.toInt()} y=${y.toInt()}")

        if (!hasBtConnectPerm()) {
            Log.w(TAG, "sendMouseMove skipped: missing BLUETOOTH_CONNECT")
            return
        }
        try {
            hidDevice?.sendReport(connectedDevice, 2, report)
        } catch (se: SecurityException) {
            Log.e(TAG, "sendReport (mouse move) SecurityException: ${se.message}")
        }
    }
    
    fun sendMouseClick(button: Byte) {
        if (!registered) {
            Log.e(TAG, "Cannot send mouse click: HID not registered")
            return
        }
        if (connectedDevice == null || hidDevice == null) {
            Log.e(TAG, "Cannot send mouse click: Not connected")
            return
        }
        
        // Mouse click - button down (payload WITHOUT Report ID)
        val reportPress = byteArrayOf(
            button, // Button pressed
            0x00,   // X (no movement)
            0x00    // Y (no movement)
        )

        Log.d(TAG, "sendMouseClick id=2 button=0x%02X".format(button.toInt() and 0xFF))

        if (!hasBtConnectPerm()) {
            Log.w(TAG, "sendMouseClick skipped: missing BLUETOOTH_CONNECT")
            return
        }
        try {
            hidDevice?.sendReport(connectedDevice, 2, reportPress)
        } catch (se: SecurityException) {
            Log.e(TAG, "sendReport (mouse press) SecurityException: ${se.message}")
            return
        }

        // Button release after short delay
        handler.postDelayed({
            val reportRelease = byteArrayOf(
                0x00, // Buttons released
                0x00, // X
                0x00  // Y
            )
            if (!hasBtConnectPerm()) return@postDelayed
            try {
                hidDevice?.sendReport(connectedDevice, 2, reportRelease)
            } catch (se: SecurityException) {
                Log.e(TAG, "sendReport (mouse release) SecurityException: ${se.message}")
            }
        }, 50)
    }

    fun disconnect() {
        if (!hasBtConnectPerm()) {
            Log.w(TAG, "disconnect skipped: missing BLUETOOTH_CONNECT")
            return
        }
        // Clear target device to prevent auto-reconnect on user-initiated disconnect
        userRequestedDisconnect = true
        lastTargetDevice = null
        connectRetryCount = 0
        connectTimeoutRunnable?.let { handler.removeCallbacks(it) }
        connectedDevice?.let { device ->
            try {
                hidDevice?.disconnect(device)
            } catch (se: SecurityException) {
                Log.e(TAG, "disconnect SecurityException: ${se.message}")
            }
        }
    }
    
    fun unregister() {
        if (hasBtConnectPerm()) {
            try {
                hidDevice?.unregisterApp()
            } catch (se: SecurityException) {
                Log.e(TAG, "unregisterApp SecurityException: ${se.message}")
            }
        } else {
            Log.w(TAG, "unregisterApp skipped: missing BLUETOOTH_CONNECT")
        }
        bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        executor.shutdown()
    }
    
    fun isConnected(): Boolean {
        return connectedDevice != null
    }
    
    fun connect(device: BluetoothDevice): Boolean {
        if (!registered) {
            Log.w(TAG, "connect() called before HID registered")
            listener?.onStatus("Registering HID…")
            return false
        }
        if (!hasBtConnectPerm()) {
            Log.w(TAG, "connect skipped: missing BLUETOOTH_CONNECT")
            listener?.onStatus("Missing Bluetooth permission")
            return false
        }
        // Remember target for optional auto-retry flow
        lastTargetDevice = device
        userRequestedDisconnect = false
        connectRetryCount = 0
        val result = try {
            hidDevice?.connect(device) ?: false
        } catch (se: SecurityException) {
            Log.e(TAG, "connect SecurityException: ${se.message}")
            false
        }
        val safeId = try {
            if (hasBtConnectPerm()) device.address else "device"
        } catch (_: SecurityException) { "device" }
        Log.d(TAG, "connect($safeId) -> $result")
        if (!result) {
            listener?.onStatus("Connect request failed to start")
        } else {
            // Schedule a timeout to avoid indefinite 'Connecting…' if host never responds
            connectTimeoutRunnable?.let { handler.removeCallbacks(it) }
            connectTimeoutRunnable = Runnable {
                if (connectedDevice == null) {
                    Log.d(TAG, "Connect attempt timed out; issuing disconnect to reset")
                    listener?.onStatus("Connection timed out; retrying…")
                    try {
                        hidDevice?.disconnect(device)
                    } catch (se: SecurityException) {
                        Log.e(TAG, "disconnect after timeout SecurityException: ${se.message}")
                    }
                }
            }
            handler.postDelayed(connectTimeoutRunnable!!, 10000)
        }
        return result
    }

    /**
     * Attempt a silent auto-reconnect to the last target device if conditions permit.
     * Called from activity lifecycle (e.g., onResume) after ensureRegistered().
     */
    /**
     * Attempt a silent auto-reconnect to the last target device if conditions permit.
     * Enhancements:
     *  - Will defer if HID not yet registered (sets pending flag)
     *  - Adds staged backoff for long-background cases (>5s, >30s) to avoid host throttling
     *  - force=true ignores pendingAutoReconnect gating (used right after registration)
     */
    fun attemptAutoReconnect(force: Boolean = false) {
        if (userRequestedDisconnect) {
            Log.d(TAG, "attemptAutoReconnect: suppressed due to userRequestedDisconnect")
            return
        }
        val target = lastTargetDevice ?: run {
            Log.d(TAG, "attemptAutoReconnect: no lastTargetDevice")
            return
        }
        if (connectedDevice != null) {
            Log.d(TAG, "attemptAutoReconnect: already connected; abort")
            return
        }
        if (!hasBtConnectPerm()) {
            Log.d(TAG, "attemptAutoReconnect: missing BLUETOOTH_CONNECT permission")
            return
        }

        if (!registered) {
            // Defer until ensureRegistered finishes
            pendingAutoReconnect = true
            Log.d(TAG, "attemptAutoReconnect: HID not registered yet; deferring")
            return
        }

        if (pendingAutoReconnect && !force) {
            Log.d(TAG, "attemptAutoReconnect: already pending; skip duplicate")
            return
        }

        pendingAutoReconnect = false

        // Determine how long we've been in background to adjust backoff
        val now = System.currentTimeMillis()
        val backgroundDelta = if (lastBackgroundTimestamp > 0) now - lastBackgroundTimestamp else 0L
        val delayMs = when {
            backgroundDelta > 30_000L -> 2500L // longer pause, give host stack time
            backgroundDelta > 5_000L -> 1500L // moderate pause
            else -> 400L // quick resume
        }

        // Additional staged attempts: we schedule up to 2 follow-ups if first doesn't yield connection state change
        autoReconnectAttemptCount = 0
        fun scheduleAttempt() {
            val attemptIndex = autoReconnectAttemptCount
            autoReconnectAttemptCount += 1
            val attemptDelay = if (attemptIndex == 0) delayMs else if (attemptIndex == 1) delayMs + 2000L else delayMs + 5000L
            Log.d(TAG, "attemptAutoReconnect: scheduling attempt #${attemptIndex+1} in ${attemptDelay}ms (bgDelta=${backgroundDelta}ms)")
            handler.postDelayed({
                if (connectedDevice != null || userRequestedDisconnect || lastTargetDevice == null) {
                    Log.d(TAG, "attemptAutoReconnect: abort attempt #${attemptIndex+1} (connected or no target)")
                    return@postDelayed
                }
                try {
                    val safeAddr = try { target.address } catch (_: SecurityException) { "device" }
                    Log.d(TAG, "attemptAutoReconnect: connect(${safeAddr}) attempt #${attemptIndex+1}")
                    hidDevice?.connect(target)
                    if (attemptIndex == 0) {
                        listener?.onStatus("Reconnecting to ${try { target.name } catch (_:SecurityException){"device"}}…")
                    }
                } catch (se: SecurityException) {
                    Log.e(TAG, "attemptAutoReconnect connect SecurityException: ${se.message}")
                }
                // If still not connected and attempts remain, chain next
                if (connectedDevice == null && autoReconnectAttemptCount < 3 && !userRequestedDisconnect) {
                    scheduleAttempt()
                }
            }, attemptDelay)
        }
        scheduleAttempt()
    }
}
