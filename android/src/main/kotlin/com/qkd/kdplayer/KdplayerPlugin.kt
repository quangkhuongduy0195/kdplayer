package com.qkd.kdplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.NonNull

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.BasicMessageChannel
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.nio.ByteBuffer
import io.flutter.plugin.common.EventChannel.EventSink
import io.flutter.plugin.common.EventChannel.StreamHandler
import io.flutter.plugin.common.BinaryCodec
import java.io.ByteArrayOutputStream
import android.content.ServiceConnection
import android.content.ComponentName
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.IBinder
import androidx.localbroadcastmanager.content.LocalBroadcastManager

/** KdplayerPlugin */
class KdplayerPlugin: FlutterPlugin, MethodCallHandler {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var context: Context
  private lateinit var channel: MethodChannel
  private lateinit var stateChannel: EventChannel
  private lateinit var metadataChannel: EventChannel
  private lateinit var currentChannel: EventChannel
  private lateinit var durationChannel: EventChannel
  private lateinit var defaultArtworkChannel: BasicMessageChannel<ByteBuffer>
  private lateinit var metadataArtworkChannel: BasicMessageChannel<ByteBuffer>
  private lateinit var intent: Intent
  private lateinit var service: KDPlayerService

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    context = flutterPluginBinding.applicationContext
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "kdplayer")
    channel.setMethodCallHandler(this)

    stateChannel = EventChannel(flutterPluginBinding.binaryMessenger, "kdplayer/stateEvents")
    stateChannel.setStreamHandler(stateStreamHandler)
    metadataChannel = EventChannel(flutterPluginBinding.binaryMessenger, "kdplayer/metadataEvents")
    metadataChannel.setStreamHandler(metadataStreamHandler)

    currentChannel = EventChannel(flutterPluginBinding.binaryMessenger, "kdplayer/currentEvents")
    currentChannel.setStreamHandler(currentPositionStreamHandler)

    durationChannel = EventChannel(flutterPluginBinding.binaryMessenger, "kdplayer/durationEvents")
    durationChannel.setStreamHandler(durationStreamHandler)

    // Channel for default artwork
    defaultArtworkChannel = BasicMessageChannel(flutterPluginBinding.binaryMessenger, "kdplayer/setArtwork", BinaryCodec.INSTANCE)
    defaultArtworkChannel.setMessageHandler { message, result -> run {
      val array = message!!.array();
      val image = BitmapFactory.decodeByteArray(array, 0, array.size);
      service.setDefaultArtwork(image)
      result.reply(null)
    }
    }

    // Channel for metadata artwork
    metadataArtworkChannel = BasicMessageChannel(flutterPluginBinding.binaryMessenger, "kdplayer/getArtwork", BinaryCodec.INSTANCE)
    metadataArtworkChannel.setMessageHandler { message, result -> run {
        if (service.metadataArtwork == null) {
          result.reply(null)
        } else {
          val stream = ByteArrayOutputStream()
          service.metadataArtwork!!.compress(Bitmap.CompressFormat.JPEG, 100, stream)
          val array = stream.toByteArray();
          val byteBuffer = ByteBuffer.allocateDirect(array.size);
          byteBuffer.put(array)
          result.reply(byteBuffer)
        }
      }
    }

    // Start service
    intent = Intent(context, KDPlayerService::class.java)
    context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT)
    context.startService(intent)
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    when (call.method) {
      "set" -> {
        val args = call.arguments<ArrayList<String>>()
        service.setMediaItem(args[0], args[1])
      }
      "play" -> {
        service.play()
      }
      "pause" -> {
        service.pause()
      }
      "stop" -> {
        service.stop()
      }
      "setSeekTo" -> {
        val args = call.arguments<Double>()
        service.setSeekTo(args.toLong());
      }
      else -> {
        result.notImplemented()
      }
    }

    result.success(1)
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    stateChannel.setStreamHandler(null)
    metadataChannel.setStreamHandler(null)
    defaultArtworkChannel.setMessageHandler(null)
    metadataArtworkChannel.setMessageHandler(null)
    context.unbindService(serviceConnection)
    context.stopService(intent)
  }

  /** Defines callbacks for service binding, passed to bindService() */
  private val serviceConnection = object : ServiceConnection {
    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
      val binder = iBinder as KDPlayerService.LocalBinder
      service = binder.getService()
    }

    // Called when the connection with the service disconnects unexpectedly.
    // The service should be running in a different process.
    override fun onServiceDisconnected(componentName: ComponentName) {
    }
  }

  /** Handler for playback state changes, passed to setStreamHandler() */
  private var stateStreamHandler = object : StreamHandler {
    private var eventSink: EventSink? = null

    override fun onListen(arguments: Any?, events: EventSink?) {
      eventSink = events
      LocalBroadcastManager.getInstance(context).registerReceiver(broadcastReceiver,
        IntentFilter(KDPlayerService.ACTION_STATE_CHANGED))
    }

    override fun onCancel(arguments: Any?) {
      eventSink = null
      LocalBroadcastManager.getInstance(context).unregisterReceiver(broadcastReceiver)
    }

    // Broadcast receiver for playback state changes, passed to registerReceiver()
    private var broadcastReceiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        if (intent != null) {
          val received = intent.getBooleanExtra(KDPlayerService.ACTION_STATE_CHANGED_EXTRA, false)
          eventSink?.success(received)
        }
      }
    }
  }

  /** Handler for new metadata, passed to setStreamHandler() */
  private var metadataStreamHandler = object : StreamHandler {
    private var eventSink: EventSink? = null

    override fun onListen(arguments: Any?, events: EventSink?) {
      eventSink = events
      LocalBroadcastManager.getInstance(context).registerReceiver(broadcastReceiver,
        IntentFilter(KDPlayerService.ACTION_NEW_METADATA))
    }

    override fun onCancel(arguments: Any?) {
      eventSink = null
      LocalBroadcastManager.getInstance(context).unregisterReceiver(broadcastReceiver)
    }

    // Broadcast receiver for new metadata, passed to registerReceiver()
    private var broadcastReceiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        if (intent != null) {
          val received = intent.getStringArrayListExtra(KDPlayerService.ACTION_NEW_METADATA_EXTRA)
          eventSink?.success(received)
        }
      }
    }
  }

  private var currentPositionStreamHandler = object  : StreamHandler {
    private var eventSink: EventSink? = null

    override fun onListen(arguments: Any?, events: EventSink?) {
      eventSink = events
      LocalBroadcastManager.getInstance(context).registerReceiver(broadcastReceiver,
        IntentFilter(KDPlayerService.ACTION_CURRENT_CHANGED))
    }

    override fun onCancel(arguments: Any?) {
      eventSink = null
      LocalBroadcastManager.getInstance(context).unregisterReceiver(broadcastReceiver)
    }

    private var broadcastReceiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        if (intent != null) {
          val received = intent.getLongExtra(KDPlayerService.ACTION_CURRENT_CHANGED_EXTRA, 0)
          eventSink?.success(received.toDouble())
        }
      }
    }
  }

  private var durationStreamHandler = object  : StreamHandler {
    private var eventSink: EventSink? = null

    override fun onListen(arguments: Any?, events: EventSink?) {
      eventSink = events
      LocalBroadcastManager.getInstance(context).registerReceiver(broadcastReceiver,
        IntentFilter(KDPlayerService.ACTION_DURATION_CHANGED))
    }

    override fun onCancel(arguments: Any?) {
      eventSink = null
      LocalBroadcastManager.getInstance(context).unregisterReceiver(broadcastReceiver)
    }

    private var broadcastReceiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        if (intent != null) {
          val received = intent.getLongExtra(KDPlayerService.ACTION_DURATION_CHANGED_EXTRA, 0)
          eventSink?.success(received.toDouble())
        }
      }
    }
  }
}
