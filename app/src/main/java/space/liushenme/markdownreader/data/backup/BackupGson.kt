package space.liushenme.markdownreader.data.backup

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.util.Date

object BackupGson {
    val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Date::class.java, DateLongAdapter())
        .create()

    private class DateLongAdapter : TypeAdapter<Date>() {
        override fun write(out: JsonWriter, value: Date?) {
            if (value == null) {
                out.nullValue()
            } else {
                out.value(value.time)
            }
        }

        override fun read(reader: JsonReader): Date? {
            return when (reader.peek()) {
                JsonToken.NULL -> {
                    reader.nextNull()
                    null
                }
                JsonToken.NUMBER -> Date(reader.nextLong())
                JsonToken.STRING -> Date(reader.nextString().toLongOrNull() ?: 0L)
                else -> {
                    reader.skipValue()
                    null
                }
            }
        }
    }
}
