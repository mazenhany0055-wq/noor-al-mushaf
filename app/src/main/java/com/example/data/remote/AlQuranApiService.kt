package com.example.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

data class QuranPageResponseDto(
    val code: Int,
    val status: String,
    val data: QuranPageDataDto
)

data class QuranPageDataDto(
    val number: Int,
    val ayahs: List<QuranAyahDto>
)

data class QuranAyahDto(
    val number: Int,
    val text: String,
    val numberInSurah: Int,
    val juz: Int,
    val page: Int,
    val hizbQuarter: Int,
    val surah: QuranSurahHeaderDto
)

data class QuranSurahHeaderDto(
    val number: Int,
    val name: String,
    val englishName: String
)

data class SurahAudioResponseDto(
    val code: Int,
    val status: String,
    val data: SurahAudioDataDto
)

data class SurahAudioDataDto(
    val number: Int,
    val name: String,
    val numberOfAyahs: Int,
    val ayahs: List<SurahAudioAyahDto>
)

data class SurahAudioAyahDto(
    val number: Int,
    val audio: String? = null,
    val text: String,
    val numberInSurah: Int
)

interface AlQuranApiService {
    @GET("v1/page/{page}/quran-uthmani")
    suspend fun getPageUthmani(@Path("page") page: Int): QuranPageResponseDto

    @GET("v1/surah/{surah}/{edition}")
    suspend fun getSurahAudio(
        @Path("surah") surah: Int,
        @Path("edition") edition: String = "quran-uthmani"
    ): SurahAudioResponseDto

    companion object {
        private const val BASE_URL = "https://api.alquran.cloud/"

        fun create(): AlQuranApiService {
            val client = OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(AlQuranApiService::class.java)
        }
    }
}
