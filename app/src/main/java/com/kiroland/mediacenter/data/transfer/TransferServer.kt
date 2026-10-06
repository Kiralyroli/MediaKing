package com.kiroland.mediacenter.data.transfer

import com.kiroland.mediacenter.R
import com.kiroland.mediacenter.util.AppLocale
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.discard
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class RootDto(val path: String, val name: String, val kind: String, val freeBytes: Long, val totalBytes: Long)

@Serializable
data class InfoDto(val device: String, val roots: List<RootDto>)

@Serializable
data class EntryDto(val name: String, val size: Long)

@Serializable
data class PartialDto(val name: String, val received: Long)

@Serializable
data class ListingDto(
    val path: String,
    val dirs: List<String>,
    val files: List<EntryDto>,
    val partials: List<PartialDto>,
    val freeBytes: Long,
)

@Serializable
data class StatusDto(val received: Long, val exists: Boolean)

@Serializable
data class ChunkDto(val received: Long, val done: Boolean)

@Serializable
data class PreviewDto(val isDirectory: Boolean, val files: Int, val bytes: Long)

@Serializable
data class AddonDto(val id: String, val name: String, val version: Int, val description: String? = null, val channels: Int)

@Serializable
data class ErrorDto(val error: String, val received: Long? = null, val retryAfterMs: Long? = null)

/** [source]: "user" (given on this page), "build" (built into the app) or null (no metadata). */
@Serializable
data class TmdbDto(val configured: Boolean, val source: String? = null)

/** A network folder to add; an empty password keeps the one already saved for the share. */
@Serializable
data class NetworkFolderRequest(
    val host: String,
    val share: String,
    val path: String = "",
    val username: String = "",
    val password: String = "",
    val domain: String = "",
)

@Serializable
data class NetworkFolderDto(val root: String, val username: String = "", val items: Int = 0)

/** What the server needs from the app; implemented by [TransferRepository]. */
interface TransferHost {
    val pairingCode: String
    val deviceName: String
    fun roots(): List<RootDto>
    fun onChunk(file: File, total: Long, receivedBefore: Long, bytes: Long)
    fun onFileDone(file: File)
    fun onDeleted(file: File)

    fun addons(): List<AddonDto>
    fun installAddon(text: String): Result<AddonDto>
    suspend fun installAddonFromUrl(url: String): Result<AddonDto>
    fun removeAddon(id: String): Boolean

    fun tmdbStatus(): TmdbDto
    suspend fun setTmdbToken(token: String): Result<TmdbDto>
    fun clearTmdbToken(): TmdbDto

    suspend fun networkFolders(): List<NetworkFolderDto>
    suspend fun addNetworkFolder(request: NetworkFolderRequest): Result<NetworkFolderDto>
    suspend fun removeNetworkFolder(root: String): Boolean
}

/**
 * The HTTP side of Wi-Fi uploads: serves the web page and a small JSON API.
 * Every /api call must carry the pairing code in the X-Pairing-Code header.
 */
private const val MAX_ADDON_BYTES = 256L * 1024
private const val MAX_TOKEN_BYTES = 4L * 1024

