package com.nubo.nubo.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.nubo.nubo.domain.model.Units

/**
 * Unidades con las que se pintan las cifras, disponibles en toda la interfaz.
 *
 * Van por aquí y no como parámetro porque las toca **casi todo** —la cabecera,
 * las horas, los días, la lista del menú— y arrastrarlas por cada firma llenaría
 * de ruido componentes que no tienen nada que ver entre sí. Es exactamente el
 * caso para el que existe un `CompositionLocal`: un ajuste de presentación que
 * cambia muy de vez en cuando y que lee mucha gente.
 *
 * Es `static` a propósito: al cambiarlas se recompone el árbol entero, que es
 * lo que hay que hacer cuando cambia una unidad, y a cambio leerlas no cuesta
 * seguimiento en cada sitio donde se leen.
 */
val LocalUnits = staticCompositionLocalOf { Units() }
