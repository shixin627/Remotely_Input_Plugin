package com.example.remote_input

import android.app.Activity
import android.app.RemoteInput
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
class RemoteInputPlugin: FlutterPlugin, MethodCallHandler, EventChannel.StreamHandler, ActivityAware {
  private lateinit var eventChannel: EventChannel
  private lateinit var methodChannel : MethodChannel
  companion object {
    const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"
    const val ACTION_NOTIFICATION_LISTENER_SETTINGS = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
    const val EVENT_CHANNEL_NAME = "flutter.io/remote_input/eventChannel"
    const val METHOD_CHANNEL_NAME = "flutter.io/remote_input/methodChannel"
  }

  private var eventSink: EventSink? = null
  private var context: Context? = null

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    //method channel
    methodChannel = MethodChannel(flutterPluginBinding.binaryMessenger, METHOD_CHANNEL_NAME)
    methodChannel.setMethodCallHandler(this)
    //event channel
    eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, EVENT_CHANNEL_NAME)
    eventChannel.setStreamHandler(this)
    context = flutterPluginBinding.applicationContext;
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    // method channel
    methodChannel.setMethodCallHandler(null)
    // event channel
    eventChannel.setStreamHandler(null)
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
      else -> {
        result.notImplemented()
      }
    }
  }

  override fun onListen(arguments: Any?, events: EventSink?) {
    println("Start Listening RemoteInputPlugin")
    eventSink = events

    val receiver = eventSink?.let { NotificationReceiver(it) }
    val intentFilter = IntentFilter()
    intentFilter.addAction(NotificationListener.NOTIFICATION_INTENT)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      context!!.registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED)
    }

    /* Start the notification service once permission has been given. */
    val listenerIntent = Intent(context, NotificationListener::class.java)
    context!!.startService(listenerIntent)
  }

  override fun onCancel(arguments: Any?) {
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
    handleNotificationPermissions(binding.activity)
  }

  override fun onDetachedFromActivity() {
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    handleNotificationPermissions(binding.activity)
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
