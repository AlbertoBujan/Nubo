package com.nubo.nubo.data.remote

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * De dónde saca la app la clave de AEMET cuando la embebida caduca.
 *
 * Lo que se fija aquí es que el respaldo no estorbe —mientras la de dentro
 * valga, no se baja nada— y que renovar no pueda empeorar las cosas: ni
 * cambiar una clave buena por basura, ni insistir con la misma que acaba de
 * fallar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AemetKeyringTest {

    private val embebida = "eyJhbGciOiJIUzI1NiJ9.ZW1iZWJpZGFlbWJlYmlkYWVtYmViaWRh.firmaembebida"
    private val publicada = "eyJhbGciOiJIUzI1NiJ9.cHVibGljYWRhcHVibGljYWRhcHVibGk.firmapublicada"

    private lateinit var server: MockWebServer

    @Before
    fun arrancaServidor() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun paraServidor() {
        server.shutdown()
    }

    private fun keyring() = AemetKeyring(
        http = HttpClient(maxRetries = 1),
        bakedIn = embebida,
        url = server.url("/aemet-key.json").toString(),
    )

    private fun publica(json: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(json))
    }

    @Test
    fun `mientras nadie renueve se usa la embebida y no se baja nada`() = runTest {
        assertEquals(embebida, keyring().current())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `al fallar la embebida se coge la publicada`() = runTest {
        publica("""{"key":"$publicada","expires":"2027-02-18"}""")
        val keyring = keyring()

        assertEquals(publicada, keyring.renewed(embebida))
        assertEquals(publicada, keyring.current())
    }

    @Test
    fun `si la publicada es la que acaba de fallar no se reintenta`() = runTest {
        // Insistir con la misma clave es gastar otra petición para el mismo
        // 401: el problema no era la clave.
        publica("""{"key":"$embebida"}""")

        assertNull(keyring().renewed(embebida))
    }

    @Test
    fun `si no se puede bajar el fichero no hay renovacion`() = runTest {
        publica("no existe", code = 404)

        assertNull(keyring().renewed(embebida))
    }

    @Test
    fun `una pagina de error no se confunde con una clave`() = runTest {
        // Esa URL puede contestar HTML; tomarlo por clave sustituiría una que
        // funciona por basura.
        publica("<!DOCTYPE html><html><body>404</body></html>")

        assertNull(keyring().renewed(embebida))
    }

    @Test
    fun `un fichero sin clave con forma de JWT se descarta`() = runTest {
        publica("""{"key":"esto-no-es-un-jwt"}""")

        assertNull(keyring().renewed(embebida))
    }

    @Test
    fun `dos ciudades fallando a la vez solo bajan el fichero una vez`() = runTest {
        publica("""{"key":"$publicada"}""")
        val keyring = keyring()

        val una = async { keyring.renewed(embebida) }
        val otra = async { keyring.renewed(embebida) }

        assertEquals(publicada, una.await())
        assertEquals(publicada, otra.await())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `lee la clave del fichero publicado`() {
        assertEquals(
            publicada,
            AemetKeyring.parseKey("""{"key":"$publicada","expires":"2027-02-18"}"""),
        )
    }
}
