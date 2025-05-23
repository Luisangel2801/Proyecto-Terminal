package com.example.grafica

import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Clase que calcula el Tiempo de Transmisión del Pulso (PTT) a partir de las señales procesadas
// y estima la presión arterial basada en estos valores

class PTTCalculator(private val signalProcessor: SignalProcessor) {

    private val signalAnalyzer = SignalAnalyzer()
    private val firebaseDataManager = FirebaseDataManager()
    private val bloodPressureCalculator = BloodPressureCalculator()

    /**
     * Calcula el PTT sistólico (desde el pico R del ECG hasta el pico superior del pulso)
     * @return Lista de valores de PTT sistólico en milisegundos
     */
    fun calculateSystolicPTT(): List<Int> {
        val pttValues = mutableListOf<Int>()
        val rPeakSamples = signalProcessor.getRPeakSamples()
        val allSamples = signalProcessor.getSamples()

        if (rPeakSamples.isEmpty() || allSamples.isEmpty()) return pttValues

        // Extraer valores PPG y timestamps para detectar picos
        val ppgValues = allSamples.map { it.ppg }
        val timestamps = allSamples.map { it.timestamp }

        // Aplicar filtro para reducir ruido
        val filteredPPG = signalAnalyzer.movingAverageFilter(ppgValues)

        // Detectar picos superiores del PPG usando el analizador de señales
        val ppgPeakIndices = signalAnalyzer.findPeaks(
            signal = filteredPPG,
            windowSize = 8,
            minPeakHeight = 100,
            minPeakDistance = 10
        )

        // Para cada pico R del ECG, buscar el pico superior del pulso más cercano que lo siga
        for (rPeakSample in rPeakSamples) {
            val rPeakTime = rPeakSample.timestamp

            // Encontrar el índice de la muestra con este timestamp
            val rPeakIndex = allSamples.indexOfFirst { it.timestamp == rPeakTime }
            if (rPeakIndex < 0) continue

            // Buscar el próximo pico superior del pulso después del pico R
            val nextPpgPeakIndex = ppgPeakIndices.firstOrNull { it > rPeakIndex } ?: continue

            if (nextPpgPeakIndex < timestamps.size) {
                // Calcular el PTT sistólico (diferencia de tiempo entre el pico R y el pico superior del pulso)
                val pttSystolic = timestamps[nextPpgPeakIndex] - rPeakTime

                // Solo consideramos valores positivos y razonables (entre 100 y 500 ms)
                if (pttSystolic in 100..500) {
                    pttValues.add(pttSystolic.toInt())
                }
            }
        }

        return pttValues
    }

    /**
     * Calcula el PTT diastólico (desde el pico R del ECG hasta el pico inferior/valle del pulso)
     * @return Lista de valores de PTT diastólico en milisegundos
     */
    fun calculateDiastolicPTT(): List<Int> {
        val pttValues = mutableListOf<Int>()
        val rPeakSamples = signalProcessor.getRPeakSamples()
        val pulseValleySamples = signalProcessor.getPulseValleySamples()
        val allSamples = signalProcessor.getSamples()

        if (rPeakSamples.isEmpty() || pulseValleySamples.isEmpty() || allSamples.isEmpty()) return pttValues

        // Para cada pico R del ECG, buscar el valle del pulso más cercano que lo siga
        for (rPeakSample in rPeakSamples) {
            val rPeakTime = rPeakSample.timestamp

            // Encontrar el siguiente valle del pulso que ocurra después del pico R
            val nextValley = pulseValleySamples.firstOrNull { it.timestamp > rPeakTime } ?: continue

            // Calcular el PTT diastólico
            val pttDiastolic = nextValley.timestamp - rPeakTime

            // Solo consideramos valores positivos y razonables (entre 100 y 500 ms)
            if (pttDiastolic in 100..500) {
                pttValues.add(pttDiastolic.toInt())
            }
        }

        return pttValues
    }

    /**
     * Calcula el PTT sistólico promedio, PTT diastólico promedio, y BPM
     * @return Triple de valores: PTT sistólico promedio, PTT diastólico promedio y BPM
     */
    fun calculateAveragePTTs(): Triple<Int, Int, Int> {
        val systolicValues = calculateSystolicPTT()
        val diastolicValues = calculateDiastolicPTT()

        val avgSystolic = if (systolicValues.isNotEmpty()) systolicValues.average().toInt() else 0
        val avgDiastolic = if (diastolicValues.isNotEmpty()) diastolicValues.average().toInt() else 0

        // Calcular BPM basado en picos R del ECG
        val rPeaks = signalProcessor.getRPeakSamples().size
        val elapsedTimeSeconds = 15.0 // Recolectamos durante 15 segundos
        val bpm = if (rPeaks > 0) ((rPeaks / elapsedTimeSeconds) * 60).toInt() else 0

        return Triple(avgSystolic, avgDiastolic, bpm)
    }

    /**
     * Calcula la presión arterial basada en los valores PTT
     * @return Triple con SBP, DBP y MAP (Presión Arterial Media)
     */
    fun calculateBloodPressure(): Triple<Int, Int, Int> {
        val (avgSystolic, avgDiastolic, _) = calculateAveragePTTs()

        // Calcular presión arterial sistólica y diastólica
        val sbp = bloodPressureCalculator.calculateSBP(avgSystolic)
        val dbp = bloodPressureCalculator.calculateDBP(avgDiastolic)

        // Calcular presión arterial media
        val map = bloodPressureCalculator.calculateMAP(sbp, dbp)

        return Triple(sbp, dbp, map)
    }

    /**
     * Guarda los resultados del PTT y presión arterial en Firebase Firestore
     * @param email Email del usuario para asociar con los datos
     * @param callback Función de retorno para indicar éxito o fracaso
     */
    fun saveResultsToFirebase(email: String, callback: (Boolean, String?) -> Unit) {
        val (avgSystolic, avgDiastolic, bpm) = calculateAveragePTTs()
        val (systolicBP, diastolicBP, _) = calculateBloodPressure()

        // Si no hay suficientes datos, no guardar nada
        if (avgSystolic == 0 && avgDiastolic == 0) {
            callback(false, "No hay suficientes datos para guardar")
            return
        }

        // Usar el gestor de datos de Firebase para guardar la información
        firebaseDataManager.saveBloodPressureMeasurement(
            email = email,
            systolicBP = systolicBP,
            diastolicBP = diastolicBP,
            bpm = bpm,
            systolicPTT = avgSystolic,
            diastolicPTT = avgDiastolic,
            callback = callback
        )
    }

    /**
     * Función auxiliar para calcular la frecuencia cardíaca en BPM
     * @return Frecuencia cardíaca calculada en BPM
     */
    fun calculateHeartRate(): Int {
        val rPeaks = signalProcessor.getRPeakSamples().size
        val elapsedTimeSeconds = 15.0 // Recolectamos durante 15 segundos
        return if (rPeaks > 0) ((rPeaks / elapsedTimeSeconds) * 60).toInt() else 0
    }
}