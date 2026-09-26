package com.kiroland.mediacenter.ui.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiroland.mediacenter.data.storage.StorageRepository
import com.kiroland.mediacenter.data.storage.StorageVolumeInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StorageViewModel @Inject constructor(
    private val repository: StorageRepository,
) : ViewModel() {

    /** null while the first scan is running. */
    private val _volumes = MutableStateFlow<List<StorageVolumeInfo>?>(null)
    val volumes: StateFlow<List<StorageVolumeInfo>?> = _volumes.asStateFlow()

    init {
        viewModelScope.launch {
            repository.volumeChanges()
                .onStart { emit(Unit) }
                .collect { _volumes.value = repository.volumes() }
        }
    }

    fun refresh() {
        viewModelScope.launch { _volumes.value = repository.volumes() }
    }
}
