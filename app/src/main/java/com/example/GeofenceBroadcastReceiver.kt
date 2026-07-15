package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.room.Room
import com.example.data.AppDatabase
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("GeofenceReceiver", "Geofence transition received!")
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return

        if (geofencingEvent.hasError()) {
            Log.e("GeofenceReceiver", "GeofencingEvent error: ${geofencingEvent.errorCode}")
            return
        }

        val transitionType = geofencingEvent.geofenceTransition
        if (transitionType == Geofence.GEOFENCE_TRANSITION_ENTER) {
            val triggeringGeofences = geofencingEvent.triggeringGeofences ?: return
            
            val db = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java, "locatask_database"
            ).build()
            
            val dao = db.locaTaskDao()
            val scope = CoroutineScope(Dispatchers.IO)

            for (geofence in triggeringGeofences) {
                val taskIdString = geofence.requestId
                val taskId = taskIdString.toIntOrNull() ?: continue
                
                scope.launch {
                    val task = dao.getTaskById(taskId)
                    if (task != null && task.isActive) {
                        // Mark as triggered and inactive
                        dao.updateTriggeredStatus(taskId, true)
                        dao.updateActiveStatus(taskId, false)
                        
                        // Write active alarm ID to preferences to sync with VM
                        val prefs = context.getSharedPreferences("locatask_settings", Context.MODE_PRIVATE)
                        prefs.edit().putInt("active_alarm_task_id", taskId).apply()
                        
                        // Start Alarm sound
                        AlarmPlayer.startAlarm(context)
                        
                        // Fire notification
                        showNotification(context, task.title, task.description, taskId)
                    }
                }
            }
        }
    }

    companion object {
        fun showNotification(context: Context, title: String, content: String, notificationId: Int) {
            val channelId = "locatask_alerts"
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "LocaTask Location Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifies you when you arrive at a task location"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // High priority alerts for maximum precise feedback
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(notificationId, notification)
        }
    }
}
