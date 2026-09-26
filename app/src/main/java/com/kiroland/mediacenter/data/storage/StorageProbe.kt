package com.kiroland.mediacenter.data.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

data class WriteTestResult(
    val location: String,
    val success: Boolean,
    val message: String,
    val writeMbPerSec: Double? = null,
)

data class ProbeReport(
    val volumePath: String,
    val readable: Boolean,
    val visibleEntries: Int,
    /** Writing to an arbitrary folder of the volume: what the Wi-Fi upload needs. */
    val directWrite: WriteTestResult,
    /** Fallback: the app-specific folder, writable without permissions but wiped on uninstall. */
    val appDirWrite: WriteTestResult?,
)

/**
 * Checks what the app may actually do on a volume. Only runs on explicit user request:
 * it writes a small temporary file (deleted right away), and USB drives can be fragile.
 */
@Singleton
class StorageProbe @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun probe(volumePath: String): ProbeReport = withContext(Dispatchers.IO) {
        val root = File(volumePath)
        val entries = root.list()
        val appDir = context.getExternalFilesDirs(null)
            .filterNotNull()
            .firstOrNull { it.absolutePath.startsWith(volumePath) }
        ProbeReport(
            volumePath = volumePath,
            readable = entries != null,
            visibleEntries = entries?.size ?: 0,
            directWrite = writeTest(root),
            appDirWrite = appDir?.let(::writeTest),
        )
    }

    private fun writeTest(dir: File): WriteTestResult {
        val file = File(dir, PROBE_FILE_NAME)
        val payload = Random.nextBytes(PROBE_SIZE_BYTES)
        return try {
            val started = System.nanoTime()
            FileOutputStream(file).use { out ->
                out.write(payload)
                out.fd.sync()
            }
            val seconds = (System.nanoTime() - started) / 1e9
            val readBack = file.readBytes()
            if (!readBack.contentEquals(payload)) {
                WriteTestResult(dir.absolutePath, false, "A visszaolvasott adat eltér")
            } else {
                WriteTestResult(
                    location = dir.absolutePath,
                    success = true,
                    message = "Írás és visszaolvasás rendben",
                    writeMbPerSec = PROBE_SIZE_BYTES / 1_048_576.0 / seconds,
                )
            }
        } catch (e: Exception) {
            WriteTestResult(dir.absolutePath, false, e.message ?: e.javaClass.simpleName)
        } finally {
            file.delete()
        }
    }

    private companion object {
        const val PROBE_FILE_NAME = ".mediacenter-probe.tmp"
        const val PROBE_SIZE_BYTES = 4 * 1024 * 1024
    }
}
