# app/utils_ffmpeg.py
import os
import subprocess
import tempfile
from typing import List, Dict, Any, Tuple

import cv2
from gtts import gTTS

PHONE_W, PHONE_H = 720, 1280  # vertical 9:16


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
    font: str = "Poppins",     # caerá a una fuente del sistema si no existe
    font_size: int = 50,
    margin_v: int = 50,
    subtitle_delay: float = 0.0  # retraso en segundos para mostrar subtítulos después de la seña
) -> None:
    header = f"""[Script Info]
Title: Subs estilo Poppins
ScriptType: v4.00+
WrapStyle: 2
ScaledBorderAndShadow: yes
PlayResX: 720
PlayResY: 1280

[V4+ Styles]
; Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
; PrimaryColour: texto blanco      (&H00FFFFFF, alfa 00 = opaco)
; OutlineColour: caja negra 85%    (&H28 000000 -> ~85% opaco, 15% transparente)
Style: Default,{font},{font_size},&H00FFFFFF,&H000000FF,&H28000000,&H00000000,-1,0,0,0,100,100,0,0,3,18,0,2,30,30,{margin_v},1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
"""
    def _to_ass_time(s: float) -> str:
        h = int(s // 3600); m = int((s % 3600) // 60); sec = s % 60
        return f"{h:d}:{m:02d}:{sec:05.2f}"

    lines = []
    subtitle_times = []  # Para evitar overlaps: [(start, end), ...]
    
    for d in sorted(detections or [], key=lambda x: x.get("start_time", 0.0)):
        label = _clean_label(str(d.get("label", "")))
        if not label: continue
        
        # Calcular delay dinámico basado en duración de la seña
        sign_duration = float(d.get("end_time", 0.0)) - float(d.get("start_time", 0.0))
        
        # Para señas rápidas (< 0.5s): delay mínimo
        # Para señas normales (0.5-2s): delay proporcional pequeño 
        # Para señas largas (>2s): delay fijo pequeño
        if sign_duration < 0.5:
            dynamic_delay = 0.01  # 10ms para señas muy rápidas
        elif sign_duration < 2.0:
            dynamic_delay = sign_duration * 0.02  # 2% de la duración de la seña
        else:
            dynamic_delay = 0.04  # 40ms máximo para señas largas
        
        # Los subtítulos aparecen DESPUÉS de que termine la seña con delay dinámico
        start_subtitle = float(d.get("end_time", 0.0)) + max(subtitle_delay, dynamic_delay)
        end_subtitle = start_subtitle + min(1.2, max(0.8, sign_duration * 0.8))  # Duración proporcional a la seña
        
        # Evitar overlaps con subtítulos anteriores
        for prev_start, prev_end in subtitle_times:
            if start_subtitle < prev_end:
                # Si hay overlap, mover el inicio después del anterior (gap mínimo)
                start_subtitle = prev_end + 0.02  # Gap de solo 20ms
                end_subtitle = start_subtitle + min(1.2, max(0.8, sign_duration * 0.8))
        
        subtitle_times.append((start_subtitle, end_subtitle))
        lines.append(f"Dialogue: 0,{_to_ass_time(start_subtitle)},{_to_ass_time(end_subtitle)},Default,,0,0,0,,{label}")

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
      - Video: escala + pad + ASS
      - Audio: silencio base recortado a duración real + todos los TTS con adelay + amix
    """
    inputs = ["-i", src_video]
    for item in tts_items:
        inputs += ["-i", item["audio_path"]]

    # Escapar ruta ASS para Windows
    ass_escaped = ass_path.replace("\\", "/").replace(":", r"\:").replace("'", r"\'")

    # Cadena de video
    vchain = (
        f"[0:v]"
        f"scale={PHONE_W}:{PHONE_H}:force_original_aspect_ratio=decrease,"
        f"pad={PHONE_W}:{PHONE_H}:(ow-iw)/2:(oh-ih)/2:black,"
        f"ass='{ass_escaped}'[vout]"
    )

    # Silencio base FINITO: recortado a la duración real
    dur = max(0.01, float(video_duration))
    a_parts = [
        "anullsrc=channel_layout=stereo:sample_rate=44100[base0]",
        f"[base0]atrim=0:{dur:.3f},asetpts=N/SR/TB[base]"
    ]
    mix_inputs = "[base]"
    out_labels = []

    for idx, item in enumerate(tts_items, start=1):
        delay_ms = max(0, int(round(float(item.get("start_time", 0.0)) * 1000)))
        a_parts.append(f"[{idx}:a]volume=12dB,adelay={delay_ms}|{delay_ms}[a{idx}]")  # Subido a 12dB para mejor audibilidad
        out_labels.append(f"[a{idx}]")

    if out_labels:
        mix_inputs += "".join(out_labels)
        amix = f"{mix_inputs}amix=inputs={1+len(tts_items)}:duration=longest:dropout_transition=0[aout]"
        filters = [vchain, *a_parts, amix]
    else:
        a_parts.append("[base]anull[aout]")
        filters = [vchain, *a_parts]

    return inputs, filters


def ffmpeg_burn_subs_and_mix_audio(
    src_video: str,
    ass_path: str,
    tts_items: List[Dict[str, Any]],
    out_video_path: str
) -> None:
    """
    Renderiza video vertical con ASS y mezcla TTS. Portátil con imageio-ffmpeg.
    Evita render infinito: silencio base recortado + -shortest.
    """
    ffmpeg = get_ffmpeg_path()
    video_duration = get_video_duration_seconds(src_video)

    inputs, filters = build_ffmpeg_filter_and_inputs(src_video, ass_path, tts_items, video_duration)
    filter_complex = ";".join(filters)

    cmd = [
        ffmpeg, "-y",
        *inputs,
        "-filter_complex", filter_complex,
        "-map", "[vout]",
        "-map", "[aout]",
        "-c:v", "libx264", "-preset", "fast", "-crf", "23",
        "-c:a", "aac", "-b:a", "160k",
        "-pix_fmt", "yuv420p",
        "-shortest",
        out_video_path
    ]
    print("FFmpeg CMD:", " ".join(cmd))
    run = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if run.returncode != 0:
        print(run.stdout)
        print(run.stderr)
        raise RuntimeError(f"FFmpeg falló: {run.stderr}")
