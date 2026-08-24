package com.nubo.nubo.data.local

import com.nubo.nubo.data.local.FlutterPreferencesMigration.Companion.LIST_PREFIX
import com.nubo.nubo.data.local.FlutterPreferencesMigration.Companion.decodeStringList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cubre la lectura del formato en el que `shared_preferences` de Flutter
 * guardaba las ciudades, que es de lo que depende que nadie las pierda al
 * actualizar desde la versión Flutter.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FlutterPreferencesMigrationTest {

    @Test
    fun `el prefijo de lista es el que usa el plugin`() {
        // Base64 de "This is the prefix for a list."
        val decoded = String(android.util.Base64.decode(LIST_PREFIX, android.util.Base64.DEFAULT))
        assertEquals("This is the prefix for a list.", decoded)
    }

    @Test
    fun `decodifica el formato real que escribe la app Flutter`() {
        // Valor leído literalmente del FlutterSharedPreferences.xml de Nubo
        // v0.1.36 en el emulador. El '!' entre el prefijo y el JSON es lo que
        // hacía fallar la primera versión de la migración.
        val raw = """VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu!["15032|Curtis"]"""

        assertEquals(listOf("15032|Curtis"), decodeStringList(raw))
    }

    @Test
    fun `decodifica varias ciudades conservando el orden`() {
        val raw = LIST_PREFIX + """!["15030|A Coruña","28079|Madrid"]"""

        assertEquals(listOf("15030|A Coruña", "28079|Madrid"), decodeStringList(raw))
    }

    @Test
    fun `tambien acepta el prefijo sin el separador`() {
        val raw = LIST_PREFIX + """["28079|Madrid"]"""

        assertEquals(listOf("28079|Madrid"), decodeStringList(raw))
    }

    @Test
    fun `decodifica tambien un JSON sin prefijo`() {
        assertEquals(listOf("15030|Curtis"), decodeStringList("""["15030|Curtis"]"""))
    }

    @Test
    fun `un valor suelto se trata como un unico elemento`() {
        assertEquals(listOf("15030|Curtis"), decodeStringList("15030|Curtis"))
    }

    @Test
    fun `un JSON corrupto no rompe la migracion`() {
        assertTrue(decodeStringList("$LIST_PREFIX[esto no es json").isEmpty())
    }

    @Test
    fun `una lista vacia se decodifica sin elementos`() {
        assertTrue(decodeStringList("$LIST_PREFIX[]").isEmpty())
    }
}
