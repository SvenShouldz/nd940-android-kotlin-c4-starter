package com.udacity.project4.locationreminders.geofence

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.JobIntentService
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
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

    // Inject the ReminderDataSource using Koin
    private val remindersLocalRepository: ReminderDataSource by inject()

    companion object {
        private const val JOB_ID = 573

        fun enqueueWork(context: Context, intent: Intent) {
            Log.d("GEOFENCE", "Enqueuing job intent service")
            enqueueWork(
                context,
                GeofenceTransitionsJobIntentService::class.java,
                JOB_ID,
                intent
            )
        }
    }

    override fun onHandleWork(intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent?.hasError() == true) {
            Log.e("GEOFENCE", "Geofencing event error: ${geofencingEvent.errorCode}")
            return
        }

        val geofenceList = geofencingEvent?.triggeringGeofences
        if (geofenceList != null && geofenceList.isNotEmpty()) {
            Log.d("GEOFENCE", "Geofence transition detected: ${geofencingEvent.geofenceTransition}")
            if (geofencingEvent.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
                CoroutineScope(coroutineContext).launch(SupervisorJob()) {
                    geofenceList.forEach { geofence ->
                        val result = remindersLocalRepository.getReminder(geofence.requestId)
                        if (result is Result.Success<ReminderDTO>) {
                            val data = result.data
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
            Log.e("GEOFENCE", "No triggering geofences found")
        }
    }

    private fun sendNotification(reminderDataItem: ReminderDataItem) {
        sendNotification(
            this@GeofenceTransitionsJobIntentService,
            reminderDataItem
        )
        Log.d("GEOFENCE", "Notification sent for reminder: ${reminderDataItem.title}")
    }
}
