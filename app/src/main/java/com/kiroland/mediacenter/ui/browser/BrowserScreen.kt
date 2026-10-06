package com.kiroland.mediacenter.ui.browser

import com.kiroland.mediacenter.R
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.LibraryAddCheck
import androidx.compose.ui.Alignment
import androidx.tv.material3.OutlinedButton
import java.io.File
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kiroland.mediacenter.data.storage.FileEntry
import com.kiroland.mediacenter.media.MediaType
import com.kiroland.mediacenter.util.formatBytes
import com.kiroland.mediacenter.util.formatDate

@Composable
fun BrowserScreen(
    onPlay: (FileEntry) -> Unit,
    onExit: () -> Unit,
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val libraryStatus by viewModel.libraryStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current

    BackHandler { if (!viewModel.goUp()) onExit() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 48.dp, end = 48.dp, top = 36.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(state.rootName, style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = state.relativePath.ifEmpty { "/" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.StartEllipsis,
                )
            }
            when (val status = libraryStatus) {
                LibraryStatus.NotIncluded -> OutlinedButton(onClick = viewModel::toggleLibrary) {
                    Icon(Icons.Outlined.LibraryAdd, contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(20.dp))
                    Text(stringResource(R.string.browser_add_to_library))
                }
                LibraryStatus.Included -> OutlinedButton(onClick = viewModel::toggleLibrary) {
                    Icon(Icons.Outlined.LibraryAddCheck, contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(20.dp))
                    Text(stringResource(R.string.browser_in_library_remove))
                }
                is LibraryStatus.IncludedVia -> Text(
                    stringResource(R.string.browser_part_of_library, File(status.folder).name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        val entries = state.entries
        when {
            entries == null -> Message(stringResource(R.string.browser_loading))
            state.unreadable -> Message(stringResource(R.string.browser_unreadable))
            entries.isEmpty() -> Message(stringResource(R.string.browser_empty))
            else -> EntryList(
                entries = entries,
                // Re-run focus handling for every directory change.
                directoryKey = state.currentPath,
                focusName = state.focusName,
                onClick = { entry ->
                    when {
                        entry.isDirectory -> viewModel.open(entry)
                        entry.type.isPlayable -> onPlay(entry)
                        else -> Toast.makeText(context, context.getString(R.string.browser_cannot_open), Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 24.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EntryList(
    entries: List<FileEntry>,
    directoryKey: String,
    focusName: String?,
    onClick: (FileEntry) -> Unit,
) {
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val focusIndex = entries.indexOfFirst { it.name == focusName }.coerceAtLeast(0)

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(entries, key = { _, entry -> entry.path }) { index, entry ->
            ListItem(
                selected = false,
                onClick = { onClick(entry) },
                modifier = if (index == focusIndex) Modifier.focusRequester(focusRequester) else Modifier,
                headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(entry.details()) },
                leadingContent = { Icon(entry.icon(), contentDescription = null) },
            )
        }
    }

    LaunchedEffect(directoryKey, entries) {
        listState.scrollToItem(focusIndex)
        withFrameNanos { } // Let the target item compose before asking it for focus.
        runCatching { focusRequester.requestFocus() }
    }
}

@Composable
private fun FileEntry.details(): String =
    if (isDirectory) stringResource(R.string.browser_folder, formatDate(lastModified)) else "${formatBytes(sizeBytes)} · ${formatDate(lastModified)}"

private fun FileEntry.icon(): ImageVector = when {
    isDirectory -> Icons.Outlined.Folder
    else -> when (type) {
        MediaType.VIDEO -> Icons.Outlined.Movie
        MediaType.AUDIO -> Icons.Outlined.MusicNote
        MediaType.IMAGE -> Icons.Outlined.Image
        MediaType.SUBTITLE -> Icons.Outlined.Subtitles
        MediaType.STREAM -> Icons.Outlined.Link
        MediaType.OTHER -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }
}
