package com.udacity.project4.locationreminders.geofence

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.JobIntentService
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.udacity.project4.locationreminders.ReminderDescriptionActivity.Companion.EXTRA_ReminderDataItem
import com.udacity.project4.locationreminders.data.ReminderDataSource
import com.udacity.project4.locationreminders.data.dto.ReminderDTO
import com.udacity.project4.locationreminders.data.dto.Result
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem
import com.udacity.project4.utils.sendNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
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
        val geofencingTrigger = geofencingEvent?.triggeringGeofences
        val remindersLocalRepository: ReminderDataSource by inject()
        val geofenceTransition = geofencingEvent?.geofenceTransition

        if (geofencingEvent?.hasError() == true) {
            Log.e("GEOFENCE", "Geofencing event error: ${geofencingEvent.errorCode}")
            return
        }

        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            Log.d("GEOFENCE", "Geofence transition ENTER detected")
            CoroutineScope(coroutineContext).launch(SupervisorJob()) {
                //get the reminder with the request id
                if (geofencingTrigger != null) {
                    for (geofence in geofencingTrigger) {
                        val result = remindersLocalRepository.getReminder(geofence.requestId)
                        if (result is Result.Success<ReminderDTO>) {
                            val data = result.data
                            //send a notification to the user with the reminder details
                            sendNotification(
                                ReminderDataItem(
                                    data.title,
                                    data.description,
                                    data.location,
                                    data.latitude,
                                    data.longitude,
                                    data.geofence,
                                    data.id
                                )
                            )
                        }
                    }
                }
            }
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
