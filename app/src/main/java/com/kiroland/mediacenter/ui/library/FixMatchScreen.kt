package com.kiroland.mediacenter.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.kiroland.mediacenter.data.library.db.MediaKind
import com.kiroland.mediacenter.data.metadata.MatchCandidate
import com.kiroland.mediacenter.data.metadata.MetadataRepository
import com.kiroland.mediacenter.data.metadata.tmdb.TmdbImages
import com.kiroland.mediacenter.ui.FixMatchRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface FixMatchState {
    data object Idle : FixMatchState
    data object Searching : FixMatchState
    data class Results(val items: List<MatchCandidate>) : FixMatchState
    data class Error(val message: String) : FixMatchState
    data object Saved : FixMatchState
}

@HiltViewModel
class FixMatchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val metadata: MetadataRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<FixMatchRoute>()
    val kind = MediaKind.valueOf(route.kind)
    val initialQuery = route.query

    private val _state = MutableStateFlow<FixMatchState>(FixMatchState.Idle)
    val state: StateFlow<FixMatchState> = _state

    init {
        search(initialQuery)
    }

    fun search(query: String) {
        if (query.isBlank()) return
        _state.value = FixMatchState.Searching
        viewModelScope.launch {
            _state.value = runCatching { metadata.search(kind, query.trim()) }
                .fold({ FixMatchState.Results(it) }, { FixMatchState.Error("A keresés nem sikerült: ${it.message}") })
        }
    }

    fun choose(candidate: MatchCandidate) {
        _state.value = FixMatchState.Searching
        viewModelScope.launch {
            _state.value = runCatching { metadata.applyMatch(route.key, kind, candidate.id) }
                .fold({ FixMatchState.Saved }, { FixMatchState.Error("Nem sikerült menteni: ${it.message}") })
        }
    }
}

@Composable
fun FixMatchScreen(onDone: () -> Unit, viewModel: FixMatchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf(viewModel.initialQuery) }
    var fieldFocused by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val firstResult = remember { FocusRequester() }

    LaunchedEffect(state) {
        when (val s = state) {
            FixMatchState.Saved -> onDone()
            is FixMatchState.Results -> if (s.items.isNotEmpty()) runCatching { firstResult.requestFocus() }
            else -> Unit
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 56.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            if (viewModel.kind == MediaKind.MOVIE) "Melyik film ez?" else "Melyik sorozat ez?",
            style = MaterialTheme.typography.headlineMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // OK on the field opens the on-screen keyboard; its search key runs the query.
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search(query) }),
                modifier = Modifier
                    .width(640.dp)
                    .focusRequester(fieldFocus)
                    .onFocusChanged { fieldFocused = it.isFocused }
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(2.dp, if (fieldFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Button(onClick = { viewModel.search(query) }) { Text("Keresés") }
        }

        when (val s = state) {
            FixMatchState.Idle, FixMatchState.Saved -> Unit
            FixMatchState.Searching -> Text("Keresés…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            is FixMatchState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
            is FixMatchState.Results -> if (s.items.isEmpty()) {
                Text("Nincs találat. Próbáld az eredeti (angol) címmel.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(s.items, key = { it.id }) { candidate ->
                        ListItem(
                            selected = false,
                            onClick = { viewModel.choose(candidate) },
                            modifier = if (candidate == s.items.first()) Modifier.focusRequester(firstResult) else Modifier,
                            leadingContent = {
                                Box(
                                    Modifier
                                        .width(54.dp)
                                        .height(80.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(placeholderBrush(candidate.title)),
                                ) {
                                    candidate.posterPath?.let {
                                        AsyncImage(TmdbImages.poster(it), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    }
                                }
                            },
                            headlineContent = {
                                Text(
                                    listOfNotNull(candidate.title, candidate.year?.let { "($it)" }).joinToString(" "),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                Text(
                                    listOfNotNull(
                                        candidate.originalTitle?.takeIf { it != candidate.title },
                                        candidate.overview?.takeIf { it.isNotBlank() },
                                    ).joinToString(" · "),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
