
package com.kmz.taskmanager.util

import android.content.Context
import android.util.Base64
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object BackupUtils {
    private val client = OkHttpClient()
    private const val GITHUB_API_URL = "https://api.github.com/repos/${GitHubConfig.OWNER}/${GitHubConfig.REPO}/contents/${GitHubConfig.FILE_PATH}"

    suspend fun saveToCloud(context: Context, json: String) {
        withContext(Dispatchers.IO) {
            try {
                val sha = getFileSha()
                val base64Content = Base64.encodeToString(json.toByteArray(), Base64.NO_WRAP)
                
                val bodyJson = JSONObject().apply {
                    put("message", "Mise à jour du backup TaskManager")
                    put("content", base64Content)
                    if (sha != null) put("sha", sha)
                }

                val request = Request.Builder()
                    .url(GITHUB_API_URL)
                    .header("Authorization", "token ${GitHubConfig.TOKEN}")
                    .header("Accept", "application/vnd.github+json")
                    .put(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        showToast(context, "Cloud: Backup synchronisé !")
                    } else {
                        val errorBody = response.body?.string() ?: ""
                        throw Exception("Erreur GitHub (${response.code})")
                    }
                }
            } catch (e: Exception) {
                showToast(context, "Erreur Cloud: ${e.localizedMessage}")
            }
        }
    }

    suspend fun fetchFromCloud(context: Context): String? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(GITHUB_API_URL)
                    .header("Authorization", "token ${GitHubConfig.TOKEN}")
                    .header("Accept", "application/vnd.github+json")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val jsonResponse = JSONObject(response.body?.string() ?: "")
                        val contentRelay = jsonResponse.getString("content")
                        val cleanContent = contentRelay.replace("\n", "").replace("\r", "")
                        val decodedBytes = Base64.decode(cleanContent, Base64.DEFAULT)
                        
                        showToast(context, "Cloud: Backup récupéré !")
                        return@withContext String(decodedBytes)
                    } else if (response.code == 404) {
                        throw Exception("Aucun backup trouvé sur le GitHub")
                    } else {
                        throw Exception("Erreur GitHub (${response.code})")
                    }
                }
            } catch (e: Exception) {
                showToast(context, "Erreur Cloud: ${e.localizedMessage}")
                null
            }
        }
    }

    private fun getFileSha(): String? {
        val request = Request.Builder()
            .url(GITHUB_API_URL)
            .header("Authorization", "token ${GitHubConfig.TOKEN}")
            .get()
            .build()
        
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonResponse = JSONObject(response.body?.string() ?: "")
                    jsonResponse.getString("sha")
                } else null
            }
        } catch (e: Exception) { null }
    }

    private suspend fun showToast(context: Context, message: String) {
        withContext(Dispatchers.Main) { 
            Toast.makeText(context, message, Toast.LENGTH_LONG).show() 
        }
    }
}
