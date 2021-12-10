import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class Kdplayer {
  static const _methodChannel = MethodChannel('kdplayer');
  static const _metadataEvents = EventChannel('kdplayer/metadataEvents');
  static const _stateEvents = EventChannel('kdplayer/stateEvents');
  static const _currentEvents = EventChannel('kdplayer/currentEvents');
  static const _durationEvents = EventChannel('kdplayer/durationEvents');
  static const _defaultArtworkChannel =
      BasicMessageChannel("kdplayer/setArtwork", BinaryCodec());
  static const _metadataArtworkChannel =
      BasicMessageChannel("kdplayer/getArtwork", BinaryCodec());

  Stream<bool>? _stateStream;
  Stream<double>? _currentStream;
  Stream<double>? _durationStream;
  Stream<List<String>>? _metadataStream;

  Future<void> setMediaItem(String title, String url, [String? image]) async {
    await Future.delayed(const Duration(milliseconds: 500));
    await _methodChannel.invokeMethod('set', [title, url]);
    if (image != null) setDefaultArtwork(image);
  }

  Future<void> setSeekTo(double value) async {
    await Future.delayed(const Duration(milliseconds: 500));
    await _methodChannel.invokeMethod('setSeekTo', value);
  }

  /// Set default artwork
  Future<void> setDefaultArtwork(String image) async {
    await rootBundle.load(image).then((value) {
      _defaultArtworkChannel.send(value);
    });
  }

  Future<void> play() async {
    await _methodChannel.invokeMethod('play');
  }

  Future<void> pause() async {
    await _methodChannel.invokeMethod('pause');
  }

  /// Get artwork from metadata
  Future<Image?> getMetadataArtwork() async {
    final byteData = await _metadataArtworkChannel.send(ByteData(0));
    if (byteData == null) return null;

    return Image.memory(
      byteData.buffer.asUint8List(),
      key: UniqueKey(),
      fit: BoxFit.cover,
    );
  }

  /// Get the current position stream.
  Stream<double> get currentStream {
    _currentStream ??=
        _currentEvents.receiveBroadcastStream().map<double>((value) => value);
    return _currentStream!;
  }

  /// Get the current position stream.
  Stream<double> get durationtStream {
    _durationStream ??=
        _durationEvents.receiveBroadcastStream().map<double>((value) => value);
    return _durationStream!;
  }

  /// Get the playback state stream.
  Stream<bool> get stateStream {
    _stateStream ??=
        _stateEvents.receiveBroadcastStream().map<bool>((value) => value);
    return _stateStream!;
  }

  /// Get the metadata stream.
  Stream<List<String>> get metadataStream {
    _metadataStream ??=
        _metadataEvents.receiveBroadcastStream().map((metadata) {
      return metadata.map<String>((value) => value as String).toList();
    });
    return _metadataStream!;
  }
}
