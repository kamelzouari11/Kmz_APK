package com.kmz.shazamplayer.network

import android.net.Uri
import android.util.Log
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Gestionnaire de métadonnées musicales multi-sources.
 *
 * Stratégie de validation PRO:
 * 1. Deezer API (prioritaire) - matching très fiable avec durée exacte
 * 2. iTunes API (fallback) - sans clé API, cover HD disponible
 *
 * Permet de:
 * - Valider que c'est le BON titre (pas remix, pas live)
 * - Récupérer la durée réelle pour filtrer les versions incorrectes
 * - Obtenir une cover propre en haute définition
 */
data class OfficialTrackMetadata(
        val title: String,
        val artist: String,
        val album: String?,
        val durationMs: Long,
        val coverUrl: String?,
        val coverUrlHD: String?,
        val source: String // "deezer" ou "itunes"
)

class MusicMetadataManager {
    private val client =
            OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()

    companion object {
        private const val TAG = "MusicMetadata"
        // Tolérance de durée: ±20% ou ±30 secondes max
        private const val DURATION_TOLERANCE_PERCENT = 0.20
        private const val DURATION_TOLERANCE_MS = 30000L
    }

    /**
     * Recherche les métadonnées officielles d'un titre. Essaie Deezer d'abord, puis iTunes en
     * fallback.
     */
    suspend fun getOfficialMetadata(artist: String, title: String): OfficialTrackMetadata? =
            withContext(Dispatchers.IO) {
                // 1. Essayer Deezer d'abord
                val deezerResult = searchDeezer(artist, title)
                if (deezerResult != null) {
                    Log.d(
                            TAG,
                            "✅ Deezer match: ${deezerResult.title} - ${deezerResult.durationMs}ms"
                    )
                    return@withContext deezerResult
                }

                // 2. Fallback sur iTunes
                val itunesResult = searchiTunes(artist, title)
                if (itunesResult != null) {
                    Log.d(
                            TAG,
                            "✅ iTunes match: ${itunesResult.title} - ${itunesResult.durationMs}ms"
                    )
                    return@withContext itunesResult
                }

                Log.w(TAG, "⚠️ Aucune métadonnée trouvée pour: $artist - $title")
                null
            }

