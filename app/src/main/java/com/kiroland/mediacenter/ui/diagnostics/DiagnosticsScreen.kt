package com.kiroland.mediacenter.ui.diagnostics

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
        item { Text("Diagnosztika", style = MaterialTheme.typography.headlineMedium) }

        section("Készülék")
        items(state.device) { (label, value) -> InfoRow(label, value) }

        section("Tárhelyek – írásteszt")
        item {
            Text(
                "A teszt egy 4 MB-os ideiglenes fájlt ír ki és töröl. Csak kérésre fut.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(state.volumes, key = { it.path }) { volume ->
            val probe = state.probes[volume.path]
            ListItem(
                selected = false,
                onClick = { viewModel.runWriteTest(volume) },
                headlineContent = { Text("${volume.name} · ${formatBytes(volume.freeBytes)} szabad") },
                supportingContent = {
                    when {
                        state.armedPath == volume.path ->
                            Text("Külső meghajtó: a teszt írni fog rá. Nyomd meg újra az indításhoz.", color = Color(0xFFFFC857))
                        probe == null -> Text(volume.path)
                        probe == ProbeState.Running -> Text("Teszt fut…")
                        probe is ProbeState.Done -> ProbeSummary(probe.report)
                    }
                },
                trailingContent = {
                    Text(
                        when {
                            state.armedPath == volume.path -> "Megerősítés"
                            probe == null -> "Teszt indítása"
                            else -> "Újra"
                        },
                    )
                },
            )
        }

        val codecs = state.codecs
        if (codecs != null) {
            section("Videó dekóderek")
            items(codecs.video) { CodecRow(it) }
            section("Hang dekóderek")
            item {
                Text(
                    "FFmpeg bővítmény: ${codecs.ffmpegVersion ?: "nem érhető el"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(codecs.audio) { CodecRow(it) }
        }

        section("Adatforrás")
        item {
            InfoRow("Film- és sorozatadatok", "The Movie Database (TMDB)")
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

private fun LazyListScope.section(title: String) {
    item {
        Text(
            title,
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
        codec.platformDecoders.isNotEmpty() && codec.hardwareAccelerated -> "Hardveres" to Success
        codec.platformDecoders.isNotEmpty() -> "Szoftveres (rendszer)" to Success
        codec.ffmpeg -> "FFmpeg" to Success
        else -> "Nem támogatott" to Danger
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
        add((if (report.readable) "✔ Olvasható" else "✖ Nem olvasható") + " (${report.visibleEntries} elem)")
        add("Mappába írás: " + report.directWrite.describe())
        report.appDirWrite?.let { add("Alkalmazásmappa: " + it.describe()) }
    }
    val ok = report.readable && report.directWrite.success
    Text(lines.joinToString("\n"), color = if (ok) Success else Color(0xFFFFC857))
}

private fun WriteTestResult.describe(): String =
    if (success) {
        "✔ " + (writeMbPerSec?.let { String.format(Locale.forLanguageTag("hu-HU"), "%.1f MB/s", it) } ?: message)
    } else {
        "✖ $message"
    }
