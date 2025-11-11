# 🤲 Manos Que Hablan

<div align="center">

![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)
![Platform](https://img.shields.io/badge/platform-Android-green.svg)
![Python](https://img.shields.io/badge/python-3.8+-blue.svg)
![License](https://img.shields.io/badge/license-MIT-green.svg)

**Sistema de traducción automática de Lengua de Señas Guatemalteca (LSG) usando Deep Learning**

[Características](#-características) •
[Tecnologías](#-tecnologías) •
[Arquitectura](#-arquitectura) •
[Instalación](#-instalación) •
[Uso](#-uso) •
[Modelo IA](#-modelo-de-ia)

</div>

---

## 📋 Descripción

**Manos Que Hablan** es una aplicación móvil Android que utiliza Inteligencia Artificial para reconocer y traducir gestos de la Lengua de Señas Guatemalteca (LSG) en tiempo real. El proyecto combina visión por computadora, redes neuronales recurrentes (LSTM) y procesamiento de video para facilitar la comunicación entre personas sordas y oyentes.

### 🎯 Objetivo

Reducir las barreras de comunicación mediante tecnología accesible que interprete automáticamente la LSG, generando texto y audio en español a partir de videos de señas.

---

## ✨ Características

### 📱 Aplicación Android
- ✅ **Captura de video en tiempo real** con visualización de gestos
- ✅ **Traducción automática** de LSG a texto y audio
- ✅ **Catálogo interactivo** de señas con videos de referencia
- ✅ **Historial de traducciones** con reproducción de resultados
- ✅ **Configuración flexible** de servidor local o remoto
- ✅ **Interfaz intuitiva** con Material Design 3
- ✅ **Procesamiento en segundo plano** con notificaciones

### 🤖 Modelo de IA (MargaritaLSG1)
- 🧠 **Red neuronal LSTM** para secuencias temporales
- 👋 **Detección de landmarks** con MediaPipe Holistic (42 puntos por frame: 21 mano izquierda + 21 mano derecha)
- 📊 **Normalización adaptativa** de secuencias de video
- 🎯 **Alta precisión** en reconocimiento de palabras y frases
- 🔄 **Suavizado de keypoints** para reducir ruido
- 📈 **Métricas de confianza** por cada predicción

### 🖥️ Servidor Backend
- ⚡ **API REST con FastAPI** de alto rendimiento
- 🎬 **Procesamiento de video** con FFmpeg
- 🔊 **Síntesis de voz** con gTTS (Google Text-to-Speech)
- 📝 **Subtítulos quemados** en video de salida
- 🗂️ **Gestión de sesiones** con manifiestos JSON
- 🧹 **Limpieza automática** de archivos temporales
- 🐳 **Contenedorización** con Docker

---

## 🛠️ Tecnologías

### Frontend (Android)
```
• Kotlin
• Jetpack Compose & Material 3
• CameraX para captura de video
• Retrofit para consumo de API
• Coroutines para programación asíncrona
• Gson para serialización JSON
• ExoPlayer para reproducción de medios
```

### Backend (Python)
```
• FastAPI - Framework web asíncrono
• TensorFlow/Keras - Deep Learning
• MediaPipe - Detección de landmarks
• OpenCV - Procesamiento de imágenes
• FFmpeg - Edición de video/audio
• NumPy - Cálculos numéricos
• gTTS - Text-to-Speech
```

### DevOps & Tools
```
• Docker & Docker Compose
• Gradle (Android Build System)
• Git & GitHub
• Jupyter Notebook (experimentación)
```

---

## 🏗️ Arquitectura

```
┌─────────────────────────────────────────────────────────────┐
│                     MANOS QUE HABLAN                        │
└─────────────────────────────────────────────────────────────┘

┌──────────────────┐          ┌──────────────────┐
│                  │          │                  │
│  📱 ANDROID APP  │ ◄─────► │  🖥️ BACKEND API  │
│                  │   HTTP   │                  │
│  - Captura video │          │  - FastAPI       │
│  - UI/UX         │          │  - Evaluador IA  │
│  - Reproducción  │          │  - FFmpeg        │
│                  │          │                  │
└──────────────────┘          └──────────────────┘
                                      │
                                      ▼
                              ┌──────────────────┐
                              │                  │
                              │  🤖 MODELO IA    │
                              │                  │
                              │  MargaritaLSG1   │
                              │  - LSTM Network  │
                              │  - MediaPipe     │
                              │  - 30 frames     │
                              │  - 126 keypoints │
                              │                  │
                              └──────────────────┘
```

### Flujo de Trabajo

1. **Captura**: Usuario graba video de señas en la app Android
2. **Upload**: Video se envía al servidor vía API REST
3. **Procesamiento**: 
   - Extracción de frames con OpenCV
   - Detección de landmarks con MediaPipe
   - Normalización a 30 frames
   - Predicción con modelo LSTM
4. **Post-procesamiento**:
   - Generación de audio (gTTS)
   - Incrustación de subtítulos (FFmpeg)
   - Mezcla de audio original + TTS
5. **Respuesta**: Video procesado se devuelve a la app
6. **Visualización**: Usuario ve traducción con subtítulos y audio

---

## 📦 Instalación

### Prerrequisitos

- **Android**: Android Studio Hedgehog+, JDK 17+, SDK 34
- **Python**: Python 3.8+, pip
- **Sistema**: FFmpeg instalado
- **Opcional**: Docker y Docker Compose

### 1️⃣ Clonar el Repositorio

```bash
git clone https://github.com/ItsFernan17/LenguaSeniasApp.git
cd LenguaSeniasApp
```

### 2️⃣ Configurar Backend

#### Opción A: Con Docker (Recomendado)

```bash
cd ia-python/server
docker-compose up -d
```

El servidor estará disponible en `http://localhost:8000`

#### Opción B: Instalación Manual

```bash
cd ia-python/server

# Crear entorno virtual
python -m venv venv
source venv/bin/activate  # En Windows: venv\Scripts\activate

# Instalar dependencias
pip install -r requirements.txt

# Verificar FFmpeg
ffmpeg -version

# Iniciar servidor
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

### 3️⃣ Configurar Android App

```bash
cd app-android

# Abrir en Android Studio
# File > Open > Seleccionar carpeta app-android

# Sincronizar Gradle
./gradlew build

# Ejecutar en dispositivo/emulador
./gradlew installDebug
```

**Configurar URL del servidor en la app:**
- Abrir `Configuración` en la app
- Ingresar IP del servidor (ej: `http://192.168.1.100:8000`)
- Verificar conexión con el botón de prueba

---

## 🚀 Uso

### Aplicación Android

1. **Iniciar App**: Abrir "Manos Que Hablan"

2. **Traducir Señas**:
   - Tap en botón "Traducir Video"
   - Grabar señas (máx. 10 segundos)
   - Esperar procesamiento (barra de progreso)
   - Ver video con traducción automática

3. **Ver Catálogo**:
   - Tap en "Catálogo de Señas"
   - Explorar palabras disponibles
   - Ver videos de referencia

4. **Historial**:
   - Acceder a traducciones anteriores
   - Reproducir videos procesados
   - Compartir resultados

### API Backend (Desarrollo)

#### Health Check
```bash
curl http://localhost:8000/health
```

#### Procesar Video
```bash
curl -X POST http://localhost:8000/procesar \
  -F "video=@video_senias.mp4"
```

#### Documentación Interactiva
```
http://localhost:8000/docs
```

---

## 🧠 Modelo de IA

### MargaritaLSG1

**Arquitectura de Red Neuronal:**

```python
Sequential([
    LSTM(128, return_sequences=True, input_shape=(30, 126)),
    Dropout(0.3),
    LSTM(128, return_sequences=False),
    Dropout(0.3),
    Dense(64, activation='relu'),
    Dropout(0.2),
    Dense(num_classes, activation='softmax')
])
```

**Características Técnicas:**

| Parámetro | Valor |
|-----------|-------|
| Tipo | LSTM Recurrente |
| Frames por video | 30 |
| Keypoints por frame | 126 (21×3×2 manos) |
| Clases | ~50 palabras/frases LSG |
| Optimizador | Adam |
| Loss | Categorical Crossentropy |
| Métricas | Accuracy, F1-Score |

**Proceso de Entrenamiento:**

1. Captura de 50+ videos por cada seña
2. Extracción de landmarks con MediaPipe
3. Normalización a 30 frames
4. Data augmentation (rotación, escala)
5. División: 80% train, 10% validation, 10% test
6. Early stopping con patience=15
7. Guardado del mejor modelo (val_loss)

**Dataset:**
- Ubicación: `ia-python/notebook/Modelo No. 2/data/`
- Formato: Arrays NumPy (.npy)
- Estructura: `data/frases/<palabra>/video_<N>.npy`

---

## 📂 Estructura del Proyecto

```
ManosQueHablan/
├── app-android/              # Aplicación Android
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/.../manosquehablan/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── GrabarVideoActivity.kt
│   │   │   │   ├── CatalogoSeniasActivity.kt
│   │   │   │   ├── ConfiguracionActivity.kt
│   │   │   │   ├── api/
│   │   │   │   ├── config/
│   │   │   │   └── dialogs/
│   │   │   ├── res/              # Recursos UI
│   │   │   └── assets/           # Catálogo JSON
│   │   └── build.gradle.kts
│   └── gradle/
│
├── ia-python/
│   ├── notebook/              # Experimentación
│   │   ├── Modelo No. 2/
│   │   │   ├── MARGARITALSG1.ipynb
│   │   │   ├── data/          # Dataset señas
│   │   │   └── models/        # Modelos guardados
│   │   └── requirements.txt
│   │
│   └── server/                # Backend FastAPI
│       ├── app/
│       │   ├── main.py        # API endpoints
│       │   ├── evaluator.py   # Predicción IA
│       │   ├── utils_ffmpeg.py
│       │   ├── models/
│       │   │   ├── MargaritaLSG1.h5
│       │   │   └── labels.txt
│       │   └── middlewares/
│       ├── requirements.txt
│       ├── Dockerfile
│       └── docker-compose.yml
│
└── README.md
```

---

## 🎬 Demo y Capturas

### Interfaz Principal
<div align="center">
<i>(Aquí puedes agregar screenshots de tu app)</i>
</div>

### Proceso de Traducción
```
📹 Grabar → 📤 Subir → 🤖 Procesar → 📝 Traducir → 🔊 Reproducir
```

---

## 🧪 Testing

### Backend
```bash
cd ia-python/server
pytest tests/
```

### Android
```bash
cd app-android
./gradlew test
./gradlew connectedAndroidTest
```

---

## 🔐 Configuración de Seguridad

### Variables de Entorno (Servidor)

Crear archivo `.env` en `ia-python/server/`:

```env
MAX_UPLOAD_MB=100
RESULTS_TTL_SECS=3600
MAX_CONCURRENT_REQUESTS=5
ALLOWED_ORIGINS=http://localhost:3000
```

### Permisos Android

La app requiere:
- 📷 `CAMERA`
- 🎤 `RECORD_AUDIO`
- 🌐 `INTERNET`
- 📁 `READ_MEDIA_VIDEO`

---

## 🐛 Troubleshooting

### Problema: Servidor no responde
**Solución**: Verificar firewall y puerto 8000 abierto

### Problema: App no detecta servidor
**Solución**: Usar IP local (no localhost en dispositivos físicos)

### Problema: Modelo no predice correctamente
**Solución**: Verificar iluminación y encuadre de manos en video

### Problema: Error de memoria en Android
**Solución**: Reducir resolución de video en configuración

---

## 🤝 Contribuciones

¡Las contribuciones son bienvenidas! Por favor:

1. Fork el proyecto
2. Crea una rama feature (`git checkout -b feature/NuevaCaracteristica`)
3. Commit cambios (`git commit -m 'Agregar nueva característica'`)
4. Push a la rama (`git push origin feature/NuevaCaracteristica`)
5. Abre un Pull Request

---

## 📝 Roadmap

- [ ] Ampliar dataset a 100+ palabras/frases
- [ ] Implementar traducción en tiempo real (streaming)
- [ ] Modo offline con modelo TFLite
- [ ] Soporte para iOS
- [ ] Integración con WhatsApp/Telegram
- [ ] Multi-idioma (español/inglés)
- [ ] Reconocimiento facial para expresiones

---

## 👨‍💻 Autor

**Fernando Josué Rivas Mauricio**  
Proyecto de Tesis - Ingeniería en Sistemas

[![GitHub](https://img.shields.io/badge/GitHub-ItsFernan17-black?logo=github)](https://github.com/ItsFernan17)

---

## 📄 Licencia

Este proyecto es de código abierto bajo la licencia MIT. Ver archivo `LICENSE` para más detalles.

---

## 🙏 Agradecimientos

- Comunidad sorda guatemalteca por su colaboración
- MediaPipe y TensorFlow por sus herramientas de IA
- FastAPI por el framework backend
- Todos los colaboradores y testers

---

## 📚 Referencias

- [MediaPipe Hands](https://google.github.io/mediapipe/solutions/hands.html)
- [TensorFlow LSTM](https://www.tensorflow.org/api_docs/python/tf/keras/layers/LSTM)
- [FastAPI Documentation](https://fastapi.tiangolo.com/)
- [Android CameraX](https://developer.android.com/training/camerax)

---

<div align="center">

**⭐ Si este proyecto te fue útil, considera darle una estrella ⭐**

Hecho con ❤️ para la comunidad sorda de Guatemala 🇬🇹

</div>
