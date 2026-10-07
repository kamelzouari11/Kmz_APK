package com.kmzapk.mylinks

import android.app.DatePickerDialog
import android.content.Intent
import android.content.DialogInterface
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.text.Html
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.kmzapk.mylinks.data.LinkSuggestionService
import com.kmzapk.mylinks.data.GeminiRateLimitException
import com.kmzapk.mylinks.data.GitHubBackup
import com.kmzapk.mylinks.data.MetaArchiveImporter
import com.kmzapk.mylinks.data.WhatsAppArchiveImporter
import com.kmzapk.mylinks.data.SavedLink
import com.kmzapk.mylinks.data.SavedLinkDatabase
import com.kmzapk.mylinks.databinding.ActivityConsultationBinding
import com.kmzapk.mylinks.ui.SavedLinkAdapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class ConsultationActivity : AppCompatActivity() {
    private lateinit var binding: ActivityConsultationBinding
    private val db by lazy { SavedLinkDatabase.getDatabase(this) }
    private var savedLinks: List<SavedLink> = emptyList()
    private var profileRefreshRunning = false
    private val attemptedProfileIds = mutableSetOf<Long>()
    private val attemptedDateIds = mutableSetOf<Long>()
    private val attemptedAiIds = mutableSetOf<Long>()
    private val archivePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        importMetaArchive(uri)
    }
    private val whatsAppArchivePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        importWhatsAppArchive(uri)
    }

    private fun importMetaArchive(uri: Uri) {
        setImportButtonsEnabled(false)
        binding.uploadGitHubButton.isEnabled = false
        binding.downloadGitHubButton.isEnabled = false
        binding.enrichButton.isEnabled = false
        binding.recheckInstagramButton.isEnabled = false
        binding.findLogosButton.isEnabled = false
        binding.findDatesButton.isEnabled = false
        binding.progressText.visibility = View.VISIBLE
        binding.progressText.text = "Importing archive…"
        lifecycleScope.launch {
            try {
                val result = MetaArchiveImporter.import(this@ConsultationActivity, uri, db.savedLinkDao())
                binding.progressText.text = "${result.source}: ${result.added} added, ${result.updated} updated, " +
                    "${result.skipped} already present; ${result.undated} without individual save date."
                loadLinks()
            } catch (error: Exception) {
                binding.progressText.text = "Import failed: ${error.message ?: "invalid archive"}"
            } finally {
                setImportButtonsEnabled(true)
                binding.uploadGitHubButton.isEnabled = true
                binding.downloadGitHubButton.isEnabled = true
                binding.enrichButton.isEnabled = true
                binding.recheckInstagramButton.isEnabled = true
                binding.findLogosButton.isEnabled = true
                binding.findDatesButton.isEnabled = true
            }
        }
    }

    private fun importWhatsAppArchive(uri: Uri) {
        setImportButtonsEnabled(false)
        binding.progressText.visibility = View.VISIBLE
        binding.progressText.text = "Importing WhatsApp archive…"
        lifecycleScope.launch {
            try {
                val result = WhatsAppArchiveImporter.importIfPresent(
                    this@ConsultationActivity, uri, db.savedLinkDao()
                ) ?: error("No WhatsApp conversation text file found")
                binding.progressText.text = "WhatsApp: ${result.added} added, ${result.updated} updated, " +
                    "${result.skipped} already present."
                loadLinks()
            } catch (error: Exception) {
                binding.progressText.text = "WhatsApp import failed: ${error.message ?: "invalid archive"}"
            } finally {
                setImportButtonsEnabled(true)
            }
        }
    }

    private fun setImportButtonsEnabled(enabled: Boolean) {
        binding.importArchiveButton.isEnabled = enabled
        binding.importWhatsAppArchiveButton.isEnabled = enabled
    }
    private val adapter by lazy {
        SavedLinkAdapter { link ->
            startActivity(Intent(this, LinkDetailActivity::class.java).putExtra("link_id", link.id))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConsultationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.savedLinksRecycler.layoutManager = LinearLayoutManager(this)
        binding.savedLinksRecycler.adapter = adapter
        listOf(binding.searchInput, binding.tagFilterInput, binding.placeFilterInput,
            binding.dateFromInput, binding.dateToInput).forEach { field ->
            field.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = filterLinks()
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        binding.dateFromInput.setOnClickListener { showDatePicker(binding.dateFromInput) }
        binding.dateToInput.setOnClickListener { showDatePicker(binding.dateToInput) }
        binding.categoryFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = filterLinks()
            override fun onNothingSelected(parent: AdapterView<*>?) = filterLinks()
        }
        binding.toggleFiltersButton.setOnClickListener {
            val expanded = binding.filtersPanel.visibility != View.VISIBLE
            binding.filtersPanel.visibility = if (expanded) View.VISIBLE else View.GONE
            binding.toggleFiltersButton.text = if (expanded) "Filters and tools ▴" else "Filters and tools ▾"
        }
        binding.enrichButton.setOnClickListener { completeNextBatch() }
        binding.recheckInstagramButton.setOnClickListener { recheckInstagramLinks() }
        binding.findLogosButton.setOnClickListener { refreshMissingProfiles() }
        binding.findDatesButton.setOnClickListener { findPublicationDates() }
        binding.importArchiveButton.setOnClickListener { archivePicker.launch(arrayOf("application/zip", "application/octet-stream")) }
        binding.importWhatsAppArchiveButton.setOnClickListener {
            whatsAppArchivePicker.launch(arrayOf("application/zip", "application/octet-stream"))
        }
        binding.uploadGitHubButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Upload MyLinks backup")
                .setMessage("The links on this device will replace the MyLinks backup on GitHub. Upload only from the device with the latest data.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Upload") { _, _ -> transferGitHub(upload = true) }
                .show()
        }
        binding.downloadGitHubButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Download MyLinks backup")
                .setMessage("The GitHub backup will replace all links on this device. Changes made here since the last upload will be lost.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Download") { _, _ -> transferGitHub(upload = false) }
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        loadLinks()
    }

    private fun loadLinks() {
        lifecycleScope.launch {
            savedLinks = db.savedLinkDao().getAll()
            val logoCount = savedLinks.count { !it.profileImageUrl.isNullOrBlank() }
            binding.collectionInfoText.text = "${savedLinks.size} links · $logoCount image URLs found"
            val selected = binding.categoryFilter.selectedItem?.toString()
            val categories = listOf(ALL_CATEGORIES) + savedLinks.map { it.category }
                .distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
            binding.categoryFilter.adapter = ArrayAdapter(
                this@ConsultationActivity, android.R.layout.simple_spinner_dropdown_item, categories
            )
            categories.indexOf(selected).takeIf { it >= 0 }?.let { binding.categoryFilter.setSelection(it) }
            filterLinks()
            // Enrichment is requested in explicit batches to avoid hundreds of network requests.
        }
    }

    private fun transferGitHub(upload: Boolean) {
        binding.uploadGitHubButton.isEnabled = false
        binding.downloadGitHubButton.isEnabled = false
        setImportButtonsEnabled(false)
        binding.enrichButton.isEnabled = false
        binding.recheckInstagramButton.isEnabled = false
        binding.findLogosButton.isEnabled = false
        binding.findDatesButton.isEnabled = false
        binding.progressText.visibility = View.VISIBLE
        binding.progressText.text = if (upload) "Uploading MyLinks backup…" else "Downloading MyLinks backup…"
        lifecycleScope.launch {
            try {
                val count = if (upload) GitHubBackup.upload(db) else GitHubBackup.download(db)
                val successMessage = if (upload) "$count links uploaded to GitHub."
                    else "$count links restored from GitHub."
                binding.progressText.text = successMessage
                Toast.makeText(this@ConsultationActivity, successMessage, Toast.LENGTH_LONG).show()
                if (!upload) {
                    attemptedProfileIds.clear()
                    attemptedDateIds.clear()
                    attemptedAiIds.clear()
                    getSharedPreferences("instagram_ai_recheck_v1", MODE_PRIVATE).edit().clear().apply()
                    loadLinks()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                binding.progressText.text = "GitHub backup failed: ${error.message ?: "unknown error"}"
            } finally {
                binding.uploadGitHubButton.isEnabled = true
                binding.downloadGitHubButton.isEnabled = true
                setImportButtonsEnabled(true)
                binding.enrichButton.isEnabled = true
                binding.recheckInstagramButton.isEnabled = true
                binding.findLogosButton.isEnabled = true
                binding.findDatesButton.isEnabled = true
            }
        }
    }

    private fun refreshMissingProfiles() {
        if (profileRefreshRunning) return
        val candidates = savedLinks.filter { link ->
            link.id !in attemptedProfileIds &&
                (link.profileImageUrl.isNullOrBlank() || imageUrlExpired(link.profileImageUrl))
        }.take(ENRICHMENT_BATCH_SIZE)
        if (candidates.isEmpty()) {
            Toast.makeText(this, "No missing image URLs.", Toast.LENGTH_SHORT).show()
            return
        }
        profileRefreshRunning = true
        binding.findLogosButton.isEnabled = false
        binding.findDatesButton.isEnabled = false
        binding.enrichButton.isEnabled = false
        binding.recheckInstagramButton.isEnabled = false
        setImportButtonsEnabled(false)
        binding.uploadGitHubButton.isEnabled = false
        binding.downloadGitHubButton.isEnabled = false
        binding.progressText.visibility = View.VISIBLE
        lifecycleScope.launch {
            var added = 0
            try {
                candidates.forEachIndexed { index, original ->
                    attemptedProfileIds.add(original.id)
                    binding.progressText.text = "Finding images ${index + 1}/${candidates.size}: ${original.title}"
                    try {
                        val found = LinkSuggestionService.discoverProfile(
                            original.originalUrl, original.profileUrl
                        )
                        val current = db.savedLinkDao().getById(original.id) ?: return@forEachIndexed
                        val updated = current.copy(
                            profileUrl = current.profileUrl.takeUnless { it.isNullOrBlank() }
                                ?: found.profileUrl,
                            profileImageUrl = if (current.profileImageUrl.isNullOrBlank() ||
                                imageUrlExpired(current.profileImageUrl)) {
                                found.imageUrl ?: current.profileImageUrl
                            } else current.profileImageUrl,
                            googleMapsUrl = current.googleMapsUrl.takeUnless { it.isNullOrBlank() }
                                ?: found.googleMapsUrl,
                            address = current.address.takeUnless { it.isNullOrBlank() }
                                ?: found.address
                        )
                        if (updated != current) {
                            db.savedLinkDao().update(updated)
                            if (current.profileImageUrl.isNullOrBlank() && !updated.profileImageUrl.isNullOrBlank()) added++
                            savedLinks = savedLinks.map { if (it.id == updated.id) updated else it }
                            val logos = savedLinks.count { !it.profileImageUrl.isNullOrBlank() }
                            binding.collectionInfoText.text = "${savedLinks.size} links · $logos image URLs found"
                            filterLinks()
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) { /* An inaccessible page remains editable by hand. */ }
                }
                binding.progressText.text = "$added image URLs added from ${candidates.size} links."
            } finally {
                profileRefreshRunning = false
                binding.findLogosButton.isEnabled = true
                binding.findDatesButton.isEnabled = true
                binding.enrichButton.isEnabled = true
                binding.recheckInstagramButton.isEnabled = true
                setImportButtonsEnabled(true)
                binding.uploadGitHubButton.isEnabled = true
                binding.downloadGitHubButton.isEnabled = true
            }
        }
    }

    private fun findPublicationDates() {
        val candidates = savedLinks.filter {
            it.publishedAt == null && it.id !in attemptedDateIds
        }.take(ENRICHMENT_BATCH_SIZE)
        if (candidates.isEmpty()) {
            Toast.makeText(this, "No unchecked publication dates remain in this session.", Toast.LENGTH_SHORT).show()
            return
        }
        binding.findDatesButton.isEnabled = false
        binding.findLogosButton.isEnabled = false
        binding.enrichButton.isEnabled = false
        binding.recheckInstagramButton.isEnabled = false
        setImportButtonsEnabled(false)
        binding.uploadGitHubButton.isEnabled = false
        binding.downloadGitHubButton.isEnabled = false
        binding.progressText.visibility = View.VISIBLE
        lifecycleScope.launch {
            var foundCount = 0
            try {
                candidates.forEachIndexed { index, original ->
                    attemptedDateIds.add(original.id)
                    binding.progressText.text = "Finding dates ${index + 1}/${candidates.size}: ${original.title}"
                    try {
                        val date = LinkSuggestionService.publicationDateFor(original.originalUrl)
                            ?: return@forEachIndexed
                        val current = db.savedLinkDao().getById(original.id) ?: return@forEachIndexed
                        if (current.originalUrl == original.originalUrl && current.publishedAt == null) {
                            db.savedLinkDao().update(current.copy(publishedAt = date))
                            foundCount++
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) { /* The publication page may be inaccessible. */ }
                }
                binding.progressText.text = "$foundCount publication dates found from ${candidates.size} links."
                loadLinks()
            } finally {
                binding.findDatesButton.isEnabled = true
                binding.findLogosButton.isEnabled = true
                binding.enrichButton.isEnabled = true
                binding.recheckInstagramButton.isEnabled = true
                setImportButtonsEnabled(true)
                binding.uploadGitHubButton.isEnabled = true
                binding.downloadGitHubButton.isEnabled = true
            }
        }
    }

    private fun imageUrlExpired(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val decoded = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()
        val expiry = Uri.parse(decoded).getQueryParameter("oe")?.toLongOrNull(16) ?: return false
        return expiry * 1000 <= System.currentTimeMillis() + 60 * 60 * 1000
    }

    private fun completeNextBatch() {
        if (profileRefreshRunning) return
        val key = BuildConfig.GEMINI_API_KEY
        fun needsAi(link: SavedLink) = key.isNotBlank() &&
            (link.aiCheckedAt == null || link.aiCheckStatus == "failed") &&
            link.id !in attemptedAiIds &&
            (link.profileUrl.isNullOrBlank() || link.profileImageUrl.isNullOrBlank() ||
                (link.googleMapsUrl.isNullOrBlank() && link.address.isNullOrBlank()) ||
                categoryNeedsUpdate(link.category, link.source))
        val candidates = savedLinks.filter { link ->
            needsAi(link) ||
                (link.id !in attemptedProfileIds &&
                    (link.profileImageUrl.isNullOrBlank() || imageUrlExpired(link.profileImageUrl))) ||
                (link.id !in attemptedDateIds && link.publishedAt == null)
        }.take(ENRICHMENT_BATCH_SIZE)
        if (candidates.isEmpty()) {
            Toast.makeText(this, "No unchecked details remain in this session.", Toast.LENGTH_SHORT).show()
            return
        }
        profileRefreshRunning = true
        binding.enrichButton.isEnabled = false
        binding.recheckInstagramButton.isEnabled = false
        binding.findLogosButton.isEnabled = false
        binding.findDatesButton.isEnabled = false
        setImportButtonsEnabled(false)
        binding.uploadGitHubButton.isEnabled = false
        binding.downloadGitHubButton.isEnabled = false
        binding.progressText.visibility = View.VISIBLE
        val previousBrightness = window.attributes.screenBrightness
        val currentBrightness = if (previousBrightness >= 0f) previousBrightness else
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 180) / 255f
        window.attributes = window.attributes.apply {
            screenBrightness = currentBrightness.coerceIn(0.1f, 1f)
        }
        binding.root.keepScreenOn = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        lifecycleScope.launch {
            var aiUpdated = 0
            var imagesFound = 0
            var datesFound = 0
            var errors = 0
            var quotaReached = false
            var processed = 0
            try {
                val dao = db.savedLinkDao()
                for ((index, original) in candidates.withIndex()) {
                    binding.progressText.text = "Completing ${index + 1}/${candidates.size}: ${original.title}"
                    var current = dao.getById(original.id)?.takeIf { it.originalUrl == original.originalUrl }
                        ?: continue
                    if (needsAi(current)) {
                        attemptedAiIds.add(current.id)
                        try {
                            if (LinkSuggestionService.isConfirmedNotFound(current.originalUrl)) {
                                current = current.copy(aiCheckedAt = System.currentTimeMillis(),
                                    aiCheckStatus = "unavailable")
                            } else {
                                val suggestion = LinkSuggestionService.suggest(key, current.originalUrl,
                                    current.importContext.orEmpty(), current.profileUrl)
                                val before = current
                                current = current.copy(
                                    title = if ((current.title.isBlank() || current.title == current.originalUrl) && suggestion.title.isNotBlank())
                                        suggestion.title else current.title,
                                    category = if (categoryNeedsUpdate(current.category, current.source) &&
                                        !categoryNeedsUpdate(suggestion.category, suggestion.source) &&
                                        suggestion.category.isNotBlank()) suggestion.category else current.category,
                                    profileUrl = current.profileUrl.takeUnless { it.isNullOrBlank() } ?: suggestion.profileUrl,
                                    profileImageUrl = current.profileImageUrl.takeUnless { it.isNullOrBlank() } ?: suggestion.profileImageUrl,
                                    googleMapsUrl = current.googleMapsUrl.takeUnless { it.isNullOrBlank() } ?: suggestion.googleMapsUrl,
                                    address = current.address.takeUnless { it.isNullOrBlank() }
                                        ?: suggestion.address.takeIf { it.isNotBlank() },
                                    publishedAt = current.publishedAt ?: suggestion.publishedAt,
                                    aiCheckedAt = System.currentTimeMillis(),
                                    aiCheckStatus = "completed"
                                )
                                if (current != before.copy(aiCheckedAt = current.aiCheckedAt,
                                        aiCheckStatus = current.aiCheckStatus)) aiUpdated++
                                if (before.profileImageUrl.isNullOrBlank() && !current.profileImageUrl.isNullOrBlank()) imagesFound++
                                if (before.publishedAt == null && current.publishedAt != null) datesFound++
                            }
                            dao.update(current)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: GeminiRateLimitException) {
                            attemptedAiIds.remove(current.id)
                            quotaReached = true
                            break
                        } catch (_: Exception) { errors++ }
                    }
                    if (current.id !in attemptedProfileIds &&
                        (current.profileImageUrl.isNullOrBlank() || imageUrlExpired(current.profileImageUrl))) {
                        attemptedProfileIds.add(current.id)
                        try {
                            val found = LinkSuggestionService.discoverProfile(current.originalUrl, current.profileUrl)
                            val before = current
                            current = current.copy(
                                profileUrl = current.profileUrl.takeUnless { it.isNullOrBlank() } ?: found.profileUrl,
                                profileImageUrl = if (current.profileImageUrl.isNullOrBlank() ||
                                    imageUrlExpired(current.profileImageUrl)) found.imageUrl ?: current.profileImageUrl
                                    else current.profileImageUrl,
                                googleMapsUrl = current.googleMapsUrl.takeUnless { it.isNullOrBlank() } ?: found.googleMapsUrl,
                                address = current.address.takeUnless { it.isNullOrBlank() } ?: found.address
                            )
                            if (current != before) dao.update(current)
                            if (before.profileImageUrl.isNullOrBlank() && !current.profileImageUrl.isNullOrBlank()) imagesFound++
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) { errors++ }
                    }
                    if (current.publishedAt == null && current.id !in attemptedDateIds) {
                        attemptedDateIds.add(current.id)
                        try {
                            val date = LinkSuggestionService.publicationDateFor(current.originalUrl)
                            if (date != null) {
                                current = current.copy(publishedAt = date)
                                dao.update(current)
                                datesFound++
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) { errors++ }
                    }
                    processed++
                }
                binding.progressText.text = "$processed/${candidates.size} links processed: $aiUpdated enriched, " +
                    "$imagesFound image URLs, $datesFound publication dates; $errors errors." +
                    (if (key.isBlank()) " AI skipped: configure GEMINI_API_KEY." else "") +
                    (if (quotaReached) " Gemini quota reached; batch stopped. This link remains unchecked by AI." else "")
                loadLinks()
            } finally {
                profileRefreshRunning = false
                binding.root.keepScreenOn = false
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                window.attributes = window.attributes.apply { screenBrightness = previousBrightness }
                binding.enrichButton.isEnabled = true
                binding.recheckInstagramButton.isEnabled = true
                binding.findLogosButton.isEnabled = true
                binding.findDatesButton.isEnabled = true
                setImportButtonsEnabled(true)
                binding.uploadGitHubButton.isEnabled = true
                binding.downloadGitHubButton.isEnabled = true
            }
        }
    }

    private fun recheckInstagramLinks() {
        if (profileRefreshRunning) return
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isBlank()) {
            Toast.makeText(this, "Set GEMINI_API_KEY in local.properties.", Toast.LENGTH_LONG).show()
            return
        }
        val preferences = getSharedPreferences("instagram_ai_recheck_v1", MODE_PRIVATE)
        val processedIds = preferences.getStringSet("processed_ids", emptySet()).orEmpty().toMutableSet()
        val candidates = savedLinks.filter { link ->
            val host = Uri.parse(link.originalUrl).host?.lowercase(Locale.ROOT).orEmpty()
            (host == "instagram.com" || host.endsWith(".instagram.com")) &&
                link.id.toString() !in processedIds
        }
        if (candidates.isEmpty()) {
            Toast.makeText(this, "All Instagram links have been rechecked.", Toast.LENGTH_SHORT).show()
            return
        }
        profileRefreshRunning = true
        binding.enrichButton.isEnabled = false
        binding.recheckInstagramButton.isEnabled = false
        binding.findLogosButton.isEnabled = false
        binding.findDatesButton.isEnabled = false
        setImportButtonsEnabled(false)
        binding.uploadGitHubButton.isEnabled = false
        binding.downloadGitHubButton.isEnabled = false
        binding.progressText.visibility = View.VISIBLE
        binding.root.keepScreenOn = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        lifecycleScope.launch {
            var processed = 0
            var updatedCount = 0
            var profileCount = 0
            var imageCount = 0
            var failedCount = 0
            var quotaReached = false
            try {
                val dao = db.savedLinkDao()
                for ((index, original) in candidates.withIndex()) {
                    binding.progressText.text = "Rechecking Instagram ${index + 1}/${candidates.size}: ${original.title.ifBlank { original.originalUrl }}"
                    val current = dao.getById(original.id)
                        ?.takeIf { it.originalUrl == original.originalUrl } ?: continue
                    try {
                        val suggestion = LinkSuggestionService.suggest(key, current.originalUrl,
                            current.importContext.orEmpty(), current.profileUrl)
                        val updated = current.copy(
                            title = if ((current.title.isBlank() || current.title == current.originalUrl) &&
                                suggestion.title.isNotBlank()) suggestion.title else current.title,
                            category = if ((current.category.isBlank() ||
                                categoryNeedsUpdate(current.category, current.source)) &&
                                suggestion.category.isNotBlank() &&
                                !categoryNeedsUpdate(suggestion.category, current.source)) {
                                suggestion.category
                            } else current.category,
                            profileUrl = current.profileUrl.takeUnless { it.isNullOrBlank() }
                                ?: suggestion.profileUrl,
                            profileImageUrl = if (current.profileUrl.isNullOrBlank() ||
                                current.profileImageUrl.isNullOrBlank() || imageUrlExpired(current.profileImageUrl)) {
                                suggestion.profileImageUrl ?: current.profileImageUrl
                            } else current.profileImageUrl,
                            googleMapsUrl = current.googleMapsUrl.takeUnless { it.isNullOrBlank() }
                                ?: suggestion.googleMapsUrl,
                            address = current.address.takeUnless { it.isNullOrBlank() }
                                ?: suggestion.address.takeIf { it.isNotBlank() },
                            publishedAt = current.publishedAt ?: suggestion.publishedAt
                        )
                        dao.update(updated.copy(
                            aiCheckedAt = System.currentTimeMillis(),
                            aiCheckStatus = if (updated == current) "empty" else "completed"
                        ))
                        if (updated != current) updatedCount++
                        if (current.profileUrl.isNullOrBlank() && !updated.profileUrl.isNullOrBlank()) profileCount++
                        if (current.profileImageUrl != updated.profileImageUrl &&
                            !updated.profileImageUrl.isNullOrBlank()) imageCount++
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: GeminiRateLimitException) {
                        quotaReached = true
                        break
                    } catch (_: Exception) {
                        failedCount++
                        try {
                            dao.update(current.copy(aiCheckedAt = System.currentTimeMillis(), aiCheckStatus = "failed"))
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) { /* Keep the saved link intact if the write fails. */ }
                    }
                    processedIds.add(current.id.toString())
                    preferences.edit().putStringSet("processed_ids", processedIds.toSet()).apply()
                    processed++
                }
                binding.progressText.text = "$processed/${candidates.size} Instagram links rechecked: " +
                    "$updatedCount updated, $profileCount profiles, $imageCount profile images, $failedCount failed." +
                    (if (quotaReached) " Gemini quota reached; tap again later to continue." else "")
                loadLinks()
            } finally {
                profileRefreshRunning = false
                binding.root.keepScreenOn = false
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                binding.enrichButton.isEnabled = true
                binding.recheckInstagramButton.isEnabled = true
                binding.findLogosButton.isEnabled = true
                binding.findDatesButton.isEnabled = true
                setImportButtonsEnabled(true)
                binding.uploadGitHubButton.isEnabled = true
                binding.downloadGitHubButton.isEnabled = true
            }
        }
    }

    private fun showDatePicker(field: EditText) {
        val selected = runCatching { LocalDate.parse(field.text.toString()) }.getOrNull()
            ?: LocalDate.now()
        DatePickerDialog(this, { _, year, month, day ->
            field.setText(LocalDate.of(year, month + 1, day).toString())
        }, selected.year, selected.monthValue - 1, selected.dayOfMonth).apply {
            setButton(DialogInterface.BUTTON_NEUTRAL, "Clear") { _, _ -> field.setText("") }
        }.show()
    }

    private fun filterLinks() {
        val query = binding.searchInput.text?.toString()?.trim().orEmpty()
        val tag = binding.tagFilterInput.text?.toString()?.trim().orEmpty()
        val place = binding.placeFilterInput.text?.toString()?.trim().orEmpty()
        val category = binding.categoryFilter.selectedItem?.toString() ?: ALL_CATEGORIES
        fun dateMillis(value: String): Long? = runCatching {
            LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
        val from = dateMillis(binding.dateFromInput.text?.toString()?.trim().orEmpty())
        val throughExclusive = runCatching {
            LocalDate.parse(binding.dateToInput.text.toString()).plusDays(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
        val items = savedLinks.filter { link ->
            (category == ALL_CATEGORIES || link.category == category) &&
                (from == null || (link.publishedAt ?: link.createdAt) >= from) &&
                (throughExclusive == null || ((link.publishedAt ?: link.createdAt) > 0 &&
                    (link.publishedAt ?: link.createdAt) < throughExclusive)) &&
                (tag.isBlank() || link.tags.split(',').any { it.trim().contains(tag, ignoreCase = true) }) &&
                (place.isBlank() || link.address.orEmpty().contains(place, ignoreCase = true)) &&
                (query.isBlank() || listOf(link.title, link.category, link.source, link.tags,
                    link.address.orEmpty(), link.originalUrl, link.importCollection.orEmpty()).any { it.contains(query, ignoreCase = true) })
        }
        adapter.submitList(items)
        binding.emptyStateText.text = if (savedLinks.isEmpty()) "No saved links yet." else "No links match these filters."
        binding.emptyStateText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        binding.savedLinksRecycler.visibility = View.VISIBLE
    }

    private fun completeMissingDetails() {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isBlank()) {
            Toast.makeText(this, "Set GEMINI_API_KEY in local.properties.", Toast.LENGTH_LONG).show()
            return
        }
        val candidates = savedLinks.filter {
            it.aiCheckedAt == null &&
                (it.profileUrl.isNullOrBlank() ||
                it.profileImageUrl.isNullOrBlank() ||
                (it.googleMapsUrl.isNullOrBlank() && it.address.isNullOrBlank()) ||
                categoryNeedsUpdate(it.category, it.source))
        }
        if (candidates.isEmpty()) {
            Toast.makeText(this, "No missing details to complete.", Toast.LENGTH_SHORT).show()
            return
        }
        binding.enrichButton.isEnabled = false
        binding.recheckInstagramButton.isEnabled = false
        binding.findLogosButton.isEnabled = false
        binding.findDatesButton.isEnabled = false
        binding.uploadGitHubButton.isEnabled = false
        binding.downloadGitHubButton.isEnabled = false
        setImportButtonsEnabled(false)
        binding.progressText.visibility = View.VISIBLE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        lifecycleScope.launch {
            var updatedCount = 0
            var failedCount = 0
            var unavailableCount = 0
            var emptyCount = 0
            var logoAddedCount = 0
            try {
                candidates.take(ENRICHMENT_BATCH_SIZE).forEachIndexed { index, original ->
                    binding.progressText.text = "Checking ${index + 1}/${minOf(candidates.size, ENRICHMENT_BATCH_SIZE)}: ${original.title}"
                    try {
                        if (LinkSuggestionService.isConfirmedNotFound(original.originalUrl)) {
                            db.savedLinkDao().getById(original.id)?.let { current ->
                                if (current.originalUrl == original.originalUrl) {
                                    db.savedLinkDao().update(current.copy(
                                        aiCheckedAt = System.currentTimeMillis(),
                                        aiCheckStatus = "unavailable"))
                                    unavailableCount++
                                }
                            }
                            return@forEachIndexed
                        }
                        val suggestion = LinkSuggestionService.suggest(key, original.originalUrl,
                            original.importContext.orEmpty(), original.profileUrl)
                        val current = db.savedLinkDao().getById(original.id) ?: return@forEachIndexed
                        if (current.originalUrl != original.originalUrl) return@forEachIndexed
                        val updated = current.copy(
                            title = if ((current.title.isBlank() || current.title == current.originalUrl) && suggestion.title.isNotBlank())
                                suggestion.title else current.title,
                            category = if (categoryNeedsUpdate(current.category, current.source) &&
                                !categoryNeedsUpdate(suggestion.category, suggestion.source) &&
                                suggestion.category.isNotBlank()) suggestion.category else current.category,
                            profileUrl = current.profileUrl.takeUnless { it.isNullOrBlank() } ?: suggestion.profileUrl,
                            profileImageUrl = current.profileImageUrl.takeUnless { it.isNullOrBlank() } ?: suggestion.profileImageUrl,
                            googleMapsUrl = current.googleMapsUrl.takeUnless { it.isNullOrBlank() } ?: suggestion.googleMapsUrl,
                            address = current.address.takeUnless { it.isNullOrBlank() }
                                ?: suggestion.address.takeIf { it.isNotBlank() },
                            publishedAt = suggestion.publishedAt ?: current.publishedAt
                        )
                        db.savedLinkDao().update(updated.copy(
                            aiCheckedAt = System.currentTimeMillis(),
                            aiCheckStatus = if (updated == current) "empty" else "completed"))
                        if (current.profileImageUrl.isNullOrBlank() && !updated.profileImageUrl.isNullOrBlank()) {
                            logoAddedCount++
                        }
                        if (updated != current) updatedCount++
                        else emptyCount++
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: GeminiRateLimitException) {
                        binding.progressText.text = "Gemini quota reached. This link remains unchecked by AI. Try again after the quota resets."
                        return@launch
                    } catch (_: Exception) {
                        failedCount++
                        // Record the attempt even when the service fails, so the next batch moves on.
                        try {
                            db.savedLinkDao().getById(original.id)?.let { current ->
                                if (current.originalUrl == original.originalUrl && current.aiCheckedAt == null) {
                                    db.savedLinkDao().update(current.copy(
                                        aiCheckedAt = System.currentTimeMillis(),
                                        aiCheckStatus = "failed"))
                                }
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) { /* A failed database write can be retried later. */ }
                    }
                }
                binding.progressText.text = "$updatedCount updated, $logoAddedCount image URLs added, " +
                    "$emptyCount unchanged, " +
                    "$unavailableCount unavailable (HTTP 404), $failedCount failed. " +
                    "${(candidates.size - ENRICHMENT_BATCH_SIZE).coerceAtLeast(0)} remaining."
                loadLinks()
            } finally {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                binding.enrichButton.isEnabled = true
                binding.recheckInstagramButton.isEnabled = true
                binding.findLogosButton.isEnabled = true
                binding.findDatesButton.isEnabled = true
                binding.uploadGitHubButton.isEnabled = true
                binding.downloadGitHubButton.isEnabled = true
                setImportButtonsEnabled(true)
            }
        }
    }

    private fun categoryNeedsUpdate(category: String, source: String): Boolean =
        category.equals(source, ignoreCase = true) ||
            category.lowercase() in setOf("à classer", "instagram", "facebook", "tiktok", "youtube",
                "general", "suggested: general", "article", "publication")

    companion object {
        private const val ALL_CATEGORIES = "All categories"
        private const val ENRICHMENT_BATCH_SIZE = 200
    }
}
