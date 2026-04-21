import 'dart:async';
import 'dart:convert';
import 'package:flutter/services.dart';
import 'dart:io' show Platform;

class NotificationException implements Exception {
  String _cause;

  NotificationException(this._cause);

  @override
  String toString() {
    return _cause;
  }
}

class NotificationAction {
  final int index;
  final String title;

  NotificationAction({
    required this.index,
    required this.title,
  });

  factory NotificationAction.fromMap(Map<String, dynamic> map, int index) {
    return NotificationAction(
      index: index,
      title: map['title'] ?? 'Action $index',
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'index': index,
      'title': title,
    };
  }

  @override
  String toString() {
    return 'NotificationAction{index: $index, title: $title}';
  }
}

/// CallStyle callType values from Android's Notification.CallStyle
/// (mirrors Notification.CALL_TYPE_* constants, API 31+).
class NotificationCallType {
  /// Not a CallStyle notification, or dialer predates API 31.
  static const int unknown = 0;

  /// Ringing incoming call.
  static const int incoming = 1;

  /// Active call — covers both an answered incoming call AND any
  /// outgoing call the user placed from the dialer.
  static const int ongoing = 2;

  /// Call screening (e.g. Google Call Screen).
  static const int screening = 3;
}

class NotificationEvent {
  String id; //id is timestamp (millisecondsSinceEpoch)
  String packageName;
  String packageTitle;
  String packageMessage;
  bool withRemoteInput;
  String? remoteInputSymbol;
  List<NotificationAction> actions;
  String? category; // Notification category (call, msg, email, social, etc.)

  /// For [category] == "call": CallStyle's callType. See [NotificationCallType].
  /// 0 on non-call notifications or when the dialer did not set
  /// Notification.EXTRA_CALL_TYPE.
  int callType;
  DateTime receivedAt = DateTime.now();

  NotificationEvent({
    required this.id,
    required this.packageName,
    required this.packageTitle,
    required this.packageMessage,
    this.withRemoteInput = false,
    this.remoteInputSymbol,
    this.actions = const [],
    this.category,
    this.callType = NotificationCallType.unknown,
  });

  factory NotificationEvent.fromMap(Map<dynamic, dynamic> map) {
    String id = map['id'];
    String name = map['packageName'];
    String title = map['packageTitle'];
    title = title.split('@')[0];
    String message = map['packageMessage'];
    final remoteInputSymbol = map['remoteInputSymbol'];
    bool withRemoteInput = remoteInputSymbol != null ? true : false;
    final category = map['category']; // Extract notification category
    final callType = (map['callType'] as int?) ?? NotificationCallType.unknown;
    List<NotificationAction> actions = [];
    if (map['actions'] != null) {
      for (var i = 0; i < map['actions'].length; i++) {
        actions.add(NotificationAction.fromMap(map['actions'][i], i));
      }
    }

    return NotificationEvent(
      id: id,
      packageName: name,
      packageTitle: title,
      packageMessage: message,
      withRemoteInput: withRemoteInput,
      remoteInputSymbol: remoteInputSymbol,
      actions: actions,
      category: category,
      callType: callType,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'packageName': packageName,
      'packageTitle': packageTitle,
      'packageMessage': packageMessage,
      'withRemoteInput': withRemoteInput,
      'remoteInputSymbol': remoteInputSymbol,
      'actions': actions.map((a) => a.toMap()).toList(),
      'category': category,
      'callType': callType,
    };
  }

  String toJson() {
    Map<String, dynamic> map = Map();
    map['id'] = id;
    // map['2'] = packageName;
    map['title'] = packageTitle;
    map['message'] = packageMessage;
    map['remote_input'] = withRemoteInput;
    return jsonEncode(map);
  }

  String toWatchString() {
    Map<String, dynamic> map = Map();
    map['id'] = id;
    map['title'] = packageTitle;
    if (packageMessage.length > 128) {
      // split packageMessage to 128 characters
      map['message'] = packageMessage.substring(0, 128);
    } else {
      map['message'] = packageMessage;
    }
    map['reply'] = withRemoteInput;
    map['calling'] = category == "call" ? true : false;
    return jsonEncode(map);
  } // For Watch

  @override
  String toString() {
    return "通知事件 Notification Event \n - ID: $id - Package Name: $packageName \n - Package Title: $packageTitle \n - Package Message: $packageMessage(category: $category) \n - Remote Input: $remoteInputSymbol";
  }
}

NotificationEvent _notificationEvent(dynamic data) {
  print('NotificationEvent: $data');
  return new NotificationEvent.fromMap(data);
}

/// Event fired when a notification is dismissed/removed from the phone.
class NotificationRemovedEvent {
  final String id;
  final String packageName;

  NotificationRemovedEvent({required this.id, required this.packageName});

  factory NotificationRemovedEvent.fromMap(Map<dynamic, dynamic> map) {
    return NotificationRemovedEvent(
      id: map['id'] ?? '',
      packageName: map['packageName'] ?? '',
    );
  }

