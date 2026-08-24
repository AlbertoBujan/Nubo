package com.nubo.nubo.domain.weather

/** Una hora suelta reducida a lo que necesita la agregación diaria. */
data class HourSample(
    /** Código WMO numérico, ya sin sufijo de noche. */
    val code: Int,
    val isDay: Boolean,
)

/**
 * Decide qué código WMO representa mejor un día completo a partir de sus horas.
 *
 * Existe porque Open-Meteo devuelve en `daily.weather_code` el fenómeno *más
 * significativo* del día, no el más duradero: una sola hora de nubes en un día
 * despejado convertía el día entero en "intervalos nubosos", y el icono de
 * "Próximos días" salía sistemáticamente pesimista.
 *
 * El criterio es: gana el fenómeno significativo más severo que dure al menos
 * sus horas mínimas ([WeatherCodeGroup.minHours]) —así una tormenta breve pero
 * real sigue avisando— y, si ninguno llega al umbral, la nubosidad dominante
 * ponderando las horas de luz.
 */
object DailyCodeAggregator {

    /**
     * Peso de una hora diurna al decidir la nubosidad dominante del día.
     *
     * Las horas de noche cuentan 1. Así una noche nublada no gana a un día
     * entero despejado, que es lo que el usuario percibe como tiempo del día.
     */
    const val DAY_HOUR_WEIGHT = 2

    /**
     * Devuelve el código dominante, o `null` si no hay horas con las que
     * decidir — en ese caso quien llame debería recurrir al código diario que
     * dé la API.
     */
    fun dominantCode(hours: List<HourSample>): Int? {
        if (hours.isEmpty()) return null

        val hoursByGroup = mutableMapOf<WeatherCodeGroup, Int>()
        val weightByGroup = mutableMapOf<WeatherCodeGroup, Int>()
        val codeCountsByGroup = mutableMapOf<WeatherCodeGroup, MutableMap<Int, Int>>()

        for (hour in hours) {
            val group = WeatherCodeGroup.fromCode(hour.code.toString())
            hoursByGroup[group] = (hoursByGroup[group] ?: 0) + 1
            weightByGroup[group] =
                (weightByGroup[group] ?: 0) + if (hour.isDay) DAY_HOUR_WEIGHT else 1
            val counts = codeCountsByGroup.getOrPut(group) { mutableMapOf() }
            counts[hour.code] = (counts[hour.code] ?: 0) + 1
        }

        val winner = pickGroup(hoursByGroup, weightByGroup)
        return representativeCode(codeCountsByGroup.getValue(winner))
    }

    /** Elige la familia que representa al día. */
    private fun pickGroup(
        hoursByGroup: Map<WeatherCodeGroup, Int>,
        weightByGroup: Map<WeatherCodeGroup, Int>,
    ): WeatherCodeGroup {
        // 1. Fenómenos significativos que duran lo suficiente → gana el más severo.
        val significant = hoursByGroup.keys
            .filter { it.isSignificant && hoursByGroup.getValue(it) >= it.minHours }
        if (significant.isNotEmpty()) {
            return significant.maxBy { it.severity }
        }

        // 2. Si no, la nubosidad dominante: más peso gana, y a igualdad el cielo
        //    más cargado, para no vender como despejado un día a medias.
        val cloudGroups = weightByGroup.keys.filter { !it.isSignificant }
        if (cloudGroups.isNotEmpty()) {
            return cloudGroups.maxWith(
                compareBy({ weightByGroup.getValue(it) }, { it.severity }),
            )
        }

        // 3. Día compuesto solo por fenómenos breves (ninguno llega a su umbral):
        //    gana el más duradero, y a igualdad el más severo.
        return hoursByGroup.keys.maxWith(
            compareBy({ hoursByGroup.getValue(it) }, { it.severity }),
        )
    }

    /**
     * Dentro de la familia ganadora, elige el código concreto más frecuente
     * (a igualdad, el de mayor intensidad) para conservar el matiz del icono y
     * de la descripción: "lluvia fuerte" en vez de un genérico "lluvia".
     */
    private fun representativeCode(codeCounts: Map<Int, Int>): Int =
        codeCounts.keys.maxWith(compareBy({ codeCounts.getValue(it) }, { it }))
}
