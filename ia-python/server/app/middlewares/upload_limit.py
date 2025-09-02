# app/middlewares/upload_limit.py
from starlette.middleware.base import BaseHTTPMiddleware
from fastapi import Request
from fastapi.responses import PlainTextResponse

class MaxUploadSizeMiddleware(BaseHTTPMiddleware):
    """
    Límite por Content-Length. Si el cliente no envía Content-Length,
    deja pasar (la mayoría sí lo envía). Límite en bytes.
    """
    def __init__(self, app, max_bytes: int):
        super().__init__(app)
        self.max_bytes = max_bytes

    async def dispatch(self, request: Request, call_next):
        cl = request.headers.get("content-length")
        if cl:
            try:
                if int(cl) > self.max_bytes:
                    return PlainTextResponse("Payload Too Large", status_code=413)
            except ValueError:
                # Header inválido → deja fluir
                pass
        return await call_next(request)
