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
        SeasonMetadataEntity::class,
        SegmentCacheEntity::class,
        WatchlistEntity::class,
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5)],
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun metadataDao(): MetadataDao
}
