package com.kmzapk.mylinks

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.database.sqlite.SQLiteConstraintException
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kmzapk.mylinks.data.SavedLink
import com.kmzapk.mylinks.data.SavedLinkDatabase
import com.kmzapk.mylinks.data.LinkSuggestionService
import com.kmzapk.mylinks.data.GeminiRateLimitException
import com.kmzapk.mylinks.databinding.ActivityMainBinding
import com.kmzapk.mylinks.ui.LinkOpener
import com.kmzapk.mylinks.ui.SavedLinkAdapter
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val db by lazy { SavedLinkDatabase.getDatabase(this) }
    private var sharedTextForSuggestion = ""
    private var analyzedUrl: String? = null
    private var aiAttemptedAt: Long? = null
    private var aiCheckStatus: String? = null
    private var suggestedPublishedAt: Long? = null
    private val adapter by lazy {
        SavedLinkAdapter { link ->
            val intent = Intent(this, LinkDetailActivity::class.java).apply {
                putExtra("link_id", link.id)
            }
            startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.validateButton.setOnClickListener {
            saveCurrentLink()
        }

        binding.analyzeButton.setOnClickListener { suggestCurrentLink() }

        binding.openLinkButton.setOnClickListener {
            LinkOpener.open(this, binding.urlInput.text?.toString())
        }

        binding.newLinkButton.setOnClickListener {
            clearForm()
        }

        binding.viewSavedButton.setOnClickListener {
            startActivity(Intent(this, ConsultationActivity::class.java))
        }
        binding.savedLinksRecycler.adapter = adapter
        if (savedInstanceState == null) handleIncomingShareIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) loadLatestLink()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun saveCurrentLink() {
        val title = binding.titleInput.text?.toString()?.trim().orEmpty()
        val category = binding.categoryInput.text?.toString()?.trim().orEmpty()
        val url = binding.urlInput.text?.toString()?.trim().orEmpty()
        val source = binding.sourceInput.text?.toString()?.trim().orEmpty()
        val address = binding.addressInput.text?.toString()?.trim().orEmpty()
        val mapsUrl = binding.mapsInput.text?.toString()?.trim().orEmpty()
        val tags = binding.tagsInput.text?.toString()?.trim().orEmpty()
        val profileUrl = binding.profileInput.text?.toString()?.trim().orEmpty()
        val profileImageUrl = binding.profileImageInput.text?.toString()?.trim().orEmpty()

        if (url.isEmpty()) {
            Toast.makeText(this, "Please fill in the URL.", Toast.LENGTH_SHORT).show()
            return
        }

        val link = SavedLink(
            title = title,
            category = category,
            originalUrl = url,
            source = source,
            address = address.ifEmpty { null },
            googleMapsUrl = mapsUrl.ifEmpty { null },
            tags = tags,
            profileUrl = profileUrl.ifEmpty { null },
            profileImageUrl = profileImageUrl.ifEmpty { null },
            createdAt = System.currentTimeMillis(),
            aiCheckedAt = aiAttemptedAt.takeIf { analyzedUrl == url },
            aiCheckStatus = aiCheckStatus.takeIf { analyzedUrl == url },
            publishedAt = suggestedPublishedAt.takeIf { analyzedUrl == url }
        )

        lifecycleScope.launch {
            val dao = db.savedLinkDao()
            if (dao.getByUrl(url) != null) {
                binding.urlInput.error = "This link is already saved."
                return@launch
            }
            try {
                dao.insert(link)
            } catch (_: SQLiteConstraintException) {
                binding.urlInput.error = "This link is already saved."
                return@launch
            }
            runOnUiThread {
                Toast.makeText(this@MainActivity, "Link saved: $title", Toast.LENGTH_LONG).show()
                loadLatestLink()
                clearForm()
            }
        }
    }

    private fun loadLatestLink() {
        lifecycleScope.launch {
            val latest = db.savedLinkDao().getLatest()
            adapter.submitList(latest?.let { listOf(it) } ?: emptyList())
            binding.emptyStateText.text = if (latest == null) {
                "No saved links yet. Share a publication to create the first item."
            } else {
                ""
            }
            binding.emptyStateText.visibility = if (latest == null) android.view.View.VISIBLE else android.view.View.GONE
            binding.savedLinksRecycler.visibility = if (latest == null) android.view.View.GONE else android.view.View.VISIBLE
        }
    }

    private fun clearForm() {
        sharedTextForSuggestion = ""
        analyzedUrl = null
        aiAttemptedAt = null
        aiCheckStatus = null
        suggestedPublishedAt = null
        binding.titleInput.setText("")
        binding.categoryInput.setText("")
        binding.urlInput.setText("")
        binding.sourceInput.setText("")
        binding.addressInput.setText("")
        binding.mapsInput.setText("")
        binding.tagsInput.setText("")
        binding.profileInput.setText("")
        binding.profileImageInput.setText("")
        binding.statusCard.visibility = View.GONE
        binding.profileSourceText.visibility = View.GONE
    }

    private fun handleIncomingShareIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) return

        val sharedText = buildList {
            if (intent.action == Intent.ACTION_SEND) {
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.let { add(it) }
            } else {
                intent.getCharSequenceArrayListExtra(Intent.EXTRA_TEXT)?.forEach { add(it.toString()) }
            }
            intent.getStringExtra(Intent.EXTRA_HTML_TEXT)?.let { add(it) }
            intent.dataString?.let { add(it) }
            intent.clipData?.let { clip ->
                for (index in 0 until clip.itemCount) {
                    clip.getItemAt(index).text?.toString()?.let { add(it) }
                    clip.getItemAt(index).uri?.takeIf { it.scheme in listOf("http", "https") }
                        ?.toString()?.let { add(it) }
                }
            }
        }.distinct().joinToString("\n")
        sharedTextForSuggestion = sharedText
        val sharedUrl = extractUrl(sharedText)
        val title = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
        val mapsUrl = extractGoogleMapsUrl(sharedText)

        if (!sharedUrl.isNullOrBlank()) {
            binding.urlInput.setText(sharedUrl)
            binding.titleInput.setText(title)
            binding.categoryInput.setText("")
            binding.sourceInput.setText("")
            binding.addressInput.setText("")
            binding.mapsInput.setText(mapsUrl.orEmpty())
            binding.tagsInput.setText("")
            binding.profileInput.setText("")
            binding.profileImageInput.setText("")
            binding.statusText.text = "Shared link detected. Review the fields before saving."
            binding.statusCard.visibility = View.VISIBLE
            binding.profileSourceText.visibility = View.GONE
        } else {
            binding.statusText.text = "No URL detected in the shared content"
            binding.statusCard.visibility = View.VISIBLE
            binding.profileSourceText.visibility = View.GONE
        }
    }

    private fun suggestCurrentLink() {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val url = binding.urlInput.text?.toString()?.trim().orEmpty()
        val uri = Uri.parse(url)
        if (apiKey.isBlank()) {
            Toast.makeText(this, "Set GEMINI_API_KEY in local.properties.", Toast.LENGTH_LONG).show()
            return
        }
        if (uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank()) {
            Toast.makeText(this, "Enter a valid URL.", Toast.LENGTH_SHORT).show()
            return
        }
        binding.analyzeButton.isEnabled = false
        binding.statusText.text = "Analyzing link…"
        binding.statusCard.visibility = View.VISIBLE
        binding.profileSourceText.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val suggestion = LinkSuggestionService.suggest(apiKey, url, sharedTextForSuggestion)
                if (binding.urlInput.text?.toString()?.trim() != url) {
                    binding.statusText.text = "URL changed. Run the analysis again."
                    return@launch
                }
                analyzedUrl = url
                aiAttemptedAt = System.currentTimeMillis()
                aiCheckStatus = "completed"
                suggestedPublishedAt = suggestion.publishedAt
                if (suggestion.title.isBlank() && suggestion.category.isBlank() &&
                    suggestion.source.isBlank() && suggestion.address.isBlank() &&
                    suggestion.googleMapsUrl == null && suggestion.profileUrl == null &&
                    suggestion.profileImageUrl == null && suggestion.publishedAt == null) {
                    aiCheckStatus = "empty"
                    binding.statusText.text = "No useful public details found for this link. Add the information manually."
                    return@launch
                }
                if (suggestion.title.isNotBlank()) binding.titleInput.setText(suggestion.title)
                if (suggestion.category.isNotBlank() &&
                    !suggestion.category.equals("General", ignoreCase = true)) {
                    binding.categoryInput.setText(suggestion.category)
                }
                if (suggestion.source.isNotBlank()) binding.sourceInput.setText(suggestion.source)
                if (suggestion.address.isNotBlank()) binding.addressInput.setText(suggestion.address)
                suggestion.googleMapsUrl?.let { binding.mapsInput.setText(it) }
                suggestion.profileUrl?.let { binding.profileInput.setText(it) }
                suggestion.profileImageUrl?.let { binding.profileImageInput.setText(it) }
                binding.profileSourceText.text = suggestion.profileUrl.orEmpty()
                binding.profileSourceText.visibility = if (suggestion.profileUrl == null) View.GONE else View.VISIBLE
                binding.statusText.text = if (suggestion.profileUrl != null) {
                    "AI suggestions include public profile details. Review every field before saving."
                } else {
                    "AI suggestions ready. Review every field before saving."
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (error !is GeminiRateLimitException && binding.urlInput.text?.toString()?.trim() == url) {
                    analyzedUrl = url
                    aiAttemptedAt = System.currentTimeMillis()
                    aiCheckStatus = "failed"
                    suggestedPublishedAt = null
                }
                binding.statusText.text = "AI suggestion failed: ${error.message ?: "network error"}"
            } finally {
                binding.analyzeButton.isEnabled = true
            }
        }
    }

    private fun extractUrl(text: String): String? {
        val match = Regex("https?://\\S+").find(text)
        return match?.value?.trimEnd('.', ',', ';', ')', ']')
    }

    private fun extractGoogleMapsUrl(text: String): String? {
        val match = Regex("https?://(?:maps\\.google\\.[^\\s]+|goo\\.gl/maps/[^\\s]+|maps\\.app\\.goo\\.gl/[^\\s]+)", RegexOption.IGNORE_CASE).find(text)
        return match?.value?.trimEnd('.', ',', ';', ')', ']')
    }

}
