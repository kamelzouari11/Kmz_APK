package com.example.simpleradio.utils

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.simpleradio.data.local.entities.RadioStationEntity

fun RadioStationEntity.toMediaItem(): MediaItem {
    // IMPORTANT: On ne met PAS le nom de la radio dans Title/Artist
    // car cela impacterait le listener de métadonnées.
    // On utilise uniquement Station et Album pour le système.
    val meta =
            MediaMetadata.Builder()
                    .setStation(name)
                    .setAlbumTitle(name)
                    .setArtworkUri(favicon?.toUri())
                    .build()
    return MediaItem.Builder().setUri(url).setMediaId(stationuuid).setMediaMetadata(meta).build()
}
