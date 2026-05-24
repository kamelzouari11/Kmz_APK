package fr.kmz.projects.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "sous_lots",
    foreignKeys = [
        ForeignKey(
            entity = Lot::class,
            parentColumns = ["id"],
            childColumns = ["lotId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SousLot(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val lotId: Long,
    val nom: String,
    val dateCreation: Long = System.currentTimeMillis(),
    val isValidated: Boolean = false
)
