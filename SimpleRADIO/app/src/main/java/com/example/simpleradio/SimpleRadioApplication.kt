package com.example.simpleradio

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import okhttp3.OkHttpClient

/** Shared image loader for station artwork coming from third-party sites. */
class SimpleRadioApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
            ImageLoader.Builder(this)
                    .okHttpClient {
                        OkHttpClient.Builder()
                                .addNetworkInterceptor { chain ->
                                    val request =
                                            chain.request()
                                                    .newBuilder()
                                                    .header("User-Agent", IMAGE_USER_AGENT)
                                                    .build()
                                    chain.proceed(request)
                                }
                                .build()
                    }
                    .components { add(SvgDecoder.Factory()) }
                    .build()

    companion object {
        const val IMAGE_USER_AGENT = "SimpleRADIO/1.0 (Android; radio logo client)"
    }
}
