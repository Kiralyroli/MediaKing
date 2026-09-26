package com.kiroland.mediacenter.data.library.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        MediaEntity::class,
        WatchProgressEntity::class,
        LibraryFolderEntity::class,
        MetadataEntity::class,
        EpisodeMetadataEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun metadataDao(): MetadataDao
}
