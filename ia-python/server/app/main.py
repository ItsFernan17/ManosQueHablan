import os
import uuid
import shutil
import asyncio
import tempfile
from fastapi import FastAPI, UploadFile, File, Form, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from gtts import gTTS

from app.evaluator import evaluate_video_to_file
from app.utils import ensure_dirs, unique_name, RESULTS_DIR, UPLOADS_DIR
from app.utils_ffmpeg import (
    build_ass_file_per_detection,
    generate_tts_per_detection_items,
    ffmpeg_burn_subs_and_mix_audio,
    generate_tts_continuous_mp3,
)

# -----------------------
# Inicialización
# -----------------------
ensure_dirs()

app = FastAPI(title="Sign Translator API", version="2.2.0")

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

# -----------------------
# Helpers de sesión
# -----------------------
def create_session_dir() -> tuple[str, str]:
    """Crea carpeta por petición: static/results/<uuid>/"""
    session_id = uuid.uuid4().hex
    session_path = os.path.join(RESULTS_DIR, session_id)
    os.makedirs(session_path, exist_ok=True)
    return session_id, session_path


async def schedule_cleanup(path: str, delay_seconds: int = 5):
    """Borra la carpeta completa luego de delay_seconds."""
    try:
        await asyncio.sleep(delay_seconds)
        shutil.rmtree(path, ignore_errors=True)
    except Exception:
        pass

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
        with open(input_path, "wb") as f:
            f.write(await file.read())

        out_name = unique_name("resultado_test", "mp4")
        out_path = os.path.join(session_dir, out_name)

        detections, meta = evaluate_video_to_file(
            video_path=input_path,
            out_mp4_path=out_path,
            threshold=threshold,
            margin_frame=margin_frame,
            delay_frames=delay_frames,
            draw=True,
        )

        public_url = f"/static/results/{session_id}/{out_name}"
        asyncio.create_task(schedule_cleanup(session_dir, delay_seconds=5))

        return JSONResponse(
            {
                "success": True,
                "detections": detections,
                "meta": meta,
                "output_video_url": public_url,
            }
        )

    except Exception as e:
        asyncio.create_task(schedule_cleanup(session_dir, delay_seconds=5))
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
        with open(input_path, "wb") as f:
            f.write(await video.read())

        # Corre evaluación SIN overlays para obtener detecciones limpias
        with tempfile.NamedTemporaryFile(prefix="tmp_eval_", suffix=".mp4",
                                         dir=session_dir, delete=False) as tmpf:
            tmp_eval_path = tmpf.name

        detections, meta = evaluate_video_to_file(
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

        # MP3 continuo “como leído” (para que el usuario lo pueda escuchar aparte)
        audio_ok = generate_tts_continuous_mp3(detections, out_audio_path, lang=tts_lang)

        # Render de video con subtítulos + TTS sincronizado por detección
        with tempfile.TemporaryDirectory(dir=session_dir) as tmpdir:
            # 1) Subtítulos (una línea por detección)
            ass_path = os.path.join(tmpdir, "subs.ass")
            build_ass_file_per_detection(detections, ass_path)

            # 2) TTS por detección (para mezcla dentro del video)
            tts_items = generate_tts_per_detection_items(detections, tmpdir, lang=tts_lang)

            # 3) Render final
            ffmpeg_burn_subs_and_mix_audio(
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
        asyncio.create_task(schedule_cleanup(session_dir, delay_seconds=5))

        return JSONResponse({
            "video_url": video_url,
            "audio_url": audio_url,   # MP3 continuo “como leído”
            "texto_url": texto_url,
        })

    except Exception as e:
        # En error también limpia la sesión
        asyncio.create_task(schedule_cleanup(session_dir, delay_seconds=5))
        raise HTTPException(status_code=500, detail=str(e))


# -------------------------------------------------------------------
# 3) TEST simple con un solo MP3
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

    input_name = unique_name("in", "mp4")
    input_path = os.path.join(UPLOADS_DIR, input_name)

    try:
        with open(input_path, "wb") as f:
            f.write(await video.read())

        out_video_name = unique_name("resultado", "mp4")
        out_video_path = os.path.join(RESULTS_DIR, out_video_name)

        detections, meta = evaluate_video_to_file(
            video_path=input_path,
            out_mp4_path=out_video_path,
            threshold=threshold,
            margin_frame=margin_frame,
            delay_frames=delay_frames,
            draw=True,
        )

        # Transcripción detallada
        out_text_name = unique_name("transcripcion", "txt")
        out_text_path = os.path.join(RESULTS_DIR, out_text_name)
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

        # TTS simple
        out_audio_name = unique_name("audio", "mp3")
        out_audio_path = os.path.join(RESULTS_DIR, out_audio_name)
        try:
            spoken_text = (
                ". ".join([d.get("label", "") for d in detections if d.get("label")])
                if detections
                else "Sin detecciones válidas."
            )
            gTTS(text=spoken_text, lang=tts_lang).save(out_audio_path)
        except Exception:
            out_audio_path = None

        return JSONResponse(
            {
                "video_url": f"/static/results/{out_video_name}",
                "audio_url": f"/static/results/{out_audio_name}" if out_audio_path else "",
                "texto_url": f"/static/results/{out_text_name}",
            }
        )

    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        try:
            if os.path.exists(input_path):
                os.remove(input_path)
        except Exception:
            pass


def secs_to_hhmmss_mmm(s: float) -> str:
    """Convierte segundos float a 'hh:mm:ss.mmm'."""
    msec = int(round((s - int(s)) * 1000))
    secs_total = int(s)
    h = secs_total // 3600
    m = (secs_total % 3600) // 60
    sec = secs_total % 60
    return f"{h:02d}:{m:02d}:{sec:02d}.{msec:03d}"
