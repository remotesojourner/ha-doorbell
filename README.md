# HA Doorbell

<p align="center">
  <img src="assets/logo.svg" width="128" alt="App Logo" />
</p>

An Android doorbell application built for Home Assistant. Integrates with Frigate's built-in `go2rtc` proxy for 2-way audio and video streaming.

## Features

- ⚡ **WebRTC Video & Audio**: Low latency streaming using WebRTC.
- 🎙️ **2-Way Audio Intercom**: Supports talking back through your camera using Android's native acoustic echo cancellation.
- 🔐 **Biometric Door Lock Integration**: Unlock your front door from the video stream using Android Fingerprint, Face Unlock, or PIN.
- 💬 **Quick Replies**: Trigger pre-recorded Home Assistant `select` entity messages.

## Requirements

This app directly hooks into Frigate for 2-way audio support.

1. **Home Assistant**
2. **Frigate Integration** 

## App Setup

When you first launch the app, you will be prompted for:

1. **Home Assistant URL**: The local or remote URL to your Home Assistant instance that is accessible from the device. (e.g., `http://192.168.1.100:8123`).
2. **Long-Lived Access Token**: Generate this in your Home Assistant user profile.
3. **go2rtc Stream Name**: The exact name of the camera stream as configured in Frigate's go2rtc config (e.g., `front_door_camera`).

### Optional Integrations

- **Quick Reply Entity ID**: Provide the ID of a `select` entity (e.g., `select.doorbell_quick_reply`) to enable instant pre-recorded messages e.g. You can get this through reolink integration in home assistant. 
- **Door Lock Entity ID**: Provide the ID of a `lock` entity (e.g., `lock.front_door`) to add a biometric-protected unlock button to the camera feed.

## Development

This project is built using:
- **Jetpack Compose** for UI
- **Stream WebRTC Android** for WebRTC networking
- **OkHttp & Coroutines** for API communication
- **AndroidX Biometric** for security overlays

To build the project, open it in Android Studio and run a standard Gradle build.
