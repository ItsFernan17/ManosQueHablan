import os
import cv2
import json

# CONFIGURACIONES GENERALES
MIN_LENGTH_FRAMES = 5
LENGTH_KEYPOINTS = 126
MODEL_FRAMES = 30

# RUTAS DE ARCHIVOS
ROOT_PATH = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))

FRAME_ACTIONS_PATH = os.path.join(ROOT_PATH, "frame_actions")
DATA_PATH = os.path.join(ROOT_PATH, "data")
DATA_JSON_PATH = os.path.join(DATA_PATH, "data.json")
KEYPOINTS_PATH = os.path.join(DATA_PATH, "keypoints")

MODEL_FOLDER_PATH = os.path.join(ROOT_PATH, "models")
MODEL_PATH = os.path.join(MODEL_FOLDER_PATH, "action.h5")
WORDS_JSON_PATH = os.path.join(MODEL_FOLDER_PATH, "words.json")

# VALIDACIONES
if not os.path.exists(MODEL_PATH):
    raise FileNotFoundError(f"❌ Modelo no encontrado en la ruta: {MODEL_PATH}")

if not os.path.exists(WORDS_JSON_PATH):
    raise FileNotFoundError(f"❌ Archivo de palabras no encontrado en: {WORDS_JSON_PATH}")

# PARÁMETROS VISUALES PARA OpenCV
FONT = cv2.FONT_HERSHEY_PLAIN
FONT_SIZE = 1.5
FONT_POS = (5, 30)
