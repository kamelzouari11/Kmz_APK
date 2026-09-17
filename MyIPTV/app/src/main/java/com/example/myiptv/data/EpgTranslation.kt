package com.example.myiptv.data

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal fun epgLanguageForCountry(country: String): String? = when (country.trim().uppercase(Locale.ROOT)) {
    "UK", "GB", "US", "USA", "AU", "NZ", "IE" -> "en"
    "FR", "MC" -> "fr"
    "DE", "AT" -> "de"
    "IT", "SM" -> "it"
    "ES", "MX", "AR", "CL", "CO", "PE", "VE" -> "es"
    "PT", "BR" -> "pt"
    "TN", "DZ", "MA", "EG", "SA", "AE", "QA", "KW", "BH", "OM", "JO", "LB", "SY", "IQ", "LY", "SD", "YE" -> "ar"
    "TR" -> "tr"
    "NL" -> "nl"
    "PL" -> "pl"
    "RO", "MD" -> "ro"
    "RU" -> "ru"
    "UA" -> "uk"
    "GR", "CY" -> "el"
    "SE" -> "sv"
    "NO" -> "no"
    "DK" -> "da"
    "FI" -> "fi"
    "CZ" -> "cs"
    "SK" -> "sk"
    "HU" -> "hu"
    "BG" -> "bg"
    "HR" -> "hr"
    "SI" -> "sl"
    "AL" -> "sq"
    "CN", "TW" -> "zh"
    "JP" -> "ja"
    "KR" -> "ko"
    "IR" -> "fa"
    "IL" -> "he"
    "TH" -> "th"
    "VN" -> "vi"
    "ID" -> "id"
    else -> null // Country alone cannot determine the language for these channels.
}

internal suspend fun translateEpgToFrench(
    title: String,
    description: String,
    country: String,
    onProgress: (String) -> Unit = {},
): Pair<String, String> {
    onProgress("Identification de la langue…")
    val identifier = LanguageIdentification.getClient()
    val detected = try {
        identifier.identifyLanguage(
            listOf(title, description).filter(String::isNotBlank).joinToString("\n"),
        ).resultAsync()
    } finally {
        identifier.close()
    }
    currentCoroutineContext().ensureActive()
    val source = if (detected == "und") epgLanguageForCountry(country)
        else TranslateLanguage.fromLanguageTag(detected)
    requireNotNull(source) { "Unknown or unsupported language" }
    if (source == TranslateLanguage.FRENCH) return title to description

    val translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(source)
            .setTargetLanguage(TranslateLanguage.FRENCH)
            .build(),
    )
    return try {
        onProgress("Préparation des langues…")
        // Reuses installed models; downloads only missing models, including over Ethernet on TV.
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).resultAsync()
        currentCoroutineContext().ensureActive()
        onProgress("Traduction en cours…")
        val translatedTitle = if (title.isBlank()) title else translator.translate(title).resultAsync()
        currentCoroutineContext().ensureActive()
        val translatedDescription = if (description.isBlank()) description else translator.translate(description).resultAsync()
        currentCoroutineContext().ensureActive()
        translatedTitle to translatedDescription
    } finally {
        translator.close()
    }
}

// Await the task before closing its client, then honour cancellation between operations.
private suspend fun <T> Task<T>.resultAsync(): T = suspendCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.resumeWithException(CancellationException()) }
}
