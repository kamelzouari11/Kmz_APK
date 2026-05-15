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
    private const val FILE_PATH = "MySharedFolder/depenses.csv"
    private const val BRANCH = "main"

    private const val API_URL =
            "https://api.github.com/repos/$OWNER/$REPO/contents/$FILE_PATH?ref=$BRANCH"
    private const val PUT_URL = "https://api.github.com/repos/$OWNER/$REPO/contents/$FILE_PATH"

    class SyncException(message: String) : Exception(message)

    /** Download CSV content from GitHub */
    suspend fun downloadCsvContent(): String =
            withContext(Dispatchers.IO) {
                val url = URL(API_URL)
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "GET"
                    connection.setRequestProperty(
                            "Authorization",
                            "token ${BuildConfig.GITHUB_TOKEN}"
                    )
                    connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val reader = BufferedReader(InputStreamReader(connection.inputStream))
                        val response = reader.readText()
                        val jsonObject = JSONObject(response)

                        val contentBase64 = jsonObject.getString("content")
                        // Base64 from github contains newlines, Android Base64 can parse it with
                        // DEFAULT
                        val decodedBytes = Base64.decode(contentBase64, Base64.DEFAULT)
                        String(decodedBytes, Charsets.UTF_8)
                    } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                        // File does not exist yet on remote, return empty
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

    /** Upload CSV content to GitHub */
    suspend fun uploadCsvContent(
            content: String,
            commitMessage: String = "Update depenses.csv"
    ): Boolean =
            withContext(Dispatchers.IO) {
                // First get the SHA of the existing file to update it
                val currentFileState = getFileState()
                val fileSha = currentFileState?.sha

                val encodedContent =
                        Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

                val url = URL(PUT_URL)
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "PUT"
                    connection.setRequestProperty(
                            "Authorization",
                            "token ${BuildConfig.GITHUB_TOKEN}"
                    )
                    connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.doOutput = true

                    val jsonPayload =
                            JSONObject().apply {
                                put("message", commitMessage)
                                put("content", encodedContent)
                                put("branch", BRANCH)
                                if (fileSha != null) {
                                    put("sha", fileSha)
                                }
                            }

                    val writer = OutputStreamWriter(connection.outputStream)
                    writer.write(jsonPayload.toString())
                    writer.flush()
                    writer.close()

                    val responseCode = connection.responseCode
                    // HTTP 200 OK (updated) or HTTP 201 Created
                    if (responseCode == HttpURLConnection.HTTP_OK ||
                                    responseCode == HttpURLConnection.HTTP_CREATED
                    ) {
                        true
                    } else {
                        val errorReader = BufferedReader(InputStreamReader(connection.errorStream))
                        val errorResponse = errorReader.readText()
                        throw SyncException(
                                "Erreur lors de l'envoi ($responseCode): $errorResponse"
                        )
                    }
                } catch (e: Exception) {
                    throw SyncException(e.message ?: "Erreur inconnue lors de l'upload")
                } finally {
                    connection.disconnect()
                }
            }

    private fun getFileState(): GitHubFileState? {
        val url = URL(API_URL)
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
            // Ignored, file might not exist
        } finally {
            connection.disconnect()
        }
        return null
    }

    private data class GitHubFileState(val sha: String)
}
