package com.prototype.upnpradio

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.*

/**
 * MainActivity — UPnP Radio Prototype
 *
 * Standalone prototype to demonstrate:
 * 1. SSDP Discovery of UPnP MediaRenderer devices
 * 2. SOAP control (SetAVTransportURI + Play + Stop)
 * 3. Watchdog monitoring
 *
 * Hardcoded radio: https://stream.regenbogen2.de/exclassicrock/mp3-128/
 */
class MainActivity : AppCompatActivity(), SsdpDiscovery.Listener, UpnpController.Listener {

    // UI elements
    private lateinit var btnDiscover: MaterialButton
    private lateinit var btnPlay: MaterialButton
    private lateinit var btnStop: MaterialButton
    private lateinit var btnClearLog: MaterialButton
    private lateinit var btnCopyLog: MaterialButton
    private lateinit var tvDiscoveryStatus: TextView
    private lateinit var tvSelectedDevice: TextView
    private lateinit var tvPlaybackStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var tvNoDevices: TextView
    private lateinit var logScrollView: ScrollView
    private lateinit var deviceListContainer: LinearLayout
    private lateinit var controlSection: LinearLayout

    // Core components
    private lateinit var ssdpDiscovery: SsdpDiscovery
    private lateinit var upnpController: UpnpController
    private lateinit var streamProxy: StreamProxy
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // State
    private val discoveredDevices = mutableListOf<SsdpDiscovery.DiscoveredDevice>()
    private var selectedDevice: SsdpDiscovery.DiscoveredDevice? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind views
        btnDiscover = findViewById(R.id.btnDiscover)
        btnPlay = findViewById(R.id.btnPlay)
        btnStop = findViewById(R.id.btnStop)
        btnClearLog = findViewById(R.id.btnClearLog)
        btnCopyLog = findViewById(R.id.btnCopyLog)
        tvDiscoveryStatus = findViewById(R.id.tvDiscoveryStatus)
        tvSelectedDevice = findViewById(R.id.tvSelectedDevice)
        tvPlaybackStatus = findViewById(R.id.tvPlaybackStatus)
        tvLog = findViewById(R.id.tvLog)
        tvNoDevices = findViewById(R.id.tvNoDevices)
        logScrollView = findViewById(R.id.logScrollView)
        deviceListContainer = findViewById(R.id.deviceListContainer)
        controlSection = findViewById(R.id.controlSection)

        // Initialize components
        ssdpDiscovery = SsdpDiscovery(this, this)
        upnpController = UpnpController(this, this)
        streamProxy =
                StreamProxy(this).also {
                    it.onLog = { msg -> runOnUiThread { logMessage(msg) } }
                    it.start()
                }

        // Acquire multicast lock (required for SSDP on Android)
        acquireMulticastLock()

        // Setup button listeners
        btnDiscover.setOnClickListener {
            discoveredDevices.clear()
            deviceListContainer.removeAllViews()
            selectedDevice = null
            controlSection.visibility = View.GONE
            onSearchStarted()
            ssdpDiscovery.startDiscovery(scope)
        }

        btnPlay.setOnClickListener {
            selectedDevice?.let { device -> upnpController.play(device, scope, streamProxy) }
                    ?: Toast.makeText(this, "Aucun appareil sélectionné", Toast.LENGTH_SHORT).show()
        }

        btnStop.setOnClickListener {
            selectedDevice?.let { device -> upnpController.stop(device, scope) }
        }

        btnClearLog.setOnClickListener { tvLog.text = "" }

