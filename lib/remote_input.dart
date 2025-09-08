import 'dart:async';
import 'dart:convert';
import 'package:flutter/services.dart';
import 'dart:io' show Platform;

/// Exception thrown when notification operations fail
class NotificationException implements Exception {
  final String _cause;

  /// Creates a NotificationException with the given cause
  NotificationException(this._cause);

  @override
  String toString() {
    return _cause;
  }
}

/// Represents an action available on a notification
class NotificationAction {
  /// The index of this action in the notification's action list
  final int index;
  
  /// The title/label of this action
  final String title;

  /// Creates a NotificationAction with the given index and title
  NotificationAction({
    required this.index,
    required this.title,
  });

  /// Creates a NotificationAction from a map representation
  factory NotificationAction.fromMap(Map<String, dynamic> map, int index) {
    return NotificationAction(
      index: index,
      title: map['title'] ?? 'Action $index',
    );
  }

  /// Converts this NotificationAction to a map representation
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

/// Represents a notification event received from the system
class NotificationEvent {
  /// Unique identifier for this notification (timestamp in milliseconds since epoch)
  String id;
  
  /// Package name of the app that posted this notification
  String packageName;
  
  /// Title of the notification
  String packageTitle;
  
  /// Message content of the notification
  String packageMessage;
  
  /// Whether this notification supports remote input (reply functionality)
  bool withRemoteInput;
  
  /// Symbol or text associated with remote input functionality
  String? remoteInputSymbol;
  
  /// List of actions available on this notification
  List<NotificationAction> actions;

  /// Creates a NotificationEvent with the given parameters
  NotificationEvent({
    required this.id,
    required this.packageName,
    required this.packageTitle,
    required this.packageMessage,
    this.withRemoteInput = false,
    this.remoteInputSymbol,
    this.actions = const [],
  });

  /// Creates a NotificationEvent from a map representation
  factory NotificationEvent.fromMap(Map<dynamic, dynamic> map) {
    String id = map['id'];
    String name = map['packageName'];
    String title = map['packageTitle'];
    title = title.split('@')[0];
    String message = map['packageMessage'];
    final remoteInputSymbol = map['remoteInputSymbol'];
    bool withRemoteInput = remoteInputSymbol != null;
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
    );
  }

  /// Converts this NotificationEvent to a map representation
  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'packageName': packageName,
      'packageTitle': packageTitle,
      'packageMessage': packageMessage,
      'withRemoteInput': withRemoteInput,
      'remoteInputSymbol': remoteInputSymbol,
      'actions': actions.map((a) => a.toMap()).toList(),
    };
  }

  /// Converts this NotificationEvent to a JSON string
  String toJson() {
    final map = <String, dynamic>{
      'id': id,
      'title': packageTitle,
      'message': packageMessage,
      'remote_input': withRemoteInput,
    };
    return jsonEncode(map);
  }

  /// Converts this NotificationEvent to a watch-compatible string format
  /// Truncates message to 128 characters for watch display
  String toWatchString() {
    final map = <String, dynamic>{
      'id': id,
      'title': packageTitle,
      'message': packageMessage.length > 128 
          ? packageMessage.substring(0, 128)
          : packageMessage,
      'reply': withRemoteInput,
    };
    return jsonEncode(map);
  }

  @override
  String toString() {
    return "Notification Event\n - ID: $id\n - Package Name: $packageName\n - Package Title: $packageTitle\n - Package Message: $packageMessage\n - Remote Input: $remoteInputSymbol";
  }
}

/// Creates a NotificationEvent from raw data received from platform channels
NotificationEvent _notificationEvent(dynamic data) {
  return NotificationEvent.fromMap(data);
}

/// A Flutter plugin for listening to Android notifications and interacting with them
/// 
/// This plugin provides functionality to:
/// - Listen to incoming notifications
/// - Reply to notifications that support remote input
/// - Trigger notification actions
/// - Get available notification actions
class RemoteInput {
  static const MethodChannel _methodChannel =
      MethodChannel('flutter.io/remote_input/methodChannel');
  static const EventChannel _eventChannel =
      EventChannel('flutter.io/remote_input/eventChannel');

  /// Gets the platform version
  static Future<String> get platformVersion async {
    final String version =
        await _methodChannel.invokeMethod('getPlatformVersion');
    return version;
  }

  /// Sends a reply to a notification that supports remote input
  /// 
  /// [text] The reply text to send
  /// [id] The notification ID to reply to
  /// 
  /// Returns true if the reply was sent successfully
  static Future<bool> remoteReply(String text, String id) async {
    final bool isSuccessful = await _methodChannel.invokeMethod('remoteInput',
        {"text": text, "id": id});
    return isSuccessful;
  }

  /// Triggers a specific notification action by index
  /// 
  /// [notificationId] The ID of the notification
  /// [actionIndex] The index of the action to trigger
  /// 
  /// Returns true if the action was triggered successfully
  static Future<bool> triggerAction(
      String notificationId, int actionIndex) async {
    final bool isSuccessful = await _methodChannel.invokeMethod(
        'triggerAction', {"id": notificationId, "actionIndex": actionIndex});
    return isSuccessful;
  }

  /// Gets all available actions for a notification
  /// 
  /// [notificationId] The ID of the notification
  /// 
  /// Returns a list of action titles
  static Future<List<String>> getNotificationActions(
      String notificationId) async {
    final List<dynamic> actions = await _methodChannel
        .invokeMethod('getNotificationActions', {"id": notificationId});
    return actions.map((action) => action.toString()).toList();
  }

  late Stream<NotificationEvent> _notificationStream;

  /// Stream of incoming notification events
  /// 
  /// Note: This functionality is only available on Android
  /// 
  /// Throws [NotificationException] if called on non-Android platforms
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
}
