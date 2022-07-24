package com.example.remote_input

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.flutter.plugin.common.EventChannel.EventSink
import java.util.*

class NotificationReceiver internal constructor(private val eventSink: EventSink) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(NotificationListener.NOTIFICATION_ID)
        val packageName = intent.getStringExtra(NotificationListener.NOTIFICATION_PACKAGE_NAME)
        val packageTitle = intent.getStringExtra(NotificationListener.NOTIFICATION_PACKAGE_TITLE)
        val packageMessage = intent.getStringExtra(NotificationListener.NOTIFICATION_PACKAGE_MESSAGE)
        val map = HashMap<String, Any?>()

        map["id"] = id
        map["packageName"] = packageName
        map["packageTitle"] = packageTitle
        map["packageMessage"] = packageMessage

        if (packageTitle!=null&& packageMessage!=null) {
            eventSink.success(map)
            Log.i(TAG, "Map $map Received.")
        }
    }

    companion object {
        const val TAG = "NOTIFICATION_RECEIVER"
    }
}