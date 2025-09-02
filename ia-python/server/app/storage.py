# app/storage.py
import os
import shutil
import subprocess
from fastapi import UploadFile

CHUNK = 1024 * 1024  # 1 MB

async def save_upload_to_path(upload: UploadFile, dest_path: str):
    """
    Guarda el UploadFile a disco por chunks, robusto para archivos grandes.
    """
    try:
        await upload.seek(0)
    except Exception:
        pass
    with open(dest_path, "wb") as out:
        shutil.copyfileobj(upload.file, out, length=CHUNK)

def get_ffmpeg_path() -> str:
    """Obtiene ffmpeg binario portable desde imageio-ffmpeg; si falla, usa 'ffmpeg' del PATH."""
    try:
        import imageio_ffmpeg
        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:
        return "ffmpeg"

def is_valid_media(path: str) -> bool:
    """
    Usa ffmpeg para chequear que el video no esté vacío/corrupto.
    Retorna True si ffmpeg puede leer el video sin errores.
    """
    try:
        if not os.path.exists(path) or os.path.getsize(path) == 0:
            return False
        ffmpeg = get_ffmpeg_path()
        # Usar ffmpeg para extraer información básica del video
        r = subprocess.run(
            [ffmpeg, "-v", "error", "-i", path, "-f", "null", "-"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
        )
        return r.returncode == 0
    except Exception:
        return False
