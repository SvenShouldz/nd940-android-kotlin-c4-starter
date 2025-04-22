package com.udacity.project4.locationreminders.savereminder.selectreminderlocation

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Resources.NotFoundException
import android.location.Geocoder
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
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.udacity.project4.R
import com.udacity.project4.base.BaseFragment
import com.udacity.project4.base.NavigationCommand
import com.udacity.project4.databinding.FragmentSelectLocationBinding
import com.udacity.project4.locationreminders.savereminder.SaveReminderViewModel
import com.udacity.project4.utils.drawGeofenceCircle
import com.udacity.project4.utils.setDisplayHomeAsUpEnabled
import com.udacity.project4.utils.setTitle
import com.udacity.project4.utils.showSnackbar
import com.udacity.project4.utils.showToast
import org.koin.android.ext.android.inject
import java.util.Locale

class SelectLocationFragment : BaseFragment() {

    // Use Koin to get the view model of the SaveReminder
    override val _viewModel: SaveReminderViewModel by inject()
    private lateinit var binding: FragmentSelectLocationBinding

    private lateinit var googleMap: GoogleMap
    private var selectedPosition: LatLng? = null
    private var geofenceRadius: Float = 30f
    private var poiTitle: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val layoutId = R.layout.fragment_select_location
        binding = DataBindingUtil.inflate(inflater, layoutId, container, false)

        binding.viewModel = _viewModel
        binding.lifecycleOwner = this

        setHasOptionsMenu(true)
        setDisplayHomeAsUpEnabled(true)
        setTitle("Select your Location")
        setupGoogleMap()


        // Handle slider for radius
        binding.radiusSlider.addOnChangeListener { _, value, _ ->
            geofenceRadius = value
            selectedPosition?.let { latLng ->
                drawGeofenceCircle(latLng, geofenceRadius, googleMap)
            }
        }

        // Handle confirm button
        binding.confirmButton.setOnClickListener {
            selectedPosition?.let { latLng ->
                onLocationSelected(latLng, geofenceRadius)
            } ?: showToast("Please select a location first.", Gravity.TOP)
        }
        return binding.root
    }

    private fun setupGoogleMap() {
        val mapFragment =
            childFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment

        mapFragment.getMapAsync { map ->
            googleMap = map

            val savedLat = _viewModel.latitude.value
            val savedLng = _viewModel.longitude.value

            if (savedLat != null && savedLng != null) {
                val savedPosition = LatLng(savedLat, savedLng)
                // Draw existing selection if available
                selectedPosition = savedPosition
                addMarkerAndMoveCamera(savedPosition, _viewModel.reminderSelectedLocationStr.value ?: "Saved Location")
                drawGeofenceCircle(savedPosition, _viewModel.geofenceRadius.value ?: geofenceRadius, googleMap)
                // Move camera without animation initially
                // googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(savedPosition, 15f))
            } else {
                // Move camera to default or user location if no saved position
                // Keep existing MyLocation logic below
            }


            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                googleMap.isMyLocationEnabled = true
            }

            googleMap.setOnMapClickListener { latLng ->
                selectedPosition = latLng
                googleMap.clear()
                addMarkerAndMoveCamera(latLng, "Selected Location")
                drawGeofenceCircle(latLng, geofenceRadius, googleMap)
                showSnackbar(
                    "Location selected: (${String.format("%.4f", latLng.latitude)}, ${String.format("%.4f", latLng.longitude)})", // Format coordinates
                    Gravity.TOP
                )
            }

            setPoiClick(googleMap) // Call POI click handler

        }

    }

    private fun addMarkerAndMoveCamera(position: LatLng, title: String) {
        googleMap.addMarker(
            MarkerOptions().position(position).title(title)
        )
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 15f))
    }

    private fun onLocationSelected(latLng: LatLng, radius: Float) {
        _viewModel.latitude.value = latLng.latitude
        _viewModel.longitude.value = latLng.longitude
        _viewModel.geofenceRadius.value = radius
        _viewModel.reminderTitle.value = poiTitle ?: _viewModel.reminderTitle.value
        _viewModel.reminderSelectedLocationStr.value = getCityName(latLng)
        _viewModel.navigationCommand.value = NavigationCommand.Back
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.map_options, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.normal_map -> setMapType(GoogleMap.MAP_TYPE_NORMAL)
            R.id.hybrid_map -> setMapType(GoogleMap.MAP_TYPE_HYBRID)
            R.id.satellite_map -> setMapType(GoogleMap.MAP_TYPE_SATELLITE)
            R.id.terrain_map -> setMapType(GoogleMap.MAP_TYPE_TERRAIN)
            R.id.custom_map -> setCustomMapType()
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setMapType(mapType: Int): Boolean {
        googleMap.setMapStyle(null) // reset style
        googleMap.mapType = mapType
        return true
    }

    private fun setCustomMapType(): Boolean {
        try {
            val styleOptions = MapStyleOptions.loadRawResourceStyle(
                requireContext(), R.raw.mapstyle
            )
            val success: Boolean = googleMap.setMapStyle(styleOptions)

            if (!success) {
                showToast("Failed to apply custom style", Gravity.CENTER)
            } else {
                Log.d("SelectLocationFragment", "Custom map style applied successfully.")
                // Optional: Set map type to normal if needed, as custom style overrides type visuals
                //googleMap.mapType = GoogleMap.MAP_TYPE_NORMAL
            }
        } catch (e: NotFoundException) {
            Log.e("SelectLocationFragment", "Can't find style resource. Error: ", e)
            showToast("Cannot find style resource", Gravity.CENTER)
            return false
        } catch (e: Exception) {
            Log.e("SelectLocationFragment", "Error applying map style: ", e)
            showToast("Error applying style", Gravity.CENTER)
            return false
        }
        return true
    }

    private fun getCityName(latLng: LatLng): String {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        try {
            @Suppress("DEPRECATION") // Necessary for getFromLocation below API 33
            val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            return addresses?.firstOrNull()?.locality?.takeIf { it.isNotBlank() }
                ?: "lat: ${latLng.latitude}, lng: ${latLng.longitude}"
        } catch (e: Exception) {
            return "lat: ${latLng.latitude}, lng: ${latLng.longitude}"
        }
    }

    // Handles Point of Interest clicks
    private fun setPoiClick(map: GoogleMap) {
        map.setOnPoiClickListener { poi ->
            googleMap.clear()
            selectedPosition = poi.latLng
            poiTitle = poi.name
            val poiMarker = map.addMarker(
                MarkerOptions()
                    .position(poi.latLng)
                    .title(poi.name)
            )
            drawGeofenceCircle(poi.latLng, geofenceRadius, googleMap)
            poiMarker?.showInfoWindow()
            showSnackbar(
                "${poi.name} selected.",
                Gravity.TOP
            )
        }
    }

}