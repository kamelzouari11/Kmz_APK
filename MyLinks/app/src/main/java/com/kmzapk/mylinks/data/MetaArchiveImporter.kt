package com.kmzapk.mylinks.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/** Reads only saved items from Meta's JSON exports. No login or security files are opened. */
object MetaArchiveImporter {
    data class Result(val source: String, val added: Int, val updated: Int, val skipped: Int, val undated: Int)
    private data class Candidate(
        val url: String, val title: String, val source: String, val date: Long,
        val profileUrl: String? = null, val mapsUrl: String? = null,
        val collection: String? = null, val context: String? = null
    )

    suspend fun import(context: Context, archive: Uri, dao: SavedLinkDao): Result = withContext(Dispatchers.IO) {
        val files = mutableMapOf<String, String>()
        context.contentResolver.openInputStream(archive)?.use { stream ->
            ZipInputStream(stream).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (!entry.isDirectory && name.endsWith(".json") && (
                            name.endsWith("/saved_posts.json") ||
                            name.endsWith("/saved_collections.json") ||
                            name.endsWith("/your_saved_items.json") ||
                            name.endsWith("/collections.json") ||
                            name.endsWith("/pages_you've_liked.json")
                        )) {
                        val bytes = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            if (bytes.size() + count > 20_000_000) error("JSON file too large")
                            bytes.write(buffer, 0, count)
                        }
                        files[name.substringAfterLast('/')] = bytes.toString(Charsets.UTF_8.name())
                    }
                    zip.closeEntry()
                }
            }
        } ?: error("Cannot open archive")
        val instagram = files.containsKey("saved_posts.json")
        val facebook = files.containsKey("your_saved_items.json")
        require(instagram || facebook) { "No supported Facebook or Instagram saved items found" }
        val candidates = if (instagram) instagramCandidates(files) else facebookCandidates(files)
        var added = 0; var updated = 0; var skipped = 0; var undated = 0
        for (item in candidates.values) {
            if (item.date == 0L) undated++
            val existing = dao.getByUrl(item.url)
            if (existing == null) {
                dao.insert(SavedLink(
                    title = item.title, category = "",
                    originalUrl = item.url, source = item.source,
                    googleMapsUrl = item.mapsUrl, createdAt = item.date,
                    profileUrl = item.profileUrl, importCollection = item.collection,
                    importContext = item.context
                ))
                added++
            } else {
                val changed = existing.copy(
                    createdAt = if (item.date > 0) item.date else existing.createdAt,
                    profileUrl = existing.profileUrl ?: item.profileUrl,
                    googleMapsUrl = existing.googleMapsUrl ?: item.mapsUrl,
                    importCollection = existing.importCollection ?: item.collection,
                    importContext = existing.importContext ?: item.context
                )
                if (changed != existing) { dao.update(changed); updated++ } else skipped++
            }
        }
        Result(if (instagram) "Instagram" else "Facebook", added, updated, skipped, undated)
    }

    private fun instagramCandidates(files: Map<String, String>): Map<String, Candidate> {
        val result = linkedMapOf<String, Candidate>()
        val posts = JSONArray(files.getValue("saved_posts.json"))
        for (i in 0 until posts.length()) {
            val post = posts.getJSONObject(i)
            val labels = post.optJSONArray("label_values") ?: continue
            val url = label(labels, "URL") ?: continue
            val owner = section(labels, "Propri")
            val username = label(owner, "Nom de profil")
            val name = label(owner, "Nom")
            val external = label(owner, "URL")
            val profile = username?.let { "https://www.instagram.com/${it.trim().trimStart('@')}/" }
            val title = name ?: username ?: label(labels, "Titre") ?: ""
            val context = listOfNotNull(label(labels, "Légende"),
                external?.takeUnless(::isMapsUrl)).joinToString("\n").take(6000).ifBlank { null }
            merge(result, Candidate(url, title, "Instagram", seconds(post.optLong("timestamp")), profile,
                external?.takeIf(::isMapsUrl), context = context))
        }
        files["saved_collections.json"]?.let { raw ->
            val collections = JSONArray(raw)
            for (i in 0 until collections.length()) {
                val labels = collections.getJSONObject(i).optJSONArray("label_values") ?: continue
                val name = label(labels, "Nom") ?: continue
                for (j in 0 until labels.length()) {
                    val entries = labels.optJSONObject(j)?.optJSONArray("dict") ?: continue
                    for (k in 0 until entries.length()) {
                        val url = label(entries.optJSONObject(k)?.optJSONArray("dict"), "URL") ?: continue
                        result[url]?.let { result[url] = it.copy(collection = listOfNotNull(it.collection, name).distinct().joinToString(", ")) }
                    }
                }
            }
        }
        return result
    }

    private fun facebookCandidates(files: Map<String, String>): Map<String, Candidate> {
        val result = linkedMapOf<String, Candidate>()
        val pages = mutableMapOf<String, String>()
        files["pages_you've_liked.json"]?.let { raw ->
            val list = JSONObject(raw).optJSONArray("page_likes_v2")
            if (list != null) for (i in 0 until list.length()) {
                val page = list.optJSONObject(i) ?: continue
                val name = repair(page.optString("name")).trim().lowercase()
                val url = page.optString("url")
                if (name.isNotEmpty() && webUrl(url)) pages[name] = url
            }
        }
        val saves = JSONObject(files.getValue("your_saved_items.json")).optJSONArray("saves_v2") ?: JSONArray()
        for (i in 0 until saves.length()) {
            val save = saves.optJSONObject(i) ?: continue
            val date = seconds(save.optLong("timestamp"))
            val attachments = save.optJSONArray("attachments") ?: continue
            for (a in 0 until attachments.length()) {
                val data = attachments.optJSONObject(a)?.optJSONArray("data") ?: continue
                for (d in 0 until data.length()) {
                    val context = data.optJSONObject(d)?.optJSONObject("external_context") ?: continue
                    val name = repair(context.optString("name")).trim()
                    val rawUrl = context.optString("source").takeIf(::webUrl)
                        ?: context.optString("url").takeIf(::webUrl) ?: continue
                    val page = pages[name.lowercase()]
                    merge(result, Candidate(rawUrl, name, "Facebook", date, page))
                }
            }
        }
        files["collections.json"]?.let { raw ->
            val collections = JSONArray(raw)
            for (i in 0 until collections.length()) {
                val labels = collections.getJSONObject(i).optJSONArray("label_values") ?: continue
                val name = label(labels, "Titre") ?: ""
                for (j in 0 until labels.length()) {
                    val section = labels.optJSONObject(j) ?: continue
                    if (section.optString("title") != "Enregistrements") continue
                    val entries = section.optJSONArray("dict") ?: continue
                    for (k in 0 until entries.length()) {
                        val item = entries.optJSONObject(k)?.optJSONArray("dict") ?: continue
                        val url = label(item, "URL")?.takeIf(::webUrl) ?: continue
                        val title = label(item, "Titre").orEmpty()
                        merge(result, Candidate(url, title, "Facebook", 0L, collection = name))
                    }
                }
            }
        }
        return result
    }

    private fun merge(items: MutableMap<String, Candidate>, item: Candidate) {
        if (!webUrl(item.url)) return
        val old = items[item.url]
        items[item.url] = if (old == null) item else old.copy(
            title = old.title.ifBlank { item.title },
            date = old.date.takeIf { it > 0 } ?: item.date,
            profileUrl = old.profileUrl ?: item.profileUrl,
            mapsUrl = old.mapsUrl ?: item.mapsUrl,
            collection = listOfNotNull(old.collection, item.collection).flatMap { it.split(", ") }
                .distinct().joinToString(", ").ifBlank { null },
            context = old.context ?: item.context
        )
    }

    private fun label(labels: JSONArray?, key: String): String? {
        if (labels == null) return null
        for (i in 0 until labels.length()) {
            val item = labels.optJSONObject(i) ?: continue
            if (repair(item.optString("label")) == key) return repair(item.optString("value")).takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun section(labels: JSONArray, prefix: String): JSONArray? {
        for (i in 0 until labels.length()) {
            val item = labels.optJSONObject(i) ?: continue
            if (!repair(item.optString("title")).startsWith(prefix)) continue
            return item.optJSONArray("dict")?.optJSONObject(0)?.optJSONArray("dict")
        }
        return null
    }

    private fun repair(text: String): String = if ("Ã" in text || "Ø" in text || "â€" in text) {
        runCatching { String(text.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8) }.getOrDefault(text)
    } else text

    private fun seconds(value: Long): Long = if (value > 0) value * 1000 else 0L
    private fun webUrl(value: String): Boolean = value.startsWith("https://") || value.startsWith("http://")
    private fun isMapsUrl(value: String): Boolean = value.contains("maps.google.") ||
        value.contains("maps.app.goo.gl") || value.contains("goo.gl/maps")
}
