package com.example.remote_input

import android.app.Activity
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
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
    context!!.registerReceiver(receiver, intentFilter)

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
    if (!TextUtils.isEmpty(flat)) {
      val names = flat.split(":").toTypedArray()
      for (name in names) {
        val componentName = ComponentName.unflattenFromString(name)
        val nameMatch = TextUtils.equals(packageName, componentName?.packageName)
        if (nameMatch) {
          return true
        }
      }
    }
    return false
  }

  private fun handleNotificationPermissions(activity: Activity) {
    if (!permissionGiven()) {
      val intent = Intent(ACTION_NOTIFICATION_LISTENER_SETTINGS)
      activity.startActivity(intent)
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

        for (remoteInput in remoteInputs) {
//          getDetailsOfNotification(remoteInput)
          val key = remoteInput?.resultKey
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

//  private fun remoteInput(text: String): Boolean {
//    if (NotificationListener.mNotificationObject!=null){
//      val wear: NotificationWear = NotificationListener.mNotificationObject!!
//      if (wear != null){
//        val key = wear.remoteInputs[0].resultKey
//        val remoteInputs = wear.remoteInputs
//        var array = arrayOfNulls<RemoteInput>(remoteInputs.size)
//        array = remoteInputs.toArray(array)
//
//        val localIntent = Intent("remoteInput")
//        val localBundle = Bundle()
//        localBundle.putCharSequence(key, text)
//
//        RemoteInput.addResultsToIntent(array, localIntent, localBundle)
//        var pendingIntent = wear.pendingIntent
//        pendingIntent.send(context, 0, localIntent)
//        return true
//      }
//    } else {
//      println("Object is null");
//    }
//    return false
//  }

  //Most interesting code here - end
  private fun getDetailsOfNotification(remoteInput: RemoteInput) {
    //Some more details of RemoteInput... no idea what for but maybe it will be useful at some point
    val resultKey = remoteInput.resultKey
    val label = remoteInput.label.toString()
    val canFreeForm = remoteInput.allowFreeFormInput
    if (remoteInput.choices != null && remoteInput.choices.isNotEmpty()) {
      val possibleChoices = arrayOfNulls<String>(remoteInput.choices.size)
      for (i in remoteInput.choices.indices) {
        possibleChoices[i] = remoteInput.choices[i].toString()
      }
    }
  }
}

