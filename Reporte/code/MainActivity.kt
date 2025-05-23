package com.example.grafica

import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import cn.pedant.SweetAlert.SweetAlertDialog
import com.example.grafica.LoginActivity.Companion.useremail
import com.ingenieriajhr.blujhr.BluJhr
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import com.example.grafica.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    lateinit var blue: BluJhr
    var devicesBluetooth = ArrayList<String>()
    var graphViewVisible = true

    lateinit var ecgSeries: LineGraphSeries<DataPoint?>
    lateinit var ppgSeries: LineGraphSeries<DataPoint?>

    var receivingData = false
    var estadoConexion = BluJhr.Connected.False

    lateinit var loadSweet: SweetAlertDialog
    lateinit var errorSweet: SweetAlertDialog
    lateinit var okSweet: SweetAlertDialog
    lateinit var disconnection: SweetAlertDialog

    var ejeX = 0.6

    // Clases para procesamiento de señales y cálculo de PTT
    private lateinit var signalProcessor: SignalProcessor
    private lateinit var pttCalculator: PTTCalculator

    // Handler para detener la recolección después de 15 segundos
    private val handler = Handler(Looper.getMainLooper())

    // Runnable para ejecutar después de 15 segundos
    private val stopCollectionRunnable = Runnable {
        if (receivingData && signalProcessor.isCollectingData()) {
            // Detener la recolección
            signalProcessor.stopCollection()

            // Calcular y mostrar resultados
            calculateAndDisplayResults()

            // Cambiar estado y texto del botón
            receivingData = false
            binding.btnHistorial.text = "INICIAR MEDICIÓN"

            // Enviar comando para detener la transmisión
            if (estadoConexion == BluJhr.Connected.True) {
                blue.bluTx("0")
            }
        }
    }

    private fun rxReceived() {
        blue.loadDateRx(object : BluJhr.ReceivedData {
            override fun rxDate(rx: String) {
                try {
                    ejeX += 0.6

                    // Expresión regular para capturar todos los valores
                    // e(ecg) p(ppg) r(rPeak) b(pulsePeak) t(timestamp)
                    val regex = Regex("e(\\d+) p(\\d+) r(\\d+) b(\\d+) t(\\d+)")

                    val matchResult = regex.find(rx)

                    if (matchResult != null) {
                        // Extraer los valores y convertirlos
                        val ecg = matchResult.groupValues[1].toInt()
                        val ppg = matchResult.groupValues[2].toInt()
                        val isRPeak = matchResult.groupValues[3].toInt() == 1
                        val isPulseValley = matchResult.groupValues[4].toInt() == 1
                        val timestamp = matchResult.groupValues[5].toLong()

                        // Agregar los valores a las series para visualización
                        ecgSeries.appendData(DataPoint(ejeX, ecg.toDouble()), true, 10000)
                        ppgSeries.appendData(DataPoint(ejeX, ppg.toDouble()), true, 10000)

                        // Procesar la muestra si está en modo de recolección
                        if (receivingData) {
                            val shouldContinue = signalProcessor.processSample(ecg, ppg, isRPeak, isPulseValley, timestamp)

                            // Si ya no debe continuar, detener la recolección
                            if (!shouldContinue) {
                                handler.removeCallbacks(stopCollectionRunnable)
                                stopCollectionRunnable.run()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Error al recibir los datos: $e", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // Modificar textos de la UI con valores predeterminados
    private fun updateUILabels() {
        // Actualizar las etiquetas para mostrar que son valores de presión arterial
        binding.txtSistole.text = "Sistólica"
        binding.txtDiastole.text = "Diastólica"
        binding.txtBPM.text = "Pulso (BPM)"

        // Inicializar con valores cero
        binding.txtResultadoSistole.text = "0 mmHg"
        binding.txtResultadoDiastole.text = "0 mmHg"
        binding.txtResultadoBPM.text = "0"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar las clases para procesamiento
        signalProcessor = SignalProcessor()
        pttCalculator = PTTCalculator(signalProcessor)

        // Inicializa las etiquetas de la UI
        updateUILabels()

        initSweet()
        blue = BluJhr(this)
        blue.onBluetooth()

        binding.btnDispositivos.setOnClickListener {
            when (graphViewVisible) {
                true -> listaDispositivosVisible()
                false -> listaDispositivosOculta()
            }
        }

        binding.listDeviceBluetooth.setOnItemClickListener { adapterView, view, i, l ->
            if (devicesBluetooth.isNotEmpty()) {
                blue.connect(devicesBluetooth[i])
                initSweet()
                blue.setDataLoadFinishedListener(object : BluJhr.ConnectedBluetooth {
                    override fun onConnectState(state: BluJhr.Connected) {
                        estadoConexion = state
                        when (state) {
                            BluJhr.Connected.True -> {
                                loadSweet.dismiss()
                                okSweet.show()
                                listaDispositivosOculta()
                                rxReceived()
                            }
                            BluJhr.Connected.Pending -> {
                                loadSweet.show()
                            }
                            BluJhr.Connected.False -> {
                                loadSweet.dismiss()
                                errorSweet.show()
                            }
                            BluJhr.Connected.Disconnect -> {
                                loadSweet.dismiss()
                                disconnection.show()
                                listaDispositivosVisible()
                            }
                        }
                    }
                })
            }
        }

        initGraph()

        // Botón para iniciar/detener la recolección de datos
        binding.btnHistorial.setOnClickListener {
            if (estadoConexion == BluJhr.Connected.True) {
                if (!receivingData) {
                    // Iniciar recolección
                    signalProcessor.startCollection()
                    receivingData = true
                    binding.btnHistorial.text = "DETENER MEDICIÓN"

                    // Enviar comando para iniciar transmisión
                    blue.bluTx("1")

                    // Programar la detención después de 15 segundos
                    handler.postDelayed(stopCollectionRunnable, 15000)
                } else {
                    // Detener recolección manualmente
                    handler.removeCallbacks(stopCollectionRunnable)
                    signalProcessor.stopCollection()
                    receivingData = false
                    binding.btnHistorial.text = "INICIAR MEDICIÓN"

                    // Calcular y mostrar resultados
                    calculateAndDisplayResults()

                    // Enviar comando para detener transmisión
                    blue.bluTx("0")
                }
            } else {
                Toast.makeText(this, "Conecta un dispositivo primero", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Calcula y muestra los resultados de presión arterial y BPM en la interfaz de usuario.
     */
    private fun calculateAndDisplayResults() {
        if (signalProcessor.hasSufficientData()) {
            // Calcular PTT promedio y BPM
            val (avgSystolic, avgDiastolic, bpm) = pttCalculator.calculateAveragePTTs()

            // Calcular presión arterial basada en PTT
            val (systolicBP, diastolicBP, meanBP) = pttCalculator.calculateBloodPressure()

            // Mostrar resultados de presión arterial en la interfaz
            binding.txtResultadoSistole.text = "$systolicBP mmHg"
            binding.txtResultadoDiastole.text = "$diastolicBP mmHg"
            binding.txtResultadoBPM.text = "$bpm"

            // Guardar resultados en Firebase
            if (avgSystolic > 0 || avgDiastolic > 0) {
                pttCalculator.saveResultsToFirebase(useremail) { success, message ->
                    if (success) {
                        runOnUiThread {
                            Toast.makeText(this, "Datos de presión arterial guardados correctamente", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this, "Error al guardar: $message", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        } else {
            Toast.makeText(this, "No hay suficientes datos para el cálculo de presión arterial", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initSweet() {
        okSweet = SweetAlertDialog(this, SweetAlertDialog.SUCCESS_TYPE)
        errorSweet = SweetAlertDialog(this, SweetAlertDialog.ERROR_TYPE)
        disconnection = SweetAlertDialog(this, SweetAlertDialog.NORMAL_TYPE)
        loadSweet = SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE)

        okSweet.titleText = "Conectado"
        loadSweet.setCancelable(false)
        errorSweet.titleText = "Error"
        disconnection.titleText = "Desconectado"
        loadSweet.titleText = "Conectando"
    }

    private fun initGraph() {
        binding.graphPPG.viewport.isXAxisBoundsManual = true
        binding.graphPPG.viewport.setMinX(0.0)
        binding.graphPPG.viewport.setMaxX(200.0)
        binding.graphPPG.viewport.setMinY(0.0)
        binding.graphPPG.viewport.setMaxY(500.0)
        binding.graphPPG.title = "Pulso Cardiaco"
        binding.graphPPG.titleColor = Color.RED

        binding.graphPPG.gridLabelRenderer.isHorizontalLabelsVisible = false
        binding.graphPPG.gridLabelRenderer.isVerticalLabelsVisible = false

        binding.graphPPG.viewport.isScalable = false
        binding.graphPPG.viewport.setScalableY(true)

        ppgSeries = LineGraphSeries()
        ppgSeries.color = Color.RED
        binding.graphPPG.addSeries(ppgSeries)

        binding.graphECG.viewport.isXAxisBoundsManual = true
        binding.graphECG.viewport.setMinX(0.0)
        binding.graphECG.viewport.setMaxX(200.0)
        binding.graphECG.viewport.setMinY(0.0)
        binding.graphECG.viewport.setMaxY(2000.0)
        binding.graphECG.title = "Electrocardiograma"
        binding.graphECG.titleColor = Color.BLACK

        binding.graphECG.gridLabelRenderer.isHorizontalLabelsVisible = false
        binding.graphECG.gridLabelRenderer.isVerticalLabelsVisible = false

        binding.graphECG.viewport.isScalable = false
        binding.graphECG.viewport.setScalableY(true)

        ecgSeries = LineGraphSeries()
        ecgSeries.color = Color.GREEN
        binding.graphECG.addSeries(ecgSeries)
    }

    private fun listaDispositivosVisible() {
        binding.containerDevice.visibility = View.VISIBLE
        binding.graphPPG.visibility = View.GONE
        binding.graphECG.visibility = View.GONE
        graphViewVisible = false
        binding.btnDispositivos.text = "Gráficas"
    }

    private fun listaDispositivosOculta() {
        binding.containerDevice.visibility = View.GONE
        binding.graphPPG.visibility = View.VISIBLE
        binding.graphECG.visibility = View.VISIBLE
        graphViewVisible = true
        binding.btnDispositivos.text = "Dispositivos"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!blue.stateBluetoooth() && requestCode == 100) {
            blue.initializeBluetooth()
        } else {
            if (requestCode == 100) {
                devicesBluetooth = blue.deviceBluetooth()
                if (devicesBluetooth.isNotEmpty()) {
                    val adapter = ArrayAdapter(
                        this,
                        android.R.layout.simple_expandable_list_item_1,
                        devicesBluetooth
                    )
                    binding.listDeviceBluetooth.adapter = adapter
                } else {
                    Toast.makeText(this, "No tienes vinculados dispositivos", Toast.LENGTH_SHORT).show()
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (blue.checkPermissions(requestCode, grantResults)) {
            Toast.makeText(this, "Exit", Toast.LENGTH_SHORT).show()
            blue.initializeBluetooth()
        } else {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                blue.initializeBluetooth()
            } else {
                Toast.makeText(this, "Algo salio mal", Toast.LENGTH_SHORT).show()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    fun callSingOut(view: View) {
        signOut()
    }

    private fun signOut() {
        useremail = ""
        FirebaseAuth.getInstance().signOut()
        startActivity(Intent(this, LoginActivity::class.java))
    }

    override fun onBackPressed() {
        super.onBackPressed()
        val startMain = Intent(Intent.ACTION_MAIN)
        startMain.addCategory(Intent.CATEGORY_HOME)
        startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(startMain)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Eliminar cualquier callback pendiente para evitar fugas de memoria
        handler.removeCallbacks(stopCollectionRunnable)
    }
}