package com.example.simpleradio.cast

import android.content.Context
import androidx.core.net.toUri
import com.example.simpleradio.data.local.entities.RadioStationEntity
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata as CastMediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.images.WebImage

class CastHelper(
        private val context: Context,
        private val onSessionStatusChanged: (CastSession?) -> Unit,
        private val onSessionStarted: (CastSession) -> Unit
) {
    private var castSession: CastSession? = null

    val sessionManagerListener =
            object : SessionManagerListener<CastSession> {
                override fun onSessionStarted(session: CastSession, sessionId: String) {
                    castSession = session
                    onSessionStatusChanged(session)
                    onSessionStarted(session)
                }

                override fun onSessionEnded(session: CastSession, error: Int) {
                    castSession = null
                    onSessionStatusChanged(null)
                }

                override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                    castSession = session
                    onSessionStatusChanged(session)
                }

                override fun onSessionStarting(session: CastSession) {}
                override fun onSessionStartFailed(session: CastSession, error: Int) {}
                override fun onSessionEnding(session: CastSession) {}
                override fun onSessionResumeFailed(session: CastSession, error: Int) {}
                override fun onSessionResuming(session: CastSession, sessionId: String) {}
                override fun onSessionSuspended(session: CastSession, reason: Int) {}
            }

    fun initCast(): Boolean {
        return try {
            if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) ==
                            ConnectionResult.SUCCESS
            ) {
                CastContext.getSharedInstance(context) != null
            } else false
        } catch (_: Exception) {
            false
        }
    }

    fun loadMedia(
            session: CastSession,
            radio: RadioStationEntity,
            currentArtist: String?,
            currentTitle: String?,
            currentArtworkUrl: String?
    ) {
        val songTitle = if (currentTitle.isNullOrBlank()) radio.name else currentTitle
        val songArtist = if (currentArtist.isNullOrBlank()) (radio.country ?: "") else currentArtist
        val stationInfo =
                "${radio.name} | ${radio.country ?: "Monde"} | ${radio.bitrate ?: "?"} kbps"

        val metadata =
                CastMediaMetadata(CastMediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
                    putString(CastMediaMetadata.KEY_TITLE, songTitle)
                    putString(CastMediaMetadata.KEY_ARTIST, songArtist)
                    putString(CastMediaMetadata.KEY_ALBUM_TITLE, stationInfo)
                    (currentArtworkUrl ?: radio.favicon)?.let { addImage(WebImage(it.toUri())) }
                }

        val mediaInfo =
                MediaInfo.Builder(radio.url)
                        .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
                        .setContentType("audio/mpeg")
                        .setMetadata(metadata)
                        .build()

        session.remoteMediaClient?.load(
                MediaLoadRequestData.Builder().setMediaInfo(mediaInfo).build()
        )
    }
}
