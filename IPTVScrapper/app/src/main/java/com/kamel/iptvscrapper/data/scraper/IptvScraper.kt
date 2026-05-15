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
        
        // Pre-clean text: replace non-breaking spaces and common HTML entities
        val cleanText = text.replace("\u00A0", " ")
            .replace("&nbsp;", " ")
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
        
        // Improved URL pattern that stops at spaces and common emoji/separator ranges
        val urlPattern = Pattern.compile("https?://[^\\s\"'<>\\(\\)\\[\\]\\{\\}\\^\\|\\\\\\u27A4\\u2705\\u2714\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+", Pattern.CASE_INSENSITIVE)
        val urlMatcher = urlPattern.matcher(cleanText)
        
        val urlPositions = mutableListOf<Pair<Int, String>>()
        while (urlMatcher.find()) {
            urlPositions.add(urlMatcher.start() to urlMatcher.group())
        }

        for (i in urlPositions.indices) {
            val (pos, urlStr) = urlPositions[i]
            if (urlStr.isBlank()) continue
            
            // Look ahead for the next URL or take a reasonable segment size
            val nextUrlPos = if (i + 1 < urlPositions.size) urlPositions[i + 1].first else minOf(pos + 1000, cleanText.length)
            val segment = cleanText.substring(pos, nextUrlPos)
            
            // 1. M3U Check (get.php, m3u, m3u8, m3u8_plus)
            if (urlStr.contains(Regex("m3u|m3u8|get\\.php|m3u8_plus|type=m3u", RegexOption.IGNORE_CASE))) {
                results.add(LinkEntity(type = "M3U", url = urlStr, sourceUrl = sourceUrl))
                decomposeM3u(urlStr)?.let { results.add(it.copy(sourceUrl = sourceUrl)) }
                continue 
            }
            
            var foundSomething = false

            // 2. Stalker Check (Multiple MACs allowed)
            val pureMacPattern = Pattern.compile("([0-9A-Fa-f:]{17}|[0-9A-Fa-f-]{17})")
            val macMatcher = pureMacPattern.matcher(segment)
            val isStalkerUrl = urlStr.contains(Regex("/c/?$", RegexOption.IGNORE_CASE))
            
            while (macMatcher.find()) {
                val mac = macMatcher.group(1).clean().replace("-", ":")
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
            val userPattern = Pattern.compile("(?:Username|User|Utilisateur|Login|Account|👤Username|👤User|USER|👤)\\s*[:\\u27A4]?\\s*([^\\s\"'<>]+)", Pattern.CASE_INSENSITIVE)
            val passPattern = Pattern.compile("(?:Password|Pass|Pwd|Mot\\s*de\\s*passe|Motdepasse|🔑Password|🔑Pass|Password🔑|PASS|🔑)\\s*[:\\u27A4]?\\s*([^\\s\"'<>]+)", Pattern.CASE_INSENSITIVE)
            
            val userM = userPattern.matcher(segment)
            val passM = passPattern.matcher(segment)
            
            // Find all user/pass pairs in the segment
            while (userM.find()) {
                val user = userM.group(1).clean()
                if (passM.find(userM.end())) { // Look for pass after this user
                    val pass = passM.group(1).clean()
                    results.add(LinkEntity(
                        type = "XTREAM",
                        url = urlStr.trimEnd('/'),
                        username = user,
                        password = pass,
                        sourceUrl = sourceUrl
                    ))
                    foundSomething = true
                }
            }
            
            // 4. Last-ditch: if no labels but the segment looks like it has credentials
            if (!foundSomething) {
                val lines = segment.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                // Skip the first line if it's just the URL
                val dataLines = if (lines.isNotEmpty() && lines[0].startsWith("http")) lines.drop(1) else lines
                
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
