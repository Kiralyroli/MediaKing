package com.kiroland.mediacenter.ui.diagnostics

import com.kiroland.mediacenter.R
import com.kiroland.mediacenter.util.AppLocale
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiroland.mediacenter.data.storage.ProbeReport
import com.kiroland.mediacenter.data.storage.StorageProbe
import com.kiroland.mediacenter.data.storage.StorageRepository
import com.kiroland.mediacenter.data.storage.StorageVolumeInfo
import com.kiroland.mediacenter.media.CodecInspector
import com.kiroland.mediacenter.media.CodecReport
import com.kiroland.mediacenter.util.formatBytes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface ProbeState {
    data object Running : ProbeState
    data class Done(val report: ProbeReport) : ProbeState
}

data class DiagnosticsUiState(
    val device: List<Pair<String, String>> = emptyList(),
    val volumes: List<StorageVolumeInfo> = emptyList(),
    val probes: Map<String, ProbeState> = emptyMap(),
    /** Removable volume waiting for a second press before its write test runs. */
    val armedPath: String? = null,
    val codecs: CodecReport? = null,
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val storageRepository: StorageRepository,
    private val storageProbe: StorageProbe,
    private val codecInspector: CodecInspector,
) : ViewModel() {

    private val _state = MutableStateFlow(DiagnosticsUiState())
    val state: StateFlow<DiagnosticsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val codecs = withContext(Dispatchers.Default) { codecInspector.inspect() }
            _state.update { it.copy(device = deviceInfo(), volumes = storageRepository.volumes(), codecs = codecs) }
        }
    }

    fun runWriteTest(volume: StorageVolumeInfo) {
        val current = _state.value
        if (current.probes[volume.path] == ProbeState.Running) return
        // USB drives can drop off the bus under write load: require an explicit confirmation.
        if (volume.removable && current.armedPath != volume.path) {
            _state.update { it.copy(armedPath = volume.path) }
            return
        }
        _state.update { it.copy(probes = it.probes + (volume.path to ProbeState.Running), armedPath = null) }
        viewModelScope.launch {
            val report = storageProbe.probe(volume.path)
            _state.update { it.copy(probes = it.probes + (volume.path to ProbeState.Done(report))) }
        }
    }

    private fun deviceInfo(): List<Pair<String, String>> {
        val memory = ActivityManager.MemoryInfo().also {
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(it)
        }
        val display = context.resources.displayMetrics
        return listOf(
            AppLocale.text(R.string.diag_device) to "${Build.MANUFACTURER} ${Build.MODEL}",
            "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "ABI" to Build.SUPPORTED_ABIS.joinToString(),
            AppLocale.text(R.string.diag_memory) to AppLocale.text(R.string.diag_memory_value, formatBytes(memory.availMem), formatBytes(memory.totalMem)),
            AppLocale.text(R.string.diag_display) to "${display.widthPixels}×${display.heightPixels}, ${display.densityDpi} dpi",
        )
    }
}
