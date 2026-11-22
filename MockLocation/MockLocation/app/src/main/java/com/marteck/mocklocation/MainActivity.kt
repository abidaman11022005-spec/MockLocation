package com.marteck.mocklocation

import android.Manifest
import android.app.AppOpsManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var latInput: EditText
    private lateinit var lngInput: EditText
    private lateinit var statusText: TextView
    private var marker: Marker? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // osmdroid needs a user agent and a cache location
        Configuration.getInstance().apply {
            load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))
            userAgentValue = packageName
        }

        setContentView(R.layout.activity_main)

        map = findViewById(R.id.map)
        latInput = findViewById(R.id.latInput)
        lngInput = findViewById(R.id.lngInput)
        statusText = findViewById(R.id.statusText)

        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(16.0)

        // Default start point: Burj Khalifa, Dubai
        val start = GeoPoint(25.1972, 55.2744)
        map.controller.setCenter(start)
        placeMarker(start)

        // Tap the map to pick a point
        val tapOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                placeMarker(p)
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean = false
        })
        map.overlays.add(0, tapOverlay)

        findViewById<Button>(R.id.btnGo).setOnClickListener { goToTypedCoordinates() }
        findViewById<Button>(R.id.btnStart).setOnClickListener { startMocking() }
        findViewById<Button>(R.id.btnStop).setOnClickListener { stopMocking() }
        findViewById<Button>(R.id.btnDevOptions).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        requestRuntimePermissions()
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        refreshStatus()
    }

    override fun onPause() {
        map.onPause()
        super.onPause()
    }

    private fun requestRuntimePermissions() {
        val needed = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(needed.toTypedArray())
    }

    private fun placeMarker(point: GeoPoint) {
        marker?.let { map.overlays.remove(it) }
        marker = Marker(map).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = String.format("%.6f, %.6f", point.latitude, point.longitude)
        }
        map.overlays.add(marker)
        map.invalidate()

        latInput.setText(String.format("%.6f", point.latitude))
        lngInput.setText(String.format("%.6f", point.longitude))
    }

    private fun parseTypedPoint(): GeoPoint? {
        val lat = latInput.text.toString().trim().toDoubleOrNull()
        val lng = lngInput.text.toString().trim().toDoubleOrNull()
        if (lat == null || lng == null || lat !in -90.0..90.0 || lng !in -180.0..180.0) {
            Toast.makeText(this, R.string.error_bad_coords, Toast.LENGTH_SHORT).show()
            return null
        }
        return GeoPoint(lat, lng)
    }

    private fun goToTypedCoordinates() {
        val p = parseTypedPoint() ?: return
        placeMarker(p)
        map.controller.animateTo(p)
    }

    private fun startMocking() {
        val p = parseTypedPoint() ?: return
        if (!isMockAppEnabled()) {
            refreshStatus()
            Toast.makeText(this, R.string.error_not_mock_app, Toast.LENGTH_LONG).show()
            return
        }
        placeMarker(p)
        val intent = Intent(this, MockLocationService::class.java)
            .putExtra(MockLocationService.EXTRA_LAT, p.latitude)
            .putExtra(MockLocationService.EXTRA_LNG, p.longitude)
        ContextCompat.startForegroundService(this, intent)
        statusText.postDelayed({ refreshStatus() }, 700)
    }

    private fun stopMocking() {
        startService(
            Intent(this, MockLocationService::class.java).setAction(MockLocationService.ACTION_STOP)
        )
        statusText.postDelayed({ refreshStatus() }, 500)
    }

    /** True when this app is selected as the mock location app in Developer Options. */
    @Suppress("DEPRECATION")
    private fun isMockAppEnabled(): Boolean {
        return try {
            val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOp(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), packageName)
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    private fun refreshStatus() {
        statusText.text = when {
            MockLocationService.isRunning -> getString(R.string.status_running)
            !isMockAppEnabled() -> getString(R.string.error_not_mock_app)
            MockLocationService.lastError != null -> MockLocationService.lastError
            else -> getString(R.string.status_ready)
        }
    }
}
