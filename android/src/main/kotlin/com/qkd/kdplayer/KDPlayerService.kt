package com.qkd.kdplayer

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.MediaMetadata
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.metadata.Metadata
import com.google.android.exoplayer2.metadata.MetadataOutput
import com.google.android.exoplayer2.metadata.icy.IcyInfo
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.google.android.exoplayer2.ui.PlayerNotificationManager.BitmapCallback
import com.google.android.exoplayer2.ui.PlayerNotificationManager.MediaDescriptionAdapter
import kotlinx.coroutines.*
import java.net.URL
import kotlin.reflect.KParameter

class KDPlayerService : Service(), Player.Listener, MetadataOutput {
    companion object {
        const val KdPlayer = "KdPlayer"
        const val NOTIFICATION_CHANNEL_ID = "kd_channel_id"
        const val NOTIFICATION_ID = 1
        const val ACTION_STATE_CHANGED = "state_changed"
        const val ACTION_STATE_CHANGED_EXTRA = "state"
        const val ACTION_NEW_METADATA = "matadata_changed"
        const val ACTION_NEW_METADATA_EXTRA = "matadata"
        const val ACTION_CURRENT_CHANGED = "current_changed"
        const val ACTION_CURRENT_CHANGED_EXTRA = "current"
        const val ACTION_DURATION_CHANGED = "duration_changed"
        const val ACTION_DURATION_CHANGED_EXTRA = "duration"
    }
    var metadataArtwork: Bitmap? = null
    private var durationSet : Boolean = false
    private var defaultArtwork: Bitmap? = null
    private var playerNotificationManager: PlayerNotificationManager? = null
    private var notificationTitle = ""
    private var isForegroundService = false
    private var metadataList: MutableList<String>? = null
    private var localBinder = LocalBinder()
    private val handler = Handler(Looper.getMainLooper())
    private val player: ExoPlayer by lazy {
        ExoPlayer.Builder(this).build()
    }
    private val localBroadcastManager: LocalBroadcastManager by lazy {
        LocalBroadcastManager.getInstance(this)
    }

    inner class LocalBinder : Binder() {
        // Return this instance of RadioPlayerService so clients can call public methods.
        fun getService(): KDPlayerService = this@KDPlayerService
    }

    override fun onBind(intent: Intent?): IBinder {
        return localBinder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.addListener(this)
//        player.addMetadataOutput(this)

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        playerNotificationManager?.setPlayer(null)
        player.release()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    fun setMediaItem(streamTitle: String, streamUrl: String) {
        Log.d(KdPlayer, "setMediaItem");

        val mediaItems: List<MediaItem> = runBlocking {
            GlobalScope.async {
                parseUrls(streamUrl).map { MediaItem.fromUri(it) }
            }.await()
        }

        metadataList = null
        defaultArtwork = null
        metadataArtwork = null
        notificationTitle = streamTitle
        playerNotificationManager?.invalidate() ?: createNotificationManager()
        sentCurrentPosition(0)
        sendDuration(0);
        player.stop()
        player.clearMediaItems()
        player.seekTo(0)
        player.addMediaItems(mediaItems)
        player.playWhenReady = true
        player.pause()
    }

    fun setDefaultArtwork(image: Bitmap) {
        defaultArtwork = image
        playerNotificationManager?.invalidate()
    }

    fun play() {
        handler.removeCallbacks(updateProgressAction)
        handler.post(updateProgressAction)
        player.playWhenReady = true

    }

    fun pause() {
        handler.removeCallbacks(updateProgressAction)
        player.playWhenReady = false
    }

    fun stop() {
        player.stop()
    }

    fun setSeekTo(value: Long){
        player.seekTo(value)
    }

    /** Extract URLs from user link. */
    private fun parseUrls(url: String): List<String> {
        var urls: List<String> = emptyList()

        urls = when (url.substringAfterLast(".")) {
            "pls" -> {
                URL(url).readText().lines().filter {
                    it.contains("=http") }.map {
                    it.substringAfter("=")
                }
            }
            "m3u" -> {
                val content = URL(url).readText().trim()
                listOf(content)
            }
            else -> {
                listOf(url)
            }
        }

        return urls
    }

    /** Creates a notification manager for background playback. */
    private fun createNotificationManager() {
        val mediaDescriptionAdapter = object : MediaDescriptionAdapter {
            override fun createCurrentContentIntent(player: Player): PendingIntent? {
                return null
            }
            override fun getCurrentLargeIcon(player: Player, callback: BitmapCallback): Bitmap? {
                metadataArtwork = downloadImage(metadataList?.get(2))
                if (metadataArtwork != null) callback.onBitmap(metadataArtwork!!)
                return defaultArtwork
            }
            override fun getCurrentContentTitle(player: Player): String {
                return metadataList?.get(0) ?: notificationTitle
            }
            override fun getCurrentContentText(player: Player): String? {
                return metadataList?.get(1)
            }
        }

        val notificationListener = object : PlayerNotificationManager.NotificationListener {
            override fun onNotificationPosted(notificationId: Int, notification: Notification, ongoing: Boolean) {
                if(ongoing && !isForegroundService) {
                    startForeground(notificationId, notification)
                    isForegroundService = true
                }
            }
            override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
                stopForeground(true)
                isForegroundService = false
                stopSelf()
            }
        }

        playerNotificationManager = PlayerNotificationManager.Builder(this, NOTIFICATION_ID, NOTIFICATION_CHANNEL_ID)
                                                                .setNotificationListener(notificationListener)
                                                                .setChannelNameResourceId(R.string.channel_name)
                                                                .setMediaDescriptionAdapter(mediaDescriptionAdapter)
                                                                .build()
        playerNotificationManager?.setUsePlayPauseActions(true)
        playerNotificationManager?.setUseNextAction(true);
        playerNotificationManager?.setUsePreviousAction(true);
        playerNotificationManager?.setUseNextActionInCompactView(true);
        playerNotificationManager?.setUsePreviousActionInCompactView(true);
        playerNotificationManager?.setUseFastForwardAction(false)
        playerNotificationManager?.setUseFastForwardActionInCompactView(false)
        playerNotificationManager?.setUseRewindAction(false)
        playerNotificationManager?.setUseRewindActionInCompactView(false)
        playerNotificationManager?.setPlayer(player)

    }


    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
        // Notify the client if the playback state was changed
        val stateIntent = Intent(ACTION_STATE_CHANGED)

