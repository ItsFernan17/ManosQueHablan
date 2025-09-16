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

async def save_upload_to_session(upload: UploadFile, session_dir: str, filename: str) -> bool:
    """
    Guarda un UploadFile de forma segura en una sesión usando el sistema de manifest.
    
    Args:
        upload: El archivo subido
        session_dir: Directorio de la sesión
        filename: Nombre final del archivo
    
    Returns:
        True si se guardó exitosamente
    """
    from app.manifest import safe_file_save
    
    def save_func(temp_path: str):
        # Guardar usando la función original
        import asyncio
        loop = asyncio.get_event_loop()
        loop.run_until_complete(_save_upload_sync(upload, temp_path))
    
    return safe_file_save(session_dir, filename, save_func)

async def _save_upload_sync(upload: UploadFile, dest_path: str):
    """Función auxiliar para guardar upload de forma síncrona."""
    try:
        await upload.seek(0)
    except Exception:
        pass
    with open(dest_path, "wb") as out:
        shutil.copyfileobj(upload.file, out, length=CHUNK)

def save_text_to_session(text: str, session_dir: str, filename: str) -> bool:
    """
    Guarda texto de forma segura en una sesión usando el sistema de manifest.
    
    Args:
        text: El contenido de texto
        session_dir: Directorio de la sesión  
        filename: Nombre final del archivo
    
    Returns:
        True si se guardó exitosamente
    """
    from app.manifest import safe_file_save
    return safe_file_save(session_dir, filename, text)

def save_audio_to_session(audio_func, session_dir: str, filename: str, *args, **kwargs) -> bool:
    """
    Guarda audio de forma segura en una sesión usando el sistema de manifest.
    
    Args:
        audio_func: Función que genera el audio (ej: gTTS.save)
        session_dir: Directorio de la sesión
        filename: Nombre final del archivo
        *args, **kwargs: Argumentos para la función de audio
    
    Returns:
        True si se guardó exitosamente
    """
    from app.manifest import safe_file_save
    
    def save_func(temp_path: str):
        audio_func(temp_path, *args, **kwargs)
    
    return safe_file_save(session_dir, filename, save_func)

def create_processing_lock(session_dir: str) -> str:
    """
    Crea un archivo processing.lock para indicar que se está procesando.
    
    Returns:
        Ruta del archivo de lock
    """
    lock_path = os.path.join(session_dir, "processing.lock")
    with open(lock_path, "w") as f:
        f.write(str(os.getpid()))
    return lock_path

def remove_processing_lock(session_dir: str):
    """Elimina el archivo processing.lock."""
    lock_path = os.path.join(session_dir, "processing.lock")
    try:
        os.remove(lock_path)
    except:
        pass
