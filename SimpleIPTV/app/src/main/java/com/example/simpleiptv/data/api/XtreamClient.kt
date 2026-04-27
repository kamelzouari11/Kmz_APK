package com.example.simpleiptv.data.api

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object XtreamClient {

    private val moshi = Moshi.Builder().build()

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()
            chain.proceed(request)
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    // Thread-safe cache: un client Retrofit par URL de serveur
    private val cache = ConcurrentHashMap<String, XtreamApi>()

    fun create(baseUrl: String): XtreamApi {
        val sanitizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return cache.getOrPut(sanitizedUrl) {
            Retrofit.Builder()
                .baseUrl(sanitizedUrl)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(XtreamApi::class.java)
        }
    }
}
