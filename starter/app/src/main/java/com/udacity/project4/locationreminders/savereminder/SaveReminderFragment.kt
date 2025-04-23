package com.udacity.project4.locationreminders.savereminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.udacity.project4.R
import com.udacity.project4.base.BaseFragment
import com.udacity.project4.base.NavigationCommand
import com.udacity.project4.databinding.FragmentSaveReminderBinding
import com.udacity.project4.locationreminders.geofence.LocationUpdateService
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem
import com.udacity.project4.utils.drawGeofenceCircle
import com.udacity.project4.utils.setDisplayHomeAsUpEnabled
import com.udacity.project4.utils.setTitle
import com.udacity.project4.utils.showSnackbarWithOk
import org.koin.android.ext.android.inject
import java.io.IOException
import java.util.Locale

class SaveReminderFragment : BaseFragment() {

    override val _viewModel: SaveReminderViewModel by inject()
    private lateinit var binding: FragmentSaveReminderBinding

    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    private var pendingReminder: ReminderDataItem? = null

    companion object {
        private const val TAG = "SaveReminderFragment"
        private const val BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE = 101
        private const val REQUEST_MAP_FINE_LOCATION_CODE = 1001
        private val DEFAULT_LOCATION = LatLng(52.5200, 13.4050) // Berlin as default
        private const val DEFAULT_ZOOM = 12f
        private const val SELECTED_ZOOM = 15f
    }

    // --- Permission Launchers ---

