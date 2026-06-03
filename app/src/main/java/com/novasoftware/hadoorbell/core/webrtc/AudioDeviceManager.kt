package com.novasoftware.hadoorbell.core.webrtc

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

class AudioDeviceManager(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioDeviceCallback: android.media.AudioDeviceCallback? = null

    fun start() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        setOptimalAudioDevice()

        audioDeviceCallback = object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                setOptimalAudioDevice()
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                setOptimalAudioDevice()
            }
        }
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
    }

    fun stop() {
        audioDeviceCallback?.let {
            audioManager.unregisterAudioDeviceCallback(it)
            audioDeviceCallback = null
        }

        audioManager.mode = AudioManager.MODE_NORMAL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
            @Suppress("DEPRECATION")
            audioManager.stopBluetoothSco()
        }
    }

    fun reevaluateAudioDevice() {
        setOptimalAudioDevice()
    }

    private fun setOptimalAudioDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = audioManager.availableCommunicationDevices
            
            val headset = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }

            if (headset != null) {
                audioManager.setCommunicationDevice(headset)
            } else {
                val speaker = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speaker != null) {
                    audioManager.setCommunicationDevice(speaker)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val isBluetoothOn = audioManager.isBluetoothScoOn || audioManager.isBluetoothA2dpOn
            @Suppress("DEPRECATION")
            val isWiredOn = audioManager.isWiredHeadsetOn

            if (isBluetoothOn) {
                @Suppress("DEPRECATION")
                audioManager.startBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = true
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
            } else if (isWiredOn) {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = true
            }
        }
    }
}
