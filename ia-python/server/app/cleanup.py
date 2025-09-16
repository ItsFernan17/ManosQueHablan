# app/cleanup.py
import os
import time
import shutil
import asyncio
import logging
from fastapi import FastAPI

from app.utils import RESULTS_DIR
from app.manifest import read_manifest, has_temp_files, cleanup_manifest_locks

async def results_cleaner(ttl_secs: int, logger: logging.Logger, interval_secs: int = 60):
    """
    Borra entradas en RESULTS_DIR respetando el sistema de manifest.
    
    Reglas de limpieza:
    - NO borrar si status = "uploading" y last_update < 30 minutos
    - Si status = "ready", solo borrar si no hubo descargas en ≥ 60 minutos
    - Si status = "delivered", borrar a los 15 minutos de last_update
    - Nunca borrar si existen *.tmp o processing.lock
    """
    while True:
        try:
            now = time.time()
            if os.path.isdir(RESULTS_DIR):
                for entry in os.listdir(RESULTS_DIR):
                    session_path = os.path.join(RESULTS_DIR, entry)
                    try:
                        if not os.path.isdir(session_path):
                            continue
                        
                        # Verificar si hay archivos temporales o locks
                        if has_temp_files(session_path):
                            logger.debug(f"Skipping cleanup for {entry}: has temp files or processing lock")
                            continue
                        
                        # Leer manifest
                        manifest = read_manifest(session_path)
                        if manifest is None:
                            # Sin manifest, usar lógica antigua con TTL más corto para videos de 1 minuto
                            mtime = os.path.getmtime(session_path)
                            if (now - mtime) > min(ttl_secs, 300):  # Máximo 5 minutos sin manifest
                                logger.info(f"Cleaning session without manifest: {entry}")
                                shutil.rmtree(session_path, ignore_errors=True)
                            continue
                        
                        status = manifest.get("status", "uploading")
                        last_update = manifest.get("last_update", 0)
                        time_since_update = now - last_update
                        
                        should_delete = False
                        reason = ""
                        
                        if status == "uploading":
                            # NO borrar si el último update fue hace menos de 30 minutos
                            if time_since_update > 1800:  # 30 minutos
                                should_delete = True
                                reason = f"uploading timeout ({time_since_update/60:.1f} min)"
                        
                        elif status == "ready":
                            # Solo borrar si no hubo actividad en 60 minutos
                            if time_since_update > 3600:  # 60 minutos
                                should_delete = True
                                reason = f"ready timeout ({time_since_update/60:.1f} min)"
                        
                        elif status == "delivered":
                            # Borrar a los 15 minutos después de delivered
                            if time_since_update > 900:  # 15 minutos
                                should_delete = True
                                reason = f"delivered cleanup ({time_since_update/60:.1f} min)"
                        
                        if should_delete:
                            logger.info(f"Cleaning session {entry}: {reason}")
                            shutil.rmtree(session_path, ignore_errors=True)
                        else:
                            logger.debug(f"Keeping session {entry}: status={status}, age={time_since_update/60:.1f}min")
                    
                    except Exception as e:
                        logger.exception(f"Error processing session {entry}: {e}")
            
            # Limpiar locks de manifest de sesiones que ya no existen
            cleanup_manifest_locks()
            
        except Exception as e:
            logger.exception(f"results_cleaner error: {e}")
        
        await asyncio.sleep(interval_secs)

def register_results_cleaner(app: FastAPI, ttl_secs: int, logger: logging.Logger):
    @app.on_event("startup")
    async def _startup_results_cleaner():
        asyncio.create_task(results_cleaner(ttl_secs, logger))
