package com.example.simpleiptv.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BackupUtils {
    suspend fun saveBackup(context: Context, json: String) {
        withContext(Dispatchers.IO) {
            try {
                val fileName = "simple_iptv_backup_${System.currentTimeMillis() / 1000}.json"
                val resolver = context.contentResolver

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues =
                            ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                                put(
                                        MediaStore.MediaColumns.RELATIVE_PATH,
                                        Environment.DIRECTORY_DOWNLOADS
                                )
                            }
                    val uri =
                            resolver.insert(
                                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                    contentValues
                            )
                    uri?.let {
                        resolver.openOutputStream(it)?.use { stream ->
                            stream.write(json.toByteArray())
                        }
                        showToast(context, "Sauvegardé : $fileName dans Téléchargements")
                    }
                            ?: throw Exception("Erreur création fichier MediaStore")
                } else {
                    val downloadsDir =
                            Environment.getExternalStoragePublicDirectory(
                                    Environment.DIRECTORY_DOWNLOADS
                            )
                    if (!downloadsDir.exists()) downloadsDir.mkdirs()
                    val file = File(downloadsDir, fileName)
                    file.writeText(json)
                    showToast(context, "Sauvegardé : $fileName dans Téléchargements")
                }
            } catch (e: Exception) {
                showToast(context, "Erreur export: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun showToast(context: Context, message: String) {
        withContext(Dispatchers.Main) { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
    }
}
