# app/config.py
import os

# Tamaño máx de subida en MB (responde 413 si se excede)
MAX_UPLOAD_MB = int(os.getenv("MAX_UPLOAD_MB", "200"))

# Concurrencia máxima para tareas pesadas (FFmpeg/TTS)
FFMPEG_CONCURRENCY = int(os.getenv("FFMPEG_CONCURRENCY", "2"))

# TTL de resultados en segundos (30 min por defecto)
RESULTS_TTL_SECS = int(os.getenv("RESULTS_TTL_SECS", "1800"))

# Puerto (lo usas al correr con Gunicorn/Uvicorn)
PORT = int(os.getenv("PORT", "8000"))

# Timeout para Gunicorn (videos pesados)
GUNICORN_TIMEOUT = int(os.getenv("GUNICORN_TIMEOUT", "180"))
