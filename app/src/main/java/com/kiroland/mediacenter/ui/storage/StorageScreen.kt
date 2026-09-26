package com.kiroland.mediacenter.ui.storage

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kiroland.mediacenter.data.storage.StorageVolumeInfo
import com.kiroland.mediacenter.util.formatBytes

private val STORAGE_PERMISSIONS = arrayOf(
    Manifest.permission.READ_EXTERNAL_STORAGE,
    Manifest.permission.WRITE_EXTERNAL_STORAGE,
)

@Composable
fun StorageScreen(
    onOpenVolume: (StorageVolumeInfo) -> Unit,
    viewModel: StorageViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionGranted = result[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        viewModel.refresh()
    }
    val volumes by viewModel.volumes.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("Tárhelyek", style = MaterialTheme.typography.headlineMedium)

        if (!permissionGranted) {
            PermissionRequest(onRequest = { permissionLauncher.launch(STORAGE_PERMISSIONS) })
            return@Column
        }

        val list = volumes
        when {
            list == null -> Text("Keresés…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            list.isEmpty() -> Text("Nem található tárhely.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> VolumeRow(list, onOpenVolume)
        }
    }
}

@Composable
private fun PermissionRequest(onRequest: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "A médiatárhoz hozzáférés kell a tárhelyekhez (belső tár és USB-meghajtó).",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onRequest, modifier = Modifier.focusRequester(focusRequester)) {
            Text("Hozzáférés engedélyezése")
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun VolumeRow(volumes: List<StorageVolumeInfo>, onOpenVolume: (StorageVolumeInfo) -> Unit) {
    val firstCard = remember { FocusRequester() }
    // Padding leaves room for the focused card's scale-up, which would otherwise be clipped.
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    ) {
        itemsIndexed(volumes, key = { _, volume -> volume.path }) { index, volume ->
            VolumeCard(
                volume = volume,
                onClick = { onOpenVolume(volume) },
                modifier = if (index == 0) Modifier.focusRequester(firstCard) else Modifier,
            )
        }
    }
    LaunchedEffect(volumes.first().path) { runCatching { firstCard.requestFocus() } }
}

@Composable
private fun VolumeCard(volume: StorageVolumeInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.width(320.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = if (volume.removable) Icons.Outlined.Usb else Icons.Outlined.PhoneAndroid,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(volume.name, style = MaterialTheme.typography.titleLarge)
            Text(
                volume.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            UsageBar(used = volume.totalBytes - volume.freeBytes, total = volume.totalBytes)
            Text(
                "${formatBytes(volume.freeBytes)} szabad · ${formatBytes(volume.totalBytes)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun UsageBar(used: Long, total: Long) {
    val fraction = if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(6.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}
