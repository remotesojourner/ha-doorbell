<p align="center">
  <img src="assets/logo.svg" width="128" alt="App Logo" /><br><br>
</p>

# HA Doorbell

[![CI](https://github.com/remotesojourner/ha-doorbell/actions/workflows/build-release.yml/badge.svg?branch=main&event=push)](https://github.com/remotesojourner/ha-doorbell/actions/workflows/build-release.yml)
[![Latest Release](https://img.shields.io/github/v/release/remotesojourner/ha-doorbell?sort=semver&style=flat&logo=github&label=release)](https://github.com/remotesojourner/ha-doorbell/releases/latest)
[![License: GPL v3](https://img.shields.io/badge/license-GPL--v3-blue?style=flat&logo=gnu)](LICENSE.txt)
[![AI-DECLARATION: auto](https://img.shields.io/badge/䷼%20AI--DECLARATION-auto-ede9fe?labelColor=ede9fe)](AI-DECLARATION.md)

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
- **Connect in 2-way mode instantly**: If you leave the Quick Reply Entity ID blank, you can optionally enable this toggle. It forces the app to skip the initial receive-only mode and hook directly into 2-way audio when the stream opens.
- **Door Lock Entity ID**: Provide the ID of a `lock` entity (e.g., `lock.front_door`) to add a biometric-protected unlock button to the camera feed.

## Development

This project leverages the following major external libraries:
- **[Stream WebRTC Android](https://github.com/GetStream/webrtc-android)** for low-latency WebRTC video and audio networking.
- **[OkHttp](https://square.github.io/okhttp/)** for WebSocket and REST API communication.
- **[Gson](https://github.com/google/gson)** for JSON parsing.

To build the project, open it in Android Studio and run a standard Gradle build.

## Personal Setup Notes

For my personal setup, I am using this app with Home Assistant, Frigate, a Reolink Wi-Fi doorbell, and a SwitchBot lock.

### Reolink & Frigate 2-Way Audio
The Reolink doorbell has issues dropping the call if you use WebRTC 2-way audio on the exact same stream that Frigate is using to record and detect. To prevent the doorbell from locking up and refusing to ring when all systems try to use the same stream, I set up a separate, dedicated `go2rtc` stream in Frigate specifically to use with this app.

Here is the config for the 2-way stream in the Frigate `go2rtc` section:
```yaml
    doorbell_camera_2way:
      - rtsp://admin:your_password_here@192.168.1.10:554/Preview_01_sub
      - ffmpeg:doorbell_camera_2way#audio=opus#audio=copy
```
*(This [Frigate documentation page](https://docs.frigate.video/configuration/live/) is very helpful in setting up 2-way communications).*

### Reolink Quick Replies & SwitchBot
I use a SwitchBot lock integrated through Home Assistant, which works flawlessly with the lock button in the app. 

The auto-reply feature of the Reolink doorbell is quite finicky and actually refuses to work if a 2-way audio stream is actively going on. Because of this limitation, if the quick reply option is set up, this app connects in a receive-only streaming mode first. It only switches over to 2-way audio when you explicitly hit the **Call** button, allowing the quick reply functionality to work uninterrupted while viewing the live feed.

### Fast Actionable Notifications

To get immediate notifications with an image when the doorbell is pressed, I use a custom automation that relies on the Reolink camera's built-in snapshot HTTP API rather than the default Home Assistant camera snapshot functionality. This is significantly faster!

**Prerequisites:**
1. **Enable Reolink HTTP Server**: Make sure the HTTP/HTTPS web server is enabled in your Reolink doorbell's network settings so the snapshot API works.
2. **Downloader Integration**: You must add the `downloader` integration to your Home Assistant `configuration.yaml`.
3. **Download Directory**: Configure the `downloader` download directory to start with `www` (e.g., `www/doorbell`) so that Home Assistant can serve the image to the notification. (Files in `www` are accessible via `/local/` in Home Assistant).

**Blueprint Import:**

You can easily add this automation using the blueprint below:

[![Open your Home Assistant instance and show the blueprint import dialog with a specific blueprint pre-filled.](https://my.home-assistant.io/badges/blueprint_import.svg)](https://my.home-assistant.io/redirect/blueprint_import/?blueprint_url=https%3A%2F%2Fraw.githubusercontent.com%2Fremotesojourner%2Fha-doorbell%2Fmain%2Fblueprints%2Fdoorbell_notification.yaml)

**Manual Automation Code:**

If you prefer to set it up manually, here is the automation YAML. **Note:** Due to Home Assistant limitations, you must manually edit the `entity_id` under `triggers` at the very top to match your doorbell sensor. For everything else, simply update the values in the `variables:` block at the very bottom!

```yaml
alias: Doorbell visitor notification
description: ""
triggers:
  - entity_id: binary_sensor.doorbell_visitor
    from: "off"
    to: "on"
    trigger: state
actions:
  - action: downloader.download_file
    data:
      url:  "{{camera_snapshot_url}}"
      filename: "{{ snapshot_file_name }}"
      overwrite: true
  - wait_for_trigger:
      - trigger: event
        event_type: downloader_download_completed
    timeout: "{{ wait_timeout }}"
    continue_on_timeout: false
  - data:
      title: "{{notification_title}}"
      message: "{{ as_timestamp(now()) | timestamp_custom('%d:%m %H:%M', true) }}"
      data:
        ttl: "{{ notification_ttl }}"
        priority: "{{ notification_priority }}"
        channel: "{{notification_channel}}"
        image: "/local/{{ downloader_path }}/{{ snapshot_file_name }}"
        notification_icon: mdi:doorbell-video
        clickAction: app://com.novasoftware.hadoorbell
    action: "{{ notify_service }}"
mode: single
variables:
  camera_snapshot_url: http://192.168.1.10/cgi-bin/api.cgi?cmd=Snap&channel=0&user=admin&password=your_password_here
  notify_service: notify.mobile_devices
  notification_channel: alarm_stream
  notification_title: There is somebody at the door!
  notification_ttl: 0
  notification_priority: high
  snapshot_file_name: snapshot_doorbell.jpg
  downloader_path: doorbell
  wait_timeout: "00:10:00"
```
