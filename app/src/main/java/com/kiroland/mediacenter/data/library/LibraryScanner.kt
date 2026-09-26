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
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ScanState {
    data object Idle : ScanState
    data class Scanning(val folder: String, val found: Int) : ScanState
}

/**
 * Walks library folders and mirrors their video files into the database. Read-only on disk.
 * A folder that cannot be listed (drive unplugged) is skipped, never emptied.
 */
@Singleton
class LibraryScanner @Inject constructor(
    private val dao: LibraryDao,
    private val metadata: MetadataRepository,
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
        val rootDir = File(root)
        if (rootDir.list() == null) {
            Log.i(TAG, "Skipping unavailable library folder $root")
            return@withContext
        }
        _state.value = ScanState.Scanning(root, 0)

        val files = mutableListOf<Pair<File, List<String>>>()
        collect(rootDir, emptyList(), files)

        val known = dao.pathsInRoot(root).associate { it.path to it.addedAt }
        val entities = files.map { (file, dirs) -> toEntity(file, dirs, root, known[file.absolutePath]) }
        dao.upsertMedia(entities)

        val present = entities.mapTo(HashSet()) { it.path }
        val gone = known.keys.filterNot { it in present }
        if (gone.isNotEmpty()) gone.chunked(500).forEach { dao.deleteMedia(it) }
        Log.i(TAG, "Scanned $root: ${entities.size} items, ${gone.size} removed")
    }

    private fun collect(dir: File, dirs: List<String>, into: MutableList<Pair<File, List<String>>>) {
        if (dirs.size > MAX_DEPTH) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (StorageRepository.isHidden(child.name)) continue
            if (child.isDirectory) {
                if (child.name.equals("sample", ignoreCase = true)) continue
                collect(child, dirs + child.name, into)
            } else if (MediaType.fromFileName(child.name) == MediaType.VIDEO && !isSample(child)) {
                into += child to dirs
                _state.value = ScanState.Scanning(dir.absolutePath, into.size)
            }
        }
    }

    private fun toEntity(file: File, dirs: List<String>, root: String, knownAddedAt: Long?): MediaEntity {
        val lastModified = file.lastModified()
        val base = MediaEntity(
            path = file.absolutePath,
            libraryRoot = root,
            fileName = file.name,
            sizeBytes = file.length(),
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

    private fun isSample(file: File): Boolean =
        SAMPLE.containsMatchIn(file.nameWithoutExtension) && file.length() < 300L * 1024 * 1024

    private companion object {
        const val TAG = "LibraryScanner"
        const val MAX_DEPTH = 8
        val SAMPLE = Regex("""(?i)(^|[ ._-])sample([ ._-]|$)""")
    }
}
