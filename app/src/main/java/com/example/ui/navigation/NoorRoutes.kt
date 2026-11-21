package com.example.ui.navigation

object NoorRoutes {
    const val HOME = "home"
    const val QURAN = "quran"
    const val AUDIO = "audio"
    const val LESSONS = "lessons"
    const val TASBIH = "tasbih"
    const val ADHKAR = "adhkar"
    const val MUSHAF_READER = "mushaf_reader/{page}"

    fun mushafReader(page: Int): String = "mushaf_reader/${page.coerceIn(1, 604)}"
}
