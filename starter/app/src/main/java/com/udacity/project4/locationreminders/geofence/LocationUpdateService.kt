package com.udacity.project4.locationreminders.geofence

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.MarkerOptions
import com.udacity.project4.R


class LocationUpdateService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest

    private val NOTIFICATION_CHANNEL_ID = "location_service_channel"
    private val NOTIFICATION_ID = 12345

    companion object {
        private const val TAG = "LocationUpdateService"
        const val ACTION_START_OR_RESUME_SERVICE = "ACTION_START_OR_RESUME_SERVICE"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L) // 10 seconds
                .setMinUpdateIntervalMillis(5000L) // 5 seconds
                .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult ?: return
                for (location in locationResult.locations) {
                    // THIS LINE ALREADY LOGS THE LOCATION!
                    Log.d(TAG, "New Location: ${location.latitude}, ${location.longitude}")
                }
            }
        }

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_START_OR_RESUME_SERVICE -> {
                    Log.d(TAG, "Starting or Resuming Service")
                    startForegroundService()
                    requestLocationUpdates()
                }

                ACTION_STOP_SERVICE -> {
                    Log.d(TAG, "Stopping Service")
                    stopLocationUpdates()
                    stopForeground(STOP_FOREGROUND_REMOVE) // Use STOP_FOREGROUND_DETACH to keep notification if needed
                    stopSelf()
                }

                else -> {} // Handle other actions if needed
            }
        }
        // If the service is killed, restart it
        return START_STICKY
    }

    private fun startForegroundService() {
        // Example Notification - customize as needed
        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setAutoCancel(false)
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_location) // Replace with your app's icon
            .setContentTitle("Location Service Active")
            .setContentText("Tracking your location for reminders")
            .setPriority(NotificationCompat.PRIORITY_LOW) // Adjust priority as needed
            .setCategory(Notification.CATEGORY_SERVICE)

        startForeground(NOTIFICATION_ID, notificationBuilder.build())
        Log.d(TAG, "Foreground service started")
    }

    private fun requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Location permissions not granted. Cannot request updates.")
            // Permissions should be checked/requested by the Activity *before* starting the service.
            // Stop the service if permissions are missing here.
            stopSelf()
            return
        }
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper() // Use background looper if processing is heavy
            )
            Log.d(TAG, "Location updates requested")
        } catch (unlikely: SecurityException) {
            Log.e(TAG, "Lost location permission. Could not request updates.", unlikely)
            stopSelf()
        }
    }

    private fun stopLocationUpdates() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Log.d(TAG, "Location updates stopped")
        } catch (ex: Exception) {
            Log.e(TAG, "Error stopping location updates", ex)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Return null if clients cannot bind to the service
        // Or implement a Binder for communication (e.g., getting current status)
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Location Service Channel",
                NotificationManager.IMPORTANCE_LOW // Adjust importance
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

}