package com.example.grafica

class SignalAnalyzer {

    /**
     * Detecta picos en una señal utilizando un algoritmo de ventana deslizante
     * con umbral adaptativo.
     *
     * @param signal Lista de valores de la señal
     * @param windowSize Tamaño de la ventana para el análisis
     * @param minPeakHeight Altura mínima de los picos
     * @param minPeakDistance Distancia mínima entre picos consecutivos
     * @return Lista de índices donde se encontraron picos
     */
    fun findPeaks(
        signal: List<Int>,
        windowSize: Int = 10,
        minPeakHeight: Int = 50,
        minPeakDistance: Int = 5
    ): List<Int> {
        if (signal.size < windowSize) return emptyList()

        val peakIndices = mutableListOf<Int>()
        var lastPeakIndex = -minPeakDistance

        // Iteramos a través de la señal con una ventana deslizante
        for (i in windowSize until signal.size - windowSize) {
            val currentValue = signal[i]

            // Verificar si es un posible pico (mayor que sus vecinos)
            if (currentValue < minPeakHeight) continue

            // Verificar si es el máximo local en la ventana
            var isPeak = true

            // Verificar si es mayor que todos los elementos en la ventana anterior
            for (j in i - windowSize until i) {
                if (signal[j] >= currentValue) {
                    isPeak = false
                    break
                }
            }

            // Verificar si es mayor que todos los elementos en la ventana posterior
            if (isPeak) {
                for (j in i + 1..i + windowSize) {
                    if (j < signal.size && signal[j] > currentValue) {
                        isPeak = false
                        break
                    }
                }
            }

            // Si es un pico y cumple con la distancia mínima, añadirlo a la lista
            if (isPeak && (i - lastPeakIndex) >= minPeakDistance) {
                peakIndices.add(i)
                lastPeakIndex = i
            }
        }

        return peakIndices
    }

    /**
     * Detecta valles en una señal utilizando un algoritmo de ventana deslizante
     * con umbral adaptativo.
     *
     * @param signal Lista de valores de la señal
     * @param windowSize Tamaño de la ventana para el análisis
     * @param maxValleyHeight Altura máxima de los valles
     * @param minValleyDistance Distancia mínima entre valles consecutivos
     * @return Lista de índices donde se encontraron valles
     */
    fun findValleys(
        signal: List<Int>,
        windowSize: Int = 10,
        maxValleyHeight: Int = 300,
        minValleyDistance: Int = 5
    ): List<Int> {
        if (signal.size < windowSize) return emptyList()

        val valleyIndices = mutableListOf<Int>()
        var lastValleyIndex = -minValleyDistance

        // Iteramos a través de la señal con una ventana deslizante
        for (i in windowSize until signal.size - windowSize) {
            val currentValue = signal[i]

            // Verificar si es un posible valle (menor que el umbral)
            if (currentValue > maxValleyHeight) continue

            // Verificar si es el mínimo local en la ventana
            var isValley = true

            // Verificar si es menor que todos los elementos en la ventana anterior
            for (j in i - windowSize until i) {
                if (signal[j] <= currentValue) {
                    isValley = false
                    break
                }
            }

            // Verificar si es menor que todos los elementos en la ventana posterior
            if (isValley) {
                for (j in i + 1..i + windowSize) {
                    if (j < signal.size && signal[j] < currentValue) {
                        isValley = false
                        break
                    }
                }
            }

            // Si es un valle y cumple con la distancia mínima, añadirlo a la lista
            if (isValley && (i - lastValleyIndex) >= minValleyDistance) {
                valleyIndices.add(i)
                lastValleyIndex = i
            }
        }

        return valleyIndices
    }

    /**
     * Aplica un filtro de media móvil a la señal para reducir el ruido
     *
     * @param signal Lista de valores de la señal original
     * @param windowSize Tamaño de la ventana para el promedio
     * @return Señal filtrada
     */
    fun movingAverageFilter(signal: List<Int>, windowSize: Int = 5): List<Int> {
        if (signal.size <= windowSize) return signal

        val filteredSignal = mutableListOf<Int>()

        // Mantener los primeros valores sin cambios
        for (i in 0 until windowSize / 2) {
            filteredSignal.add(signal[i])
        }

        // Aplicar filtro de media móvil
        for (i in windowSize / 2 until signal.size - windowSize / 2) {
            var sum = 0
            for (j in i - windowSize / 2..i + windowSize / 2) {
                sum += signal[j]
            }
            filteredSignal.add(sum / windowSize)
        }

        // Mantener los últimos valores sin cambios
        for (i in signal.size - windowSize / 2 until signal.size) {
            filteredSignal.add(signal[i])
        }

        return filteredSignal
    }

    /**
     * Calcula los intervalos de tiempo entre eventos consecutivos en una señal
     *
     * @param eventIndices Lista de índices donde ocurren los eventos
     * @param timestamps Lista de timestamps correspondientes a cada muestra
     * @return Lista de intervalos de tiempo entre eventos consecutivos
     */
    fun calculateTimeIntervals(eventIndices: List<Int>, timestamps: List<Long>): List<Long> {
        if (eventIndices.size < 2) return emptyList()

        val intervals = mutableListOf<Long>()

        for (i in 0 until eventIndices.size - 1) {
            val currentIndex = eventIndices[i]
            val nextIndex = eventIndices[i + 1]

            if (currentIndex < timestamps.size && nextIndex < timestamps.size) {
                val interval = timestamps[nextIndex] - timestamps[currentIndex]
                intervals.add(interval)
            }
        }

        return intervals
    }
}