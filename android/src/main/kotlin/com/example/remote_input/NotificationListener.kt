package com.example.remote_input

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.remote_input.Models.NotificationWear
import java.lang.System.currentTimeMillis
import java.util.*

class NotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        instance = this
        Log.d(TAG, "NotificationListener SERVICE CONNECTED")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        instance = null
        Log.d(TAG, "NotificationListener SERVICE DISCONNECTED")
        // Note: Android may call requestRebind() here if app is in foreground
    }

    override fun onNotificationPosted(statusBarNotification: StatusBarNotification) {
        Log.i(TAG, "onNotificationPosted: ${statusBarNotification.packageName}")

        val packageName = statusBarNotification.packageName
        val notification = statusBarNotification.notification
        val extrasBundle = notification.extras
        val sbnKey = statusBarNotification.key

        // Reuse the existing id when this sbn.key is an update to a
        // notification we've already seen, otherwise mint a new one from
        // postTime. CallStyle notifications (Messenger, Pixel Dialer…)
        // fire NotificationManager.notify() 2–3 times within <100 ms
        // while the Person/action supplier resolves — each post gets a
        // fresh postTime. Keying our id off sbn.key keeps it stable
        // across those updates so onNotificationRemoved's dismiss id
        // still matches what the watch currently shows.
        val idString = sbnKeyToIdMap[sbnKey] ?: statusBarNotification.postTime.toString()

        val intent = Intent(NOTIFICATION_INTENT)
        // 置入APP包裝名稱
        intent.putExtra(NOTIFICATION_PACKAGE_NAME, packageName)

        // 提取通知類別 (category)
        val category = notification.category
        intent.putExtra(NOTIFICATION_CATEGORY, category ?: "")

        // 提取 CallStyle 的 callType（Android 12+ / API 31+）。
        // 1=INCOMING (ringing), 2=ONGOING (active, including outgoing),
        // 3=SCREENING, 0=unknown/not a CallStyle notification.
        // We key off the string "android.callType" so this also works on
        // API < 31 when a dialer happens to set the extra manually.
        val callType = if (category == "call") {
            extrasBundle.getInt("android.callType", 0)
        } else 0
        intent.putExtra(NOTIFICATION_CALL_TYPE, callType)

        val extraTitle = extrasBundle.getCharSequence(Notification.EXTRA_TITLE).toString()
        val extraText = extrasBundle.getCharSequence(Notification.EXTRA_TEXT).toString()

        if (extraTitle.isNotEmpty() and (extraTitle != "null")) {
            intent.putExtra(NOTIFICATION_PACKAGE_TITLE, extraTitle)
        }

        if (extraText.isNotEmpty() and (extraText != "null")) {
            intent.putExtra(NOTIFICATION_PACKAGE_MESSAGE, extraText)
        }

        mNotificationObject = NotificationWear()
        mNotificationObject!!.id = idString
        // 置入ID(時間標籤)
        intent.putExtra(NOTIFICATION_ID, idString)

        mNotificationObject!!.packageName = packageName
        mNotificationObject!!.tag = statusBarNotification.tag
        mNotificationObject!!.key = sbnKey
        mNotificationObject!!.bundle = extrasBundle
        if (notification.actions != null) {
            for (action in notification.actions) {
                // Store all actions
                mNotificationObject!!.actions.add(action)

                if (action.remoteInputs != null) { // make remoteInputs contained in the action
                    Log.d(TAG, "There is remote input action in notification")
                    val remoteInputs = action.remoteInputs
                    val remoteInputArrayList = ArrayList(listOf(*remoteInputs))
                    mNotificationObject!!.remoteInputs.addAll(remoteInputArrayList)
                    mNotificationObject!!.pendingIntent = action.actionIntent
                    intent.putExtra(NOTIFICATION_REMOTE_INPUT, "$remoteInputArrayList")
                }
            }

            // Store all action titles
            val actionTitles = ArrayList<String>()
            for (action in notification.actions) {
                if (action.title != null) {
                    actionTitles.add(action.title.toString())
                } else {
                    actionTitles.add("Unknown Action")
                }
            }
            intent.putStringArrayListExtra(NOTIFICATION_ACTIONS_TITLES, actionTitles)
            intent.putExtra(NOTIFICATION_ACTIONS_COUNT, actionTitles.size)
        }

        if (extraTitle.isNotEmpty() and (extraText != "null") && extraText.isNotEmpty() and (extraText != "null")) {
            Log.d(TAG, "Normal notification bundle: $mNotificationObject was received.")
            notificationsMap[idString] = mNotificationObject!!
            // Store bidirectional key mappings for dismiss sync. Safe to
            // overwrite on updates — idString is already the stable one.
            sbnKeyToIdMap[sbnKey] = idString
            idToSbnKeyMap[idString] = sbnKey
            sendBroadcast(intent)
        }
    }

    override fun onNotificationRemoved(statusBarNotification: StatusBarNotification) {
        val sbnKey = statusBarNotification.key
        val customId = sbnKeyToIdMap.remove(sbnKey)
        if (customId != null) {
            idToSbnKeyMap.remove(customId)
            notificationsMap.remove(customId)
            Log.d(TAG, "Notification removed: sbnKey=$sbnKey, customId=$customId")

            val intent = Intent(NOTIFICATION_REMOVED_INTENT)
            intent.putExtra(NOTIFICATION_ID, customId)
            intent.putExtra(NOTIFICATION_PACKAGE_NAME, statusBarNotification.packageName)
            sendBroadcast(intent)
        } else {
            Log.d(TAG, "Notification removed but no mapping found: sbnKey=$sbnKey")
        }
    }

    private fun extractWearNotification(statusBarNotification: StatusBarNotification): NotificationWear? {
        val notificationWear = NotificationWear()
        notificationWear.packageName = statusBarNotification.packageName

        for (action in statusBarNotification.notification.actions) {
            if (action != null && action.remoteInputs != null) {
                notificationWear.remoteInputs.addAll(ArrayList(listOf(*action.remoteInputs)))
            }
        }
        notificationWear.bundle = statusBarNotification.notification.extras
        notificationWear.tag =
            statusBarNotification.tag //TODO find how to pass Tag with sending PendingIntent, might fix Hangout problem
        notificationWear.pendingIntent = statusBarNotification.notification.contentIntent
        return notificationWear
    }

    private fun createIntent(notificationWear: NotificationWear): Intent {
        val intent = Intent(NOTIFICATION_INTENT)
        intent.putExtra(NOTIFICATION_PACKAGE_NAME, notificationWear.packageName)
        val extras = notificationWear.bundle
        if (extras != null) {
            val extraText = extras.getCharSequence(Notification.EXTRA_TEXT)
            if (extraText != null) intent.putExtra(
                NOTIFICATION_PACKAGE_MESSAGE,
                extraText.toString()
            )
            val extraTitle = extras.getCharSequence(Notification.EXTRA_TITLE)
            if (extraTitle != null) intent.putExtra(
                NOTIFICATION_PACKAGE_TITLE,
                extraTitle.toString()
            )
        }
        return intent
    }

    companion object {
        const val TAG = "NOTIFICATION_LISTENER"
        const val NOTIFICATION_INTENT = "notification_intent"
        const val NOTIFICATION_REMOVED_INTENT = "notification_removed_intent"
        const val NOTIFICATION_PACKAGE_NAME = "notification_package_name"
        const val NOTIFICATION_PACKAGE_TITLE = "notification_package_title"
        const val NOTIFICATION_PACKAGE_MESSAGE = "notification_package_message"
        const val NOTIFICATION_ID = "notification_id"
        const val NOTIFICATION_REMOTE_INPUT = "remote_input"
        const val NOTIFICATION_ACTIONS_TITLES = "notification_actions_titles"
        const val NOTIFICATION_ACTIONS_COUNT = "notification_actions_count"
        const val NOTIFICATION_CATEGORY = "notification_category"
        const val NOTIFICATION_CALL_TYPE = "notification_call_type"
        var mNotificationObject: NotificationWear? = null
        var notificationsMap = hashMapOf<String, NotificationWear>()

        /// Bidirectional key mappings: StatusBarNotification.key ↔ our custom ID
        var sbnKeyToIdMap = hashMapOf<String, String>()
        var idToSbnKeyMap = hashMapOf<String, String>()

        // Track service connection status
        @Volatile
        var isConnected: Boolean = false

        // Static reference to the service instance for cancelNotification calls
        @Volatile
        var instance: NotificationListener? = null

        /**
         * Returns a snapshot of all currently active notifications in the
         * status bar, each as a HashMap suitable for sending to Dart.
         * Also populates the key mappings so dismiss sync works for these
         * pre-existing notifications.
         */
        fun getActiveNotificationsSnapshot(): List<HashMap<String, Any?>> {
            val listener = instance ?: return emptyList()
            val sbns: Array<StatusBarNotification>
            try {
                sbns = listener.activeNotifications ?: return emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "getActiveNotifications failed: ${e.message}")
                return emptyList()
            }

            val result = mutableListOf<HashMap<String, Any?>>()
            for (sbn in sbns) {
                val notification = sbn.notification
                val extras = notification.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                if (title.isEmpty() && text.isEmpty()) continue
                if (title == "null" && text == "null") continue

                val category = notification.category ?: ""
                val callType = if (category == "call") {
                    extras.getInt("android.callType", 0)
                } else 0

                // Generate a stable ID based on sbn.key hash + postTime
                val idString = sbn.postTime.toString()
                val sbnKey = sbn.key

                // Store in maps for dismiss sync
                sbnKeyToIdMap[sbnKey] = idString
                idToSbnKeyMap[idString] = sbnKey

                // Check for remote input support
                var hasRemoteInput = false
                var remoteInputSymbol: String? = null
                val actionTitles = ArrayList<String>()

                if (notification.actions != null) {
                    // Also populate notificationsMap for remote reply
                    val wear = NotificationWear()
                    wear.id = idString
                    wear.key = sbnKey
                    wear.packageName = sbn.packageName
                    wear.tag = sbn.tag
                    wear.bundle = extras

                    for (action in notification.actions) {
                        wear.actions.add(action)
                        if (action.title != null) {
                            actionTitles.add(action.title.toString())
                        }
                        if (action.remoteInputs != null) {
                            hasRemoteInput = true
                            val ri = action.remoteInputs
                            val riList = ArrayList(listOf(*ri))
                            wear.remoteInputs.addAll(riList)
                            wear.pendingIntent = action.actionIntent
                            remoteInputSymbol = "$riList"
                        }
                    }
                    notificationsMap[idString] = wear
                }

                val map = HashMap<String, Any?>()
                map["id"] = idString
                map["packageName"] = sbn.packageName
                map["packageTitle"] = title.split("@")[0]
                map["packageMessage"] = text
                map["remoteInputSymbol"] = remoteInputSymbol
                map["actionTitles"] = actionTitles
                map["actionsCount"] = actionTitles.size
                map["category"] = category
                map["callType"] = callType
                result.add(map)
            }
            Log.d(TAG, "getActiveNotificationsSnapshot: ${result.size} notifications")
            return result
        }
    }
}
