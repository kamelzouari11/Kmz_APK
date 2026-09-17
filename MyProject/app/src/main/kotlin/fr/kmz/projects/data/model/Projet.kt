package fr.kmz.projects.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projets")
data class Projet(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nom: String
)
