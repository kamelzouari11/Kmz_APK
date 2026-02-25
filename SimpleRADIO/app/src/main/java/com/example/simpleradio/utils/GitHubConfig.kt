package com.example.simpleradio.utils

import com.example.simpleradio.BuildConfig

/**
 * Configuration GitHub. Le TOKEN est injecté depuis local.properties via BuildConfig au moment du
 * build. Il n'est JAMAIS stocké dans le code source ni commité.
 *
 * Pour configurer : ajouter dans local.properties (fichier ignoré par git) :
 * github.token=ghp_VOTRE_TOKEN_ICI
 */
object GitHubConfig {
    val TOKEN: String
        get() = BuildConfig.GITHUB_TOKEN
    const val OWNER = "kamelzouari11"
    const val REPO = "Kmz_APK"
    const val FILE_PATH = "MySharedFolder/simple_radio_backup.json"
}
