package com.kmzapk.mylinks

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.database.sqlite.SQLiteConstraintException
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kmzapk.mylinks.data.SavedLink
import com.kmzapk.mylinks.data.SavedLinkDatabase
import com.kmzapk.mylinks.data.LinkSuggestionService
import com.kmzapk.mylinks.data.GeminiRateLimitException
import com.kmzapk.mylinks.databinding.ActivityLinkDetailBinding
import com.kmzapk.mylinks.ui.ProfileImageLoader
import com.kmzapk.mylinks.ui.LinkOpener
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.text.DateFormat
import java.util.Date

class LinkDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLinkDetailBinding
    private val db by lazy { SavedLinkDatabase.getDatabase(this) }
    private var pendingAiCheckAt: Long? = null
    private var pendingPublishedAt: Long? = null
    private var pendingAiStatus: String? = null
    private var analyzedUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLinkDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val linkId = intent.getLongExtra("link_id", -1L)
        if (linkId == -1L) {
            finish()
            return
        }

        lifecycleScope.launch {
            val link = db.savedLinkDao().getById(linkId)
            if (link == null) {
                runOnUiThread { finish() }
                return@launch
            }

            runOnUiThread {
                bindLink(link)
            }
        }

        binding.openButton.setOnClickListener {
            openUrl(binding.urlField.text?.toString())
        }

        binding.profileImage.setOnClickListener { openUrl(binding.profileField.text?.toString()) }
        binding.openProfileButton.setOnClickListener { openUrl(binding.profileField.text?.toString()) }
        binding.urlField.setOnClickListener { openUrl(binding.urlField.text?.toString()) }
        binding.mapsField.setOnClickListener { openUrl(binding.mapsField.text?.toString()) }

        binding.openMapsButton.setOnClickListener {
            openUrl(binding.mapsField.text?.toString())
        }

        binding.saveButton.setOnClickListener {
            saveEditedLink(linkId)
        }
        binding.recheckAiButton.setOnClickListener { recheckWithAi(linkId) }

        binding.deleteButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage("Delete this saved link?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete") { _, _ -> deleteLink(linkId) }
                .show()
        }
    }

    private fun openUrl(value: String?) {
        LinkOpener.open(this, value)
    }

    private fun bindLink(link: SavedLink) {
        binding.titleField.setText(link.title)
        binding.categoryField.setText(link.category)
        binding.sourceField.setText(link.source)
        binding.addressField.setText(link.address ?: "")
        binding.urlField.setText(link.originalUrl)
        binding.mapsField.setText(link.googleMapsUrl ?: "")
        binding.tagsField.setText(link.tags)
        binding.profileField.setText(link.profileUrl ?: "")
        binding.profileImageField.setText(link.profileImageUrl ?: "")
        ProfileImageLoader.load(binding.profileImage, link.profileImageUrl)
        binding.aiStatusText.text = link.aiCheckedAt?.let {
            val status = when (link.aiCheckStatus) {
                "failed" -> "AI attempt failed"
                "unavailable" -> "Link unavailable (HTTP 404)"
                "empty" -> "AI checked: no new details"
                else -> "AI checked"
            }
            "$status: ${DateFormat.getDateTimeInstance().format(Date(it))}"
        } ?: "Not checked by AI"
        binding.dateStatusText.text = buildString {
            if (link.createdAt > 0) append("Saved: ${DateFormat.getDateInstance().format(Date(link.createdAt))}")
            link.publishedAt?.let {
                if (isNotEmpty()) append(" · ")
                append("Published: ${DateFormat.getDateInstance().format(Date(it))}")
            }
        }
    }

    private fun recheckWithAi(linkId: Long) {
        val key = BuildConfig.GEMINI_API_KEY
        val url = binding.urlField.text?.toString()?.trim().orEmpty()
        val uri = Uri.parse(url)
        if (key.isBlank() || uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank()) {
            Toast.makeText(this, "Enter a valid URL and configure GEMINI_API_KEY.", Toast.LENGTH_LONG).show()
            return
        }
        pendingAiCheckAt = null
        pendingPublishedAt = null
        pendingAiStatus = null
        analyzedUrl = null
        binding.recheckAiButton.isEnabled = false
        binding.aiStatusText.text = "Checking with AI…"
        lifecycleScope.launch {
            try {
                val current = db.savedLinkDao().getById(linkId) ?: return@launch
                val suggestion = LinkSuggestionService.suggest(key, url,
                    current.importContext.orEmpty(), binding.profileField.text?.toString()?.trim())
                if (binding.urlField.text?.toString()?.trim() != url) {
                    binding.aiStatusText.text = "URL changed. Run the check again."
                    return@launch
                }
                if (suggestion.title.isNotBlank()) binding.titleField.setText(suggestion.title)
                if (suggestion.category.isNotBlank() &&
                    !suggestion.category.equals("General", ignoreCase = true)) {
                    binding.categoryField.setText(suggestion.category)
                }
                if (suggestion.address.isNotBlank()) binding.addressField.setText(suggestion.address)
                suggestion.googleMapsUrl?.let { binding.mapsField.setText(it) }
                suggestion.profileUrl?.let { binding.profileField.setText(it) }
                suggestion.profileImageUrl?.let { binding.profileImageField.setText(it) }
                pendingAiCheckAt = System.currentTimeMillis()
                pendingPublishedAt = suggestion.publishedAt
                analyzedUrl = url
                val status = if (suggestion.title.isBlank() && suggestion.category.isBlank() &&
                    suggestion.address.isBlank() && suggestion.googleMapsUrl == null &&
                    suggestion.profileUrl == null && suggestion.profileImageUrl == null &&
                    suggestion.publishedAt == null) "empty" else "completed"
                pendingAiStatus = status
                db.savedLinkDao().getById(linkId)?.let { latest ->
                    if (latest.originalUrl == url) {
                        db.savedLinkDao().update(latest.copy(
                            aiCheckedAt = pendingAiCheckAt, aiCheckStatus = status,
                            publishedAt = suggestion.publishedAt ?: latest.publishedAt))
                    }
                }
                binding.aiStatusText.text = "AI check complete. Review the fields, then tap Save."
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (error !is GeminiRateLimitException) try {
                    db.savedLinkDao().getById(linkId)?.let { latest ->
                        if (latest.originalUrl == url) {
                            db.savedLinkDao().update(latest.copy(
                                aiCheckedAt = System.currentTimeMillis(), aiCheckStatus = "failed"))
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A database error should not hide the original AI error.
                }
                binding.aiStatusText.text = "AI check failed: ${error.message ?: "network error"}"
            } finally {
                binding.recheckAiButton.isEnabled = true
            }
        }
    }

    private fun saveEditedLink(linkId: Long) {
        val title = binding.titleField.text?.toString()?.trim().orEmpty()
        val category = binding.categoryField.text?.toString()?.trim().orEmpty()
        val url = binding.urlField.text?.toString()?.trim().orEmpty()
        val source = binding.sourceField.text?.toString()?.trim().orEmpty()
        val address = binding.addressField.text?.toString()?.trim().orEmpty()
        val mapsUrl = binding.mapsField.text?.toString()?.trim().orEmpty()
        val tags = binding.tagsField.text?.toString()?.trim().orEmpty()
        val profileUrl = binding.profileField.text?.toString()?.trim().orEmpty()
        val profileImageUrl = binding.profileImageField.text?.toString()?.trim().orEmpty()

        if (url.isEmpty()) {
            Toast.makeText(this, "URL is required.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val dao = db.savedLinkDao()
            val existing = dao.getById(linkId) ?: return@launch
            if (url != existing.originalUrl && dao.getByUrl(url) != null) {
                binding.urlField.error = "This link is already saved."
                return@launch
            }
            val updated = existing.copy(
                title = title,
                category = category,
                originalUrl = url,
                source = source,
                address = address.ifEmpty { null },
                googleMapsUrl = mapsUrl.ifEmpty { null },
                tags = tags,
                profileUrl = profileUrl.ifEmpty { null },
                profileImageUrl = profileImageUrl.ifEmpty { null },
                aiCheckedAt = when {
                    url == analyzedUrl && pendingAiCheckAt != null -> pendingAiCheckAt
                    url != existing.originalUrl -> null
                    else -> existing.aiCheckedAt
                },
                aiCheckStatus = when {
                    url == analyzedUrl && pendingAiCheckAt != null -> pendingAiStatus
                    url != existing.originalUrl -> null
                    else -> existing.aiCheckStatus
                },
                publishedAt = when {
                    url == analyzedUrl && pendingPublishedAt != null -> pendingPublishedAt
                    url != existing.originalUrl -> null
                    else -> existing.publishedAt
                }
            )
            try {
                dao.update(updated)
            } catch (_: SQLiteConstraintException) {
                binding.urlField.error = "This link is already saved."
                return@launch
            }
            runOnUiThread {
                Toast.makeText(this@LinkDetailActivity, "Link updated.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun deleteLink(linkId: Long) {
        lifecycleScope.launch {
            val existing = db.savedLinkDao().getById(linkId) ?: return@launch
            db.savedLinkDao().delete(existing)
            runOnUiThread {
                Toast.makeText(this@LinkDetailActivity, "Link deleted.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
