import os
import cv2
import numpy as np
import subprocess
import json
import warnings
from gtts import gTTS
from moviepy.editor import VideoFileClip, AudioFileClip, CompositeAudioClip
from .verificar import evaluate_model_with_frames

warnings.filterwarnings("default")

# Configuración de calidad
BITRATE = "10000k"
PRESET = "slow"

def generar_audio(texto, ruta_output):
    if texto.strip() == "":
        return None
    try:
        print(f"🎤 Generando audio para: '{texto}'")
        tts = gTTS(text=texto, lang='es')
        tts.save(ruta_output)
        return ruta_output
    except Exception as e:
        print(f"❌ Error al generar audio para '{texto}': {e}")
        return None


def aplicar_filtros(frame):
    hsv = cv2.cvtColor(frame, cv2.COLOR_BGR2HSV)
    v = hsv[:, :, 2]
    brillo = np.mean(v)

    if brillo < 50 or brillo > 200:
        lab = cv2.cvtColor(frame, cv2.COLOR_BGR2LAB)
        l, a, b = cv2.split(lab)
        clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
        cl = clahe.apply(l)
        limg = cv2.merge((cl, a, b))
        return cv2.cvtColor(limg, cv2.COLOR_LAB2BGR)
    return frame


def dibujar_texto(frame, texto):
    texto = texto.capitalize()
    h, w = frame.shape[:2]
    alto_banda = int(h * 0.06)  # Más delgado

    overlay = frame.copy()
    cv2.rectangle(overlay, (0, h - alto_banda), (w, h), (0, 0, 0), -1)
    alpha = 0.7
    frame = cv2.addWeighted(overlay, alpha, frame, 1 - alpha, 0)

    font = cv2.FONT_HERSHEY_SIMPLEX
    escala = max(0.8, min(w / 700.0, 1.5))  # Texto más sutil y adaptable
    grosor = 2
    size = cv2.getTextSize(texto, font, escala, grosor)[0]
    x = (w - size[0]) // 2
    y = h - int(alto_banda / 2) + int(size[1] / 2)
    cv2.putText(frame, texto, (x, y), font, escala, (255, 255, 255), grosor, cv2.LINE_AA)
    return frame


def obtener_rotacion_video(ruta_video):
    try:
        cmd = [
            "ffprobe", "-v", "error", "-select_streams", "v:0",
            "-show_entries", "stream_tags=rotate",
            "-of", "json", ruta_video
        ]
        salida = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        info = json.loads(salida.stdout)
        rotacion = int(info["streams"][0]["tags"]["rotate"])
        print(f"🔄 Rotación detectada: {rotacion}°")
        return rotacion
    except:
        print("🔄 No se detectó rotación (asumiendo 0°)")
        return 0


