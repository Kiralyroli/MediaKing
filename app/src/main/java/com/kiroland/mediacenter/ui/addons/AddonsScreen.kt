package com.kiroland.mediacenter.ui.addons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kiroland.mediacenter.data.addons.AddonManifest
import com.kiroland.mediacenter.data.addons.AddonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AddonsViewModel @Inject constructor(private val repository: AddonRepository) : ViewModel() {
    val addons: StateFlow<List<AddonManifest>> = repository.addons
    fun remove(id: String) = repository.remove(id)
}

@Composable
fun AddonsScreen(viewModel: AddonsViewModel = hiltViewModel()) {
    val addons by viewModel.addons.collectAsStateWithLifecycle()
    // First press arms, second press removes: no dialogs to fight with the remote.
    var armed by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Kiegészítők", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "A kiegészítők élő csatornákat adnak az Élő TV menühöz. Telepítés: Feltöltés → Bekapcsolás, " +
                        "majd a böngészőben a „Kiegészítők” résznél fájlból vagy URL-ről.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (addons.isEmpty()) {
            item { Text("Nincs telepített kiegészítő.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(addons, key = { it.id }) { addon ->
            val isArmed = armed == addon.id
            ListItem(
                selected = false,
                onClick = {
                    if (isArmed) {
                        viewModel.remove(addon.id)
                        armed = null
                    } else {
                        armed = addon.id
                    }
                },
                leadingContent = { Icon(Icons.Outlined.Extension, contentDescription = null) },
                headlineContent = { Text("${addon.name}  ·  v${addon.version}") },
                supportingContent = {
                    Text(
                        if (isArmed) "Nyomd meg újra az eltávolításhoz."
                        else listOfNotNull(addon.description, "${addon.channels.size} csatorna", addon.id).joinToString(" · "),
                        color = if (isArmed) Color(0xFFFFC857) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingContent = { Text(if (isArmed) "Eltávolítás" else "") },
            )
        }
    }
}
