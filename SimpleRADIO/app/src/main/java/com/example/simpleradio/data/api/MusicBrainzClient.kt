package com.example.simpleradio.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object MusicBrainzClient {
    private const val BASE_URL = "https://musicbrainz.org/ws/2/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "SimpleIPTV/1.0 (kamel@example.com)")
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }
        .build()

    fun create(): MusicBrainzApi {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(MusicBrainzApi::class.java)
    }
}
