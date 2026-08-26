package com.nubo.nubo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

/**
 * Lo que tarda un número en llegar a su valor.
 *
 * Generoso a propósito: lo que se quiere ver no es el recuento entero sino el
 * final, y con menos tiempo los últimos dígitos pasaban de golpe por muy
 * frenada que fuese la curva.
 */
private const val COUNT_MILLIS = 2200

/**
 * Sale disparado y va frenando hasta posarse en el dato.
 *
 * Es lo contrario de una curva que arranca despacio: aquí lo interesante no es
 * el arranque —el cero no dice nada— sino el final. La primera versión frenaba
 * tanto que el número llegaba a su valor a mitad de la animación y el resto del
 * tiempo no pasaba nada: se leía como que acababa de golpe. Esta reparte el
 * frenado, así que **los dígitos siguen cambiando hasta el final**, cada vez
 * más despacio, que es lo que se quería.
 */
internal val SETTLE = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)

/**
 * Un número que sube —o baja— hasta su valor en vez de aparecer puesto.
 *
 * Cuenta cuando [trigger] cambia, y [trigger] **es el dato que se estrena** —la
 * hora de la última actualización— y no un simple sí/no. Con un booleano no
 * funcionaba: al refrescar ya valía `true` de la vez anterior, así que no
 * cambiaba nada y no se disparaba nada. En reposo llega nulo y el número sale
 * puesto: si contase en cada composición, cambiar de ciudad y volver haría
 * bailar una cifra que ya estaba ahí, y la animación pasaría de decir "esto
 * acaba de actualizarse" a no decir nada.
 *
 * Arranca en cero y no en el valor anterior a propósito: entre 19° y 20° un
 * recuento no se vería, y lo que se quiere enseñar no es el salto sino que hay
 * un dato nuevo.
 */
@Composable
fun countUpTo(target: Int?, trigger: Any?, delayMillis: Int = 0): Int? {
    if (target == null) return null

    // Sin dato que estrenar no se crea nada que animar: es el caso de siempre
    // —una ciudad ya cargada— y no tiene por qué pagar un fotograma de más.
    if (trigger == null) return target

    val value = remember(trigger) { Animatable(0f) }
    var settled by remember(trigger) { mutableStateOf(false) }

    LaunchedEffect(trigger) {
        value.animateTo(
            target.toFloat(),
            tween(COUNT_MILLIS, delayMillis = delayMillis, easing = SETTLE),
        )
        // A partir de aquí manda el dato: dejarlo colgando del `Animatable`
        // redondearía para siempre un valor que ya es exacto.
        settled = true
    }

    return if (settled) target else value.value.roundToInt()
}

/** Lo que tarda una flecha en girar del norte a su dirección. */
private const val SWEEP_MILLIS = 1600

/**
 * Ángulo que gira desde el norte hasta [target] cuando hay dato que estrenar.
 *
 * Se gira por el camino corto: una dirección de 350° se alcanza retrocediendo
 * diez grados, no dando casi la vuelta entera. Sin eso, media rosa de los
 * vientos daría un volantazo que no dice nada de lo que hace el viento.
 */
@Composable
fun sweepTo(target: Float, trigger: Any?, delayMillis: Int = 0): Float {
    if (trigger == null) return target

    val shortest = shortestSweep(target)
    val angle = remember(trigger) { Animatable(0f) }
    var settled by remember(trigger) { mutableStateOf(false) }

    LaunchedEffect(trigger, shortest) {
        angle.animateTo(
            shortest,
            tween(SWEEP_MILLIS, delayMillis = delayMillis, easing = SETTLE),
        )
        settled = true
    }

    return if (settled) target else angle.value
}

/**
 * El mismo ángulo que [target] pero expresado en (-180°, 180°], que es el
 * camino corto desde el norte.
 */
internal fun shortestSweep(target: Float): Float {
    val wrapped = ((target % 360f) + 540f) % 360f - 180f
    // -180 y 180 son el mismo sitio; se prefiere el positivo para que media
    // vuelta gire siempre en el mismo sentido y no dependa del redondeo.
    return if (wrapped <= -180f) 180f else wrapped
}

/** Lo que tarda una barra en llenarse. */
private const val FILL_MILLIS = 1400

/**
 * De 0 a 1 cuando hay dato que estrenar, y 1 el resto del tiempo.
 *
 * Es el equivalente del recuento para lo que no son cifras: una barra que se
 * llena, una curva que se traza. Frena igual que los números para que todo lo
 * que se estrena a la vez se mueva con el mismo gesto.
 */
@Composable
fun fillProgress(trigger: Any?, delayMillis: Int = 0): Float {
    if (trigger == null) return 1f

    val progress = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) {
        progress.animateTo(1f, tween(FILL_MILLIS, delayMillis = delayMillis, easing = SETTLE))
    }
    return progress.value
}