class TransferServer(
    private val host: TransferHost,
    private val store: UploadStore,
    /** The upload page (assets/web/index.html in the app, a stub in tests). */
    private val page: () -> ByteArray,
) {
    private val json = Json { encodeDefaults = false }
    private val guard = PairingGuard()
    private var server: EmbeddedServer<*, *>? = null

    /** Starts on the first free port from [ports]; returns the port or throws if none could be bound. */
    fun start(ports: IntRange = 8080..8090): Int {
        var lastError: Exception? = null
        for (port in ports) {
            try {
                val candidate = embeddedServer(CIO, port = port, host = "0.0.0.0") { routing { routes() } }
                candidate.start(wait = false)
                server = candidate
                return port
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw IllegalStateException("Nincs szabad port (${ports.first}–${ports.last})", lastError)
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 500, timeoutMillis = 2_000)
        server = null
    }

    private fun io.ktor.server.routing.Routing.routes() {
        get("/") {
            val html = withContext(Dispatchers.IO) { page() }
            call.response.header("Cache-Control", "no-store")
            call.respondBytes(html, ContentType.Text.Html.withParameter("charset", "utf-8"))
        }

        get("/api/info") {
            if (!authorized(call)) return@get
            val roots = withContext(Dispatchers.IO) { host.roots() }
            call.respondJson(InfoDto(host.deviceName, roots))
        }

        get("/api/list") {
            if (!authorized(call)) return@get
            val dir = targetDir(call) ?: return@get
            val listing = withContext(Dispatchers.IO) { list(dir) }
            call.respondJson(listing)
        }

        post("/api/mkdir") {
            if (!authorized(call)) return@post
            val dir = targetDir(call) ?: return@post
            val name = call.parameters["name"].orEmpty().trim()
            val created = withContext(Dispatchers.IO) { store.createFolder(dir, name) }
            if (created == null) call.respondError(HttpStatusCode.BadRequest, AppLocale.text(R.string.server_bad_folder_name))
            else call.respondJson(EntryDto(created.name, 0))
        }

        get("/api/upload/status") {
            if (!authorized(call)) return@get
            val dir = targetDir(call) ?: return@get
            val name = call.parameters["name"].orEmpty()
            val status = withContext(Dispatchers.IO) { store.status(dir, name) }
            call.respondJson(StatusDto(status.received, status.exists))
        }

        put("/api/upload") {
            // Any refusal reads the rest of the chunk first: answering while the browser is still sending
            // resets the connection, and the page would see a network error instead of our reply.
            val body = call.receiveChannel()
            if (!authorized(call, body)) return@put
            val dir = targetDir(call, body) ?: return@put
            val name = call.parameters["name"].orEmpty()
            val offset = call.parameters["offset"]?.toLongOrNull()
            val total = call.parameters["total"]?.toLongOrNull()
            val length = call.request.contentLength()
            if (offset == null || total == null || length == null) {
                body.discard()
                call.respondError(HttpStatusCode.BadRequest, AppLocale.text(R.string.server_missing_headers))
                return@put
            }
            val input = body.toInputStream()
            val file = File(dir, name)
            var sent = 0L
            val result = withContext(Dispatchers.IO) {
                store.append(dir, name, offset, total, length, input) { bytes ->
                    host.onChunk(file, total, offset + sent, bytes)
                    sent += bytes
                }
            }
            if (result !is UploadResult.Accepted) body.discard()
            when (result) {
                is UploadResult.Accepted -> {
                    if (result.done) host.onFileDone(result.file)
                    call.respondJson(ChunkDto(result.received, result.done))
                }
                is UploadResult.OffsetMismatch ->
                    call.respondError(HttpStatusCode.Conflict, AppLocale.text(R.string.server_offset_mismatch), received = result.received)
                UploadResult.AlreadyExists ->
                    call.respondError(HttpStatusCode.Conflict, AppLocale.text(R.string.server_file_exists))
                is UploadResult.NotEnoughSpace ->
                    call.respondError(HttpStatusCode.InsufficientStorage, AppLocale.text(R.string.server_no_space))
                is UploadResult.Rejected -> call.respondError(HttpStatusCode.BadRequest, result.reason)
            }
        }

        get("/api/stat") {
            if (!authorized(call)) return@get
            val dir = targetDir(call) ?: return@get
            val preview = withContext(Dispatchers.IO) { store.inspect(dir, call.parameters["name"].orEmpty()) }
            if (preview == null) call.respondError(HttpStatusCode.NotFound, AppLocale.text(R.string.server_not_found_or_protected))
            else call.respondJson(PreviewDto(preview.isDirectory, preview.files, preview.bytes))
        }

        post("/api/delete") {
            if (!authorized(call)) return@post
            val dir = targetDir(call) ?: return@post
            val name = call.parameters["name"].orEmpty()
            val deleted = withContext(Dispatchers.IO) { store.delete(dir, name) }
            if (!deleted) {
                call.respondError(HttpStatusCode.Forbidden, AppLocale.text(R.string.server_cannot_delete))
                return@post
            }
            host.onDeleted(File(dir, name))
            call.respondJson(ChunkDto(0, true))
        }

        get("/api/addons") {
            if (!authorized(call)) return@get
            call.respondJson(host.addons())
        }

        post("/api/addons") {
            if (!authorized(call)) return@post
            val length = call.request.contentLength() ?: 0
            if (length > MAX_ADDON_BYTES) {
                call.receiveChannel().discard()
                call.respondError(HttpStatusCode.PayloadTooLarge, AppLocale.text(R.string.server_addon_too_large))
                return@post
            }
            val text = call.receiveText()
            val result = withContext(Dispatchers.IO) { host.installAddon(text) }
            result.fold({ call.respondJson(it) }, { call.respondError(HttpStatusCode.BadRequest, it.message ?: AppLocale.text(R.string.server_bad_addon)) })
        }

        post("/api/addons/url") {
            if (!authorized(call)) return@post
            val result = host.installAddonFromUrl(call.parameters["url"].orEmpty())
            result.fold({ call.respondJson(it) }, { call.respondError(HttpStatusCode.BadRequest, it.message ?: AppLocale.text(R.string.server_failed)) })
        }

        delete("/api/addons") {
            if (!authorized(call)) return@delete
            if (host.removeAddon(call.parameters["id"].orEmpty())) call.respondJson(ChunkDto(0, true))
            else call.respondError(HttpStatusCode.NotFound, AppLocale.text(R.string.server_no_addon))
        }

        get("/api/tmdb") {
            if (!authorized(call)) return@get
            call.respondJson(host.tmdbStatus())
        }

        post("/api/tmdb") {
            if (!authorized(call)) return@post
            if ((call.request.contentLength() ?: 0) > MAX_TOKEN_BYTES) {
                call.receiveChannel().discard()
                call.respondError(HttpStatusCode.PayloadTooLarge, AppLocale.text(R.string.server_too_long))
                return@post
            }
            host.setTmdbToken(call.receiveText())
                .fold({ call.respondJson(it) }, { call.respondError(HttpStatusCode.BadRequest, it.message ?: AppLocale.text(R.string.server_failed)) })
        }

        delete("/api/tmdb") {
            if (!authorized(call)) return@delete
            call.respondJson(host.clearTmdbToken())
        }

        get("/api/network") {
            if (!authorized(call)) return@get
            call.respondJson(host.networkFolders())
        }

        post("/api/network") {
            if (!authorized(call)) return@post
            if ((call.request.contentLength() ?: 0) > MAX_TOKEN_BYTES) {
                call.receiveChannel().discard()
                call.respondError(HttpStatusCode.PayloadTooLarge, AppLocale.text(R.string.server_request_too_large))
                return@post
            }
            val request = runCatching { json.decodeFromString<NetworkFolderRequest>(call.receiveText()) }.getOrNull()
            if (request == null) {
                call.respondError(HttpStatusCode.BadRequest, AppLocale.text(R.string.server_bad_request))
                return@post
            }
            host.addNetworkFolder(request)
                .fold({ call.respondJson(it) }, { call.respondError(HttpStatusCode.BadRequest, it.message ?: AppLocale.text(R.string.server_failed)) })
        }

        delete("/api/network") {
            if (!authorized(call)) return@delete
            if (host.removeNetworkFolder(call.parameters["root"].orEmpty())) call.respondJson(ChunkDto(0, true))
            else call.respondError(HttpStatusCode.NotFound, AppLocale.text(R.string.server_no_network_folder))
        }

        delete("/api/upload") {
            if (!authorized(call)) return@delete
            val dir = targetDir(call) ?: return@delete
            withContext(Dispatchers.IO) { store.discard(dir, call.parameters["name"].orEmpty()) }
            call.respondJson(ChunkDto(0, false))
        }
    }

    private suspend fun authorized(call: ApplicationCall, body: ByteReadChannel? = null): Boolean {
        val check = guard.check(host.pairingCode, call.request.headers["X-Pairing-Code"])
        if (check != PairingGuard.Check.Ok) body?.discard()
        return when (check) {
            PairingGuard.Check.Ok -> true
            PairingGuard.Check.Wrong -> {
                call.respondError(HttpStatusCode.Unauthorized, AppLocale.text(R.string.server_wrong_code))
                false
            }
            is PairingGuard.Check.Locked -> {
                call.respondError(HttpStatusCode.TooManyRequests, AppLocale.text(R.string.server_too_many_attempts), retryAfterMs = check.retryAfterMs)
                false
            }
        }
    }

    /** dir + optional "rel" sub-folders (folder uploads), confined to the allowed roots. */
    private suspend fun targetDir(call: ApplicationCall, body: ByteReadChannel? = null): File? {
        val dir = call.parameters["dir"].orEmpty()
        val rel = call.parameters["rel"].orEmpty().split('/').filter { it.isNotEmpty() }
        val resolved = withContext(Dispatchers.IO) { store.resolveDir(dir, rel) }
        if (resolved == null) {
            body?.discard()
            call.respondError(HttpStatusCode.Forbidden, AppLocale.text(R.string.server_upload_forbidden))
        }
        return resolved
    }

    private fun list(dir: File): ListingDto {
        val children = dir.listFiles().orEmpty()
        val partials = children.filter { it.isFile && it.name.startsWith('.') && it.name.endsWith(UploadStore.PART_SUFFIX) }
        val visible = children.filterNot { it.name.startsWith('.') || it.name.startsWith('$') }
        return ListingDto(
            path = dir.path,
            dirs = visible.filter { it.isDirectory }.map { it.name }.sortedBy { it.lowercase() },
            files = visible.filter { it.isFile }.map { EntryDto(it.name, it.length()) }.sortedBy { it.name.lowercase() },
            partials = partials.map { PartialDto(it.name.removePrefix(".").removeSuffix(UploadStore.PART_SUFFIX), it.length()) },
            freeBytes = dir.usableSpace,
        )
    }

    private suspend inline fun <reified T> ApplicationCall.respondJson(value: T) {
        respondText(json.encodeToString(value), ContentType.Application.Json)
    }

    private suspend fun ApplicationCall.respondError(
        status: HttpStatusCode,
        message: String,
        received: Long? = null,
        retryAfterMs: Long? = null,
    ) {
        respondText(json.encodeToString(ErrorDto(message, received, retryAfterMs)), ContentType.Application.Json, status)
    }
}
