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

class NotificationEvent {
  String id; //id is timestamp (millisecondsSinceEpoch)
  String packageName;
  String packageTitle;
  String packageMessage;
  bool withRemoteInput = false;

  NotificationEvent({
    required this.id,
    required this.packageName,
    required this.packageTitle,
    required this.packageMessage,
  });

  factory NotificationEvent.fromMap(Map<dynamic, dynamic> map) {
    String id = map['id'];
    String name = map['packageName'];
    String title = map['packageTitle'];
    title = title.split('@')[0];
    String message = map['packageMessage'];

    return NotificationEvent(
      id: id,
      packageName: name,
      packageTitle: title,
      packageMessage: message,
    );
  }

  String toJson() {
    Map<String, dynamic> map = Map();
    map['id'] = id;
    // map['2'] = packageName;
    map['title'] = packageTitle;
    map['message'] = packageMessage;
    return jsonEncode(map);
  }

  @override
  String toString() {
    return "通知事件 Notification Event \n - ID: $id - Package Name: $packageName \n - Package Title: $packageTitle \n - Package Message: $packageMessage";
  }
}

NotificationEvent _notificationEvent(dynamic data) {
  return new NotificationEvent.fromMap(data);
}

class RemoteInput {
  static const MethodChannel _methodChannel =
      const MethodChannel('flutter.io/remote_input/methodChannel');
  static const EventChannel _eventChannel =
      const EventChannel('flutter.io/remote_input/eventChannel');

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

  late Stream<NotificationEvent> _notificationStream;

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
