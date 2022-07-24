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

    override fun onNotificationPosted(statusBarNotification: StatusBarNotification) {
        if (statusBarNotification.tag != null) {
            val secondsSinceUnixEpoch: Long = currentTimeMillis() / 1000
            val idString = secondsSinceUnixEpoch.toString()
            // Retrieve extra object from notification to extract payload.
            val packageName = statusBarNotification.packageName
            val notification = statusBarNotification.notification
            val extrasBundle = notification.extras

            val intent = Intent(NOTIFICATION_INTENT)
            // 置入APP包裝名稱
            intent.putExtra(NOTIFICATION_PACKAGE_NAME, packageName)

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
                    Log.i(TAG, "成功抓取到action，取得回覆資格")
                    for (action in notification.actions) {
                        if (action.remoteInputs != null) { // make remoteInputs contained in the action
                            val remoteInputs = action.remoteInputs
                            val remoteInputArrayList = ArrayList(listOf(*remoteInputs))
                            mNotificationObject!!.remoteInputs.addAll(remoteInputArrayList)
                            mNotificationObject!!.pendingIntent = action.actionIntent
                        }
                    }
                }

                if (extraTitle.isNotEmpty() and (extraText != "null") && extraText.isNotEmpty() and (extraText != "null")) {
                    Log.i(TAG, "Normal notification bundle: $mNotificationObject was received.")
                    notificationsMap[idString] = mNotificationObject!!
                    sendBroadcast(intent)
                }
//        mNotificationObject = extractWearNotification(statusBarNotification)
//        val intent = mNotificationObject?.let { createIntent(it) }
            }
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
        notificationWear.tag = statusBarNotification.tag //TODO find how to pass Tag with sending PendingIntent, might fix Hangout problem
        notificationWear.pendingIntent = statusBarNotification.notification.contentIntent
        return notificationWear
    }

    private fun createIntent(notificationWear: NotificationWear): Intent {
        val intent = Intent(NOTIFICATION_INTENT)
        intent.putExtra(NOTIFICATION_PACKAGE_NAME, notificationWear.packageName)
        val extras = notificationWear.bundle
        if (extras != null) {
            val extraText = extras.getCharSequence(Notification.EXTRA_TEXT)
            if (extraText != null) intent.putExtra(NOTIFICATION_PACKAGE_MESSAGE, extraText.toString())
            val extraTitle = extras.getCharSequence(Notification.EXTRA_TITLE)
            if (extraTitle != null) intent.putExtra(NOTIFICATION_PACKAGE_TITLE, extraTitle.toString())
        }
        return intent
    }

    companion object {
        const val TAG = "NOTIFICATION_LISTENER"
        const val NOTIFICATION_INTENT = "notification_intent"
        const val NOTIFICATION_PACKAGE_NAME = "notification_package_name"
        const val NOTIFICATION_PACKAGE_TITLE = "notification_package_title"
        const val NOTIFICATION_PACKAGE_MESSAGE = "notification_package_message"
        const val NOTIFICATION_ID = "notification_id"
        var mNotificationObject: NotificationWear? = null
        var notificationsMap = hashMapOf<String, NotificationWear>()
    }
}
