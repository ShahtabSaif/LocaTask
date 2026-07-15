package com.example

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.view.MotionEvent
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File

@Composable
fun GoogleMapView(
    latitude: Double,
    longitude: Double,
    zoom: Int = 15,
    onLocationSelected: ((Double, Double) -> Unit)? = null,
    cameraTriggerKey: Any? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Initialize osmdroid configuration using app cache directory to avoid storage permission issues
    remember {
        val config = Configuration.getInstance()
        config.userAgentValue = context.packageName
        val basePath = File(context.cacheDir, "osmdroid")
        if (!basePath.exists()) {
            basePath.mkdirs()
        }
        config.osmdroidBasePath = basePath
        val tileCache = File(basePath, "tiles")
        if (!tileCache.exists()) {
            tileCache.mkdirs()
        }
        config.osmdroidTileCache = tileCache
        true
    }

    // Track the last coordinates and trigger key to prevent unexpected camera snaps on user interaction
    var lastCenteredPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var lastTriggerKey by remember { mutableStateOf<Any?>(null) }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            controller.setZoom(zoom.toDouble())
            
            // Set initial position
            val startPoint = GeoPoint(latitude, longitude)
            controller.setCenter(startPoint)
            lastCenteredPoint = startPoint

            // Prevent parent containers (Dialog, LazyColumn, etc.) from intercepting map gestures
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
                false // Allow standard MapView touch processing
            }
        }
    }

    // Keep the MapView lifecycle synchronized
    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            val currentPoint = GeoPoint(latitude, longitude)

            // Center the map only on first render or when the external cameraTriggerKey changes
            val shouldCenter = lastCenteredPoint == null || lastTriggerKey != cameraTriggerKey
                
            if (shouldCenter) {
                lastCenteredPoint = currentPoint
                lastTriggerKey = cameraTriggerKey
                
                // If this is the initial layout center, do it instantly. Otherwise, animate smoothly!
                if (view.tag == null) {
                    view.tag = "initialized"
                    view.controller.setCenter(currentPoint)
                } else {
                    view.controller.animateTo(currentPoint)
                }
            }

            // 2. Manage MapEventsOverlay (for single-tap location selection)
            if (onLocationSelected != null) {
                val hasEventsOverlay = view.overlays.any { it is MapEventsOverlay }
                if (!hasEventsOverlay) {
                    val mapEventsReceiver = object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            onLocationSelected(p.latitude, p.longitude)
                            return true
                        }
                        override fun longPressHelper(p: GeoPoint): Boolean {
                            return false
                        }
                    }
                    // Insert at index 0 so it receives touch events first
                    view.overlays.add(0, MapEventsOverlay(mapEventsReceiver))
                }
            } else {
                view.overlays.removeAll { it is MapEventsOverlay }
            }

            // 3. Manage the location Marker dynamically without clearing/re-instantiating every frame
            var marker = view.overlays.filterIsInstance<Marker>().firstOrNull()
            if (marker == null) {
                marker = Marker(view).apply {
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Selected Location"
                    val pinDrawable = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_person_pin)
                    if (pinDrawable != null) {
                        setIcon(pinDrawable)
                    }
                }
                view.overlays.add(marker)
            }
            
            marker.position = currentPoint
            marker.isDraggable = false
            marker.setOnMarkerDragListener(null)

            // Force redraw of overlays
            view.invalidate()
        }
    )
}
