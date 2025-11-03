package com.example.numerickeypad

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Color
import android.view.WindowInsetsController
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.widget.Switch
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.provider.Settings
import android.content.SharedPreferences
import kotlin.math.roundToInt
import com.example.numerickeypad.Translator
import com.example.numerickeypad.Language
import com.google.android.material.bottomsheet.BottomSheetDialog

class MainActivity : AppCompatActivity() {

    private lateinit var hidService: HidService
    private lateinit var statusText: TextView
    private lateinit var connectButton: Button
    private lateinit var trackpadToggleButton: Button
    private lateinit var trackpadOverlay: View
    private lateinit var numLockButton: Button
    private var languageToggleButton: Button? = null
    private lateinit var menuButton: View
    
    private var bluetoothAdapter: BluetoothAdapter? = null
    
    // Trackpad state
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private val mouseSensitivity = 0.5f // Adjust for cursor speed
    private var trackpadVisible = false

    // Input buffer shown in status line while typing digits
    private val inputBuffer = StringBuilder()
    private var lastStatusMessage: String = "Not connected"

    private val REQ_PERMS_BT_PRE12 = 1001
    private val REQ_PERMS_BT_12PLUS = 1002
    private val REQ_ENABLE_BT = 1003

    companion object {
        private const val REQUEST_ENABLE_BT = 1
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Ensure no action bar is shown (defensive in case theme changes)
        supportActionBar?.hide()

        // Match system bars to app background
        val appBg = ContextCompat.getColor(this, R.color.app_background)
        window.navigationBarColor = appBg
        window.statusBarColor = appBg
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = Color.TRANSPARENT
        }
        // Ensure light icons are NOT requested on dark background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController
            controller?.setSystemBarsAppearance(
                /* appearance = */ 0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }

        // Initialize views
        statusText = findViewById(R.id.statusText)
        connectButton = findViewById(R.id.connectButton)
        trackpadToggleButton = findViewById(R.id.trackpadToggleButton)
        trackpadOverlay = findViewById(R.id.trackpadOverlay)
        numLockButton = findViewById(R.id.numLockButton)
        languageToggleButton = findViewById(R.id.languageToggleButton)
        menuButton = findViewById(R.id.menuButton)

        // Load preferred language and apply
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val savedLang = prefs.getString("lang", "EN")
        Translator.setLanguage(if (savedLang == "SL") Language.SL else Language.EN)
        updateLanguageButtonLabel()

        // Wire up UI handlers
        setupKeypadButtons()
        setupTrackpad()
        setupTrackpadToggle()
        numLockButton.setOnClickListener { sendKey(HidService.KEY_NUM_LOCK) }
        languageToggleButton?.setOnClickListener { toggleLanguage() }
        menuButton.setOnClickListener { showFlyoutMenu() }
        connectButton.setOnClickListener {
            if (isHidReady() && hidService.isConnected()) {
                hidService.disconnect()
                updateStatus("Disconnected")
            } else {
                showDeviceSelectionDialog()
            }
        }

