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
  bool withRemoteInput;
  String? remoteInputSymbol;

  NotificationEvent({
    required this.id,
    required this.packageName,
    required this.packageTitle,
    required this.packageMessage,
    this.withRemoteInput = false,
    this.remoteInputSymbol,
  });

  factory NotificationEvent.fromMap(Map<dynamic, dynamic> map) {
    String id = map['id'];
    String name = map['packageName'];
    String title = map['packageTitle'];
    title = title.split('@')[0];
    String message = map['packageMessage'];
    final remoteInputSymbol = map['remoteInputSymbol'];
    bool withRemoteInput = remoteInputSymbol != null ? true : false;

    return NotificationEvent(
      id: id,
      packageName: name,
      packageTitle: title,
      packageMessage: message,
      withRemoteInput: withRemoteInput,
      remoteInputSymbol: remoteInputSymbol
    );
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
    return jsonEncode(map);
  } // For Watch

  @override
  String toString() {
    return "通知事件 Notification Event \n - ID: $id - Package Name: $packageName \n - Package Title: $packageTitle \n - Package Message: $packageMessage - Remote Input: $remoteInputSymbol";
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
