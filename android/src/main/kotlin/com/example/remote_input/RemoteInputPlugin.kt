package com.example.remote_input

import android.app.Activity
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.text.TextUtils
import androidx.annotation.NonNull
import com.example.remote_input.Models.NotificationWear
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.EventChannel.EventSink
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

/** RemoteInputPlugin */
class RemoteInputPlugin: FlutterPlugin, MethodCallHandler, ActivityAware {
  private lateinit var eventChannel: EventChannel
  private lateinit var removedEventChannel: EventChannel
  private lateinit var methodChannel : MethodChannel
  companion object {
    const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"
    const val ACTION_NOTIFICATION_LISTENER_SETTINGS = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
    const val EVENT_CHANNEL_NAME = "flutter.io/remote_input/eventChannel"
    const val REMOVED_EVENT_CHANNEL_NAME = "flutter.io/remote_input/removedEventChannel"
    const val METHOD_CHANNEL_NAME = "flutter.io/remote_input/methodChannel"
  }

  private var eventSink: EventSink? = null
  private var removedEventSink: EventSink? = null
  private var context: Context? = null
  private var notificationReceiver: BroadcastReceiver? = null
  private var removedReceiver: BroadcastReceiver? = null

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    context = flutterPluginBinding.applicationContext

    //method channel
    methodChannel = MethodChannel(flutterPluginBinding.binaryMessenger, METHOD_CHANNEL_NAME)
    methodChannel.setMethodCallHandler(this)

