package com.kamel.iptvscrapper.data.scraper

import com.kamel.iptvscrapper.data.local.entities.LinkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.util.regex.Pattern

class IptvScraper {

    private val stalkerPattern = Pattern.compile(
        "(?:PORTAL|Portal|Real).*?(http[^\\s\"'<>]+).*?(?:MAC|Mac).*?([0-9A-Fa-f:]{17})",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )

    private val xtreamPattern = Pattern.compile(
        "(?:URL|SERVER|PORTAL).*?(http[^\\s\"'<>]+).*?(?:User|USER|Username).*?(?:[:\\u27A4])?\\s*([^\\s\"'<>]+).*?(?:Pass|PASS|Password).*?(?:[:\\u27A4])?\\s*([^\\s\"'<>]+)",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )

    private val m3uPattern = Pattern.compile(
        "(?:M3U|M3U8).*?(http[^\\s\"'<>]+(?:m3u|m3u8|type=m3u|get\\.php)[^\\s\"'<>]+)",
        Pattern.CASE_INSENSITIVE
    )

    suspend fun scrapeLatest(): List<LinkEntity> = withContext(Dispatchers.IO) {
        val links = mutableListOf<LinkEntity>()
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        val baseUrl = "https://stbstalker.alaaeldinee.com/"
        
        try {
            // Strategy 1: Standard Jsoup scraping
            val doc = Jsoup.connect(baseUrl)
                .userAgent(userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .timeout(30000)
                .followRedirects(true)
                .get()
                
            var postLinks = doc.select("a[href]")
                .map { it.attr("abs:href") }
                .filter { it.contains(Regex("/20\\d{2}/\\d{2}/.*\\.html")) }
                .distinct()
            
            // Strategy 2: If Strategy 1 finds nothing, try the feed (Blogger standard)
            if (postLinks.isEmpty()) {
                try {
                    val feedDoc = Jsoup.connect("${baseUrl.removeSuffix("/")}/feeds/posts/default")
                        .userAgent(userAgent)
                        .ignoreContentType(true)
                        .get()
                    postLinks = feedDoc.select("link[rel=alternate][type=text/html]")
                        .map { it.attr("href") }
                        .filter { it.contains(baseUrl.replace("https://", "")) }
                        .distinct()
                } catch (fe: Exception) {
                    fe.printStackTrace()
                }
            }
            
            if (postLinks.isEmpty()) {
                // Last ditch effort: regex on raw HTML
                val rawHtml = Jsoup.connect(baseUrl).userAgent(userAgent).execute().body()
                val regex = Regex("href=[\"'](https?://stbstalker\\.alaaeldinee\\.com/20\\d{2}/\\d{2}/[^\"']+\\.html)[\"']")
                postLinks = regex.findAll(rawHtml).map { it.groupValues[1] }.toList().distinct()
            }

            for (url in postLinks.take(5)) {
                try {
                    val postDoc = Jsoup.connect(url)
                        .userAgent(userAgent)
                        .timeout(20000)
                        .get()
                    
                    // The content is usually in .post-body or .entry-content
                    // We also clean the text to ensure our segment logic works
                    val contentElement = postDoc.select(".post-body, .entry-content, .post, article").firstOrNull()
                    val textToParse = contentElement?.text() ?: postDoc.body().text()
                    
                    val parsed = parseText(textToParse, url)
                    links.addAll(parsed)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Final sanity check: if we found 0 links, try parsing the homepage directly 
        // as some blogs put codes on the main page
        if (links.isEmpty()) {
            try {
                val homeDoc = Jsoup.connect(baseUrl).userAgent(userAgent).get()
                links.addAll(parseText(homeDoc.text(), baseUrl))
            } catch (e: Exception) {}
        }
        
        links.distinctBy { "${it.type}|${it.url}|${it.username}|${it.mac}" }
    }

    fun parseText(text: String, sourceUrl: String? = null): List<LinkEntity> {
        val results = mutableListOf<LinkEntity>()
        
        val cleanText = text.normalizeParserText()
        val urlPattern = Pattern.compile(
            "https?://[^\\s\"'<>\\(\\)\\[\\]\\{\\}\\^|\\\\\\p{So}]+",
            Pattern.CASE_INSENSITIVE
        )
        val urlMatcher = urlPattern.matcher(cleanText)
        
        val urlPositions = mutableListOf<LocatedValue>()
        while (urlMatcher.find()) {
            urlPositions.add(LocatedValue(urlMatcher.start(), urlMatcher.end(), urlMatcher.group().cleanUrl()))
        }

        val credentialPairs = pairCredentialsLocated(
            extractLabeledValues(cleanText, USER_LABELS),
            extractLabeledValues(cleanText, PASSWORD_LABELS)
        )
        val globalMacs = extractMacs(cleanText)

        for (i in urlPositions.indices) {
            val urlMatch = urlPositions[i]
            val pos = urlMatch.start
            val urlStr = urlMatch.value
            if (urlStr.isBlank()) continue
            
            // Include text before and after the URL: some posts use user/pass/url order.
            val segmentStart = if (i > 0) {
                maxOf(urlPositions[i - 1].end, pos - 1200)
            } else {
                maxOf(0, pos - 1200)
            }
            val segmentEnd = if (i + 1 < urlPositions.size) {
                minOf(urlPositions[i + 1].start, pos + 1200)
            } else {
                minOf(cleanText.length, pos + 1200)
            }
            val segment = cleanText.substring(segmentStart, segmentEnd)
            
            // 1. M3U Check (get.php, m3u, m3u8, m3u8_plus)
            if (urlStr.contains(Regex("m3u|m3u8|get\\.php|m3u8_plus|type=m3u", RegexOption.IGNORE_CASE))) {
                results.add(LinkEntity(type = "M3U", url = urlStr, sourceUrl = sourceUrl))
                decomposeM3u(urlStr)?.let { results.add(it.copy(sourceUrl = sourceUrl)) }
                continue 
            }
            
            var foundSomething = false

            // 2. Stalker Check (Multiple MACs allowed)
            val macs = globalMacs.filter { closestUrlIndex(it.start, urlPositions) == i }
            for (macValue in macs) {
                val mac = macValue.value.cleanValue().replace("-", ":")
                var stalkerUrl = urlStr.clean()
                if (!stalkerUrl.contains("/c", ignoreCase = true)) {
                    stalkerUrl = if (stalkerUrl.endsWith("/")) "${stalkerUrl}c/" else "$stalkerUrl/c/"
                } else if (!stalkerUrl.endsWith("/")) {
                    stalkerUrl = "$stalkerUrl/"
                }
                results.add(LinkEntity(
                    type = "STALKER",
                    url = stalkerUrl,
                    mac = mac,
                    sourceUrl = sourceUrl
                ))
                foundSomething = true
            }

            // 3. Xtream Check (Multiple User/Pass allowed)
            val pairs = credentialPairs.filter { closestUrlIndex(it.center, urlPositions) == i }

            for (pair in pairs) {
                results.add(LinkEntity(
                    type = "XTREAM",
                    url = urlStr.trimEnd('/'),
                    username = pair.user,
                    password = pair.password,
                    sourceUrl = sourceUrl
                ))
                foundSomething = true
            }
            
            // 4. Last-ditch: if no labels but the segment looks like it has credentials
            if (!foundSomething) {
                val lines = segment.split("\n")
                    .map { it.cleanValue() }
                    .filter { it.isNotEmpty() && !it.startsWith("http", ignoreCase = true) }
                    .filterNot { line ->
                        URL_LABELS.any { label -> line.equals(label, ignoreCase = true) } ||
                            USER_LABELS.any { label -> line.equals(label, ignoreCase = true) } ||
                            PASSWORD_LABELS.any { label -> line.equals(label, ignoreCase = true) }
                    }
                val dataLines = lines.filterNot { it.contains(":") && extractMacs(it).isNotEmpty() }
                
                if (dataLines.size >= 2) {
                    val line1 = dataLines[0]
                    val line2 = dataLines[1]
                    // Could be Xtream user/pass block
                    if (line1.length < 50 && line2.length < 50 && !line1.startsWith("http") && !line2.startsWith("http")) {
                        results.add(LinkEntity(
                            type = "XTREAM",
                            url = urlStr.trimEnd('/'),
                            username = line1,
                            password = line2,
                            sourceUrl = sourceUrl
                        ))
                    }
                }
            }
        }
        
        return results.distinctBy { "${it.type}|${it.url}|${it.username}|${it.mac}" }
    }

    private data class LocatedValue(val start: Int, val end: Int, val value: String)
    private data class CredentialPair(val user: String, val password: String, val center: Int)

    private companion object {
        val URL_LABELS = listOf("url", "host", "server", "portal", "dns", "panel", "real")
        val USER_LABELS = listOf("username", "user", "utilisateur", "login", "account", "compte")
        val PASSWORD_LABELS = listOf("password", "pass", "pwd", "mot de passe", "motdepasse", "senha")
    }

    private fun String.normalizeParserText(): String {
        return replace("\u00A0", " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(Regex("[\\t ]+"), " ")
    }

    private fun extractLabeledValues(segment: String, labels: List<String>): List<LocatedValue> {
        val labelRegex = labels.joinToString("|") { label ->
            label.trim().split(Regex("\\s+")).joinToString("\\s*") { Regex.escape(it) }
        }
        val pattern = Pattern.compile(
            "(?:^|[\\s\\p{Punct}\\p{So}])(?:$labelRegex)\\s*[:=\\-–—>➤➡\\u27A4]*\\s*([^\\s\"'<>|;]+)",
            Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        )
        val matcher = pattern.matcher(segment)
        val values = mutableListOf<LocatedValue>()
        while (matcher.find()) {
            val raw = matcher.group(1).cleanValue()
            if (raw.isNotBlank() && !raw.startsWith("http", ignoreCase = true)) {
                values.add(LocatedValue(matcher.start(1), matcher.end(1), raw))
            }
        }
        return values
    }

    private fun extractMacs(segment: String): List<LocatedValue> {
        val macPattern = Pattern.compile(
            "(?:mac(?:\\s*address)?\\s*[:=\\-–—>➤➡\\u27A4]*\\s*)?([0-9A-Fa-f]{2}(?:[:-][0-9A-Fa-f]{2}){5})",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = macPattern.matcher(segment)
        val values = mutableListOf<LocatedValue>()
        while (matcher.find()) {
            values.add(LocatedValue(matcher.start(1), matcher.end(1), matcher.group(1)))
        }
        return values.distinctBy { it.value.lowercase().replace("-", ":") }
    }

    private fun pairCredentialsLocated(users: List<LocatedValue>, passwords: List<LocatedValue>): List<CredentialPair> {
        if (users.isEmpty() || passwords.isEmpty()) return emptyList()

        val remainingPasswords = passwords.toMutableList()
        val pairs = mutableListOf<CredentialPair>()
        for (user in users) {
            val pass = remainingPasswords.minByOrNull { kotlin.math.abs(it.start - user.start) } ?: continue
            remainingPasswords.remove(pass)
            pairs.add(CredentialPair(
                user = user.value.cleanValue(),
                password = pass.value.cleanValue(),
                center = (user.start + pass.start) / 2
            ))
        }
        return pairs.filter { it.user.isNotBlank() && it.password.isNotBlank() }
    }

    private fun closestUrlIndex(position: Int, urls: List<LocatedValue>): Int {
        return urls.indices.minByOrNull { index ->
            val url = urls[index]
            val center = (url.start + url.end) / 2
            kotlin.math.abs(center - position)
        } ?: -1
    }

    private fun String.cleanValue(): String {
        return clean()
            .trim()
            .trim { it.isWhitespace() || it in charArrayOf(':', '=', '-', '–', '—', '>', '➤', '➡', '\u27A4', '"', '\'', ',', ';') }
    }

    private fun String.cleanUrl(): String {
        return clean()
            .trim()
            .trimEnd('.', ',', ';', '"', '\'', ')', ']', '}', '>', '➤', '➡', '\u27A4')
    }

    private fun decomposeM3u(url: String): LinkEntity? {
        return try {
            val cleanUrl = url.trim()
            val uri = java.net.URI(cleanUrl)
            val query = uri.query ?: return null
            val params = query.split("&").associate {
                val parts = it.split("=")
                parts[0] to (parts.getOrNull(1) ?: "")
            }
            
            val username = params["username"]
            val password = params["password"]
            
            if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
                // Remove the filename (like get.php) from the path
                val path = uri.path ?: ""
                val cleanPath = if (path.contains("/")) {
                    path.substringBeforeLast("/") + "/"
                } else {
                    "/"
                }
                
                val baseUrl = "${uri.scheme}://${uri.host}${if (uri.port != -1) ":${uri.port}" else ""}$cleanPath"
                
                LinkEntity(
                    type = "XTREAM",
                    url = baseUrl,
                    username = username,
                    password = password,
                    status = "PENDING"
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun String?.clean(): String {
        return this?.replace(Regex("<[^>]*>"), "")
            ?.replace("&amp;", "&")
            ?.replace("&lt;", "<")
            ?.replace("&gt;", ">")
            ?.trim() ?: ""
    }
}
