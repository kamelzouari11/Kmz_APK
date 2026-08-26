package com.example.simpleiptv

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.simpleiptv.data.IptvRepository
import com.example.simpleiptv.data.local.AppDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {
        private var mediaSession: MediaSession? = null
        private var wifiLock: WifiManager.WifiLock? = null
        private var wakeLock: PowerManager.WakeLock? = null
        private lateinit var repository: IptvRepository
        private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        // Reconnexion automatique : continue pour le Live, limitée pour la VOD.
        private var retryCount = 0
        private val maxVodRetries = 3
        private val retryHandler = android.os.Handler(android.os.Looper.getMainLooper())
        private var retryRunnable: Runnable? = null
        private var reconnectJob: Job? = null
        private var currentMediaId: String? = null

        private fun isLiveItem(item: MediaItem?): Boolean =
                item?.mediaId?.startsWith("LIVE:") == true

        private fun cancelPendingRetry() {
                retryRunnable?.let { retryHandler.removeCallbacks(it) }
                retryRunnable = null
                reconnectJob?.cancel()
                reconnectJob = null
        }

        private suspend fun rebuildLiveMediaItem(mediaItem: MediaItem): MediaItem {
                val parts = mediaItem.mediaId.split(":", limit = 3)
                if (parts.size != 3 || parts[0] != "LIVE") return mediaItem
                val profileId = parts[1].toIntOrNull() ?: return mediaItem
                val streamId = parts[2]
                val profile = repository.getProfileById(profileId) ?: return mediaItem
                val channel = repository.getChannelById(streamId, profileId, "LIVE") ?: return mediaItem
                val refreshedUrl = repository.getStreamUrl(profile, channel)
                if (refreshedUrl.isBlank()) throw IllegalStateException("Empty refreshed stream URL")
                return mediaItem.buildUpon().setUri(refreshedUrl).build()
        }

        private fun scheduleReconnect(player: Player) {
                if (retryRunnable != null || reconnectJob?.isActive == true) return
                val mediaItem = player.currentMediaItem ?: return
                val isLive = isLiveItem(mediaItem)
                if (!isLive && retryCount >= maxVodRetries) {
                        retryCount = 0
                        return
                }

                if (isLive) {
                        if (wifiLock?.isHeld == false) wifiLock?.acquire()
                        if (wakeLock?.isHeld == false) wakeLock?.acquire()
                }

                val expectedMediaId = mediaItem.mediaId
                val delayMs = if (isLive) {
                        minOf(2_000L * (1L shl retryCount.coerceAtMost(3)), 15_000L)
                } else {
                        2_000L
                }

                retryRunnable = Runnable {
                        retryRunnable = null
                        val currentItem = player.currentMediaItem
                        if (currentItem?.mediaId != expectedMediaId) return@Runnable

                        reconnectJob = serviceScope.launch {
                                var retryAfterFailure = false
                                try {
                                        retryCount++
                                        if (isLive) {
                                                // Régénère notamment les liens Stalker temporaires.
                                                player.setMediaItem(rebuildLiveMediaItem(currentItem))
                                        }
                                        player.prepare()
                                        player.play()
                                } catch (e: CancellationException) {
                                        throw e
                                } catch (e: Exception) {
                                        retryAfterFailure = true
                                } finally {
                                        reconnectJob = null
                                }
                                if (retryAfterFailure && player.currentMediaItem?.mediaId == expectedMediaId) {
                                        scheduleReconnect(player)
                                }
                        }
                }
                retryHandler.postDelayed(retryRunnable!!, delayMs)
        }

        @OptIn(UnstableApi::class)
        override fun onCreate() {
                super.onCreate()
                repository = IptvRepository(AppDatabase.getDatabase(applicationContext).iptvDao())
                val httpDataSourceFactory =
                        androidx.media3.datasource.DefaultHttpDataSource.Factory()
                                .setUserAgent(
                                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                                )
                                .setAllowCrossProtocolRedirects(true)

                val player =
                        ExoPlayer.Builder(this)
                                .setMediaSourceFactory(
                                        androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                                                        this
                                                )
                                                .setDataSourceFactory(httpDataSourceFactory)
                                )
                                .setAudioAttributes(
                                        androidx.media3.common.AudioAttributes.DEFAULT,
                                        true
                                )
                                .setHandleAudioBecomingNoisy(true)
                                .setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)
                                .build()

                val wifiManager =
                        applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val lockType =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                        } else {
                                @Suppress("DEPRECATION") WifiManager.WIFI_MODE_FULL_HIGH_PERF
                        }

                wifiLock = wifiManager.createWifiLock(lockType, "SimpleIPTV:WifiLock")

                wakeLock =
                        (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(
                                PowerManager.PARTIAL_WAKE_LOCK,
                                "SimpleIPTV:WakeLock"
                        )

                mediaSession =
                        MediaSession.Builder(this, player)
                                .setCallback(
                                        object : MediaSession.Callback {
                                                override fun onConnect(
                                                        session: MediaSession,
                                                        controller: MediaSession.ControllerInfo
                                                ): MediaSession.ConnectionResult {
                                                        val connectionResult =
                                                                super.onConnect(session, controller)
                                                        val availablePlayerCommands =
                                                                connectionResult
                                                                        .availablePlayerCommands
                                                                        .buildUpon()
                                                                        .add(Player.COMMAND_SEEK_TO_NEXT)
                                                                        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                                                                        .build()
                                                        return MediaSession.ConnectionResult
                                                                .AcceptedResultBuilder(session)
                                                                .setAvailablePlayerCommands(
                                                                        availablePlayerCommands
                                                                )
                                                                .build()
                                                }
                                        }
                                )
                                .build()

                player.addListener(
                        object : Player.Listener {
                                override fun onIsPlayingChanged(isPlaying: Boolean) {
                                        if (isPlaying) {
                                                retryCount = 0
                                                cancelPendingRetry()
                                                if (wifiLock?.isHeld == false) wifiLock?.acquire()
                                                if (wakeLock?.isHeld == false) wakeLock?.acquire()
                                        } else if (!player.playWhenReady || player.currentMediaItem == null) {
                                                if (wifiLock?.isHeld == true) wifiLock?.release()
                                                if (wakeLock?.isHeld == true) wakeLock?.release()
                                        }
                                }

                                override fun onMediaItemTransition(
                                        mediaItem: MediaItem?,
                                        reason: Int
                                ) {
                                        if (mediaItem?.mediaId != currentMediaId) {
                                                currentMediaId = mediaItem?.mediaId
                                                retryCount = 0
                                                cancelPendingRetry()
                                        }
                                        if (mediaItem == null) {
                                                if (wifiLock?.isHeld == true) wifiLock?.release()
                                                if (wakeLock?.isHeld == true) wakeLock?.release()
                                        }
                                }

                                override fun onPlaybackStateChanged(playbackState: Int) {
                                        if (playbackState == Player.STATE_ENDED &&
                                                isLiveItem(player.currentMediaItem)) {
                                                scheduleReconnect(player)
                                        }
                                }

                                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                        scheduleReconnect(player)
                                }
                        }
                )
        }

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
                super.onStartCommand(intent, flags, startId)
                return START_NOT_STICKY
        }

        fun stopPlayback() {
                mediaSession?.player?.let { player ->
                        player.stop()
                        player.clearMediaItems()
                }
                stopSelf()
        }

        override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
                return mediaSession
        }

        override fun onDestroy() {
                cancelPendingRetry()
                serviceScope.cancel()
                mediaSession?.run {
                        if (player.isPlaying) player.pause()
                        player.release()
                        release()
                        mediaSession = null
                }
                if (wifiLock?.isHeld == true) wifiLock?.release()
                if (wakeLock?.isHeld == true) wakeLock?.release()
                super.onDestroy()
        }
}
