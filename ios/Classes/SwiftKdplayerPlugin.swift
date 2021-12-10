import Flutter
import UIKit

public class SwiftKdplayerPlugin: NSObject, FlutterPlugin {
    
    static let instance = SwiftKdplayerPlugin()
    private let player = KdPlayer()
    
    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "kdplayer", binaryMessenger: registrar.messenger())
        registrar.addMethodCallDelegate(instance, channel: channel)
        
        let stateChannel = FlutterEventChannel(name: "kdplayer/stateEvents", binaryMessenger: registrar.messenger())
        stateChannel.setStreamHandler(StateStreamHandler())
        
        let metadataChannel = FlutterEventChannel(name: "kdplayer/metadataEvents", binaryMessenger: registrar.messenger())
        metadataChannel.setStreamHandler(MetadataStreamHandler())
        
        let durationChannel = FlutterEventChannel(name: "kdplayer/durationEvents", binaryMessenger: registrar.messenger())
        durationChannel.setStreamHandler(DurationStreamHandler())
        
        let currentChannel = FlutterEventChannel(name: "kdplayer/currentEvents", binaryMessenger: registrar.messenger())
        currentChannel.setStreamHandler(CurrentStreamHandler())
        
        // Channel for default artwork
        let defaultArtworkChannel = FlutterBasicMessageChannel(name: "kdplayer/setArtwork", binaryMessenger: registrar.messenger(), codec: FlutterBinaryCodec())
        defaultArtworkChannel.setMessageHandler { message, result in
            let image = UIImage(data: message as! Data)
            instance.player.defaultArtwork = image
            instance.player.setArtwork(image)
            result(nil)
        }
        
        // Channel for metadata artwork
        let metadataArtworkChannel = FlutterBasicMessageChannel(name: "kdplayer/getArtwork", binaryMessenger: registrar.messenger(), codec: FlutterBinaryCodec())
        metadataArtworkChannel.setMessageHandler { message, result in
            let data = instance.player.metadataArtwork?.jpegData(compressionQuality: 1.0)
            result(data)
        }
        
    }
    
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "set":
            let args = call.arguments as! Array<String>
            player.setMediaItem(args[0], args[1])
        case "play":
            player.play()
        case "pause":
            player.pause()
        case "stop":
            player.stop()
        case "setSeekTo":
            let args = call.arguments as! Double
            player.setSeekTo(timer: args)
        default:
            result(FlutterMethodNotImplemented)
        }
        
        result(1)
    }
}

/** Handler for playback state changes, passed to setStreamHandler() */
class StateStreamHandler: NSObject, FlutterStreamHandler {
    private var eventSink: FlutterEventSink?
    
    func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
        eventSink = events
        NotificationCenter.default.addObserver(self, selector: #selector(onRecieve(_:)), name: NSNotification.Name(rawValue: "state"), object: nil)
        return nil
    }
    
    func onCancel(withArguments arguments: Any?) -> FlutterError? {
        eventSink = nil
        return nil
    }
    
    // Notification receiver for playback state changes, passed to addObserver()
    @objc private func onRecieve(_ notification: Notification) {
        if let metadata = notification.userInfo!["state"] {
            eventSink?(metadata)
        }
    }
}

/** Handler for new metadata, passed to setStreamHandler() */
class MetadataStreamHandler: NSObject, FlutterStreamHandler {
    private var eventSink: FlutterEventSink?
    
    func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
        eventSink = events
        NotificationCenter.default.addObserver(self, selector: #selector(onRecieve(_:)), name: NSNotification.Name(rawValue: "metadata"), object: nil)
        return nil
    }
    
    func onCancel(withArguments arguments: Any?) -> FlutterError? {
        eventSink = nil
        return nil
    }
    
    // Notification receiver for new metadata, passed to addObserver()
    @objc private func onRecieve(_ notification: Notification) {
        if let metadata = notification.userInfo!["metadata"] {
            eventSink?(metadata)
        }
    }
}

class DurationStreamHandler: NSObject, FlutterStreamHandler {
    private var eventSink: FlutterEventSink?
    
    func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
        eventSink = events
        NotificationCenter.default.addObserver(self, selector: #selector(onRecieve(_:)), name: NSNotification.Name(rawValue: "duration"), object: nil)
        return nil
    }
    
    func onCancel(withArguments arguments: Any?) -> FlutterError? {
        eventSink = nil
        return nil
    }
    
    // Notification receiver for new metadata, passed to addObserver()
    @objc private func onRecieve(_ notification: Notification) {
        if let duration = notification.userInfo!["duration"] {
            eventSink?(duration)
        }
    }
}


class CurrentStreamHandler: NSObject, FlutterStreamHandler {
    private var eventSink: FlutterEventSink?
    
    func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
        eventSink = events
        NotificationCenter.default.addObserver(self, selector: #selector(onRecieve(_:)), name: NSNotification.Name(rawValue: "current"), object: nil)
        return nil
    }
    
    func onCancel(withArguments arguments: Any?) -> FlutterError? {
        eventSink = nil
        return nil
    }
    
    // Notification receiver for new metadata, passed to addObserver()
    @objc private func onRecieve(_ notification: Notification) {
        if let current = notification.userInfo!["current"] {
            eventSink?(current)
        }
    }
}
