# =========================
# Stage 1: Builder
# =========================
FROM python:3.10.18-bookworm AS builder

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    TF_CPP_MIN_LOG_LEVEL=2 \
    TF_ENABLE_ONEDNN_OPTS=0

# Dependencias mínimas para OpenCV/MediaPipe + fuentes
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 libglib2.0-0 \
    fontconfig \
    wget curl \
  && rm -rf /var/lib/apt/lists/*

# Instalar Poppins y cachear
RUN mkdir -p /usr/share/fonts/truetype \
  && wget -q https://github.com/google/fonts/raw/main/ofl/poppins/Poppins-Regular.ttf -O /usr/share/fonts/truetype/Poppins-Regular.ttf \
  && wget -q https://github.com/google/fonts/raw/main/ofl/poppins/Poppins-Bold.ttf -O /usr/share/fonts/truetype/Poppins-Bold.ttf \
  && fc-cache -f -v

WORKDIR /app

# Dependencias Python (con constraints)
COPY requirements.txt constraints.txt ./
RUN python -m pip install --upgrade pip \
  && pip install --no-cache-dir -r requirements.txt -c constraints.txt

# Pre-descargar el binario de ffmpeg usando imageio-ffmpeg (sin apt)
RUN python - << 'PY'
import imageio_ffmpeg, shutil, os
exe = imageio_ffmpeg.get_ffmpeg_exe()
os.makedirs('/opt/bin', exist_ok=True)
shutil.copy(exe, '/opt/bin/ffmpeg')
print('ffmpeg cached at /opt/bin/ffmpeg')
PY

# Copiar el código
COPY . .

# =========================
# Stage 2: Runtime
# =========================
FROM python:3.10.18-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    TF_CPP_MIN_LOG_LEVEL=2 \
    TF_ENABLE_ONEDNN_OPTS=0 \
    TZ=America/Guatemala

# Runtime deps: OpenCV/MediaPipe + fuentes + utilidades
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 libglib2.0-0 \
    fontconfig \
    wget curl \
  && rm -rf /var/lib/apt/lists/*

# Fuentes desde builder
COPY --from=builder /usr/share/fonts/truetype /usr/share/fonts/truetype
RUN fc-cache -f -v

# ffmpeg (binario precargado por imageio-ffmpeg)
COPY --from=builder /opt/bin/ffmpeg /opt/bin/ffmpeg
ENV PATH="/opt/bin:${PATH}"

# Crear usuario no-root
RUN useradd --create-home --shell /bin/bash --uid 10001 app
WORKDIR /app

# Paquetes y código
COPY --from=builder /usr/local/lib/python3.10/site-packages /usr/local/lib/python3.10/site-packages
COPY --from=builder /app /app

# Permisos
RUN chown -R app:app /app
USER app

EXPOSE 8000

# Healthcheck (ajusta la ruta si usas otra)
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8000/health || exit 1

# Arranque (sin depender del binario 'uvicorn' en /usr/local/bin)
CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--proxy-headers", "--forwarded-allow-ips", "*"]
