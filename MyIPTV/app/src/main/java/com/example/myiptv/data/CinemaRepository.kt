package com.example.myiptv.data

import android.content.Context
import android.util.AtomicFile
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlin.coroutines.coroutineContext

internal data class CinemaCountry(val code: String, val name: String, val feed: String)
internal val cinemaCountries = listOf(
    CinemaCountry("FR", "France", "https://xmltvfr.fr/xmltv/xmltv_fr.xml.gz"),
    CinemaCountry("IT", "Italie", "IT1"), CinemaCountry("ES", "Espagne", "ES1"),
    CinemaCountry("UK", "Royaume-Uni", "UK1"), CinemaCountry("DE", "Allemagne", "DE1"),
    CinemaCountry("BE", "Belgique", "BE2"), CinemaCountry("CH", "Suisse", "CH1"),
    CinemaCountry("PT", "Portugal", "PT1"), CinemaCountry("NL", "Pays-Bas", "NL1"),
    CinemaCountry("PL", "Pologne", "PL1"), CinemaCountry("US", "États-Unis", "US2"),
    CinemaCountry("CA", "Canada", "CA2"),
)
internal data class CinemaProgram(
    val source: String, val channelId: String, val channelName: String, val country: String,
    val title: String, val description: String, val start: Long, val stop: Long,
    val image: String = "", val year: String = "", val genres: String = "",
) {
    val channelKey get() = "$source|$channelId"
    val key get() = "$channelKey|$start"
}
internal data class CinemaPage(val programs: List<CinemaProgram>, val notices: List<String>)
private data class CinemaCountryLoad(val programs: List<CinemaProgram>, val notice: String? = null)

private val cinemaCacheDurationMillis = TimeUnit.HOURS.toMillis(12)
internal fun cinemaCacheIsFresh(updated: Long, now: Long = System.currentTimeMillis()): Boolean =
    updated > 0 && now - updated in 0 until cinemaCacheDurationMillis

internal enum class CinemaPeriod(val label: String) {
    PAST("Past"), NOW("Now"), NEXT("Next"), LATER("Later")
}

/** The complete source horizon is retained; period buttons only filter the local cache. */
internal fun cinemaInPeriod(p: CinemaProgram, period: CinemaPeriod, now: Instant): Boolean {
    if (p.stop <= p.start) return false
    return when (period) {
        CinemaPeriod.PAST -> p.stop <= now.epochSecond
        CinemaPeriod.NOW -> p.start <= now.epochSecond && p.stop > now.epochSecond
        CinemaPeriod.NEXT, CinemaPeriod.LATER -> p.start > now.epochSecond
    }
}

/** One upcoming film per channel is Next; all remaining future films belong to Later. */
internal fun cinemaProgramsInPeriod(
    programs: List<CinemaProgram>,
    period: CinemaPeriod,
    now: Instant,
): List<CinemaProgram> {
    val usable = programs.filter { cinemaInPeriod(it, period, now) }
    if (period == CinemaPeriod.PAST) {
        return usable.sortedWith(compareByDescending<CinemaProgram> { it.stop }.thenBy { it.title })
    }
    if (period == CinemaPeriod.NOW) {
        return usable.sortedWith(compareByDescending<CinemaProgram> { it.start }.thenBy { it.title })
    }
    val nextKeys = usable
        .groupBy(CinemaProgram::channelKey)
        .mapValues { (_, entries) -> entries.minBy(CinemaProgram::start).key }
        .values
        .toSet()
    return when (period) {
        CinemaPeriod.NEXT -> usable.filter { it.key in nextKeys }
        CinemaPeriod.LATER -> usable.filterNot { it.key in nextKeys }
        CinemaPeriod.PAST, CinemaPeriod.NOW -> usable
    }.sortedWith(compareBy<CinemaProgram> { it.start }.thenBy { it.channelName }.thenBy { it.title })
}

private val cinemaMarks = Regex("\\p{M}+")
private val cinemaQuality = Regex("\\b(?:sd|hd|fhd|uhd|4k|8k|hevc|h265|h264|1080p|720p)\\b")
private val cinemaCountryPrefix = Regex("^(?:fr|it|es|uk|gb|de|be|ch|pt|nl|pl|us|usa|ca)\\s*[:|_-]\\s*")
private val cinemaCountryWord = Regex("^(?:fr|it|es|uk|gb|de|be|ch|pt|nl|pl|us|usa|ca) +")
private val cinemaSeparators = Regex("[^a-z0-9]+")

