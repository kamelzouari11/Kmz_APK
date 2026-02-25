package com.kmz.taskmanager.util

object GitHubConfig {
    const val TOKEN = "" // ADD YOUR TOKEN LOCALLY, DO NOT COMMIT
    const val OWNER = "kamelzouari11"
    const val REPO = "Kmz_APK"
    const val FILE_PATH = "MySharedFolder/task_manager_backup.json"
}
 *
 * Pour configurer : ajouter dans /media/kamel/DATA/KmzAPK/local.properties :
 *   github.token=ghp_VOTRE_TOKEN_ICI
 */
object GitHubConfig {
    val TOKEN: String get() = BuildConfig.GITHUB_TOKEN
    const val OWNER = "kamelzouari11"
    const val REPO = "Kmz_APK"
    const val FILE_PATH = "MySharedFolder/task_manager_backup.json"
}
