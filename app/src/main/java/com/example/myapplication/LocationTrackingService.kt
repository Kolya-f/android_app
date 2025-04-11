package com.example.myapplication

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class LocationTrackingService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val db = Firebase.firestore
    private var userName = "Гость"
    private val notificationId = 1
    private val channelId = "location_channel"

    override fun onCreate() {
        super.onCreate()
        Log.d("LocationService", "Service created")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("LocationService", "Service started")

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        userName = prefs.getString("user_name", "Гость") ?: "Гость"
        val keepTracking = prefs.getBoolean("keep_tracking", false)

        if (!keepTracking || userName.isBlank() || userName == "Гость") {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(notificationId, createNotification())
        startLocationUpdates()

        return START_STICKY
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000
        ).setMinUpdateIntervalMillis(3000).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    Log.d("LocationService", "Updating location for $userName")
                    db.collection("locations").document(userName).set(mapOf(
                        "latitude" to location.latitude,
                        "longitude" to location.longitude,
                        "timestamp" to System.currentTimeMillis()
                    ))
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d("LocationService", "Location updates started")
        } catch (e: SecurityException) {
            Log.e("LocationService", "Location permission error", e)
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("LocationService", "Service destroyed")

        stopLocationUpdates()
        removeUserData()
    }

    private fun stopLocationUpdates() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Log.d("LocationService", "Location updates stopped")
        } catch (e: Exception) {
            Log.e("LocationService", "Error stopping updates", e)
        }
    }

    private fun removeUserData() {
        if (userName.isNotBlank() && userName != "Гость") {
            db.collection("locations").document(userName).delete()
                .addOnSuccessListener {
                    Log.d("LocationService", "User data removed")
                }
                .addOnFailureListener { e ->
                    Log.e("LocationService", "Error removing user data", e)
                }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Tracking your location"
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Відстеження позиції")
            .setContentText("Оновлення для $userName")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun startService(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}