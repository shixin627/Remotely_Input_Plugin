# Remote Input Plugin

A Flutter plugin that provides notification listener capabilities with remote input functionality for Android applications. This plugin allows your Flutter app to listen to incoming notifications and interact with them, including replying to notifications that support remote input.

## Features

- 🔔 Listen to incoming Android notifications in real-time
- 💬 Reply to notifications that support remote input (e.g., messaging apps)
- ⚡ Trigger notification actions programmatically
- 📱 Get available actions for notifications
- 🎯 Filter and process notification events

## Platform Support

| Platform | Support |
|----------|---------|
| Android  | ✅ Full support |
| iOS      | ❌ Not supported |

## Installation

Add this to your package's `pubspec.yaml` file:

```yaml
dependencies:
  remote_input: ^0.0.2
```

Run the following command to install the package:

```bash
flutter pub get
```

## Permissions

This plugin requires notification access permission on Android. The user must manually enable notification access for your app in the device settings.

### Android Setup

Add the following permission to your `android/app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" />
```

## Usage

### Basic Example

```dart
import 'package:remote_input/remote_input.dart';
import 'dart:async';

class NotificationListener {
  late RemoteInput _remoteInput;
  late StreamSubscription<NotificationEvent> _subscription;

  void startListening() {
    _remoteInput = RemoteInput();
    
    try {
      _subscription = _remoteInput.notificationStream.listen((event) {
        print('Received notification from: ${event.packageName}');
        print('Title: ${event.packageTitle}');
        print('Message: ${event.packageMessage}');
        print('Supports reply: ${event.withRemoteInput}');
      });
    } on NotificationException catch (e) {
      print('Error: $e');
    }
  }

  void stopListening() {
    _subscription.cancel();
  }
}
```

### Replying to Notifications

```dart
// Reply to a notification that supports remote input
void replyToNotification(String notificationId, String replyText) async {
  try {
    bool success = await RemoteInput.remoteReply(replyText, notificationId);
    if (success) {
      print('Reply sent successfully');
    } else {
      print('Failed to send reply');
    }
  } catch (e) {
    print('Error sending reply: $e');
  }
}
```

### Triggering Notification Actions

```dart
// Get available actions for a notification
void getNotificationActions(String notificationId) async {
  List<String> actions = await RemoteInput.getNotificationActions(notificationId);
  print('Available actions: $actions');
}

// Trigger a specific action
void triggerAction(String notificationId, int actionIndex) async {
  bool success = await RemoteInput.triggerAction(notificationId, actionIndex);
  if (success) {
    print('Action triggered successfully');
  }
}
```

## API Reference

### Classes

#### `RemoteInput`
Main class providing notification listening and interaction capabilities.

**Static Methods:**
- `Future<String> get platformVersion` - Gets the platform version
- `Future<bool> remoteReply(String text, String id)` - Sends a reply to a notification
- `Future<bool> triggerAction(String notificationId, int actionIndex)` - Triggers a notification action
- `Future<List<String>> getNotificationActions(String notificationId)` - Gets available actions

**Instance Properties:**
- `Stream<NotificationEvent> get notificationStream` - Stream of incoming notifications

#### `NotificationEvent`
Represents a notification event with all relevant data.

**Properties:**
- `String id` - Unique notification identifier
- `String packageName` - Source app package name
- `String packageTitle` - Notification title
- `String packageMessage` - Notification message content
- `bool withRemoteInput` - Whether the notification supports replies
- `String? remoteInputSymbol` - Remote input symbol/hint
- `List<NotificationAction> actions` - Available actions

#### `NotificationAction`
Represents an action available on a notification.

**Properties:**
- `int index` - Action index
- `String title` - Action title/label

#### `NotificationException`
Exception thrown when notification operations fail.

## Example App

See the [example](example/) directory for a complete demonstration of the plugin's capabilities, including:

- Setting up notification listening
- Displaying incoming notifications
- Replying to notifications
- Managing notification actions

## Important Notes

1. **Permissions**: Users must manually grant notification access permission in Android settings
2. **Android Only**: This plugin only works on Android devices
3. **Background Processing**: Consider the app's lifecycle when implementing notification listening
4. **Testing**: Use real messaging apps to test remote input functionality

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

