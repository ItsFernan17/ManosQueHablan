# app/main.py
import os
import uuid
import shutil
import asyncio
import tempfile
import logging
from typing import Tuple
from fastapi import FastAPI, UploadFile, File, Form, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from gtts import gTTS

from app.evaluator import evaluate_video_to_file
from app.utils import ensure_dirs, unique_name, RESULTS_DIR
from app.utils_ffmpeg import (
    build_ass_file_per_detection,
    generate_tts_per_detection_items,
    ffmpeg_burn_subs_and_mix_audio_no_resize,
    generate_tts_continuous_mp3,
)
from app.manifest import (
    create_manifest, 
    read_manifest, 
    mark_as_delivered, 
    safe_file_save
)
from app.storage import (
    save_upload_to_path,
    save_text_to_session,
    create_processing_lock,
    remove_processing_lock,
    is_valid_media
)

# ==== Imports de los módulos auxiliares ====
from app.config import MAX_UPLOAD_MB, RESULTS_TTL_SECS, MAX_CONCURRENT_REQUESTS
from app.logging_setup import get_app_logger, RequestIdAndLoggingMiddleware
from app.middlewares.upload_limit import MaxUploadSizeMiddleware
from app.concurrency import guarded
from app.cleanup import register_results_cleaner
# ===========================================

# -----------------------
# Inicialización
# -----------------------
ensure_dirs()

app = FastAPI(title="Sign Translator API", version="2.2.0")

# Semáforo para limitar concurrencia y proteger IA
processing_semaphore = asyncio.Semaphore(MAX_CONCURRENT_REQUESTS)

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
def create_session_dir() -> Tuple[str, str]:
    """Crea carpeta por petición: static/results/<uuid>/ con manifest"""
    session_id = uuid.uuid4().hex
    session_path = os.path.join(RESULTS_DIR, session_id)
    os.makedirs(session_path, exist_ok=True)
    
    # Crear manifest con archivos requeridos estándar
    required_files = ["video.mp4", "audio.mp3", "transcript.txt"]
    create_manifest(session_path, required_files)
    
    return session_id, session_path

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
            "download": "/download/{session_id}/{filename}",
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
    create_processing_lock(session_dir)
    
    input_name = unique_name("in", "mp4")
    input_path = os.path.join(session_dir, input_name)

    try:
        # Guardado robusto por chunks + validación
        await save_upload_to_path(file, input_path)
        size = os.path.getsize(input_path)
        logger.info(f"file_saved path={input_path} size={size}B")
        if size == 0:
            remove_processing_lock(session_dir)
            raise HTTPException(status_code=400, detail="No se recibió contenido en el campo 'file'.")
        if not is_valid_media(input_path):
            remove_processing_lock(session_dir)
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

        remove_processing_lock(session_dir)
        public_url = f"/static/results/{session_id}/{out_name}"

        return JSONResponse(
            {
                "success": True,
                "detections": detections,
                "meta": meta,
                "output_video_url": public_url,
            }
        )

    except Exception as e:
        remove_processing_lock(session_dir)
        raise HTTPException(status_code=500, detail=str(e))

