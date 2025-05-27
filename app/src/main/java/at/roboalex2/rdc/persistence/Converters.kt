package at.roboalex2.rdc.persistence


import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>?): String =
        Gson().toJson(value)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        Gson().fromJson(value, object: TypeToken<List<String>>(){}.type)
}