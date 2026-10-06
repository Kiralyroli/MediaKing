package com.kiroland.mediacenter.di

import com.kiroland.mediacenter.data.metadata.TmdbCredentials
import com.kiroland.mediacenter.util.AppLocale
import com.kiroland.mediacenter.data.metadata.tmdb.TmdbApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideTmdbApi(okHttp: OkHttpClient, credentials: TmdbCredentials): TmdbApi {
        // The token only goes to api.themoviedb.org; image requests use the shared client without it.
        val tmdbClient = okHttp.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request()
                // "app" stands for the app's current language, which can change while it runs.
                val url = if (request.url.queryParameter("language") == TmdbApi.LANGUAGE) {
                    request.url.newBuilder().setQueryParameter("language", AppLocale.tmdbLanguage).build()
                } else {
                    request.url
                }
                chain.proceed(
                    request.newBuilder().url(url)
                        .header("Authorization", "Bearer ${credentials.token}")
                        .header("Accept", "application/json")
                        .build(),
                )
            }
            .build()
        val json = Json { ignoreUnknownKeys = true }
        return Retrofit.Builder()
            .baseUrl(TmdbApi.BASE_URL)
            .client(tmdbClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TmdbApi::class.java)
    }
}