internal fun cinemaName(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(cinemaMarks, "").lowercase(Locale.ROOT)
    .replace(cinemaQuality, " ")
    .replace(cinemaCountryPrefix, "")
    .replace("+", " plus ").replace(cinemaSeparators, " ").trim()
    .replace(cinemaCountryWord, "")

internal fun catalogueMatches(program: CinemaProgram, query: String): Boolean {
    val terms = cinemaName(query).split(' ').filter(String::isNotBlank)
    if (terms.isEmpty()) return true
    val searchable = cinemaName(
        listOf(program.title, program.description, program.genres, program.channelName, program.year)
            .joinToString(" "),
    )
    val words = searchable.split(' ').filter(String::isNotBlank)
    return terms.all { term ->
        searchable.contains(term) ||
            (term.length >= 5 && words.any { word -> differsByAtMostOneCharacter(term, word) })
    }
}

private fun differsByAtMostOneCharacter(first: String, second: String): Boolean {
    if (kotlin.math.abs(first.length - second.length) > 1) return false
    val shorter = if (first.length <= second.length) first else second
    val longer = if (first.length <= second.length) second else first
    var shortIndex = 0
    var longIndex = 0
    var differences = 0
    while (shortIndex < shorter.length && longIndex < longer.length) {
        if (shorter[shortIndex] == longer[longIndex]) {
            shortIndex++
            longIndex++
        } else {
            if (++differences > 1) return false
            if (shorter.length == longer.length) shortIndex++
            longIndex++
        }
    }
    if (longIndex < longer.length) differences++
    return differences <= 1
}

private fun normalizedCinemaCountry(value: String): String =
    value.uppercase(Locale.ROOT).let { if (it == "GB") "UK" else it }

internal fun cinemaMatches(p: CinemaProgram, channels: List<SavedChannel>): List<SavedChannel> {
    val name = cinemaName(p.channelName)
    return channels.filter { c ->
        normalizedCinemaCountry(c.countryCode) == normalizedCinemaCountry(p.country) && (cinemaName(c.name) == name ||
            (!c.epgChannelId.isNullOrBlank() && c.epgChannelId == p.channelId))
    } // Deliberately preserve every stream ID, including identical names and all quality variants.
}

internal class CinemaRepository(context: Context) {
    private val database = MyIptvDatabase.get(context)
    private val folder = File(context.filesDir, "cinema").apply { mkdirs() }
    private val preferences = context.getSharedPreferences("cinema_links", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val client = OkHttpClient.Builder().callTimeout(90, TimeUnit.SECONDS).build()
    @Volatile private var channelsByName: Map<String, List<SavedChannel>> = emptyMap()
    @Volatile private var channelsByEpgId: Map<String, List<SavedChannel>> = emptyMap()
    @Volatile private var channelsByStreamId: Map<Int, SavedChannel> = emptyMap()

    suspend fun strongChannels(): List<SavedChannel> = withContext(Dispatchers.IO) {
        val profile = database.profileDao().getAll().firstOrNull {
            it.name.trim().equals("strong iptv", ignoreCase = true)
        } ?: return@withContext emptyList()
        database.channelDao().getForEpg(profile.id).also { channels ->
            channelsByName = channels.groupBy { channel ->
                "${normalizedCinemaCountry(channel.countryCode)}|${cinemaName(channel.name)}"
            }
            channelsByEpgId = channels
                .filter { !it.epgChannelId.isNullOrBlank() }
                .groupBy { it.epgChannelId!!.trim() }
            channelsByStreamId = channels.associateBy(SavedChannel::streamId)
        }
    }

    fun variants(p: CinemaProgram, channels: List<SavedChannel>): List<SavedChannel> {
        val profile = channels.firstOrNull()?.profileId ?: return emptyList()
        val key = "$profile|${p.channelKey}"
        val ids = preferences.getStringSet(key, null)
        val result = if (ids == null) {
            val byName = channelsByName["${normalizedCinemaCountry(p.country)}|${cinemaName(p.channelName)}"].orEmpty()
            val byEpg = channelsByEpgId[p.channelId].orEmpty()
            (byName + byEpg).distinctBy { it.streamId }
        } else {
            ids.mapNotNull { it.toIntOrNull()?.let(channelsByStreamId::get) }
        }
        val last = preferences.getInt("last|$key", -1)
        return result.sortedBy { if (it.streamId == last) 0 else 1 }
    }

    fun associate(p: CinemaProgram, channels: List<SavedChannel>, selected: Set<Int>) {
        val profile = channels.firstOrNull()?.profileId ?: return
        preferences.edit().putStringSet("$profile|${p.channelKey}", selected.map(Int::toString).toSet()).apply()
    }

    fun rememberVariant(p: CinemaProgram, c: SavedChannel) {
        preferences.edit().putInt("last|${c.profileId}|${p.channelKey}", c.streamId).apply()
    }

    suspend fun load(
        codes: Set<String>,
        refresh: Boolean,
        sports: Boolean = false,
        progress: (String) -> Unit,
    ): CinemaPage = withContext(Dispatchers.IO) {
        mutex.withLock {
            val selectedCountries = cinemaCountries.filter { it.code in codes }
            val downloadSlots = Semaphore(4)
            val results = coroutineScope {
                selectedCountries.map { country ->
                    async {
                        val date = LocalDate.now(EpgSearch.tunis)
                        val cachePrefix = if (sports) "sports_" else "cinema_"
                        val file = AtomicFile(File(folder, "$cachePrefix${country.code}.json"))
                        val cached = runCatching {
                            JSONObject(file.openRead().bufferedReader().use { it.readText() })
                        }.getOrNull()
                        val cachedPrograms = cached?.let { runCatching { decode(it) }.getOrNull() }.orEmpty()
                        val recent = cachedPrograms.isNotEmpty() &&
                            cached?.optInt("version") == 2 &&
                            cinemaCacheIsFresh(cached?.optLong("updated") ?: 0)
                        if (!refresh && recent) return@async CinemaCountryLoad(cachedPrograms)

                        downloadSlots.withPermit {
                            coroutineContext.ensureActive()
                            progress("Téléchargement ${if (sports) "sports" else "cinéma"} · ${country.name}…")
                            try {
                                val urls = if (country.code == "FR") {
                                    listOf(country.feed, feedUrl("FR1"))
                                } else {
                                    listOf(feedUrl(country.feed))
                                }
                                var loaded = emptyList<CinemaProgram>()
                                for (url in urls) {
                                    try {
                                        loaded = download(country, url, date, sports)
                                        if (loaded.isNotEmpty()) break
                                    } catch (e: Exception) {
                                        coroutineContext.ensureActive()
                                        if (url == urls.last()) throw e
                                    }
                                }
                                check(loaded.isNotEmpty()) {
                                    "Aucun programme ${if (sports) "sportif" else "cinéma"} dans le guide reçu"
                                }
                                val output = file.startWrite()
                                try {
                                    output.write(encode(loaded, date).toString().toByteArray(Charsets.UTF_8))
                                    file.finishWrite(output)
                                } catch (e: Exception) {
                                    file.failWrite(output)
                                    throw e
                                }
                                CinemaCountryLoad(loaded)
                            } catch (e: Exception) {
                                coroutineContext.ensureActive()
                                if (cachedPrograms.isNotEmpty()) {
                                    CinemaCountryLoad(
                                        cachedPrograms,
                                        "${country.name} : dernier cache conservé, actualisation indisponible.",
                                    )
                                } else {
                                    CinemaCountryLoad(
                                        emptyList(),
                                        "${country.name} : programmes indisponibles. Réessayez plus tard.",
                                    )
                                }
                            }
                        }
                    }
                }.awaitAll()
            }
            progress("Catalogue ${if (sports) "sports" else "cinéma"} prêt")
            val programs = results.flatMap(CinemaCountryLoad::programs)
            val notices = results.mapNotNull(CinemaCountryLoad::notice)
            CinemaPage(programs.distinctBy { it.key }.sortedWith(
                compareBy<CinemaProgram> { cinemaCountries.indexOfFirst { c -> c.code == it.country } }
                    .thenBy { it.start }.thenBy { it.channelName },
            ), notices)
        }
    }

    private fun feedUrl(tag: String) = "https://epgshare01.online/epgshare01/epg_ripper_$tag.xml.gz"

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun download(
        country: CinemaCountry,
        url: String,
        date: LocalDate,
        sports: Boolean,
    ): List<CinemaProgram> {
        val call = client.newCall(Request.Builder().url(url).build())
        val response = kotlinx.coroutines.suspendCancellableCoroutine<okhttp3.Response> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(e))
                }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    continuation.resume(response, onCancellation = { response.close() })
                }
            })
        }
        response.use {
            check(it.isSuccessful) { "HTTP ${it.code}" }
            return GZIPInputStream(it.body?.byteStream() ?: error("Réponse vide")).use { input ->
                parseCinemaXml(input, country.code, url, date, sports)
            }
        }
    }

    private fun encode(programs: List<CinemaProgram>, date: LocalDate): JSONObject = JSONObject()
        .put("version", 2).put("date", date.toString()).put("updated", System.currentTimeMillis()).put("programs", JSONArray().apply {
            programs.forEach { p -> put(JSONObject().put("source", p.source).put("channelId", p.channelId)
                .put("channelName", p.channelName).put("country", p.country).put("title", p.title)
                .put("description", p.description).put("start", p.start).put("stop", p.stop)
                .put("image", p.image).put("year", p.year).put("genres", p.genres)) }
        })
    private fun decode(json: JSONObject): List<CinemaProgram> {
        val items = json.optJSONArray("programs") ?: return emptyList()
        return (0 until items.length()).map { i -> items.getJSONObject(i).let { p ->
            CinemaProgram(p.getString("source"), p.getString("channelId"), p.getString("channelName"),
                p.getString("country"), p.getString("title"), p.optString("description"),
                p.getLong("start"), p.getLong("stop"), p.optString("image"), p.optString("year"),
                p.optString("genres"))
        } }
    }
}

