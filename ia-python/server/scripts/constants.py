import os, cv2

# SETTINGS
MIN_LENGTH_FRAMES = 5
LENGTH_KEYPOINTS = 126
MODEL_FRAMES = 30
THRESHOLD = 0.80
MARGIN_FRAME = 1
DELAY_FRAMES = 3
MAX_SEGMENT_FRAMES = 40

# --- PARÁMETROS ANTI-DUPLICADO ---
# Cooldown global por clase en segundos
COOLDOWN_SECONDS = 0.9

# Umbrales de histéresis para confirmación de gestos
HISTERESIS_ON_THRESHOLD = 0.80  # Umbral para confirmar una detección (alineado con THRESHOLD)
HISTERESIS_OFF_THRESHOLD = 0.40 # Umbral para poder re-armar la misma detección

# Silencio entre detecciones iguales (en número de frames)
SILENCE_FRAMES_BETWEEN_SAME_CLASS = 15

# Non-Maximum Suppression (NMS) temporal en milisegundos
# Si dos detecciones de la misma clase están más cerca que este umbral, se descarta una
NMS_TEMPORAL_MS = 700

# Mínimo de frames "limpios" (sin manos) para resetear completamente el estado
MIN_CLEAN_FRAMES_FOR_RESET = 10

# --- PARÁMETROS DE DESAMBIGUACIÓN GEOMÉTRICA ---
# Grupos de señas similares que necesitan desambiguación
SIMILAR_SIGNS_GROUPS = [
    ["hola", "papá"]
]

# Umbral de confianza para activar la desambiguación
# Si la confianza es MAYOR a este valor, se confía en el modelo y no se desambigua
DISAMBIGUATION_CONFIDENCE_THRESHOLD = 0.95 # Se eliminará su uso, pero se mantiene por si se reactiva

# --- UMBRALES PARA REGLAS GEOMÉTRICAS DE DESAMBIGUACIÓN ---
# Un gesto "hola" debe tener un pico de altura (muñeca sobre cejas) de al menos este valor (normalizado por altura de cara)
APEX_HEIGHT_THRESHOLD = 0.05 
# Un gesto "papá" debe tener un % de frames cerca de la boca de al menos este valor
MOUTH_DWELL_THRESHOLD = 0.30
# Número de frames consecutivos para calcular el pico de altura (ápice)
APEX_K_FRAMES = 4


# PATHS
ROOT_PATH = os.getcwd()
DATA_PATH = os.path.join(ROOT_PATH, "data")
MODEL_FOLDER_PATH = os.path.join(ROOT_PATH, "models")
MODEL_PATH = os.path.join(MODEL_FOLDER_PATH, "action.h5")

# SHOW IMAGE PARAMETERS
FONT = cv2.FONT_HERSHEY_PLAIN
FONT_SIZE = 1.5
FONT_POS = (5, 30)

# Mapa para mostrar en pantalla (solo las 3 clases)
words_text = {
    "feliz": "FELIZ",
    "hola": "HOLA", 
    "papá": "PAPÁ",
}

# Orden de clases EXACTO del modelo
CLASSES = ["feliz", "hola", "papá"]
