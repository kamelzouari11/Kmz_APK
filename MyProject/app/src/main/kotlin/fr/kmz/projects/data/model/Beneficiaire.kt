package fr.kmz.projects.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "beneficiaires")
data class Beneficiaire(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nom: String
)
