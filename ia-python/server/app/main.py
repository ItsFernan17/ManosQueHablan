# app/main.py
import os
import uuid
import shutil
import asyncio
import tempfile
import logging
from fastapi import FastAPI, UploadFile, File, Form, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from gtts import gTTS

from app.evaluator import evaluate_video_to_file
from app.utils import ensure_dirs, unique_name, RESULTS_DIR  # UPLOADS_DIR ya no se usa aquí
from app.utils_ffmpeg import (
    build_ass_file_per_detection,
    generate_tts_per_detection_items,
    ffmpeg_burn_subs_and_mix_audio,
    generate_tts_continuous_mp3,
)

# ==== Imports de los módulos auxiliares ====
from app.config import MAX_UPLOAD_MB, RESULTS_TTL_SECS
from app.logging_setup import get_app_logger, RequestIdAndLoggingMiddleware
from app.middlewares.upload_limit import MaxUploadSizeMiddleware
from app.concurrency import guarded
from app.storage import save_upload_to_path, is_valid_media
from app.cleanup import register_results_cleaner
# ===========================================

# -----------------------
# Inicialización
# -----------------------
ensure_dirs()

app = FastAPI(title="Sign Translator API", version="2.2.0")

# Logger y middlewares nuevos
logger = get_app_logger()
app.add_middleware(RequestIdAndLoggingMiddleware, logger=logger)
app.add_middleware(MaxUploadSizeMiddleware, max_bytes=MAX_UPLOAD_MB * 1024 * 1024)

# CORS (ajusta origins en producción)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Archivos estáticos
app.mount(
    "/static",
    StaticFiles(directory=os.path.join(os.path.dirname(__file__), "static")),
    name="static",
)

# Limpieza periódica por TTL (resultados)
register_results_cleaner(app, RESULTS_TTL_SECS, logger)

# -----------------------
# Helpers de sesión
# -----------------------
def create_session_dir() -> tuple[str, str]:
    """Crea carpeta por petición: static/results/<uuid>/"""
    session_id = uuid.uuid4().hex
    session_path = os.path.join(RESULTS_DIR, session_id)
    os.makedirs(session_path, exist_ok=True)
    return session_id, session_path


async def schedule_cleanup(path: str, delay_seconds: int = 3):
    """Borra la carpeta completa luego de delay_seconds."""
    try:
        await asyncio.sleep(delay_seconds)
        shutil.rmtree(path, ignore_errors=True)
    except Exception:
        pass

# -----------------------
# Instrumentación por etapas (para depurar 500s)
# -----------------------
async def stage(name: str, func, *args, **kwargs):
    logger.info(f"stage_start={name}")
    try:
        result = await guarded(func, *args, **kwargs)
        logger.info(f"stage_ok={name}")
        return result
    except Exception as e:
        logger.exception(f"stage_fail={name} error={e}")
        raise

# -----------------------
# Endpoints básicos
# -----------------------
@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/")
def root():
    return {
        "status": "ok",
        "service": "Sign Translator API",
        "version": "2.2.0",
        "endpoints": {
            "health": "/health",
            "upload_video (final)": "/upload_video",
            "evaluate-video (testing)": "/evaluate-video",
            "static": "/static/*",
        },
    }

# -------------------------------------------------------------------
# 1) TESTING: devuelve detecciones + video con overlays simples
# -------------------------------------------------------------------
@app.post("/evaluate-video")
async def evaluate_video_endpoint(
    file: UploadFile = File(..., description="Video a procesar (campo: file)"),
    threshold: float = Form(0.80),
    margin_frame: int = Form(1),
    delay_frames: int = Form(3),
):
    if not (file.content_type or "").startswith("video"):
        raise HTTPException(status_code=400, detail="El archivo debe ser un video.")

    session_id, session_dir = create_session_dir()
    input_name = unique_name("in", "mp4")
    input_path = os.path.join(session_dir, input_name)

    try:
        # Guardado robusto por chunks + validación
        await save_upload_to_path(file, input_path)
        size = os.path.getsize(input_path)
        logger.info(f"file_saved path={input_path} size={size}B")
        if size == 0:
            asyncio.create_task(schedule_cleanup(session_dir, delay_seconds=3))
            raise HTTPException(status_code=400, detail="No se recibió contenido en el campo 'file'.")
        if not is_valid_media(input_path):
            asyncio.create_task(schedule_cleanup(session_dir, delay_seconds=3))
            raise HTTPException(status_code=400, detail="El video recibido está vacío o corrupto.")

        out_name = unique_name("resultado_test", "mp4")
        out_path = os.path.join(session_dir, out_name)

        # PROTEGIDO (FFmpeg / pesado)
        detections, meta = await stage(
            "evaluate_video_to_file(draw=True)",
            evaluate_video_to_file,
            video_path=input_path,
            out_mp4_path=out_path,
            threshold=threshold,
            margin_frame=margin_frame,
            delay_frames=delay_frames,
            draw=True,
        )

        public_url = f"/static/results/{session_id}/{out_name}"
        asyncio.create_task(schedule_cleanup(session_dir, delay_seconds=3))

        return JSONResponse(
            {
                "success": True,
                "detections": detections,
                "meta": meta,
                "output_video_url": public_url,
            }
        )

    except Exception as e:
        asyncio.create_task(schedule_cleanup(session_dir, delay_seconds=3))
        raise HTTPException(status_code=500, detail=str(e))

