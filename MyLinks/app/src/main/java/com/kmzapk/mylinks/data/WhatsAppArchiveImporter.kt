package com.kmzapk.mylinks.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipInputStream

/** Imports links from one exported WhatsApp conversation, without accessing other chats. */
object WhatsAppArchiveImporter {
    data class Result(val added: Int, val updated: Int, val skipped: Int)
    private data class Message(val savedAt: Long, val author: String, val body: String)

    private val header = Regex("^(\\d{1,2}/\\d{1,2}/\\d{4}), (\\d{1,2}:\\d{2}) - ([^:]+): (.*)$")
    private val dateFormat = DateTimeFormatter.ofPattern("d/M/uuuu H:mm", Locale.FRANCE)
    private val urlPattern = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE)
    private val edited = Regex("\\s*<Ce message a été modifié>\\s*$", RegexOption.IGNORE_CASE)

    suspend fun importIfPresent(context: Context, archive: Uri, dao: SavedLinkDao): Result? = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(archive)?.use { stream ->
            ZipInputStream(stream).use { zip ->
                var found: String? = null
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && entry.name.substringAfterLast('/').endsWith(".txt", true)) {
                        require(found == null) { "Archive contains multiple text files" }
                        val bytes = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            require(bytes.size() + count <= 20_000_000) { "WhatsApp export is too large" }
                            bytes.write(buffer, 0, count)
                        }
                        found = bytes.toString(Charsets.UTF_8.name()).removePrefix("\uFEFF")
                    }
                    zip.closeEntry()
                }
                found
            }
        } ?: return@withContext null
        val messages = parseMessages(text)
        require(messages.isNotEmpty()) { "No dated WhatsApp messages found" }
        var added = 0
        var updated = 0
        var skipped = 0
        for ((index, message) in messages.withIndex()) {
            val matches = urlPattern.findAll(message.body).toList()
            for (match in matches) {
                val url = match.value.trimEnd('.', ',', ';', '!', ')', ']', '"', '\'')
                val platform = platform(url) ?: continue
                val after = message.body.substring(match.range.last + 1).let { urlPattern.replace(it, "") }.trim()
                val before = message.body.substring(0, match.range.first).let { urlPattern.replace(it, "") }.trim()
                val sameMessage = listOf(before, after).filter { it.isNotBlank() }.joinToString(" ").replace(edited, "").trim()
                val next = messages.getOrNull(index + 1)
                val following = if (matches.size == 1 && sameMessage.isBlank() && next != null &&
                    next.author == message.author && next.savedAt in message.savedAt..(message.savedAt + 10 * 60_000) &&
                    next.body.length <= 200 && !urlPattern.containsMatchIn(next.body) &&
                    !next.body.contains("<Médias omis>") && !next.body.contains("Vous avez supprimé")
                ) next.body.replace(edited, "").trim() else ""
                val description = sameMessage.ifBlank { following }.take(200)
                val existing = dao.getByUrl(url)
                if (existing == null) {
                    dao.insert(SavedLink(
                        title = description,
                        category = "",
                        originalUrl = url,
                        source = platform,
                        googleMapsUrl = null,
                        createdAt = message.savedAt,
                        tags = description,
                        importContext = description.ifBlank { null }
                    ))
                    added++
                } else if (description.isNotBlank()) {
                    val mergedTags = mergeDescription(existing.tags, description)
                    val changed = existing.copy(
                        tags = mergedTags,
                        importContext = existing.importContext.takeUnless { it.isNullOrBlank() } ?: description,
                        title = existing.title.ifBlank { description }
                    )
                    if (changed != existing) {
                        dao.update(changed)
                        updated++
                    } else skipped++
                } else skipped++
            }
        }
        Result(added, updated, skipped)
    }

    private fun parseMessages(text: String): List<Message> {
        val result = mutableListOf<Message>()
        for (line in text.lineSequence()) {
            val match = header.matchEntire(line)
            if (match == null) {
                if (result.isNotEmpty()) {
                    val last = result.last()
                    result[result.lastIndex] = last.copy(body = last.body + "\n" + line)
                }
                continue
            }
            val savedAt = runCatching {
                LocalDateTime.parse("${match.groupValues[1]} ${match.groupValues[2]}", dateFormat)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrNull() ?: continue
            result.add(Message(savedAt, match.groupValues[3], match.groupValues[4]))
        }
        return result
    }

    private fun platform(url: String): String? {
        val host = runCatching { Uri.parse(url).host?.lowercase(Locale.ROOT) }.getOrNull() ?: return null
        return when {
            host == "facebook.com" || host.endsWith(".facebook.com") -> "Facebook"
            host == "instagram.com" || host.endsWith(".instagram.com") -> "Instagram"
            else -> null
        }
    }

    private fun mergeDescription(tags: String, description: String): String {
        val existingTags = tags.split(',', '\n').map(String::trim).filter(String::isNotBlank)
        if (existingTags.any { it.equals(description.trim(), ignoreCase = true) }) return tags
        return (existingTags + description.trim()).joinToString(", ").take(1000)
    }
}
