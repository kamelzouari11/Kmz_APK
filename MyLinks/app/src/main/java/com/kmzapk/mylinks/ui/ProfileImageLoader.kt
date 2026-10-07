package com.kmzapk.mylinks.ui

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.Html
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Remote profile images are held only in RAM while the app is running. */
object ProfileImageLoader {
    private const val MAX_DOWNLOAD_BYTES = 5_000_000
    private var oldDiskCacheCleared = false
    private val memoryCache = object : LruCache<String, Bitmap>(6 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun load(view: ImageView, imageUrl: String?) {
        clearOldDiskCache(view.context)
        view.setImageDrawable(null)
        view.tag = imageUrl
        view.visibility = if (imageUrl.isNullOrBlank()) View.GONE else View.VISIBLE
        if (imageUrl.isNullOrBlank()) return
        val decodedUrl = Html.fromHtml(imageUrl, Html.FROM_HTML_MODE_LEGACY).toString()
        val uri = Uri.parse(decodedUrl)
        if (uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank()) {
            view.visibility = View.GONE
            return
        }
        val cached = memoryCache.get(decodedUrl)
        if (cached != null) {
            view.setImageBitmap(cached)
            return
        }
        val owner = view.findViewTreeLifecycleOwner() ?: lifecycleOwner(view.context) ?: return
        owner.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { downloadThumbnail(decodedUrl) }
            if (view.tag == imageUrl) {
                if (bitmap == null) view.visibility = View.GONE else view.setImageBitmap(bitmap)
            }
        }
    }

    @Synchronized
    private fun clearOldDiskCache(context: Context) {
        if (oldDiskCacheCleared) return
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("profile_") && file.name.endsWith(".jpg")) file.delete()
        }
        oldDiskCacheCleared = true
    }

    private fun lifecycleOwner(context: Context): LifecycleOwner? {
        var current: Context? = context
        while (current != null) {
            if (current is LifecycleOwner) return current
            current = (current as? ContextWrapper)?.baseContext
        }
        return null
    }

    private fun downloadThumbnail(url: String): Bitmap? {
        val connection = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 MyLinks/1.0")
            }
        } catch (_: Exception) { return null }
        return try {
            if (connection.responseCode !in 200..299 ||
                !connection.contentType.orEmpty().startsWith("image/")) return null
            val bytes = connection.inputStream.use { stream ->
                val output = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (output.size() <= MAX_DOWNLOAD_BYTES) {
                    val count = stream.read(chunk)
                    if (count < 0) break
                    output.write(chunk, 0, count)
                }
                output.toByteArray()
            }
            if (bytes.size > MAX_DOWNLOAD_BYTES) return null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return null
            var sample = 1
            while (options.outWidth / sample > 480 || options.outHeight / sample > 480) sample *= 2
            options.inJustDecodeBounds = false
            options.inSampleSize = sample
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
            val thumbnail = Bitmap.createScaledBitmap(decoded, 384, 384, true)
            if (thumbnail !== decoded) decoded.recycle()
            memoryCache.put(url, thumbnail)
            thumbnail
        } catch (_: Exception) { null } finally { connection.disconnect() }
    }
}
