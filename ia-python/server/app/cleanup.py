# app/cleanup.py
import os
import time
import shutil
import asyncio
import logging
from fastapi import FastAPI

from app.utils import RESULTS_DIR

async def results_cleaner(ttl_secs: int, logger: logging.Logger, interval_secs: int = 60):
    """
    Borra entradas en RESULTS_DIR con mtime más viejo que ttl_secs.
    """
    while True:
        try:
            now = time.time()
            if os.path.isdir(RESULTS_DIR):
                for entry in os.listdir(RESULTS_DIR):
                    path = os.path.join(RESULTS_DIR, entry)
                    try:
                        mtime = os.path.getmtime(path)
                        if (now - mtime) > ttl_secs:
                            if os.path.isdir(path):
                                shutil.rmtree(path, ignore_errors=True)
                            else:
                                os.remove(path)
                    except Exception:
                        logger.exception("Error al limpiar: %s", path)
        except Exception:
            logger.exception("results_cleaner error raíz")
        await asyncio.sleep(interval_secs)

def register_results_cleaner(app: FastAPI, ttl_secs: int, logger: logging.Logger):
    @app.on_event("startup")
    async def _startup_results_cleaner():
        asyncio.create_task(results_cleaner(ttl_secs, logger))
