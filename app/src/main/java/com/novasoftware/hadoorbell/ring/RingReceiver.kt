package com.novasoftware.hadoorbell.ring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives the Home Assistant Companion `command_broadcast_intent` that starts a ring, and the
 * cancel broadcast sent by the Decline action or by an automation.
 */
class RingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            DoorbellRing.ACTION_RING -> DoorbellRing.show(context, RingRequest.from(intent.extras))
            DoorbellRing.ACTION_CANCEL_RING -> DoorbellRing.cancel(context)
        }
    }
}