def procesar_video_con_voz(input_path, carpeta=".", duracion_texto=1.8):
    print(f"🚀 Procesando video: {input_path}")

    output_final = input_path.replace(".mp4", "_FINAL.mp4")
    transcript_path = input_path.replace(".mp4", "_transcripcion.txt")
    audio_completo_path = input_path.replace(".mp4", "_audio_completo.mp3")

    print("🔍 Evaluando modelo...")
    detecciones = evaluate_model_with_frames(input_path, threshold=0.8)
    print(f"📊 Detecciones encontradas: {len(detecciones)}")
    print("📝 Detecciones:", detecciones)

    cap = cv2.VideoCapture(input_path)
    if not cap.isOpened():
        print("❌ Error al abrir el video")
        return

    fps = cap.get(cv2.CAP_PROP_FPS)
    original_w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    original_h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    resolucion_original = (original_w, original_h)

    rotacion = obtener_rotacion_video(input_path)

    # Calcular resolución corregida si hay rotación
    if rotacion in [90, 270]:
        final_w, final_h = original_h, original_w
    else:
        final_w, final_h = original_w, original_h

    print(f"🎞️ FPS: {fps}")
    print(f"📐 Resolución original: {original_w}x{original_h}")
    print(f"📐 Resolución corregida: {final_w}x{final_h}")

    fourcc = cv2.VideoWriter_fourcc(*"mp4v")
    temp_video = os.path.join(carpeta, "video_temp_sin_audio.mp4")
    writer = cv2.VideoWriter(temp_video, fourcc, fps, (final_w, final_h))

    if not writer.isOpened():
        print("❌ Error: no se pudo abrir VideoWriter con resolución corregida")
        return

    texto_activo = None
    frames_restantes = 0
    detecciones_dict = dict(detecciones)
    index = 0

    print("🔧 Procesando frames del video...")

    while True:
        ret, frame = cap.read()
        if not ret:
            break

        # Rotar según metadata
        if rotacion == 90:
            frame = cv2.rotate(frame, cv2.ROTATE_90_CLOCKWISE)
        elif rotacion == 180:
            frame = cv2.rotate(frame, cv2.ROTATE_180)
        elif rotacion == 270:
            frame = cv2.rotate(frame, cv2.ROTATE_90_COUNTERCLOCKWISE)

        frame = aplicar_filtros(frame)

        if index in detecciones_dict:
            texto_activo = detecciones_dict[index]
            frames_restantes = int(duracion_texto * fps)
            print(f"🖼️ Frame {index}: detectado '{texto_activo}'")

        if texto_activo and frames_restantes > 0:
            frame = dibujar_texto(frame, texto_activo)
            frames_restantes -= 1

        writer.write(frame)
        index += 1

    cap.release()
    writer.release()

    if not os.path.exists(temp_video):
        print("❌ Error: video temporal no se generó")
        return

    print(f"✅ Video temporal generado: {temp_video}")
    print("🎧 Generando clips de audio por palabra...")

    try:
        video_clip = VideoFileClip(temp_video)
    except Exception as e:
        print(f"❌ Error al abrir clip temporal: {e}")
        return

    audio_clips = []

    for frame_idx, palabra in detecciones:
        start_time = frame_idx / fps
        audio_path = os.path.join(carpeta, f"tmp_{frame_idx}.mp3")
        if generar_audio(palabra, audio_path) and os.path.exists(audio_path):
            try:
                clip = AudioFileClip(audio_path).set_start(start_time)
                audio_clips.append(clip)
            except Exception as e:
                print(f"⚠️ No se pudo usar el audio {audio_path}: {e}")

    if audio_clips:
        audio_final = CompositeAudioClip(audio_clips)
        video_clip = video_clip.set_audio(audio_final)

    print("💾 Exportando video final con alta calidad...")
    try:
        video_clip.write_videofile(
            output_final,
            codec="libx264",
            audio_codec="aac",
            bitrate=BITRATE,
            preset=PRESET
        )
    except Exception as e:
        print(f"❌ Error al escribir el video final: {e}")
        return

    # Guardar transcripción
    transcripcion = ". ".join([p.capitalize() for _, p in sorted(detecciones, key=lambda x: x[0])])
    print("📝 Guardando transcripción...")
    try:
        with open(transcript_path, "w", encoding="utf-8") as f:
            f.write(transcripcion)
    except Exception as e:
        print(f"❌ Error al guardar la transcripción: {e}")

    # Audio completo continuo
    if transcripcion.strip():
        print("🔊 Generando audio completo continuo...")
        generar_audio(transcripcion, audio_completo_path)

    # Limpieza
    print("🧹 Eliminando temporales...")
    for f in [temp_video] + [os.path.join(carpeta, f"tmp_{idx}.mp3") for idx, _ in detecciones]:
        try:
            os.remove(f)
        except Exception as e:
            print(f"⚠️ No se pudo eliminar {f}: {e}")

    print("🎉 Proceso finalizado")
    print("📦 Video final:", output_final)
    print("📄 Transcripción:", transcript_path)
    print("🔊 Audio completo:", audio_completo_path)

    return output_final, transcript_path, audio_completo_path
