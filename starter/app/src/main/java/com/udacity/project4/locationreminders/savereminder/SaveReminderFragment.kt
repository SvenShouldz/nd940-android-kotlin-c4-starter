package com.udacity.project4.locationreminders.savereminder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.udacity.project4.R
import com.udacity.project4.base.BaseFragment
import com.udacity.project4.base.NavigationCommand
import com.udacity.project4.databinding.FragmentSaveReminderBinding
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem
import com.udacity.project4.utils.drawGeofenceCircle
import com.udacity.project4.utils.setDisplayHomeAsUpEnabled
import com.udacity.project4.utils.setTitle
import org.koin.android.ext.android.inject
import java.io.IOException
import java.util.Locale

class SaveReminderFragment : BaseFragment() {

    // Get the view model this time as a single to be shared with the another fragment
    override val _viewModel: SaveReminderViewModel by inject()
    private lateinit var binding: FragmentSaveReminderBinding

    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var geofencingClient: GeofencingClient

    // Permission launcher for requesting location permission
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            if (_viewModel.allLocationAreNull()) {
                checkLocationSettingsAndFetch()
            } // Permission granted
        } else {
            // Permission denied, fallback to default position
            setDefaultPosition()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_save_reminder, container, false)
        binding.viewModel = _viewModel
        binding.lifecycleOwner = this

        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(requireActivity())
        setDisplayHomeAsUpEnabled(true)
        setTitle("Save Reminder")
        setupGoogleMap()
        geofencingClient = LocationServices.getGeofencingClient(requireContext())
        binding.selectLocation.setOnClickListener { navigateToSelectLocation() }
        binding.saveReminder.setOnClickListener { saveReminder(context) }

        return binding.root
    }

    private fun setupGoogleMap() {
        (childFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment).getMapAsync { map ->
            googleMap = map
            googleMap.uiSettings.isMyLocationButtonEnabled = false
            googleMap.uiSettings.setAllGesturesEnabled(false)

            val selectedLat = _viewModel.latitude.value
            val selectedLng = _viewModel.longitude.value
            val selectedRadius = _viewModel.geofenceRadius.value

            if (selectedLat != null && selectedLng != null) {
                val selectedPosition = LatLng(selectedLat, selectedLng)
                drawGeofenceCircle(selectedPosition, selectedRadius, googleMap)
                googleMap.addMarker(
                    MarkerOptions().position(selectedPosition).title("Selected Location")
                )
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(selectedPosition, 15f))
            } else {
                if (ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    googleMap.isMyLocationEnabled = true
                    checkLocationSettingsAndFetch()
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }
        }
    }

    private fun checkLocationSettingsAndFetch() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 100L).build()
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        LocationServices.getSettingsClient(requireContext()).checkLocationSettings(builder.build())
            .addOnSuccessListener { getCurrentLocation() }
            .addOnFailureListener { setDefaultPosition() }
    }

    // Get the user's current location
    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        fusedLocationProviderClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) updateMapWithLocation(location) else requestFreshLocation()
        }.addOnFailureListener { setDefaultPosition() }
    }

    private fun requestFreshLocation() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 100L).build()
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    updateMapWithLocation(it)
                    fusedLocationProviderClient.removeLocationUpdates(this)
                }
            }
        }
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION
            )
            return
        }

        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null)
    }

    private fun updateMapWithLocation(location: Location) {
        val userLocation = LatLng(location.latitude, location.longitude)
        _viewModel.setSelectedLocation(location.latitude, location.longitude)
        googleMap.clear()
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 12f))
    }

    // Set a default position if the location is not available or permission is denied
    private fun setDefaultPosition() {
        val defaultPosition = LatLng(52.5200, 13.4050) // Berlin
        _viewModel.setSelectedLocation(defaultPosition.latitude, defaultPosition.longitude)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultPosition, 12f))
    }

    private fun saveReminder(context: Context?) {
        val reminder = ReminderDataItem(
            _viewModel.reminderTitle.value,
            _viewModel.reminderDescription.value,
            getAddressFromCoordinates(_viewModel.longitude, _viewModel.latitude, context),
            _viewModel.latitude.value,
            _viewModel.longitude.value,
            _viewModel.geofenceRadius.value
        )
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            _viewModel.validateAndSaveReminder(reminder)
            Log.d("GEOFENCE", "REMINDER SAVED")
        }
    }

    // Function to get the address from coordinates
    private fun getAddressFromCoordinates(
        latitude: MutableLiveData<Double?>,
        longitude: MutableLiveData<Double?>,
        context: Context?,
    ): String {
        val geocoder = context?.let { Geocoder(it, Locale.getDefault()) }
        try {
            val addresses = longitude.value?.let {
                latitude.value?.let { it1 ->
                    geocoder?.getFromLocation(
                        it,
                        it1, 1
                    )
                }
            }
            if (addresses != null && addresses.isNotEmpty()) {
                val address = addresses[0]
                return address.locality
            } else {
                return getString(R.string.error_location)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return getString(R.string.error_happened)
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