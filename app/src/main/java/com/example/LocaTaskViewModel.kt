package com.example

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.location.Geocoder
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.LocaTask
import com.example.data.LocaTaskRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.Build
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

// Customizable preset locations
data class PresetLocation(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val icon: String,
    val region: String,
    val category: String,
    val isCustom: Boolean = false
)

data class SearchResult(
    val displayName: String,
    val latitude: Double,
    val longitude: Double
)

class LocaTaskViewModel(
    private val application: Application,
    private val repository: LocaTaskRepository,
    private val geofencingManager: GeofencingManager
) : ViewModel() {

    private val fusedLocationClient: FusedLocationProviderClient = run {
        val attribContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            application.createAttributionContext("locatask_attribution")
        } else {
            application
        }
        LocationServices.getFusedLocationProviderClient(attribContext)
    }

    // Flow of all tasks from Room DB
    val tasksState: StateFlow<List<LocaTask>> = repository.allTasksFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Real or Simulated location
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    // Simulation Mode States
    private val _isSimulationMode = MutableStateFlow(false)
    val isSimulationMode: StateFlow<Boolean> = _isSimulationMode.asStateFlow()

    private val _simulatedLat = MutableStateFlow(37.7749) // Default SF
    val simulatedLat: StateFlow<Double> = _simulatedLat.asStateFlow()

    private val _simulatedLng = MutableStateFlow(-122.4194)
    val simulatedLng: StateFlow<Double> = _simulatedLng.asStateFlow()

    private val _gpsStatusMessage = MutableStateFlow("GPS Initializing...")
    val gpsStatusMessage: StateFlow<String> = _gpsStatusMessage.asStateFlow()

    private var locationCallback: LocationCallback? = null

    // --- Persisted System Settings ---
    private val prefs = application.getSharedPreferences("locatask_settings", Context.MODE_PRIVATE)

    private val _notificationVolume = MutableStateFlow(prefs.getFloat("notification_volume", 80f))
    val notificationVolume = _notificationVolume.asStateFlow()

    private val _highPrecisionGPS = MutableStateFlow(prefs.getBoolean("high_precision_gps", true))
    val highPrecisionGPS = _highPrecisionGPS.asStateFlow()

    private val _batteryOptimizationCheck = MutableStateFlow(prefs.getBoolean("battery_optimization_check", true))
    val batteryOptimizationCheck = _batteryOptimizationCheck.asStateFlow()

    // --- Active Alarm State ---
    private val _isAlarmActive = MutableStateFlow(false)
    val isAlarmActive = _isAlarmActive.asStateFlow()

    private val _activeAlarmTask = MutableStateFlow<LocaTask?>(null)
    val activeAlarmTask = _activeAlarmTask.asStateFlow()

    // --- Customizable Presets ---
    private val defaultPresets = emptyList<PresetLocation>()

    private val _customPresets = MutableStateFlow<List<PresetLocation>>(emptyList())
    val customPresets = _customPresets.asStateFlow()

    val allPresetsState: StateFlow<List<PresetLocation>> = _customPresets.map { custom ->
        defaultPresets + custom
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = defaultPresets
    )

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "active_alarm_task_id") {
            checkAlarmStatus()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        loadCustomPresets()
        startLocationUpdates()
        checkAlarmStatus()
    }

    // --- Location updates starting with current settings ---
    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (_isSimulationMode.value) return

        try {
            _gpsStatusMessage.value = "Starting live GPS updates..."
            
            val isHighPrecision = _highPrecisionGPS.value
            val priority = if (isHighPrecision) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
            val interval = if (isHighPrecision) 5000L else 15000L
            val minInterval = if (isHighPrecision) 2000L else 8000L

            val locationRequest = LocationRequest.Builder(priority, interval)
                .setMinUpdateIntervalMillis(minInterval)
                .build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val lastLoc = locationResult.lastLocation
                    if (lastLoc != null) {
                        _currentLocation.value = lastLoc
                        updateLocationAddress(lastLoc.latitude, lastLoc.longitude, "GPS Active")
                        checkGeofencesForLocation(lastLoc.latitude, lastLoc.longitude)
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            
            // Get last known location as fallback
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null && _currentLocation.value == null) {
                    _currentLocation.value = loc
                    updateLocationAddress(loc.latitude, loc.longitude, "GPS Last Known")
                    checkGeofencesForLocation(loc.latitude, loc.longitude)
                }
            }
        } catch (e: SecurityException) {
            _gpsStatusMessage.value = "GPS Disabled: Permissions required"
            Log.e("LocaTaskViewModel", "Permission missing for location updates", e)
        } catch (e: Exception) {
            _gpsStatusMessage.value = "GPS Error: ${e.message}"
            Log.e("LocaTaskViewModel", "Error getting location", e)
        }
    }

    fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        locationCallback = null
    }

    // --- Simulation mode controls ---
    fun toggleSimulationMode(enabled: Boolean) {
        _isSimulationMode.value = enabled
        if (enabled) {
            stopLocationUpdates()
            updateSimulatedLocation(_simulatedLat.value, _simulatedLng.value)
        } else {
            startLocationUpdates()
        }
    }

    fun updateSimulatedLocation(lat: Double, lng: Double) {
        _simulatedLat.value = lat
        _simulatedLng.value = lng
        
        val mockLoc = Location("simulated").apply {
            latitude = lat
            longitude = lng
            time = System.currentTimeMillis()
        }
        _currentLocation.value = mockLoc
        updateLocationAddress(lat, lng, "Mock Location")
        checkGeofencesForLocation(lat, lng)
    }

    fun updateLocationAddress(latitude: Double, longitude: Double, labelPrefix: String) {
        viewModelScope.launch {
            val addressName = withContext(Dispatchers.IO) {
                try {
                    if (Geocoder.isPresent()) {
                        val geocoder = Geocoder(application, Locale.getDefault())
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val addr = addresses[0]
                            val thoroughfare = addr.thoroughfare ?: addr.subThoroughfare
                            val locality = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: addr.countryName
                            
                            when {
                                thoroughfare != null && locality != null -> "$thoroughfare, $locality"
                                locality != null -> locality
                                thoroughfare != null -> thoroughfare
                                else -> addr.getAddressLine(0)?.substringBefore(",") ?: ""
                            }
                        } else ""
                    } else ""
                } catch (e: Exception) {
                    Log.e("LocaTaskViewModel", "Geocoding failed", e)
                    ""
                }
            }
            
            val displayAddress = if (addressName.isNotEmpty()) {
                addressName
            } else {
                String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
            }
            
            _gpsStatusMessage.value = "$labelPrefix: $displayAddress"
        }
    }

    // --- Settings modification methods ---
    fun updateNotificationVolume(volume: Float) {
        _notificationVolume.value = volume
        prefs.edit().putFloat("notification_volume", volume).apply()
    }

    fun updateHighPrecisionGPS(enabled: Boolean) {
        _highPrecisionGPS.value = enabled
        prefs.edit().putBoolean("high_precision_gps", enabled).apply()
        stopLocationUpdates()
        startLocationUpdates()
    }

    fun updateBatteryOptimizationCheck(enabled: Boolean) {
        _batteryOptimizationCheck.value = enabled
        prefs.edit().putBoolean("battery_optimization_check", enabled).apply()
    }

    // --- Active Alarm actions ---
    fun checkAlarmStatus() {
        val activeId = prefs.getInt("active_alarm_task_id", -1)
        if (activeId != -1) {
            viewModelScope.launch {
                val task = repository.getTaskById(activeId)
                if (task != null) {
                    _activeAlarmTask.value = task
                    _isAlarmActive.value = true
                    if (!AlarmPlayer.isAlarmPlaying) {
                        AlarmPlayer.startAlarm(application)
                    }
                } else {
                    _isAlarmActive.value = false
                    _activeAlarmTask.value = null
                    AlarmPlayer.stopAlarm()
                    prefs.edit().putInt("active_alarm_task_id", -1).apply()
                }
            }
        } else {
            _isAlarmActive.value = false
            _activeAlarmTask.value = null
        }
    }

    fun stopActiveAlarm() {
        AlarmPlayer.stopAlarm()
        _isAlarmActive.value = false
        _activeAlarmTask.value = null
        prefs.edit().putInt("active_alarm_task_id", -1).apply()
    }

    // --- Custom Presets persistence ---
    private fun loadCustomPresets() {
        val jsonStr = prefs.getString("custom_presets", null)
        if (jsonStr != null) {
            try {
                val arr = JSONArray(jsonStr)
                val list = mutableListOf<PresetLocation>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        PresetLocation(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            lat = obj.getDouble("lat"),
                            lng = obj.getDouble("lng"),
                            icon = obj.getString("icon"),
                            region = obj.getString("region"),
                            category = obj.getString("category"),
                            isCustom = true
                        )
                    )
                }
                _customPresets.value = list
            } catch (e: Exception) {
                Log.e("LocaTaskViewModel", "Failed to load custom presets", e)
            }
        }
    }

    fun addCustomPreset(name: String, lat: Double, lng: Double, icon: String, region: String, category: String) {
        val newPreset = PresetLocation(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            lat = lat,
            lng = lng,
            icon = icon.ifBlank { "📍" },
            region = region.ifBlank { "Custom" },
            category = category,
            isCustom = true
        )
        val updated = _customPresets.value + newPreset
        _customPresets.value = updated
        saveCustomPresets(updated)
    }

    fun deleteCustomPreset(id: String) {
        val updated = _customPresets.value.filter { it.id != id }
        _customPresets.value = updated
        saveCustomPresets(updated)
    }

    private fun saveCustomPresets(list: List<PresetLocation>) {
        try {
            val arr = JSONArray()
            for (preset in list) {
                val obj = JSONObject().apply {
                    put("id", preset.id)
                    put("name", preset.name)
                    put("lat", preset.lat)
                    put("lng", preset.lng)
                    put("icon", preset.icon)
                    put("region", preset.region)
                    put("category", preset.category)
                }
                arr.put(obj)
            }
            prefs.edit().putString("custom_presets", arr.toString()).apply()
        } catch (e: Exception) {
            Log.e("LocaTaskViewModel", "Failed to save custom presets", e)
        }
    }

    // --- Global search with Nominatim OSM API ---
    suspend fun searchLocation(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://nominatim.openstreetmap.org/search?format=json&q=$encodedQuery&limit=8"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "LocaTaskAlertApp-shahtabhossain05-v1.0")
            .build()
        
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext listOf(
                        SearchResult(
                            displayName = "❌ Error: HTTP ${response.code}",
                            latitude = 0.0,
                            longitude = 0.0
                        )
                    )
                }
                val bodyString = response.body?.string() ?: return@withContext emptyList()
                if (bodyString.trim().startsWith("{")) {
                    val obj = JSONObject(bodyString)
                    val errMsg = obj.optJSONObject("error")?.optString("message") 
                        ?: obj.optString("message", "API Error")
                    return@withContext listOf(
                        SearchResult(
                            displayName = "❌ API Error: $errMsg",
                            latitude = 0.0,
                            longitude = 0.0
                        )
                    )
                }
                val jsonArray = JSONArray(bodyString)
                val results = mutableListOf<SearchResult>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val latStr = obj.optString("lat", "0.0")
                    val lonStr = obj.optString("lon", "0.0")
                    results.add(
                        SearchResult(
                            displayName = obj.optString("display_name", "Unknown Location"),
                            latitude = latStr.toDoubleOrNull() ?: 0.0,
                            longitude = lonStr.toDoubleOrNull() ?: 0.0
                        )
                    )
                }
                if (results.isEmpty()) {
                    listOf(
                        SearchResult(
                            displayName = "🔍 No results found",
                            latitude = 0.0,
                            longitude = 0.0
                        )
                    )
                } else {
                    results
                }
            }
        } catch (e: Exception) {
            Log.e("LocaTaskViewModel", "Search failed", e)
            listOf(
                SearchResult(
                    displayName = "❌ Connection error: ${e.localizedMessage ?: "Unknown error"}",
                    latitude = 0.0,
                    longitude = 0.0
                )
            )
        }
    }

    // Main logic: checks distances and triggers alarm if inside radius
    private fun checkGeofencesForLocation(lat: Double, lng: Double) {
        viewModelScope.launch {
            val activeTasks = repository.getActiveTasks()
            for (task in activeTasks) {
                val results = FloatArray(1)
                Location.distanceBetween(lat, lng, task.latitude, task.longitude, results)
                val distanceInMeters = results[0]
                
                if (distanceInMeters <= task.radius) {
                    // Trigger geofence!
                    repository.updateTriggeredStatus(task.id, true)
                    repository.updateActiveStatus(task.id, false)
                    
                    // Unregister physical geofence
                    geofencingManager.removeGeofence(task.id)
                    
                    // Persist active alarm task ID
                    prefs.edit().putInt("active_alarm_task_id", task.id).apply()
                    
                    // Trigger alarm sound and update UI state
                    _activeAlarmTask.value = task
                    _isAlarmActive.value = true
                    AlarmPlayer.startAlarm(application)
                    
                    // Show Notification
                    GeofenceBroadcastReceiver.showNotification(
                        application,
                        "📍 Arrived at: ${task.title}",
                        "${task.description} (Precise location alert triggered!)",
                        task.id
                    )
                }
            }
        }
    }

    // Add task
    fun addTask(title: String, description: String, lat: Double, lng: Double, radius: Float) {
        viewModelScope.launch {
            val newTask = LocaTask(
                title = title,
                description = description,
                latitude = lat,
                longitude = lng,
                radius = radius,
                isActive = true,
                isTriggered = false
            )

            // Save to offline Room DB
            val newId = repository.insertTask(newTask)
            val taskWithId = newTask.copy(id = newId.toInt())
            
            // Register physical geofence
            geofencingManager.addGeofence(taskWithId)
        }
    }

    // Delete task
    fun deleteTask(task: LocaTask) {
        viewModelScope.launch {
            repository.deleteTaskById(task.id)
            geofencingManager.removeGeofence(task.id)
        }
    }

    // Toggle Task Active/Inactive
    fun toggleTaskActive(task: LocaTask) {
        viewModelScope.launch {
            val nextActive = !task.isActive
            repository.updateActiveStatus(task.id, nextActive)
            if (nextActive) {
                geofencingManager.addGeofence(task.copy(isActive = true, isTriggered = false))
            } else {
                geofencingManager.removeGeofence(task.id)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        stopLocationUpdates()
    }
}

// ViewModel Factory
class LocaTaskViewModelFactory(
    private val application: Application,
    private val repository: LocaTaskRepository,
    private val geofencingManager: GeofencingManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LocaTaskViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LocaTaskViewModel(application, repository, geofencingManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
