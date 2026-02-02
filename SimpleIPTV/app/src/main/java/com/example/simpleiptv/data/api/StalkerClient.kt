package com.example.simpleiptv.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object StalkerClient {

        private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

        @Suppress("DEPRECATION")
        fun create(baseUrl: String, mac: String): StalkerApi {
                val sanitizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

                // Define CookieJar inside create to use a fresh session for each client instance
                val cookieJar =
                        object : okhttp3.CookieJar {
                                private val cookieStore =
                                        mutableMapOf<String, List<okhttp3.Cookie>>()

                                override fun saveFromResponse(
                                        url: okhttp3.HttpUrl,
                                        cookies: List<okhttp3.Cookie>
                                ) {
                                        cookies.forEach { cookie ->
                                                val list =
                                                        (cookieStore[url.host] ?: emptyList())
                                                                .toMutableList()
                                                val iterator = list.iterator()
                                                while (iterator.hasNext()) {
                                                        if (iterator.next().name == cookie.name) {
                                                                iterator.remove()
                                                        }
                                                }
                                                list.add(cookie)
                                                cookieStore[url.host] = list
                                        }
                                }

                                override fun loadForRequest(
                                        url: okhttp3.HttpUrl
                                ): List<okhttp3.Cookie> {
                                        return cookieStore[url.host] ?: emptyList()
                                }
                        }

                val okHttpClient =
                        OkHttpClient.Builder()
                                .cookieJar(cookieJar)
                                .connectTimeout(30, TimeUnit.SECONDS)
                                .readTimeout(30, TimeUnit.SECONDS)
                                .build()

                // Pre-populate CookieJar with the MAC cookie
                val urlObj = sanitizedUrl.toHttpUrlOrNull()
                if (urlObj != null) {
                        val macCookieObj =
                                okhttp3.Cookie.Builder()
                                        .domain(urlObj.host)
                                        .path("/")
                                        .name("mac")
                                        .value(mac)
                                        .build()
                        val langCookieObj =
                                okhttp3.Cookie.Builder()
                                        .domain(urlObj.host)
                                        .path("/")
                                        .name("stb_lang")
                                        .value("en")
                                        .build()
                        val tzCookieObj =
                                okhttp3.Cookie.Builder()
                                        .domain(urlObj.host)
                                        .path("/")
                                        .name("timezone")
                                        .value("Europe/Paris")
                                        .build()
                        cookieJar.saveFromResponse(
                                urlObj,
                                listOf(macCookieObj, langCookieObj, tzCookieObj)
                        )
                }

                return Retrofit.Builder()
                        .baseUrl(sanitizedUrl)
                        .client(okHttpClient)
                        .addConverterFactory(MoshiConverterFactory.create(moshi))
                        .build()
                        .create(StalkerApi::class.java)
        }
}