    //event channel - notifications posted
    eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, EVENT_CHANNEL_NAME)
    eventChannel.setStreamHandler(object : EventChannel.StreamHandler {
      override fun onListen(arguments: Any?, events: EventSink?) {
        eventSink = events
        notificationReceiver = eventSink?.let { NotificationReceiver(it) }
        val intentFilter = IntentFilter()
        intentFilter.addAction(NotificationListener.NOTIFICATION_INTENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context!!.registerReceiver(notificationReceiver, intentFilter, Context.RECEIVER_EXPORTED)
        }
        val listenerIntent = Intent(context, NotificationListener::class.java)
        context!!.startService(listenerIntent)
      }
      override fun onCancel(arguments: Any?) {
        notificationReceiver?.let { context?.unregisterReceiver(it) }
        notificationReceiver = null
        eventSink = null
      }
    })

    //event channel - notifications removed
    removedEventChannel = EventChannel(flutterPluginBinding.binaryMessenger, REMOVED_EVENT_CHANNEL_NAME)
    removedEventChannel.setStreamHandler(object : EventChannel.StreamHandler {
      override fun onListen(arguments: Any?, events: EventSink?) {
        removedEventSink = events
        removedReceiver = object : BroadcastReceiver() {
          override fun onReceive(ctx: Context, intent: Intent) {
            val id = intent.getStringExtra(NotificationListener.NOTIFICATION_ID)
            val packageName = intent.getStringExtra(NotificationListener.NOTIFICATION_PACKAGE_NAME)
            val map = HashMap<String, Any?>()
            map["id"] = id
            map["packageName"] = packageName
            events?.success(map)
          }
        }
        val intentFilter = IntentFilter()
        intentFilter.addAction(NotificationListener.NOTIFICATION_REMOVED_INTENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context!!.registerReceiver(removedReceiver, intentFilter, Context.RECEIVER_EXPORTED)
        }
      }
      override fun onCancel(arguments: Any?) {
        removedReceiver?.let { context?.unregisterReceiver(it) }
        removedReceiver = null
        removedEventSink = null
      }
    })
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    methodChannel.setMethodCallHandler(null)
    eventChannel.setStreamHandler(null)
    removedEventChannel.setStreamHandler(null)
    notificationReceiver?.let { context?.unregisterReceiver(it) }
    removedReceiver?.let { context?.unregisterReceiver(it) }
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    when (call.method) {
      "getPlatformVersion" -> {
        result.success("Android ${android.os.Build.VERSION.RELEASE}")
      }
      "remoteInput" -> {
        val id: String? = call.argument("id")
        val text: String? = call.argument("text")
        if (text != null) {
          println("Remote input called: $text")
          var isSuccessful = remoteInput(text, id)
          result.success(isSuccessful)
        }
      }
      "triggerAction" -> {
        val id: String? = call.argument("id")
        val actionIndex: Int? = call.argument("actionIndex")
        if (id != null && actionIndex != null) {
          println("Trigger action called: action $actionIndex for notification $id")
          var isSuccessful = triggerNotificationAction(id, actionIndex)
          result.success(isSuccessful)
        } else {
          result.error("INVALID_ARGUMENTS", "id and actionIndex are required", null)
        }
      }
      "getNotificationActions" -> {
        val id: String? = call.argument("id")
        if (id != null) {
          val actions = getNotificationActions(id)
          result.success(actions)
        } else {
          result.error("INVALID_ARGUMENTS", "id is required", null)
        }
      }
      "isNotificationAccessGranted" -> {
        result.success(permissionGiven())
      }
      "openNotificationListenerSettings" -> {
        openNotificationListenerSettings()
        result.success(true)
      }
      "isServiceConnected" -> {
        result.success(isServiceConnected())
      }
      "requestRebind" -> {
        requestRebind()
        result.success(true)
      }
      "cancelNotification" -> {
        val id: String? = call.argument("id")
        if (id != null) {
          val success = cancelNotificationById(id)
          result.success(success)
        } else {
          result.error("INVALID_ARGUMENTS", "id is required", null)
        }
      }
      "getActiveNotifications" -> {
        val notifications = NotificationListener.getActiveNotificationsSnapshot()
        result.success(notifications)
      }
      else -> {
        result.notImplemented()
      }
    }
  }

  /**
   * Cancel a notification on the phone by our custom ID.
   * Looks up the StatusBarNotification key and calls cancelNotification on the
   * NotificationListenerService instance.
   */
  private fun cancelNotificationById(customId: String): Boolean {
    val sbnKey = NotificationListener.idToSbnKeyMap[customId]
    if (sbnKey == null) {
      android.util.Log.w("RemoteInputPlugin", "No sbnKey found for customId=$customId")
      return false
    }
    val listener = NotificationListener.instance
    if (listener == null) {
      android.util.Log.w("RemoteInputPlugin", "NotificationListener not connected")
      return false
    }
    return try {
      listener.cancelNotification(sbnKey)
      android.util.Log.i("RemoteInputPlugin", "Cancelled notification: sbnKey=$sbnKey")
      true
    } catch (e: Exception) {
      android.util.Log.e("RemoteInputPlugin", "Failed to cancel notification: ${e.message}")
      false
    }
  }

  private fun permissionGiven(): Boolean {
    val packageName = context!!.packageName
    val flat: String = Settings.Secure.getString(context!!.contentResolver,
            ENABLED_NOTIFICATION_LISTENERS)
    android.util.Log.d("RemoteInputPlugin", "🔍 Checking permission for package: $packageName")
    android.util.Log.d("RemoteInputPlugin", "📋 Enabled listeners: $flat")
    
    if (!TextUtils.isEmpty(flat)) {
      val names = flat.split(":").toTypedArray()
      for (name in names) {
        val componentName = ComponentName.unflattenFromString(name)
        val nameMatch = TextUtils.equals(packageName, componentName?.packageName)
        android.util.Log.d("RemoteInputPlugin", "🔎 Checking component: $name, match: $nameMatch")
        if (nameMatch) {
          android.util.Log.i("RemoteInputPlugin", "✅ Permission granted but service connection status unknown")
          return true
        }
      }
    }
    android.util.Log.w("RemoteInputPlugin", "❌ Permission NOT granted")
    return false
  }

  private fun handleNotificationPermissions(activity: Activity) {
    if (!permissionGiven()) {
      val intent = Intent(ACTION_NOTIFICATION_LISTENER_SETTINGS)
      activity.startActivity(intent)
    }
  }

  private fun openNotificationListenerSettings() {
    val intent = Intent(ACTION_NOTIFICATION_LISTENER_SETTINGS)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context?.startActivity(intent)
  }

  /**
   * Check if the NotificationListenerService is actually connected
   * Returns true only if both permission is granted AND service is connected
   */
  private fun isServiceConnected(): Boolean {
    if (!permissionGiven()) {
      android.util.Log.w("RemoteInputPlugin", "❌ Permission not granted, service cannot be connected")
      return false
    }

    val connected = NotificationListener.isConnected
    android.util.Log.i("RemoteInputPlugin", "🔌 Service connection status: $connected")
    return connected
  }

  /**
   * Request the system to rebind the NotificationListenerService
   * This is necessary after system updates or when service becomes disconnected
   */
  private fun requestRebind() {
    android.util.Log.i("RemoteInputPlugin", "🔄 Requesting NotificationListener service rebind...")

    try {
      // Method 1: Request rebind via system API (Android 7.0+)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val componentName = ComponentName(context!!, NotificationListener::class.java)
        NotificationListenerService.requestRebind(componentName)
        android.util.Log.i("RemoteInputPlugin", "✓ Rebind request sent via API")
      } else {
        // Method 2: For older Android versions, we need to toggle the permission
        android.util.Log.w("RemoteInputPlugin", "⚠️ Android < 7.0 detected, user needs to manually toggle permission")
        openNotificationListenerSettings()
      }
    } catch (e: Exception) {
      android.util.Log.e("RemoteInputPlugin", "❌ Failed to request rebind: ${e.message}")
      // Fallback: Open settings for user to manually toggle
      openNotificationListenerSettings()
    }
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    // handleNotificationPermissions(binding.activity)
  }

  override fun onDetachedFromActivity() {
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    // handleNotificationPermissions(binding.activity)
  }

  override fun onDetachedFromActivityForConfigChanges() {
  }

  private fun remoteInput(text: String, id: String?): Boolean {
    val notificationWear: NotificationWear? = NotificationListener.notificationsMap[id]
      if (notificationWear != null) {

        var remoteInputs = arrayOfNulls<RemoteInput>(notificationWear.remoteInputs.size)
        remoteInputs = notificationWear.remoteInputs.toArray(remoteInputs)

        val localIntent = Intent()
        localIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val localBundle = notificationWear.bundle

        for (remoteIn in remoteInputs) { // Renamed remoteInput to remoteIn to avoid conflict
          val key = remoteIn?.resultKey // Use remoteIn here
          println("remoteInput resultKey: $key")
          localBundle.putCharSequence(key, text)
        }

        RemoteInput.addResultsToIntent(remoteInputs, localIntent, localBundle)
        notificationWear.pendingIntent.send(context, 0, localIntent)
        return true
      } else {
        println("mNotificationObject is null");
        return false
      }
  }

  private fun triggerNotificationAction(id: String, actionIndex: Int): Boolean {
    val notificationWear: NotificationWear? = NotificationListener.notificationsMap[id]
    return if (notificationWear != null && actionIndex < notificationWear.actions.size) {
      val action = notificationWear.actions[actionIndex]
      val intent = Intent()
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      action.actionIntent.send(context, 0, intent)
      true
    } else {
      false
    }
  }

  private fun getNotificationActions(id: String): List<String>? {
    val notificationWear: NotificationWear? = NotificationListener.notificationsMap[id]
    return notificationWear?.actions?.map { it.title?.toString() ?: "Unknown Action" }
  }
}
