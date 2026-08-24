package com.nubo.nubo.ui.weather

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nubo.nubo.BuildConfig
import com.nubo.nubo.data.remote.AvailableUpdate
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
        title = { Text("¡Nueva actualización!", color = Color.White) },
        text = {
            Column {
                if (!downloading) {
                    Text(
                        "La versión ${update.version} de Nubo está disponible. " +
                            "¿Quieres descargarla e instalarla ahora?",
                        color = Color.White.copy(alpha = 0.75f),
                    )
                } else {
                    Text("Descargando actualización…", color = Color.White.copy(alpha = 0.75f))
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
                    Text("Actualizar")
                }
            }
        },
        dismissButton = {
            if (!downloading) {
                TextButton(onClick = onDismiss) {
                    Text("Más tarde", color = Color.White.copy(alpha = 0.55f))
                }
            }
        },
    )
}

/** Información de la app y créditos de las fuentes de datos. */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E2A3A),
        title = { Text("Acerca de Nubo", color = Color.White) },
        text = {
            Column {
                Text(
                    "Versión ${BuildConfig.VERSION_NAME}",
                    color = Color.White.copy(alpha = 0.75f),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Predicción de Open-Meteo. Avisos meteorológicos de AEMET " +
                        "(Agencia Estatal de Meteorología).",
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}