        when(playbackState){
            Player.STATE_BUFFERING -> {
                Log.d(KdPlayer, "PlayerState-STATE_BUFFERING: $playWhenReady")
            }
            Player.STATE_ENDED -> {
                Log.d(KdPlayer, "PlayerState-STATE_ENDED: $playWhenReady")
                stateIntent.putExtra(ACTION_STATE_CHANGED_EXTRA, false)
                player.seekTo(0);
                player.playWhenReady = true;
                player.pause()
                handler.removeCallbacks(updateProgressAction)
            }
            Player.STATE_IDLE -> {
                Log.d(KdPlayer, "PlayerState-STATE_IDLE: $playWhenReady")
                player.prepare()
                durationSet = false;
                Log.d(KdPlayer, "${player.duration}")
            }
            Player.STATE_READY -> {
                Log.d(KdPlayer, "PlayerState-STATE_READY: $playWhenReady")
                Log.d(KdPlayer, "PlayerState-STATE_READY: ${player.duration}")
                if(!durationSet) {
                    durationSet = true
                    val realDurationMillis = player.duration
                    sendDuration(realDurationMillis)
                }
                stateIntent.putExtra(ACTION_STATE_CHANGED_EXTRA, playWhenReady)
            }
        }
        localBroadcastManager.sendBroadcast(stateIntent)
    }

    private  fun sendDuration(durationMillis: Long) {
        val durationIntent = Intent(ACTION_DURATION_CHANGED)
        durationIntent.putExtra(ACTION_DURATION_CHANGED_EXTRA, durationMillis)
        localBroadcastManager.sendBroadcast(durationIntent)
    }

    private fun updateProgress() {
        sentCurrentPosition(player.currentPosition)
        handler.postDelayed(updateProgressAction, 10)
    }

    private val updateProgressAction = Runnable { updateProgress() }

    private fun sentCurrentPosition(currentPosition : Long) {
        val currentIntent = Intent(ACTION_CURRENT_CHANGED)
        currentIntent.putExtra(ACTION_CURRENT_CHANGED_EXTRA, currentPosition)
        localBroadcastManager.sendBroadcast(currentIntent)
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        super.onMediaMetadataChanged(mediaMetadata)
        val title: String = mediaMetadata.title?.toString() ?: ""
        val cover: String = mediaMetadata.artworkUri.toString()
        Log.d(KdPlayer, title);
        Log.d(KdPlayer, cover);
        val image = mediaMetadata.artworkData?.let { BitmapFactory.decodeByteArray(mediaMetadata.artworkData, 0, it.size) };
        defaultArtwork = image
        metadataList = title.split(" - ").toMutableList()
        if (metadataList!!.lastIndex == 0) metadataList!!.add("")
        metadataList!!.add(cover)
        playerNotificationManager?.invalidate()

        val metadataIntent = Intent(ACTION_NEW_METADATA)
        metadataIntent.putStringArrayListExtra(ACTION_NEW_METADATA_EXTRA, metadataList!! as ArrayList<String>)
        localBroadcastManager.sendBroadcast(metadataIntent)
    }

    override fun onMetadata(metadata: Metadata) {
        val icyInfo: IcyInfo = metadata[0] as IcyInfo
        val title: String = icyInfo.title ?: return
        val cover: String = icyInfo.url ?: ""

        metadataList = title.split(" - ").toMutableList()
        if (metadataList!!.lastIndex == 0) metadataList!!.add("")
        metadataList!!.add(cover)
        playerNotificationManager?.invalidate()

        val metadataIntent = Intent(ACTION_NEW_METADATA)
        metadataIntent.putStringArrayListExtra(ACTION_NEW_METADATA_EXTRA, metadataList!! as ArrayList<String>)
        localBroadcastManager.sendBroadcast(metadataIntent)
    }

    fun downloadImage(value: String?): Bitmap? {
        if (value == null) return null
        var bitmap: Bitmap? = null

        try {
            val url = URL(value)
            bitmap = runBlocking {
                withContext(Dispatchers.Default) {
                    BitmapFactory.decodeStream(url.openStream())
                }
            }
        } catch (e: Throwable) {
            println(e)
        }

        return bitmap
    }
}

