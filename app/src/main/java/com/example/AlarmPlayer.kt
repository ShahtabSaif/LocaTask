package com.example

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log

object AlarmPlayer {
    private var ringtone: Ringtone? = null
    var isAlarmPlaying: Boolean = false
        private set

    fun startAlarm(context: Context) {
        try {
            stopAlarm() // Stop any running alarm first
            
            val alarmUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            ringtone = RingtoneManager.getRingtone(context.applicationContext, alarmUri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                }
                play()
            }
            isAlarmPlaying = true
            Log.d("AlarmPlayer", "Alarm playing successfully")
        } catch (e: Exception) {
            Log.e("AlarmPlayer", "Failed to play alarm", e)
        }
    }

    fun stopAlarm() {
        try {
            ringtone?.let {
                if (it.isPlaying) {
                    it.stop()
                }
            }
            ringtone = null
            isAlarmPlaying = false
            Log.d("AlarmPlayer", "Alarm stopped successfully")
        } catch (e: Exception) {
            Log.e("AlarmPlayer", "Failed to stop alarm", e)
        }
    }
}
