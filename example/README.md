# Remote Input Plugin Example

This example demonstrates how to use the `remote_input` plugin to listen to Android notifications and interact with them.

## Features Demonstrated

- 📱 **Notification Listening**: Real-time monitoring of incoming notifications
- 💬 **Remote Replies**: Send replies to notifications that support it (e.g., messaging apps)
- ⚡ **Action Triggering**: Execute notification actions programmatically
- 🎯 **Event Filtering**: Process and display notification data

## Setup

1. **Grant Notification Access**: 
   - Go to Settings > Apps > [Your App] > Permissions
   - Enable "Notification access" for this app
   - This is required for the plugin to work

2. **Run the Example**:
   ```bash
   flutter run
   ```

## How to Use

1. **Start Listening**: Tap the play button to begin monitoring notifications
2. **Receive Notifications**: Send yourself messages from apps like WhatsApp, Telegram, or SMS
3. **View Events**: See incoming notifications listed in the app
4. **Reply**: Tap the reply icon on notifications that support remote input
5. **Stop Listening**: Tap the stop button to pause monitoring

## Code Structure

- `main.dart`: Contains the main application logic
- `MyApp`: Main app widget with notification stream handling
- `ReplyDialog`: Dialog for composing replies to notifications

## Key Components

### Notification Stream
```dart
_subscription = _remoteInput.notificationStream.listen(onData);
```

### Reply Functionality
```dart
RemoteInput.remoteReply(textEditingController.text, id)
```

### Event Processing
```dart
void onData(NotificationEvent event) {
  setState(() {
    _log.add(event);
  });
}
```

## Testing

To test the example:

1. Install messaging apps (WhatsApp, Telegram, etc.)
2. Start the example app and begin listening
3. Send yourself messages from another device
4. Observe notifications appearing in the app
5. Try replying to supported notifications

## Requirements

- Android device (iOS not supported)
- Notification access permission
- Messaging apps for testing remote input functionality
