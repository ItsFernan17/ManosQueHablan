# app/utils_ffmpeg.py
import os
import subprocess
import tempfile
from typing import List, Dict, Any, Tuple

import cv2
from gtts import gTTS


def get_ffmpeg_path() -> str:
    """Obtiene ffmpeg binario portable desde imageio-ffmpeg; si falla, usa 'ffmpeg' del PATH."""
    try:
        import imageio_ffmpeg
        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:
        return "ffmpeg"


def get_video_duration_seconds(path: str) -> float:
    """Duración real del video en segundos usando OpenCV."""
    cap = cv2.VideoCapture(path)
    if not cap.isOpened():
        return 0.0
    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    frames = cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0
    cap.release()
    if fps <= 0:
        return 0.0
    return float(frames) / float(fps)


# ---------------------------
# Helpers detecciones / texto
# ---------------------------

def _clean_label(text: str) -> str:
    t = (text or "").strip()
    if not t:
        return ""
    return t[0].upper() + t[1:]


def _group_by_gaps(dets: List[Dict[str, Any]], gap_seconds: float = 1.2) -> List[List[Dict[str, Any]]]:
    """Agrupa detecciones separadas por pausas > gap_seconds."""
    if not dets:
        return []
    dets_sorted = sorted(dets, key=lambda d: d.get("start_time", 0.0))
    groups = [[dets_sorted[0]]]
    last_end = dets_sorted[0].get("end_time", 0.0)
    for d in dets_sorted[1:]:
        start = d.get("start_time", 0.0)
        if start - last_end > gap_seconds:
            groups.append([d])
        else:
            groups[-1].append(d)
        last_end = d.get("end_time", start)
    return groups


def _natural_join_es(words: List[str]) -> str:
    words = [w.strip() for w in words if w and w.strip()]
    if not words:
        return ""
    if len(words) == 1:
        return f"{words[0]}."
    if len(words) == 2:
        return f"{words[0]} y {words[1]}."
    return f"{', '.join(words[:-1])} y {words[-1]}."


def _build_sentence_for_group(group: List[Dict[str, Any]]) -> str:
    labels = [str(d.get("label", "")).lower() for d in group]
    # NO eliminamos duplicados consecutivos - preservamos TODAS las palabras detectadas
    out = []
    for l in labels:
        if l and l.strip():
            out.append(l.strip())
    sent = _natural_join_es(out)
    if sent:
        sent = sent[0].upper() + sent[1:]
    return sent


# ----------------------------------------
# Subtítulos por detección (no agrupados)
# ----------------------------------------

