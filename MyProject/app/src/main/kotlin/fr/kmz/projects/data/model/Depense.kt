package fr.kmz.projects.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "depenses",
    foreignKeys = [
        ForeignKey(
            entity = Chapitre::class,
            parentColumns = ["id"],
            childColumns = ["chapitreId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Beneficiaire::class,
            parentColumns = ["id"],
            childColumns = ["beneficiaireId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        androidx.room.Index(value = ["chapitreId"]),
        androidx.room.Index(value = ["beneficiaireId"])
    ]
)
data class Depense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: Long = System.currentTimeMillis(),
    val chapitreId: Long,
    val beneficiaireId: Long,
    val montant: Long,
    val nature: String
)