private val cinemaChannelPattern = Regex(
    "cine|cinema|movie|film|\\btcm\\b|\\bocs\\b|\\bhbo\\b|starz|showtime|screenpix|hollywood|super ecran|super channel|crave|canal\\s*play|a la carte|bein.*cinema|lux\\s*play|premium\\s*play|^prime(?: video| movies?| cinema)?(?: [0-9]+)?$|^netflix(?: movies?| cinema)?(?: [0-9]+)?$|canal.*(?:grand ecran|box office)|^action$|paramount",
)
private val sportsChannelPattern = Regex(
    "sport|football|soccer|calcio|bein|dazn|espn|eurosport|tnt sports|sky sports|canal plus foot|canal plus live|nba|nfl|nhl|mlb|golf|tennis|rugby|racing|motor|fight|ufc|wwe|f1|formula 1|premier sports|optus",
)
private val nonFilmCategory = Regex("series|serie|episode|sport|news|magazine|talk.show", RegexOption.IGNORE_CASE)
private val xmltvTime = DateTimeFormatter.ofPattern("yyyyMMddHHmmss xx", Locale.ROOT)
internal fun cinemaXmlTime(value: String?): Long? = value?.trim()?.let {
    runCatching { OffsetDateTime.parse(it.replace(Regex("\\s+"), " "), xmltvTime).toEpochSecond() }.getOrNull()
}

