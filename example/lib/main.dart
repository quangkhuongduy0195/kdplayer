import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:kdplayer/kdplayer.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final Kdplayer _kdplayer = Kdplayer();
  bool isPlaying = false;
  double durationPlayer = 0.1;
  double currentPlayer = 0.0;
  List<String>? metadata;
  @override
  void initState() {
    super.initState();
    initPlayer();
  }

  void initPlayer() {
    _kdplayer.setMediaItem('Radio Player',
        'https://www.bensound.com/bensound-music/bensound-ukulele.mp3');

    _kdplayer.stateStream.listen((value) {
      if (kDebugMode) {
        print(value);
      }
      setState(() {
        isPlaying = value;
      });
    });

    _kdplayer.metadataStream.listen((value) {
      setState(() {
        metadata = value;
      });
    });

    _kdplayer.currentStream.listen((value) {
      if (kDebugMode) {
        print("currentStream: $value");
      }
      if (value < 0) return;
      setState(() {
        currentPlayer = value;
      });
    });

    _kdplayer.durationtStream.listen((value) {
      if (kDebugMode) {
        print("durationtStream: $value");
      }
      setState(() {
        durationPlayer = value;
      });
    });
  }

  String intToTimeLeft(double? value) {
    if (value == null || value == 0 || value.isNaN) {
      return '00:00';
    }
    var aaa = value / 1000;
    var h = (aaa ~/ 3600);
    var m = ((aaa - h * 3600)) ~/ 60;
    var s = (aaa - (h * 3600) - (m * 60)).toInt();

    String hourLeft =
        h.toString().length < 2 ? "0" + h.toString() : h.toString();

    String minuteLeft =
        m.toString().length < 2 ? "0" + m.toString() : m.toString();

    String secondsLeft =
        s.toString().length < 2 ? "0" + s.toString() : s.toString();

    String result = "$hourLeft:$minuteLeft:$secondsLeft";
    if (h == 0) {
      result = "$minuteLeft:$secondsLeft";
    }
    return result;
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: [
                  IconButton(
                      onPressed: () {}, icon: const Icon(Icons.skip_previous)),
                  IconButton(
                      onPressed: () {
                        isPlaying ? _kdplayer.pause() : _kdplayer.play();
                      },
                      icon: isPlaying
                          ? const Icon(Icons.pause)
                          : const Icon(Icons.play_arrow)),
                  IconButton(
                      onPressed: () {}, icon: const Icon(Icons.skip_next)),
                ],
              ),
              Slider(
                value: currentPlayer,
                min: 0.0,
                max: durationPlayer,
                onChanged: (value) {
                  _kdplayer.setSeekTo(value);
                },
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20.0),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(intToTimeLeft(currentPlayer)),
                    Text(intToTimeLeft(durationPlayer)),
                  ],
                ),
              )
            ],
          ),
        ),
      ),
    );
  }
}
