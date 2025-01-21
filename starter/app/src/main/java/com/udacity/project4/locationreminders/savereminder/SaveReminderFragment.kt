package com.udacity.project4.locationreminders.savereminder

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.udacity.project4.R
import com.udacity.project4.base.BaseFragment
import com.udacity.project4.base.NavigationCommand
import com.udacity.project4.databinding.FragmentSaveReminderBinding
import com.udacity.project4.locationreminders.geofence.GeofenceBroadcastReceiver
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem
import com.udacity.project4.utils.requestPermission
import com.udacity.project4.utils.setDisplayHomeAsUpEnabled
import com.udacity.project4.utils.showToast
import org.koin.android.ext.android.inject

class SaveReminderFragment : BaseFragment() {

    // Get the view model this time as a single to be shared with the another fragment
    override val _viewModel: SaveReminderViewModel by inject()
    private lateinit var binding: FragmentSaveReminderBinding

    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    // Permission launcher for requesting location permission
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            if (_viewModel.allLocationAreNull()) {
                getCurrentLocation()
            } // Permission granted
        } else {
            // Permission denied, fallback to default position
            setDefaultPosition()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val layoutId = R.layout.fragment_save_reminder
        binding = DataBindingUtil.inflate(inflater, layoutId, container, false)
        setDisplayHomeAsUpEnabled(true)
        binding.viewModel = _viewModel

        // Init FusedLocationProviderClient
        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(requireActivity())

        setupGoogleMap()

        return binding.root
    }

    // Get the user's current location and set it as the default map position
    private fun getCurrentLocation() {
        if (context?.let {
                ActivityCompat.checkSelfPermission(
                    it,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            } != PackageManager.PERMISSION_GRANTED && context?.let {
                ActivityCompat.checkSelfPermission(
                    it,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            } != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermission()
            return
        }
        fusedLocationProviderClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val userLocation = LatLng(location.latitude, location.longitude)
                _viewModel.setSelectedLocation(location.latitude, location.longitude)

                googleMap.clear() // Clear existing markers
                googleMap.addMarker(MarkerOptions().position(userLocation).title("My Location"))
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 12f))
            } else {
                // Fallback to default position
                setDefaultPosition()
            }
        }.addOnFailureListener {
            // Handle failure (e.g., location services disabled)
            setDefaultPosition()
        }
    }

    // Set a default position if the location is not available or permission is denied
    private fun setDefaultPosition() {
        val defaultPosition = LatLng(52.5200, 13.4050) // Berlin
        _viewModel.setSelectedLocation(defaultPosition.latitude, defaultPosition.longitude)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultPosition, 12f))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.lifecycleOwner = this
        binding.selectLocation.setOnClickListener {
            navigateToSelectLocation()
        }

        binding.saveReminder.setOnClickListener {
            val title = _viewModel.reminderTitle.value
            val description = _viewModel.reminderDescription
            val location = _viewModel.reminderSelectedLocationStr.value
            val latitude = _viewModel.latitude.value
            val longitude = _viewModel.longitude.value

            // add Geofence
//            if (longitude != null && latitude != null && title != null) {
//                addGeofence(title, latitude, longitude, 30F)
//            }

            // save reminder to DB
            _viewModel.validateAndSaveReminder(
                ReminderDataItem(
                    title,
                    description.toString(),
                    location,
                    latitude,
                    longitude
                )
            )
        }
    }

    private fun addGeofence(
        reminderId: String,
        latitude: Double,
        longitude: Double,
        radius: Float
    ) {
        val geofence = Geofence.Builder()
            .setRequestId(reminderId)
            .setCircularRegion(latitude, longitude, radius)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        val geofencePendingIntent: PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, GeofenceBroadcastReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val geofencingClient = context?.let { LocationServices.getGeofencingClient(it) }
        if (context?.let {
                ActivityCompat.checkSelfPermission(
                    it,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            } != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermission()
            return
        }
        geofencingClient?.addGeofences(geofencingRequest, geofencePendingIntent)
            ?.addOnFailureListener { exception ->
                showToast("Failed to add geofence: ${exception.message}", Gravity.TOP)
            }
    }

    private fun setupGoogleMap() {
        val mapFragment =
            childFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment

        mapFragment.getMapAsync { map ->
            googleMap = map

            // Disable user interactions with the map
            googleMap.uiSettings.setAllGesturesEnabled(false)

            // Check if a location has been selected in the ViewModel
            val selectedLat = _viewModel.latitude.value
            val selectedLng = _viewModel.longitude.value

            if (selectedLat != null && selectedLng != null) {
                // Show the selected location
                val selectedPosition = LatLng(selectedLat, selectedLng)
                googleMap.addMarker(
                    MarkerOptions().position(selectedPosition).title("Selected Location")
                )
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(selectedPosition, 15f))
            } else {
                // Otherwise, use the user's current location
                if (ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    getCurrentLocation()
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }
        }
        binding.selectedLocation.setOnClickListener {
            navigateToSelectLocation()
        }
    }

    private fun navigateToSelectLocation() {
        val directions = SaveReminderFragmentDirections
            .actionSaveReminderFragmentToSelectLocationFragment()
        _viewModel.navigationCommand.value = NavigationCommand.To(directions)
    }

    override fun onDestroy() {
        super.onDestroy()
        _viewModel.onClear()
    }

    companion object {
        const val REQUEST_LOCATION = 1001
    }
}