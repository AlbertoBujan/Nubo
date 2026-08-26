package com.nubo.nubo.ui.weather

import com.nubo.nubo.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import com.nubo.nubo.domain.geo.formatDistance
import com.nubo.nubo.ui.components.LocalUnits
import com.nubo.nubo.domain.model.SavedLocation

/** Hoja inferior para buscar y añadir municipios. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchLocationSheet(
    results: List<SearchResult>,
    isSearching: Boolean,
    isLocating: Boolean,
    nearby: Boolean,
    onQueryChange: (String) -> Unit,
    onNearbyChange: (Boolean) -> Unit,
    onSelect: (SavedLocation) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Al reordenar por cercanía, `LazyColumn` deja anclado el elemento que
    // estaba arriba, así que los resultados nuevos —los cercanos— aparecen
    // por encima del borde visible y parece que no ha pasado nada. Volver al
    // principio en cada cambio de lista es además lo que se espera de una
    // búsqueda.
    LaunchedEffect(results) {
        if (results.isNotEmpty()) listState.scrollToItem(0)
    }

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
                stringResource(R.string.search_location),
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
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
            )

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = onUseCurrentLocation,
                    enabled = !isLocating,
                ) {
                    Icon(Icons.Outlined.MyLocation, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            if (isLocating) R.string.locating else R.string.use_my_location,
                        ),
                    )
                }

                // Va apagado por defecto a propósito: ordenar por cercanía
                // estorba cuando lo que buscas está lejos —desde España,
                // "Tokio" tiene más cerca el de Dakota del Norte que el de
                // Japón—, y solo lo enciendes cuando buscas algo de tu zona.
                Row(
                    Modifier.clickable { onNearbyChange(!nearby) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Sin `onCheckedChange` el checkbox no responde por su
                    // cuenta: pulsarlo disparaba dos veces —una él y otra la
                    // fila— y el segundo evento deshacía el primero.
                    Checkbox(
                        checked = nearby,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF64B5F6),
                            uncheckedColor = Color.White.copy(alpha = 0.5f),
                        ),
                    )
                    Text(
                        stringResource(R.string.search_nearby),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Alto fijo en cuanto hay algo escrito. Si la caja se ajustase a
            // su contenido, la hoja daría un salto con cada tecla: las tres
            // ramas miden distinto y además el número de resultados cambia a
            // cada carácter. Con la consulta vacía se pliega del todo para no
            // dejar un hueco muerto al abrir.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (query.isBlank()) 0.dp else RESULTS_HEIGHT),
            ) {
                when {
                    isSearching -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Color.White.copy(alpha = 0.8f))
                    }

                    results.isEmpty() -> Text(
                        stringResource(R.string.no_results_for, query),
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 24.dp),
                    )

                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(results, key = { it.location.locationId }) { result ->
                            val location = result.location
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
                                // La región va debajo porque la búsqueda es
                                // mundial: "Curtis" son dos sitios, uno en A
                                // Coruña y otro en Nebraska, y sin esto las dos
                                // filas serían idénticas.
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        location.nombre,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                    )
                                    // La distancia se cuelga de la región para
                                    // que se vea de dónde sale el orden.
                                    val subtitle = listOfNotNull(
                                        location.region,
                                        result.distanceKm?.let {
                                            formatDistance(it, LocalUnits.current.distance)
                                        },
                                    ).joinToString(" · ")

                                    if (subtitle.isNotBlank()) {
                                        Text(
                                            subtitle,
                                            color = Color.White.copy(alpha = 0.55f),
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                Icon(
                                    Icons.Filled.Add,
                                    stringResource(R.string.add),
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
}

/**
 * Alto reservado para los resultados.
 *
 * Da para unas cinco filas; el resto se ven haciendo scroll dentro de la
 * caja, sin que la hoja cambie de tamaño.
 */
private val RESULTS_HEIGHT = 320.dp
