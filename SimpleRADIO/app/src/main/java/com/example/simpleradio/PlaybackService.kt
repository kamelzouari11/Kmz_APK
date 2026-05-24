package com.example.simpleradio

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaybackService : MediaSessionService() {
        private var mediaSession: MediaSession? = null
        private var wifiLock: WifiManager.WifiLock? = null
        private var wakeLock: PowerManager.WakeLock? = null
        private val serviceScope =
                kotlinx.coroutines.CoroutineScope(
                        kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()
                )

        // Artwork injecté par l'UI (Bluetooth), séparé du flux audio
        private var overrideArtworkData: ByteArray? = null
        private var overrideArtworkUri: String? = null
        private var overrideTitle: String? = null
        private var overrideArtist: String? = null
        private var overrideAlbum: String? = null

        // Player custom avec gestion manuelle des événements
        private var customPlayer: CustomForwardingPlayer? = null

        @OptIn(UnstableApi::class)
        override fun onCreate() {
                super.onCreate()
                // Configure ExoPlayer avec support HLS et HTTP pour les radios
                val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this)
                val mediaSourceFactory =
                        androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this)
                                .setDataSourceFactory(dataSourceFactory)

                val realPlayer =
                        ExoPlayer.Builder(this)
                                .setMediaSourceFactory(mediaSourceFactory)
                                .setAudioAttributes(
                                        androidx.media3.common.AudioAttributes.DEFAULT,
                                        true
                                )
                                .setHandleAudioBecomingNoisy(true)
                                .setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)
                                .build()

                customPlayer = CustomForwardingPlayer(realPlayer)

                val wifiManager =
                        applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                if (wifiManager != null) {
                        val lockType =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                                } else {
                                        @Suppress("DEPRECATION")
                                        WifiManager.WIFI_MODE_FULL_HIGH_PERF
                                }
                        wifiLock = wifiManager.createWifiLock(lockType, "SimpleRADIO:WifiLock")
                }

                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (powerManager != null) {
                        wakeLock =
                                powerManager.newWakeLock(
                                        PowerManager.PARTIAL_WAKE_LOCK,
                                        "SimpleRADIO:WakeLock"
                                )
                }

                mediaSession =
                        MediaSession.Builder(this, customPlayer!!)
                                .setCallback(
                                        object : MediaSession.Callback {
                                                override fun onConnect(
                                                        session: MediaSession,
                                                        controller: MediaSession.ControllerInfo
                                                ): MediaSession.ConnectionResult {
                                                        val connectionResult =
                                                                super.onConnect(session, controller)
                                                        val availableSessionCommands =
                                                                connectionResult
                                                                        .availableSessionCommands
                                                                        .buildUpon()
                                                                        .add(
                                                                                androidx.media3
                                                                                        .session
                                                                                        .SessionCommand(
                                                                                                "UPDATE_METADATA",
                                                                                                Bundle.EMPTY
                                                                                        )
                                                                        )
                                                                        .build()

                                                        return MediaSession.ConnectionResult
                                                                .AcceptedResultBuilder(session)
                                                                .setAvailableSessionCommands(
                                                                        availableSessionCommands
                                                                )
                                                                .build()
                                                }

                                                override fun onCustomCommand(
                                                        session: MediaSession,
                                                        controller: MediaSession.ControllerInfo,
                                                        customCommand:
                                                                androidx.media3.session.SessionCommand,
                                                        args: Bundle
                                                ): com.google.common.util.concurrent.ListenableFuture<
                                                        androidx.media3.session.SessionResult> {
                                                        if (customCommand.customAction ==
                                                                        "UPDATE_METADATA"
                                                        ) {
                                                                val title = args.getString("TITLE")
                                                                val artist =
                                                                        args.getString("ARTIST")
                                                                val album = args.getString("ALBUM")
                                                                val artworkUrl =
                                                                        args.getString(
                                                                                "ARTWORK_URL"
                                                                        )

                                                                overrideTitle = title
                                                                overrideArtist = artist
                                                                overrideAlbum = album

                                                                serviceScope.launch {
                                                                        overrideArtworkUri =
                                                                                artworkUrl
                                                                        if (artworkUrl != null) {
                                                                                try {
                                                                                        val url =
                                                                                                java.net
                                                                                                        .URL(
                                                                                                                artworkUrl
                                                                                                        )
                                                                                        overrideArtworkData =
                                                                                                url.readBytes()
                                                                                } catch (
                                                                                        e:
                                                                                                Exception) {
                                                                                        overrideArtworkData =
                                                                                                null
                                                                                }
                                                                        } else {
                                                                                overrideArtworkData =
                                                                                        null
                                                                        }

                                                                        withContext(
                                                                                Dispatchers.Main
                                                                        ) {
                                                                                customPlayer
                                                                                        ?.notifyMetadataChanged()
                                                                        }
                                                                }
                                                                return com.google.common.util
                                                                        .concurrent.Futures
                                                                        .immediateFuture(
                                                                                androidx.media3
                                                                                        .session
                                                                                        .SessionResult(
                                                                                                androidx.media3
                                                                                                        .session
                                                                                                        .SessionResult
                                                                                                        .RESULT_SUCCESS
                                                                                        )
                                                                        )
                                                        }
                                                        return super.onCustomCommand(
                                                                session,
                                                                controller,
                                                                customCommand,
                                                                args
                                                        )
                                                }
                                        }
                                )
                                .build()

                // Force l'affichage des boutons dans la notification système
                setMediaNotificationProvider(
                        object : androidx.media3.session.DefaultMediaNotificationProvider(this) {
                                override fun getMediaButtons(
                                        session: MediaSession,
                                        playerCommands: androidx.media3.common.Player.Commands,
                                        customLayout:
                                                com.google.common.collect.ImmutableList<
                                                        androidx.media3.session.CommandButton>,
                                        showPlayingWhenPaused: Boolean
                                ): com.google.common.collect.ImmutableList<
                                        androidx.media3.session.CommandButton> {
                                        val playPauseButton =
                                                if (session.player.isPlaying) {
                                                        androidx.media3.session.CommandButton
                                                                .Builder()
                                                                .setPlayerCommand(
                                                                        androidx.media3.common
                                                                                .Player
                                                                                .COMMAND_PLAY_PAUSE
                                                                )
                                                                .setIconResId(
                                                                        android.R
                                                                                .drawable
                                                                                .ic_media_pause
                                                                )
                                                                .setDisplayName("Pause")
                                                                .build()
                                                } else {
                                                        androidx.media3.session.CommandButton
                                                                .Builder()
                                                                .setPlayerCommand(
                                                                        androidx.media3.common
                                                                                .Player
                                                                                .COMMAND_PLAY_PAUSE
                                                                )
                                                                .setIconResId(
                                                                        android.R
                                                                                .drawable
                                                                                .ic_media_play
                                                                )
                                                                .setDisplayName("Play")
                                                                .build()
                                                }
                                        val prevButton =
                                                androidx.media3.session.CommandButton.Builder()
                                                        .setPlayerCommand(8)
                                                        .setIconResId(
                                                                android.R.drawable.ic_media_previous
                                                        )
                                                        .setDisplayName("Previous")
                                                        .setEnabled(true)
                                                        .build()
                                        val nextButton =
                                                androidx.media3.session.CommandButton.Builder()
                                                        .setPlayerCommand(9)
                                                        .setIconResId(
                                                                android.R.drawable.ic_media_next
                                                        )
                                                        .setDisplayName("Next")
                                                        .setEnabled(true)
                                                        .build()
                                        return com.google.common.collect.ImmutableList.of(
                                                prevButton,
                                                playPauseButton,
                                                nextButton
                                        )
                                }
                        }
                )

                realPlayer.addListener(
                        object : Player.Listener {
                                override fun onIsPlayingChanged(isPlaying: Boolean) {
                                        if (isPlaying) {
                                                if (wifiLock?.isHeld == false) wifiLock?.acquire()
                                                if (wakeLock?.isHeld == false) wakeLock?.acquire()
                                        }
                                }
                        }
                )
        }

        // Classe interne pour gérer l'injection de métadonnées et la notification
        @OptIn(UnstableApi::class)
        inner class CustomForwardingPlayer(player: androidx.media3.common.Player) :
                androidx.media3.common.ForwardingPlayer(player) {
                private val listeners =
                        java.util.concurrent.CopyOnWriteArraySet<
                                androidx.media3.common.Player.Listener>()

                override fun addListener(listener: androidx.media3.common.Player.Listener) {
                        listeners.add(listener)
                        super.addListener(listener)
                }

                override fun removeListener(listener: androidx.media3.common.Player.Listener) {
                        listeners.remove(listener)
                        super.removeListener(listener)
                }

                fun notifyMetadataChanged() {
                        val meta = mediaMetadata
                        listeners.forEach { it.onMediaMetadataChanged(meta) }
                }

                override fun getMediaMetadata(): androidx.media3.common.MediaMetadata {
                        val realMeta = super.getMediaMetadata()
                        val builder = realMeta.buildUpon()

                        if (overrideTitle != null) builder.setTitle(overrideTitle)
                        if (overrideArtist != null) builder.setArtist(overrideArtist)
                        if (overrideAlbum != null) {
                                builder.setAlbumTitle(overrideAlbum)
                                builder.setStation(overrideAlbum)
                        }
                        if (overrideArtworkUri != null)
                                try {
                                        builder.setArtworkUri(
                                                android.net.Uri.parse(overrideArtworkUri)
                                        )
                                } catch (e: Exception) {}
                        if (overrideArtworkData != null)
                                builder.setArtworkData(
                                        overrideArtworkData,
                                        androidx.media3.common.MediaMetadata
                                                .PICTURE_TYPE_FRONT_COVER
                                )

                        return builder.build()
                }

                override fun getAvailableCommands(): androidx.media3.common.Player.Commands {
                        return super.getAvailableCommands()
                                .buildUpon()
                                .add(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT)
                                .add(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS)
                                .add(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                                .add(
                                        androidx.media3.common.Player
                                                .COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
                                )
                                .build()
                }

                override fun isCommandAvailable(command: Int): Boolean {
                        return when (command) {
                                9, 10, 11, 12, 8 -> true // Next/Prev commands
                                else -> super.isCommandAvailable(command)
                        }
                }
        }

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
                super.onStartCommand(intent, flags, startId)
                return START_STICKY
        }

        override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
                return mediaSession
        }

        override fun onDestroy() {
                serviceScope.cancel() // Nettoyage
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
