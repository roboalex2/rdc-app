package at.roboalex2.rdc.persistence.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "number_table")
data class NumberItemEntity(
    @PrimaryKey val number: String,
    val permissions: List<String>
) {
    fun toModel() = at.roboalex2.rdc.model.NumberItem(number, permissions)
}