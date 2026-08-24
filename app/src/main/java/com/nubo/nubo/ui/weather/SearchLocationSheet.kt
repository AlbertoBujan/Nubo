package com.nubo.nubo.ui.weather

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nubo.nubo.domain.model.SavedLocation

/** Hoja inferior para buscar y añadir municipios. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchLocationSheet(
    results: List<SavedLocation>,
    isSearching: Boolean,
    isLocating: Boolean,
    onQueryChange: (String) -> Unit,
    onSelect: (SavedLocation) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E2A3A),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                "Buscar localización",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.W600,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    onQueryChange(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Escribe un municipio…") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
            )

            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = onUseCurrentLocation,
                enabled = !isLocating,
            ) {
                Icon(Icons.Outlined.MyLocation, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isLocating) "Localizando…" else "Usar mi ubicación actual")
            }

            Spacer(Modifier.height(8.dp))

            when {
                isSearching -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White.copy(alpha = 0.8f))
                }

                query.isNotBlank() && results.isEmpty() -> Text(
                    "Sin resultados para \"$query\"",
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 24.dp),
                )

                else -> LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(results, key = { it.municipioId }) { location ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(location) }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.LocationOn,
                                null,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                location.nombre,
                                color = Color.White,
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.Filled.Add,
                                "Añadir",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
