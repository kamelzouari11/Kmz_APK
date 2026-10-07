package com.kmzapk.mylinks.data

import android.util.Base64
import androidx.room.withTransaction
import com.kmzapk.mylinks.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** The same GitHub Contents API used by MyTasks, with a separate MyLinks backup file. */
object GitHubBackup {
    private const val API_URL =
        "https://api.github.com/repos/kamelzouari11/Kmz_APK/contents/MySharedFolder/mylinks_backup.json.gz"
    private val client = OkHttpClient()

    suspend fun upload(db: SavedLinkDatabase): Int {
        val links = db.withTransaction { db.savedLinkDao().getAll() }
        val encoded = withContext(Dispatchers.Default) {
            val root = JSONObject().put("version", 2).put("links", JSONArray().apply {
                links.forEach { link -> put(link.toJson()) }
            })
            val bytes = ByteArrayOutputStream().also { output ->
                GZIPOutputStream(output).use { it.write(root.toString().toByteArray(Charsets.UTF_8)) }
            }.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
        withContext(Dispatchers.IO) {
            val sha = readRemote()?.optString("sha")?.takeIf { it.isNotBlank() }
            val body = JSONObject().put("message", "Update MyLinks backup")
                .put("content", encoded)
            if (sha != null) body.put("sha", sha)
            val request = authorizedRequest().put(body.toString()
                .toRequestBody("application/json".toMediaType())).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("GitHub upload failed (HTTP ${response.code})")
            }
        }
        return links.size
    }

    suspend fun download(db: SavedLinkDatabase): Int {
        val links = withContext(Dispatchers.IO) {
            val remote = readRemote() ?: error("No MyLinks backup found on GitHub")
            val content = remote.optString("content").replace("\n", "").replace("\r", "")
            require(content.isNotBlank()) { "GitHub did not return the backup content" }
            val compressed = Base64.decode(content, Base64.DEFAULT)
            val json = GZIPInputStream(ByteArrayInputStream(compressed)).bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val root = JSONObject(json)
            require(root.getInt("version") in 1..2) { "Unsupported MyLinks backup version" }
            val items = root.getJSONArray("links")
            List(items.length()) { index -> items.getJSONObject(index).toSavedLink() }.also { parsed ->
                require(parsed.map { it.id }.distinct().size == parsed.size) { "Duplicate IDs in backup" }
                require(parsed.map { it.originalUrl }.distinct().size == parsed.size) { "Duplicate URLs in backup" }
            }
        }
        db.withTransaction {
            val dao = db.savedLinkDao()
            dao.deleteAll()
            links.forEach { dao.insert(it) }
        }
        return links.size
    }

    private fun authorizedRequest(): Request.Builder {
        check(BuildConfig.GITHUB_TOKEN.isNotBlank()) { "Set github.token in the shared KmzAPK/local.properties" }
        return Request.Builder().url(API_URL)
            .header("Authorization", "token ${BuildConfig.GITHUB_TOKEN}")
            .header("Accept", "application/vnd.github+json")
    }

    private fun readRemote(): JSONObject? {
        client.newCall(authorizedRequest().get().build()).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) error("GitHub download failed (HTTP ${response.code})")
            return JSONObject(response.body?.string() ?: error("Empty GitHub response"))
        }
    }

    private fun SavedLink.toJson() = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("category", category)
        put("original_url", originalUrl)
        put("source", source)
        put("google_maps_url", googleMapsUrl ?: JSONObject.NULL)
        put("created_at", createdAt)
        put("address", address ?: JSONObject.NULL)
        put("tags", tags)
        put("profile_url", profileUrl ?: JSONObject.NULL)
        put("profile_image_url", profileImageUrl ?: JSONObject.NULL)
        put("import_collection", importCollection ?: JSONObject.NULL)
        put("import_context", importContext ?: JSONObject.NULL)
        put("ai_checked_at", aiCheckedAt ?: JSONObject.NULL)
        put("ai_check_status", aiCheckStatus ?: JSONObject.NULL)
        put("published_at", publishedAt ?: JSONObject.NULL)
    }

    private fun JSONObject.optionalString(name: String): String? =
        if (isNull(name)) null else getString(name)

    private fun JSONObject.toSavedLink(): SavedLink {
        val link = SavedLink(
            id = getLong("id"),
            title = getString("title"),
            category = getString("category"),
            originalUrl = getString("original_url"),
            source = getString("source"),
            googleMapsUrl = optionalString("google_maps_url"),
            createdAt = getLong("created_at"),
            address = optionalString("address"),
            tags = getString("tags"),
            profileUrl = optionalString("profile_url"),
            profileImageUrl = optionalString("profile_image_url"),
            importCollection = optionalString("import_collection"),
            importContext = optionalString("import_context"),
            aiCheckedAt = if (isNull("ai_checked_at")) null else getLong("ai_checked_at"),
            aiCheckStatus = optionalString("ai_check_status"),
            publishedAt = if (isNull("published_at")) null else getLong("published_at")
        )
        require(link.id > 0 && link.originalUrl.isNotBlank()) { "Invalid link in backup" }
        return link
    }
}