    /**
     * Recherche sur Deezer API. URL: https://api.deezer.com/search?q=artist:"Daft Punk" track:"One
     * More Time"
     */
    private fun searchDeezer(artist: String, title: String): OfficialTrackMetadata? {
        return try {
            // Nettoyer et encoder les paramètres
            val cleanArtist = cleanSearchTerm(artist)
            val cleanTitle = cleanSearchTerm(title)
            val query = Uri.encode("artist:\"$cleanArtist\" track:\"$cleanTitle\"")
            val url = "https://api.deezer.com/search?q=$query&limit=5"

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null

            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: return null

            if (data.length() == 0) {
                // Essayer une recherche plus simple si la recherche précise échoue
                return searchDeezerSimple(artist, title)
            }

            // Prendre le premier résultat
            val track = data.getJSONObject(0)
            val albumObj = track.optJSONObject("album")
            val artistObj = track.optJSONObject("artist")

            OfficialTrackMetadata(
                    title = track.getString("title"),
                    artist = artistObj?.getString("name") ?: artist,
                    album = albumObj?.optString("title"),
                    durationMs = track.getLong("duration") * 1000, // Deezer retourne en secondes
                    coverUrl = albumObj?.optString("cover_medium"),
                    coverUrlHD = albumObj?.optString("cover_big")?.replace("500x500", "600x600")
                                    ?: albumObj?.optString("cover_medium")
                                            ?.replace("250x250", "600x600"),
                    source = "deezer"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Deezer search error: ${e.message}")
            null
        }
    }

    /** Recherche Deezer simplifiée (sans qualificateurs) */
    private fun searchDeezerSimple(artist: String, title: String): OfficialTrackMetadata? {
        return try {
            val query = Uri.encode("$artist $title")
            val url = "https://api.deezer.com/search?q=$query&limit=5"

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null

            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: return null

            if (data.length() == 0) return null

            val track = data.getJSONObject(0)
            val albumObj = track.optJSONObject("album")
            val artistObj = track.optJSONObject("artist")

            OfficialTrackMetadata(
                    title = track.getString("title"),
                    artist = artistObj?.getString("name") ?: artist,
                    album = albumObj?.optString("title"),
                    durationMs = track.getLong("duration") * 1000,
                    coverUrl = albumObj?.optString("cover_medium"),
                    coverUrlHD = albumObj?.optString("cover_big")?.replace("500x500", "600x600")
                                    ?: albumObj?.optString("cover_medium")
                                            ?.replace("250x250", "600x600"),
                    source = "deezer"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Deezer simple search error: ${e.message}")
            null
        }
    }

    /**
     * Recherche sur iTunes API (sans clé API requise). URL:
     * https://itunes.apple.com/search?term=daft+punk+one+more+time&entity=song&limit=5
     */
    private fun searchiTunes(artist: String, title: String): OfficialTrackMetadata? {
        return try {
            val query = Uri.encode("$artist $title")
            val url = "https://itunes.apple.com/search?term=$query&entity=song&limit=5"

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null

            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return null

            if (results.length() == 0) return null

            val track = results.getJSONObject(0)
            val artworkUrl = track.optString("artworkUrl100", "")

            OfficialTrackMetadata(
                    title = track.getString("trackName"),
                    artist = track.getString("artistName"),
                    album = track.optString("collectionName"),
                    durationMs = track.getLong("trackTimeMillis"),
                    coverUrl = artworkUrl.replace("100x100bb", "300x300bb"),
                    coverUrlHD = artworkUrl.replace("100x100bb", "600x600bb"),
                    source = "itunes"
            )
        } catch (e: Exception) {
            Log.e(TAG, "iTunes search error: ${e.message}")
            null
        }
    }

    /**
     * Vérifie si la durée d'un flux SoundCloud correspond à la durée officielle. Utilise une
     * tolérance de ±20% ou ±30 secondes (le plus permissif).
     *
     * @param soundcloudDurationMs Durée du flux SoundCloud en millisecondes
     * @param officialDurationMs Durée officielle en millisecondes
     * @return true si les durées correspondent, false sinon
     */
    fun isDurationMatch(soundcloudDurationMs: Long, officialDurationMs: Long): Boolean {
        if (officialDurationMs <= 0 || soundcloudDurationMs <= 0)
                return true // Pas de validation possible

        val diff = kotlin.math.abs(soundcloudDurationMs - officialDurationMs)
        val percentDiff = diff.toDouble() / officialDurationMs

        // Match si diff < 20% OU diff < 30 secondes
        val isMatch = percentDiff <= DURATION_TOLERANCE_PERCENT || diff <= DURATION_TOLERANCE_MS

        if (!isMatch) {
            Log.d(
                    TAG,
                    "❌ Duration mismatch: SC=${soundcloudDurationMs}ms vs Official=${officialDurationMs}ms (diff=${diff}ms, ${(percentDiff*100).toInt()}%)"
            )
        }

        return isMatch
    }

    /** Nettoie les termes de recherche (supprime les caractères spéciaux problématiques) */
    private fun cleanSearchTerm(term: String): String {
        return term.replace("\"", "")
                .replace("'", "'")
                .replace(":", " ")
                .replace("/", " ")
                .replace("*", " ")
                .replace("  ", " ")
                .trim()
    }

    /** Obtient la meilleure cover disponible (priorité: Deezer HD > iTunes HD > SoundCloud) */
    fun getBestCoverUrl(
            officialMetadata: OfficialTrackMetadata?,
            soundcloudArtwork: String?
    ): String? {
        return officialMetadata?.coverUrlHD ?: officialMetadata?.coverUrl ?: soundcloudArtwork
    }
}
