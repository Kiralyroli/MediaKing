package com.kiroland.mediacenter.data.storage

import com.kiroland.mediacenter.data.network.SmbClient
import com.kiroland.mediacenter.data.network.SmbPath
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class FsEntry(val path: String, val name: String, val isDirectory: Boolean, val size: Long, val lastModified: Long) {
    val nameWithoutExtension: String get() = name.substringBeforeLast('.')
    val extension: String get() = name.substringAfterLast('.', "")
}

/** Library files wherever they are: local paths through java.io, "smb://" paths through [SmbClient]. */
@Singleton
class MediaFiles @Inject constructor(private val smb: SmbClient) {

    /** A folder's entries; null if it cannot be listed (drive unplugged, host offline). */
    fun list(dir: String): List<FsEntry>? {
        SmbPath.parse(dir)?.let { return smb.list(it) }
        return File(dir).listFiles()?.map { FsEntry(it.absolutePath, it.name, it.isDirectory, it.length(), it.lastModified()) }
    }

    fun readBytes(path: String, limit: Long): ByteArray {
        val smbPath = SmbPath.parse(path) ?: return File(path).also {
            if (it.length() > limit) throw IOException("Too large: $path")
        }.readBytes()
        return smb.open(smbPath).use { file ->
            if (file.length > limit) throw IOException("Too large: $path")
            val out = ByteArrayOutputStream(file.length.toInt())
            val buffer = ByteArray(64 * 1024)
            var position = 0L
            while (position < file.length) {
                val n = file.read(position, buffer, 0, buffer.size)
                if (n <= 0) break
                out.write(buffer, 0, n)
                position += n
            }
            out.toByteArray()
        }
    }

    /** Up to [length] bytes at [offset]; fewer at the end of the file. */
    fun readRange(path: String, offset: Long, length: Int): ByteArray {
        val buffer = ByteArray(length)
        var filled = 0
        val smbPath = SmbPath.parse(path)
        if (smbPath == null) {
            java.io.RandomAccessFile(path, "r").use { file ->
                file.seek(offset)
                while (filled < length) {
                    val n = file.read(buffer, filled, length - filled)
                    if (n <= 0) break
                    filled += n
                }
            }
        } else {
            smb.open(smbPath).use { file ->
                while (filled < length && offset + filled < file.length) {
                    val n = file.read(offset + filled, buffer, filled, length - filled)
                    if (n <= 0) break
                    filled += n
                }
            }
        }
        return if (filled == length) buffer else buffer.copyOf(filled)
    }

    companion object {
        fun isNetwork(path: String) = SmbPath.isSmb(path)
        fun parentOf(path: String): String = path.trimEnd('/').substringBeforeLast('/')
        fun nameOf(path: String): String = path.trimEnd('/').substringAfterLast('/')
    }
}
