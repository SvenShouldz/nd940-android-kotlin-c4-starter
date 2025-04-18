package com.udacity.project4.locationreminders.reminderslist

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log // Import Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.udacity.project4.R
import com.udacity.project4.authentication.AuthenticationActivity
import com.udacity.project4.base.BaseFragment
import com.udacity.project4.base.NavigationCommand
import com.udacity.project4.databinding.FragmentRemindersBinding
import com.udacity.project4.locationreminders.geofence.LocationUpdateService
import com.udacity.project4.utils.setDisplayHomeAsUpEnabled
import com.udacity.project4.utils.setTitle
import com.udacity.project4.utils.setup
import com.udacity.project4.utils.showSnackbarWithOk
import org.koin.androidx.viewmodel.ext.android.viewModel


class ReminderListFragment : BaseFragment() {

    // Use Koin to retrieve the ViewModel instance
    override val _viewModel: RemindersListViewModel by viewModel()
    private lateinit var binding: FragmentRemindersBinding
    private lateinit var auth: FirebaseAuth

    companion object {
        private const val TAG = "ReminderListFragment" // For logging
    }

    // --- Permission Launchers ---

    // Launcher for notification permission (Android 13+)
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            Log.d(TAG, "Notification permission granted: $isGranted")
            checkLocationPermission()
        }

    // Launcher for fine location permission
    private val fineLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Log.d(TAG, "Fine location permission granted.")
                startLocationServiceIfPermitted() // Start the service
                checkBackgroundLocationPermission() // Continue to background check
            } else {
                Log.d(TAG, "Fine location permission denied.")
                // Handle denial - maybe show a message explaining core functionality loss
                showSnackbarWithOk(
                    "Location access is required for core features. Please enable it in settings.",
                    Gravity.TOP
                ) {}
            }
        }

    // --- Lifecycle Methods ---

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_reminders, container, false
        )
        binding.viewModel = _viewModel
        auth = Firebase.auth
        setHasOptionsMenu(true)
        setDisplayHomeAsUpEnabled(false)
        setTitle("Welcome " + auth.currentUser?.email)
        binding.refreshLayout.setOnRefreshListener { _viewModel.loadReminders() }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.lifecycleOwner = this
        setupRecyclerView()
        // Start the permission check flow when the view is created
        checkPermissionsSequentially()
        binding.addReminderFAB.setOnClickListener {
            navigateToAddReminder()
        }
    }

    override fun onResume() {
        super.onResume()
        _viewModel.loadReminders()
        startLocationServiceIfPermitted()
    }

    override fun onPause() {
        super.onPause()
        //Log.d(TAG, "Fragment paused, stopping service.")
        //sendCommandToService(LocationUpdateService.ACTION_STOP_SERVICE)
    }

    private fun startLocationServiceIfPermitted() {
        // Double-check permission before starting
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permissions seem granted, attempting to start service.")
            sendCommandToService(LocationUpdateService.ACTION_START_OR_RESUME_SERVICE)
        } else {
            Log.w(TAG, "Tried starting service, but location permissions are not granted.")
            checkPermissionsSequentially()
        }
    }

    private fun sendCommandToService(action: String) {
        Log.d(TAG, "Sending command to service: $action")
        activity?.let { // Ensure activity context is available
            val intent = Intent(it, LocationUpdateService::class.java).apply {
                this.action = action
            }
            ContextCompat.startForegroundService(it, intent)
        } ?: Log.e(TAG, "Cannot send command to service: Activity context is null")
    }

    private fun navigateToAddReminder() {
        _viewModel.navigationCommand.postValue(
            NavigationCommand.To(ReminderListFragmentDirections.toSaveReminder())
        )
    }

    private fun setupRecyclerView() {
        val adapter = RemindersListAdapter {}
        binding.reminderssRecyclerView.setup(adapter)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.main_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.logout -> logout()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun logout() {
        FirebaseAuth.getInstance().signOut()
        val intent = Intent(context, AuthenticationActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        activity?.finish()
    }

    private fun checkPermissionsSequentially() {
        // 1. Check Notification Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Requesting Notification permission...")
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Log.d(TAG, "Notification permission granted or not required.")
            checkLocationPermission()
        }
    }

    private fun checkLocationPermission() {
        // 2. Check Fine Location Permission
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineLocationGranted) {
            Log.d(TAG, "Fine Location already granted.")
            startLocationServiceIfPermitted()
            checkBackgroundLocationPermission()
        } else {
            Log.d(TAG, "Fine Location not granted, checking rationale...")
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                Log.d(TAG, "Showing rationale for Fine Location.")
                showSnackbarWithOk(
                    "Location access is required for this feature. Please allow it.",
                    Gravity.TOP
                ) {
                    Log.d(TAG, "Requesting Fine Location after rationale.")
                    fineLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            } else {
                Log.d(TAG, "Requesting Fine Location directly.")
                fineLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        //CHECK FOR FOREGROUND_SERVICE_LOCATION on Android 14+ (API 34)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.FOREGROUND_SERVICE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG,"FOREGROUND_SERVICE_LOCATION permission needed for Android 14+")
            }
        }
    }


    private fun checkBackgroundLocationPermission() {
        // 3. Check Background Location Permission (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val backgroundLocationGranted = ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!backgroundLocationGranted) {
                Log.d(TAG, "Background Location not granted, checking rationale...")
                if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                    Log.d(TAG, "Showing rationale for Background Location.")
                    showSnackbarWithOk(
                        "Background location is needed for geofencing to work even when the app is closed.",
                        Gravity.TOP
                    ) {
                        Log.d(TAG, "Requesting Background Location after rationale.")
                        requestBackgroundLocation()
                    }
                } else {
                    Log.d(TAG, "Requesting Background Location directly.")
                    requestBackgroundLocation()
                }
            } else {
                Log.d(TAG, "Background Location already granted.")
            }
        } else {
            Log.d(TAG, "Background Location permission not required for this Android version.")
        }
    }

    private fun requestBackgroundLocation() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            101
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Background Location permission granted via onRequestPermissionsResult.")
            } else {
                Log.d(TAG, "Background Location permission denied via onRequestPermissionsResult.")
                showSnackbarWithOk(
                    "Background location access was denied. Geofences might not work reliably when the app is closed.",
                    Gravity.TOP
                ) {}
            }
        }
    }
}