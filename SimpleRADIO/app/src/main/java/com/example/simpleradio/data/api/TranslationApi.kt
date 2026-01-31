package com.example.simpleradio.data.api

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

object TranslationApi {
    private const val BASE_URL =
            "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=fr&dt=t&q="

    suspend fun translate(text: String): String? =
            withContext(Dispatchers.IO) {
                if (text.isBlank()) return@withContext null
                try {
                    val encodedText = URLEncoder.encode(text, "UTF-8")
                    val url = URL(BASE_URL + encodedText)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0")

                    if (connection.responseCode == 200) {
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        val outerArray = JSONArray(response)
                        val innerArray = outerArray.getJSONArray(0)
                        val result = StringBuilder()
                        for (i in 0 until innerArray.length()) {
                            val segment = innerArray.getJSONArray(i)
                            result.append(segment.getString(0))
                        }
                        return@withContext result.toString()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                null
            }
}
