package com.kmzapk.mylinks.data

import android.net.Uri
import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class LinkSuggestion(
    val title: String,
    val category: String,
    val source: String,
    val address: String,
    val profileUrl: String?,
    val googleMapsUrl: String?,
    val profileImageUrl: String?,
    val publishedAt: Long?
)

data class ProfileSuggestion(
    val profileUrl: String?, val imageUrl: String?,
    val googleMapsUrl: String?, val address: String?
)

class GeminiRateLimitException(message: String) : IllegalStateException(message)

private data class PageMetadata(
    val title: String = "",
    val description: String = "",
    val finalUrl: String = "",
    val profileUrl: String? = null,
    val imageUrl: String? = null,
    val siteIconUrl: String? = null,
    val publishedAt: Long? = null,
    val biography: String = "",
    val addressHint: String = "",
    val mapsUrls: Set<String> = emptySet()
)

object LinkSuggestionService {
    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"

    suspend fun publicationDateFor(url: String): Long? = withContext(Dispatchers.IO) {
        readPageMetadata(url).publishedAt
    }

    /** A single failed request is not evidence that a saved link has disappeared. */
    suspend fun isConfirmedNotFound(url: String): Boolean = withContext(Dispatchers.IO) {
        fun status(): Int? {
            val uri = Uri.parse(url)
            if (uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank()) return null
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 MyLinks/1.0")
            }
            return try { connection.responseCode } catch (_: Exception) { null }
            finally { connection.disconnect() }
        }
        status() == HttpURLConnection.HTTP_NOT_FOUND &&
            status() == HttpURLConnection.HTTP_NOT_FOUND
    }

    suspend fun discoverProfile(url: String, knownProfileUrl: String? = null): ProfileSuggestion =
        withContext(Dispatchers.IO) {
            val page = readPageMetadata(url)
            val profileUrl = knownProfileUrl?.takeIf { isWebUrl(it) }
                ?: page.profileUrl ?: profileUrlFor(page.finalUrl)
            val profile = profileUrl?.takeUnless { it == page.finalUrl }
                ?.let { readPageMetadata(it) } ?: page
            ProfileSuggestion(
                profileUrl,
                profile.imageUrl ?: page.imageUrl.takeIf {
                    profileUrl != null && page.profileUrl == profileUrl
                } ?: page.siteIconUrl.takeIf { !isPlatformUrl(page.finalUrl) },
                (profile.mapsUrls + page.mapsUrls).firstOrNull(),
                profile.addressHint.ifBlank { page.addressHint }.takeIf { it.isNotBlank() }
            )
        }

    suspend fun suggest(apiKey: String, url: String, sharedText: String, knownProfileUrl: String? = null): LinkSuggestion =
        withContext(Dispatchers.IO) {
            val page = readPageMetadata(url)
            val profileUrl = knownProfileUrl?.takeIf { isWebUrl(it) }
                ?: page.profileUrl ?: profileUrlFor(page.finalUrl)
            val profile = profileUrl?.let { readPageMetadata(it) } ?: PageMetadata()
            val prompt = """
                Suggest fields for a saved link. Return only JSON with string fields
                title, category, source, address, googleMapsUrl. Use the same language as the source.
                The public advertiser profile and its About/biography are primary evidence.
                The publication is secondary context for the saved item's title. Identify
                the advertiser's main continuing activity, not the subject or format of
                this particular publication. Category is a short business/activity type:
                for example Poissonnerie for a fish shop, Restaurant for a restaurant.
                Never use Instagram, Facebook, photo, video or a publication topic as category.
                Make the title concise, not a copied paragraph.
                Seek an exact Google Maps link on the advertiser profile first.
                Address may be a short locality explicitly shown after a location icon,
                such as Aïn Zaghouan Nord. Return an empty string for every field
                without evidence. Do not invent facts or use generic filler values.
                googleMapsUrl must match an actual URL listed below.
                Never construct a Google Maps URL from an address.
                Advertiser profile URL: ${profileUrl.orEmpty()}
                Advertiser profile title: ${profile.title}
                Advertiser profile About/description: ${profile.description}
                Advertiser profile biography: ${profile.biography}
                Advertiser profile location: ${profile.addressHint}
                Advertiser profile Maps URLs: ${profile.mapsUrls.joinToString(" ")}
                Saved publication URL: $url
                Saved publication shared text: ${sharedText.take(6000)}
                Saved publication resolved URL: ${page.finalUrl}
                Saved publication title: ${page.title}
                Saved publication description: ${page.description}
                Saved publication biography: ${page.biography}
                Found Google Maps URLs: ${(page.mapsUrls + profile.mapsUrls + extractMapsUrls("$url $sharedText")).joinToString(" ")}
            """.trimIndent()
            val body = JSONObject().apply {
                put("contents", org.json.JSONArray().put(JSONObject().put(
                    "parts", org.json.JSONArray().put(JSONObject().put("text", prompt))
                )))
                put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
            }
            val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 30000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-goog-api-key", apiKey)
            }
            try {
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val statusCode = connection.responseCode
                val response = if (statusCode in 200..299) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText().take(8000) }
                    val apiMessage = try {
                        JSONObject(errorBody.orEmpty()).getJSONObject("error").optString("message")
                    } catch (_: Exception) {
                        ""
                    }
                    val message = when {
                        apiMessage.contains("API key not valid", ignoreCase = true) ->
                            "Invalid Gemini API key. Update GEMINI_API_KEY in local.properties and rebuild the app."
                        statusCode == 429 -> "Gemini free quota reached. Try again later."
                        apiMessage.isNotBlank() -> "Gemini HTTP $statusCode: ${apiMessage.replace(apiKey, "[redacted]").take(250)}"
                        else -> "Gemini returned HTTP $statusCode."
                    }
                    if (statusCode == 429) throw GeminiRateLimitException(message)
                    throw IllegalStateException(message)
                }
                val text = JSONObject(response).getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                    .getString("text")
                val result = JSONObject(text)
                val maps = result.optString("googleMapsUrl").trim()
                val suppliedMapsUrls = page.mapsUrls + profile.mapsUrls +
                    extractMapsUrls("$url $sharedText")
                val title = result.optString("title").trim()
                val category = result.optString("category").trim()
                val source = result.optString("source").trim()
                val address = result.optString("address").trim().ifBlank {
                    profile.addressHint.ifBlank { page.addressHint }
                }
                LinkSuggestion(
                    title = title,
                    category = category,
                    source = source,
                    address = address,
                    profileUrl = profileUrl,
                    googleMapsUrl = maps.takeIf { it in suppliedMapsUrls } ?: suppliedMapsUrls.firstOrNull(),
                    profileImageUrl = profile.imageUrl ?: page.imageUrl.takeIf {
                        profileUrl != null && page.profileUrl == profileUrl
                    } ?: page.siteIconUrl.takeIf { !isPlatformUrl(page.finalUrl) },
                    publishedAt = page.publishedAt
                )
            } finally {
                connection.disconnect()
            }
        }

    private fun readPageMetadata(url: String): PageMetadata {
        val uri = Uri.parse(url)
        if (uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank()) return PageMetadata()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 6000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", if (uri.host.orEmpty().contains("instagram.com"))
                "Googlebot" else "Mozilla/5.0 MyLinks/1.0")
        }
        return try {
            if (connection.responseCode !in 200..299 ||
                !connection.contentType.orEmpty().contains("text/html", ignoreCase = true)) return PageMetadata()
            val html = connection.inputStream.bufferedReader().use { reader ->
                val result = StringBuilder()
                val buffer = CharArray(8192)
                while (result.length < 1_200_000) {
                    val count = reader.read(buffer, 0, minOf(buffer.size, 1_200_000 - result.length))
                    if (count <= 0) break
                    result.append(buffer, 0, count)
                }
                result.toString()
            }
            val htmlTitle = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(html)?.groupValues?.get(1).orEmpty()
            val title = metaContent(html, "og:title").ifBlank { htmlTitle }
            val biography = jsonStringField(html, "biography")
            PageMetadata(
                title = cleanHtml(title).take(1500),
                description = cleanHtml(metaContent(html, "og:description")).take(1500),
                finalUrl = connection.url.toString().take(1500),
                profileUrl = profileUrlFor(metaContent(html, "og:url"))
                    ?: profileUrlFor(connection.url.toString())
                    ?: instagramProfileFromMetadata(connection.url.toString(), html)
                    ?: facebookProfileFromHtml(html),
                imageUrl = resolveImageUrl(connection.url.toString(),
                    metaContent(html, "og:image").ifBlank { metaContent(html, "twitter:image") }),
                siteIconUrl = siteIconUrl(connection.url.toString(), html),
                publishedAt = publicationDate(html),
                biography = biography.take(1500),
                addressHint = locationHint(biography).ifBlank {
                    locationHint(metaContent(html, "og:description"))
                },
                mapsUrls = extractMapsUrls(html)
            )
        } catch (_: Exception) {
            PageMetadata()
        } finally {
            connection.disconnect()
        }
    }

    private fun resolveImageUrl(pageUrl: String, imageUrl: String): String? = try {
        val decoded = Html.fromHtml(imageUrl, Html.FROM_HTML_MODE_LEGACY).toString()
        val resolved = URL(URL(pageUrl), decoded).toString()
        resolved.takeIf { imageUrl.isNotBlank() && Uri.parse(it).scheme in listOf("http", "https") }
    } catch (_: Exception) { null }

    private fun siteIconUrl(pageUrl: String, html: String): String? {
        val links = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html)
        return links.mapNotNull { match ->
            val tag = match.value
            val rel = attributeValue(tag, "rel").orEmpty().lowercase().split(Regex("\\s+"))
            if ("icon" !in rel && "apple-touch-icon" !in rel) return@mapNotNull null
            val type = attributeValue(tag, "type").orEmpty()
            if (type.contains("svg", ignoreCase = true) ||
                type.contains("x-icon", ignoreCase = true) ||
                type.contains("vnd.microsoft.icon", ignoreCase = true)) return@mapNotNull null
            val url = resolveImageUrl(pageUrl, attributeValue(tag, "href").orEmpty())
            url?.takeUnless {
                val path = Uri.parse(it).path.orEmpty()
                path.endsWith(".svg", ignoreCase = true) || path.endsWith(".ico", ignoreCase = true)
            }
        }.firstOrNull()
    }

    private fun publicationDate(html: String): Long? {
        val timeTag = Regex("<time\\b[^>]*>", RegexOption.IGNORE_CASE)
            .find(html)?.value?.let { attributeValue(it, "datetime") }
        val jsonDate = Regex("\\\"datePublished\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
            .find(html)?.groupValues?.get(1)
        return sequenceOf(
            metaContent(html, "article:published_time"),
            metaContent(html, "og:article:published_time"),
            metaContent(html, "datePublished"),
            jsonDate,
            timeTag
        ).filterNotNull().mapNotNull { raw ->
            runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()
                ?: runCatching { LocalDate.parse(raw.take(10)).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
        }.firstOrNull { it in 946684800000L..System.currentTimeMillis() }
    }

    private fun isPlatformUrl(url: String): Boolean {
        val host = Uri.parse(url).host?.lowercase().orEmpty()
        return host.startsWith("maps.google.") || host.startsWith("www.google.") ||
            listOf("facebook.com", "instagram.com", "tiktok.com", "youtube.com",
                "youtu.be", "maps.app.goo.gl", "goo.gl").any {
            host == it || host.endsWith(".$it")
        }
    }

    private fun jsonStringField(html: String, key: String): String {
        val marker = "\"$key\":\""
        val start = html.indexOf(marker)
        if (start < 0) return ""
        var index = start + marker.length
        var escaped = false
        while (index < html.length) {
            val character = html[index]
            if (character == '"' && !escaped) break
            escaped = character == '\\' && !escaped
            if (character != '\\') escaped = false
            index++
        }
        if (index >= html.length) return ""
        return try {
            JSONObject("{\"value\":\"${html.substring(start + marker.length, index)}\"}")
                .getString("value")
        } catch (_: Exception) { "" }
    }

    private fun locationHint(value: String): String {
        val decoded = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()
        return decoded.lineSequence().firstOrNull { "📍" in it }
            ?.substringAfter("📍")?.substringBefore("📞")?.trim().orEmpty()
    }

    private fun metaContent(html: String, property: String): String {
        val tags = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html)
        for (tag in tags) {
            val text = tag.value
            val name = attributeValue(text, "property") ?: attributeValue(text, "name")
            if (name.equals(property, ignoreCase = true)) {
                return attributeValue(text, "content").orEmpty()
            }
        }
        return ""
    }

    private fun attributeValue(tag: String, name: String): String? =
        Regex("\\b${Regex.escape(name)}\\s*=\\s*([\"'])(.*?)\\1", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(tag)?.groupValues?.get(2)

    private fun cleanHtml(value: String): String =
        Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()
            .replace(Regex("\\s+"), " ").trim()

    private fun isWebUrl(value: String): Boolean {
        val uri = Uri.parse(value)
        return uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank()
    }

    private fun profileUrlFor(resolvedUrl: String): String? {
        val uri = Uri.parse(resolvedUrl)
        val host = uri.host?.lowercase().orEmpty()
        val segments = uri.pathSegments
        if (segments.isEmpty()) return null
        val name = segments.first()
        return when {
            (host == "facebook.com" || host.endsWith(".facebook.com")) &&
                name == "p" && segments.size >= 2 ->
                "https://www.facebook.com/p/${Uri.encode(segments[1])}/"
            (host == "facebook.com" || host.endsWith(".facebook.com")) &&
                name == "people" && segments.size >= 3 ->
                "https://www.facebook.com/people/${Uri.encode(segments[1])}/${segments[2]}/"
            (host == "facebook.com" || host.endsWith(".facebook.com")) &&
                name.matches(Regex("[A-Za-z0-9._-]+")) &&
                name !in setOf("share", "watch", "reel", "groups", "marketplace",
                    "profile.php", "photo.php", "photos", "story.php", "permalink.php") &&
                (segments.size == 1 || segments.getOrNull(1) in setOf("videos", "posts", "photos")) ->
                "https://www.facebook.com/$name"
            (host == "instagram.com" || host.endsWith(".instagram.com")) &&
                name == "stories" && segments.size >= 2 &&
                segments[1].matches(Regex("[A-Za-z0-9._]+")) ->
                "https://www.instagram.com/${segments[1]}/"
            (host == "instagram.com" || host.endsWith(".instagram.com")) &&
                name.matches(Regex("[A-Za-z0-9._]+")) &&
                (segments.size == 1 || segments.getOrNull(1) in setOf("p", "reel", "tv")) &&
                name !in setOf("p", "reel", "stories", "explore", "tv") ->
                "https://www.instagram.com/$name/"
            (host == "tiktok.com" || host.endsWith(".tiktok.com")) &&
                name.matches(Regex("@[A-Za-z0-9._-]+")) ->
                "https://www.tiktok.com/$name"
            else -> null
        }
    }

    private fun instagramProfileFromMetadata(pageUrl: String, html: String): String? {
        val host = Uri.parse(pageUrl).host?.lowercase().orEmpty()
        if (host != "instagram.com" && !host.endsWith(".instagram.com")) return null
        val description = cleanHtml(metaContent(html, "og:description"))
        val username = Regex("\\s-\\s([A-Za-z0-9._]+)\\s+on\\s+", RegexOption.IGNORE_CASE)
            .find(description)?.groupValues?.get(1) ?: return null
        return "https://www.instagram.com/$username/"
    }

    private fun extractMapsUrls(text: String): Set<String> {
        val normalized = text.replace("\\/", "/").replace("&amp;", "&")
        return Regex("https?://(?:maps\\.google\\.[^\\s\"'<>]+|goo\\.gl/maps/[^\\s\"'<>]+|maps\\.app\\.goo\\.gl/[^\\s\"'<>]+)", RegexOption.IGNORE_CASE)
            .findAll(normalized)
            .map { it.value.substringBefore('\\').trimEnd('.', ',', ';', ')', ']') }
            .toSet()
    }

    private fun facebookProfileFromHtml(html: String): String? {
        val encoded = Regex("facebook\\.com%2F([A-Za-z0-9._-]+)%2Fvideos", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)
        val direct = Regex("facebook\\.com/([A-Za-z0-9._-]+)/videos", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)
        return (encoded ?: direct)?.let { "https://www.facebook.com/$it" }
    }
}
