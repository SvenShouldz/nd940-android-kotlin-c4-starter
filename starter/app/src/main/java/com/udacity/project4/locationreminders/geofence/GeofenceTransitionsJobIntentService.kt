package com.udacity.project4.locationreminders.geofence

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.JobIntentService
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem
import com.udacity.project4.utils.sendNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext

class GeofenceTransitionsJobIntentService : JobIntentService(), CoroutineScope {

    private var coroutineJob: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + coroutineJob

    companion object {
        private const val JOB_ID = 573

        /**
         * Call this to start the JobIntentService to handle the geofencing transition events.
         */
        fun enqueueWork(context: Context, intent: Intent) {
            enqueueWork(
                context,
                GeofenceTransitionsJobIntentService::class.java,
                JOB_ID,
                intent
            )
        }
    }

    override fun onHandleWork(intent: Intent) {
        // Parse the geofencing event from the intent
        val geofencingEvent = GeofencingEvent.fromIntent(intent)

        if (geofencingEvent?.hasError() == true) {
            Log.e("GEOFENCE", "Geofencing event error: ${geofencingEvent.errorCode}")
            return
        }

        val geofenceTransition = geofencingEvent?.geofenceTransition
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            Log.d("GEOFENCE", "Geofence transition ENTER detected")

            // Extract the ReminderDataItem directly from the intent
//            val reminderDataItem =
//                intent.getSerializableExtra("EXTRA_ReminderDataItem") as? ReminderDataItem
//            if (reminderDataItem != null) {
//                sendNotification(reminderDataItem)
//            } else {
//                Log.e("GEOFENCE", "ReminderDataItem not found in intent extras")
//            }
        } else {
            Log.d("GEOFENCE", "Unhandled geofence transition type: $geofenceTransition")
        }
    }

    private fun sendNotification(reminderDataItem: ReminderDataItem) {
        // Directly send the notification using the ReminderDataItem
        sendNotification(
            this@GeofenceTransitionsJobIntentService,
            reminderDataItem
        )
        Log.d("GEOFENCE", "Notification sent for reminder: ${reminderDataItem.title}")
    }

}
