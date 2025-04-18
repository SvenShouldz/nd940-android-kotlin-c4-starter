package com.udacity.project4.utils

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.snackbar.Snackbar
import com.udacity.project4.R
import com.udacity.project4.base.BaseRecyclerViewAdapter
import com.udacity.project4.locationreminders.data.dto.ReminderDTO
import com.udacity.project4.locationreminders.savereminder.SaveReminderFragment
import com.udacity.project4.locationreminders.savereminder.SaveReminderFragment.Companion.REQUEST_LOCATION

/**
 * Extension function to setup the RecyclerView.
 */
fun <T> RecyclerView.setup(
    adapter: BaseRecyclerViewAdapter<T>
) {
    this.apply {
        layoutManager = LinearLayoutManager(this.context)
        this.adapter = adapter
    }
}

fun Fragment.setTitle(title: String) {
    if (activity is AppCompatActivity) {
        (activity as AppCompatActivity).supportActionBar?.title = title
    }
}

fun Fragment.setDisplayHomeAsUpEnabled(bool: Boolean) {
    if (activity is AppCompatActivity) {
        (activity as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(
            bool
        )
    }
}

fun Fragment.showToast(text: String, position: Int) {
    val toast = Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT)
    toast.setGravity(position, 0, 0)
    toast.show()
}

fun Fragment.showSnackbar(text: String, position: Int) {
    val snackbar = Snackbar.make(requireView(), text, Snackbar.LENGTH_SHORT)

    // Access the Snackbar view to customize its position
    val snackbarView = snackbar.view
    val params = snackbarView.layoutParams as FrameLayout.LayoutParams
    params.gravity = position
    params.marginStart = 16
    params.marginEnd = 16
    params.topMargin = 32
    snackbarView.layoutParams = params

    snackbar.show()
}

fun Fragment.showSnackbarWithOk(text: String, position: Int, action: () -> Unit) {
    val snackbar =  Snackbar.make(
        requireView(),
        text,
        Snackbar.LENGTH_INDEFINITE
    ).setAction("OK") {
        action()
    }
    val snackbarView = snackbar.view
    val params = snackbarView.layoutParams as FrameLayout.LayoutParams
    params.gravity = Gravity.TOP
    params.marginStart = 16
    params.marginEnd = 16
    params.topMargin = 32
    snackbarView.layoutParams = params
    snackbar.show()
}

fun Fragment.drawGeofenceCircle(latLng: LatLng, radius: Float?, googleMap: GoogleMap) {
    googleMap.clear()
    googleMap.addCircle(
        CircleOptions()
            .center(latLng)
            .radius(radius?.toDouble() ?: 0.0)
            .strokeColor(ContextCompat.getColor(requireContext(), R.color.colorPrimary))
            .fillColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.colorPrimaryTransparent
                )
            )
            .strokeWidth(4f)
    )
    googleMap.addMarker(MarkerOptions().position(latLng))
}

fun Fragment.requestPermission() {
    activity?.let {
        ActivityCompat.requestPermissions(
            it,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            REQUEST_LOCATION
        )
    }
}

/**
 * Animate changing the view visibility.
 */
fun View.fadeIn() {
    this.visibility = View.VISIBLE
    this.alpha = 0f
    this.animate().alpha(1f).setListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            this@fadeIn.alpha = 1f
        }
    })
}

/**
 * Animate changing the view visibility.
 */
fun View.fadeOut() {
    this.animate().alpha(0f).setListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            this@fadeOut.alpha = 1f
            this@fadeOut.visibility = View.GONE
        }
    })
}

/**
 * handle Notifications by geofence
 */
fun NotificationManager.sendReminderNotification(context: Context, reminder: ReminderDTO) {
    val intent = Intent(context, SaveReminderFragment::class.java)
    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, "reminders_channel")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(reminder.title)
        .setContentText(reminder.description)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()

    notify(reminder.id.hashCode(), notification)
}

