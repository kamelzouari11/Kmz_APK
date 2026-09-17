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
        val durationMs: Long = 0L,
        val playbackCount: Long = 0L,
        val isVerified: Boolean = false,
        val title: String = ""
)

data class SoundCloudPlaylist(
        val id: Long,
        val userId: Long,
        val title: String,
        val trackCount: Int,
        val artworkUrl: String?,
        val likesCount: Int,
        val permalinkUrl: String,
        val isAlbum: Boolean = false,
        val releaseYear: String? = null,
        val creatorName: String? = null,
        val secretToken: String? = null
)

/**
 * Gestionnaire SoundCloud avec validation multi-sources.
 *
 * Stratégie PRO de sélection des flux:
 * 1. Recherche Deezer/iTunes pour la durée et CoverArtProvider pour la pochette
 * 2. Recherche SoundCloud avec filtrage:
 * ```
 *    - Durée > 2 minutes (évite pubs/teasers)
 *    - Durée proche de la durée officielle (±20% ou ±30s)
 *    - Flux 'progressive' privilégiés (MP3 stables)
 * ```
 * 3. Hiérarchie des covers:
 * ```
 *    a. Cover validée par la même méthode que SimpleRADIO
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
                    // 1. Obtenir la durée officielle et la cover validée par CoverArtProvider
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
                        val scTitle = trackJson.optString("title", "")
                        val durationMs = trackJson.optLong("duration", 0L)
                        val playbackCount = trackJson.optLong("playback_count", 0L)

                        val userObj = trackJson.optJSONObject("user")
                        val isVerified = userObj?.optBoolean("verified", false) ?: false

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

                        // La recherche de cover passe exclusivement par CoverArtProvider.
                        val bestArtwork = officialCoverHD ?: artworkUrl.ifEmpty { null }

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
                                            durationMs = durationMs,
                                            playbackCount = playbackCount,
                                            isVerified = isVerified,
                                            title = scTitle
                                    )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error searching for $artist - $title: ${e.message}")
                }

                Log.d(TAG, "📊 Found ${results.size} candidates. Sorting by score...")

                // Calcul d'un score pour donner la priorité aux versions officielles
                val sortedResults =
                        results.sortedByDescending { res ->
                            var score = res.playbackCount.toDouble()

                            // Bonus énorme pour les comptes vérifiés
                            if (res.isVerified) score *= 100

                            // Bonus pour les mots clés originaux dans le titre
                            val lowerTitle = res.title.lowercase()
                            if (lowerTitle.contains("original") || lowerTitle.contains("official")
                            ) {
                                score *= 1.5
                            }

                            // Malus pour les "remix" ou "cover" si on cherche l'original
                            if (lowerTitle.contains("remix") ||
                                            lowerTitle.contains("edit") ||
                                            lowerTitle.contains("cover")
                            ) {
                                score /= 10
                            }

                            score
                        }
                sortedResults.take(5)
            }

    /** Retourne les métadonnées officielles pour un track (utile pour l'affichage). */
    suspend fun getOfficialMetadata(artist: String, title: String): OfficialTrackMetadata? =
            metadataManager.getOfficialMetadata(artist, title)

    /** Recherche les 5 meilleures playlists d'un artiste. */
    suspend fun searchArtistPlaylists(artist: String): List<SoundCloudPlaylist> =
            withContext(Dispatchers.IO) {
                val playlists = mutableListOf<SoundCloudPlaylist>()
                try {
                    val query = Uri.encode(artist)
                    val url =
                            "https://api-v2.soundcloud.com/search/playlists?q=$query&client_id=$clientId&limit=30"
                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: return@withContext emptyList()

                    val json = JSONObject(body)
                    val collection = json.getJSONArray("collection")

                    for (i in 0 until collection.length()) {
                        val plJson = collection.getJSONObject(i)
                        val title = plJson.optString("title", "")
                        val userObj = plJson.optJSONObject("user")
                        val username = userObj?.optString("username", "") ?: ""

                        // Filtre : Artiste dans le titre OU dans le nom de l'utilisateur
                        if (!title.lowercase().contains(artist.lowercase()) &&
                                        !username.lowercase().contains(artist.lowercase())
                        )
                                continue

                        val trackCount = plJson.optInt("track_count", 0)
                        if (trackCount < 5) continue

                        val playlistType = plJson.optString("playlist_type", "playlist")
                        // On considère comme album si type est album ou ep, ou si le titre contient
                        // "Album"
                        val isAlbum =
                                playlistType.lowercase() == "album" ||
                                        playlistType.lowercase() == "ep" ||
                                        title.lowercase().contains("album")

                        val releaseYear =
                                plJson.optString("display_date", "")
                                        .takeIf { it.isNotBlank() }
                                        ?.take(4)
                                        ?: plJson.optString("created_at", "").take(4).takeIf {
                                            it.isNotBlank()
                                        }

                        playlists.add(
                                SoundCloudPlaylist(
                                        id = plJson.getLong("id"),
                                        userId = userObj?.optLong("id") ?: 0L,
                                        title = if (isAlbum) "💿 $title" else title,
                                        trackCount = plJson.optInt("track_count", 0),
                                        artworkUrl =
                                                plJson.optString("artwork_url", "")
                                                        .replace("-large.", "-t500x500."),
                                        likesCount = plJson.optInt("likes_count", 0),
                                        permalinkUrl = plJson.optString("permalink_url", ""),
                                        isAlbum = isAlbum,
                                        releaseYear = releaseYear,
                                        creatorName = username,
                                        secretToken =
                                                plJson.optString("secret_token", "").takeIf {
                                                    it.isNotBlank()
                                                }
                                )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error searching playlists: ${e.message}")
                }
                // Tri : Priorité aux Likes (Les plus populaires)
                playlists
                        .sortedWith(
                                compareByDescending<SoundCloudPlaylist> { it.likesCount }
                                        .thenByDescending { it.isAlbum }
                        )
                        .take(5)
            }

    /** Cherche toutes les playlists d'un utilisateur spécifique */
    suspend fun searchUserPlaylists(userId: Long): List<SoundCloudPlaylist> =
            withContext(Dispatchers.IO) {
                val playlists = mutableListOf<SoundCloudPlaylist>()
                try {
                    val url =
                            "https://api-v2.soundcloud.com/users/$userId/playlists?client_id=$clientId&limit=20"
                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: return@withContext emptyList()

                    val json = JSONObject(body)
                    val collection = json.getJSONArray("collection")

                    for (i in 0 until collection.length()) {
                        val plJson = collection.getJSONObject(i)
                        val title = plJson.optString("title", "")
                        val playlistType = plJson.optString("playlist_type", "playlist")
                        val isAlbum =
                                playlistType.lowercase() == "album" ||
                                        playlistType.lowercase() == "ep" ||
                                        title.lowercase().contains("album")

                        val releaseYear =
                                plJson.optString("display_date", "")
                                        .takeIf { it.isNotBlank() }
                                        ?.take(4)
                                        ?: plJson.optString("created_at", "").take(4).takeIf {
                                            it.isNotBlank()
                                        }

                        val userObj = plJson.optJSONObject("user")
                        val username = userObj?.optString("username", "") ?: ""

                        val trackCount = plJson.optInt("track_count", 0)
                        if (trackCount < 5) continue

                        playlists.add(
                                SoundCloudPlaylist(
                                        id = plJson.getLong("id"),
                                        userId = userId,
                                        title = if (isAlbum) "💿 $title" else title,
                                        trackCount = trackCount,
                                        artworkUrl =
                                                plJson.optString("artwork_url", "")
                                                        .replace("-large.", "-t500x500."),
                                        likesCount = plJson.optInt("likes_count", 0),
                                        permalinkUrl = plJson.optString("permalink_url", ""),
                                        isAlbum = isAlbum,
                                        releaseYear = releaseYear,
                                        creatorName = username,
                                        secretToken =
                                                plJson.optString("secret_token", "").takeIf {
                                                    it.isNotBlank()
                                                }
                                )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error searching user playlists: ${e.message}")
                }
                playlists.sortedByDescending { it.likesCount }
            }

    /** Charge tous les morceaux d'une playlist et les convertit en modèle Track de l'app. */
    suspend fun getPlaylistTracks(
            playlistId: Long,
            secretToken: String? = null
    ): List<com.kmz.shazamplayer.model.Track> =
            withContext(Dispatchers.IO) {
                val fullTracks = mutableListOf<com.kmz.shazamplayer.model.Track>()
                try {
                    val url = buildString {
                        append(
                                "https://api-v2.soundcloud.com/playlists/$playlistId?client_id=$clientId"
                        )
                        secretToken?.let { append("&secret_token=$it") }
                    }
                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: return@withContext emptyList()

                    val json = JSONObject(body)
                    val tracksJsonArray = json.getJSONArray("tracks")

                    val idsToFetch = mutableListOf<Long>()
                    val partialTracks = mutableMapOf<Int, com.kmz.shazamplayer.model.Track>()

                    for (i in 0 until tracksJsonArray.length()) {
                        val tJson = tracksJsonArray.getJSONObject(i)

                        if (tJson.has("title")) {
                            partialTracks[i] = convertJsonToTrack(tJson, i + 1)
                        } else {
                            idsToFetch.add(tJson.getLong("id"))
                        }
                    }

                    // Si on a des IDs manquants, on les récupère par paquets (max 50 par requête
                    // SC)
                    if (idsToFetch.isNotEmpty()) {
                        idsToFetch.chunked(50).forEach { batchIds ->
                            val idsQuery = batchIds.joinToString(",")
                            val tracksUrl =
                                    "https://api-v2.soundcloud.com/tracks?ids=$idsQuery&client_id=$clientId"
                            val tracksReq = Request.Builder().url(tracksUrl).build()
                            val tracksResp = client.newCall(tracksReq).execute()
                            val tracksBody = tracksResp.body?.string()

                            if (tracksBody != null) {
                                try {
                                    val collection =
                                            if (tracksBody.startsWith("[")) {
                                                org.json.JSONArray(tracksBody)
                                            } else {
                                                val batchJson = JSONObject(tracksBody)
                                                if (batchJson.has("collection")) {
                                                    batchJson.getJSONArray("collection")
                                                } else {
                                                    org.json.JSONArray("[]")
                                                }
                                            }

                                    val fetchedTracksMap = mutableMapOf<Long, JSONObject>()
                                    for (j in 0 until collection.length()) {
                                        val trackObj = collection.getJSONObject(j)
                                        fetchedTracksMap[trackObj.getLong("id")] = trackObj
                                    }

                                    // Réinsérer dans les trous de partialTracks
                                    for (i in 0 until tracksJsonArray.length()) {
                                        if (!partialTracks.containsKey(i)) {
                                            val tJson = tracksJsonArray.getJSONObject(i)
                                            val id = tJson.optLong("id")
                                            fetchedTracksMap[id]?.let {
                                                partialTracks[i] = convertJsonToTrack(it, i + 1)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error parsing track batch: ${e.message}")
                                }
                            }
                        }
                    }

                    // On construit la liste finale ordonnée
                    for (i in 0 until tracksJsonArray.length()) {
                        partialTracks[i]?.let { fullTracks.add(it) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching playlist tracks: ${e.message}")
                }
                fullTracks
            }

    private fun convertJsonToTrack(
            tJson: JSONObject,
            index: Int
    ): com.kmz.shazamplayer.model.Track {
        val user = tJson.optJSONObject("user")
        val scArtist = user?.optString("username", "Inconnu") ?: "Inconnu"
        val scTitle = tJson.optString("title", "")

        val track =
                com.kmz.shazamplayer.model.Track(
                        index = index.toString(),
                        tagTime = "Playlist Discovery",
                        title = scTitle,
                        artist = scArtist,
                        shazamUrl = tJson.optString("permalink_url", ""),
                        trackKey = tJson.optLong("id").toString()
                )

        track.artworkUrl = tJson.optString("artwork_url", "").replace("-large.", "-t500x500.")
        if (track.artworkUrl.isNullOrBlank()) {
            track.artworkUrl = user?.optString("avatar_url", "")?.replace("-large.", "-t500x500.")
        }
        return track
    }
}
