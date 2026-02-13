

package com.example.simpleradio.data.api

import android.net.Uri
import android.util.Log
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Gestionnaire de métadonnées musicales multi-sources (Deezer/iTunes).
 * Priorité à la qualité et aux pochettes HD pour les titres radios.
 */
data class OfficialTrackMetadata(
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val coverUrl: String?,
    val coverUrlHD: String?,
    val source: String
)

class MusicMetadataManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "MusicMetadata"
    }

    suspend fun getOfficialMetadata(artist: String, title: String): OfficialTrackMetadata? =
        withContext(Dispatchers.IO) {
            // 1. Deezer
            val deezerResult = searchDeezer(artist, title)
            if (deezerResult != null) return@withContext deezerResult

            // 2. iTunes
            val itunesResult = searchiTunes(artist, title)
            if (itunesResult != null) return@withContext itunesResult

            null
        }

    private fun searchDeezer(artist: String, title: String): OfficialTrackMetadata? {
        return try {
            val cleanArtist = cleanSearchTerm(artist)
            val cleanTitle = cleanSearchTerm(title)
            val query = Uri.encode("artist:\"$cleanArtist\" track:\"$cleanTitle\"")
            val url = "https://api.deezer.com/search?q=$query&limit=3"

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null

            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: return null

            if (data.length() == 0) return searchDeezerSimple(artist, title)

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
                    ?: albumObj?.optString("cover_medium")?.replace("250x250", "600x600"),
                source = "deezer"
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun searchDeezerSimple(artist: String, title: String): OfficialTrackMetadata? {
        return try {
            val query = Uri.encode("$artist $title")
            val url = "https://api.deezer.com/search?q=$query&limit=3"

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
                    ?: albumObj?.optString("cover_medium")?.replace("250x250", "600x600"),
                source = "deezer"
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun searchiTunes(artist: String, title: String): OfficialTrackMetadata? {
        return try {
            val query = Uri.encode("$artist $title")
            val url = "https://itunes.apple.com/search?term=$query&entity=song&limit=3"

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
            null
        }
    }

    private fun cleanSearchTerm(term: String): String {
        return term.replace("\"", "").replace("'", "'").replace(":", " ").replace("/", " ").trim()
    }
}
