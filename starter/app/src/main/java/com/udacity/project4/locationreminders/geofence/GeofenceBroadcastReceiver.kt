package com.udacity.project4.locationreminders.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.udacity.project4.utils.sendNotification

/**
 * Triggered by the Geofence.  Since we can have many Geofences at once, we pull the request
 * ID from the first Geofence, and locate it within the cached data in our Room DB
 *
 * Or users can add the reminders and then close the app, So our app has to run in the background
 * and handle the geofencing in the background.
 * To do that you can use https://developer.android.com/reference/android/support/v4/app/JobIntentService to do that.
 *
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("GeofenceReceiver", "onReceive called")

        // Pass the intent to the JobIntentService for processing
        GeofenceTransitionsJobIntentService.enqueueWork(context, intent)

        if (intent.action == ".locationreminders.geofence.GEOFENCE_TRANSITION") {
            Log.d("GeofenceReceiver", "Correct intent action received")
        } else {
            Log.d("GeofenceReceiver", "Incorrect intent action: ${intent.action}")
        }

        GeofencingEvent.fromIntent(intent)?.let { geofencingEvent ->
            if (geofencingEvent.hasError()) {
                Log.e("GeofenceReceiver", "Geofence event error: ${geofencingEvent.errorCode}")
                return
            }

            val geofenceTransition = geofencingEvent.geofenceTransition
            Log.d("GeofenceReceiver", "Geofence transition type: $geofenceTransition")

            if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
                // Handle transition
                val title = intent.getStringExtra("title") ?: "Unknown"
                val description = intent.getStringExtra("description") ?: "Unknown"
                Log.d("GeofenceReceiver", "Entered geofence: Title=$title, Description=$description")
                //sendNotification(context, title, description)
            } else {
                Log.d("GeofenceReceiver", "Unhandled geofence transition type: $geofenceTransition")
            }
        }
    }
}