        btnCopyLog.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("UPnP Log", tvLog.text.toString())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Log copié !", Toast.LENGTH_SHORT).show()
        }

        // Set initial states
        btnPlay.isEnabled = false
        btnStop.isEnabled = false

        logMessage("🚀 UPnP Radio Prototype prêt")
        logMessage("📻 Station: ${UpnpController.RADIO_NAME}")
        logMessage("🔗 URL: ${UpnpController.RADIO_URL}")
        logMessage("")
        logMessage("👉 Appuyez sur 'Rechercher' pour trouver votre Sangean")
    }

    override fun onDestroy() {
        super.onDestroy()
        ssdpDiscovery.stopDiscovery()
        upnpController.release()
        streamProxy.stop()
        releaseMulticastLock()
        scope.cancel()
    }

    // ══════════════════════════════════════════════════
    // SsdpDiscovery.Listener implementation
    // ══════════════════════════════════════════════════

    override fun onDeviceFound(device: SsdpDiscovery.DiscoveredDevice) {
        runOnUiThread {
            // Avoid duplicates
            if (discoveredDevices.any { it.uuid == device.uuid }) return@runOnUiThread

            discoveredDevices.add(device)
            tvNoDevices.visibility = View.GONE
            addDeviceToUI(device)
        }
    }

    fun onSearchStarted() {
        runOnUiThread {
            tvDiscoveryStatus.text = "● Recherche..."
            tvDiscoveryStatus.setTextColor(Color.parseColor("#F39C12"))
            btnDiscover.isEnabled = false
            btnDiscover.text = "🔄  Recherche en cours..."
        }
    }

    override fun onDiscoveryFinished() {
        runOnUiThread {
            btnDiscover.isEnabled = true
            btnDiscover.text = "🔍  Rechercher les appareils"

            if (discoveredDevices.isEmpty()) {
                tvDiscoveryStatus.text = "● Aucun appareil"
                tvDiscoveryStatus.setTextColor(Color.parseColor("#E74C3C"))
                tvNoDevices.visibility = View.VISIBLE
            } else {
                tvDiscoveryStatus.text = "● ${discoveredDevices.size} appareil(s)"
                tvDiscoveryStatus.setTextColor(Color.parseColor("#1DB954"))
            }
        }
    }

    fun onError(message: String) {
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    }

    // ══════════════════════════════════════════════════
    // UpnpController.Listener implementation
    // ══════════════════════════════════════════════════

    override fun onPlaybackStarted() {
        runOnUiThread {
            tvPlaybackStatus.text = "État : 🔊 Lecture en cours"
            tvPlaybackStatus.setTextColor(Color.parseColor("#1DB954"))
            btnPlay.isEnabled = false
            btnStop.isEnabled = true
        }
    }

    override fun onPlaybackStopped() {
        runOnUiThread {
            tvPlaybackStatus.text = "État : ⏹ Arrêté"
            tvPlaybackStatus.setTextColor(Color.parseColor("#B3B3B3"))
            btnPlay.isEnabled = true
            btnStop.isEnabled = false
        }
    }

    override fun onPlaybackError(message: String) {
        runOnUiThread {
            tvPlaybackStatus.text = "État : ❌ Erreur - $message"
            tvPlaybackStatus.setTextColor(Color.parseColor("#E74C3C"))
            btnPlay.isEnabled = true
            btnStop.isEnabled = false
            Toast.makeText(this, "Erreur: $message", Toast.LENGTH_LONG).show()
        }
    }

    override fun onTransportState(state: String) {
        runOnUiThread {
            if (state == "PLAYING") {
                tvPlaybackStatus.text = "État : 🔊 $state"
                tvPlaybackStatus.setTextColor(Color.parseColor("#1DB954"))
            }
        }
    }

    // ══════════════════════════════════════════════════
    // Common: onLog (from both Discovery and Controller)
    // ══════════════════════════════════════════════════

    override fun onLog(message: String) {
        runOnUiThread { logMessage(message) }
    }

    // ══════════════════════════════════════════════════
    // UI Helpers
    // ══════════════════════════════════════════════════

    private fun logMessage(message: String) {
        val color =
                when {
                    message.startsWith("✅") -> "#1DB954"
                    message.startsWith("❌") -> "#E74C3C"
                    message.startsWith("⚠️") -> "#F39C12"
                    message.startsWith("📤") -> "#3498DB"
                    message.startsWith("📥") -> "#9B59B6"
                    message.startsWith("🐕") -> "#E67E22"
                    message.startsWith("━") -> "#555555"
                    message.startsWith("   ") -> "#888888"
                    else -> "#00FF00"
                }

        val spannable = SpannableStringBuilder("$message\n")
        spannable.setSpan(
                ForegroundColorSpan(Color.parseColor(color)),
                0,
                spannable.length,
                SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        tvLog.append(spannable)

        // Auto-scroll to bottom
        logScrollView.post { logScrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    /** Add a discovered device as a clickable card to the device list. */
    private fun addDeviceToUI(device: SsdpDiscovery.DiscoveredDevice) {
        val deviceView =
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundResource(R.drawable.device_item_background)
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    layoutParams =
                            LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.MATCH_PARENT,
                                            LinearLayout.LayoutParams.WRAP_CONTENT
                                    )
                                    .apply { bottomMargin = dp(8) }

                    // Device name
                    addView(
                            TextView(this@MainActivity).apply {
                                text = "📡 ${device.friendlyName}"
                                setTextColor(Color.WHITE)
                                textSize = 15f
                                setTypeface(null, Typeface.BOLD)
                            }
                    )

                    // Device IP
                    addView(
                            TextView(this@MainActivity).apply {
                                text =
                                        "IP: ${device.remoteIp}  •  UUID: ${device.uuid.takeLast(12)}"
                                setTextColor(Color.parseColor("#999999"))
                                textSize = 11f
                                setPadding(0, dp(2), 0, 0)
                            }
                    )

                    // Control URL
                    addView(
                            TextView(this@MainActivity).apply {
                                text = "Control: ${device.avTransportControlUrl}"
                                setTextColor(Color.parseColor("#777777"))
                                textSize = 10f
                                setPadding(0, dp(2), 0, 0)
                                maxLines = 1
                            }
                    )

                    // Click handler — select this device
                    setOnClickListener { selectDevice(device) }
                }

        deviceListContainer.addView(deviceView)
    }

    /** Select a device for playback control. */
    private fun selectDevice(device: SsdpDiscovery.DiscoveredDevice) {
        selectedDevice = device

        // Update visual selection
        for (i in 0 until deviceListContainer.childCount) {
            val child = deviceListContainer.getChildAt(i)
            val childDevice = discoveredDevices.getOrNull(i)
            if (childDevice?.uuid == device.uuid) {
                child.setBackgroundResource(R.drawable.device_item_selected)
            } else {
                child.setBackgroundResource(R.drawable.device_item_background)
            }
        }

        // Show control section
        controlSection.visibility = View.VISIBLE
        tvSelectedDevice.text = "Appareil : 📡 ${device.friendlyName}"
        tvPlaybackStatus.text = "État : Prêt"
        tvPlaybackStatus.setTextColor(Color.parseColor("#B3B3B3"))
        btnPlay.isEnabled = true
        btnStop.isEnabled = false

        logMessage("")
        logMessage("✅ Appareil sélectionné: ${device.friendlyName}")
        logMessage("   IP: ${device.remoteIp}")
        logMessage("   Control: ${device.avTransportControlUrl}")

        Toast.makeText(this, "Sélectionné: ${device.friendlyName}", Toast.LENGTH_SHORT).show()
    }

    /** Acquire WiFi Multicast lock. Required on Android to receive multicast packets (SSDP). */
    private fun acquireMulticastLock() {
        try {
            val wifiManager =
                    applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock =
                    wifiManager.createMulticastLock("upnp_prototype").apply {
                        setReferenceCounted(false)
                        acquire()
                    }
            logMessage("🔓 Multicast lock acquis")
        } catch (e: Exception) {
            logMessage("⚠️ Échec acquisition multicast lock: ${e.message}")
        }
    }

    private fun releaseMulticastLock() {
        multicastLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        value.toFloat(),
                        resources.displayMetrics
                )
                .toInt()
    }
}
