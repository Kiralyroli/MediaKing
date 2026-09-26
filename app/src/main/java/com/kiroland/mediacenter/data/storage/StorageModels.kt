package com.kiroland.mediacenter.data.storage

import com.kiroland.mediacenter.media.MediaType

data class StorageVolumeInfo(
    val name: String,
    val path: String,
    val removable: Boolean,
    val totalBytes: Long,
    val freeBytes: Long,
)

data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val type: MediaType,
)

sealed interface DirectoryListing {
    data class Success(val entries: List<FileEntry>) : DirectoryListing

    /** listFiles() returned null: missing permission, or the volume went away. */
    data object Unreadable : DirectoryListing
}