    private val notificationPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            Log.d(TAG, "Notification permission granted: $isGranted")
            if (isGranted || !isNotificationPermissionRequired()) {
                // Notification granted or not needed, proceed to Fine Location check
                checkFineLocationPermissionAndProceed()
            } else {
                // Notification permission denied, stop the save process.
                Log.w(TAG,"Notification permission denied on Android 13+. Save process halted.")
                // *** REVERTED: Removed added snackbar call here ***
                pendingReminder = null
            }
        }

    private val fineLocationPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Log.d(TAG, "Fine location permission granted.")
                // Fine location granted, now check for background permission if needed
                checkBackgroundLocationPermissionAndProceed()
            } else {
                Log.d(TAG, "Fine location permission denied.")
                pendingReminder = null
                showLocationPermissionRequiredSnackbar()
            }
        }

    // --- Location Settings Launcher ---
    private val locationSettingsLauncher: ActivityResultLauncher<IntentSenderRequest> =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Log.d(TAG, "Location settings enabled by user.")
                // Location is enabled, proceed to add geofence
                attemptToAddGeofence()
            } else {
                Log.w(TAG, "User did not enable location settings.")
                _viewModel.showSnackBarInt.postValue(R.string.location_required_error)
                pendingReminder = null // Stop the process if location is not enabled
            }
        }

    private val mapPermissionLauncher: ActivityResultLauncher<String> = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "Fine location permission granted.")
            enableMapMyLocation()
            if (_viewModel.allLocationAreNull()) {
                checkLocationSettingsAndFetchCurrentLocationForMap()
            }
        } else {
            Log.d(TAG, "Fine location permission denied (for map view).")
            setDefaultMapPosition()
            _viewModel.showSnackBarInt.postValue(R.string.permission_denied_explanation)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_save_reminder, container, false)
        binding.viewModel = _viewModel
        binding.lifecycleOwner = this

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        setupUI()
        setupMap()
        setupClickListeners()

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        displayInitialMapState()
    }

    override fun onDestroy() {
        super.onDestroy()
        _viewModel.onClear()
        Log.d(TAG,"onDestroy called.")
    }

    private fun setupUI() {
        setDisplayHomeAsUpEnabled(true)
        setTitle(getString(R.string.save_reminder))
    }

    private fun setupClickListeners() {
        binding.selectLocation.setOnClickListener { navigateToSelectLocation() }
        binding.saveReminder.setOnClickListener {
            prepareAndValidateReminder(requireContext())
        }
    }

    private fun setupMap() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFragment.getMapAsync { map ->
            googleMap = map
            configureMapUI()
            displayInitialMapState()
        }
    }

    private fun configureMapUI() {
        googleMap.uiSettings.isMyLocationButtonEnabled = false
        googleMap.uiSettings.setAllGesturesEnabled(false)
    }

    private fun displayInitialMapState() {
        if (!::googleMap.isInitialized) return

        val selectedLat = _viewModel.latitude.value
        val selectedLng = _viewModel.longitude.value

        if (selectedLat != null && selectedLng != null) {
            val position = LatLng(selectedLat, selectedLng)
            updateMapWithSelectedPosition(position, _viewModel.geofenceRadius.value)
        } else {
            checkMapFineLocationPermission()
        }
    }

    private fun checkMapFineLocationPermission() {
        if (hasFineLocationPermission()) {
            enableMapMyLocation()
            if (_viewModel.allLocationAreNull()) {
                checkLocationSettingsAndFetchCurrentLocationForMap()
            }
        } else {
            mapPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableMapMyLocation() {
        if (!::googleMap.isInitialized) return
        if (hasFineLocationPermission()) {
            googleMap.isMyLocationEnabled = true
        } else {
            Log.w(TAG, "Attempted to enable MyLocation layer without permission.")
        }
    }

    private fun updateMapWithSelectedPosition(position: LatLng, radius: Float?) {
        if (!::googleMap.isInitialized) return
        googleMap.clear()
        drawGeofenceCircle(position, radius, googleMap)
        googleMap.addMarker(MarkerOptions().position(position).title(_viewModel.reminderSelectedLocationStr.value?.takeIf { it.isNotBlank() } ?: getString(
            R.string.selected_location
        )))
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, SELECTED_ZOOM))
    }

    private fun setDefaultMapPosition() {
        if (!::googleMap.isInitialized) return
        if (_viewModel.allLocationAreNull()) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_LOCATION, DEFAULT_ZOOM))
            Log.d(TAG,"Map set to default position.")
        }
    }

    private fun checkLocationSettingsAndFetchCurrentLocationForMap() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 100L).build()
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        LocationServices.getSettingsClient(requireContext()).checkLocationSettings(builder.build())
            .addOnSuccessListener {
                Log.d(TAG, "Location settings OK. Fetching current location for map.")
                fetchCurrentLocationForMap()
            }
            .addOnFailureListener { exception ->
                Log.w(TAG, "Location settings check failed for map.", exception)
                setDefaultMapPosition()
            }
    }

    @SuppressLint("MissingPermission")
    private fun fetchCurrentLocationForMap() {
        if (!hasFineLocationPermission()) {
            Log.w(TAG, "fetchCurrentLocationForMap called without permission.")
            return
        }
        fusedLocationProviderClient.lastLocation.addOnSuccessListener { location ->
            if (location != null && _viewModel.allLocationAreNull()) {
                Log.d(TAG, "Got last known location for map: ${location.latitude}, ${location.longitude}")
                centerMapOnLocation(location)
            } else if (_viewModel.allLocationAreNull()) {
                Log.d(TAG, "Last known location null, requesting fresh location for map.")
                requestFreshLocationForMap()
            }
        }.addOnFailureListener { e->
            Log.e(TAG, "Error getting last location for map", e)
            setDefaultMapPosition()
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestFreshLocationForMap() {
        if (!hasFineLocationPermission()) {
            Log.w(TAG, "requestFreshLocationForMap called without permission.")
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                mapPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                return
            }
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 100L).build()
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                fusedLocationProviderClient.removeLocationUpdates(this)
                result.lastLocation?.let { location ->
                    if (_viewModel.allLocationAreNull()) {
                        Log.d(TAG, "Got fresh location for map: ${location.latitude}, ${location.longitude}")
                        centerMapOnLocation(location)
                    }
                } ?: run {
                    Log.w(TAG, "Fresh location result was null.")
                    setDefaultMapPosition()
                }
            }
            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) {
                    Log.w(TAG, "Location not available for fresh map request.")
                    setDefaultMapPosition()
                    fusedLocationProviderClient.removeLocationUpdates(this)
                }
            }
        }
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null)
    }


    private fun centerMapOnLocation(location: Location) {
        if (!::googleMap.isInitialized) return
        if (_viewModel.allLocationAreNull()) {
            val userLocation = LatLng(location.latitude, location.longitude)
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation, DEFAULT_ZOOM))
            Log.d(TAG,"Map centered on current location.")
        }
    }

    private fun prepareAndValidateReminder(context: Context) {
        val title = _viewModel.reminderTitle.value
        val description = _viewModel.reminderDescription.value
        val latitude = _viewModel.latitude.value
        val longitude = _viewModel.longitude.value
        val radius = _viewModel.geofenceRadius.value

        val locationString = _viewModel.reminderSelectedLocationStr.value?.takeIf { it.isNotBlank() }
            ?: getAddressFromCoordinates(latitude, longitude, context)

        val reminder = ReminderDataItem(title, description, locationString, latitude, longitude, radius)

        if (_viewModel.validateEnteredData(reminder)) {
            this.pendingReminder = reminder
            startPermissionChecks()
        } else {
            Log.d(TAG, "Reminder data validation failed.")
            this.pendingReminder = null
        }
    }

    // Step 1: Check Notification Permission (If required)
    private fun startPermissionChecks() {
        if (isNotificationPermissionRequired() && !hasNotificationPermission()) {
            Log.d(TAG, "Requesting Notification permission.")
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Log.d(TAG, "Notification permission granted or not required. Proceeding to location permissions.")
            checkFineLocationPermissionAndProceed()
        }
    }

    // Step 2: Check Fine Location Permission
    private fun checkFineLocationPermissionAndProceed() {
        if (hasFineLocationPermission()) {
            Log.d(TAG, "Fine Location permission already granted. Proceeding.")
            checkBackgroundLocationPermissionAndProceed()
        } else {
            Log.d(TAG, "Requesting Fine Location permission.")
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                showSnackbarWithOk(
                    "Location access is required for this feature. Please allow it.", // *** VERIFIED: Original String ***
                    Gravity.TOP
                ) {
                    fineLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            } else {
                fineLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    // Step 3: Check Background Location Permission (If required)
    private fun checkBackgroundLocationPermissionAndProceed() {
        if (isBackgroundLocationRequired() && !hasBackgroundLocationPermission()) {
            Log.d(TAG,"Background location permission needed and not granted. Requesting.")
            requestBackgroundLocationPermissionWithRationale() // *** VERIFIED: Uses original rationale func ***
        } else {
            Log.d(TAG, "Background location permission granted or not required. Proceeding to check location settings.")
            checkLocationSettingsAndSave()
        }
    }

    // Step 4: Check Location Settings
    private fun checkLocationSettingsAndSave() {
        if (pendingReminder == null) {
            Log.w(TAG, "checkLocationSettingsAndSave called but pendingReminder is null. Aborting.")
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 100L).build()
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client: SettingsClient = LocationServices.getSettingsClient(requireActivity())
        val task = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            Log.d(TAG, "Location settings are sufficient.")
            attemptToAddGeofence()
        }

        task.addOnFailureListener { exception ->
            Log.w(TAG, "Location settings check failed.", exception)
            if (exception is ResolvableApiException){
                try {
                    val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution.intentSender).build()
                    locationSettingsLauncher.launch(intentSenderRequest)
                } catch (sendEx: IntentSender.SendIntentException) {
                    Log.e(TAG, "Error starting location settings resolution intent", sendEx)
                    _viewModel.showSnackBarInt.postValue(R.string.location_required_error) // Keep specific error for this case
                    pendingReminder = null
                }
            } else {
                Log.e(TAG, "Location settings check failed with unresolvable exception.", exception)
                _viewModel.showSnackBarInt.postValue(R.string.location_required_error) // Keep specific error for this case
                pendingReminder = null
            }
        }
    }

    // Step 5: Attempt to Add Geofence (Final Step)
    private fun attemptToAddGeofence() {
        pendingReminder?.let { reminderToSave ->
            Log.d(TAG, "All checks passed (Permissions & Location Settings). Attempting to save reminder and add geofence.")
            startLocationServiceIfPermitted()
            _viewModel.validateAndSaveReminder(reminderToSave)
        } ?: run {
            Log.e(TAG, "attemptToAddGeofence: pendingReminder was null unexpectedly!")
            _viewModel.showSnackBarInt.postValue(R.string.error_saving_reminder)
        }
        pendingReminder = null
    }

    private fun requestBackgroundLocationPermissionWithRationale() {
        showSnackbarWithOk(
            "Background location is needed for geofencing to work even when the app is closed.",
            Gravity.TOP
        ) {
            requestBackgroundLocationPermission()
        }
    }

    private fun requestBackgroundLocationPermission() {
        if (!isBackgroundLocationRequired()) return
        requestPermissions(
            arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Background Location permission granted via onRequestPermissionsResult.")
                    checkLocationSettingsAndSave()
                } else {
                    Log.d(TAG, "Background Location permission denied via onRequestPermissionsResult.")
                    pendingReminder = null
                    requestBackgroundLocationPermissionWithRationale()
                }
            }
            REQUEST_MAP_FINE_LOCATION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Fine location permission granted via onRequestPermissionsResult (for map).")
                    enableMapMyLocation()
                    if(_viewModel.allLocationAreNull()){
                        checkLocationSettingsAndFetchCurrentLocationForMap()
                    }
                } else {
                    Log.d(TAG, "Fine location permission denied via onRequestPermissionsResult (for map).")
                    setDefaultMapPosition()
                    _viewModel.showSnackBarInt.postValue(R.string.permission_denied_explanation)
                }
            }
        }
    }

    // --- Utility and Helper Functions ---

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else { true }
    }

    private fun isNotificationPermissionRequired(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    private fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBackgroundLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else { true }
    }

    private fun isBackgroundLocationRequired(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }


    private fun showLocationPermissionRequiredSnackbar() {
        showSnackbarWithOk(
            "Location access is required for core features. Please enable it in settings.",
            Gravity.TOP
        ) {}
    }

    private fun startLocationServiceIfPermitted() {
        if (hasFineLocationPermission()) {
            Log.d(TAG, "Permissions seem granted, attempting to start service.")
            sendCommandToService()
        } else {
            Log.w(TAG, "Tried starting service, but location permissions are not granted.")
        }
    }

    private fun sendCommandToService() {
        val intent = Intent(requireContext(), LocationUpdateService::class.java).apply {
            this.action = LocationUpdateService.ACTION_START_OR_RESUME_SERVICE
        }
        try {
            ContextCompat.startForegroundService(requireContext(), intent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not start foreground service: ${e.message}")
            _viewModel.showSnackBar.postValue("Could not start location service: ${e.localizedMessage}")
        }
    }

    private fun getAddressFromCoordinates(
        latitude: Double?,
        longitude: Double?,
        context: Context?,
    ): String {
        if (latitude == null || longitude == null || context == null) {
            return getString(R.string.error_location)
        }
        val geocoder = Geocoder(context, Locale.getDefault())
        try {
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)

            if (addresses != null && addresses.isNotEmpty()) {
                val address = addresses[0]
                return address.locality ?: getString(R.string.error_location)
            } else {
                Log.w(TAG,"Geocoder returned no addresses for $latitude, $longitude")
                return getString(R.string.error_location)
            }
        } catch (e: IOException) {
            Log.e(TAG,"Geocoder IOException", e)
            return getString(R.string.error_happened)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG,"Geocoder IllegalArgumentException (invalid lat/long?)", e)
            return getString(R.string.error_location)
        }
    }

    private fun navigateToSelectLocation() {
        Log.d(TAG,"Navigating to Select Location.")
        val directions = SaveReminderFragmentDirections.actionSaveReminderFragmentToSelectLocationFragment()
        _viewModel.navigationCommand.value = NavigationCommand.To(directions)
    }
}