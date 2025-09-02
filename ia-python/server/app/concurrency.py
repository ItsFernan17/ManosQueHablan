# app/concurrency.py
import asyncio
from typing import Any, Callable
from app.config import FFMPEG_CONCURRENCY

_SEM = asyncio.Semaphore(FFMPEG_CONCURRENCY)

async def guarded(func: Callable[..., Any], *args, **kwargs) -> Any:
    """
    Limita concurrencia de tareas pesadas. Ejecuta func en un ThreadPool
    para no bloquear el loop (útil para FFmpeg/gTTS y funciones sync).
    """
    async with _SEM:
        loop = asyncio.get_running_loop()
        return await loop.run_in_executor(None, lambda: func(*args, **kwargs))
