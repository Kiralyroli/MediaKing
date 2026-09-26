package com.kiroland.mediacenter.ui.transfer

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.kiroland.mediacenter.data.transfer.PairingGuard
import com.kiroland.mediacenter.data.transfer.TransferRepository
import com.kiroland.mediacenter.data.transfer.TransferService
import com.kiroland.mediacenter.data.transfer.TransferState
import com.kiroland.mediacenter.data.transfer.UploadProgress
import com.kiroland.mediacenter.ui.library.ButtonContent
import com.kiroland.mediacenter.ui.library.ProgressStrip
import com.kiroland.mediacenter.util.formatBytes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class TransferViewModel @Inject constructor(private val repository: TransferRepository) : ViewModel() {
    val state: StateFlow<TransferState> = repository.state

    fun start(context: Context) = TransferService.start(context)
    fun stop(context: Context) = TransferService.stop(context)
    fun newCode() = repository.newPairingCode()
}

@Composable
fun TransferScreen(viewModel: TransferViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val primaryAction = remember { FocusRequester() }

    // Keep the TV awake while something is arriving and this screen is open.
    val view = LocalView.current
    LaunchedEffect(state.active.isNotEmpty()) { view.keepScreenOn = state.active.isNotEmpty() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { Text("Wi-Fi feltöltés", style = MaterialTheme.typography.headlineMedium) }

        if (!state.running) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "Tölts fel filmeket és sorozatokat telefonról vagy számítógépről, böngészőből, " +
                            "közvetlenül a TV-re kötött meghajtóra. Az eszköznek ugyanazon a hálózaton kell lennie.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = { viewModel.start(context) }, modifier = Modifier.focusRequester(primaryAction)) {
                        ButtonContent(Icons.Outlined.CloudUpload, "Bekapcsolás")
                    }
                }
            }
        } else {
            item { ConnectCard(state) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = { viewModel.stop(context) }, modifier = Modifier.focusRequester(primaryAction)) {
                        ButtonContent(Icons.Outlined.StopCircle, "Kikapcsolás")
                    }
                    OutlinedButton(onClick = viewModel::newCode) {
                        ButtonContent(Icons.Outlined.Refresh, "Új párosítási kód")
                    }
                }
            }
        }

        if (state.active.isNotEmpty()) {
            item { Text("Folyamatban", style = MaterialTheme.typography.titleLarge) }
            items(state.active, key = { it.path }) { ActiveUpload(it) }
        }
        if (state.received.isNotEmpty()) {
            item { Text("Megérkezett", style = MaterialTheme.typography.titleLarge) }
            items(state.received, key = { "done-" + it.path + it.at }) { file ->
                ListItem(
                    selected = false,
                    onClick = {},
                    headlineContent = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("${formatBytes(file.size)} · ${file.path.substringBeforeLast('/')}") },
                    leadingContent = { Icon(Icons.Outlined.CheckCircle, contentDescription = null) },
                )
            }
        }
    }
    LaunchedEffect(state.running) { runCatching { primaryAction.requestFocus() } }
}

@Composable
private fun ConnectCard(state: TransferState) {
    val url = state.url
    Row(horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
        if (url != null) {
            // The code rides in the fragment, which browsers never send to the server.
            val qr = remember(url, state.pairingCode) { qrBitmap("$url/#code=${state.pairingCode}") }
            Box(
                Modifier
                    .size(260.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(12.dp),
            ) {
                Image(qr.asImageBitmap(), contentDescription = "QR-kód a feltöltő oldalhoz", filterQuality = FilterQuality.None)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Telefonon olvasd be a QR-kódot, vagy nyisd meg a böngészőben:", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(url ?: "Nincs hálózati cím", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            Text("Párosítási kód:", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                PairingGuard.display(state.pairingCode),
                style = MaterialTheme.typography.displaySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ActiveUpload(upload: UploadProgress) {
    val fraction = if (upload.total > 0) upload.received.toFloat() / upload.total else 0f
    val remaining = upload.total - upload.received
    val eta = if (upload.bytesPerSecond > 0) remaining / upload.bytesPerSecond else null
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(upload.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        ProgressStrip(fraction, Modifier.width(900.dp).clip(RoundedCornerShape(2.dp)))
        Text(
            listOfNotNull(
                "${formatBytes(upload.received)} / ${formatBytes(upload.total)}",
                "${(fraction * 100).toInt()}%",
                upload.bytesPerSecond.takeIf { it > 0 }?.let { "${formatBytes(it)}/s" },
                eta?.let { "még kb. ${formatEta(it)}" },
            ).joinToString(" · "),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatEta(seconds: Long): String = when {
    seconds >= 3600 -> "${seconds / 3600} ó ${seconds % 3600 / 60} p"
    seconds >= 60 -> "${seconds / 60} p"
    else -> "$seconds mp"
}

private fun qrBitmap(content: String, size: Int = 512): Bitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 0))
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
}
