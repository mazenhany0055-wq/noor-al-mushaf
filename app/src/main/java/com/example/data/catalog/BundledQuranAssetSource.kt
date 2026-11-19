package com.example.data.catalog

import android.content.Context
import android.util.JsonReader
import com.example.domain.model.MushafAyah
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

object BundledQuranAssetSource {

    @Volatile
    private var cachedPages: Map<Int, List<MushafAyah>>? = null

    @Volatile
    private var checkedAvailability: Boolean? = null

    fun isAvailable(context: Context): Boolean {
        checkedAvailability?.let { return it }
        val exists = try {
            val audioOrRoot = context.assets.list("")?.toSet() ?: emptySet()
            audioOrRoot.contains("quran_pages.json.gz") || audioOrRoot.contains("quran_pages.json")
        } catch (_: Exception) {
            false
        }
        checkedAvailability = exists
        return exists
    }

    fun getPage(context: Context, page: Int): List<MushafAyah>? {
        val map = getOrLoadAll(context) ?: return null
        return map[page]
    }

    private fun getOrLoadAll(context: Context): Map<Int, List<MushafAyah>>? {
        cachedPages?.let { return it }
        synchronized(this) {
            cachedPages?.let { return it }
            val loaded = loadFromAssets(context)
            if (loaded != null) {
                cachedPages = loaded
                checkedAvailability = true
            } else {
                checkedAvailability = false
            }
            return loaded
        }
    }

    private fun loadFromAssets(context: Context): Map<Int, List<MushafAyah>>? {
        val stream = try {
            GZIPInputStream(context.assets.open("quran_pages.json.gz"))
        } catch (_: Exception) {
            try {
                context.assets.open("quran_pages.json")
            } catch (_: Exception) {
                return null
            }
        }

        val byPage = HashMap<Int, MutableList<MushafAyah>>(604)
        stream.use { rawStream ->
            JsonReader(InputStreamReader(rawStream, Charsets.UTF_8)).use { reader ->
                reader.beginArray()
                while (reader.hasNext()) {
                    var g = 0
                    var s = 1
                    var sn = ""
                    var a = 1
                    var t = ""
                    var p = 1
                    var j = 1
                    var h = 1
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "g" -> g = reader.nextInt()
                            "s" -> s = reader.nextInt()
                            "sn" -> sn = reader.nextString()
                            "a" -> a = reader.nextInt()
                            "t" -> t = reader.nextString()
                            "p" -> p = reader.nextInt()
                            "j" -> j = reader.nextInt()
                            "h" -> h = reader.nextInt()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    val ayah = MushafAyah(
                        globalNumber = g,
                        numberInSurah = a,
                        text = t,
                        surahNumber = s,
                        surahName = sn,
                        page = p,
                        juz = j,
                        hizbQuarter = h
                    )
                    byPage.getOrPut(p) { mutableListOf() }.add(ayah)
                }
                reader.endArray()
            }
        }
        return if (byPage.isNotEmpty()) byPage else null
    }
}
