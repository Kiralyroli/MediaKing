package com.kiroland.mediacenter.data.storage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import com.kiroland.mediacenter.media.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Collator
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plain java.io.File access to mounted volumes. This works on the Android 10 target thanks to
 * requestLegacyExternalStorage; the TV has no DocumentsUI, so SAF tree pickers are not an option.
 */
@Singleton
class StorageRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val storageManager = context.getSystemService(StorageManager::class.java)
    private val collator = Collator.getInstance(Locale.forLanguageTag("hu-HU"))

    suspend fun volumes(): List<StorageVolumeInfo> = withContext(Dispatchers.IO) {
        val roots = buildList {
            add(Environment.getExternalStorageDirectory())
            File("/storage").listFiles()
                ?.filter { it.isDirectory && it.name !in IGNORED_STORAGE_DIRS }
                ?.let(::addAll)
        }
        roots.distinctBy { it.absolutePath }
            .map { root ->
                val volume = storageManager.getStorageVolume(root)
                val stat = runCatching { StatFs(root.path) }.getOrNull()
                StorageVolumeInfo(
                    name = volume?.getDescription(context) ?: root.name,
                    path = root.absolutePath,
                    removable = volume?.isRemovable ?: true,
                    totalBytes = stat?.totalBytes ?: 0L,
                    freeBytes = stat?.availableBytes ?: 0L,
                )
            }
            .sortedBy { !it.removable } // USB drives first: that is where the media lives.
    }

    suspend fun list(path: String): DirectoryListing = withContext(Dispatchers.IO) {
        val files = File(path).listFiles() ?: return@withContext DirectoryListing.Unreadable
        val entries = files
            .filterNot { isHidden(it.name) }
            .map { file ->
                val isDirectory = file.isDirectory
                FileEntry(
                    name = file.name,
                    path = file.absolutePath,
                    isDirectory = isDirectory,
                    sizeBytes = if (isDirectory) 0L else file.length(),
                    lastModified = file.lastModified(),
                    type = if (isDirectory) MediaType.OTHER else MediaType.fromFileName(file.name),
                )
            }
            .sortedWith(compareBy<FileEntry> { !it.isDirectory }.thenComparator { a, b -> collator.compare(a.name, b.name) })
        DirectoryListing.Success(entries)
    }

    /** Emits whenever a volume is mounted, unmounted or removed. */
    fun volumeChanges(): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        }
        context.registerReceiver(receiver, filter)
        awaitClose { context.unregisterReceiver(receiver) }
    }

    companion object {
        private val IGNORED_STORAGE_DIRS = setOf("emulated", "self")
        private val HIDDEN_NAMES = setOf("System Volume Information", "LOST.DIR")
        private val CHKDSK_DIR = Regex("""found\.\d{3}""")

        fun isHidden(name: String): Boolean =
            name.startsWith('.') || name.startsWith('$') || name in HIDDEN_NAMES || CHKDSK_DIR.matches(name)
    }
}
