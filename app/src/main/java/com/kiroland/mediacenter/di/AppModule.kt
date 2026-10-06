package com.kiroland.mediacenter.di

import android.content.Context
import androidx.room.Room
import com.kiroland.mediacenter.data.library.db.LibraryDao
import com.kiroland.mediacenter.data.library.db.LibraryDatabase
import com.kiroland.mediacenter.data.library.db.MetadataDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Scope for work that must outlive a screen, e.g. a library scan. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LibraryDatabase =
        Room.databaseBuilder(context, LibraryDatabase::class.java, "library.db").build()

    @Provides
    fun provideLibraryDao(database: LibraryDatabase): LibraryDao = database.libraryDao()

    @Provides
    fun provideMetadataDao(database: LibraryDatabase): MetadataDao = database.metadataDao()

    @Provides
    fun provideNewsDao(database: LibraryDatabase): com.kiroland.mediacenter.data.library.db.NewsDao = database.newsDao()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        // Background work (scans, metadata) must never take the whole app down; log and carry on.
        val handler = CoroutineExceptionHandler { _, e -> Log.e("ApplicationScope", "Background task failed", e) }
        return CoroutineScope(SupervisorJob() + Dispatchers.Default + handler)
    }
}
