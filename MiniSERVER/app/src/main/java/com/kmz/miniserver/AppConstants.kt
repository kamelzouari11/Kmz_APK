package com.kmz.miniserver

import android.os.Environment
import java.io.File

object AppConstants {
    const val PORT = 8888
    const val FOLDER_NAME = "MySharedFolder"
    const val CHANNEL_ID = "HttpServerChannel"
    const val NOTIFICATION_ID = 1

    val DOWNLOAD_DIR: File =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val BASE_DIR = File(DOWNLOAD_DIR, FOLDER_NAME)
}
