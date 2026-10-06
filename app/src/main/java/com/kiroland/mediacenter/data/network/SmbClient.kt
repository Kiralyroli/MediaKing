package com.kiroland.mediacenter.data.network

import com.kiroland.mediacenter.R
import com.kiroland.mediacenter.util.AppLocale
import android.util.Log
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileStandardInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.security.bc.BCSecurityProvider
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import com.kiroland.mediacenter.data.storage.FsEntry
import java.io.Closeable
import java.io.IOException
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A share's sign-in was refused, or the share does not exist: retrying will not help. */
class SmbAccessException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Reads SMB shares. One session per share is kept open and reused; a broken one (PC asleep,
 * Wi-Fi hiccup) is dropped and reopened once before giving up. Read-only: nothing here writes.
 */
@Singleton
class SmbClient @Inject constructor(private val shares: NetworkShareRepository) {

    private val client = SMBClient(
        SmbConfig.builder()
            // Android's JCE has no MD4, which NTLM needs; smbj's BouncyCastle provider brings its own.
            .withSecurityProvider(BCSecurityProvider())
            .withTimeout(20, TimeUnit.SECONDS)
            .withSoTimeout(30, TimeUnit.SECONDS)
            .build(),
    )
    private val open = HashMap<String, DiskShare>()

    /** Entries of a folder; null if it cannot be listed right now (host offline, share gone). */
    fun list(dir: SmbPath): List<FsEntry>? = runCatching {
        withShare(dir) { share ->
            share.list(dir.smbPath)
                .filter { it.fileName != "." && it.fileName != ".." }
                .map { info ->
                    FsEntry(
                        path = dir.child(info.fileName).uri,
                        name = info.fileName,
                        isDirectory = info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L,
                        size = info.endOfFile,
                        lastModified = info.lastWriteTime.toEpochMillis(),
                    )
                }
        }
    }.onFailure { Log.w(TAG, "Cannot list $dir: ${it.message}") }.getOrNull()

    /** Opens a file for random-access reads. */
    fun open(path: SmbPath): SmbFile = withShare(path) { share ->
        val file = share.openFile(
            path.smbPath,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null,
        )
        SmbFile(file, file.getFileInformation(FileStandardInformation::class.java).endOfFile)
    }

    /** Signs in with [credentials] afresh and lists [dir]: the check before a share is saved. */
    fun test(credentials: NetworkShare, dir: SmbPath): Result<Int> = runCatching {
        connect(credentials).use { share -> share.list(dir.smbPath).count { it.fileName != "." && it.fileName != ".." } }
    }.recoverCatching { throw describe(it) }

    /** Forgets the open session of a share, e.g. after its password changed. */
    @Synchronized
    fun forget(host: String, share: String) {
        open.remove(key(host, share))?.let { runCatching { it.close() } }
    }

    private fun <T> withShare(path: SmbPath, block: (DiskShare) -> T): T {
        return try {
            block(share(path))
        } catch (e: SMBApiException) {
            throw describe(e)
        } catch (e: Exception) {
            if (e is SmbAccessException) throw e
            // A stale connection: reconnect once.
            forget(path.host, path.share)
            block(share(path))
        }
    }

    @Synchronized
    private fun share(path: SmbPath): DiskShare {
        val key = key(path.host, path.share)
        open[key]?.takeIf { it.isConnected }?.let { return it }
        val credentials = shares.find(path.host, path.share) ?: NetworkShare(path.host, path.share)
        return connect(credentials).also { open[key] = it }
    }

    private fun connect(credentials: NetworkShare): DiskShare {
        val connection = try {
            client.connect(credentials.host)
        } catch (e: IOException) {
            throw SmbAccessException(AppLocale.text(R.string.smb_unreachable, credentials.host), e)
        }
        val auth = if (credentials.username.isBlank()) {
            AuthenticationContext.guest()
        } else {
            AuthenticationContext(credentials.username, credentials.password.toCharArray(), credentials.domain.ifBlank { null })
        }
        return try {
            connection.authenticate(auth).connectShare(credentials.share) as? DiskShare
                ?: throw SmbAccessException(AppLocale.text(R.string.smb_not_disk_share, credentials.share))
        } catch (e: Exception) {
            runCatching { connection.close() }
            throw describe(e)
        }
    }

    private fun describe(e: Throwable): Exception {
        if (e is SmbAccessException) return e
        val status = (e as? SMBApiException)?.statusCode ?: (e.cause as? SMBApiException)?.statusCode
        return when (status?.let { java.lang.Long.toHexString(it) }) {
            "c000006d", "c0000022" -> SmbAccessException(AppLocale.text(R.string.smb_bad_login), e)
            "c00000cc" -> SmbAccessException(AppLocale.text(R.string.smb_no_share), e)
            "c0000034", "c000003a" -> SmbAccessException("Nincs ilyen mappa", e)
            else -> e as? Exception ?: Exception(e)
        }
    }

    private fun key(host: String, share: String) = "${host.lowercase()}/${share.lowercase()}"

    private companion object {
        const val TAG = "SmbClient"
    }
}

/** An open remote file; reads go straight to the server, so callers should read in large blocks. */
class SmbFile(private val file: File, val length: Long) : Closeable {
    fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int = file.read(buffer, position, offset, length)
    override fun close() = runCatching { file.close() }.let { }
}