# -------------------------------------------------------------------
# 2) PRODUCCIÓN: /upload_video (FINAL para el usuario)
#    - Campo: "video"
#    - Genera: Video vertical 720x1280 con SUBS + AUDIO TTS embebido
#    - Respuesta: { video_url, audio_url, texto_url }
# -------------------------------------------------------------------
@app.post("/upload_video")
async def upload_video_endpoint(
    video: UploadFile = File(..., description="Video a procesar (campo: video)"),
    threshold: float = Form(0.80),
    margin_frame: int = Form(1),
    delay_frames: int = Form(3),
    tts_lang: str = Form("es"),
):
    if not (video.content_type or "").startswith("video"):
        raise HTTPException(status_code=400, detail="El archivo debe ser un video.")

    # Carpeta de sesión
    session_id, session_dir = create_session_dir()

    # Guardar upload dentro de la sesión
    input_name = unique_name("in", "mp4")
    input_path = os.path.join(session_dir, input_name)

    try:
        # Guardado robusto por chunks + validación
        await save_upload_to_path(video, input_path)
        size = os.path.getsize(input_path)
        logger.info(f"file_saved path={input_path} size={size}B")
        if size == 0:
            asyncio.create_task(schedule_cleanup(session_dir, delay_seconds=3))
            raise HTTPException(status_code=400, detail="No se recibió contenido en el campo 'video'.")
        
        logger.info("checking_media_validity")
        is_valid = is_valid_media(input_path)
        logger.info(f"media_valid={is_valid}")
        if not is_valid:
            asyncio.create_task(schedule_cleanup(session_dir, delay_seconds=3))
            raise HTTPException(status_code=400, detail="El video recibido está vacío o corrupto.")

        # Corre evaluación SIN overlays para obtener detecciones limpias
        with tempfile.NamedTemporaryFile(prefix="tmp_eval_", suffix=".mp4",
                                         dir=session_dir, delete=False) as tmpf:
            tmp_eval_path = tmpf.name

        # PROTEGIDO
        detections, meta = await stage(
            "evaluate_video_to_file(draw=False)",
            evaluate_video_to_file,
            video_path=input_path,
            out_mp4_path=tmp_eval_path,
            threshold=threshold,
            margin_frame=margin_frame,
            delay_frames=delay_frames,
            draw=False,
        )

        # Transcripción (texto continuo)
        out_text_name = unique_name("transcripcion", "txt")
        out_text_path = os.path.join(session_dir, out_text_name)
        transcript = (
            ". ".join([str(d.get("label", "")).capitalize()
                       for d in detections if d.get("label")])
            if detections else "Sin detecciones válidas."
        )
        with open(out_text_path, "w", encoding="utf-8") as tf:
            tf.write(transcript)

        # Archivos finales de salida
        out_video_name = unique_name("resultado_vertical", "mp4")
        out_video_path = os.path.join(session_dir, out_video_name)

        out_audio_name = unique_name("tts_total", "mp3")
        out_audio_path = os.path.join(session_dir, out_audio_name)

        # MP3 continuo “como leído” (PROTEGIDO)
        audio_ok = await stage(
            "generate_tts_continuous_mp3",
            generate_tts_continuous_mp3,
            detections, out_audio_path, tts_lang
        )

        # Render de video con subtítulos + TTS sincronizado por detección (PROTEGIDO)
        with tempfile.TemporaryDirectory(dir=session_dir) as tmpdir:
            # 1) Subtítulos (una línea por detección)
            ass_path = os.path.join(tmpdir, "subs.ass")
            await stage("build_ass_file_per_detection", build_ass_file_per_detection, detections, ass_path)

            # 2) TTS por detección (para mezcla dentro del video)
            tts_items = await stage(
                "generate_tts_per_detection_items",
                generate_tts_per_detection_items,
                detections, tmpdir, tts_lang
            )

            # 3) Render final
            await stage(
                "ffmpeg_burn_subs_and_mix_audio",
                ffmpeg_burn_subs_and_mix_audio,
                src_video=input_path,
                ass_path=ass_path,
                tts_items=tts_items,
                out_video_path=out_video_path,
            )

        # URLs públicas
        video_url = f"/static/results/{session_id}/{out_video_name}"
        texto_url = f"/static/results/{session_id}/{out_text_name}"
        audio_url = f"/static/results/{session_id}/{out_audio_name}" if audio_ok else ""

        # Limpieza diferida de la carpeta de sesión
        asyncio.create_task(schedule_cleanup(session_dir, delay_seconds=3))

        return JSONResponse({
            "video_url": video_url,
            "audio_url": audio_url,   # MP3 continuo “como leído”
            "texto_url": texto_url,
        })

    except Exception as e:
        # En error también limpia la sesión
        asyncio.create_task(schedule_cleanup(session_dir, delay_seconds=3))
        raise HTTPException(status_code=500, detail=str(e))

