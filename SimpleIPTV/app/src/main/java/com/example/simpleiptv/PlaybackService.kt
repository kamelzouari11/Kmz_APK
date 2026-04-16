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
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
        private var mediaSession: MediaSession? = null
        private var wifiLock: WifiManager.WifiLock? = null
        private var wakeLock: PowerManager.WakeLock? = null

        @OptIn(UnstableApi::class)
        override fun onCreate() {
                super.onCreate()
                val httpDataSourceFactory =
                        androidx.media3.datasource.DefaultHttpDataSource.Factory()
                                .setUserAgent(
                                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                                )
                                .setAllowCrossProtocolRedirects(true)

                // Optimize buffering for IPTV (high bitrate/unstable network)
                // Optimized for Fast Zapping
                val loadControl =
                        androidx.media3.exoplayer.DefaultLoadControl.Builder()
                                .setBufferDurationsMs(
                                        10_000, // minBufferMs: Reduced for faster start
                                        30_000, // maxBufferMs: Allow up to 30s of cache
                                        1_000, // bufferForPlaybackMs: Wait for 1s before start
                                        2_000 // bufferForPlaybackAfterRebufferMs: Less cautious
                                        // when resuming
                                        )
                                .setPrioritizeTimeOverSizeThresholds(true)
                                .build()

                // Configure TrackSelection to be aggressive for IPTV
                // Higher duration for increase (don't rush to 4K/HD)
                // Lower duration for decrease (Drop to SD fast if buffer is low)
                val trackSelectionFactory =
                        AdaptiveTrackSelection.Factory(
                                20_000, // minDurationForQualityIncreaseMs: Wait 20s of stability
                                // before going HD
                                3_000, // maxDurationForQualityDecreaseMs: Drop to SD in 3s if it
                                // lags
                                10_000, // minDurationToRetainAfterDiscardMs
                                AdaptiveTrackSelection.DEFAULT_BANDWIDTH_FRACTION
                        )
                val trackSelector = DefaultTrackSelector(this, trackSelectionFactory)

                val player =
                        ExoPlayer.Builder(this)
                                .setMediaSourceFactory(
                                        androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                                                        this
                                                )
                                                .setDataSourceFactory(httpDataSourceFactory)
                                )
                                .setTrackSelector(trackSelector)
                                .setLoadControl(loadControl)
                                .setAudioAttributes(
                                        androidx.media3.common.AudioAttributes.DEFAULT,
                                        true
                                )
                                .setHandleAudioBecomingNoisy(true)
                                .setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)
                                .build()

                player.repeatMode = Player.REPEAT_MODE_ONE

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
                                                if (wifiLock?.isHeld == false) wifiLock?.acquire()
                                                if (wakeLock?.isHeld == false) wakeLock?.acquire()
                                        }
                                }

                                override fun onMediaItemTransition(
                                        mediaItem: MediaItem?,
                                        reason: Int
                                ) {
                                        if (wifiLock?.isHeld == false) wifiLock?.acquire()
                                        if (wakeLock?.isHeld == false) wakeLock?.acquire()
                                }

                                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                                try {
                                                        if (player.playbackState != Player.STATE_READY) {
                                                                player.seekToDefaultPosition()
                                                                player.prepare()
                                                                player.play()
                                                        }
                                                } catch(e: Exception) {}
                                        }, 4000)
                                }
                        }
                )
        }

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
                super.onStartCommand(intent, flags, startId)
                // START_NOT_STICKY : le service NE redémarre PAS tout seul après être tué.
                // Avant c'était START_STICKY ce qui causait la lecture continue même app fermée.
                return START_NOT_STICKY
        }

        /** Stoppe la lecture et ferme le service proprement. Appelé depuis MainActivity. */
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
