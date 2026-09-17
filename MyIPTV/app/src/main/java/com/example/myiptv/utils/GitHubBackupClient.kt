package com.example.myiptv.utils

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GitHubBackupClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun upload(json: String) = withContext(Dispatchers.IO) {
        uploadBytes(
            content = json.toByteArray(Charsets.UTF_8),
            path = GitHubConfig.FILE_PATH,
            message = "Mise à jour du backup multi-profils MyIPTV",
        )
    }

    suspend fun uploadEpgDatabase(content: ByteArray) = withContext(Dispatchers.IO) {
        uploadBytes(
            content = content,
            path = GitHubConfig.EPG_FILE_PATH,
            message = "Mise à jour de la base EPG courte MyIPTV",
        )
    }

    suspend fun download(): String = withContext(Dispatchers.IO) {
        String(downloadBytes(GitHubConfig.FILE_PATH, "sauvegarde MyIPTV"), Charsets.UTF_8)
    }

    suspend fun downloadEpgDatabase(): ByteArray = withContext(Dispatchers.IO) {
        downloadBytes(GitHubConfig.EPG_FILE_PATH, "base EPG MyIPTV")
    }

    private fun uploadBytes(content: ByteArray, path: String, message: String) {
        requireToken()
        val body = JSONObject().apply {
            put("message", message)
            put("content", Base64.encodeToString(content, Base64.NO_WRAP))
            getRemoteSha(path)?.let { put("sha", it) }
        }
        val request = authenticatedRequest(path)
            .put(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Erreur GitHub pendant l’upload (${response.code}).")
            }
        }
    }

    private fun downloadBytes(path: String, label: String): ByteArray {
        requireToken()
        val request = authenticatedRequest(path)
            .header("Accept", "application/vnd.github.raw+json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            when {
                response.code == 404 -> error("Aucune $label trouvée sur GitHub.")
                !response.isSuccessful -> error("Erreur GitHub pendant le download (${response.code}).")
                else -> {
                    return response.body?.bytes()?.takeIf { it.isNotEmpty() }
                        ?: error("La $label GitHub est vide.")
                }
            }
        }
    }

    private fun getRemoteSha(path: String): String? {
        client.newCall(authenticatedRequest(path).get().build()).execute().use { response ->
            return when {
                response.code == 404 -> null
                response.isSuccessful -> JSONObject(response.body?.string().orEmpty()).getString("sha")
                else -> error("Impossible de lire la sauvegarde GitHub (${response.code}).")
            }
        }
    }

    private fun authenticatedRequest(path: String): Request.Builder = Request.Builder()
        .url(apiUrl(path))
        .header("Authorization", "token ${GitHubConfig.token}")
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")

    private fun requireToken() {
        check(GitHubConfig.token.isNotBlank()) {
            "Jeton GitHub absent de /KmzAPK/local.properties (github.token)."
        }
    }

    companion object {
        private fun apiUrl(path: String) =
            "https://api.github.com/repos/${GitHubConfig.OWNER}/${GitHubConfig.REPOSITORY}/contents/$path"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
