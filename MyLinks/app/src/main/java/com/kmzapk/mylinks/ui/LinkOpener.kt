package com.kmzapk.mylinks.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Open known links in their matching app, then use Android's normal URL handling. */
object LinkOpener {
    fun open(context: Context, value: String?) {
        val uri = Uri.parse(value?.trim().orEmpty())
        if (uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank()) {
            Toast.makeText(context, "Enter a valid web URL.", Toast.LENGTH_SHORT).show()
            return
        }
        val host = uri.host.orEmpty().lowercase()
        fun matches(domain: String) = host == domain || host.endsWith(".$domain")
        val packages = when {
            matches("facebook.com") || matches("fb.watch") ->
                listOf("com.facebook.katana", "com.facebook.lite")
            matches("instagram.com") ->
                listOf("com.instagram.android")
            matches("threads.net") ->
                listOf("com.instagram.barcelona")
            matches("tiktok.com") ->
                listOf("com.zhiliaoapp.musically")
            matches("youtube.com") || matches("youtu.be") ->
                listOf("com.google.android.youtube")
            matches("x.com") || matches("twitter.com") ->
                listOf("com.twitter.android")
            matches("pinterest.com") || matches("pin.it") ->
                listOf("com.pinterest")
            matches("linkedin.com") ->
                listOf("com.linkedin.android")
            matches("maps.google.com") || matches("maps.app.goo.gl") ||
                matches("goo.gl") && uri.path.orEmpty().startsWith("/maps") ||
                matches("google.com") && uri.path.orEmpty().startsWith("/maps") ->
                listOf("com.google.android.apps.maps")
            else -> emptyList()
        }
        for (packageName in packages) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(packageName))
                return
            } catch (_: ActivityNotFoundException) {
                // The app is absent or cannot handle this particular URL.
            }
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app can open this link.", Toast.LENGTH_SHORT).show()
        }
    }
}
