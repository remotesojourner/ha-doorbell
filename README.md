<div align="center">

  <img src="assets/logo.svg" width="250" alt="App Logo" />

# HA Doorbell

[![CI](https://github.com/remotesojourner/ha-doorbell/actions/workflows/build-release.yml/badge.svg?branch=main&event=push)](https://github.com/remotesojourner/ha-doorbell/actions/workflows/build-release.yml)
[![Latest Release](https://img.shields.io/github/v/release/remotesojourner/ha-doorbell?sort=semver&style=flat&logo=github&label=release)](https://github.com/remotesojourner/ha-doorbell/releases/latest)
[![License: GPL v3](https://img.shields.io/badge/license-GPL--v3-blue?style=flat&logo=gnu)](LICENSE.txt)
[![AI-DECLARATION: auto](https://img.shields.io/badge/䷼%20AI--DECLARATION-auto-ede9fe?labelColor=ede9fe)](AI-DECLARATION.md)

<a href="https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/remotesojourner/ha-doorbell"><img src="https://github.com/ImranR98/Obtainium/blob/main/assets/graphics/badge_obtainium.png?raw=true" alt="Get it on Obtainium" align="center" height="54" />
</a>

</div>

___

An Android doorbell application built for Home Assistant. Integrates with built-in `go2rtc` from the Frigate or AlexxIT/WebRTC integrations for 2-way audio and video streaming.

## Features

- ⚡ **WebRTC Video & Audio**: Low latency streaming using WebRTC.
- 🎙️ **2-Way Audio Intercom**: Supports talking back through your camera using Android's native acoustic echo cancellation.
- 🔐 **Biometric Door Lock Integration**: Unlock your front door from the video stream using Android Fingerprint, Face Unlock, or PIN.
- 💬 **Quick Replies**: Trigger pre-recorded Home Assistant `select` entity messages.
- **Custom Stream Buttons**: Put Home Assistant actions on the camera screen and arrange them in the order you use them.
- 📞 **Doorbell Alerts**: Get a notification with a snapshot, or have the phone ring like an incoming call with Answer and Decline, without the app running in the background.

## Requirements

This app directly hooks into Frigate or AlexxIT/WebRTC for 2-way audio support.

1. **Home Assistant**
2. **Frigate Integration** OR **AlexxIT/WebRTC Integration** 

## App Setup

When you first launch the app, you will be prompted for:

1. **Home Assistant URL**: The local or remote URL to your Home Assistant instance that is accessible from the device (e.g., `https://192.168.1.100:8123`). **Note**: WebRTC strictly requires a secure HTTPS connection to function. Standard HTTP URLs will fail to establish a video/audio stream.
2. **Long-Lived Access Token**: Create a token under **Profile → Security → Long-Lived Access Tokens** in Home Assistant. Select **Generate QR code** in the token dialog, then tap the scanner icon beside the access token field in HA Doorbell. You can also paste the token directly.
3. Tap **Connect** to verify and save the Home Assistant URL and token. The stream settings become editable once the connection is verified, and the connection card collapses. Tap **Change** to edit it again.
4. Enter the **go2rtc Stream Name** as configured in go2rtc (e.g., `front_door_camera`), then tap **Save & Connect**. With **Frigate** selected, you can tap the list icon in that field to pick from the go2rtc streams Frigate reports instead (this needs a Frigate integration version that exposes the go2rtc stream list).

After setup, tap the Settings gear on the stream and authenticate with your device to open Doorbell Setup. The gear opens directly on devices without a screen lock or enrolled biometrics. If you leave Doorbell Setup with unsaved changes, the app asks before discarding them.

On the stream, double-tap the video to switch between fitting the whole picture and filling the screen. Pinch to zoom and drag to pan; double-tap again to reset the zoom. If the stream fails to connect, tap **Retry**.

### Stream Buttons

Open **Doorbell Setup → Stream Buttons** to add, edit, or reorder up to three actions per row. Sound and Call start the bottom row, which can hold five buttons in total; move them with their arrows like any other button. A button shows a progress ring while its action runs; its icon and colors then follow the entity state from Home Assistant. Failures appear as a message over the video.

Each button opens in a full-screen editor, grouped into **What it does**, **Appearance**, and **Security**. Choose **Call a service** to browse Home Assistant services and pick a target when needed. Service data lives under **Show advanced options**, where you can tap a service's fields to add them to the JSON; it opens automatically when a service has required fields. Selecting `select.select_option` or `input_select.select_option` lets you choose an option from the entity's list. **Run a script** and **Trigger an automation** each have their own entity picker. **Fire an event** accepts an event name and optional data and requires a Home Assistant admin token.

**Quick reply menu** opens the full reply list from the stream. The stream starts in listen-only mode with this button enabled so replies can play. Existing buttons keep their configured actions when you update. Choose **Lock or unlock** in the **Action** dropdown to operate a lock based on its current state and show that state on the stream. Migrated door and lock buttons prompt for authentication when the device has a secure screen lock. On devices without a screen lock or enrolled biometrics, those controls run directly.

Tap **Call** in the list to choose optional Answer and Hang Up services, their target entities, and any JSON service data (under **Show advanced options**). If a Quick Reply button is enabled, **Connect in 2-way mode instantly** is disabled so replies can play in listen-only mode.

Stream buttons display icons. Pick a Material Design Icon for any editable button, or leave it on **Automatic** to follow its Home Assistant entity. While the stream is open, the app listens for entity changes, including those made outside the app. When the entity has no explicit icon, built-in icons can reflect states such as a light being off or a cover being open.

Choose a **State entity** to have the Automatic icon follow a different entity. For example, a button can run a script while showing whether the front door is locked. Leave the field empty to follow the action's target.

Automatic lock icons show the lock's current state. With a **State entity** selected, use **Background colors** to assign a color to each state you want to highlight. States without an assigned color use the default button background.

## Doorbell Alerts

HA Doorbell does not keep its own connection to Home Assistant in the background. Instead, a Home Assistant automation alerts your phone through the Companion app's push notifications, which Android already keeps running, so the doorbell costs no extra battery. There are three ways to set this up. Pick the one that suits you, or combine them.

| Option | What you get | Setup |
|---|---|---|
| [Notification](#option-1-notification) | A Companion notification with a camera snapshot. Tap it to open the camera or start talking, with optional **Answer** and door buttons. | Notification blueprint |
| [Ring like a phone call](#option-2-ring-like-a-phone-call) | A full-screen ring over the lock screen with **Answer** and **Decline**, ringing until you respond. | Ring blueprint, plus two permissions in HA Doorbell |
| [Links and intents](#option-3-links-and-intents) | Open or answer the doorbell from your own automations and notifications. | Your own YAML |

### Option 1: Notification

The notification arrives as soon as the doorbell is pressed. If you provide a camera snapshot URL, the snapshot is downloaded in the background and the notification is updated with it without alerting a second time, so a slow camera never delays the alert.

Blueprint options include:
- **When the notification is tapped**: open the camera, or open it and start talking straight away.
- **Answer button**: opens HA Doorbell and starts two-way audio.
- **Door button actions**: any actions to run from a button on the notification, such as unlocking the front door. On Android 12 and newer the phone must be unlocked to use it.
- **Remove after**: clears the notification after a number of seconds.

[![Open your Home Assistant instance and show the blueprint import dialog with a specific blueprint pre-filled.](https://my.home-assistant.io/badges/blueprint_import.svg)](https://my.home-assistant.io/redirect/blueprint_import/?blueprint_url=https%3A%2F%2Fraw.githubusercontent.com%2Fremotesojourner%2Fha-doorbell%2Fmain%2Fblueprints%2Fdoorbell_notification.yaml)

**Fast snapshots (optional).** I use the Reolink camera's built-in snapshot HTTP API instead of the Home Assistant camera snapshot, which is significantly faster:
1. **Enable Reolink HTTP Server**: Make sure the HTTP/HTTPS web server is enabled in your Reolink doorbell's network settings so the snapshot API works.
2. **Downloader Integration**: Add the `downloader` integration to your Home Assistant `configuration.yaml`.
3. **Download Directory**: Configure the `downloader` download directory to start with `www` (e.g., `www/doorbell`) so that Home Assistant can serve the image to the notification. Files in `www` are available at `/local/` in Home Assistant.

Leave the snapshot URL empty to skip the snapshot.

### Option 2: Ring like a phone call

The phone rings with your ringtone like an incoming call:
- **Locked or screen off**: a full-screen ring appears over the lock screen with **Answer**, **Decline**, and **View camera**. Answer and View camera ask you to unlock the phone first, so the camera and your stream buttons stay behind your screen lock.
- **In use**: a ringing notification appears at the top of the screen with **Answer** and **Decline**.
- It rings until you respond, the ring time runs out, or you open HA Doorbell. **Answer** opens the camera with two-way audio already on.

Set up HA Doorbell first: open **Doorbell Setup → Doorbell alerts**, allow **Notifications** and **Full-screen ringing**, then tap **Test ring** to check it. Without full-screen ringing (which Android 14 and newer ask you to allow separately), you still get the ringing notification, but not the lock-screen ring.

Then add the ring automation. To show a snapshot behind the ring, set **Snapshot path** to a Home Assistant path such as `/api/camera_proxy/camera.front_door` or `/local/doorbell/snapshot_doorbell.jpg`; HA Doorbell loads it with its own Home Assistant connection.

[![Open your Home Assistant instance and show the blueprint import dialog with a specific blueprint pre-filled.](https://my.home-assistant.io/badges/blueprint_import.svg)](https://my.home-assistant.io/redirect/blueprint_import/?blueprint_url=https%3A%2F%2Fraw.githubusercontent.com%2Fremotesojourner%2Fha-doorbell%2Fmain%2Fblueprints%2Fdoorbell_ring.yaml)

### Option 3: Links and intents

Use these in your own automations, notifications, or dashboards.

| Link | Opens |
|---|---|
| `hadoorbell://stream` | The camera. |
| `hadoorbell://answer` | The camera with two-way audio started, as if you pressed **Call**. |

In a Companion notification, use the intent form as the `clickAction` or as an action button's `uri` (with `action: URI`). `app://com.novasoftware.hadoorbell` still opens the app as before.

```yaml
- action: notify.mobile_app_my_phone
  data:
    title: There is somebody at the door!
    message: Tap to answer
    data:
      ttl: 0
      priority: high
      clickAction: "intent://answer#Intent;scheme=hadoorbell;package=com.novasoftware.hadoorbell;end"
      actions:
        - action: URI
          title: View camera
          uri: "intent://stream#Intent;scheme=hadoorbell;package=com.novasoftware.hadoorbell;end"
```

To ring the phone yourself, send the Companion `command_broadcast_intent` command. Extras are `name:value` pairs separated by commas; add `:String.urlencoded` to a URL-encoded value that contains commas or colons.

| Broadcast | Extras | Effect |
|---|---|---|
| `com.novasoftware.hadoorbell.RING` | `title`, `message`, `image` (a Home Assistant path), `timeout` (5–600 seconds, default 45) | Starts the ring. |
| `com.novasoftware.hadoorbell.CANCEL_RING` | none | Stops the ring, for example once someone has opened the door. |

```yaml
- action: notify.mobile_app_my_phone
  data:
    message: command_broadcast_intent
    data:
      intent_package_name: com.novasoftware.hadoorbell
      intent_class_name: com.novasoftware.hadoorbell.ring.RingReceiver
      intent_action: com.novasoftware.hadoorbell.RING
      intent_extras: "title:Someone is at the door,timeout:30,image:/api/camera_proxy/camera.front_door"
      ttl: 0
      priority: high
```

Send the same command with `intent_action: com.novasoftware.hadoorbell.CANCEL_RING` and no extras to stop the ring on every phone.

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

## Screenshots

<p align="center">
  <img src="screenshots/stream.png" width="30%" alt="Stream screen with a porch light button and the Sound, Call, Quick Reply and Lock buttons" />
  <img src="screenshots/quickreply.png" width="30%" alt="Quick Reply menu over the stream" />
  <img src="screenshots/ring.png" width="30%" alt="Incoming ring with Answer, Decline and View camera over a snapshot" />
</p>
<p align="center">
  <img src="screenshots/settings.png" width="30%" alt="Doorbell Setup with the connection, stream and button settings" />
  <img src="screenshots/editor.png" width="30%" alt="Stream button editor" />
</p>
