package com.kmz.shazamplayer.network

import android.content.Context
import android.util.Log

/**
 * SpotifyManager - Version Allégée (Shell) Le SDK Spotify n'a pas été trouvé dans les dépôts Maven
 * publics. Cette classe permet à l'application de compiler et de fonctionner avec SoundCloud
 * uniquement.
 */
data class SpotifySearchResult(val uri: String, val artworkUrl: String?)

class SpotifyManager(
        private val context: Context,
        private val clientId: String,
        private val redirectUri: String
) {
    companion object {
        private const val TAG = "SpotifyManager"
    }

    // Le SDK Spotify est actuellement désactivé pour permettre la compilation
    fun connect(onConnected: () -> Unit, onFailure: (Throwable) -> Unit) {
        Log.w(TAG, "Spotify SDK non disponible. Connexion impossible.")
    }

    fun disconnect() {}

    fun isConnected(): Boolean = false

    fun getAuthenticationRequest(): Any? =
            null // Retourne null car AuthorizationRequest n'existe pas

    fun onAuthenticationResponse(response: Any?) {}

    suspend fun searchTrack(artist: String, title: String): SpotifySearchResult? = null

    fun play(spotifyUri: String) {}

    fun pause() {}

    fun resume() {}

    fun skipNext() {}

    fun skipPrevious() {}

    fun seekTo(posMs: Long) {}

    fun subscribeToPlayerState(callback: (String, Long, Long, Boolean) -> Unit) {}
}
