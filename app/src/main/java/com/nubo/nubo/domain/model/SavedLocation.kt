package com.nubo.nubo.domain.model

/**
 * Localización guardada por el usuario.
 *
 * La igualdad se define solo por [municipioId] para que la lista de guardadas
 * no admita duplicados aunque el nombre venga escrito distinto.
 */
data class SavedLocation(
    val municipioId: String,
    val nombre: String,
) {
    override fun equals(other: Any?): Boolean =
        other is SavedLocation && other.municipioId == municipioId

    override fun hashCode(): Int = municipioId.hashCode()

    /** Serialización para DataStore. Formato: `municipioId|nombre`. */
    fun toPrefsString(): String = "$municipioId|$nombre"

    companion object {
        /**
         * Crea una localización normalizando el nombre que llega de AEMET.
         */
        fun of(municipioId: String, nombre: String): SavedLocation =
            SavedLocation(municipioId, formatNombre(nombre))

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

        /** Deserialización desde DataStore. */
        fun fromPrefsString(s: String): SavedLocation? {
            val parts = s.split("|")
            if (parts.size < 2) return null
            return SavedLocation(
                municipioId = parts[0],
                // El nombre puede contener '|', así que se recompone entero.
                nombre = parts.drop(1).joinToString("|"),
            )
        }
    }
}
