package at.roboalex2.rdc.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import at.roboalex2.rdc.persistence.dao.CommandDao
import at.roboalex2.rdc.persistence.dao.NumberDao
import at.roboalex2.rdc.persistence.entity.CommandEntity
import at.roboalex2.rdc.persistence.entity.NumberItemEntity

@Database(
    entities = [CommandEntity::class, NumberItemEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun commandDao(): CommandDao
    abstract fun numberDao(): NumberDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rdc_app_db"
                )
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}