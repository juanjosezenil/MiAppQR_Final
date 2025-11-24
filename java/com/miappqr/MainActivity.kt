
package com.miappqr

import android.content.Context
import android.graphics.BitmapFactory
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val qrLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            procesarQrYEnviar(result.contents)
        } else {
            Toast.makeText(this, "No se leyó ningún contenido del QR", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // UI 100% por código: dos botones (cámara y prueba desde archivo)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        val btnScan = Button(this).apply { text = "Escanear QR con cámara" }
        val btnTest = Button(this).apply { text = "Probar desde archivo (assets/test_qr.png)" }

        root.addView(
            btnScan, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            btnTest, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 24 }
        )
        setContentView(root)

        btnScan.setOnClickListener { qrLauncher.launch(ScanOptions()) }

        btnTest.setOnClickListener {
            try {
                val inputStream = assets.open("test_qr.png") // coloca el archivo en app/src/main/assets/test_qr.png
                val bitmap = BitmapFactory.decodeStream(inputStream)
                val qrText = decodeQrFromBitmap(bitmap)
                if (qrText.isNullOrEmpty()) {
                    Toast.makeText(this, "No se pudo decodificar el QR del archivo", Toast.LENGTH_LONG).show()
                } else {
                    procesarQrYEnviar(qrText)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error cargando archivo", e)
                Toast.makeText(
                    this,
                    "Error cargando archivo: ${e::class.java.simpleName} - ${e.message ?: "sin mensaje"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun procesarQrYEnviar(qrTextRaw: String) {
        val qrText = qrTextRaw.replace("\r", "").replace("\n", "").trim()
        val qrData = qrText.split(',', ';', '|').map { it.trim() }.filter { it.isNotEmpty() }

        if (qrData.size < 3) {
            Toast.makeText(
                this,
                "QR inválido: faltan datos (esperado: Escuela,Matricula,Alumno)",
                Toast.LENGTH_LONG
            ).show()
            Log.e("MainActivity", "Contenido QR recibido: '$qrText'")
            return
        }

        val escuela = qrData[0]
        val matricula = qrData[1]
        val alumno = qrData[2]

        val fechaHora = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val macAddress = getMacAddress()

        // Lanzamos corrutina en Main; el servicio ejecuta red en IO (seguro)
        CoroutineScope(Dispatchers.Main).launch {
            try {
                GoogleSheetsService.enviarDatos(
                    context = this@MainActivity,
                    escuela = escuela,
                    matricula = matricula,
                    alumno = alumno,
                    fechaHora = fechaHora,
                    mac = macAddress
                )
                Toast.makeText(this@MainActivity, "Registro de prueba enviado", Toast.LENGTH_SHORT).show()
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e("MainActivity", "Fallo al enviar", e)
                Toast.makeText(
                    this@MainActivity,
                    "Error al enviar: ${e::class.java.simpleName} - ${e.message ?: "sin mensaje"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun getMacAddress(): String {
        return try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiManager.connectionInfo.macAddress ?: "02:00:00:00:00:00"
        } catch (e: Exception) {
            "02:00:00:00:00:00"
        }
    }
}