def build_ass_file_per_detection(
   detections: List[Dict[str, Any]],
    ass_path: str,
    font: str = "Arial",       # Fuente compatible universalmente
    font_size: int = 50,       # Tamaño optimizado para 720p
    margin_v: int = 84,        # distancia desde el borde inferior
    subtitle_delay: float = 0.0
) -> None:
    """
    Subtítulos estilo 'pill':
    - Texto blanco pequeño centrado.
    - Caja negra opaca con bordes suaves (simula esquinas redondeadas).
    - Sin forzar la resolución del video (solo coordenadas de referencia).
    """
    header = f"""[Script Info]
Title: Subs estilo pill
ScriptType: v4.00+
WrapStyle: 2
ScaledBorderAndShadow: yes
PlayResX: 720
PlayResY: 1280

[V4+ Styles]
; Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
; PrimaryColour  -> texto blanco (opaco)
; OutlineColour  -> color de la CAJA (negro con ~85% opacidad)
; BackColour     -> sombra/halo (ligero)
Style: Pill,{font},{font_size},&H00FFFFFF,&H000000FF,&H2A000000,&H5A000000,-1,0,0,0,100,100,0,0,3,10,0,2,30,30,{margin_v},1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
"""

    def _to_ass_time(s: float) -> str:
        h = int(s // 3600); m = int((s % 3600) // 60); sec = s % 60
        return f"{h:d}:{m:02d}:{sec:05.2f}"

    lines = []
    used = []  # evitar solapes

    for d in sorted(detections or [], key=lambda x: x.get("start_time", 0.0)):
        label = _clean_label(str(d.get("label", "")))
        if not label:
            continue

        # pequeño retardo dinámico (como ya hacías)
        dur = float(d.get("end_time", 0.0)) - float(d.get("start_time", 0.0))
        if dur < 0.5:
            dyn = 0.01
        elif dur < 2.0:
            dyn = dur * 0.02
        else:
            dyn = 0.04

        start = float(d.get("end_time", 0.0)) + max(subtitle_delay, dyn)
        end   = start + min(1.2, max(0.8, dur * 0.8))

        for ps, pe in used:
            if start < pe:
                start = pe + 0.02
                end   = start + min(1.2, max(0.8, dur * 0.8))
        used.append((start, end))

        # Truco para “bordes suaves” del pill:
        #   - BorderStyle=3 crea caja opaca
        #   - \bord ajusta acolchado; \be suaviza bordes (blur)
        #   - \an2 asegura centrado abajo (alignment 2)
        tag = r"{\an2\bord10\be2}"
        lines.append(f"Dialogue: 0,{_to_ass_time(start)},{_to_ass_time(end)},Pill,,0,0,0,,{tag}{label}")

    with open(ass_path, "w", encoding="utf-8") as f:
        f.write(header + "\n".join(lines))


# ----------------------------------------
# TTS por detección (sin agrupar) -> VIDEO
# ----------------------------------------

def generate_tts_per_detection_items(
    detections: List[Dict[str, Any]],
    tmpdir: str,
    lang: str = "es",
    tld: str = "com.mx",
    voice_offset: float = 0.0,  # retraso adicional en segundos después del end_time
) -> List[Dict[str, Any]]:
    """
    Crea un MP3 por detección con el label capitalizado para sincronia en el VIDEO.
    La voz aparece DESPUÉS de que termine cada seña (end_time + voice_offset).
    Devuelve [{'start_time': s, 'end_time': e, 'audio_path': mp3}, ...]
    """
    items: List[Dict[str, Any]] = []
    audio_times = []  # Para evitar overlaps de audio: [(start, end), ...]
    
    for i, d in enumerate(sorted(detections or [], key=lambda x: x.get("start_time", 0.0)), start=1):
        label = _clean_label(str(d.get("label", "")))
        if not label:
            continue
        
        # Calcular delay dinámico basado en duración de la seña (igual que subtítulos)
        sign_duration = float(d.get("end_time", 0.0)) - float(d.get("start_time", 0.0))
        
        if sign_duration < 0.5:
            dynamic_delay = 0.01  # 10ms para señas muy rápidas
        elif sign_duration < 2.0:
            dynamic_delay = sign_duration * 0.02  # 2% de la duración de la seña
        else:
            dynamic_delay = 0.04  # 40ms máximo para señas largas
        
        # La voz aparece DESPUÉS de que termine la seña con delay dinámico
        start = max(0.0, float(d.get("end_time", 0.0)) + max(voice_offset, dynamic_delay))
        # Duración del audio proporcional a la longitud de la palabra
        audio_duration = min(1.5, max(0.6, len(label) * 0.1 + 0.4))  # 0.4s base + 0.1s por carácter
        end = start + audio_duration
        
        # Evitar overlaps con audios anteriores
        for prev_start, prev_end in audio_times:
            if start < prev_end:
                # Si hay overlap, mover el inicio después del anterior (gap mínimo)
                start = prev_end + 0.02  # Gap de solo 20ms
                end = start + audio_duration  # Mantener duración dinámica
        
        audio_times.append((start, end))
        audio_path = os.path.join(tmpdir, f"tts_det_{i:03d}.mp3")
        try:
            gTTS(text=label, lang=lang, tld=tld, slow=False).save(audio_path)
            items.append({"start_time": start, "end_time": end, "audio_path": audio_path})
        except Exception:
            continue

    if not items:
        p = os.path.join(tmpdir, "tts_vacio.mp3")
        try:
            gTTS(text="Sin detecciones válidas.", lang=lang, tld=tld, slow=False).save(p)
            items.append({"start_time": 0.0, "end_time": 0.0, "audio_path": p})
        except Exception:
            pass

    return items


# ----------------------------------------
# TTS continuo (agrupado) -> MP3 de salida
# ----------------------------------------

def generate_tts_continuous_mp3(
    detections: List[Dict[str, Any]],
    out_mp3_path: str,
    lang: str = "es",
    tld: str = "com.mx",
    gap_seconds: float = 1.2
) -> bool:
    """
    Genera UN solo MP3 continuo, sonando “como leído”.
    - Agrupa detecciones por pausas (> gap_seconds).
    - Cada grupo se convierte en una frase natural: "Hola, gracias y por favor."
    - Une todas las frases con punto y espacio.
    """
    if not detections:
        text = "Sin detecciones válidas."
    else:
        groups = _group_by_gaps(detections, gap_seconds=gap_seconds)
        sentences = []
        for g in groups:
            s = _build_sentence_for_group(g)
            if s:
                sentences.append(s)
        text = " ".join(sentences) if sentences else "Sin detecciones válidas."

    try:
        gTTS(text=text, lang=lang, tld=tld, slow=False).save(out_mp3_path)
        return True
    except Exception:
        return False


def concat_tts_items_to_mp3(tts_items: List[Dict[str, Any]], out_mp3_path: str) -> bool:
    """
    (Aún disponible si lo necesitas) Concatena MP3 individuales a un único MP3.
    Preferible usar generate_tts_continuous_mp3 para un audio más natural.
    """
    if not tts_items:
        return False

    with tempfile.NamedTemporaryFile(mode="w", suffix=".txt", delete=False, encoding="utf-8") as lf:
        list_path = lf.name
        for it in tts_items:
            p = os.path.abspath(it["audio_path"]).replace("\\", "/")
            lf.write(f"file '{p}'\n")

    ffmpeg = get_ffmpeg_path()
    cmd = [
        ffmpeg, "-y",
        "-f", "concat", "-safe", "0", "-i", list_path,
        "-c", "copy",
        out_mp3_path
    ]
    run = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    ok = (run.returncode == 0)

    if not ok:
        cmd = [
            ffmpeg, "-y",
            "-f", "concat", "-safe", "0", "-i", list_path,
            "-vn",
            "-ar", "44100", "-ac", "2", "-b:a", "128k",
            out_mp3_path
        ]
        run = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        ok = (run.returncode == 0)

    try:
        os.remove(list_path)
    except Exception:
        pass

    return ok


# ---------------------------
# Filtros FFmpeg (video+audio)
# ---------------------------

def build_ffmpeg_filter_and_inputs(
    src_video: str,
    ass_path: str,
    tts_items: List[Dict[str, Any]],
    video_duration: float
) -> Tuple[List[str], List[str]]:
    """
    Prepara entradas y filtros:
      - Video: solo ASS (sin rescalado)
      - Audio: silencio base recortado a duración real + todos los TTS con adelay + amix
    """
    # Entradas (0 = video base, 1..N = TTS)
    inputs = ["-i", src_video]
    for item in tts_items:
        inputs += ["-i", item["audio_path"]]

    # Escapar ruta ASS (Linux/Windows)
    ass_escaped = ass_path.replace("\\", "/").replace(":", r"\:").replace("'", r"\'")

    # Cadena de video: solo subtítulos
    vchain = f"[0:v]ass='{ass_escaped}'[vout]"

    # ===== Audio =====
    # Silencio base FINITO (stereo/44.1k) recortado a duración real
    dur = max(0.01, float(video_duration))
    a_parts = [
        "anullsrc=channel_layout=stereo:sample_rate=44100[base0]",
        f"[base0]atrim=0:{dur:.3f},asetpts=N/SR/TB[base]"
    ]

    # Mezclaremos base + N TTS
    mix_inputs = "[base]"
    out_labels = []

    # Para cada TTS:
    # - Resample a 44.1k y estéreo para igualar con base
    # - Reset PTS
    # - Aplicar delay (para mono, usar 'delays=...:all=1')
    for idx, item in enumerate(tts_items, start=1):
        delay_ms = max(0, int(round(float(item.get("start_time", 0.0)) * 1000)))
        a_parts.append(
            f"[{idx}:a]"
            f"aresample=async=1:first_pts=0,"
            f"aformat=sample_rates=44100:channel_layouts=stereo,"
            f"adelay=delays={delay_ms}:all=1"
            f"[a{idx}]"
        )
        out_labels.append(f"[a{idx}]")

    if out_labels:
        # Construir inputs para amix: 1 (base) + len(tts_items)
        mix_inputs += "".join(out_labels)
        amix_inputs = 1 + len(tts_items)

        # OJO: sin :normalize=1 (tu ffmpeg no lo soporta).
        # Si quieres nivelado automático, añadimos dynaudnorm después de amix.
        amix = (
            f"{mix_inputs}"
            f"amix=inputs={amix_inputs}:duration=longest:dropout_transition=0"
            f",dynaudnorm=f=150:g=15"
            f",acompressor=threshold=-18dB:ratio=2:attack=5:release=50"
            f",alimiter=limit=0.95[aout]"
        )
        filters = [vchain, *a_parts, amix]
    else:
        # Sin TTS: deja el silencio base como salida
        a_parts.append("[base]anull[aout]")
        filters = [vchain, *a_parts]

    return inputs, filters



def ffmpeg_burn_subs_and_mix_audio_no_resize(
    src_video: str,
    ass_path: str,
    tts_items: List[Dict[str, Any]],
    out_video_path: str
) -> None:
    """
    Renderiza el video SIN cambiar resolución:
      - Evita normalize=1 (no existe en FFmpeg 4.2.2).
      - Fuerza todos los audios a 44.1 kHz MONO y usa adelay legacy (adelay=MS).
      - Mezcla con amix y nivelación posterior (dynaudnorm + acompressor + alimiter).
    Requisitos previos:
      - get_ffmpeg_path()
      - get_video_duration_seconds()
    """
    ffmpeg = get_ffmpeg_path()
    video_duration = get_video_duration_seconds(src_video)

    # Entradas (0 = video, 1..N = TTS)
    inputs = ["-i", src_video]
    for item in tts_items:
        inputs += ["-i", item["audio_path"]]

    # Escapar ruta ASS de forma segura
    ass_escaped = ass_path.replace("\\", "/").replace(":", r"\:").replace("'", r"\'")

    # Cadena de video: solo quemar ASS, sin reescalar
    vchain = f"[0:v]ass='{ass_escaped}'[vout]"

    # ===== Audio =====
    # Base de silencio FINITA: 44.1 kHz MONO, recortada a la duración real del video
    dur = max(0.01, float(video_duration))
    a_parts = [
        "anullsrc=channel_layout=mono:sample_rate=44100[base0]",
        f"[base0]atrim=0:{dur:.3f},asetpts=N/SR/TB[base]"
    ]

    mix_inputs = "[base]"
    out_labels = []

    # Para cada TTS:
    # - Convertir a 44.1 kHz MONO (compatibilidad con amix)
    # - Reiniciar PTS (first_pts=0)
    # - Aplicar delay con sintaxis legacy: adelay=MS (sin :all=1 en FFmpeg 4.2.2)
    for idx, item in enumerate(tts_items, start=1):
        delay_ms = max(0, int(round(float(item.get("start_time", 0.0)) * 1000)))
        a_parts.append(
            f"[{idx}:a]"
            f"aresample=async=1:first_pts=0,"
            f"aformat=sample_rates=44100:channel_layouts=mono,"
            f"adelay={delay_ms}"
            f"[a{idx}]"
        )
        out_labels.append(f"[a{idx}]")

    # Mezcla y nivelado
    if out_labels:
        mix_inputs += "".join(out_labels)
        # Sin normalize=1 en amix; nivelamos después
        amix = (
            f"{mix_inputs}"
            f"amix=inputs={1+len(tts_items)}:duration=longest:dropout_transition=0,"
            f"dynaudnorm=f=150:g=15,"
            f"acompressor=threshold=-18dB:ratio=2:attack=5:release=50,"
            f"alimiter=limit=0.95[aout]"
        )
        filters = [vchain, *a_parts, amix]
    else:
        # Sin TTS: deja la base de silencio
        a_parts.append("[base]anull[aout]")
        filters = [vchain, *a_parts]

    filter_complex = ";".join(filters)

    # Comando FFmpeg final
    cmd = [
        ffmpeg, "-y",
        *inputs,
        "-filter_complex", filter_complex,
        "-map", "[vout]",
        "-map", "[aout]",
        "-c:v", "libx264", "-preset", "slow", "-crf", "26",
        "-profile:v", "high", "-level", "4.0", "-pix_fmt", "yuv420p",
        "-maxrate", "2M", "-bufsize", "4M",
        "-c:a", "aac", "-ac", "1", "-b:a", "96k",
        "-movflags", "+faststart",
        "-shortest",
        out_video_path
    ]

    print("FFmpeg CMD (no resize):", " ".join(cmd))
    run = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if run.returncode != 0:
        print(run.stdout)
        print(run.stderr)
        raise RuntimeError(f"FFmpeg falló: {run.stderr}")


def cleanup_tts_files(tts_items: List[Dict[str, Any]]) -> None:
    """
    Limpia archivos MP3 temporales de TTS para ahorrar espacio.
    Llamar después de generar el video final.
    """
    for item in tts_items:
        try:
            audio_path = item.get("audio_path")
            if audio_path and os.path.exists(audio_path):
                os.remove(audio_path)
        except Exception:
            pass  # Ignorar errores de limpieza
