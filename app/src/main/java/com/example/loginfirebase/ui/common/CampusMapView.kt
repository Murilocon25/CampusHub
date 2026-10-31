package com.example.loginfirebase.ui.common

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.loginfirebase.R
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/** A MapLibre view using the app-bundled OpenStreetMap raster style and Activity lifecycle. */
class CampusMapView(
    context: Context,
    lifecycle: Lifecycle,
    savedInstanceState: Bundle? = null
) : FrameLayout(context), LifecycleEventObserver {
    private val mapView = MapView(context)
    private val mapState = savedInstanceState?.getBundle(MAP_STATE_KEY)

    init {
        addView(mapView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(createAttribution(context), LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            val margin = resources.getDimensionPixelSize(R.dimen.space_2)
            setMargins(margin, margin, margin, margin)
        })
        lifecycle.addObserver(this)
    }

    fun getMapAsync(callback: (MapLibreMap) -> Unit) {
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromUri(OSM_STYLE_ASSET)) {
                callback(map)
            }
        }
    }

    fun onLowMemory() = mapView.onLowMemory()

    fun saveMapState(outState: Bundle) {
        val mapState = Bundle()
        mapView.onSaveInstanceState(mapState)
        outState.putBundle(MAP_STATE_KEY, mapState)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_CREATE -> mapView.onCreate(mapState)
            Lifecycle.Event.ON_START -> mapView.onStart()
            Lifecycle.Event.ON_RESUME -> mapView.onResume()
            Lifecycle.Event.ON_PAUSE -> mapView.onPause()
            Lifecycle.Event.ON_STOP -> mapView.onStop()
            Lifecycle.Event.ON_DESTROY -> {
                mapView.onDestroy()
                source.lifecycle.removeObserver(this)
            }
            else -> Unit
        }
    }

    private fun createAttribution(context: Context) = TextView(context).apply {
        text = context.getString(R.string.map_attribution)
        setTextAppearance(R.style.TextAppearance_CampusHub_Caption)
        setCampusFont()
        setTextColor(context.getColor(R.color.color_text_body))
        setPadding(
            resources.getDimensionPixelSize(R.dimen.space_2),
            resources.getDimensionPixelSize(R.dimen.space_1),
            resources.getDimensionPixelSize(R.dimen.space_2),
            resources.getDimensionPixelSize(R.dimen.space_1)
        )
        background = GradientDrawable().apply {
            setColor(context.getColor(R.color.color_surface))
            cornerRadius = resources.getDimension(R.dimen.shape_small)
        }
        elevation = resources.getDimension(R.dimen.space_1)
    }

    companion object {
        private const val MAP_STATE_KEY = "campus_map_view_state"
        private const val OSM_STYLE_ASSET = "asset://osm-raster-style.json"
    }
}
