package com.marteck.mocklocation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat

/**
 * Foreground service that registers test providers (GPS + network) and keeps
 * pushing a fixed coordinate to them once per second.
 *
 * For testing your own app only. The app must be selected under
 * Developer Options > "Select mock location app".
 */
class MockLocationService : Service() {

    private lateinit var locationManager: LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

    private var latitude = 0.0
    private var longitude = 0.0
    private var running = false

    private val pushRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            pushLocation()
            handler.postDelayed(this, PUSH_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMocking()
            return START_NOT_STICKY
        }

        latitude = intent?.getDoubleExtra(EXTRA_LAT, latitude) ?: latitude
        longitude = intent?.getDoubleExtra(EXTRA_LNG, longitude) ?: longitude

        startInForeground()

        if (!running) {
            if (!setupProviders()) {
                lastError = getString(R.string.error_not_mock_app)
                stopSelf()
                return START_NOT_STICKY
            }
            lastError = null
            running = true
            isRunning = true
            handler.post(pushRunnable)
        }
        return START_STICKY
    }

    private fun setupProviders(): Boolean {
        return try {
            for (name in providers) {
                try {
                    locationManager.removeTestProvider(name)
                } catch (_: Exception) {
                    // not registered yet
                }
                @Suppress("DEPRECATION")
                locationManager.addTestProvider(
                    name,
                    false, // requiresNetwork
                    false, // requiresSatellite
                    false, // requiresCell
                    false, // hasMonetaryCost
                    true,  // supportsAltitude
                    true,  // supportsSpeed
                    true,  // supportsBearing
                    Criteria.POWER_LOW,
                    Criteria.ACCURACY_FINE
                )
                locationManager.setTestProviderEnabled(name, true)
            }
            true
        } catch (e: SecurityException) {
            // App is not selected as the mock location app
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    private fun pushLocation() {
        for (name in providers) {
            val loc = Location(name).apply {
                latitude = this@MockLocationService.latitude
                longitude = this@MockLocationService.longitude
                altitude = 0.0
                accuracy = 3f
                speed = 0f
                bearing = 0f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            try {
                locationManager.setTestProviderLocation(name, loc)
            } catch (e: SecurityException) {
                lastError = getString(R.string.error_not_mock_app)
                stopMocking()
                return
            } catch (_: IllegalArgumentException) {
                // provider got removed; ignore
            }
        }
    }

    private fun stopMocking() {
        running = false
        isRunning = false
        handler.removeCallbacks(pushRunnable)
        for (name in providers) {
            try {
                locationManager.setTestProviderEnabled(name, false)
                locationManager.removeTestProvider(name)
            } catch (_: Exception) {
                // already cleaned up
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (running) stopMocking()
        super.onDestroy()
    }

    private fun startInForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(String.format("%.6f, %.6f", latitude, longitude))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.marteck.mocklocation.STOP"
        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"
        private const val CHANNEL_ID = "mock_location"
        private const val NOTIF_ID = 1001
        private const val PUSH_INTERVAL_MS = 1000L

        @Volatile var isRunning = false
        @Volatile var lastError: String? = null
    }
}
