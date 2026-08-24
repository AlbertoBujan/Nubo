package com.nubo.nubo.data.remote

import org.json.JSONObject

/**
 * Acceso a AEMET OpenData.
 *
 * Su API funciona en dos pasos: el endpoint devuelve un JSON con una URL
 * temporal firmada en el campo `datos`, y el contenido real hay que pedirlo a
 * esa segunda URL (esta ya sin api_key).
 */
class AemetApi(private val http: HttpClient = HttpClient()) {

    /**
     * Resuelve un endpoint de AEMET y devuelve el cuerpo del paso 2.
     *
     * Devuelve `null` si cualquiera de los dos pasos falla, para que quien
     * llame degrade con elegancia: un fallo de AEMET no debe tumbar la app,
     * solo dejarla sin avisos.
     */
    suspend fun fetchData(path: String, timeoutSeconds: Long = 15): String? {
        val step1 = http.get(
            "$BASE_URL$path",
            headers = mapOf("api_key" to API_KEY),
            timeoutSeconds = timeoutSeconds,
        )
        if (!step1.isSuccess) return null

        val datosUrl = try {
            JSONObject(step1.decodeText()).optString("datos").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        } ?: return null

        val step2 = http.get(datosUrl, timeoutSeconds = timeoutSeconds)
        if (!step2.isSuccess) return null

        return step2.decodeText()
    }

    companion object {
        const val BASE_URL = "https://opendata.aemet.es/opendata"

        // NOTA: la clave viaja embebida en el binario, igual que en la app
        // Flutter. Es una clave pública gratuita de AEMET OpenData y ya está
        // publicada en los APK anteriores y en el historial del repositorio,
        // así que moverla ahora no la des-filtraría. Conviene rotarla y
        // servirla desde el build si en algún momento pasa a importar.
        const val API_KEY =
            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJiaXJ0ZWJzQGdtYWlsLmNvbSIsImp0aSI6ImYwM2Ux" +
                "MjFmLTE2ODktNDdkMS1hYjNhLWI0MThlM2ZmMWNjMiIsImlzcyI6IkFFTUVUIiwiaWF0" +
                "IjoxNzcxNDE3OTk3LCJ1c2VySWQiOiJmMDNlMTIxZi0xNjg5LTQ3ZDEtYWIzYS1iNDE4" +
                "ZTNmZjFjYzIiLCJyb2xlIjoiIn0.npwJf-68OE2s0kIsRHVqjMqtmR9tedsgYrD03pjuYHc"
    }
}

/**
 * Correspondencia entre el código de provincia del INE (los 2 primeros dígitos
 * del código de municipio) y el código de área de AEMET, que en realidad
 * identifica la comunidad autónoma.
 *
 * Esta tabla se obtuvo a base de prueba y error contra el servicio real y no
 * está documentada en ningún sitio, así que se porta literal desde la app
 * Flutter.
 */
object AemetAreas {

    val provinciaToArea: Map<String, String> = mapOf(
        // Andalucía (61)
        "04" to "61", // Almería
        "11" to "61", // Cádiz
        "14" to "61", // Córdoba
        "18" to "61", // Granada
        "21" to "61", // Huelva
        "23" to "61", // Jaén
        "29" to "61", // Málaga
        "41" to "61", // Sevilla

        // Aragón (62)
        "22" to "62", // Huesca
        "44" to "62", // Teruel
        "50" to "62", // Zaragoza

        // Asturias (63)
        "33" to "63",

        // Illes Balears (64)
        "07" to "64",

        // Canarias (65)
        "35" to "65", // Las Palmas
        "38" to "65", // S.C. de Tenerife

        // Cantabria (66)
        "39" to "66",

        // Castilla y León (67)
        "05" to "67", // Ávila
        "09" to "67", // Burgos
        "24" to "67", // León
        "34" to "67", // Palencia
        "37" to "67", // Salamanca
        "40" to "67", // Segovia
        "42" to "67", // Soria
        "47" to "67", // Valladolid
        "49" to "67", // Zamora

        // Castilla-La Mancha (68)
        "02" to "68", // Albacete
        "13" to "68", // Ciudad Real
        "16" to "68", // Cuenca
        "19" to "68", // Guadalajara
        "45" to "68", // Toledo

        // Cataluña (69)
        "08" to "69", // Barcelona
        "17" to "69", // Girona
        "25" to "69", // Lleida
        "43" to "69", // Tarragona

        // Extremadura (70)
        "06" to "70", // Badajoz
        "10" to "70", // Cáceres

        // Galicia (71)
        "15" to "71", // A Coruña
        "27" to "71", // Lugo
        "32" to "71", // Ourense
        "36" to "71", // Pontevedra

        // Madrid (72)
        "28" to "72",

        // Murcia (73)
        "30" to "73",

        // Navarra (74)
        "31" to "74",

        // País Vasco (75)
        "01" to "75", // Álava
        "48" to "75", // Bizkaia
        "20" to "75", // Gipuzkoa

        // La Rioja (76)
        "26" to "76",

        // Comunidad Valenciana (77)
        "03" to "77", // Alicante
        "12" to "77", // Castellón
        "46" to "77", // Valencia

        // Ceuta (78) y Melilla (79)
        "51" to "78",
        "52" to "79",
    )

    /** Código de área de AEMET para un municipio del INE, o `null` si no consta. */
    fun areaForMunicipio(municipioId: String): String? {
        if (municipioId.length < 2) return null
        return provinciaToArea[municipioId.substring(0, 2)]
    }

    /** Los 2 primeros dígitos del código de municipio son la provincia. */
    fun provinciaOf(municipioId: String): String? =
        if (municipioId.length < 2) null else municipioId.substring(0, 2)
}
