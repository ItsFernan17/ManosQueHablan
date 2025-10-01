# app/utils.py
import os
import uuid
import shutil
from datetime import datetime

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RESULTS_DIR = os.path.join(BASE_DIR, "app", "static", "results")

def ensure_dirs():
    os.makedirs(RESULTS_DIR, exist_ok=True)

def unique_name(prefix: str, ext: str) -> str:
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    uid = uuid.uuid4().hex[:8]
    return f"{prefix}_{ts}_{uid}.{ext}"

def cleanup_path(path: str):
    try:
        if os.path.isdir(path):
            shutil.rmtree(path, ignore_errors=True)
        elif os.path.isfile(path):
            os.remove(path)
    except Exception:
        pass


def capitalize_first_letter(text: str) -> str:
    """
    Capitaliza la primera letra alfabética de la cadena, manejando puntuación española correctamente.
    Convierte puntuación invertida si es necesario.
    """
    text = text.strip()
    if not text:
        return ""

    # Manejar puntuación española
    if text.endswith('?') and not text.startswith('¿'):
        # Convertir "palabra?" a "¿Palabra?"
        text = '¿' + text[:-1] + '?'
    elif text.endswith('!') and not text.startswith('¡'):
        # Convertir "palabra!" a "¡Palabra!"
        text = '¡' + text[:-1] + '!'

    # Capitalizar la primera letra alfabética
    for i, char in enumerate(text):
        if char.isalpha():
            return text[:i] + char.upper() + text[i+1:].lower()
    return text  # Si no hay letras, devolver como está
