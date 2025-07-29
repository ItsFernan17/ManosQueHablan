import os
import cv2
import numpy as np
from gtts import gTTS
from moviepy.editor import VideoFileClip, AudioFileClip, CompositeAudioClip
from .verificar import evaluate_model_with_frames

def generar_audio(texto, ruta_output):
    if not texto or texto.strip() == "":
        return None
    try:
        tts = gTTS(text=texto, lang='es')
        tts.save(ruta_output)
        return ruta_output
    except:
        return None

def aplicar_filtros(frame):
    # Mejora contraste para análisis (no afecta video final)
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    gray = clahe.apply(gray)
    return cv2.cvtColor(gray, cv2.COLOR_GRAY2BGR)

def dibujar_texto_snapchat(frame, texto):
    h, w = frame.shape[:2]
    alto_banda = 50
    overlay = frame.copy()

    # Banda negra semitransparente
    cv2.rectangle(overlay, (0, h - alto_banda), (w, h), (0, 0, 0), -1)
    alpha = 0.6
    cv2.addWeighted(overlay, alpha, frame, 1 - alpha, 0, frame)

    # Texto blanco centrado
    font = cv2.FONT_HERSHEY_SIMPLEX
    scale = 0.9
    thickness = 2
    size = cv2.getTextSize(texto, font, scale, thickness)[0]
    text_x = (w - size[0]) // 2
    text_y = h - 15
    cv2.putText(frame, texto, (text_x, text_y), font, scale, (255, 255, 255), thickness, cv2.LINE_AA)

def procesar_video_con_voz(input_path, duracion_texto=1.8, carpeta="."):
    detecciones = evaluate_model_with_frames(input_path, threshold=0.8)
    cap = cv2.VideoCapture(input_path)
    fps = cap.get(cv2.CAP_PROP_FPS)
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

    tmp_video = os.path.join(carpeta, "video_temp_sin_audio.mp4")
    out = cv2.VideoWriter(tmp_video, cv2.VideoWriter_fourcc(*"mp4v"), fps, (width, height))

    texto_activo = None
    texto_frames_restantes = 0
    detecciones_dict = {i: palabra for i, palabra in detecciones}
    index = 0

    while True:
        ret, frame = cap.read()
        if not ret:
            break

        frame_para_analisis = aplicar_filtros(frame.copy())

        if index in detecciones_dict:
            texto_activo = detecciones_dict[index]
            texto_frames_restantes = int(duracion_texto * fps)

        if texto_activo and texto_frames_restantes > 0:
            dibujar_texto_snapchat(frame, texto_activo.upper())
            texto_frames_restantes -= 1

        out.write(frame)
        index += 1

    cap.release()
    out.release()

    video_clip = VideoFileClip(tmp_video)
    audio_clips = []

    for frame_idx, palabra in detecciones:
        inicio = frame_idx / fps
        audio_path = os.path.join(carpeta, f"tmp_audio_{frame_idx}.mp3")
        if generar_audio(palabra, audio_path) and os.path.exists(audio_path):
            try:
                clip_audio = AudioFileClip(audio_path)
                if clip_audio.duration > 0.1:
                    audio_clips.append(clip_audio.set_start(inicio))
            except:
                pass

    if audio_clips:
        audio_final = CompositeAudioClip(audio_clips)
        video_clip = video_clip.set_audio(audio_final)

    output_final = input_path.replace(".mp4", "_FINAL.mp4")
    video_clip.write_videofile(
        output_final,
        codec="libx264",
        audio_codec="aac",
        bitrate="5000k"
    )

    # Limpieza
    if os.path.exists(tmp_video):
        os.remove(tmp_video)
    for frame_idx, _ in detecciones:
        audio_path = os.path.join(carpeta, f"tmp_audio_{frame_idx}.mp3")
        if os.path.exists(audio_path):
            try:
                os.remove(audio_path)
            except:
                pass

    # Transcripción y audio completo
    transcripcion = [palabra for _, palabra in sorted(detecciones, key=lambda x: x[0])]
    texto_completo = ". ".join(transcripcion)
    print("📝 Transcripción generada:", texto_completo)

    try:
        transcript_path = input_path.replace(".mp4", "_transcripcion.txt")
        with open(transcript_path, "w", encoding="utf-8") as f:
            f.write(texto_completo)
        print(f"✅ Transcripción guardada en: {transcript_path}")
    except Exception as e:
        print(f"❌ Error al guardar transcripción: {e}")

    try:
        audio_continuo_path = input_path.replace(".mp4", "_audio_completo.mp3")
        if texto_completo.strip():
            result_audio = generar_audio(texto_completo, audio_continuo_path)
            if result_audio and os.path.exists(audio_continuo_path):
                print(f"✅ Audio completo generado en: {audio_continuo_path}")
            else:
                print("⚠️ Audio no generado, aunque texto no estaba vacío.")
        else:
            print("⚠️ No se generó audio: texto completo vacío.")
    except Exception as e:
        print(f"❌ Error al generar audio completo: {e}")

    print(f"📦 Resultado completo de procesamiento: {output_final}")
    return output_final, transcript_path, audio_continuo_path

