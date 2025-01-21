package com.udacity.project4.locationreminders.savereminder.selectreminderlocation

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.udacity.project4.R
import com.udacity.project4.base.BaseFragment
import com.udacity.project4.base.NavigationCommand
import com.udacity.project4.databinding.FragmentSelectLocationBinding
import com.udacity.project4.locationreminders.savereminder.SaveReminderFragment.Companion.REQUEST_LOCATION
import com.udacity.project4.locationreminders.savereminder.SaveReminderViewModel
import com.udacity.project4.utils.setDisplayHomeAsUpEnabled
import com.udacity.project4.utils.showToast
import org.koin.android.ext.android.inject

class SelectLocationFragment : BaseFragment() {

    // Use Koin to get the view model of the SaveReminder
    override val _viewModel: SaveReminderViewModel by inject()
    private lateinit var binding: FragmentSelectLocationBinding

    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private var selectedPosition: LatLng? = null
    private var geofenceRadius: Float = 30f

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val layoutId = R.layout.fragment_select_location
        binding = DataBindingUtil.inflate(inflater, layoutId, container, false)

        binding.viewModel = _viewModel
        binding.lifecycleOwner = this

        setHasOptionsMenu(true)
        setDisplayHomeAsUpEnabled(true)

        setupGoogleMaps()
        // TODO: add style to the map
        // TODO: put a marker to location that the user selected

        // TODO: call this function after the user confirms on the selected location
        // Handle slider for radius
        binding.radiusSlider.addOnChangeListener { _, value, _ ->
            geofenceRadius = value
            selectedPosition?.let { latLng ->
                drawGeofenceCircle(latLng, geofenceRadius)
            }
        }

        // Handle confirm button
        binding.confirmButton.setOnClickListener {
            selectedPosition?.let { latLng ->
                onLocationSelected(latLng)
            } ?: showToast("Please select a location first.", Gravity.TOP)
        }
        return binding.root
    }

    private fun setupGoogleMaps() {
        val mapFragment =
            childFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment

        mapFragment.getMapAsync { map ->
            googleMap = map

            // Show the saved marker if available
            selectedPosition?.let {
                googleMap.addMarker(MarkerOptions().position(it))
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 15f))
            }
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
            }

            // Add OnMapClickListener to handle user clicks on the map
            googleMap.setOnMapClickListener { latLng ->
                // Update the selected position
                selectedPosition = latLng

                // Clear existing markers and add a new one
                googleMap.clear()
                googleMap.addMarker(
                    MarkerOptions()
                        .position(latLng)
                        .title("Selected Location")
                )
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))

                // Optionally show a toast or update the view model with the new location
                showToast(
                    "Location selected: (${latLng.latitude}, ${latLng.longitude})",
                    Gravity.TOP
                )
            }
        }
    }

    private fun drawGeofenceCircle(latLng: LatLng, radius: Float) {
        googleMap.clear()
        googleMap.addCircle(
            CircleOptions()
                .center(latLng)
                .radius(radius.toDouble())
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

    private fun onLocationSelected(latLng: LatLng) {
        _viewModel.latitude.value = latLng.latitude
        _viewModel.longitude.value = latLng.longitude
        _viewModel.reminderSelectedLocationStr.value =
            "Lat: ${latLng.latitude}, Lng: ${latLng.longitude}"
        _viewModel.navigationCommand.value = NavigationCommand.Back
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.map_options, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {

            R.id.normal_map -> {
                // Set the map type to Normal
                googleMap.mapType = GoogleMap.MAP_TYPE_NORMAL
                true
            }

            R.id.hybrid_map -> {
                // Set the map type to Hybrid
                googleMap.mapType = GoogleMap.MAP_TYPE_HYBRID
                true
            }

            R.id.satellite_map -> {
                // Set the map type to Satellite
                googleMap.mapType = GoogleMap.MAP_TYPE_SATELLITE
                true
            }

            R.id.terrain_map -> {
                // Set the map type to Terrain
                googleMap.mapType = GoogleMap.MAP_TYPE_TERRAIN
                true
            }

            else -> super.onOptionsItemSelected(item)

        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_LOCATION && resultCode == RESULT_OK) {
            val lat = data?.getDoubleExtra("latitude", 0.0) ?: return
            val lng = data.getDoubleExtra("longitude", 0.0)
            selectedPosition = LatLng(lat, lng)

            googleMap.clear()
            googleMap.addMarker(MarkerOptions().position(selectedPosition!!))
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(selectedPosition!!, 15f))
        }
    }

}