package com.kiroland.mediacenter.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import com.kiroland.mediacenter.data.network.SmbClient
import com.kiroland.mediacenter.data.network.SmbFile
import com.kiroland.mediacenter.data.network.SmbPath
import java.io.IOException

/** The player's Uri for a library path: files as file://, shares as smb:// (percent-encoded). */
fun playbackUri(path: String): Uri {
    val smb = SmbPath.parse(path) ?: return Uri.fromFile(java.io.File(path))
    return Uri.Builder().scheme("smb").authority(smb.host).appendPath(smb.share).apply {
        smb.path.split('/').filter { it.isNotEmpty() }.forEach { appendPath(it) }
    }.build()
}

/** Reads smb:// Uris in large blocks: every SMB read is a round trip, the extractors ask for a few KB at a time. */
@UnstableApi
class SmbDataSource(private val smb: SmbClient) : BaseDataSource(/* isNetwork = */ true) {
    private var uri: Uri? = null
    private var file: SmbFile? = null
    private var opened = false
    private var position = 0L
    private var remaining = 0L
    private val block = ByteArray(BLOCK_SIZE)
    private var blockStart = 0L
    private var blockLength = 0

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val segments = dataSpec.uri.pathSegments
        val host = dataSpec.uri.host
        if (host == null || segments.isEmpty()) throw DataSourceException(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
        val path = SmbPath(host, segments.first(), segments.drop(1).joinToString("/"))
        val opened = try {
            smb.open(path)
        } catch (e: Exception) {
            throw DataSourceException(IOException("${path.uri}: ${e.message}", e), PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
        }
        file = opened
        position = dataSpec.position
        if (position > opened.length) throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else opened.length - position
        blockLength = 0
        this.opened = true
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        if (position < blockStart || position >= blockStart + blockLength) fill()
        if (blockLength == 0) return C.RESULT_END_OF_INPUT
        val available = blockStart + blockLength - position
        val n = minOf(length.toLong(), remaining, available).toInt()
        System.arraycopy(block, (position - blockStart).toInt(), buffer, offset, n)
        position += n
        remaining -= n
        bytesTransferred(n)
        return n
    }

    private fun fill() {
        val source = file ?: throw IOException("Not open")
        val want = minOf(BLOCK_SIZE.toLong(), source.length - position).toInt()
        blockStart = position
        blockLength = 0
        try {
            while (blockLength < want) {
                val n = source.read(position + blockLength, block, blockLength, want - blockLength)
                if (n <= 0) break
                blockLength += n
            }
        } catch (e: Exception) {
            // smbj reports network trouble as runtime exceptions; the player only retries IOExceptions.
            throw if (e is IOException) e else IOException(e.message, e)
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        file?.close()
        file = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    private companion object {
        const val BLOCK_SIZE = 1024 * 1024
    }
}

/** Sends smb:// to [SmbDataSource] and everything else (files, http) to the default source. */
@UnstableApi
class LibraryDataSourceFactory(context: Context, private val smb: SmbClient) : DataSource.Factory {
    private val default = DefaultDataSource.Factory(context)

    override fun createDataSource(): DataSource = Routing(default.createDataSource(), SmbDataSource(smb))

    private class Routing(private val default: DataSource, private val smb: DataSource) : DataSource {
        private var current: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            default.addTransferListener(transferListener)
            smb.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val source = if (dataSpec.uri.scheme.equals("smb", ignoreCase = true)) smb else default
            current = source
            return source.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            current?.read(buffer, offset, length) ?: throw IOException("Not open")

        override fun getUri(): Uri? = current?.uri

        override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders.orEmpty()

        override fun close() {
            try {
                current?.close()
            } finally {
                current = null
            }
        }
    }
}
