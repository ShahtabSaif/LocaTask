package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.LocaTask
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

class GeofencingManager(private val context: Context) {
    private val geofencingClient: GeofencingClient = run {
        val attribContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.createAttributionContext("locatask_attribution")
        } else {
            context
        }
        LocationServices.getGeofencingClient(attribContext)
    }

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    @SuppressLint("MissingPermission")
    fun addGeofence(task: LocaTask, onSuccess: () -> Unit = {}, onFailure: (Exception) -> Unit = {}) {
        val fineLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (fineLocationPermission != PackageManager.PERMISSION_GRANTED) {
            Log.w("GeofencingManager", "ACCESS_FINE_LOCATION not granted. Skipping system geofence registration.")
            onSuccess()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val backgroundPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
            if (backgroundPermission != PackageManager.PERMISSION_GRANTED) {
                Log.w("GeofencingManager", "ACCESS_BACKGROUND_LOCATION not granted. Skipping system geofence registration.")
                onSuccess()
                return
            }
        }

        val geofence = Geofence.Builder()
            .setRequestId(task.id.toString())
            .setCircularRegion(task.latitude, task.longitude, task.radius)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .setNotificationResponsiveness(1000) // 1 second response latency
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        try {
            geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)
                .addOnSuccessListener {
                    Log.d("GeofencingManager", "Successfully added geofence for task ID: ${task.id} (${task.title})")
                    onSuccess()
                }
                .addOnFailureListener { e ->
                    Log.e("GeofencingManager", "Failed to add geofence", e)
                    onFailure(e)
                }
        } catch (e: SecurityException) {
            Log.e("GeofencingManager", "SecurityException during addGeofences", e)
            onSuccess() // Fallback gracefully to local geofencing loop
        }
    }

    fun removeGeofence(taskId: Int, onSuccess: () -> Unit = {}, onFailure: (Exception) -> Unit = {}) {
        geofencingClient.removeGeofences(listOf(taskId.toString()))
            .addOnSuccessListener {
                Log.d("GeofencingManager", "Successfully removed geofence for task ID: $taskId")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e("GeofencingManager", "Failed to remove geofence", e)
                onFailure(e)
            }
    }
}
