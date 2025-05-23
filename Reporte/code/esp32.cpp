#include <Arduino.h>
#include <BluetoothSerial.h>

// Definición de pines y constantes
#define LED_BUILTIN 2
#define PIN_ECG 34
#define PIN_PPG 35
#define PIN_HIST_ECG 25  // Pin para la señal de histéresis del ECG (pico R)
#define PIN_HIST_PPG 26  // Pin para la señal de histéresis del PPG (valle del pulso)
#define SAMPLE_PERIOD_MS 30
#define BUFFER_SIZE 100

// Estructura para almacenar las lecturas
struct SensorReading {
    int ecg;
    int ppg;
    bool isRPeak;        // Señal de histéresis para el pico R del ECG
    bool isPulseValley;  // Señal de histéresis para el valle del pulso
    unsigned long timestamp;
};

// Cola para comunicación entre núcleos
QueueHandle_t sensorQueue;

// Semáforo para proteger la comunicación Bluetooth
SemaphoreHandle_t bluetoothSemaphore;

BluetoothSerial ESP_BT;
volatile boolean BT_cnx = false;
volatile boolean transmitData = false; // Control para iniciar/detener transmisión

// Variables para el filtrado de señales
const int numReadings = 5;
int ecgReadings[numReadings];
int ppgReadings[numReadings];
int readIndex = 0;
int ecgTotal = 0;
int ppgTotal = 0;

// Función para el filtrado de señales
int smoothReading(int newReading, int readings[], int &total) {
    total = total - readings[readIndex] + newReading;
    readings[readIndex] = newReading;
    return total / numReadings;
}

// Callback para eventos Bluetooth
void callback(esp_spp_cb_event_t event, esp_spp_cb_param_t *param) {
    if (event == ESP_SPP_SRV_OPEN_EVT) {
        Serial.println("Cliente conectado");
        digitalWrite(LED_BUILTIN, HIGH);
        BT_cnx = true;
    } else if (event == ESP_SPP_CLOSE_EVT) {
        Serial.println("Cliente desconectado");
        digitalWrite(LED_BUILTIN, LOW);
        BT_cnx = false;
        transmitData = false;
        delay(1000);
        ESP.restart();
    } else if (event == ESP_SPP_DATA_IND_EVT) {
        // Procesar datos recibidos
        if (param->data_ind.len > 0) {
            char command = param->data_ind.data[0];
            if (command == '1') {
                transmitData = true;
                Serial.println("Iniciando transmisión de datos");
            } else if (command == '0') {
                transmitData = false;
                Serial.println("Deteniendo transmisión de datos");
            }
        }
    }
}

// Tarea del Núcleo 0: Muestreo y procesamiento de señales
void sensorTask(void *parameter) {
    TickType_t xLastWakeTime;
    const TickType_t xFrequency = pdMS_TO_TICKS(SAMPLE_PERIOD_MS);
    
    // Inicializar el tiempo de la última ejecución
    xLastWakeTime = xTaskGetTickCount();
    
    while (true) {
        // Leer y filtrar señales analógicas
        int ecgValue = smoothReading(analogRead(PIN_ECG), ecgReadings, ecgTotal);
        int ppgValue = smoothReading(analogRead(PIN_PPG), ppgReadings, ppgTotal);
        readIndex = (readIndex + 1) % numReadings;
        
        // Leer señales de histéresis (digitales)
        bool isRPeak = digitalRead(PIN_HIST_ECG) == HIGH;
        bool isPulseValley = digitalRead(PIN_HIST_PPG) == HIGH;
        
        // Crear estructura con los datos
        SensorReading reading = {
            .ecg = ecgValue,
            .ppg = ppgValue,
            .isRPeak = isRPeak,
            .isPulseValley = isPulseValley,
            .timestamp = millis()
        };
        
        // Enviar a la cola, esperar máximo 10ms si está llena
        xQueueSend(sensorQueue, &reading, pdMS_TO_TICKS(10));
        
        // Esperar hasta el siguiente período de muestreo
        vTaskDelayUntil(&xLastWakeTime, xFrequency);
    }
}

// Tarea del Núcleo 1: Transmisión Bluetooth
void bluetoothTask(void *parameter) {
    SensorReading reading;
    char dataBuffer[50];
    
    while (true) {
        // Esperar por nuevos datos en la cola
        if (xQueueReceive(sensorQueue, &reading, pdMS_TO_TICKS(100)) == pdTRUE) {
            if (BT_cnx && transmitData) {
                // Tomar el semáforo antes de usar Bluetooth
                if (xSemaphoreTake(bluetoothSemaphore, pdMS_TO_TICKS(10)) == pdTRUE) {
                    // Formatear y enviar datos
                    // Formato: e{ecg} p{ppg} r{isRPeak} b{isPulseValley} t{timestamp}
                    snprintf(dataBuffer, sizeof(dataBuffer), "e%d p%d r%d b%d t%lu", 
                             reading.ecg, reading.ppg, 
                             reading.isRPeak ? 1 : 0,  // Convertir boolean a 1 o 0
                             reading.isPulseValley ? 1 : 0,  // Convertir boolean a 1 o 0
                             reading.timestamp);
                    ESP_BT.println(dataBuffer);
                    
                    // Liberar el semáforo
                    xSemaphoreGive(bluetoothSemaphore);
                }
            }
        }
        // Pequeña pausa para permitir otras tareas
        vTaskDelay(1);
    }
}

void setup() {
    // Configuración de pines
    pinMode(LED_BUILTIN, OUTPUT);
    pinMode(PIN_ECG, INPUT);
    pinMode(PIN_PPG, INPUT);
    pinMode(PIN_HIST_ECG, INPUT);    // Configurar pin de histéresis ECG como entrada
    pinMode(PIN_HIST_PPG, INPUT);    // Configurar pin de histéresis PPG como entrada
    
    // Inicialización de comunicación serial
    Serial.begin(115200);
    
    // Crear cola y semáforo
    sensorQueue = xQueueCreate(BUFFER_SIZE, sizeof(SensorReading));
    bluetoothSemaphore = xSemaphoreCreateMutex();
    
    // Inicializar arrays de lecturas
    for (int i = 0; i < numReadings; i++) {
        ecgReadings[i] = 0;
        ppgReadings[i] = 0;
    }
    
    // Configuración Bluetooth
    ESP_BT.register_callback(callback);
    if (!ESP_BT.begin("ESP32_ECG")) {
        Serial.println("Error al inicializar Bluetooth");
        while(true) {
            digitalWrite(LED_BUILTIN, !digitalRead(LED_BUILTIN));
            delay(500);
        }
    }
    Serial.println("Bluetooth inicializado correctamente");
    Serial.println("Esperando conexión...");
    
    // Crear tareas en diferentes núcleos
    xTaskCreatePinnedToCore(
        sensorTask,     // Función para leer datos del sensor
        "SensorTask",   // Nombre de la tarea
        4096,           // Tamaño de la pila
        NULL,           // Parámetros de la tarea
        2,              // Prioridad
        NULL,           // Handle de la tarea
        0               // Núcleo 0
    );
    
    xTaskCreatePinnedToCore(
        bluetoothTask,  // Función para transmitir datos por Bluetooth
        "BluetoothTask",// Nombre de la tarea
        4096,           // Tamaño del stack
        NULL,           // Parámetros de la tarea
        1,              // Prioridad
        NULL,           // Handle de la tarea
        1               // Núcleo 1
    );
}

void loop() {
    // El loop principal queda vacío ya que todo se maneja en las tareas
    vTaskDelete(NULL);
}