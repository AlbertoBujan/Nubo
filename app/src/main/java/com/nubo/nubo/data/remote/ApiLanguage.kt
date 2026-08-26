package com.nubo.nubo.data.remote

import java.util.Locale

/**
 * Idioma en el que se piden los nombres de sitio a las APIs.
 *
 * Sale del idioma del teléfono, pero **acotado a los que la app declara**. No
 * se manda tal cual `Locale.getDefault()` por dos motivos: la geocodificación
 * de Open-Meteo solo admite un puñado de idiomas, y sobre todo porque los
 * nombres administrativos que devuelve se podan luego con reglas escritas a
 * mano —"Comunidad Autónoma de ", " County"— que solo existen para el español
 * y el inglés. Pedir un idioma cuyas formas nadie ha podado sería enseñar la
 * morralla entera.
 */
internal fun apiLanguage(): String =
    if (Locale.getDefault().language == "es") "es" else "en"
