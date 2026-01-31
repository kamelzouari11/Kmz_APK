package com.kmz.shazamplayer.network

import android.net.Uri
import android.util.Log
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class SoundCloudResult(
        val streamUrl: String,
        val artworkUrl: String?,
        val durationMs: Long = 0L // Durée du flux SoundCloud
)

/**
 * Gestionnaire SoundCloud avec validation multi-sources.
 *
 * Stratégie PRO de sélection des flux:
 * 1. Recherche Deezer/iTunes pour obtenir métadonnées officielles (durée, cover HD)
 * 2. Recherche SoundCloud avec filtrage:
 * ```
 *    - Durée > 2 minutes (évite pubs/teasers)
 *    - Durée proche de la durée officielle (±20% ou ±30s)
 *    - Flux 'progressive' privilégiés (MP3 stables)
 * ```
 * 3. Hiérarchie des covers:
 * ```
 *    a. Cover HD officielle (Deezer/iTunes)
 *    b. Pochette SoundCloud en HD (t500x500)
 *    c. Avatar du créateur en HD
 * ```
 * 4. Retourne les 5 meilleurs résultats validés
 */
class SoundCloudManager(private val clientId: String) {
    private val client =
            OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()

    private val metadataManager = MusicMetadataManager()

    companion object {
        private const val TAG = "SoundCloud"
    }

