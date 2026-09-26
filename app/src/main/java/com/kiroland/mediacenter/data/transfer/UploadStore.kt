package com.kiroland.mediacenter.data.transfer

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

sealed interface UploadResult {
    /** Chunk stored; [done] when the file is complete and renamed into place. */
    data class Accepted(val received: Long, val done: Boolean, val file: File) : UploadResult
    /** The client's offset does not match what is on disk; it should continue from [received]. */
    data class OffsetMismatch(val received: Long) : UploadResult
    data object AlreadyExists : UploadResult
    data class NotEnoughSpace(val needed: Long, val available: Long) : UploadResult
    data class Rejected(val reason: String) : UploadResult
}

data class UploadStatus(val received: Long, val exists: Boolean)

data class DeletionPreview(val isDirectory: Boolean, val files: Int, val bytes: Long)

/**
 * Resumable chunked uploads straight to their destination folder.
 *
 * A file grows in a hidden sibling ".<name>.mcpart" (the library scanner skips dot-files) and is renamed
 * when complete, so a half-uploaded movie never shows up in the library and an interrupted upload
 * continues from the size of the part file. Only folders under [allowedRoots] are writable.
 *
 * @param reserveBytes free space always left on the drive
 */
class UploadStore(
    private val allowedRoots: () -> List<File>,
    /** Folders that must survive deletes, with everything above them (the library folders). */
    private val protectedFolders: () -> List<File> = { emptyList() },
    private val reserveBytes: Long = 256L * 1024 * 1024,
    private val freeSpace: (File) -> Long = { it.usableSpace },
) {

    /** Resolves [dir] plus the optional sub-folders of [relativePath], or null if outside the allowed roots. */
    fun resolveDir(dir: String, relativeDirs: List<String> = emptyList()): File? {
        if (relativeDirs.any { !isSafeName(it) }) return null
        val target = relativeDirs.fold(File(dir)) { parent, name -> File(parent, name) }
        val canonical = runCatching { target.canonicalFile }.getOrNull() ?: return null
        val inside = allowedRoots().any { root ->
            val rootPath = runCatching { root.canonicalPath }.getOrNull() ?: return@any false
            canonical.path == rootPath || canonical.path.startsWith(rootPath + File.separator)
        }
        return canonical.takeIf { inside }
    }

    fun status(dir: File, name: String): UploadStatus =
        UploadStatus(received = partFile(dir, name).takeIf { it.exists() }?.length() ?: 0L, exists = File(dir, name).exists())

    /**
     * Appends one chunk read from [input] ([length] bytes) at [offset] of a [total]-byte file.
     * [onBytes] reports progress while copying.
     */
    fun append(
        dir: File,
        name: String,
        offset: Long,
        total: Long,
        length: Long,
        input: InputStream,
        onBytes: (Long) -> Unit = {},
    ): UploadResult {
        if (!isSafeName(name)) return UploadResult.Rejected("Érvénytelen fájlnév: $name")
        if (total < 0 || offset < 0 || length < 0 || offset + length > total) return UploadResult.Rejected("Hibás méretadatok")
        blockingFile(dir)?.let { return UploadResult.Rejected("Mappa helyett fájl van ezen a néven: ${it.name}") }
        val target = File(dir, name)
        if (target.exists()) return UploadResult.AlreadyExists

        val part = partFile(dir, name)
        val received = if (part.exists()) part.length() else 0L
        if (received != offset) return UploadResult.OffsetMismatch(received)

        if (offset == 0L) {
            if (!dir.isDirectory && !dir.mkdirs()) return UploadResult.Rejected("Nem sikerült létrehozni a mappát: ${dir.name}")
            val available = freeSpace(dir) - reserveBytes
            if (total > available) return UploadResult.NotEnoughSpace(total, available.coerceAtLeast(0))
        }

        val written = try {
            FileOutputStream(part, true).use { out ->
                val buffer = ByteArray(BUFFER_SIZE)
                var remaining = length
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    remaining -= read
                    onBytes(read.toLong())
                }
                // Resuming trusts the part file's size, so make sure what it claims is really on disk.
                out.fd.sync()
                length - remaining
            }
        } catch (e: IOException) {
            return UploadResult.Rejected("Írási hiba: ${e.message}")
        }
        if (written != length) return UploadResult.OffsetMismatch(part.length())

        val now = part.length()
        if (now < total) return UploadResult.Accepted(now, done = false, file = target)
        if (!part.renameTo(target)) return UploadResult.Rejected("Nem sikerült átnevezni: ${target.name}")
        return UploadResult.Accepted(total, done = true, file = target)
    }

    /** Drops a half-uploaded file (the client cancelled it). */
    fun discard(dir: File, name: String): Boolean = isSafeName(name) && partFile(dir, name).delete()

    /** What deleting [name] in [dir] would remove; null if it does not exist or may not be deleted. */
    fun inspect(dir: File, name: String): DeletionPreview? {
        val target = deletable(dir, name) ?: return null
        if (target.isFile) return DeletionPreview(isDirectory = false, files = 1, bytes = target.length())
        val files = target.walkBottomUp().filter { it.isFile }.toList()
        return DeletionPreview(isDirectory = true, files = files.size, bytes = files.sumOf { it.length() })
    }

    /**
     * Permanently deletes a file, or a folder with everything in it. Android has no recycle bin, so
     * callers confirm with the user first. Allowed roots themselves can never be deleted.
     */
    fun delete(dir: File, name: String): Boolean {
        val target = deletable(dir, name) ?: return false
        return if (target.isDirectory) target.deleteRecursively() else target.delete()
    }

    private fun deletable(dir: File, name: String): File? {
        if (!isSafeName(name)) return null
        val target = runCatching { File(dir, name).canonicalFile }.getOrNull() ?: return null
        if (!target.exists()) return null
        val roots = allowedRoots().mapNotNull { runCatching { it.canonicalPath }.getOrNull() }
        if (target.path in roots) return null
        val protected = protectedFolders().mapNotNull { runCatching { it.canonicalPath }.getOrNull() }
        if (protected.any { it == target.path || it.startsWith(target.path + File.separator) }) return null
        val inside = roots.any { target.path.startsWith(it + File.separator) }
        return target.takeIf { inside }
    }

    fun createFolder(dir: File, name: String): File? {
        if (!isSafeName(name)) return null
        return File(dir, name).takeIf { it.isDirectory || it.mkdirs() }
    }

    private fun partFile(dir: File, name: String) = File(dir, ".$name$PART_SUFFIX")

    /** The nearest existing path element of [dir] that is a file, i.e. where a folder should be. */
    private fun blockingFile(dir: File): File? =
        generateSequence(dir) { it.parentFile }.firstOrNull { it.exists() }?.takeIf { !it.isDirectory }

    companion object {
        const val PART_SUFFIX = ".mcpart"
        private const val BUFFER_SIZE = 1024 * 1024

        /** One path segment that is valid on NTFS/FAT and cannot climb out of its folder. */
        fun isSafeName(name: String): Boolean =
            name.isNotBlank() && name != "." && name != ".." && name.length <= 255 &&
                name.none { it < ' ' || it in FORBIDDEN } && !name.endsWith(' ') && !name.endsWith('.')

        private const val FORBIDDEN = "/\\<>:\"|?*"
    }
}