  Map<String, dynamic> toMap() => {'id': id, 'packageName': packageName};

  @override
  String toString() => 'NotificationRemovedEvent(id: $id, packageName: $packageName)';
}

class RemoteInput {
  static const MethodChannel _methodChannel =
      const MethodChannel('flutter.io/remote_input/methodChannel');
  static const EventChannel _eventChannel =
      const EventChannel('flutter.io/remote_input/eventChannel');
  static const EventChannel _removedEventChannel =
      const EventChannel('flutter.io/remote_input/removedEventChannel');

  static Future<String> get platformVersion async {
    final String version =
        await _methodChannel.invokeMethod('getPlatformVersion');
    return version;
  }

  static Future<bool> remoteReply(String text, String id) async {
    final bool isSuccessful = await _methodChannel.invokeMethod('remoteInput',
        {"text": text, "id": id}); // Reply to the notification with id
    return isSuccessful;
  }

  /// Trigger a specific notification action by index
  static Future<bool> triggerAction(
      String notificationId, int actionIndex) async {
    final bool isSuccessful = await _methodChannel.invokeMethod(
        'triggerAction', {"id": notificationId, "actionIndex": actionIndex});
    return isSuccessful;
  }

  /// Get all available actions for a notification
  static Future<List<String>> getNotificationActions(
      String notificationId) async {
    final List<dynamic> actions = await _methodChannel
        .invokeMethod('getNotificationActions', {"id": notificationId});
    return actions.map((action) => action.toString()).toList();
  }

  /// Check if notification access permission is granted
  /// Only applicable on Android
  static Future<bool> isNotificationAccessGranted() async {
    if (!Platform.isAndroid) return false;
    try {
      final bool isGranted = await _methodChannel.invokeMethod('isNotificationAccessGranted');
      return isGranted;
    } catch (e) {
      return false;
    }
  }

  /// Open notification listener settings page
  /// Only applicable on Android
  static Future<bool> openNotificationListenerSettings() async {
    if (!Platform.isAndroid) return false;
    try {
      final bool result = await _methodChannel.invokeMethod('openNotificationListenerSettings');
      return result;
    } catch (e) {
      return false;
    }
  }

  /// Check if the NotificationListenerService is actually connected and running
  /// This is different from permission check - permission can be granted but service not connected
  /// Only applicable on Android
  static Future<bool> isServiceConnected() async {
    if (!Platform.isAndroid) return false;
    try {
      final bool isConnected = await _methodChannel.invokeMethod('isServiceConnected');
      return isConnected;
    } catch (e) {
      print('Error checking service connection: $e');
      return false;
    }
  }

  /// Request the system to rebind the NotificationListenerService
  /// This is useful after system updates or when the service becomes disconnected
  /// Only applicable on Android (requires Android 7.0+)
  static Future<bool> requestRebind() async {
    if (!Platform.isAndroid) return false;
    try {
      final bool result = await _methodChannel.invokeMethod('requestRebind');
      return result;
    } catch (e) {
      print('Error requesting rebind: $e');
      return false;
    }
  }

  /// Get all currently active notifications in the status bar.
  /// Returns a list of NotificationEvent objects representing what's
  /// currently shown in the notification shade.
  static Future<List<NotificationEvent>> getActiveNotifications() async {
    if (!Platform.isAndroid) return [];
    try {
      final List<dynamic> result =
          await _methodChannel.invokeMethod('getActiveNotifications');
      return result
          .map((item) => NotificationEvent.fromMap(item as Map<dynamic, dynamic>))
          .toList();
    } catch (e) {
      print('Error getting active notifications: $e');
      return [];
    }
  }

  /// Cancel/dismiss a notification on the phone by its custom ID.
  /// This allows the watch to dismiss a notification and have it removed from
  /// the phone's notification shade as well.
  static Future<bool> cancelNotification(String id) async {
    if (!Platform.isAndroid) return false;
    try {
      final bool result =
          await _methodChannel.invokeMethod('cancelNotification', {"id": id});
      return result;
    } catch (e) {
      print('Error cancelling notification: $e');
      return false;
    }
  }

  late Stream<NotificationEvent> _notificationStream;
  late Stream<NotificationRemovedEvent> _notificationRemovedStream;

  Stream<NotificationEvent> get notificationStream {
    if (Platform.isAndroid) {
      _notificationStream = _eventChannel
          .receiveBroadcastStream()
          .map((event) => _notificationEvent(event));
      return _notificationStream;
    }
    throw NotificationException(
        'Notification API exclusively available on Android!');
  }

  /// Stream of notification removal events.
  /// Fires when the user (or system) dismisses a notification on the phone.
  Stream<NotificationRemovedEvent> get notificationRemovedStream {
    if (Platform.isAndroid) {
      _notificationRemovedStream = _removedEventChannel
          .receiveBroadcastStream()
          .map((event) => NotificationRemovedEvent.fromMap(event));
      return _notificationRemovedStream;
    }
    throw NotificationException(
        'Notification API exclusively available on Android!');
  }
}
