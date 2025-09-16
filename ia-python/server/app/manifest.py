# app/manifest.py
import json
import os
import time
import threading
from typing import List, Dict, Any, Optional
from datetime import datetime

# Lock para operaciones concurrentes
_manifest_locks = {}
_lock_creation_mutex = threading.Lock()

def get_manifest_lock(session_dir: str) -> threading.Lock:
    """Obtiene un lock específico para una sesión."""
    with _lock_creation_mutex:
        if session_dir not in _manifest_locks:
            _manifest_locks[session_dir] = threading.Lock()
        return _manifest_locks[session_dir]

def get_manifest_path(session_dir: str) -> str:
    """Retorna la ruta del archivo manifest.json."""
    return os.path.join(session_dir, "manifest.json")

def create_manifest(session_dir: str, required_files: List[str]) -> Dict[str, Any]:
    """
    Crea un nuevo manifest.json para una sesión.
    
    Args:
        session_dir: Directorio de la sesión
        required_files: Lista de archivos requeridos (ej: ["video.mp4", "audio.mp3", "transcript.txt"])
    
    Returns:
        Dict con el contenido del manifest
    """
    manifest = {
        "status": "uploading",
        "required": required_files,
        "have": [],
        "last_update": time.time(),
        "created_at": time.time()
    }
    
    lock = get_manifest_lock(session_dir)
    with lock:
        manifest_path = get_manifest_path(session_dir)
        with open(manifest_path, "w", encoding="utf-8") as f:
            json.dump(manifest, f, indent=2)
    
    return manifest

def read_manifest(session_dir: str) -> Optional[Dict[str, Any]]:
    """
    Lee el manifest.json de una sesión.
    
    Returns:
        Dict con el contenido del manifest o None si no existe
    """
    manifest_path = get_manifest_path(session_dir)
    if not os.path.exists(manifest_path):
        return None
    
    try:
        with open(manifest_path, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return None

def update_manifest(session_dir: str, updates: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    """
    Actualiza el manifest.json de una sesión.
    
    Args:
        session_dir: Directorio de la sesión
        updates: Dict con los campos a actualizar
    
    Returns:
        Dict con el contenido actualizado del manifest o None si no existe
    """
    lock = get_manifest_lock(session_dir)
    with lock:
        manifest = read_manifest(session_dir)
        if manifest is None:
            return None
        
        # Actualizar campos
        manifest.update(updates)
        manifest["last_update"] = time.time()
        
        # Verificar si está listo
        if set(manifest["have"]) == set(manifest["required"]) and manifest["status"] == "uploading":
            manifest["status"] = "ready"
            # Crear archivo ready.ok
            ready_path = os.path.join(session_dir, "ready.ok")
            with open(ready_path, "w") as f:
                f.write(str(time.time()))
        
        # Guardar
        manifest_path = get_manifest_path(session_dir)
        with open(manifest_path, "w", encoding="utf-8") as f:
            json.dump(manifest, f, indent=2)
        
        return manifest

def add_file_to_manifest(session_dir: str, filename: str) -> Optional[Dict[str, Any]]:
    """
    Añade un archivo a la lista 'have' del manifest.
    
    Args:
        session_dir: Directorio de la sesión
        filename: Nombre del archivo que se completó
    
    Returns:
        Dict con el contenido actualizado del manifest
    """
    lock = get_manifest_lock(session_dir)
    with lock:
        manifest = read_manifest(session_dir)
        if manifest is None:
            return None
        
        # Añadir archivo si no está ya
        if filename not in manifest["have"]:
            manifest["have"].append(filename)
        
        return update_manifest(session_dir, {"have": manifest["have"]})

def mark_as_delivered(session_dir: str) -> Optional[Dict[str, Any]]:
    """
    Marca una sesión como 'delivered'.
    
    Returns:
        Dict con el contenido actualizado del manifest
    """
    return update_manifest(session_dir, {"status": "delivered"})

def is_session_ready(session_dir: str) -> bool:
    """
    Verifica si una sesión está lista (todos los archivos requeridos están presentes).
    """
    manifest = read_manifest(session_dir)
    if manifest is None:
        return False
    
    return manifest["status"] == "ready"

def has_temp_files(session_dir: str) -> bool:
    """
    Verifica si hay archivos temporales (.tmp) en la sesión.
    """
    if not os.path.exists(session_dir):
        return False
    
    for item in os.listdir(session_dir):
        if item.endswith('.tmp') or item == 'processing.lock':
            return True
    
    return False

def safe_file_save(session_dir: str, filename: str, content_or_func, *args, **kwargs):
    """
    Guarda un archivo de forma segura usando .tmp y luego renombrando.
    
    Args:
        session_dir: Directorio de la sesión
        filename: Nombre final del archivo
        content_or_func: Contenido (bytes/str) o función para generar el archivo
        *args, **kwargs: Argumentos para la función si se proporciona
    
    Returns:
        True si se guardó exitosamente
    """
    temp_path = os.path.join(session_dir, f"{filename}.tmp")
    final_path = os.path.join(session_dir, filename)
    
    try:
        if callable(content_or_func):
            # Es una función, ejecutarla con el path temporal
            content_or_func(temp_path, *args, **kwargs)
        else:
            # Es contenido directo
            if isinstance(content_or_func, str):
                with open(temp_path, "w", encoding="utf-8") as f:
                    f.write(content_or_func)
            else:
                with open(temp_path, "wb") as f:
                    f.write(content_or_func)
        
        # Renombrar de .tmp al nombre final
        os.rename(temp_path, final_path)
        
        # Actualizar manifest
        add_file_to_manifest(session_dir, filename)
        
        return True
    
    except Exception:
        # Limpiar archivo temporal si falló
        if os.path.exists(temp_path):
            try:
                os.remove(temp_path)
            except:
                pass
        return False

def cleanup_manifest_locks():
    """Limpia locks de sesiones que ya no existen."""
    with _lock_creation_mutex:
        to_remove = []
        for session_dir in _manifest_locks:
            if not os.path.exists(session_dir):
                to_remove.append(session_dir)
        
        for session_dir in to_remove:
            del _manifest_locks[session_dir]