/** Streaming parser: never loads the complete country XML in memory. Missing offsets/stops are not guessed. */
internal suspend fun parseCinemaXml(
    input: InputStream,
    country: String,
    source: String,
    @Suppress("UNUSED_PARAMETER") date: LocalDate,
    sports: Boolean = false,
): List<CinemaProgram> {
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    parser.setInput(input, null)
    val names = mutableMapOf<String, String>()
    val output = mutableListOf<CinemaProgram>()
    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
        coroutineContext.ensureActive()
        if (parser.eventType == XmlPullParser.START_TAG && parser.name == "channel") {
            val id = parser.getAttributeValue(null, "id").orEmpty()
            var name = ""
            while (!(parser.next() == XmlPullParser.END_TAG && parser.name == "channel")) {
                coroutineContext.ensureActive()
                if (parser.eventType == XmlPullParser.END_DOCUMENT) error("XML incomplet")
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "display-name") {
                    val candidate = parser.nextText()
                    if (name.isEmpty()) name = candidate
                }
            }
            val normalizedName = cinemaName(name)
            val accepted = if (sports) {
                sportsChannelPattern.containsMatchIn(normalizedName)
            } else {
                cinemaChannelPattern.containsMatchIn(normalizedName) ||
                    (country == "US" && normalizedName.matches(Regex("(?:amc|fxm|flix|tmc)(?: .*|$)"))) ||
                    (country == "ES" && normalizedName.matches(Regex("m plus (?:estrenos|accion|comedia|clasicos|drama|suspense|romance|indie)(?: .*|$)")))
            }
            if (accepted) names[id] = name
        } else if (parser.eventType == XmlPullParser.START_TAG && parser.name == "programme") {
            val id = parser.getAttributeValue(null, "channel").orEmpty()
            val start = cinemaXmlTime(parser.getAttributeValue(null, "start"))
            val stop = cinemaXmlTime(parser.getAttributeValue(null, "stop"))
            val wanted = id in names && start != null && stop != null && stop > start
            var title = ""; var description = ""; var image = ""; var year = ""
            val categories = mutableListOf<String>()
            while (!(parser.next() == XmlPullParser.END_TAG && parser.name == "programme")) {
                coroutineContext.ensureActive()
                if (parser.eventType == XmlPullParser.END_DOCUMENT) error("XML incomplet")
                if (wanted && parser.eventType == XmlPullParser.START_TAG) when (parser.name) {
                    "title" -> { val text = parser.nextText(); if (title.isEmpty()) title = text }
                    "desc" -> { val text = parser.nextText(); if (description.isEmpty()) description = text }
                    "date" -> year = parser.nextText().take(4)
                    "category" -> categories += parser.nextText()
                    "icon" -> if (image.isEmpty()) image = parser.getAttributeValue(null, "src").orEmpty().takeIf { it.startsWith("https://") }.orEmpty()
                }
            }
            val acceptedProgram = if (sports) true else categories.none { nonFilmCategory.containsMatchIn(it) }
            if (wanted && title.isNotBlank() && acceptedProgram) {
                output += CinemaProgram(source, id, names.getValue(id), country, title, description,
                    start!!, stop!!, image, year, categories.distinct().joinToString(" · "))
            }
        }
        parser.next()
    }
    return output
}
