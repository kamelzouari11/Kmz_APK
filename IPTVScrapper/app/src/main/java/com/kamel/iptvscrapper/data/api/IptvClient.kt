package com.kamel.iptvscrapper.data.api

import com.kamel.iptvscrapper.data.local.entities.LinkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class IptvCategory(val id: String, val name: String)
data class IptvChannel(
    val id: String, 
    val name: String, 
    val logo: String? = null, 
    val cmd: String? = null,
    val nowPlaying: String? = null
)

class IptvClient {
    private val stalkerSessions = mutableMapOf<String, StalkerSession>()

    // Reusable OkHttpClient with CookieJar (inspired by SimpleIPTV)
    private fun createClient(link: LinkEntity): OkHttpClient {
        val cookieJar = object : CookieJar {
            private val cookies = mutableMapOf<String, MutableList<Cookie>>()
            
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                this.cookies.getOrPut(url.host) { mutableListOf() }.addAll(cookies)
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                val list = cookies[url.host] ?: mutableListOf()
                // Ensure MAG required cookies are always present
                if (link.type == "STALKER") {
                    val required = listOf(
                        "mac" to link.mac,
                        "stb_lang" to "en",
                        "timezone" to "Europe/Paris"
                    )
                    required.forEach { (name, value) ->
                        if (list.none { it.name == name }) {
                            list.add(Cookie.Builder().domain(url.host).path("/").name(name).value(value ?: "").build())
                        }
                    }
                }
                return list
            }
        }

        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .cookieJar(cookieJar)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    suspend fun getCategories(link: LinkEntity): List<IptvCategory> = withContext(Dispatchers.IO) {
        try {
            when (link.type) {
                "XTREAM" -> getXtreamCategories(link)
                "STALKER" -> getStalkerCategories(link)
                else -> emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getChannels(link: LinkEntity, categoryId: String): List<IptvChannel> = withContext(Dispatchers.IO) {
        try {
            when (link.type) {
                "XTREAM" -> getXtreamChannels(link, categoryId)
                "STALKER" -> getStalkerChannels(link, categoryId)
                else -> emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getStreamUrl(link: LinkEntity, channel: IptvChannel): String? = withContext(Dispatchers.IO) {
        try {
            when (link.type) {
                "XTREAM" -> {
                    val baseUrl = if (link.url.endsWith("/")) link.url else "${link.url}/"
                    "${baseUrl}live/${link.username}/${link.password}/${channel.id}.ts"
                }
                "STALKER" -> getStalkerStreamUrl(link, channel)
                else -> null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getXtreamCategories(link: LinkEntity): List<IptvCategory> {
        val client = createClient(link)
        val url = "${link.url.ensureSlash()}player_api.php?username=${link.username}&password=${link.password}&action=get_live_categories"
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        val body = response.body?.string() ?: "[]"
        if (!body.startsWith("[")) return emptyList()
        val json = JSONArray(body)
        val result = mutableListOf<IptvCategory>()
        for (i in 0 until json.length()) {
            val obj = json.getJSONObject(i)
            result.add(IptvCategory(obj.optString("category_id"), obj.optString("category_name")))
        }
        return result
    }

    private fun getXtreamChannels(link: LinkEntity, categoryId: String): List<IptvChannel> {
        val client = createClient(link)
        val url = "${link.url.ensureSlash()}player_api.php?username=${link.username}&password=${link.password}&action=get_live_streams&category_id=$categoryId"
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        val body = response.body?.string() ?: "[]"
        if (!body.startsWith("[")) return emptyList()
        val json = JSONArray(body)
        val result = mutableListOf<IptvChannel>()
        for (i in 0 until json.length()) {
            val obj = json.getJSONObject(i)
            result.add(IptvChannel(obj.optString("stream_id"), obj.optString("name"), obj.optString("stream_icon")))
        }
        return result
    }

    private fun getStalkerCategories(link: LinkEntity): List<IptvCategory> {
        val session = getOrPerformHandshake(link) ?: return emptyList()
        val actions = listOf("get_genres", "get_categories")
        for (action in actions) {
            val url = "${session.baseUrl}server/load.php?type=itv&action=$action&JsHttpRequest=1-xml"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${session.token}")
                .build()
            try {
                val response = session.client.newCall(request).execute()
                val js = JSONObject(response.body?.string() ?: "").opt("js") ?: continue
                val result = mutableListOf<IptvCategory>()
                if (js is JSONArray) {
                    for (i in 0 until js.length()) {
                        val obj = js.getJSONObject(i)
                        result.add(IptvCategory(obj.optString("id"), obj.optString("title")))
                    }
                } else if (js is JSONObject) {
                    val keys = js.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val obj = js.optJSONObject(key) ?: continue
                        result.add(IptvCategory(obj.optString("id"), obj.optString("title")))
                    }
                }
                if (result.isNotEmpty()) return result
            } catch (e: Exception) {}
        }
        return emptyList()
    }

    private fun getStalkerChannels(link: LinkEntity, categoryId: String): List<IptvChannel> {
        val session = getOrPerformHandshake(link) ?: return emptyList()
        val params = listOf("genre", "category")
        for (param in params) {
            val url = "${session.baseUrl}server/load.php?type=itv&action=get_ordered_list&$param=$categoryId&JsHttpRequest=1-xml"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${session.token}")
                .build()
            try {
                val response = session.client.newCall(request).execute()
                val body = response.body?.string() ?: ""
                val jsonJs = JSONObject(body).optJSONObject("js") ?: continue
                val data = jsonJs.optJSONArray("data") ?: (if (jsonJs.length() > 0) null else JSONObject(body).optJSONArray("js"))
                val result = mutableListOf<IptvChannel>()
                val finalArray = data ?: (if (JSONObject(body).opt("js") is JSONArray) JSONObject(body).getJSONArray("js") else null)
                if (finalArray != null) {
                    for (i in 0 until finalArray.length()) {
                        val obj = finalArray.getJSONObject(i)
                        val genreId = obj.optString("tv_genre_id")
                        if (genreId.isNotBlank() && genreId != categoryId && categoryId != "*") continue
                        result.add(IptvChannel(obj.optString("id"), obj.optString("name"), obj.optString("logo"), obj.optString("cmd"), obj.optString("cur_playing").takeIf { it != "[No channel info]" }))
                    }
                }
                if (result.isNotEmpty()) return result
            } catch (e: Exception) {}
        }
        return emptyList()
    }

    private fun getStalkerStreamUrl(link: LinkEntity, channel: IptvChannel): String? {
        val session = getOrPerformHandshake(link) ?: return null
        val cmd = channel.cmd ?: "ffmpeg http://localhost/ch/${channel.id}_"
        val url = "${session.baseUrl}server/load.php?type=itv&action=create_link&cmd=${cmd.replace(" ", "%20")}&JsHttpRequest=1-xml"
        val response = session.client.newCall(Request.Builder().url(url).header("Authorization", "Bearer ${session.token}").build()).execute()
        val js = JSONObject(response.body?.string() ?: "").opt("js") ?: return null
        val finalCmd = if (js is JSONObject) js.optString("cmd") else if (js is String) js else null
        return if (finalCmd?.contains(" ") == true) finalCmd.substringAfter(" ") else finalCmd
    }

    private data class StalkerSession(val baseUrl: String, val token: String, val client: OkHttpClient, val timestamp: Long)

    private fun getOrPerformHandshake(link: LinkEntity): StalkerSession? {
        val key = "${link.url}-${link.mac}"
        stalkerSessions[key]?.takeIf { System.currentTimeMillis() - it.timestamp < 30 * 60 * 1000 }?.let { return it }

        val client = createClient(link)
        val cleanUrl = link.url.trim().removeSuffix("/")
        val bases = listOf(cleanUrl, if (cleanUrl.endsWith("/c")) cleanUrl.substringBeforeLast("/c") else cleanUrl)
        val paths = listOf("server/load.php", "stalker_portal/server/load.php", "portal/server/load.php")
        
        for (base in bases.distinct()) {
            val norm = if (base.endsWith("/")) base else "$base/"
            for (path in paths) {
                val testUrl = "$norm$path?type=stb&action=handshake&JsHttpRequest=1-xml"
                try {
                    val resp = client.newCall(Request.Builder().url(testUrl).build()).execute()
                    val token = JSONObject(resp.body?.string() ?: "").optJSONObject("js")?.optString("token")
                    if (!token.isNullOrBlank()) {
                        val session = StalkerSession(norm, token, client, System.currentTimeMillis())
                        stalkerSessions[key] = session
                        return session
                    }
                } catch (e: Exception) {}
            }
        }
        return null
    }

    private fun String.ensureSlash() = if (this.endsWith("/")) this else "$this/"
}
