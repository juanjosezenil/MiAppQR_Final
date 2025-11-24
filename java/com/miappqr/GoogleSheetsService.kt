
package com.miappqr

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.security.interfaces.RSAPrivateKey
import java.util.Date

object GoogleSheetsService {
    private const val SPREADSHEET_ID = "1mSmSfM9dx0XoBW29AHVagOXGgWqRwq8s17VPXX0gIhw" // Tu ID real
    private const val RANGE = "Registro" // Pestaña exacta en tu Sheet

    /**
     * Envía datos a Google Sheets (PRUEBA MÍNIMA: inserta una celda con 'TEST_MINIMO').
     * TODO (cuando regreses): reemplazar el body para enviar A–E (escuela, matrícula, alumno, fechaHora, mac).
     */
    suspend fun enviarDatos(
        context: Context,
        escuela: String,
        matricula: String,
        alumno: String,
        fechaHora: String,
        mac: String
    ) = withContext(Dispatchers.IO) {
        try {
            val token = obtenerToken(context) // también corre en IO

            // Construir URL con parámetros recomendados para append
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("sheets.googleapis.com")
                .addPathSegments("v4/spreadsheets/$SPREADSHEET_ID/values/$RANGE:append")
                .addQueryParameter("valueInputOption", "RAW")            // o "USER_ENTERED"
                .addQueryParameter("insertDataOption", "INSERT_ROWS")    // inserta filas
                .addQueryParameter("includeValuesInResponse", "true")    // ver updatedRange
                .build()


            // Datos
            val values = JSONArray().put(
                JSONArray()
                    .put(escuela)
                    .put(matricula)
                    .put(alumno)
                    .put(fechaHora)
                    .put(mac)
            )
            val bodyJson = JSONObject().apply {
                put("majorDimension", "ROWS")
                put("values", values)
            }


            // Logs de diagnóstico (se verán en Logcat)
            Log.d("GoogleSheetsService", "URL: $url")
            Log.d("GoogleSheetsService", "Body JSON:\n${bodyJson.toString(2)}")

            val client = OkHttpClient()
            val body = bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                Log.d("GoogleSheetsService", "HTTP ${response.code}")
                Log.d("GoogleSheetsService", "Respuesta:\n$responseBody")

                if (!response.isSuccessful) {
                    throw IOException("Append error ${response.code}: $responseBody")
                } else {
                    Log.d("GoogleSheetsService", "Append OK: ${response.code}")
                    // Éxito típico: "updates.updatedRange": "Registro!A2:A2"
                }
            }
        } catch (e: Exception) {
            Log.e(
                "GoogleSheetsService",
                "Error al enviar datos: ${e::class.java.simpleName} - ${e.message ?: "sin mensaje"}",
                e
            )
            throw e
        }
    }

    /**
     * Obtiene el token OAuth 2.0 usando JWT firmado con la clave privada (Service Account).
     * Corre en Dispatchers.IO para evitar NetworkOnMainThreadException.
     */
    private suspend fun obtenerToken(context: Context): String = withContext(Dispatchers.IO) {
        val inputStream: InputStream = context.assets.open("credentials.json")
        val json = JSONObject(inputStream.bufferedReader().use { it.readText() })

        val privateKeyPem = json.getString("private_key")
        val clientEmail = json.getString("client_email")

        val privateKey = parsePrivateKey(privateKeyPem)

        val nowSec = System.currentTimeMillis() / 1000
        val jwt = com.auth0.jwt.JWT.create()
            .withIssuer(clientEmail) // iss
            .withAudience("https://oauth2.googleapis.com/token") // aud
            .withClaim("scope", "https://www.googleapis.com/auth/spreadsheets") // scope
            .withIssuedAt(Date(nowSec * 1000)) // iat
            .withExpiresAt(Date((nowSec + 3600) * 1000)) // exp
            .sign(com.auth0.jwt.algorithms.Algorithm.RSA256(null, privateKey))

        val client = OkHttpClient()
        val formBody = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
            .add("assertion", jwt)
            .build()

        val request = Request.Builder()
            .url("https://oauth2.googleapis.com/token")
            .post(formBody)
            .build()

        client.newCall(request).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                Log.e("GoogleSheetsService", "Token error: code=${resp.code}, body=$bodyStr")
                throw IOException("Token request failed: ${resp.code} $bodyStr")
            }
            val jsonResp = JSONObject(bodyStr)
            return@use jsonResp.getString("access_token")
        }
    }

    /**
     * Convierte la clave privada PEM en RSAPrivateKey.
     * Si tu minSdk < 26 usa android.util.Base64 en vez de java.util.Base64.
     */
    private fun parsePrivateKey(privateKeyPem: String): RSAPrivateKey {
        val cleanPem = privateKeyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val decoded: ByteArray = try {
            java.util.Base64.getDecoder().decode(cleanPem)
        } catch (e: Throwable) {
            android.util.Base64.decode(cleanPem, android.util.Base64.DEFAULT)
        }
        val keySpec = PKCS8EncodedKeySpec(decoded)
        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(keySpec)
        return privateKey as RSAPrivateKey
    }
}
