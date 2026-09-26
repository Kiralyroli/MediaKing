package com.kiroland.mediacenter.data.library

import android.util.Log
import com.kiroland.mediacenter.data.library.db.LibraryDao
import com.kiroland.mediacenter.data.library.db.MediaEntity
import com.kiroland.mediacenter.data.library.db.MediaKind
import com.kiroland.mediacenter.data.metadata.MetadataRepository
import com.kiroland.mediacenter.data.storage.StorageRepository
import com.kiroland.mediacenter.di.ApplicationScope
import com.kiroland.mediacenter.media.MediaType
import com.kiroland.mediacenter.media.parse.MediaNameParser
import com.kiroland.mediacenter.media.parse.ParsedMedia
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.kiroland.mediacenter.data.storage.FsEntry
import com.kiroland.mediacenter.data.storage.MediaFiles
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ScanState {
    data object Idle : ScanState
    data class Scanning(val folder: String, val found: Int) : ScanState
}

/**
 * Walks library folders and mirrors their video files into the database. Read-only on disk.
 * A folder that cannot be listed (drive unplugged, PC switched off) is skipped, never emptied.
 */
@Singleton
class LibraryScanner @Inject constructor(
    private val dao: LibraryDao,
    private val metadata: MetadataRepository,
    private val files: MediaFiles,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    fun scanAll() {
        scope.launch {
            mutex.withLock {
                dao.folders().forEach { scanSafely(it.path) }
                _state.value = ScanState.Idle
                metadata.enrichMissing()
            }
        }
    }

    fun scanFolder(root: String) {
        scope.launch {
            mutex.withLock {
                scanSafely(root)
                _state.value = ScanState.Idle
                metadata.enrichMissing()
            }
        }
    }

    /** One broken folder must not stop the others (or the metadata pass after them). */
    private suspend fun scanSafely(root: String) {
        try {
            scan(root)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Scan of $root failed", e)
        }
    }

    private suspend fun scan(root: String) = withContext(Dispatchers.IO) {
        val top = files.list(root)
        if (top == null) {
            Log.i(TAG, "Skipping unavailable library folder $root")
            return@withContext
        }
        _state.value = ScanState.Scanning(root, 0)

        val found = mutableListOf<Pair<FsEntry, List<String>>>()
        val complete = collect(root, top, emptyList(), found)

        val known = dao.pathsInRoot(root).associate { it.path to it.addedAt }
        val entities = found.map { (file, dirs) -> toEntity(file, dirs, root, known[file.path]) }
        dao.upsertMedia(entities)

        val present = entities.mapTo(HashSet()) { it.path }
        // A folder that could not be read halfway (network hiccup) must not look like deleted files.
        val gone = if (complete) known.keys.filterNot { it in present } else emptyList()
        if (!complete) Log.w(TAG, "Scan of $root was incomplete; nothing removed")
        if (gone.isNotEmpty()) gone.chunked(500).forEach { dao.deleteMedia(it) }
        Log.i(TAG, "Scanned $root: ${entities.size} items, ${gone.size} removed")
    }

    /** @return false if some subfolder could not be listed. */
    private fun collect(dir: String, children: List<FsEntry>, dirs: List<String>, into: MutableList<Pair<FsEntry, List<String>>>): Boolean {
        var complete = true
        if (dirs.size > MAX_DEPTH) return true
        for (child in children) {
            if (StorageRepository.isHidden(child.name)) continue
            if (child.isDirectory) {
                if (child.name.equals("sample", ignoreCase = true)) continue
                val grandchildren = files.list(child.path)
                if (grandchildren != null) {
                    complete = collect(child.path, grandchildren, dirs + child.name, into) && complete
                } else if (MediaFiles.isNetwork(child.path)) {
                    // Locally an unreadable folder stays unreadable (system folders); on a share it is a hiccup.
                    complete = false
                }
            } else if (MediaType.fromFileName(child.name) == MediaType.VIDEO && !isSample(child)) {
                into += child to dirs
                _state.value = ScanState.Scanning(dir, into.size)
            }
        }
        return complete
    }

    private fun toEntity(file: FsEntry, dirs: List<String>, root: String, knownAddedAt: Long?): MediaEntity {
        val lastModified = file.lastModified
        val base = MediaEntity(
            path = file.path,
            libraryRoot = root,
            fileName = file.name,
            sizeBytes = file.size,
            lastModified = lastModified,
            kind = MediaKind.MOVIE,
            title = "",
            year = null,
            seriesKey = null,
            season = null,
            episode = null,
            episodeEnd = null,
            addedAt = knownAddedAt ?: lastModified,
        )
        return when (val parsed = MediaNameParser.parse(file.name, dirs)) {
            is ParsedMedia.Movie -> base.copy(
                title = parsed.title,
                year = parsed.year,
                metadataKey = MetadataRepository.movieKey(parsed.title, parsed.year),
            )
            is ParsedMedia.Episode -> base.copy(
                kind = MediaKind.EPISODE,
                title = parsed.seriesTitle,
                year = parsed.year,
                seriesKey = MediaNameParser.seriesKey(parsed.seriesTitle),
                season = parsed.season,
                episode = parsed.episode,
                episodeEnd = parsed.episodeEnd,
                metadataKey = MetadataRepository.showKey(MediaNameParser.seriesKey(parsed.seriesTitle)),
            )
        }
    }

    private fun isSample(file: FsEntry): Boolean =
        SAMPLE.containsMatchIn(file.nameWithoutExtension) && file.size < 300L * 1024 * 1024

    private companion object {
        const val TAG = "LibraryScanner"
        const val MAX_DEPTH = 8
        val SAMPLE = Regex("""(?i)(^|[ ._-])sample([ ._-]|$)""")
    }
}
