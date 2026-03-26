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
    private var mPreviousNotificationKey: String? = null
    private var mPreviousId: String? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        instance = this
        Log.i(TAG, "🟢 NotificationListener SERVICE CONNECTED")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        instance = null
        Log.w(TAG, "🔴 NotificationListener SERVICE DISCONNECTED")
        // Note: Android may call requestRebind() here if app is in foreground
    }

    override fun onNotificationPosted(statusBarNotification: StatusBarNotification) {
        Log.i(TAG, "onNotificationPosted: ${statusBarNotification.packageName}")
//        if (statusBarNotification.tag != null) {
        val millisSinceEpoch: Long = currentTimeMillis()
        val idString = millisSinceEpoch.toString()
        // Retrieve extra object from notification to extract payload.
        val packageName = statusBarNotification.packageName
        val notification = statusBarNotification.notification
        val extrasBundle = notification.extras

        val intent = Intent(NOTIFICATION_INTENT)
        // 置入APP包裝名稱
        intent.putExtra(NOTIFICATION_PACKAGE_NAME, packageName)

        // 提取通知類別 (category)
        val category = notification.category
        intent.putExtra(NOTIFICATION_CATEGORY, category ?: "")

        val extraTitle = extrasBundle.getCharSequence(Notification.EXTRA_TITLE).toString()
        val extraText = extrasBundle.getCharSequence(Notification.EXTRA_TEXT).toString()


        if (extraTitle.isNotEmpty() and (extraTitle != "null")) {
            // 置入通知包裝的標題
            Log.i(TAG, "標題: ${extraTitle.length}")
            intent.putExtra(NOTIFICATION_PACKAGE_TITLE, extraTitle)
        }

        if (extraText.isNotEmpty() and (extraText != "null")) {
            // 置入通知包裝的文字內容
            Log.i(TAG, "內容: ${extraText.length}")
            intent.putExtra(NOTIFICATION_PACKAGE_MESSAGE, extraText)
        }

        if (statusBarNotification.key != mPreviousNotificationKey || idString != mPreviousId) {
            mPreviousNotificationKey = statusBarNotification.key
            mPreviousId = idString

            mNotificationObject = NotificationWear()
            mNotificationObject!!.id = idString
            // 置入ID(時間標籤)
            intent.putExtra(NOTIFICATION_ID, idString)

            mNotificationObject!!.packageName = packageName
            mNotificationObject!!.tag = statusBarNotification.tag
            mNotificationObject!!.key = statusBarNotification.key
            mNotificationObject!!.bundle = extrasBundle
//                Log.i(TAG, "Notification is: $notification")
            if (notification.actions != null) {
                for (action in notification.actions) {
                    // Store all actions
                    mNotificationObject!!.actions.add(action)

                    if (action.remoteInputs != null) { // make remoteInputs contained in the action
                        Log.i(TAG, "There is remote input action in notification")
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
                Log.i(TAG, "Normal notification bundle: $mNotificationObject was received.")
                notificationsMap[idString] = mNotificationObject!!
                // Store bidirectional key mappings for dismiss sync
                val sbnKey = statusBarNotification.key
                sbnKeyToIdMap[sbnKey] = idString
                idToSbnKeyMap[idString] = sbnKey
                sendBroadcast(intent)
            }
//        mNotificationObject = extractWearNotification(statusBarNotification)
//        val intent = mNotificationObject?.let { createIntent(it) }
        }
//        }
    }

    override fun onNotificationRemoved(statusBarNotification: StatusBarNotification) {
        val sbnKey = statusBarNotification.key
        val customId = sbnKeyToIdMap.remove(sbnKey)
        if (customId != null) {
            idToSbnKeyMap.remove(customId)
            notificationsMap.remove(customId)
            Log.i(TAG, "Notification removed: sbnKey=$sbnKey, customId=$customId")

            val intent = Intent(NOTIFICATION_REMOVED_INTENT)
            intent.putExtra(NOTIFICATION_ID, customId)
            intent.putExtra(NOTIFICATION_PACKAGE_NAME, statusBarNotification.packageName)
            sendBroadcast(intent)
        } else {
            Log.i(TAG, "Notification removed but no mapping found: sbnKey=$sbnKey")
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
                result.add(map)
            }
            Log.i(TAG, "getActiveNotificationsSnapshot: ${result.size} notifications")
            return result
        }
    }
}
