package com.kiroland.mediacenter.ui.diagnostics

import com.kiroland.mediacenter.R
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kiroland.mediacenter.data.storage.ProbeReport
import com.kiroland.mediacenter.data.storage.WriteTestResult
import com.kiroland.mediacenter.media.CodecSupport
import com.kiroland.mediacenter.ui.theme.Danger
import com.kiroland.mediacenter.ui.theme.Success
import com.kiroland.mediacenter.util.formatBytes
import java.util.Locale

@Composable
fun DiagnosticsScreen(viewModel: DiagnosticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp),
        contentPadding = PaddingValues(vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { Text(stringResource(R.string.nav_diagnostics), style = MaterialTheme.typography.headlineMedium) }

        section(R.string.diag_device)
        items(state.device) { (label, value) -> InfoRow(label, value) }

        section(R.string.diag_write_test)
        item {
            Text(
                stringResource(R.string.diag_write_test_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(state.volumes, key = { it.path }) { volume ->
            val probe = state.probes[volume.path]
            ListItem(
                selected = false,
                onClick = { viewModel.runWriteTest(volume) },
                headlineContent = { Text(stringResource(R.string.diag_free, volume.name, formatBytes(volume.freeBytes))) },
                supportingContent = {
                    when {
                        state.armedPath == volume.path ->
                            Text(stringResource(R.string.diag_external_warning), color = Color(0xFFFFC857))
                        probe == null -> Text(volume.path)
                        probe == ProbeState.Running -> Text(stringResource(R.string.diag_running))
                        probe is ProbeState.Done -> ProbeSummary(probe.report)
                    }
                },
                trailingContent = {
                    Text(
                        when {
                            state.armedPath == volume.path -> stringResource(R.string.diag_confirm)
                            probe == null -> stringResource(R.string.diag_start)
                            else -> stringResource(R.string.diag_again)
                        },
                    )
                },
            )
        }

        val codecs = state.codecs
        if (codecs != null) {
            section(R.string.diag_video_decoders)
            items(codecs.video) { CodecRow(it) }
            section(R.string.diag_audio_decoders)
            item {
                Text(
                    stringResource(R.string.diag_ffmpeg, codecs.ffmpegVersion ?: stringResource(R.string.diag_unavailable)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(codecs.audio) { CodecRow(it) }
        }

        section(R.string.diag_data_source)
        item {
            InfoRow(stringResource(R.string.diag_movie_data), "The Movie Database (TMDB)")
        }
        item {
            // Attribution required by the TMDB API terms.
            Text(
                "This product uses the TMDB API but is not endorsed or certified by TMDB.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun LazyListScope.section(@StringRes title: Int) {
    item {
        Text(
            stringResource(title),
            modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Rows are focusable (no-op click) so the D-pad can scroll through the whole report. */
@Composable
private fun InfoRow(label: String, value: String) {
    ListItem(
        selected = false,
        onClick = {},
        headlineContent = { Text(label) },
        trailingContent = { Text(value) },
    )
}

@Composable
private fun CodecRow(codec: CodecSupport) {
    val (status, color) = when {
        codec.platformDecoders.isNotEmpty() && codec.hardwareAccelerated -> stringResource(R.string.diag_hardware) to Success
        codec.platformDecoders.isNotEmpty() -> stringResource(R.string.diag_software) to Success
        codec.ffmpeg -> "FFmpeg" to Success
        else -> stringResource(R.string.diag_unsupported) to Danger
    }
    ListItem(
        selected = false,
        onClick = {},
        headlineContent = { Text(codec.label) },
        supportingContent = { Text(codec.platformDecoders.firstOrNull() ?: codec.mimeType) },
        trailingContent = { Text(status, color = color) },
    )
}

@Composable
private fun ProbeSummary(report: ProbeReport) {
    val lines = buildList {
        add(stringResource(if (report.readable) R.string.diag_readable else R.string.diag_unreadable, report.visibleEntries))
        add(stringResource(R.string.diag_folder_write, report.directWrite.describe()))
        report.appDirWrite?.let { add(stringResource(R.string.diag_app_folder, it.describe())) }
    }
    val ok = report.readable && report.directWrite.success
    Text(lines.joinToString("\n"), color = if (ok) Success else Color(0xFFFFC857))
}

private fun WriteTestResult.describe(): String =
    if (success) {
        "✔ " + (writeMbPerSec?.let { String.format(com.kiroland.mediacenter.util.AppLocale.current, "%.1f MB/s", it) } ?: message)
    } else {
        "✖ $message"
    }
