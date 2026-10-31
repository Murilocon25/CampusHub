package com.example.loginfirebase.ui.events

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.loginfirebase.R
import com.example.loginfirebase.ui.common.CampusMapView
import com.google.android.material.button.MaterialButton
import com.google.android.material.appbar.MaterialToolbar
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

class EventLocationPickerActivity : AppCompatActivity() {
    private var selectedPosition: LatLng? = null
    private var selectedMarker: Marker? = null
    private var mapLibreMap: MapLibreMap? = null
    private lateinit var confirmButton: MaterialButton
    private lateinit var selectionStatus: TextView
    private var campusMapView: CampusMapView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pick_event_location)

        confirmButton = findViewById(R.id.confirm_event_location)
        selectionStatus = findViewById(R.id.event_location_selection_status)
        findViewById<MaterialToolbar>(R.id.event_location_toolbar).setNavigationOnClickListener { finish() }
        selectedPosition = if (savedInstanceState?.containsKey(STATE_LATITUDE) == true &&
            savedInstanceState.containsKey(STATE_LONGITUDE)
        ) {
            LatLng(savedInstanceState.getDouble(STATE_LATITUDE), savedInstanceState.getDouble(STATE_LONGITUDE))
        } else if (intent.hasExtra(EXTRA_LATITUDE) && intent.hasExtra(EXTRA_LONGITUDE)) {
            LatLng(intent.getDoubleExtra(EXTRA_LATITUDE, 0.0), intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0))
        } else {
            null
        }
        updateSelectionStatus()
        confirmButton.setOnClickListener { returnSelectedLocation() }

        val mapContainer = findViewById<FrameLayout>(R.id.event_location_map_container)
        campusMapView = CampusMapView(this, lifecycle, savedInstanceState).also { mapView ->
            mapContainer.addView(mapView, FrameLayout.LayoutParams(-1, -1))
            mapView.getMapAsync(::configureMap)
        }
    }

    private fun configureMap(map: MapLibreMap) {
        mapLibreMap = map
        map.addOnMapClickListener { position ->
            selectPosition(position)
            true
        }

        val initialPosition = selectedPosition ?: SANTIAGO_CENTER
        map.cameraPosition = CameraPosition.Builder()
            .target(initialPosition)
            .zoom(if (selectedPosition == null) 14.5 else 17.0)
            .build()
        selectedPosition?.let { addOrMoveMarker(map, it) }
    }

    private fun selectPosition(position: LatLng) {
        selectedPosition = position
        mapLibreMap?.let { addOrMoveMarker(it, position) }
        updateSelectionStatus()
    }

    private fun addOrMoveMarker(map: MapLibreMap, position: LatLng) {
        selectedMarker?.let { marker -> map.removeAnnotation(marker) }
        selectedMarker = map.addMarker(
            MarkerOptions()
                .position(position)
                .title(getString(R.string.event_location_marker_title))
        )
        updateSelectionStatus()
    }

    private fun updateSelectionStatus() {
        val hasSelection = selectedPosition != null
        selectionStatus.setText(if (hasSelection) R.string.event_location_selected else R.string.event_location_pick_hint)
        confirmButton.isEnabled = hasSelection
    }

    private fun returnSelectedLocation() {
        val position = selectedPosition ?: return
        setResult(
            Activity.RESULT_OK,
            Intent()
                .putExtra(EXTRA_LATITUDE, position.latitude)
                .putExtra(EXTRA_LONGITUDE, position.longitude)
        )
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        selectedPosition?.let {
            outState.putDouble(STATE_LATITUDE, it.latitude)
            outState.putDouble(STATE_LONGITUDE, it.longitude)
        }
        campusMapView?.saveMapState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        campusMapView?.onLowMemory()
    }

    companion object {
        const val EXTRA_LATITUDE = "event_location_latitude"
        const val EXTRA_LONGITUDE = "event_location_longitude"
        private const val STATE_LATITUDE = "selected_event_location_latitude"
        private const val STATE_LONGITUDE = "selected_event_location_longitude"
        private val SANTIAGO_CENTER = LatLng(-29.19, -54.87)
    }
}