# -------------------------------------------------------------------
# 2) PRODUCCIÓN: /upload_video (FINAL para el usuario)
#    - Campo: "video"
#    - Genera: Video con SUBS + AUDIO TTS embebido (sin cambio de resolución)
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
    import time
    start_time = time.time()
    
    if not (video.content_type or "").startswith("video"):
        raise HTTPException(status_code=400, detail="El archivo debe ser un video.")

    # Límite de concurrencia para proteger IA
    async with processing_semaphore:
        logger.info(f"🚀 INICIO PROCESAMIENTO - Session iniciada - Slots disponibles: {processing_semaphore._value} - Timestamp: {start_time}")
        
        # Carpeta de sesión
        session_id, session_dir = create_session_dir()

        try:
            # Crear lock de procesamiento
            create_processing_lock(session_dir)
            
            # Guardado robusto por chunks + validación usando sistema temporal
            input_name = "video_input.mp4"
            input_path = os.path.join(session_dir, input_name)
            
            # Guardar temporalmente para validación
            upload_start = time.time()
            await save_upload_to_path(video, input_path + ".temp")
            upload_end = time.time()
            size = os.path.getsize(input_path + ".temp")
            logger.info(f"📁 ARCHIVO GUARDADO - Tiempo upload: {upload_end - upload_start:.2f}s - Tamaño: {size}B")
            if size == 0:
                remove_processing_lock(session_dir)
                raise HTTPException(status_code=400, detail="No se recibió contenido en el campo 'video'.")
            
            validation_start = time.time()
            logger.info("🔍 VALIDANDO MEDIA...")
            is_valid = is_valid_media(input_path + ".temp")
            validation_end = time.time()
            logger.info(f"✅ VALIDACIÓN COMPLETA - Tiempo: {validation_end - validation_start:.2f}s - Válido: {is_valid}")
            if not is_valid:
                remove_processing_lock(session_dir)
                os.remove(input_path + ".temp")
                raise HTTPException(status_code=400, detail="El video recibido está vacío o corrupto.")
            
            # Mover archivo temporal al definitivo
            os.rename(input_path + ".temp", input_path)

            # Corre evaluación SIN overlays para obtener detecciones limpias
            with tempfile.NamedTemporaryFile(prefix="tmp_eval_", suffix=".mp4",
                                             dir=session_dir, delete=False) as tmpf:
                tmp_eval_path = tmpf.name

            # PROTEGIDO
            ai_start = time.time()
            logger.info("🤖 INICIANDO PROCESAMIENTO IA...")
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
            ai_end = time.time()
            logger.info(f"🧠 IA COMPLETADA - Tiempo: {ai_end - ai_start:.2f}s - Detecciones: {len(detections)}")

            # Transcripción (texto continuo) - guardado DIRECTO (más simple)
            transcript = (
                ". ".join([str(d.get("label", "")).capitalize()
                           for d in detections if d.get("label")])
                if detections else "Sin detecciones válidas."
            )
            transcript_start = time.time()
            logger.info("📝 GENERANDO TRANSCRIPT...")
            
            # Guardar directamente sin manifest para evitar deadlock
            transcript_path = os.path.join(session_dir, "transcript.txt")
            with open(transcript_path, "w", encoding="utf-8") as f:
                f.write(transcript)
            transcript_end = time.time()
            logger.info(f"✍️ TRANSCRIPT GUARDADO - Tiempo: {transcript_end - transcript_start:.2f}s")

            # MP3 continuo "como leído" (PROTEGIDO) - también directo
            audio_start = time.time()
            logger.info("🔊 INICIANDO GENERACIÓN DE AUDIO...")
            spoken_text = (
                ". ".join([d.get("label", "") for d in detections if d.get("label")])
                if detections
                else "Sin detecciones válidas."
            )
            
            try:
                audio_path = os.path.join(session_dir, "audio.mp3")
                await stage(
                    "generate_audio_direct",
                    lambda: gTTS(text=spoken_text, lang=tts_lang).save(audio_path)
                )
                audio_end = time.time()
                audio_ok = True
                logger.info(f"🎵 AUDIO GENERADO - Tiempo: {audio_end - audio_start:.2f}s")
            except Exception as e:
                audio_end = time.time()
                logger.error(f"❌ ERROR AUDIO - Tiempo: {audio_end - audio_start:.2f}s - Error: {e}")
                audio_ok = False

            # Render de video con subtítulos + TTS sincronizado por detección (PROTEGIDO)
            video_render_start = time.time()
            logger.info("🎬 INICIANDO RENDER DE VIDEO...")
            
            with tempfile.TemporaryDirectory(dir=session_dir) as tmpdir:
                # 1) Subtítulos (una línea por detección) - timing dinámico inteligente SIN resolución fija
                ass_path = os.path.join(tmpdir, "subs.ass")
                subs_start = time.time()
                logger.info("📺 GENERANDO SUBTÍTULOS...")
                await stage(
                    "build_ass_file_no_fixed_res",
                    lambda: build_ass_file_per_detection(
                        detections, 
                        ass_path, 
                        subtitle_delay=0.0
                    )
                )
                logger.info("Subtítulos generados")

                # 2) TTS por detección (para mezcla dentro del video) - timing dinámico inteligente
                logger.info("Generando TTS por detección...")
                tts_items = await stage(
                    "generate_tts_per_detection_items",
                    generate_tts_per_detection_items,
                    detections, 
                    tmpdir, 
                    tts_lang, 
                    voice_offset=0.0
                )
                logger.info(f"TTS generado: {len(tts_items)} items")

                # 3) Render final - SIN redimensionar - DIRECTO
                final_video_path = os.path.join(session_dir, "video.mp4")
                ffmpeg_start = time.time()
                logger.info("🎞️ INICIANDO RENDER FINAL CON FFMPEG...")
                await stage(
                    "ffmpeg_render_no_resize",
                    ffmpeg_burn_subs_and_mix_audio_no_resize,
                    input_path,
                    ass_path,
                    tts_items,
                    final_video_path
                )
                ffmpeg_end = time.time()
                video_render_end = time.time()
                logger.info(f"🎥 RENDER COMPLETADO - Tiempo FFmpeg: {ffmpeg_end - ffmpeg_start:.2f}s - Tiempo total render: {video_render_end - video_render_start:.2f}s")
            
            video_ok = True
            
            # Remover lock de procesamiento 
            remove_processing_lock(session_dir)
            
            # Tiempo total de procesamiento
            total_end = time.time()
            total_time = total_end - start_time
            logger.info(f"🏁 PROCESAMIENTO COMPLETADO - Tiempo TOTAL: {total_time:.2f}s - Session: {session_id}")
            
            # URLs públicas usando nombres estándar
            video_url = f"/static/results/{session_id}/video.mp4"
            texto_url = f"/static/results/{session_id}/transcript.txt"
            audio_url = f"/static/results/{session_id}/audio.mp3" if audio_ok else ""

            # NO hay limpieza automática - el manifest y cleanup.py se encargan
            return JSONResponse({
                "video_url": video_url,
                "audio_url": audio_url,   # MP3 continuo "como leído"
                "texto_url": texto_url,
            })

        except Exception as e:
            # En error remover lock y NO limpiar automáticamente
            remove_processing_lock(session_dir)
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
    create_processing_lock(session_dir)

    input_name = unique_name("in", "mp4")
    input_path = os.path.join(session_dir, input_name)

    try:
        # Guardado robusto por chunks + validación
        await save_upload_to_path(video, input_path)
        size = os.path.getsize(input_path)
        logger.info(f"file_saved path={input_path} size={size}B")
        if size == 0:
            remove_processing_lock(session_dir)
            raise HTTPException(status_code=400, detail="No se recibió contenido en el campo 'video'.")
        if not is_valid_media(input_path):
            remove_processing_lock(session_dir)
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

        remove_processing_lock(session_dir)

        # URLs por sesión
        resp = {
            "video_url": f"/static/results/{session_id}/{out_video_name}",
            "audio_url": f"/static/results/{session_id}/{out_audio_name}" if out_audio_path else "",
            "texto_url": f"/static/results/{session_id}/{out_text_name}",
        }

        return JSONResponse(resp)

    except Exception as e:
        remove_processing_lock(session_dir)
        raise HTTPException(status_code=500, detail=str(e))


