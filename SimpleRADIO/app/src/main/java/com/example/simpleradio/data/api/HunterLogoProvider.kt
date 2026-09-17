package com.example.simpleradio.data.api

import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.withTimeoutOrNull

/** Free, keyless domain-logo fallback. Only an already verified official domain is accepted. */
object HunterLogoProvider {
    private const val TIMEOUT_MS = 3_000L
    private val cache = ConcurrentHashMap<String, LogoDevProvider.ImageInfo>()
    private val misses = ConcurrentHashMap.newKeySet<String>()

    suspend fun findLogo(
            officialHomepage: String?,
            rejectedUrls: Set<String>
    ): LogoDevProvider.ImageInfo? {
        val domain =
                officialHomepage?.let(LogoDevProvider::domainFromUrl)
                        ?.takeIf { it.isNotBlank() }
                        ?: return null
        cache[domain]?.takeUnless { it.url in rejectedUrls }?.let { return it }
        if (domain in misses && rejectedUrls.isEmpty()) return null

        val encodedDomain = URLEncoder.encode(domain, Charsets.UTF_8.name())
        val url = "https://logos.hunter.io/$encodedDomain"
        if (url in rejectedUrls) return null

        val result = withTimeoutOrNull(TIMEOUT_MS) { LogoDevProvider.inspectLogo(url) }
        if (result != null) {
            cache[domain] = result
            misses.remove(domain)
        } else if (rejectedUrls.isEmpty()) {
            misses += domain
        }
        return result
    }
}
