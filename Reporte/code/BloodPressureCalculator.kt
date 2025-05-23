package com.example.grafica

// Clase para calcular la presión arterial a partir de los valores PTT obtenidos
class BloodPressureCalculator {

    // Coeficientes para el modelo de presión sistólica
    private val A_SBP = -0.9f
    private val B_SBP = 180f

    // Coeficientes para el modelo de presión diastólica
    private val A_DBP = -0.6f
    private val B_DBP = 120f

    /**
     * Calcula la presión arterial sistólica basada en el PTT sistólico
     * @param systolicPTT Valor de PTT sistólico en milisegundos
     * @return Presión arterial sistólica estimada (mmHg)
     */
    fun calculateSBP(systolicPTT: Int): Int {
        // Si el PTT es 0 o muy pequeño, retornar un valor por defecto
        if (systolicPTT < 10) return 0

        // Fórmula basada en la relación inversa entre PTT y presión arterial
        val sbp = A_SBP / systolicPTT + B_SBP

        // Asegurar que el valor esté dentro de un rango fisiológicamente razonable
        return when {
            sbp < 70 -> 70
            sbp > 200 -> 200
            else -> sbp.toInt()
        }
    }

    /**
     * Calcula la presión arterial diastólica basada en el PTT diastólico
     * @param diastolicPTT Valor de PTT diastólico en milisegundos
     * @return Presión arterial diastólica estimada (mmHg)
     */
    fun calculateDBP(diastolicPTT: Int): Int {
        // Si el PTT es 0 o muy pequeño, retornar un valor por defecto
        if (diastolicPTT < 10) return 0

        // Fórmula basada en la relación inversa entre PTT y presión arterial
        val dbp = A_DBP / diastolicPTT + B_DBP

        // Asegurar que el valor esté dentro de un rango fisiológicamente razonable
        return when {
            dbp < 40 -> 40
            dbp > 120 -> 120
            else -> dbp.toInt()
        }
    }

    /**
     * Calcula la presión arterial media
     * @param systolic Presión arterial sistólica
     * @param diastolic Presión arterial diastólica
     * @return Presión arterial media (MAP)
     */
    fun calculateMAP(systolic: Int, diastolic: Int): Int {
        if (systolic <= 0 || diastolic <= 0) return 0

        // Fórmula estándar para presión arterial media
        return ((systolic + 2 * diastolic) / 3)
    }
}