package com.example.grafica

import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Clase para gestionar las operaciones con Firebase Firestore

class FirebaseDataManager {

    // Instancia de Firestore
    private val db = FirebaseFirestore.getInstance()

    // Nombre de la colección para almacenar los datos de presión arterial
    private val BP_COLLECTION = "blood_pressure_measurements"

    /**
     * Guarda una medición de presión arterial en Firestore
     * @param email Email del usuario
     * @param systolicBP Valor de presión arterial sistólica en mmHg
     * @param diastolicBP Valor de presión arterial diastólica en mmHg
     * @param bpm Latidos por minuto calculados
     * @param systolicPTT Valor de PTT sistólico (para referencia)
     * @param diastolicPTT Valor de PTT diastólico (para referencia)
     * @param callback Función de retorno para indicar éxito o fracaso
     */
    fun saveBloodPressureMeasurement(
        email: String,
        systolicBP: Int,
        diastolicBP: Int,
        bpm: Int,
        systolicPTT: Int,
        diastolicPTT: Int,
        callback: (Boolean, String?) -> Unit
    ) {
        // Formato para la fecha y hora
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val currentDateTime = dateFormat.format(Date())

        // Crear el mapa de datos a guardar
        val bpData = hashMapOf(
            "systolicBP" to systolicBP,
            "diastolicBP" to diastolicBP,
            "systolicPTT" to systolicPTT,  // Guardamos PTT para referencia
            "diastolicPTT" to diastolicPTT, // Guardamos PTT para referencia
            "bpm" to bpm,
            "user" to email,
            "timestamp" to currentDateTime,
            "date" to SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()),
            "time" to SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        )

        // Guardar en Firestore
        db.collection(BP_COLLECTION)
            .add(bpData)
            .addOnSuccessListener {
                // Éxito al guardar los datos
                callback(true, null)
            }
            .addOnFailureListener { e ->
                // Error al guardar los datos
                callback(false, e.message)
            }
    }

    fun savePTTMeasurement(
        email: String,
        systolicPTT: Int,
        diastolicPTT: Int,
        bpm: Int,
        callback: (Boolean, String?) -> Unit
    ) {
        // Calculamos la presión arterial a partir del PTT
        val bloodPressureCalculator = BloodPressureCalculator()
        val systolicBP = bloodPressureCalculator.calculateSBP(systolicPTT)
        val diastolicBP = bloodPressureCalculator.calculateDBP(diastolicPTT)

        // Llamar al nuevo método de guardado
        saveBloodPressureMeasurement(
            email = email,
            systolicBP = systolicBP,
            diastolicBP = diastolicBP,
            bpm = bpm,
            systolicPTT = systolicPTT,
            diastolicPTT = diastolicPTT,
            callback = callback
        )
    }

    /**
     * Recupera las últimas mediciones de presión arterial de un usuario
     * @param email Email del usuario
     * @param limit Número máximo de mediciones a recuperar
     * @param callback Función de retorno con la lista de mediciones
     */
    fun getLastBloodPressureMeasurements(
        email: String,
        limit: Long = 10,
        callback: (List<Map<String, Any>>, String?) -> Unit
    ) {
        db.collection(BP_COLLECTION)
            .whereEqualTo("user", email)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .addOnSuccessListener { documents ->
                val measurements = documents.documents.mapNotNull { it.data }
                callback(measurements, null)
            }
            .addOnFailureListener { e ->
                callback(emptyList(), e.message)
            }
    }

    /**
     * Método de compatibilidad: Redirige a getLastBloodPressureMeasurements
     */
    fun getLastPTTMeasurements(
        email: String,
        limit: Long = 10,
        callback: (List<Map<String, Any>>, String?) -> Unit
    ) {
        getLastBloodPressureMeasurements(email, limit, callback)
    }

    /**
     * Elimina una medición de presión arterial específica
     * @param documentId ID del documento a eliminar
     * @param callback Función de retorno para indicar éxito o fracaso
     */
    fun deleteBloodPressureMeasurement(documentId: String, callback: (Boolean, String?) -> Unit) {
        db.collection(BP_COLLECTION)
            .document(documentId)
            .delete()
            .addOnSuccessListener {
                callback(true, null)
            }
            .addOnFailureListener { e ->
                callback(false, e.message)
            }
    }


    fun deletePTTMeasurement(documentId: String, callback: (Boolean, String?) -> Unit) {
        deleteBloodPressureMeasurement(documentId, callback)
    }

    /**
     * Obtiene estadísticas de presión arterial para un usuario
     * @param email Email del usuario
     * @param callback Función de retorno con las estadísticas
     */
    fun getBloodPressureStatistics(
        email: String,
        callback: (Map<String, Any>?, String?) -> Unit
    ) {
        db.collection(BP_COLLECTION)
            .whereEqualTo("user", email)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    callback(null, "No hay mediciones registradas")
                    return@addOnSuccessListener
                }

                val measurements = documents.documents.mapNotNull { it.data }

                // Calcular promedios y estadísticas
                val systolicValues = measurements.mapNotNull { it["systolicBP"] as? Number }.map { it.toDouble() }
                val diastolicValues = measurements.mapNotNull { it["diastolicBP"] as? Number }.map { it.toDouble() }
                val bpmValues = measurements.mapNotNull { it["bpm"] as? Number }.map { it.toDouble() }

                val avgSystolic = if (systolicValues.isNotEmpty()) systolicValues.average() else 0.0
                val avgDiastolic = if (diastolicValues.isNotEmpty()) diastolicValues.average() else 0.0
                val avgBpm = if (bpmValues.isNotEmpty()) bpmValues.average() else 0.0

                val maxSystolic = systolicValues.maxOrNull() ?: 0.0
                val minSystolic = systolicValues.minOrNull() ?: 0.0
                val maxDiastolic = diastolicValues.maxOrNull() ?: 0.0
                val minDiastolic = diastolicValues.minOrNull() ?: 0.0

                val stats = hashMapOf(
                    "avgSystolicBP" to avgSystolic,
                    "avgDiastolicBP" to avgDiastolic,
                    "avgBPM" to avgBpm,
                    "maxSystolicBP" to maxSystolic,
                    "minSystolicBP" to minSystolic,
                    "maxDiastolicBP" to maxDiastolic,
                    "minDiastolicBP" to minDiastolic,
                    "totalMeasurements" to measurements.size
                )

                callback(stats, null)
            }
            .addOnFailureListener { e ->
                callback(null, e.message)
            }
    }

    fun getPTTStatistics(
        email: String,
        callback: (Map<String, Any>?, String?) -> Unit
    ) {
        getBloodPressureStatistics(email, callback)
    }
}