<div align="center">

# 📍 LocaTask

**A polished, offline-first location-based task reminder for Android.**

Never forget a task when arriving at or leaving a specific place. LocaTask uses high-efficiency geofencing to trigger loud, clear, real-time alarms right when you reach your destination — no internet connection required.

![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
![Offline First](https://img.shields.io/badge/Offline-First-success)

### [⬇️ Download Latest APK](https://github.com/ShahtabSaif/LocaTask/releases/latest)

</div>

---

## ✨ Features

| | |
|---|---|
| 📍 **Location-based reminders** | Attach tasks to places, not just times |
| 🔔 **Real-time alarms** | Loud, clear alerts triggered the moment you arrive or leave |
| 📴 **Offline-first** | Works fully without an internet connection |
| 🗺️ **No API key required** | Built on OpenStreetMap — no Google Maps setup or key management |
| ⚡ **Efficient geofencing** | Powered by Google Play Services for low battery impact |

---

## 📱 How to Use

1. **Create a Task**: Tap the **"+" (Add Task)** Floating Action Button on the main dashboard.
2. **Set a Location**: Select a coordinate on the interactive map or choose from your customized presets.
3. **Configure Radius**: Adjust the trigger radius slider (e.g., 100m, 500m) to fit your needs.
4. **Go About Your Day**: Close the app. LocaTask will run highly-optimized background trackers.
5. **Get Alerted**: As soon as you enter the designated zone, a loud persistent alarm banner and ringtone will fire. Tap **DISMISS** to turn it off.

---

## 🛠️ Tech Stack

<div align="center">

| Layer | Technology | Why |
|:---:|:---:|:---|
| 🎨 **UI** | Jetpack Compose · Material Design 3 | Fully declarative, modern Android UI toolkit |
| 🧩 **Architecture** | MVVM + Unidirectional Data Flow | Predictable state, clean separation of concerns |
| 💾 **Persistence** | Room Database | Fast, structured, local storage of tasks & presets |
| 📡 **Location** | Fused Location Provider · Geofencing API | High-efficiency, battery-friendly location triggers |
| 🗺️ **Maps** | OpenStreetMap (embedded) | Full map interactivity, no API key, privacy-friendly |
| 🔄 **Concurrency** | Kotlin Coroutines · StateFlow | Responsive, thread-safe background processing |

</div>

---

## 🏗️ Architecture Overview

LocaTask follows an **MVVM** pattern with a unidirectional data flow:

```
UI (Compose) → ViewModel → Repository → Room Database
                   ↑                          ↓
                   └──────── StateFlow ────────┘
```

Geofence events are handled by a dedicated broadcast receiver, which updates task state through the repository layer and triggers the alarm/notification system.

---

## 🔐 Permissions

LocaTask requires the following permissions to function correctly:

- **Location (Fine & Background)** — required for geofencing to trigger while the app is in the background
- **Notifications** — required to alert you when a task's location trigger fires

