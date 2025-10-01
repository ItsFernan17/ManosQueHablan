# app/config.py
import os

# Tamaño máx de subida en MB (responde 413 si se excede)
MAX_UPLOAD_MB = int(os.getenv("MAX_UPLOAD_MB", "50"))

# Concurrencia máxima para tareas pesadas (FFmpeg/TTS) - REDUCIDA para proteger IA
FFMPEG_CONCURRENCY = int(os.getenv("FFMPEG_CONCURRENCY", "1"))  # Solo 1 FFmpeg concurrente

# TTL de resultados en segundos (30 min por defecto)
RESULTS_TTL_SECS = int(os.getenv("RESULTS_TTL_SECS", "1800"))

# Puerto (lo usas al correr con Gunicorn/Uvicorn)
PORT = int(os.getenv("PORT", "8000"))

# Timeout para Gunicorn (videos pesados)
GUNICORN_TIMEOUT = int(os.getenv("GUNICORN_TIMEOUT", "180"))

# Límites de recursos para proteger IA
MAX_CONCURRENT_REQUESTS = int(os.getenv("MAX_CONCURRENT_REQUESTS", "1"))
