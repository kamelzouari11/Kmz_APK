package com.example.myiptv.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.coroutines.coroutineContext

/** Disposable SQLite cache containing at most four short-EPG entries per channel. */
class EpgSearchRepository(
    context: Context,
    private val database: MyIptvDatabase,
    private val repository: MyIptvRepository,
) {
    /* filesDir survives an app update/reinstall over the existing installation. */
    private val directory = File(context.filesDir, "epg").apply { mkdirs() }
    private val mutex = Mutex()
    private val preferences = context.getSharedPreferences("epg_countries", Context.MODE_PRIVATE)

    init {
        // Keep caches created by older versions, which used the disposable cache directory.
        val previousDirectory = File(context.cacheDir, "epg")
        if (directory.listFiles().isNullOrEmpty() && previousDirectory.isDirectory) {
            previousDirectory.listFiles().orEmpty().forEach { source ->
                runCatching { source.copyTo(File(directory, source.name), overwrite = false) }
            }
        }
    }

    private suspend fun activeProfile() = database.profileDao().getActive()
        ?: error("Aucun profil IPTV actif n’est disponible.")

    suspend fun countries(): EpgCountrySelection = withContext(Dispatchers.IO) {
        val profile = activeProfile()
        val epgChannels = database.channelDao().getForEpg(profile.id)
            .filter { !it.epgChannelId.isNullOrBlank() }
        val options = epgChannels.groupBy { it.countryCode }.map { (countryCode, countryChannels) ->
            val categories = countryChannels.groupBy { it.categoryId }.map { (categoryId, categoryChannels) ->
                EpgCategoryOption(
                    countryCode = countryCode,
                    id = categoryId,
                    name = categoryChannels.first().categoryName,
                    epgChannels = categoryChannels.size,
                    channels = categoryChannels,
                )
            }
            EpgCountryOption(countryCode, countryChannels.size, categories)
        }
        val selectedCountries = preferences.getStringSet(countryKey(profile.id), null)?.toSet()
            ?: options.map { it.code }.toSet()
        val allCategoryKeys = options.flatMap { it.categories }.map { it.key }.toSet()
        val selectedCategories = preferences.getStringSet(categoryKey(profile.id), null)?.toSet()
            ?: allCategoryKeys
        EpgCountrySelection(profile.id, options, selectedCountries, selectedCategories)
    }

    /** Reads the local short-EPG database and mirrors it to the indexed runtime cache; no network. */
    suspend fun cachedAvailableChannelKeys(): Set<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val profile = runCatching { activeProfile() }.getOrNull() ?: return@withLock emptySet()
            val file = cacheFile(profile)
            if (!file.exists() || file.length() == 0L) return@withLock emptySet()
            val availableIds = runCatching {
                SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    val ids = availableProgramChannels(db)
                    runCatching { mirrorMissingEpgCache(db, cacheSpec()) }
                    ids
                }
            }.getOrDefault(emptySet())
            database.channelDao().getForEpg(profile.id)
                .asSequence()
                .filter { it.epgChannelId?.trim() in availableIds }
                .map { "${it.profileId}:${it.streamId}" }
                .toSet()
        }
    }

    suspend fun saveFilters(
        selection: EpgCountrySelection,
        selectedCountries: Set<String>,
        selectedCategories: Set<String>,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(
                preferences.edit()
                    .putStringSet(countryKey(selection.profileId), selectedCountries.toSet())
                    .putStringSet(categoryKey(selection.profileId), selectedCategories.toSet())
                    .commit(),
            )
        }
    }

    suspend fun exportDatabase(): ByteArray = withContext(Dispatchers.IO) {
        mutex.withLock {
            val spec = cacheSpec()
            check(spec.file.exists() && spec.file.length() > 0L) {
                "La base EPG est vide. Effectuez d’abord une recherche EPG."
            }
            SQLiteDatabase.openDatabase(
                spec.file.path,
                null,
                SQLiteDatabase.OPEN_READWRITE,
            ).use { db ->
                validatePortableDatabase(db, spec)
                db.execSQL("CREATE TABLE IF NOT EXISTS filter_selection (kind TEXT NOT NULL, value TEXT NOT NULL)")
                db.beginTransaction()
                try {
                    db.execSQL("DELETE FROM filter_selection")
                    val selectedCountries = preferences.getStringSet(countryKey(spec.profile.id), null)
                        ?: spec.allChannels.map { it.countryCode }.toSet()
                    val selectedCategories = preferences.getStringSet(categoryKey(spec.profile.id), null)
                        ?: spec.allChannels.filter { !it.epgChannelId.isNullOrBlank() }
                            .map(::categorySelectionKey).toSet()
                    selectedCountries.forEach { country ->
                        db.execSQL("INSERT INTO filter_selection VALUES(?,?)", arrayOf("country", country))
                    }
                    selectedCategories.forEach { category ->
                        db.execSQL("INSERT INTO filter_selection VALUES(?,?)", arrayOf("category", category))
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
            }
            ByteArrayOutputStream().use { output ->
                GZIPOutputStream(output).use { gzip -> spec.file.inputStream().use { it.copyTo(gzip) } }
                output.toByteArray()
            }
        }
    }

    suspend fun importDatabase(compressed: ByteArray) = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(compressed.isNotEmpty()) { "La base EPG GitHub est vide." }
            val spec = cacheSpec(requireSelection = false)
            val temporary = File(directory, "${spec.file.name}.importing")
            try {
                GZIPInputStream(ByteArrayInputStream(compressed)).use { gzip ->
                    temporary.outputStream().use { output -> gzip.copyTo(output) }
                }
                val importedSelection = SQLiteDatabase.openDatabase(
                    temporary.path,
                    null,
                    SQLiteDatabase.OPEN_READONLY,
                ).use { db ->
                    validatePortableDatabase(db, spec)
                    readFilterSelection(db)
                }
                listOf(
                    File("${spec.file.path}-wal"),
                    File("${spec.file.path}-shm"),
                    File("${spec.file.path}-journal"),
                ).forEach { sidecar -> if (sidecar.exists()) sidecar.delete() }
                Files.move(
                    temporary.toPath(),
                    spec.file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
                importedSelection?.let { (countries, categories) ->
                    val localCountries = spec.allChannels.map { it.countryCode }.toSet()
                    val localCategories = spec.allChannels.filter { !it.epgChannelId.isNullOrBlank() }
                        .map(::categorySelectionKey).toSet()
                    check(preferences.edit()
                        .putStringSet(countryKey(spec.profile.id), countries.intersect(localCountries))
                        .putStringSet(categoryKey(spec.profile.id), categories.intersect(localCategories))
                        .commit()) { "Impossible de restaurer la sélection EPG." }
                }
                repository.clearEpgCache(spec.profile.id)
                SQLiteDatabase.openDatabase(
                    spec.file.path,
                    null,
                    SQLiteDatabase.OPEN_READONLY,
                ).use { db -> mirrorMissingEpgCache(db, spec) }
            } finally {
                if (temporary.exists()) temporary.delete()
            }
        }
    }

    suspend fun search(
        query: String,
        refresh: Boolean,
        priorityCategoryId: String? = null,
        progress: (String) -> Unit,
    ): EpgSearchPage = withCache(refresh, progress, priorityCategoryId = priorityCategoryId) { db, spec, synced ->
        progress("Recherche dans les programmes…")
        val now = Instant.now().epochSecond
        val words = EpgSearch.words(query).distinct()
        require(words.isNotEmpty()) { "Saisissez au moins un mot." }
        val sql = "SELECT p.id,p.channel,p.title,p.description,p.start,p.stop " +
            "FROM programmes p WHERE p.stop > ?" +
            words.joinToString("") { " AND instr(p.words, ?) > 0" } +
            " ORDER BY p.start,p.title,p.channel"
        val args = (listOf(now.toString()) + words.map { " $it " }).toTypedArray()
        val results = queryPrograms(db, sql, args, spec.channelsByEpg)
        EpgSearchPage(
            orderByProvider(results, spec.allChannels),
            synced,
            cacheCoverage(db),
            spec.file.length(),
        )
    }

    suspend fun guideCountries(
        refresh: Boolean,
        progress: (String) -> Unit,
    ): EpgCountrySelection = withCache(refresh, progress) { db, _, _ ->
        countries().withAvailablePrograms(availableProgramChannels(db))
    }

    private fun availableProgramChannels(db: SQLiteDatabase): Set<String> = db.rawQuery(
        // La présence d'un EPG doit rester visible même si tous ses
        // programmes sont déjà terminés. La date sert à afficher le
        // programme courant, pas à déterminer si la chaîne possède un EPG.
        "SELECT DISTINCT channel FROM programmes WHERE stop > start",
        null,
    ).use { cursor ->
        buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
    }

    suspend fun guide(
        channel: SavedChannel,
        refresh: Boolean,
        progress: (String) -> Unit,
    ): EpgGuidePage = withCache(refresh, progress, channel.epgChannelId?.trim()) { db, spec, synced ->
        val epgId = channel.epgChannelId?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: error("Cette chaîne ne possède pas d’identifiant EPG.")
        check(spec.channelsByEpg[epgId].orEmpty().any { candidate ->
            candidate.profileId == channel.profileId && candidate.streamId == channel.streamId
        }) {
            "Cette chaîne est exclue par la sélection pays/catégories."
        }
        progress("Lecture du guide de la chaîne…")
        val now = Instant.now().epochSecond
        val programs = queryPrograms(
            db,
            "SELECT id,channel,title,description,start,stop FROM programmes " +
                "WHERE channel = ? AND stop > ? ORDER BY start LIMIT 4",
            arrayOf(epgId, now.toString()),
            spec.channelsByEpg,
        )
        EpgGuidePage(channel, programs, synced, cacheCoverage(db), spec.file.length())
    }

    private suspend fun <T> withCache(
        refresh: Boolean,
        progress: (String) -> Unit,
        onlyEpgId: String? = null,
        priorityCategoryId: String? = null,
        block: suspend (SQLiteDatabase, CacheSpec, Long) -> T,
    ): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            val spec = cacheSpec()
            SQLiteDatabase.openOrCreateDatabase(spec.file, null).use { db ->
                createSchema(db)
                val syncSpec = if (onlyEpgId == null) spec else spec.copy(
                    channelsByEpg = spec.channelsByEpg.filterKeys { it == onlyEpgId },
                )
                val synced = ensureCache(db, syncSpec, progress, priorityCategoryId)
                mirrorMissingEpgCache(db, spec)
                block(db, spec, synced)
            }
        }
    }

    private suspend fun cacheSpec(requireSelection: Boolean = true): CacheSpec {
        val profile = activeProfile()
        val allChannels = database.channelDao().getForEpg(profile.id)
        val selectedCountries = preferences.getStringSet(countryKey(profile.id), null)
        val selectedCategories = preferences.getStringSet(categoryKey(profile.id), null)
        val selectedChannels = allChannels.filter { channel ->
            !channel.epgChannelId?.trim().isNullOrEmpty() &&
                (selectedCountries == null || channel.countryCode in selectedCountries) &&
                (selectedCategories == null || categorySelectionKey(channel) in selectedCategories)
        }
        val channelsByEpg = selectedChannels.groupBy { it.epgChannelId!!.trim() }
        check(!requireSelection || channelsByEpg.isNotEmpty()) {
            "Sélectionnez au moins un pays et une catégorie contenant des chaînes EPG."
        }
        val scope = sha256(channelsByEpg.keys.sorted().joinToString("\u0000"))
        return CacheSpec(
            profile = profile,
            allChannels = allChannels,
            channelsByEpg = channelsByEpg,
            scope = scope,
            file = cacheFile(profile),
        )
    }

    private fun createSchema(db: SQLiteDatabase) {
        if (db.version != CACHE_SCHEMA_VERSION) {
            db.beginTransaction()
            try {
                db.execSQL("DROP TABLE IF EXISTS programmes")
                db.execSQL("DROP TABLE IF EXISTS metadata")
                db.execSQL("DROP TABLE IF EXISTS country_scope")
                db.execSQL("DROP TABLE IF EXISTS channel_sync")
                db.execSQL("DROP TABLE IF EXISTS channel_attempt")
                db.version = CACHE_SCHEMA_VERSION
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            db.execSQL("VACUUM")
        }
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS programmes (" +
                "id INTEGER PRIMARY KEY, channel TEXT, title TEXT, description TEXT, " +
                "words TEXT, start INTEGER, stop INTEGER)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS programmes_time ON programmes(stop, start)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS programmes_channel_time " +
                "ON programmes(channel, start, stop)",
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS metadata (synced INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS country_scope (value TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS channel_sync (channel TEXT PRIMARY KEY, synced INTEGER)")
        // Additive table: older portable caches remain compatible.
        db.execSQL("CREATE TABLE IF NOT EXISTS channel_attempt (channel TEXT PRIMARY KEY, attempted INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS filter_selection (kind TEXT NOT NULL, value TEXT NOT NULL)")
    }

    private fun readFilterSelection(db: SQLiteDatabase): Pair<Set<String>, Set<String>>? {
        val exists = db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type='table' AND name='filter_selection'",
            null,
        ).use { it.moveToFirst() }
        if (!exists) return null // Older exports contain programmes only.
        val countries = mutableSetOf<String>()
        val categories = mutableSetOf<String>()
        db.rawQuery("SELECT kind,value FROM filter_selection", null).use { cursor ->
            while (cursor.moveToNext()) {
                when (cursor.getString(0)) {
                    "country" -> countries += cursor.getString(1)
                    "category" -> categories += cursor.getString(1)
                }
            }
        }
        return countries to categories
    }

    private suspend fun ensureCache(
        db: SQLiteDatabase,
        spec: CacheSpec,
        progress: (String) -> Unit,
        priorityCategoryId: String?,
    ): Long {
        val oldScope = db.rawQuery("SELECT value FROM country_scope", null).use {
            if (it.moveToFirst()) it.getString(0) else null
        }
        val scopeChanged = oldScope != spec.scope
        var synced = db.rawQuery("SELECT synced FROM metadata", null).use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }
        val syncedChannels = db.rawQuery("SELECT channel,synced FROM channel_sync", null).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) put(cursor.getString(0), cursor.getLong(1))
            }
        }
        val attemptedChannels = db.rawQuery("SELECT channel,attempted FROM channel_attempt", null).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) put(cursor.getString(0), cursor.getLong(1))
            }
        }
        val staleBefore = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(6)
        val available = availableProgramChannels(db)
        // Even an explicit refresh respects the provider cooldown.
        val requests = spec.channelsByEpg.entries.mapNotNull { (epgId, variants) ->
            val lastChecked = maxOf(syncedChannels[epgId] ?: 0L, attemptedChannels[epgId] ?: 0L)
            if (lastChecked > staleBefore) return@mapNotNull null
            EpgRequest(
                epgId = epgId,
                variants = variants.distinctBy(SavedChannel::streamId)
                    .sortedByDescending { categorySelectionKey(it) == priorityCategoryId },
            )
        }.sortedByDescending { request ->
            priorityCategoryId != null && request.variants.any { categorySelectionKey(it) == priorityCategoryId }
        }
        if (requests.isEmpty()) {
            if (scopeChanged) {
                db.execSQL("DELETE FROM country_scope")
                db.execSQL("INSERT INTO country_scope VALUES(?)", arrayOf(spec.scope))
            }
            return spec.channelsByEpg.keys.mapNotNull(syncedChannels::get).minOrNull() ?: synced
        }
        val loader = repository.shortEpgLoader(spec.profile)
        val totalIdentifiers = requests.size
        progress("EPG court · 0/$totalIdentifiers identifiants…")
        var downloadedAnyGuide = false
        var completedIdentifiers = 0
        requests.chunked(SHORT_EPG_PARALLEL_REQUESTS).forEachIndexed { batchIndex, batch ->
            if (batchIndex > 0) delay(SHORT_EPG_REQUEST_PAUSE_MS)
            val attemptedAt = System.currentTimeMillis()
            batch.forEach { request ->
                db.execSQL(
                    "INSERT OR REPLACE INTO channel_attempt(channel,attempted) VALUES(?,?)",
                    arrayOf<Any>(request.epgId, attemptedAt),
                )
            }
            val guides = coroutineScope {
                batch.map { request ->
                    async {
                        loadShortGuide(request.epgId, request.variants, loader)
                    }
                }.awaitAll().filterNotNull()
            }
            if (guides.isNotEmpty()) {
                downloadedAnyGuide = true
                synced = saveGuides(db, spec, guides)
            }
            completedIdentifiers += batch.size
            progress("EPG court · $completedIdentifiers/$totalIdentifiers identifiants…")
        }
        if (!downloadedAnyGuide && spec.channelsByEpg.keys.none { it in available }) {
            throw java.io.IOException("EPG requests failed")
        }
        return if (downloadedAnyGuide) synced
        else spec.channelsByEpg.keys.mapNotNull(syncedChannels::get).minOrNull() ?: synced
    }

    private suspend fun saveGuides(
        db: SQLiteDatabase,
        spec: CacheSpec,
        guides: List<ShortChannelGuide>,
    ): Long {
        val downloadedAt = System.currentTimeMillis()
        db.beginTransaction()
        try {
            db.compileStatement(
                "INSERT INTO programmes(channel,title,description,words,start,stop) " +
                    "VALUES(?,?,?,?,?,?)",
            ).use { insert ->
                guides.forEach { guide ->
                    db.execSQL("DELETE FROM programmes WHERE channel = ?", arrayOf(guide.channel))
                    guide.programmes.forEach { program ->
                        insert.bindString(1, program.channel)
                        insert.bindString(2, program.title)
                        insert.bindString(3, program.description)
                        insert.bindString(
                            4,
                            EpgSearch.searchable("${program.title} ${program.description}"),
                        )
                        insert.bindLong(5, program.start)
                        insert.bindLong(6, program.stop)
                        insert.executeInsert()
                    }
                    db.execSQL(
                        "INSERT OR REPLACE INTO channel_sync(channel,synced) VALUES(?,?)",
                        arrayOf<Any>(guide.channel, downloadedAt),
                    )
                }
            }
            db.execSQL("DELETE FROM metadata")
            db.execSQL("INSERT INTO metadata VALUES(?)", arrayOf(downloadedAt))
            db.execSQL("DELETE FROM country_scope")
            db.execSQL("INSERT INTO country_scope VALUES(?)", arrayOf(spec.scope))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        repository.saveDownloadedEpg(
            profileId = spec.profile.id,
            guides = guides.associate { guide ->
                guide.channel to (
                    guide.programmes.map { program ->
                        EpgProgram(
                            title = program.title,
                            description = program.description,
                            timeRange = "${SHORT_TIME_FORMATTER.format(Instant.ofEpochSecond(program.start))}–" +
                                SHORT_TIME_FORMATTER.format(Instant.ofEpochSecond(program.stop)),
                            startEpochSeconds = program.start,
                            stopEpochSeconds = program.stop,
                        )
                    } to spec.channelsByEpg[guide.channel]?.firstOrNull()?.streamId
                )
            },
        )
        return downloadedAt
    }

    private fun cachedShortGuide(db: SQLiteDatabase, epgId: String): ShortChannelGuide? {
        val programmes = db.rawQuery(
            "SELECT title,description,start,stop FROM programmes " +
                "WHERE channel=? AND stop>? ORDER BY start LIMIT 4",
            arrayOf(epgId, Instant.now().epochSecond.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(ShortProgramme(
                    epgId, cursor.getString(0), cursor.getString(1), cursor.getLong(2), cursor.getLong(3),
                ))
            }
        }
        return programmes.takeIf { it.isNotEmpty() }?.let { ShortChannelGuide(epgId, it) }
    }

    private suspend fun mirrorMissingEpgCache(db: SQLiteDatabase, spec: CacheSpec) {
        val missing = availableProgramChannels(db) - repository.cachedEpgIds(spec.profile.id)
        if (missing.isEmpty()) return
        val programmes = linkedMapOf<String, MutableList<EpgProgram>>()
        db.rawQuery(
            "SELECT channel,title,description,start,stop FROM programmes ORDER BY channel,start",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val epgId = cursor.getString(0)
                if (epgId !in missing) continue
                val start = cursor.getLong(3)
                val stop = cursor.getLong(4)
                programmes.getOrPut(epgId) { mutableListOf() } += EpgProgram(
                    title = cursor.getString(1),
                    description = cursor.getString(2),
                    timeRange = "${SHORT_TIME_FORMATTER.format(Instant.ofEpochSecond(start))}–" +
                        SHORT_TIME_FORMATTER.format(Instant.ofEpochSecond(stop)),
                    startEpochSeconds = start,
                    stopEpochSeconds = stop,
                )
            }
        }
        repository.saveDownloadedEpg(
            profileId = spec.profile.id,
            guides = programmes.mapValues { (epgId, entries) ->
                entries.toList() to spec.channelsByEpg[epgId]?.firstOrNull()?.streamId
            },
        )
    }

    private suspend fun loadShortGuide(
        epgId: String,
        variants: List<SavedChannel>,
        loader: suspend (SavedChannel) -> List<EpgProgram>,
    ): ShortChannelGuide? {
        var failed = false
        for ((index, channel) in variants.take(MAX_EPG_ID_VARIANTS).withIndex()) {
            if (index > 0) delay(SHORT_EPG_REQUEST_PAUSE_MS)
            try {
                val now = Instant.now().epochSecond
                val programmes = loader(channel).mapNotNull { program ->
                    val start = program.startEpochSeconds
                    val stop = program.stopEpochSeconds
                    if (start == null || stop == null || stop <= start || stop <= now) {
                        null
                    } else {
                        ShortProgramme(epgId, program.title, program.description, start, stop)
                    }
                }.sortedBy { it.start }.take(SHORT_EPG_PROGRAM_LIMIT)
                if (programmes.isNotEmpty()) return ShortChannelGuide(epgId, programmes)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
            }
        }
        // A network failure must not erase a working cached guide or mark it as freshly synced.
        return if (failed) null else ShortChannelGuide(epgId, emptyList())
    }

    private suspend fun queryPrograms(
        db: SQLiteDatabase,
        sql: String,
        args: Array<String>,
        channelsByEpg: Map<String, List<SavedChannel>>,
    ): List<EpgSearchResult> = db.rawQuery(sql, args).use { cursor ->
        val rows = buildList {
            while (cursor.moveToNext()) {
                coroutineContext.ensureActive()
                val variants = channelsByEpg[cursor.getString(1)].orEmpty()
                if (variants.isNotEmpty()) {
                    add(
                        EpgSearchResult(
                            id = cursor.getLong(0),
                            title = cursor.getString(2),
                            description = cursor.getString(3),
                            start = cursor.getLong(4),
                            stop = cursor.getLong(5),
                            channels = variants,
                        ),
                    )
                }
            }
        }
        rows
    }

    private fun orderByProvider(
        results: List<EpgSearchResult>,
        allChannels: List<SavedChannel>,
    ): List<EpgSearchResult> {
        val ranks = allChannels.withIndex().associate { it.value.streamId to it.index }
        return results.sortedWith(
            compareBy<EpgSearchResult> { it.start }.thenBy { result ->
                result.channels.minOfOrNull { ranks[it.streamId] ?: Int.MAX_VALUE }
                    ?: Int.MAX_VALUE
            },
        )
    }

    private fun cacheCoverage(db: SQLiteDatabase): String {
        val format = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(EpgSearch.tunis)
        return db.rawQuery("SELECT MIN(start),MAX(stop) FROM programmes", null).use {
            if (it.moveToFirst() && !it.isNull(0)) {
                "Guide : ${format.format(Instant.ofEpochSecond(it.getLong(0)))} → " +
                    format.format(Instant.ofEpochSecond(it.getLong(1)))
            } else {
                "Guide vide"
            }
        }
    }

    private fun validatePortableDatabase(db: SQLiteDatabase, spec: CacheSpec) {
        check(db.version == CACHE_SCHEMA_VERSION) {
            "Version de base EPG incompatible (${db.version})."
        }
        val tables = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table'",
            null,
        ).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        check(REQUIRED_CACHE_TABLES.all(tables::contains)) {
            "Le fichier GitHub n’est pas une base EPG MyIPTV valide."
        }
        val maximumPrograms = db.rawQuery(
            "SELECT MAX(program_count) FROM (" +
                "SELECT COUNT(*) AS program_count FROM programmes GROUP BY channel)",
            null,
        ).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getInt(0) else 0
        }
        check(maximumPrograms <= SHORT_EPG_PROGRAM_LIMIT) {
            "La base GitHub ne respecte pas la limite de 4 programmes par chaîne."
        }
        val importedEpgIds = db.rawQuery("SELECT channel FROM channel_sync", null).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        check(importedEpgIds.isNotEmpty()) { "La base EPG GitHub ne contient aucune chaîne." }
        val localEpgIds = spec.allChannels.mapNotNull { channel ->
            channel.epgChannelId?.trim()?.takeIf(String::isNotEmpty)
        }.toSet()
        val requiredMatches = minOf(MINIMUM_PROFILE_MATCHES, importedEpgIds.size)
        check(importedEpgIds.count(localEpgIds::contains) >= requiredMatches) {
            "La base EPG GitHub ne correspond pas au profil IPTV actif local."
        }
    }

    private fun countryKey(profileId: Int) = "profile_$profileId"
    private fun categoryKey(profileId: Int) = "profile_${profileId}_categories"
    private fun categorySelectionKey(channel: SavedChannel) =
        "${channel.countryCode}\u0000${channel.categoryId}"

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun cacheFile(profile: XtreamProfile): File {
        val identity = listOf(
            profile.id,
            profile.serverUrl,
            profile.username,
            profile.password,
        ).joinToString("\u0000")
        return File(directory, "${sha256(identity)}.db")
    }

    private data class CacheSpec(
        val profile: XtreamProfile,
        val allChannels: List<SavedChannel>,
        val channelsByEpg: Map<String, List<SavedChannel>>,
        val scope: String,
        val file: File,
    )

    private data class ShortProgramme(
        val channel: String,
        val title: String,
        val description: String,
        val start: Long,
        val stop: Long,
    )

    private data class ShortChannelGuide(
        val channel: String,
        val programmes: List<ShortProgramme>,
    )

    private data class EpgRequest(
        val epgId: String,
        val variants: List<SavedChannel>,
    )

    private companion object {
        const val CACHE_SCHEMA_VERSION = 4
        const val SHORT_EPG_PROGRAM_LIMIT = 4
        const val SHORT_EPG_PARALLEL_REQUESTS = 8
        const val SHORT_EPG_REQUEST_PAUSE_MS = 1_000L
        const val MAX_EPG_ID_VARIANTS = 4
        const val MINIMUM_PROFILE_MATCHES = 10
        val SHORT_TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("HH:mm").withZone(EpgSearch.tunis)
        val REQUIRED_CACHE_TABLES = setOf("programmes", "metadata", "country_scope", "channel_sync")
    }
}
