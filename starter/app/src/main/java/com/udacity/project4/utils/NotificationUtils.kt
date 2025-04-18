package com.udacity.project4.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import com.udacity.project4.BuildConfig
import com.udacity.project4.R
import com.udacity.project4.locationreminders.ReminderDescriptionActivity
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem

private const val NOTIFICATION_CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel"

// Function to send a notification
fun sendNotification(context: Context, reminderDataItem: ReminderDataItem) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Create channel for Android O+ if not already created
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
            val name = context.getString(R.string.app_name)
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                name,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for reminders"
                enableLights(true)
                enableVibration(true)
                lightColor = android.graphics.Color.BLUE
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    // Log reminder for debug
    Log.d("NotificationUtil", "Preparing notification for reminder: ${reminderDataItem.id}")

    // Intent to open the detail screen
    val intent = ReminderDescriptionActivity.newIntent(
        context.applicationContext,
        reminderDataItem
    )

    // Use TaskStackBuilder to create backstack
    val stackBuilder = TaskStackBuilder.create(context).apply {
        addParentStack(ReminderDescriptionActivity::class.java)
        addNextIntent(intent)
    }

    val notificationPendingIntent = stackBuilder.getPendingIntent(
        getUniqueId(),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Build notification
    val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_location) // Use your actual app icon
        .setContentTitle(reminderDataItem.title ?: "Reminder")
        .setContentText(reminderDataItem.description ?: "You have a reminder at ${reminderDataItem.location}")
        .setContentIntent(notificationPendingIntent)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()

    try {
        notificationManager.notify(getUniqueId(), notification)
        Log.d("NotificationUtil", "Notification sent successfully")
    } catch (e: Exception) {
        Log.e("NotificationUtil", "Failed to send notification: ${e.message}")
    }
}

private fun getUniqueId(): Int = ((System.currentTimeMillis() / 1000) % Int.MAX_VALUE).toInt()
