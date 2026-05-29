package com.novasoftware.hadoorbell.webrtc

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.novasoftware.hadoorbell.integrations.FrigateSignalingClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoSink
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WebRtcManager(
    private val context: Context,
    private val signalingClient: FrigateSignalingClient,
    private val coroutineScope: CoroutineScope
) {
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var remoteAudioTrack: AudioTrack? = null
    private var eglBase: EglBase? = null
    private var audioDeviceModule: JavaAudioDeviceModule? = null

    init {
        synchronized(WebRtcManager::class.java) {
            if (!isInitialized) {
                val options = PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                    .setEnableInternalTracer(true)
                    .createInitializationOptions()
                PeerConnectionFactory.initialize(options)
                isInitialized = true
            }
        }
    }

    companion object {
        private var isInitialized = false
    }

    fun getEglBaseContext(): EglBase.Context {
        if (eglBase == null) {
            eglBase = EglBase.create()
        }
        return eglBase!!.eglBaseContext
    }

    fun startConnection(videoSink: VideoSink, enableMicrophone: Boolean, onError: (String) -> Unit) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        setOptimalAudioDevice(audioManager)

        coroutineScope.launch {
            try {
                signalingClient.connect()
                
                val eglContext = getEglBaseContext()

                audioDeviceModule = JavaAudioDeviceModule.builder(context)
                    .setUseHardwareAcousticEchoCanceler(true)
                    .setUseHardwareNoiseSuppressor(true)
                    .createAudioDeviceModule()
                
                peerConnectionFactory = PeerConnectionFactory.builder()
                    .setAudioDeviceModule(audioDeviceModule)
                    .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglContext))
                    .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglContext, true, true))
                    .createPeerConnectionFactory()

                if (enableMicrophone) {
                    val audioConstraints = MediaConstraints()
                    val audioSource = peerConnectionFactory!!.createAudioSource(audioConstraints)
                    localAudioTrack = peerConnectionFactory!!.createAudioTrack("ARDAMSa0", audioSource)
                    localAudioTrack?.setEnabled(true)
                }

                val rtcConfig = PeerConnection.RTCConfiguration(emptyList())
                rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                
                val iceGatheringComplete = kotlinx.coroutines.CompletableDeferred<Unit>()
                
                peerConnection = peerConnectionFactory!!.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                    override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
                    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
                    override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                        if (state == PeerConnection.IceGatheringState.COMPLETE) {
                            iceGatheringComplete.complete(Unit)
                        }
                    }
                    override fun onIceCandidate(candidate: IceCandidate?) {}
                    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                    override fun onAddStream(stream: MediaStream?) {}
                    override fun onRemoveStream(stream: MediaStream?) {}
                    override fun onDataChannel(dataChannel: DataChannel?) {}
                    override fun onRenegotiationNeeded() {}
                    override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                        val track = receiver?.track()
                        if (track is VideoTrack) {
                            remoteVideoTrack = track
                            track.addSink(videoSink)
                        } else if (track is AudioTrack) {
                            Log.d("WebRtcManager", "Remote audio track received")
                            remoteAudioTrack = track // Prevent garbage collection
                            track.setEnabled(true)
                            
                            // Android sometimes resets audio routing when a new track starts. 
                            // Force it back to the optimal device instantly.
                            val trackAudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                            setOptimalAudioDevice(trackAudioManager)
                        }
                    }
                })



                // Add Video Transceiver (Receive Only)
                peerConnection?.addTransceiver(
                    MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, 
                    RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
                )
                
                // Add Audio Transceiver based on connection mode
                if (enableMicrophone && localAudioTrack != null) {
                    peerConnection?.addTransceiver(
                        localAudioTrack,
                        RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV)
                    )
                } else {
                    peerConnection?.addTransceiver(
                        MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
                        RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
                    )
                }
                coroutineScope.launch {
                    signalingClient.getIceCandidates().collect { candidateStr ->
                        try {
                            val candidate = IceCandidate("0", 0, candidateStr)
                            peerConnection?.addIceCandidate(candidate)
                        } catch (e: Exception) {
                            Log.e("WebRtcManager", "Error adding ICE candidate", e)
                        }
                    }
                }

                // Create Offer
                val offer = suspendCancellableCoroutine { cont ->
                    peerConnection?.createOffer(object : SdpObserver {
                        override fun onCreateSuccess(desc: SessionDescription?) {
                            if (desc != null) cont.resume(desc) else cont.resumeWithException(
                                Exception("Empty offer")
                            )
                        }

                        override fun onSetSuccess() {}
                        override fun onCreateFailure(error: String?) {
                            Log.e("WebRtcManager", "Create offer failed: $error")
                            cont.resumeWithException(Exception("Create offer failed: $error"))
                        }

                        override fun onSetFailure(error: String?) {}
                    }, MediaConstraints())
                }

                suspendCancellableCoroutine { cont ->
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            cont.resume(Unit)
                        }

                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(error: String?) {
                            Log.e("WebRtcManager", "Set local description failed: $error")
                            cont.resumeWithException(Exception("Set local desc failed: $error"))
                        }
                    }, offer)
                }

                // Wait for ICE candidates to be gathered into the local description (max 2 seconds)
                kotlinx.coroutines.withTimeoutOrNull(2000) {
                    iceGatheringComplete.await()
                }

                // Send offer to signaling server (use updated local description if possible)
                val localSdp = peerConnection?.localDescription?.description ?: offer.description
                val answerSdpString = signalingClient.sendOffer(localSdp)
                
                // Set remote description
                val answerDesc = SessionDescription(SessionDescription.Type.ANSWER, answerSdpString)
                suspendCancellableCoroutine { cont ->
                    peerConnection?.setRemoteDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            cont.resume(Unit)
                        }

                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(error: String?) {
                            Log.e("WebRtcManager", "Set remote description failed: $error")
                            cont.resumeWithException(Exception("Set remote desc failed: $error"))
                        }
                    }, answerDesc)
                }
            } catch (e: Exception) {
                Log.e("WebRtcManager", "Error establishing connection", e)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onError(e.message ?: "Unknown error occurred")
                }
            }
        }
    }



    fun toggleStreamMute(muted: Boolean) {
        remoteAudioTrack?.setEnabled(!muted)
    }

    fun disconnect() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_NORMAL
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        }

        coroutineScope.launch(Dispatchers.IO) {
            signalingClient.disconnect()
            peerConnection?.close()
            peerConnectionFactory?.dispose()
            audioDeviceModule?.release()
            eglBase?.release()
        }
    }

    private fun setOptimalAudioDevice(audioManager: AudioManager) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val devices = audioManager.availableCommunicationDevices
            
            val hasHeadset = devices.any {
                it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == 26 /* TYPE_BLE_HEADSET */ ||
                it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET
            }

            if (!hasHeadset) {
                // No headset connected. Android will default to the Earpiece in communication mode.
                // We want it to use the loud built-in speaker instead.
                val speaker = devices.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speaker != null) {
                    audioManager.setCommunicationDevice(speaker)
                }
            } else {
                // A headset is connected! Do absolutely nothing and let Android's native
                // automatic routing handle it instantly.
                audioManager.clearCommunicationDevice()
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn =
                !audioManager.isWiredHeadsetOn && !audioManager.isBluetoothScoOn && !audioManager.isBluetoothA2dpOn
        }
    }
}
