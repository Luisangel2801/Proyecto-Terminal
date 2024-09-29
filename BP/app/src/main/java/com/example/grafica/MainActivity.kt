package com.example.grafica

import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import cn.pedant.SweetAlert.SweetAlertDialog
import com.ingenieriajhr.blujhr.BluJhr
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    // Evitar que la pantalla se bloquee
    override fun onResume() {
        super.onResume()
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    lateinit var blue : BluJhr                  // Objeto para manejar la conexión bluetooth
    var devicesBluetooth = ArrayList<String>()  // Array para almacenar los dispositivos bluetooth
    var graphViewVisible = true                 // Variable para indicar si las gráficas son visibles

    lateinit var ecgSeries : LineGraphSeries<DataPoint?>  // Serie de datos para la gráfica ECG
    lateinit var ppgSeries : LineGraphSeries<DataPoint?>  // Serie de datos para la gráfica PPG

    var receivingData = false                       // Variable para indicar si se están recibiendo datos
    var estadoConexion = BluJhr.Connected.False     // Variable para almacenar el estado de la conexión

    lateinit var loadSweet : SweetAlertDialog       // Objeto para mostrar un mensaje de carga
    lateinit var errorSweet : SweetAlertDialog      // Objeto para mostrar un mensaje de error
    lateinit var okSweet : SweetAlertDialog         // Objeto para mostrar un mensaje de éxito
    lateinit var disconnection : SweetAlertDialog   // Objeto para mostrar un mensaje de desconexión

    var ejeX = 0.6                                  // Variable para almacenar el valor del eje X

    private fun rxReceived() {
        // Función que se ejecuta al recibir datos
        blue.loadDateRx(object:BluJhr.ReceivedData{
            override fun rxDate(rx: String) {
                ejeX += 0.6
                print(rx)
                // Expresión regular para capturar los valores
                val regex = Regex("e(\\d+) p(\\d+) t(\\d+)")

                // Intentar encontrar coincidencias en la cadena de entrada
                val matchResult = regex.find(rx)

                if (matchResult != null) {
                    // Extraer los valores y convertirlos a enteros
                    val ecg = matchResult.groupValues[1].toInt()
                    val pulsos = matchResult.groupValues[2].toInt()
                    val tiempo = matchResult.groupValues[3].toInt()
                    // Agregar los valores a las series
                    ecgSeries.appendData(DataPoint(ejeX, ecg.toDouble()), true, 10000)
                    ppgSeries.appendData(DataPoint(ejeX, pulsos.toDouble()), true, 10000)
                    //print("ECG: $ecg, Pulsos: $pulsos, Tiempo: $tiempo")
                }
            }
        })
    }

    // Función que se ejecuta al crear la actividad
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initSweet()                     // Inicialización de los objetos SweetAlertDialog
        blue = BluJhr(this)      // Inicialización del objeto BluJhr
        blue.onBluetooth()              // Encender bluetooth en caso de estar apagado

        // Mostrar o ocultar la lista de dispositivos bluetooth
        btnDispositivos.setOnClickListener {
            when (graphViewVisible) {
                true -> listaDispositivosVisible()
                false -> listaDispositivosOculta()
            }
        }

        listDeviceBluetooth.setOnItemClickListener { adapterView, view, i, l ->
            if (devicesBluetooth.isNotEmpty()) {
                blue.connect(devicesBluetooth[i])
                //genera error si no se vuelve a iniciar los objetos sweet
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

        // Inicialización de las gráficas
        initGraph()

        // Botón para empezar o detener la recepción de datos
        btnEmpezar.setOnClickListener {
            if (estadoConexion == BluJhr.Connected.True){
                receivingData = when (receivingData){
                    true->{
                        blue.bluTx("0")
                        btnEmpezar.text = "HISTORIAL"
                        false
                    }
                    false->{
                        blue.bluTx("1")
                        btnEmpezar.text = "HISTORIAL"
                        true
                    }
                }
            }else{
                Toast.makeText(this, "Conecta un dispositivo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initSweet() {
        // Inicialización de los objetos SweetAlertDialog
        okSweet = SweetAlertDialog(this, SweetAlertDialog.SUCCESS_TYPE)
        errorSweet = SweetAlertDialog(this, SweetAlertDialog.ERROR_TYPE)
        disconnection = SweetAlertDialog(this, SweetAlertDialog.NORMAL_TYPE)
        loadSweet = SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE)

        // Configuración de los mensajes de los objetos SweetAlertDialog
        okSweet.titleText = "Conectado"
        // Establecer que el mensaje de carga no sea cancelable
        loadSweet.setCancelable(false)
        errorSweet.titleText = "Error"
        disconnection.titleText = "Desconectado"
        loadSweet.titleText = "Conectando"
    }

    private fun initGraph() {
        graphPPG.viewport.isXAxisBoundsManual = true   // Permite ajustar el eje x manualmente
        graphPPG.viewport.setMinX(0.0)                  // Establece el valor mínimo del eje x
        graphPPG.viewport.setMaxX(200.0)                 // Establece el valor máximo del eje x
        graphPPG.viewport.setMinY(0.0)                  // Establece el valor mínimo del eje y
        graphPPG.viewport.setMaxY(500.0)               // Establece el valor máximo del eje y
        graphPPG.title = "Pulso Cardiaco"                          // Título de la gráfica PPG
        graphPPG.titleColor = Color.RED                // Color del título de la gráfica PPG

        // Esconder los numeros de los ejes
        graphPPG.gridLabelRenderer.isHorizontalLabelsVisible = false
        graphPPG.gridLabelRenderer.isVerticalLabelsVisible = false

        graphPPG.viewport.isScalable = false             // Permite realizar zoom
        graphPPG.viewport.setScalableY(true)            // Permite realizar zoom en el eje y

        ppgSeries = LineGraphSeries()                   // Serie de datos para la gráfica PPG
        ppgSeries.color = Color.RED                     // Color de la serie de datos para la gráfica PPG

        graphPPG.addSeries(ppgSeries)                   // Agregar la serie de datos a la gráfica PPG

        graphECG.viewport.isXAxisBoundsManual = false    // Permite ajustar el eje x manualmente
        graphECG.viewport.setMinX(0.0)                   // Establece el valor mínimo del eje x
        graphECG.viewport.setMaxX(200.0)                  // Establece el valor máximo del eje x
        graphECG.viewport.setMinY(0.0)                   // Establece el valor mínimo del eje y
        graphECG.viewport.setMaxY(2000.0)                // Establece el valor máximo del eje y
        graphECG.title = "Electrocardiograma"                         // Título de la gráfica ECG
        graphECG.titleColor = Color.GREEN                // Color del título de la gráfica ECG

        // Esconder los numeros de los ejes
        graphECG.gridLabelRenderer.isHorizontalLabelsVisible = false
        graphECG.gridLabelRenderer.isVerticalLabelsVisible = false

        graphECG.viewport.isScalable = false              // Permite realizar zoom
        graphECG.viewport.setScalableY(true)             // Permite realizar zoom en el eje y

        ecgSeries = LineGraphSeries()                   // Serie de datos para la gráfica ECG
        ecgSeries.color = Color.GREEN                    // Color de la serie de datos para la gráfica ECG

        graphECG.addSeries(ecgSeries)                   // Agregar la serie de datos a la gráfica ECG
    }

    private fun listaDispositivosVisible() {
        containerDevice.visibility = View.VISIBLE   // Mostrar la lista de dispositivos bluetooth
        graphPPG.visibility = View.GONE             // Ocultar la gráfica PPG
        graphECG.visibility = View.GONE             // Ocultar la gráfica ECG
        graphViewVisible = false                    // Indicar que las gráficas no son visibles
        btnDispositivos.text = "Gráficas"           // Cambiar el texto del botón
    }

    private fun listaDispositivosOculta() {
        containerDevice.visibility = View.GONE      // Ocultar la lista de dispositivos bluetooth
        graphPPG.visibility = View.VISIBLE          // Mostrar la gráfica PPG
        graphECG.visibility = View.VISIBLE          // Mostrar la gráfica ECG
        graphViewVisible = true                     // Indicar que las gráficas son visibles
        btnDispositivos.text = "Dispositivos"       // Cambiar el texto del botón
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
                    listDeviceBluetooth.adapter = adapter
                } else {
                    Toast.makeText(this, "No tienes vinculados dispositivos", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(requestCode: Int,permissions: Array<out String>, grantResults: IntArray) {
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
}

