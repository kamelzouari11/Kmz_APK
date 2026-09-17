package com.example.myiptv.utils

import com.example.myiptv.BuildConfig

object GitHubConfig {
    val token: String
        get() = BuildConfig.GITHUB_TOKEN

    const val OWNER = "kamelzouari11"
    const val REPOSITORY = "Kmz_APK"
    const val FILE_PATH = "MySharedFolder/my_iptv_backup.json"
    const val EPG_FILE_PATH = "MySharedFolder/my_iptv_epg.db.gz"
}
