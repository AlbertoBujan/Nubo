package com.nubo.nubo.ui.weather

import com.nubo.nubo.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nubo.nubo.BuildConfig
import com.nubo.nubo.data.remote.AvailableUpdate
import com.nubo.nubo.data.remote.UpdateService
import kotlinx.coroutines.launch

/** Aviso de nueva versión disponible en GitHub Releases. */
@Composable
fun UpdateDialog(
    update: AvailableUpdate,
    onDismiss: () -> Unit,
    onInstall: suspend ((Float) -> Unit) -> Boolean,
) {
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        // Mientras descarga no se puede cerrar: cancelar a medias dejaría un
        // APK incompleto en la caché.
        onDismissRequest = { if (!downloading) onDismiss() },
        containerColor = Color(0xFF1E2A3A),
        title = { Text(stringResource(R.string.update_available), color = Color.White) },
        text = {
            Column {
                if (!downloading) {
                    Text(
                        stringResource(R.string.update_body, update.version),
                        color = Color.White.copy(alpha = 0.75f),
                    )
                } else {
                    Text(stringResource(R.string.update_downloading), color = Color.White.copy(alpha = 0.75f))
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${(progress * 100).toInt()}%",
                        color = Color.White.copy(alpha = 0.55f),
                    )
                }
            }
        },
        confirmButton = {
            if (!downloading) {
                TextButton(onClick = {
                    downloading = true
                    scope.launch {
                        val ok = onInstall { progress = it }
                        // Al lanzar el instalador el diálogo ya no pinta nada.
                        if (ok) onDismiss() else downloading = false
                    }
                }) {
                    Text(stringResource(R.string.update_now))
                }
            }
        },
        dismissButton = {
            if (!downloading) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.update_later), color = Color.White.copy(alpha = 0.55f))
                }
            }
        },
    )
}

/** Comprobando si hay versión nueva; sin botones, se va solo al terminar. */
@Composable
fun CheckingUpdatesDialog() {
    AlertDialog(
        // Dura lo que tarda la petición, así que no se puede cerrar: cerrarlo
        // no cancelaría nada y el resultado aparecería igual un segundo después.
        onDismissRequest = {},
        containerColor = Color(0xFF1E2A3A),
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF64B5F6),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    stringResource(R.string.update_checking),
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
        },
        confirmButton = {},
    )
}

/**
 * Resultado de una comprobación **manual** que no ha encontrado nada nuevo.
 *
 * Pulsar un botón y que no pase nada se lee como que el botón está roto, y las
 * dos respuestas silenciosas no son la misma: una es "estás al día" y la otra
 * "no he podido preguntar". Decir la primera cuando ha fallado la red sería
 * mentira, por eso [UpdateService.UpdateCheck] las distingue en origen.
 */
@Composable
fun UpdateCheckDialog(
    result: UpdateService.UpdateCheck,
    onDismiss: () -> Unit,
) {
    val upToDate = result is UpdateService.UpdateCheck.UpToDate

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E2A3A),
        title = {
            Text(
                stringResource(
                    if (upToDate) R.string.update_up_to_date else R.string.update_check_failed,
                ),
                color = Color.White,
            )
        },
        text = {
            Text(
                if (upToDate) {
                    stringResource(R.string.update_up_to_date_body, BuildConfig.VERSION_NAME)
                } else {
                    stringResource(R.string.update_check_failed_body)
                },
                color = Color.White.copy(alpha = 0.75f),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

/**
 * Qué es Nubo, de dónde salen sus datos y qué hace con ellos.
 *
 * Antes eran dos líneas con los nombres de Open-Meteo y AEMET. Los nombres solos
 * no dicen nada: lo que hay que poder contestar desde aquí es por qué los avisos
 * solo salen en España, por qué la calidad del aire se acaba a mitad de semana y
 * qué sale del teléfono, que son justo las preguntas que la app provoca.
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E2A3A),
        title = { Text(stringResource(R.string.about_nubo), color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Body(stringResource(R.string.about_intro))

                AboutSection(stringResource(R.string.about_data))
                Body(stringResource(R.string.about_data_forecast))
                Body(stringResource(R.string.about_data_air))
                Body(stringResource(R.string.about_data_alerts))
                Body(stringResource(R.string.about_data_places))
                Body(stringResource(R.string.about_data_astro))

                AboutSection(stringResource(R.string.about_privacy))
                Body(stringResource(R.string.about_privacy_body))

                AboutSection(stringResource(R.string.about_updates))
                Body(stringResource(R.string.about_updates_body))

                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(
                        R.string.about_build,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    ),
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 12.sp,
                )
                Text(
                    stringResource(R.string.about_repository),
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

@Composable
private fun AboutSection(title: String) {
    Spacer(Modifier.height(16.dp))
    Text(
        title.uppercase(),
        color = Color.White.copy(alpha = 0.4f),
        fontSize = 11.sp,
        fontWeight = FontWeight.W600,
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Body(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.75f),
        fontSize = 14.sp,
        lineHeight = 20.sp,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

/**
 * El permiso de notificaciones está denegado y el sistema ya no vuelve a
 * preguntar.
 *
 * A partir de la segunda negativa Android deja de abrir su diálogo y devuelve
 * "no" sin enseñar nada, así que sin esto el interruptor se quedaría apagado
 * sin explicación, como si estuviera roto.
 */
@Composable
fun NotificationsBlockedDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E2A3A),
        title = { Text(stringResource(R.string.notifications_blocked), color = Color.White) },
        text = {
            Text(
                stringResource(R.string.notifications_blocked_body),
                color = Color.White.copy(alpha = 0.75f),
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close), color = Color.White.copy(alpha = 0.55f))
            }
        },
    )
}
