package com.kiroland.mediacenter.ui.browser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kiroland.mediacenter.data.library.LibraryRepository
import com.kiroland.mediacenter.data.storage.DirectoryListing
import com.kiroland.mediacenter.data.storage.FileEntry
import com.kiroland.mediacenter.data.storage.StorageRepository
import com.kiroland.mediacenter.ui.BrowserRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class BrowserUiState(
    val rootName: String,
    val rootPath: String,
    val currentPath: String,
    /** null while loading. */
    val entries: List<FileEntry>? = null,
    val unreadable: Boolean = false,
    /** Entry to focus after loading: the folder we just came back from. */
    val focusName: String? = null,
) {
    val relativePath: String get() = currentPath.removePrefix(rootPath).trimStart('/')
    val isAtRoot: Boolean get() = currentPath == rootPath
}

sealed interface LibraryStatus {
    data object NotIncluded : LibraryStatus
    data object Included : LibraryStatus
    /** Covered by a library folder higher up the tree. */
    data class IncludedVia(val folder: String) : LibraryStatus
}

@HiltViewModel
class BrowserViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: StorageRepository,
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<BrowserRoute>()
    private val _state = MutableStateFlow(BrowserUiState(route.rootName, route.rootPath, route.rootPath))
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    val libraryStatus: StateFlow<LibraryStatus> =
        combine(_state.map { it.currentPath }.distinctUntilChanged(), libraryRepository.folders) { path, folders ->
            when {
                folders.any { it.path == path } -> LibraryStatus.Included
                else -> folders.firstOrNull { path.startsWith(it.path + "/") }
                    ?.let { LibraryStatus.IncludedVia(it.path) }
                    ?: LibraryStatus.NotIncluded
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryStatus.NotIncluded)

    fun toggleLibrary() {
        val path = _state.value.currentPath
        viewModelScope.launch {
            when (libraryStatus.value) {
                LibraryStatus.NotIncluded -> libraryRepository.addFolder(path)
                LibraryStatus.Included -> libraryRepository.removeFolder(path)
                is LibraryStatus.IncludedVia -> Unit
            }
        }
    }

    init {
        load(route.rootPath, focusName = null)
        // A USB drive can disappear under us; re-list so the screen reflects it.
        viewModelScope.launch {
            repository.volumeChanges().collect { load(_state.value.currentPath, focusName = null) }
        }
    }

    fun open(entry: FileEntry) {
        if (entry.isDirectory) load(entry.path, focusName = null)
    }

    /** @return false when already at the volume root, so the caller should leave the browser. */
    fun goUp(): Boolean {
        val current = _state.value
        if (current.isAtRoot) return false
        val dir = File(current.currentPath)
        load(dir.parent ?: current.rootPath, focusName = dir.name)
        return true
    }

    private fun load(path: String, focusName: String?) {
        loadJob?.cancel()
        _state.update { it.copy(currentPath = path, entries = null, unreadable = false, focusName = focusName) }
        loadJob = viewModelScope.launch {
            when (val listing = repository.list(path)) {
                is DirectoryListing.Success -> _state.update { it.copy(entries = listing.entries) }
                DirectoryListing.Unreadable -> _state.update { it.copy(entries = emptyList(), unreadable = true) }
            }
        }
    }
}
