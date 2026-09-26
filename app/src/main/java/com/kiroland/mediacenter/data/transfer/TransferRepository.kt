package com.kiroland.mediacenter.data.transfer

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.StatFs
import android.util.Log
import com.kiroland.mediacenter.data.library.LibraryScanner
import com.kiroland.mediacenter.data.library.db.LibraryDao
import com.kiroland.mediacenter.data.storage.StorageRepository
import com.kiroland.mediacenter.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.Inet4Address
import javax.inject.Inject
import javax.inject.Singleton

data class UploadProgress(
    val path: String,
    val name: String,
    val total: Long,
    val received: Long,
    val bytesPerSecond: Long,
    val updatedAt: Long,
)

data class ReceivedFile(val path: String, val name: String, val size: Long, val at: Long)

data class TransferState(
    val running: Boolean = false,
    /** e.g. http://192.168.1.3:8080 — null when the TV has no LAN address. */
    val url: String? = null,
    val pairingCode: String = "",
    val active: List<UploadProgress> = emptyList(),
    val received: List<ReceivedFile> = emptyList(),
    val error: String? = null,
)

@Singleton
class TransferRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val storage: StorageRepository,
    private val libraryDao: LibraryDao,
    private val scanner: LibraryScanner,
    @param:ApplicationScope private val scope: CoroutineScope,
) : TransferHost {

    private val prefs = context.getSharedPreferences("transfer", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(TransferState(pairingCode = loadOrCreateCode()))
    val state: StateFlow<TransferState> = _state.asStateFlow()

    private var server: TransferServer? = null
    private var rescanJob: Job? = null
    private val speed = HashMap<String, Pair<Long, Long>>() // path -> (window start ms, bytes in window)

    override val pairingCode: String get() = _state.value.pairingCode
    override val deviceName: String get() = "${Build.MANUFACTURER} ${Build.MODEL}"

    @Synchronized
    fun start() {
        if (server != null) return
        val store = UploadStore(allowedRoots = { storage.volumeRoots() })
        val newServer = TransferServer(this, store) { context.assets.open("web/index.html").use { it.readBytes() } }
        try {
            val port = newServer.start()
            server = newServer
            val url = lanAddress()?.let { "http://$it:$port" }
            _state.update { it.copy(running = true, url = url, error = if (url == null) "Nincs hálózati kapcsolat" else null) }
            Log.i(TAG, "Upload server listening on $url")
        } catch (e: Exception) {
            Log.e(TAG, "Could not start upload server", e)
            _state.update { it.copy(running = false, error = e.message) }
        }
    }

    @Synchronized
    fun stop() {
        server?.stop()
        server = null
        _state.update { it.copy(running = false, url = null, active = emptyList()) }
    }

    fun newPairingCode() {
        val code = PairingGuard.newCode()
        prefs.edit().putString(KEY_CODE, code).apply()
        _state.update { it.copy(pairingCode = code) }
    }

    /** Library folders first (where uploads usually go), then whole drives. */
    override fun roots(): List<RootDto> {
        val libraryFolders = runBlocking { libraryDao.folders() }.map { File(it.path) }.filter { it.isDirectory }
        fun dto(file: File, kind: String, name: String): RootDto {
            val stat = runCatching { StatFs(file.path) }.getOrNull()
            return RootDto(file.path, name, kind, stat?.availableBytes ?: 0, stat?.totalBytes ?: 0)
        }
        val volumes = storage.volumeRoots()
        return libraryFolders.map { dto(it, "library", it.name) } +
            volumes.map { root ->
                val label = if (root.path.contains("emulated")) "Belső tárhely" else "USB-meghajtó (${root.name})"
                dto(root, "drive", label)
            }
    }

    @Synchronized
    override fun onChunk(file: File, total: Long, receivedBefore: Long, bytes: Long) {
        val now = System.currentTimeMillis()
        val key = file.path
        val (windowStart, windowBytes) = speed[key] ?: (now to 0L)
        val inWindow = windowBytes + bytes
        val elapsed = now - windowStart
        val current = _state.value.active.firstOrNull { it.path == key }
        // Recompute speed over ~2 s windows; publish at most a few times a second.
        val bytesPerSecond = if (elapsed >= 2_000) inWindow * 1000 / elapsed else current?.bytesPerSecond ?: 0
        speed[key] = if (elapsed >= 2_000) now to 0L else windowStart to inWindow
        if (current != null && now - current.updatedAt < 300) return
        val progress = UploadProgress(key, file.name, total, receivedBefore + bytes, bytesPerSecond, now)
        _state.update { state ->
            val others = state.active.filter { it.path != key && now - it.updatedAt < STALE_MS }
            state.copy(active = listOf(progress) + others)
        }
    }

    @Synchronized
    override fun onFileDone(file: File) {
        speed.remove(file.path)
        val done = ReceivedFile(file.path, file.name, file.length(), System.currentTimeMillis())
        _state.update { state ->
            state.copy(
                active = state.active.filter { it.path != file.path },
                received = (listOf(done) + state.received).take(20),
            )
        }
        scheduleRescan()
    }

    /** One rescan after a burst of uploads settles, not one per file. */
    private fun scheduleRescan() {
        rescanJob?.cancel()
        rescanJob = scope.launch {
            delay(RESCAN_DELAY_MS)
            scanner.scanAll()
        }
    }

    private fun lanAddress(): String? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val properties = connectivity.getLinkProperties(connectivity.activeNetwork) ?: return null
        return properties.linkAddresses.map { it.address }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    }

    private fun loadOrCreateCode(): String =
        prefs.getString(KEY_CODE, null) ?: PairingGuard.newCode().also { prefs.edit().putString(KEY_CODE, it).apply() }

    private companion object {
        const val TAG = "TransferRepository"
        const val KEY_CODE = "pairing_code"
        const val STALE_MS = 60_000L
        const val RESCAN_DELAY_MS = 5_000L
    }
}
