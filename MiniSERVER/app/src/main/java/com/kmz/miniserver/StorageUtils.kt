
package com.kmz.miniserver

import android.content.Context
import android.media.MediaScannerConnection
import android.util.Log
import java.io.File

object StorageUtils {
    
    fun ensureBaseDirectory(): Boolean {
        val dir = AppConstants.BASE_DIR
        return if (!dir.exists()) {
            dir.mkdirs()
        } else {
            true
        }
    }

    fun saveFile(fileName: String, bytes: ByteArray, context: Context): Boolean {
        return try {
            ensureBaseDirectory()
            val file = File(AppConstants.BASE_DIR, fileName)
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
            
            // Trigger Media Scanner to make it visible in file managers
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            true
        } catch (e: Exception) {
            Log.e("MiniServer", "Error saving file: ${e.message}")
            false
        }
    }
    
    fun getFilesList(): List<String> {
        return AppConstants.BASE_DIR.listFiles()?.map { it.name } ?: emptyList()
    }
}