    /**
     * Recherche les flux SoundCloud avec validation des métadonnées officielles.
     *
     * @return Liste de SoundCloudResult avec:
     * ```
     *         - streamUrl: URL du flux validé
     *         - artworkUrl: Meilleure cover disponible (officielle prioritaire)
     *         - durationMs: Durée du flux
     * ```
     */
    suspend fun searchTracks(artist: String, title: String): List<SoundCloudResult> =
            withContext(Dispatchers.IO) {
                val results = mutableListOf<SoundCloudResult>()

                try {
                    // 1. Obtenir les métadonnées officielles (Deezer/iTunes)
                    val officialMetadata = metadataManager.getOfficialMetadata(artist, title)
                    val officialDuration = officialMetadata?.durationMs ?: 0L
                    val officialCoverHD = officialMetadata?.coverUrlHD

                    Log.d(TAG, "🔍 Searching: $artist - $title")
                    if (officialMetadata != null) {
                        Log.d(
                                TAG,
                                "📀 Official: ${officialMetadata.source} | Duration: ${officialDuration/1000}s | Album: ${officialMetadata.album}"
                        )
                    }

                    // 2. Rechercher sur SoundCloud
                    val query = Uri.encode("$artist $title")
                    val url =
                            "https://api-v2.soundcloud.com/search/tracks?q=$query&client_id=$clientId&limit=20"

                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: return@withContext emptyList()

                    val json = JSONObject(body)
                    val collection = json.getJSONArray("collection")

                    for (i in 0 until collection.length()) {
                        if (results.size >= 5) break

                        val trackJson = collection.getJSONObject(i)
                        val durationMs = trackJson.optLong("duration", 0L)

                        // Filtre 1: Durée minimum 2 minutes
                        if (durationMs < 120000) {
                            Log.d(TAG, "⏭️ Skip (trop court): ${durationMs/1000}s")
                            continue
                        }

                        // Filtre 2: Validation durée avec métadonnées officielles
                        if (officialDuration > 0 &&
                                        !metadataManager.isDurationMatch(
                                                durationMs,
                                                officialDuration
                                        )
                        ) {
                            Log.d(
                                    TAG,
                                    "⏭️ Skip (durée différente): SC=${durationMs/1000}s vs Official=${officialDuration/1000}s"
                            )
                            continue
                        }

                        // 3. Détection de la meilleure pochette
                        var artworkUrl =
                                trackJson
                                        .optString("artwork_url", "")
                                        .replace("-large.", "-t500x500.")

                        // Fallback sur avatar utilisateur si artwork absent/par défaut
                        if (artworkUrl.isEmpty() ||
                                        artworkUrl.contains("default_badge") ||
                                        artworkUrl.contains("placeholder")
                        ) {
                            artworkUrl =
                                    trackJson
                                            .optJSONObject("user")
                                            ?.optString("avatar_url", "")
                                            ?.replace("-large.", "-t500x500.")
                                            ?: ""
                        }

                        // Priorité à la cover officielle HD (Deezer/iTunes)
                        val bestArtwork =
                                officialCoverHD
                                        ?: artworkUrl.ifEmpty { null }
                                                ?: fetchiTunesCover(artist, title)

                        // 4. Extraction du flux progressif (MP3 stable)
                        val media = trackJson.optJSONObject("media") ?: continue
                        val transcodings = media.optJSONArray("transcodings") ?: continue
                        var foundStreamUrl: String? = null

                        // Chercher d'abord un flux progressif (meilleure compatibilité)
                        for (j in 0 until transcodings.length()) {
                            val t = transcodings.getJSONObject(j)
                            val format = t.optJSONObject("format") ?: continue
                            if (format.optString("protocol") == "progressive") {
                                val tempUrl = t.getString("url") + "?client_id=$clientId"
                                try {
                                    val sResponse =
                                            client.newCall(Request.Builder().url(tempUrl).build())
                                                    .execute()
                                    foundStreamUrl =
                                            JSONObject(sResponse.body?.string() ?: "")
                                                    .optString("url")
                                    if (foundStreamUrl.isNotEmpty()) break
                                } catch (e: Exception) {
                                    continue
                                }
                            }
                        }

                        // Fallback sur HLS si pas de progressif
                        if (foundStreamUrl.isNullOrEmpty()) {
                            for (j in 0 until transcodings.length()) {
                                val t = transcodings.getJSONObject(j)
                                val format = t.optJSONObject("format") ?: continue
                                if (format.optString("protocol") == "hls") {
                                    val tempUrl = t.getString("url") + "?client_id=$clientId"
                                    try {
                                        val sResponse =
                                                client.newCall(
                                                                Request.Builder()
                                                                        .url(tempUrl)
                                                                        .build()
                                                        )
                                                        .execute()
                                        foundStreamUrl =
                                                JSONObject(sResponse.body?.string() ?: "")
                                                        .optString("url")
                                        if (foundStreamUrl.isNotEmpty()) break
                                    } catch (e: Exception) {
                                        continue
                                    }
                                }
                            }
                        }

                        if (!foundStreamUrl.isNullOrEmpty()) {
                            Log.d(
                                    TAG,
                                    "✅ Match #${results.size + 1}: Duration=${durationMs/1000}s | Cover=${if (officialCoverHD != null) "Official" else "SoundCloud"}"
                            )
                            results.add(
                                    SoundCloudResult(
                                            streamUrl = foundStreamUrl,
                                            artworkUrl = bestArtwork,
                                            durationMs = durationMs
                                    )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error searching for $artist - $title: ${e.message}")
                }

                Log.d(TAG, "📊 Found ${results.size} valid streams for: $artist - $title")
                results
            }

    /**
     * Récupère la cover iTunes en fallback (pour les cas où SoundCloud et les métadonnées
     * officielles échouent)
     */
    private fun fetchiTunesCover(artist: String, title: String): String? {
        return try {
            val itunesUrl =
                    "https://itunes.apple.com/search?term=${Uri.encode("$artist $title")}&entity=song&limit=1"
            val itunesResponse = client.newCall(Request.Builder().url(itunesUrl).build()).execute()
            itunesResponse.body?.string()?.let { body ->
                val results = JSONObject(body).optJSONArray("results")
                if (results != null && results.length() > 0) {
                    results.getJSONObject(0)
                            .optString("artworkUrl100", "")
                            .replace("100x100bb", "600x600bb")
                            .ifEmpty { null }
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Retourne les métadonnées officielles pour un track (utile pour l'affichage). */
    suspend fun getOfficialMetadata(artist: String, title: String): OfficialTrackMetadata? =
            metadataManager.getOfficialMetadata(artist, title)
}
