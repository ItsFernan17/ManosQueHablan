# ✅ Base completa (no slim) y versión exacta
FROM python:3.10.18-bookworm

# Ajustes útiles para logs y numerics estables en CPU
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    TF_CPP_MIN_LOG_LEVEL=2 \
    TF_ENABLE_ONEDNN_OPTS=0

# Dependencias del sistema para OpenCV/Mediapipe/ffmpeg
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Requisitos con constraints para fijar OpenCV
COPY requirements.txt constraints.txt ./
RUN python -m pip install --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt -c constraints.txt

# Copia el código
COPY . .

EXPOSE 8000

# Arranque con Uvicorn (ajusta módulo si es distinto)
CMD ["uvicorn", "app.main:app", "--host","0.0.0.0","--port","8011"]
