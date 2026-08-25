package com.nubo.nubo.domain.model

/**
 * Localización guardada por el usuario.
 *
 * Lleva encima todo lo que hace falta para funcionar sin red —coordenadas y
 * zona horaria— porque desde que la búsqueda es mundial ya no hay ningún
 * maestro descargado del que sacarlo: con AEMET bastaba el código INE, con
 * Open-Meteo no existe tal listado local.
 *
 * La igualdad se define solo por [id] para que la lista de guardadas no admita
 * duplicados aunque el nombre venga escrito distinto.
 */
data class SavedLocation(
    val locationId: String,
    val nombre: String,
    val lat: Double? = null,
    val lon: Double? = null,
    /** Zona horaria IANA del sitio, p. ej. "Europe/Madrid". */
    val timeZone: String? = null,
    /** ISO-3166 alfa-2. Solo "ES" tiene avisos, que los da AEMET. */
    val countryCode: String? = null,
    /** Región y país, para distinguir homónimos en la lista de búsqueda. */
    val region: String? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is SavedLocation && other.locationId == locationId

    override fun hashCode(): Int = locationId.hashCode()

    /** Si es una de las guardadas antes de abrir la app al mundo. */
    val isLegacyIne: Boolean get() = !locationId.contains(':')

    val hasCoordinates: Boolean get() = lat != null && lon != null

    /**
     * Si le corresponden avisos de AEMET.
     *
     * Las guardadas del formato antiguo no tienen país porque cuando se
     * guardaron la app solo cubría España, así que se dan por españolas.
     */
    val isInSpain: Boolean get() = countryCode == "ES" || (countryCode == null && isLegacyIne)

    /** Serialización para DataStore. */
    fun toPrefsString(): String = listOf(
        FORMAT_MARKER,
        locationId,
        lat?.toString().orEmpty(),
        lon?.toString().orEmpty(),
        timeZone.orEmpty(),
        countryCode.orEmpty(),
        region.orEmpty(),
        // El nombre va al final porque es el único campo que puede traer el
        // separador dentro ("Baena, La Fuente|…" y similares).
        nombre,
    ).joinToString(FIELD_SEPARATOR)

    companion object {
        /**
         * Separador de campos.
         *
         * Se mantiene el '|' del formato viejo para que una entrada nueva sea
         * ilegible con el parser antiguo y viceversa se detecte por el marcador.
         */
        private const val FIELD_SEPARATOR = "|"

        /** Cabecera que distingue el formato nuevo del `id|nombre` anterior. */
        internal const val FORMAT_MARKER = "v2"

        private const val FIELD_COUNT = 8

        /**
         * Crea una localización normalizando el nombre que llega de AEMET.
         */
        fun of(locationId: String, nombre: String): SavedLocation =
            SavedLocation(locationId = locationId, nombre = formatNombre(nombre))

        /**
         * AEMET devuelve los nombres con el artículo pospuesto ("Coruña, A"),
         * y aquí se reordenan a la forma natural ("A Coruña").
         */
        fun formatNombre(rawName: String): String {
            if (!rawName.contains(", ")) return rawName

            val parts = rawName.split(", ")
            if (parts.size == 2) {
                val article = parts[1].trim()
                val name = parts[0].trim()
                // Solo se da la vuelta si lo que sigue a la coma es corto: un
                // artículo ("A", "La", "El", "Els"), no un segundo topónimo.
                if (article.length <= 4) return "$article $name"
            }
            return rawName
        }

        /**
         * Deserialización desde DataStore.
         *
         * Acepta los dos formatos. El viejo —`códigoINE|nombre`— es el que
         * tienen guardado quienes vienen de una versión anterior, incluida la
         * app Flutter, y se lee sin coordenadas: las rellena
         * `WeatherStorage` al cargar.
         */
        fun fromPrefsString(s: String): SavedLocation? {
            val parts = s.split(FIELD_SEPARATOR)
            if (parts.size < 2) return null

            if (parts[0] != FORMAT_MARKER) return legacyFromParts(parts)
            if (parts.size < FIELD_COUNT) return null

            return SavedLocation(
                locationId = parts[1],
                nombre = parts.drop(FIELD_COUNT - 1).joinToString(FIELD_SEPARATOR),
                lat = parts[2].toDoubleOrNull(),
                lon = parts[3].toDoubleOrNull(),
                timeZone = parts[4].takeIf { it.isNotBlank() },
                countryCode = parts[5].takeIf { it.isNotBlank() },
                region = parts[6].takeIf { it.isNotBlank() },
            )
        }

        private fun legacyFromParts(parts: List<String>): SavedLocation = SavedLocation(
            locationId = parts[0],
            // El nombre puede contener '|', así que se recompone entero.
            nombre = parts.drop(1).joinToString(FIELD_SEPARATOR),
        )
    }
}
