package com.example.grafica

// Datos recibidos por el ESP32
data class SampleData(
    val ecg: Int,                  // Valor del ECG
    val ppg: Int,                  // Valor del pulso cardíaco (PPG)
    val isRPeak: Boolean,          // Marca si hay un pico R en el ECG
    val isPulseValley: Boolean,    // Marca si hay un valle en el pulso
    val timestamp: Long            // Timestamp en milisegundos
)

// Clase que procesa las señales de ECG y PPG para detectar picos y calcular parámetros

class SignalProcessor {

    // Lista para almacenar las muestras recolectadas durante 15 segundos
    private val samples = mutableListOf<SampleData>()

    // Valor límite para considerar un pico en la señal PPG
    private val peakThreshold = 20

    // Variables para seguimiento de picos PPG
    private var previousPPG = 0
    private var currentPPG = 0

    // Bandera para indicar si el proceso de recolección está activo
    private var isCollecting = false

    // Tiempo de inicio de la recolección
    private var startTimeMs = 0L

    // Duración de la recolección en milisegundos (15 segundos)
    private val collectionDurationMs = 15000L

    // Inicia la recolección de muestras
    fun startCollection() {
        samples.clear()
        isCollecting = true
        startTimeMs = System.currentTimeMillis()
    }

    // Detiene la recolección de muestras

    fun stopCollection() {
        isCollecting = false
    }

    /**
     * Procesa una nueva muestra recibida del ESP32
     * @param ecg Valor del ECG
     * @param ppg Valor del pulso cardíaco
     * @param isRPeak Indica si es un pico R del ECG
     * @param isPulseValley Indica si es un valle del pulso
     * @param timestamp Timestamp de la muestra
     * @return true si debe continuar la recolección, false si se han completado los 15 segundos
     */
    fun processSample(ecg: Int, ppg: Int, isRPeak: Boolean, isPulseValley: Boolean, timestamp: Long): Boolean {
        // Si no está recolectando, retornar directamente
        if (!isCollecting) return false

        // Verificar si ya pasaron los 15 segundos
        if (System.currentTimeMillis() - startTimeMs >= collectionDurationMs) {
            isCollecting = false
            return false
        }

        // Actualizar valores para la detección de picos PPG
        previousPPG = currentPPG
        currentPPG = ppg

        // Agregar la muestra a la lista
        samples.add(SampleData(ecg, ppg, isRPeak, isPulseValley, timestamp))

        return true
    }

    /**
     * Detecta los picos superiores de la señal PPG basado en un análisis diferencial
     * @return Lista de índices donde se encontraron picos superiores del PPG
     */
    fun detectPPGPeaks(): List<Int> {
        val peakIndices = mutableListOf<Int>()

        if (samples.size < 3) return peakIndices

        // Encuentra picos utilizando análisis diferencial
        for (i in 1 until samples.size - 1) {
            val prev = samples[i - 1].ppg
            val current = samples[i].ppg
            val next = samples[i + 1].ppg

            // Un punto es un pico si es mayor que sus vecinos y supera un umbral mínimo
            if (current > prev && current > next && current > samples[i - 1].ppg + peakThreshold) {
                peakIndices.add(i)
            }
        }

        return peakIndices
    }

    // Obtiene todas las muestras recolectadas
    fun getSamples(): List<SampleData> = samples.toList()

    // Obtiene las muestras donde se detectaron picos R del ECG
    fun getRPeakSamples(): List<SampleData> = samples.filter { it.isRPeak }

    // Obtiene las muestras donde se detectaron valles del pulso
    fun getPulseValleySamples(): List<SampleData> = samples.filter { it.isPulseValley }

    // Verifica si hay suficientes datos para realizar los cálculos
    fun hasSufficientData(): Boolean {
        val rPeaks = getRPeakSamples()
        val pulseValleys = getPulseValleySamples()
        val pulsePeaks = detectPPGPeaks()

        // Se necesita al menos un pico R, un pico superior del pulso y un valle del pulso
        return rPeaks.isNotEmpty() && pulseValleys.isNotEmpty() && pulsePeaks.isNotEmpty()
    }

    // Limpia los datos de muestras recolectadas
    fun clearData() {
        samples.clear()
        isCollecting = false
    }

    // Verifica si está en proceso de recolección
    fun isCollectingData(): Boolean = isCollecting
}