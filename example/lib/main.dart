import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:remote_input/remote_input.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _platformVersion = 'Unknown';
  late RemoteInput _remoteInput;
  late StreamSubscription<NotificationEvent> _subscription;
  List<NotificationEvent> _log = [];
  bool started = false;

  @override
  void initState() {
    super.initState();
    _remoteInput = new RemoteInput();
    initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    String platformVersion;
    // Platform messages may fail, so we use a try/catch PlatformException.
    try {
      platformVersion = await RemoteInput.platformVersion;
    } on PlatformException {
      platformVersion = 'Failed to get platform version.';
    }

    if (!mounted) return;

    setState(() {
      _platformVersion = platformVersion;
    });

    // startListening();
  }

  void onData(NotificationEvent event) {
    setState(() {
      _log.add(event);
    });
    print(event.toString());
  }

  void startListening() {
    print('start listening');
    try {
      _subscription = _remoteInput.notificationStream.listen(onData);
      setState(() {
        started = true;
      });
    } on NotificationException catch (exception) {
      print(exception);
    }
  }

  void stopListening() {
    print('stop listening');
    _subscription.cancel();
    setState(() {
      started = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(

      theme: ThemeData.dark(useMaterial3: true),
      home: Scaffold(
        appBar: AppBar(
          title: Text('Plugin Running on: $_platformVersion'),
        ),
        body: Center(
          child: ListView.builder(
              itemCount: _log.length,
              reverse: true,
              itemBuilder: (BuildContext context, int idx) {
                final entry = _log[idx];
                return ListTile(
                  leading: IconButton(
                    icon: Icon(Icons.reply),
                    onPressed: () => showReplyDialog(context, entry.id),
                  ),
                  title: Text(entry.packageName.toString().split('.').last),
                  subtitle: Text(entry.packageMessage.toString()),
                );
              }),
        ),
        floatingActionButton: new FloatingActionButton(
          onPressed: started ? stopListening : startListening,
          tooltip: 'Start/Stop sensing',
          child: started ? Icon(Icons.stop) : Icon(Icons.play_arrow),
        ),
      ),
    );
  }

  void showReplyDialog(BuildContext context, String id) {
    TextEditingController textEditingController = TextEditingController();
    showDialog(
      context: context,
      barrierDismissible: true,
      builder: (BuildContext context) {
        return ReplyDialog(textEditingController, () {
          print(textEditingController.text);
          RemoteInput.remoteReply(textEditingController.text, id)
              .then((value) => print(value));
          Navigator.of(context).pop();
        });
      },
    );
  }
}

//
class ReplyDialog extends StatelessWidget {
  final TextEditingController controller;
  final VoidCallback callback;
  const ReplyDialog(this.controller, this.callback, {Key? key})
      : super(key: key);

  @override
  Widget build(BuildContext context) {
    return CupertinoAlertDialog(
      title: Text("Reply"),
      content: CupertinoTextField(
        style: TextStyle(color: Colors.white),
        controller: controller,
      ),
      actions: <Widget>[
        CupertinoButton(
          child: Text('Send'),
          onPressed: callback,
        ),
      ],
    );
  }
}
