# app/logging_setup.py
import sys
import time
import uuid
import logging
from fastapi import Request, Response
from starlette.middleware.base import BaseHTTPMiddleware

def get_app_logger() -> logging.Logger:
    logger = logging.getLogger("sign_translator")
    logger.setLevel(logging.INFO)
    logger.propagate = False  # no heredar formateadores de uvicorn
    if not logger.handlers:
        h = logging.StreamHandler(stream=sys.stdout)
        h.setFormatter(logging.Formatter("%(asctime)s | %(levelname)s | %(message)s"))
        logger.addHandler(h)

    # (Opcional) silenciar uvicorn.access para evitar choques de formato en dev
    uvicorn_access = logging.getLogger("uvicorn.access")
    uvicorn_access.propagate = False
    for _h in list(uvicorn_access.handlers):
        uvicorn_access.removeHandler(_h)

    return logger

class RequestIdAndLoggingMiddleware(BaseHTTPMiddleware):
    """Asigna X-Request-Id y registra path, status y ms."""
    def __init__(self, app, logger: logging.Logger):
        super().__init__(app)
        self.logger = logger

    async def dispatch(self, request: Request, call_next):
        req_id = request.headers.get("X-Request-Id") or uuid.uuid4().hex
        start = time.perf_counter()
        try:
            response: Response = await call_next(request)
        except Exception:
            ms = int((time.perf_counter() - start) * 1000)
            self.logger.exception(f"req_id={req_id} path={request.url.path} status=500 ms={ms}")
            raise
        ms = int((time.perf_counter() - start) * 1000)
        response.headers["X-Request-Id"] = req_id
        self.logger.info(f"req_id={req_id} path={request.url.path} status={response.status_code} ms={ms}")
        return response