        // Ensure permissions (pre-12: Location; 12+: Bluetooth runtime), then init HID
        ensurePermissionsThenInit()
        applyTranslations()
    }

    override fun onResume() {
        super.onResume()
        // Re-apply language preference in case it was changed in Settings
        val savedLang = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("lang", "EN")
        Translator.setLanguage(if (savedLang == "SL") Language.SL else Language.EN)
        applyTranslations()
        // Refresh connect button/status labels according to current state
        updateStatus(lastStatusMessage)
    }

    private fun showFlyoutMenu() {
        val dialog = BottomSheetDialog(this, R.style.RoundedBottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.menu_bottom_sheet, null)
        dialog.setContentView(view)
        view.findViewById<Button>(R.id.menuNumpad)?.apply {
            text = Translator.t("Numpad")
            setOnClickListener {
                dialog.dismiss()
                findViewById<android.widget.ScrollView>(R.id.scrollArea)?.smoothScrollTo(0, 0)
            }
        }
        view.findViewById<Button>(R.id.menuSettings)?.apply {
            text = Translator.t("Settings")
            setOnClickListener {
                dialog.dismiss()
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }
        dialog.show()
    }

    private fun isHidReady(): Boolean = this::hidService.isInitialized

    private fun ensurePermissionsThenInit() {
        val mgr = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = mgr.adapter

        if (bluetoothAdapter?.isEnabled != true) {
            startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQ_ENABLE_BT)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val need = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                need += Manifest.permission.BLUETOOTH_CONNECT
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                need += Manifest.permission.BLUETOOTH_SCAN
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                need += Manifest.permission.BLUETOOTH_ADVERTISE
            }
            if (need.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, need.toTypedArray(), REQ_PERMS_BT_12PLUS)
                return
            }
        } else {
            // Android 11 and below: No runtime Bluetooth permissions required for our use-case
            // (we only connect to bonded devices and do not scan), so proceed.
        }

        // All good → proceed with HID service init (existing code)
        startOrBindHidService()
    }

    private fun startOrBindHidService() {
        // Initialize HID Service
        hidService = HidService(this).also { svc ->
            svc.listener = object : HidService.Listener {
                override fun onStatus(message: String) {
                    runOnUiThread { updateStatus(message) }
                }
            }
        }

        if (hidService.initialize()) {
            updateStatus("HID Service initializing…")
            makeDeviceDiscoverable()
        }
    }

    private fun makeDeviceDiscoverable() {
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED) {
            startActivity(discoverableIntent)
            Toast.makeText(this, Translator.t("Device is now discoverable"), Toast.LENGTH_LONG).show()
        }
    }

    private fun showDeviceSelectionDialog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestBluetoothPermissions()
            return
        }

        val pairedDevices = bluetoothAdapter?.bondedDevices
        if (pairedDevices.isNullOrEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(Translator.t("No paired devices"))
                .setMessage(Translator.t("Pair your computer with this phone first in Bluetooth settings, then try again."))
                .setPositiveButton(Translator.t("Open Settings")) { _, _ ->
                    startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }
                .setNegativeButton(Translator.t("Cancel"), null)
                .show()
            return
        }

        val deviceNames = pairedDevices.map { it.name ?: "Unknown" }.toTypedArray()
        val deviceList = pairedDevices.toList()

        AlertDialog.Builder(this)
            .setTitle(Translator.t("Select Device"))
            .setItems(deviceNames) { _, which ->
                val device = deviceList[which]
                connectToDevice(device)
            }
            .setNegativeButton(Translator.t("Cancel"), null)
            .show()
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val need = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                need += Manifest.permission.BLUETOOTH_CONNECT
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                need += Manifest.permission.BLUETOOTH_SCAN
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                need += Manifest.permission.BLUETOOTH_ADVERTISE
            }
            if (need.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, need.toTypedArray(), REQ_PERMS_BT_12PLUS)
            }
        } else {
            // Pre-12: no runtime permissions required for our current flow.
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        if (hidService.connect(device)) {
            val name = if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                device.name
            } else {
                "device"
            }
            updateStatus("Connecting to $name...")
            Toast.makeText(this, Translator.t("Connecting to $name..."), Toast.LENGTH_SHORT).show()
        } else {
            updateStatus("Failed to connect")
            Toast.makeText(this, Translator.t("Failed to connect"), Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStatus(status: String) {
        runOnUiThread {
            // Remember the latest status; only show it if user is not typing
            lastStatusMessage = status
            if (inputBuffer.isEmpty()) {
                this.statusText.text = Translator.t(status)
            }
            val ready = isHidReady() && hidService.isReady()
            val connected = if (ready) hidService.isConnected() else false
            connectButton.text = Translator.t(if (connected) "Disconnect" else "Connect HID Device")
            // Disable Connect button until HID is registered to avoid confusing failures
            connectButton.isEnabled = ready || connected
        }
    }

    private fun showCurrentStatusOrInput() {
        if (inputBuffer.isEmpty()) {
            statusText.text = Translator.t(lastStatusMessage)
        } else {
            statusText.text = inputBuffer.toString()
        }
    }

    private fun setupKeypadButtons() {
        // Number keys - always use keypad (numpad) keys
        findViewById<Button>(R.id.key0).setOnClickListener { sendDigit(0) }
        findViewById<Button>(R.id.key1).setOnClickListener { sendDigit(1) }
        findViewById<Button>(R.id.key2).setOnClickListener { sendDigit(2) }
        findViewById<Button>(R.id.key3).setOnClickListener { sendDigit(3) }
        findViewById<Button>(R.id.key4).setOnClickListener { sendDigit(4) }
        findViewById<Button>(R.id.key5).setOnClickListener { sendDigit(5) }
        findViewById<Button>(R.id.key6).setOnClickListener { sendDigit(6) }
        findViewById<Button>(R.id.key7).setOnClickListener { sendDigit(7) }
        findViewById<Button>(R.id.key8).setOnClickListener { sendDigit(8) }
        findViewById<Button>(R.id.key9).setOnClickListener { sendDigit(9) }

        // Special keys
        findViewById<Button>(R.id.keyReturn).setOnClickListener {
            // Flush current input display and send Enter
            inputBuffer.setLength(0)
            showCurrentStatusOrInput()
            sendKey(HidService.KEY_KP_ENTER)
        }
        findViewById<Button>(R.id.keyBackspace).setOnClickListener {
            // Remove last digit from buffer (if any) then send Backspace
            if (inputBuffer.isNotEmpty()) {
                inputBuffer.setLength(inputBuffer.length - 1)
                showCurrentStatusOrInput()
            }
            sendKey(HidService.KEY_BACKSPACE)
        }
        findViewById<Button>(R.id.arrowUpButton).setOnClickListener { sendKey(HidService.KEY_UP_ARROW) }
        findViewById<Button>(R.id.arrowDownButton).setOnClickListener { sendKey(HidService.KEY_DOWN_ARROW) }
    }

    private var showedNumlockHint = false

    private fun sendDigit(d: Int) {
        // Always use keypad keys
        val code: Byte = when (d) {
            0 -> HidService.KEY_KP_0
            1 -> HidService.KEY_KP_1
            2 -> HidService.KEY_KP_2
            3 -> HidService.KEY_KP_3
            4 -> HidService.KEY_KP_4
            5 -> HidService.KEY_KP_5
            6 -> HidService.KEY_KP_6
            7 -> HidService.KEY_KP_7
            8 -> HidService.KEY_KP_8
            9 -> HidService.KEY_KP_9
            else -> HidService.KEY_NONE
        }
        if (!showedNumlockHint) {
            Toast.makeText(this, Translator.t("Ensure Num Lock is ON on the host!"), Toast.LENGTH_LONG).show()
            showedNumlockHint = true
        }
        // Append the pressed digit to our input buffer and reflect in the status line
        inputBuffer.append(d)
        statusText.text = inputBuffer.toString()
        sendKey(code)
    }
    
    private fun setupTrackpad() {
        val trackpad = findViewById<View>(R.id.trackpadSurface)
        val leftClick = findViewById<Button>(R.id.mouseLeftClick)
        val rightClick = findViewById<Button>(R.id.mouseRightClick)
        val closeButton = findViewById<Button>(R.id.closeTrackpadButton)
        val headerText = findViewById<TextView>(R.id.trackpadHeader)
        headerText.text = Translator.t("TRACKPAD MODE")
        
        // Trackpad touch handling for cursor movement
        trackpad.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Ensure ScrollView doesn't steal gestures
                    (v.parent as? View)?.parent?.let { (it as? View)?.parent }
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    lastTouchX = event.x
                    lastTouchY = event.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // Keep gestures within the trackpad view
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    val deltaX = ((event.x - lastTouchX) * mouseSensitivity).roundToInt()
                    val deltaY = ((event.y - lastTouchY) * mouseSensitivity).roundToInt()
                    
                    if (deltaX != 0 || deltaY != 0) {
                        hidService.sendMouseMove(deltaX, deltaY)
                    }
                    
                    lastTouchX = event.x
                    lastTouchY = event.y
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }
                else -> false
            }
        }
        
        // Mouse button clicks
        leftClick.setOnClickListener {
            if (hidService.isConnected()) {
                hidService.sendMouseClick(HidService.MOUSE_BTN_LEFT)
            } else {
                Toast.makeText(this, Translator.t("Not connected"), Toast.LENGTH_SHORT).show()
            }
        }
        
        rightClick.setOnClickListener {
            if (hidService.isConnected()) {
                hidService.sendMouseClick(HidService.MOUSE_BTN_RIGHT)
            } else {
                Toast.makeText(this, Translator.t("Not connected"), Toast.LENGTH_SHORT).show()
            }
        }

        // Close trackpad overlay
        closeButton.setOnClickListener {
            trackpadOverlay.visibility = View.GONE
            trackpadVisible = false
            setBackgroundButtonsEnabled(true)
        }
    }
    
    private fun setupTrackpadToggle() {
        trackpadToggleButton.setOnClickListener {
            trackpadVisible = !trackpadVisible
            trackpadOverlay.visibility = if (trackpadVisible) View.VISIBLE else View.GONE
            setBackgroundButtonsEnabled(!trackpadVisible)
        }
    }

    private fun setBackgroundButtonsEnabled(enabled: Boolean) {
        // Disable all keypad buttons
        findViewById<Button>(R.id.key0).isEnabled = enabled
        findViewById<Button>(R.id.key1).isEnabled = enabled
        findViewById<Button>(R.id.key2).isEnabled = enabled
        findViewById<Button>(R.id.key3).isEnabled = enabled
        findViewById<Button>(R.id.key4).isEnabled = enabled
        findViewById<Button>(R.id.key5).isEnabled = enabled
        findViewById<Button>(R.id.key6).isEnabled = enabled
        findViewById<Button>(R.id.key7).isEnabled = enabled
        findViewById<Button>(R.id.key8).isEnabled = enabled
        findViewById<Button>(R.id.key9).isEnabled = enabled
        
        // Special keys
        findViewById<Button>(R.id.keyReturn).isEnabled = enabled
        findViewById<Button>(R.id.keyBackspace).isEnabled = enabled
        findViewById<Button>(R.id.arrowUpButton).isEnabled = enabled
        findViewById<Button>(R.id.arrowDownButton).isEnabled = enabled

        // Num Lock button should follow the same enabled state so its text color dims
        numLockButton.isEnabled = enabled
        
        // Connect and trackpad toggle buttons
        connectButton.isEnabled = enabled
        trackpadToggleButton.isEnabled = enabled
    }

    private fun applyTranslations() {
        // Set translated labels for static UI
        findViewById<Button>(R.id.arrowUpButton).text = Translator.t("▲ UP")
        findViewById<Button>(R.id.arrowDownButton).text = Translator.t("▼ DOWN")
        trackpadToggleButton.text = Translator.t("TRACKPAD")
        numLockButton.text = Translator.t("NUM LOCK")
        findViewById<TextView>(R.id.trackpadHeader).text = Translator.t("TRACKPAD MODE")
        findViewById<Button>(R.id.mouseLeftClick).text = Translator.t("LEFT")
        findViewById<Button>(R.id.mouseRightClick).text = Translator.t("RIGHT")
        findViewById<Button>(R.id.closeTrackpadButton).text = Translator.t("Close Trackpad")

        // Status and Connect button will be set by updateStatus; translate current visible text
        statusText.text = Translator.t(statusText.text.toString())
        updateLanguageButtonLabel()
    }

    private fun toggleLanguage() {
        val newLang = if (Translator.getLanguage() == Language.SL) Language.EN else Language.SL
        Translator.setLanguage(newLang)
        getSharedPreferences("app_prefs", MODE_PRIVATE)
            .edit().putString("lang", if (newLang == Language.SL) "SL" else "EN").apply()
        applyTranslations()
        // Re-evaluate connect button label based on state
        updateStatus(statusText.text.toString())
    }

    private fun updateLanguageButtonLabel() {
        languageToggleButton?.text = if (Translator.getLanguage() == Language.SL) "English" else "Slovenščina"
    }

    private fun sendKey(keyCode: Byte) {
        if (isHidReady() && hidService.isConnected()) {
            hidService.sendKeyPress(keyCode)
            // Visual feedback with code for diagnostics
            // val hex = String.format("0x%02X", keyCode)
            // Toast.makeText(this, Translator.t("Sent $hex"), Toast.LENGTH_SHORT).show()
        } else {
            // val ready = if (isHidReady()) Translator.t("ready") else Translator.t("not-ready")
            // val conn = if (isHidReady() && hidService.isConnected()) Translator.t("connected") else Translator.t("disconnected")
            // Toast.makeText(this, Translator.t("Cannot send ($ready / $conn)"), Toast.LENGTH_SHORT).show()
            Toast.makeText(this, Translator.t("Cannot send, please connect device"), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        when (requestCode) {
            REQ_PERMS_BT_PRE12, REQ_PERMS_BT_12PLUS -> {
                if (granted) {
                    ensurePermissionsThenInit()
                } else {
                    Toast.makeText(this, Translator.t("Bluetooth permissions required"), Toast.LENGTH_LONG).show()
                    statusText.text = Translator.t("Permissions required to continue")
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_ENABLE_BT) {
            ensurePermissionsThenInit()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Keep HID registered across activity destruction to avoid tearing down
        // the profile when the activity is recreated or app is backgrounded.
        // The system will clean up the profile when the process ends.
        // If you need an explicit shutdown, add a menu action that calls hidService.unregister().
    }
}
