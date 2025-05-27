package at.roboalex2.rdc.persistence.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "commands_table")
data class CommandEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dateTime: String,
    val issuer: String,
    val type: String
) {
    fun toModel() = at.roboalex2.rdc.model.Command(dateTime, issuer, type)
}