package at.roboalex2.rdc.persistence.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import at.roboalex2.rdc.persistence.entity.NumberItemEntity

@Dao
interface NumberDao {
    @Query("SELECT * FROM number_table")
    fun getAllNumbers(): Flow<List<NumberItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNumber(item: NumberItemEntity)

    @Query("DELETE FROM number_table WHERE number = :number")
    suspend fun deleteNumber(number: String)

    @Query("SELECT * FROM number_table WHERE number = :number LIMIT 1")
    suspend fun getNumber(number: String): NumberItemEntity?
}