package com.football.footballapp.data

import com.squareup.moshi.Moshi
import retrofit2.converter.moshi.MoshiConverterFactory

internal object Network {
    val moshi: Moshi = Moshi.Builder().build()

    val moshiConverter: MoshiConverterFactory = MoshiConverterFactory.create(moshi)
}
