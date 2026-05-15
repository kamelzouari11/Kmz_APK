package fr.kmz.projects.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chapitres")
data class Chapitre(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nom: String
)
