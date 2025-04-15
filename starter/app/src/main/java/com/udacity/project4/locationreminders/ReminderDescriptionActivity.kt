package com.udacity.project4.locationreminders

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.navigation.findNavController
import com.udacity.project4.R
import com.udacity.project4.databinding.ActivityReminderDescriptionBinding
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem
import com.udacity.project4.utils.setDisplayHomeAsUpEnabled
import com.udacity.project4.utils.setTitle

/**
 * Activity that displays the reminder details after the user clicks on the notification
 */

class ReminderDescriptionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReminderDescriptionBinding

    companion object {
        const val EXTRA_ReminderDataItem = "EXTRA_ReminderDataItem"

        // Receive the reminder object after the user clicks on the notification
        fun newIntent(context: Context, reminderDataItem: ReminderDataItem): Intent {
            val intent = Intent(context, ReminderDescriptionActivity::class.java)
            intent.putExtra(EXTRA_ReminderDataItem, reminderDataItem)
            return intent
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layoutId = R.layout.activity_reminder_description
        binding = DataBindingUtil.setContentView(this, layoutId)

        // Retrieve the ReminderDataItem from the Intent extras
        val reminderDataItem = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Use the type-safe method for Android 13 (API 33) and above
            intent?.getSerializableExtra(EXTRA_ReminderDataItem, ReminderDataItem::class.java)
        } else {
            // Use the deprecated method for older versions (requires suppress)
            @Suppress("DEPRECATION")
            intent?.getSerializableExtra(EXTRA_ReminderDataItem) as? ReminderDataItem
        }

        // Check if the data item was successfully retrieved
        if (reminderDataItem != null) {
            binding.reminderDataItem = reminderDataItem
            binding.executePendingBindings()
            supportActionBar?.title = reminderDataItem.title ?: "Reminder Details"
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        } else {
            finish()
        }

        binding.lifecycleOwner = this


    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed() // Finishes the current activity
        return true
    }
}