def secs_to_hhmmss_mmm(s: float) -> str:
    """Convierte segundos float a 'hh:mm:ss.mmm'."""
    msec = int(round((s - int(s)) * 1000))
    secs_total = int(s)
    h = secs_total // 3600
    m = (secs_total % 3600) // 60
    sec = secs_total % 60
    return f"{h:02d}:{m:02d}:{sec:02d}.{msec:03d}"


# -------------------------------------------------------------------
# ENDPOINT DE DESCARGA - Marca como delivered cuando se descarga
# -------------------------------------------------------------------
@app.get("/download/{session_id}/{filename}")
async def download_file(session_id: str, filename: str):
    """
    Endpoint para descargar archivos que actualiza el manifest a 'delivered'
    cuando se inicia la descarga.
    """
    from fastapi.responses import FileResponse
    
    session_dir = os.path.join(RESULTS_DIR, session_id)
    file_path = os.path.join(session_dir, filename)
    
    # Verificar que el archivo existe
    if not os.path.exists(file_path):
        raise HTTPException(status_code=404, detail="Archivo no encontrado")
    
    # Verificar que la sesión está lista
    manifest = read_manifest(session_dir)
    if manifest is None:
        raise HTTPException(status_code=404, detail="Sesión no encontrada")
    
    if manifest.get("status") != "ready":
        raise HTTPException(status_code=400, detail="La sesión aún no está lista")
    
    # Marcar como delivered cuando se inicia la descarga
    mark_as_delivered(session_dir)
    logger.info(f"Session {session_id} marked as delivered - file: {filename}")
    
    return FileResponse(
        path=file_path,
        filename=filename,
        media_type='application/octet-stream'
    )


# -------------------------------------------------------------------
# ENDPOINT DE ESTADO - Verifica el estado de una sesión
# -------------------------------------------------------------------
@app.get("/status/{session_id}")
async def get_session_status(session_id: str):
    """
    Endpoint para verificar el estado de una sesión.
    """
    session_dir = os.path.join(RESULTS_DIR, session_id)
    
    # Verificar que la sesión existe
    if not os.path.exists(session_dir):
        raise HTTPException(status_code=404, detail="Sesión no encontrada")
    
    # Leer manifest
    manifest = read_manifest(session_dir)
    if manifest is None:
        raise HTTPException(status_code=404, detail="Manifest no encontrado")
    
    # Verificar qué archivos están disponibles
    available_files = []
    for filename in manifest.get("required", []):
        file_path = os.path.join(session_dir, filename)
        if os.path.exists(file_path):
            available_files.append({
                "filename": filename,
                "size": os.path.getsize(file_path),
                "url": f"/static/results/{session_id}/{filename}"
            })
    
    return {
        "session_id": session_id,
        "status": manifest.get("status", "unknown"),
        "required": manifest.get("required", []),
        "have": manifest.get("have", []),
        "available_files": available_files,
        "last_update": manifest.get("last_update", 0),
        "created_at": manifest.get("created_at", 0)
    }