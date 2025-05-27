package at.roboalex2.rdc.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import at.roboalex2.rdc.persistence.entity.CommandEntity

@Dao
interface CommandDao {
    @Query("SELECT * FROM commands_table ORDER BY dateTime DESC")
    fun getAllCommands(): Flow<List<CommandEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCommand(cmd: CommandEntity)
}