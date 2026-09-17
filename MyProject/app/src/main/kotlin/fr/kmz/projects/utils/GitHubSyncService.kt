package fr.kmz.projects.utils

import android.util.Base64
import fr.kmz.projects.BuildConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object GitHubSyncService {

    private const val OWNER = "kamelzouari11"
    private const val REPO = "Kmz_APK"
    private const val BRANCH = "main"
    private const val FOLDER = "MySharedFolder"
    private const val GLOBAL_FILE = "$FOLDER/depenses_all_projects.csv"

    class SyncException(message: String) : Exception(message)

    private fun apiUrl(): String =
        "https://api.github.com/repos/$OWNER/$REPO/contents/$GLOBAL_FILE?ref=$BRANCH"

    private fun putUrl(): String =
        "https://api.github.com/repos/$OWNER/$REPO/contents/$GLOBAL_FILE"

    suspend fun downloadCsvContent(): String =
        withContext(Dispatchers.IO) {
            val url = URL(apiUrl())
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "token ${BuildConfig.GITHUB_TOKEN}")
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.readText()
                    val jsonObject = JSONObject(response)
                    val contentBase64 = jsonObject.getString("content")
                    val decodedBytes = Base64.decode(contentBase64, Base64.DEFAULT)
                    String(decodedBytes, Charsets.UTF_8)
                } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    ""
                } else {
                    throw SyncException("Erreur lors du téléchargement: $responseCode")
                }
            } catch (e: Exception) {
                throw SyncException(e.message ?: "Erreur inconnue")
            } finally {
                connection.disconnect()
            }
        }

    suspend fun uploadCsvContent(
        content: String,
        commitMessage: String = "Update depenses_all_projects.csv"
    ): Boolean =
        withContext(Dispatchers.IO) {
            val fileSha = getFileState()?.sha
            val encodedContent =
                Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

            val url = URL(putUrl())
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "PUT"
                connection.setRequestProperty("Authorization", "token ${BuildConfig.GITHUB_TOKEN}")
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val jsonPayload = JSONObject().apply {
                    put("message", commitMessage)
                    put("content", encodedContent)
                    put("branch", BRANCH)
                    if (fileSha != null) put("sha", fileSha)
                }

                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(jsonPayload.toString())
                writer.flush()
                writer.close()

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                    true
                } else {
                    val errorReader = BufferedReader(InputStreamReader(connection.errorStream))
                    val errorResponse = errorReader.readText()
                    throw SyncException("Erreur lors de l'envoi ($responseCode): $errorResponse")
                }
            } catch (e: Exception) {
                throw SyncException(e.message ?: "Erreur inconnue lors de l'upload")
            } finally {
                connection.disconnect()
            }
        }

    private fun getFileState(): GitHubFileState? {
        val url = URL(apiUrl())
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "token ${BuildConfig.GITHUB_TOKEN}")
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                val jsonObject = JSONObject(response)
                return GitHubFileState(sha = jsonObject.getString("sha"))
            }
        } catch (e: Exception) {
            // File might not exist yet
        } finally {
            connection.disconnect()
        }
        return null
    }

    private data class GitHubFileState(val sha: String)
}
