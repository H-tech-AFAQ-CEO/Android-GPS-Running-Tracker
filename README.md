# Android GPS Running Tracker 🏃‍♂️📍

A lightweight, robust, real-time GPS running tracker built exclusively for Android using **Kotlin**, **Jetpack Compose**, **OpenStreetMap (osmdroid)**, **Room Database**, and **Foreground Services**.

## App Preview

![App Screenshot](screenshot.png)

## Features

- **Real-Time GPS Tracking**: Smoothly tracks position and draws your route polyline live on an interactive OpenStreetMap view (Zero API keys required!).
- **Live Metrics**: Instantly updates elapsed time, total distance (km), current pace, and estimated calories burned (`KCAL`) every second.
- **Background-Safe**: Powered by a robust Foreground Service and CPU WakeLock to ensure tracking never stops, even when the screen locks or the app is minimized.
- **Local Database Persistence**: Automatically saves finished runs using Room Database.
- **Run History & Detail Views**: Browse past sessions with summary stats, interactive route maps, and a delete option.
- **GPX Export**: Export any recorded run as a standard `.gpx` file and share it with fitness apps (Strava, Garmin, etc.) via the Android Share Sheet.
- **Modern UI / Material 3**: Premium minimalist dark and light theme support.

## Tech Stack

- **Language**: Kotlin
- **UI Toolkit**: Jetpack Compose (Material 3)
- **Map Engine**: osmdroid (OpenStreetMap) — Free, open-source, no API key needed
- **Location**: Google Play Services Location (`FusedLocationProviderClient`)
- **Database**: Room (SQLite persistence)
- **Architecture**: MVVM + StateFlow + Foreground Service

## Author & Developer

**Afaq Ahmad**  
Explore more of my Android apps on the Google Play Store:  
👉 [Afaq Ahmad - Google Play Developer Profile](https://play.google.com/store/apps/developer?id=Afaq+Ahmad&hl=en)

---

## License

MIT License