# -------------------------------------------------------------------
# 3) TEST simple con un solo MP3 (ahora también por carpeta de sesión)
# -------------------------------------------------------------------
@app.post("/test")
async def test_endpoint(
    video: UploadFile = File(..., description="Video a procesar (campo: video)"),
    threshold: float = Form(0.80),
    margin_frame: int = Form(1),
    delay_frames: int = Form(3),
    tts_lang: str = Form("es"),
):
    if not (video.content_type or "").startswith("video"):
        raise HTTPException(status_code=400, detail="El archivo debe ser un video.")

    # Carpeta de sesión dedicada
    session_id, session_dir = create_session_dir()

    input_name = unique_name("in", "mp4")
    input_path = os.path.join(session_dir, input_name)

    try:
        # Guardado robusto por chunks + validación
        await save_upload_to_path(video, input_path)
        size = os.path.getsize(input_path)
        logger.info(f"file_saved path={input_path} size={size}B")
        if size == 0:
            asyncio.create_task(schedule_cleanup(session_dir, delay_seconds=3))
            raise HTTPException(status_code=400, detail="No se recibió contenido en el campo 'video'.")
        if not is_valid_media(input_path):
            asyncio.create_task(schedule_cleanup(session_dir, delay_seconds=3))
            raise HTTPException(status_code=400, detail="El video recibido está vacío o corrupto.")

        out_video_name = unique_name("resultado", "mp4")
        out_video_path = os.path.join(session_dir, out_video_name)

        # PROTEGIDO
        detections, meta = await stage(
            "evaluate_video_to_file(draw=True)",
            evaluate_video_to_file,
            video_path=input_path,
            out_mp4_path=out_video_path,
            threshold=threshold,
            margin_frame=margin_frame,
            delay_frames=delay_frames,
            draw=True,
        )

        # Transcripción detallada
        out_text_name = unique_name("transcripcion", "txt")
        out_text_path = os.path.join(session_dir, out_text_name)
        transcript_lines = []
        if detections:
            for d in detections:
                start_s = d.get("start_time", 0.0)
                end_s = d.get("end_time", 0.0)
                label = d.get("label", "")
                prob = d.get("prob", 0.0)
                transcript_lines.append(f"[{secs_to_hhmmss_mmm(start_s)} → {secs_to_hhmmss_mmm(end_s)}] {label} ({prob:.1f}%)")
        else:
            transcript_lines.append("(Sin detecciones sobre el umbral)")

        with open(out_text_path, "w", encoding="utf-8") as tf:
            tf.write("\n".join(transcript_lines))

        # TTS simple (PROTEGIDO)
        out_audio_name = unique_name("audio", "mp3")
        out_audio_path = os.path.join(session_dir, out_audio_name)
        try:
            spoken_text = (
                ". ".join([d.get("label", "") for d in detections if d.get("label")])
                if detections
                else "Sin detecciones válidas."
            )
            await stage(
                "gTTS_simple",
                lambda txt, lang, outp: gTTS(text=txt, lang=lang).save(outp),
                spoken_text, tts_lang, out_audio_path
            )
        except Exception:
            out_audio_path = None

        # URLs por sesión
        resp = {
            "video_url": f"/static/results/{session_id}/{out_video_name}",
            "audio_url": f"/static/results/{session_id}/{out_audio_name}" if out_audio_path else "",
            "texto_url": f"/static/results/{session_id}/{out_text_name}",
        }

        # Limpieza diferida de la carpeta de sesión
        asyncio.create_task(schedule_cleanup(session_dir, delay_seconds=3))
        return JSONResponse(resp)

    except Exception as e:
        asyncio.create_task(schedule_cleanup(session_dir, delay_seconds=3))
        raise HTTPException(status_code=500, detail=str(e))


def secs_to_hhmmss_mmm(s: float) -> str:
    """Convierte segundos float a 'hh:mm:ss.mmm'."""
    msec = int(round((s - int(s)) * 1000))
    secs_total = int(s)
    h = secs_total // 3600
    m = (secs_total % 3600) // 60
    sec = secs_total % 60
    return f"{h:02d}:{m:02d}:{sec:02d}.{msec:03d}"
