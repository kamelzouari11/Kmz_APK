package fr.kmz.projects.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lots")
data class Lot(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nom: String,
    val dateCreation: Long = System.currentTimeMillis(),
    val isValidated: Boolean = false
)
