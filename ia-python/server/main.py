# main.py (versión compacta)
import os, sys, uuid, time, shutil, urllib.parse
from io import BytesIO
from flask import Flask, request, jsonify, send_file

# Rutas base
BASE_DIR  = os.path.dirname(os.path.abspath(__file__))
STATIC_DIR = os.path.join(BASE_DIR, 'static')
os.makedirs(STATIC_DIR, exist_ok=True)

# Import de procesamiento (FFmpeg directo)
sys.path.append(BASE_DIR)
try:
    from scripts.procesar_video import procesar_video_vertical_estandar_ffmpeg as procesar_video_con_voz
    PROCESAMIENTO_DISPONIBLE = True
    print("✅ procesar_video_con_voz listo")
except Exception as e:
    print(f"⚠️ procesamiento no disponible: {e}")
    PROCESAMIENTO_DISPONIBLE = False
    def procesar_video_con_voz(*_, **__):
        return None

# Verificador (ahora es evaluate)
try:
    from scripts.evaluate import evaluate_model_with_frames as verificar_video_server
    VERIFICADOR_DISPONIBLE = True
    print("✅ evaluate_model_with_frames listo como verificador")
except Exception as e:
    print(f"⚠️ verificador (evaluate) no disponible: {e}")
    VERIFICADOR_DISPONIBLE = False
    def verificar_video_server(*_, **__):
        return []

THRESHOLD_DEFAULT = 0.8
app = Flask(__name__)

# Seguimiento de descargas para limpiar sesión al final
descargas_pendientes = {}

# ----------------- Utilidades compactas -----------------
def _threshold(req, default=THRESHOLD_DEFAULT):
    v = request.args.get("threshold") or request.form.get("threshold")
    try: x = float(v) if v is not None else float(default)
    except: x = float(default)
    return max(0.0, min(1.0, x))

def _json_error(msg, code=400, **extra):
    payload = {"success": False, "error": msg}
    if extra: payload["info"] = extra
    return jsonify(payload), code

def _url(path):  # URL de descarga
    return f"/descargar?path={urllib.parse.quote(path.replace(os.sep, '/'))}"

def _save_upload(video_file, session_path):
    name = f"temp_video{int(time.time()*1_000_000)}.mp4"
    video_path = os.path.join(session_path, name)
    video_file.save(video_path)
    if not os.path.exists(video_path) or os.path.getsize(video_path) == 0:
        raise ValueError("Falló al guardar el video")
    return video_path

def _verificar_resultado(resultado):
    if not resultado: return False, "Resultado es None"
    if isinstance(resultado, (list, tuple)):
        if len(resultado) == 1:
            return (os.path.exists(resultado[0])), "OK-1" if os.path.exists(resultado[0]) else "Video no existe"
        if len(resultado) == 3:
            v, t, a = resultado
            faltan = []
            if not os.path.exists(v): faltan.append(f"Video: {v}")
            if t and not os.path.exists(t): faltan.append(f"Texto: {t}")
            if a and not os.path.exists(a): faltan.append(f"Audio: {a}")
            return (len(faltan)==0), ("OK-3" if not faltan else ", ".join(faltan))
        return False, f"Longitud inesperada: {len(resultado)}"
    # string con ruta
    p = str(resultado)
    return os.path.exists(p), ("OK-1" if os.path.exists(p) else f"No existe: {p}")

def registrar_descargas(session_id, video_path, audio_path=None, texto_path=None):
    descargas_pendientes[session_id] = {
        "paths": {
            "video": video_path,
            "audio": audio_path if (audio_path and os.path.exists(audio_path)) else None,
            "texto": texto_path if (texto_path and os.path.exists(texto_path)) else None,
        },
        "descargados": {
            # Solo marcamos False para lo que realmente existe; lo que no existe queda True
            "video": False,
            "audio": False if (audio_path and os.path.exists(audio_path)) else True,
            "texto": False if (texto_path and os.path.exists(texto_path)) else True,
        },
        "timestamp": time.time(),
    }
    print(f"🗂️ Tracking sesión {session_id}: {descargas_pendientes[session_id]}")

def _procesar(video_path, session_path, detection_data):
    # El nombre del kwarg en la función es 'detection_data'
    return procesar_video_con_voz(video_path, carpeta=session_path, detection_data=detection_data)

# ----------------- Rutas -----------------
@app.route('/upload_video', methods=['POST'])
def upload_video():
    print("🔍 /upload_video")
    if not PROCESAMIENTO_DISPONIBLE:
        return _json_error("Procesamiento no disponible", 500)

    if 'video' not in request.files:
        return _json_error("No se proporcionó archivo 'video'", 400)
    video_file = request.files['video']
    if not video_file or video_file.filename == '':
        return _json_error("Nombre de archivo vacío", 400)

    # Crear sesión y guardar archivo
    session_id = f"sesion_{uuid.uuid4().hex}"
    session_path = os.path.join(STATIC_DIR, session_id)
    os.makedirs(session_path, exist_ok=True)
    try:
        video_path = _save_upload(video_file, session_path)
        size = os.path.getsize(video_path)
        print(f"📥 {os.path.basename(video_path)} ({size} bytes)")

        # ---- Verificación previa (opcional) ----
        thr = _threshold(request)
        print(f"🔧 threshold={thr}")
        verif_ok = False
        detection_result = {}
        if VERIFICADOR_DISPONIBLE:
            try:
                print(f"🔎 Ejecutando evaluate_model_with_frames → {os.path.basename(video_path)} (thr={thr})")
                detection_result = verificar_video_server(video_path, threshold=thr)
                verif_ok = True
                # Formato de detecciones ahora es un diccionario
                detecciones = detection_result.get('detections', [])
                print(f"🧾 Detecciones ({len(detecciones)}): {[ (p, round(c,1)) for _,p,c in detecciones ]}")
            except Exception as e:
                print(f"⚠️ Verificación falló: {e} → continuo sin verificación")
                detection_result = {} # Asegurar que sea un diccionario vacío
                verif_ok = False

        # SOLO devolver 422 cuando verif_ok sea True y no haya detecciones
        if verif_ok and not detection_result.get('detections'):
            shutil.rmtree(session_path, ignore_errors=True)
            return _json_error("No se detectaron señas en el video", 422,
                               video_original=video_file.filename, video_size=size, threshold_used=thr)

        # ---- Procesamiento (video final, audio, texto) ----
        # Pasar el diccionario completo, no solo las detecciones
        resultado = _procesar(video_path, session_path, detection_result)
        print(f"📦 resultado={resultado}")
        ok, msg = _verificar_resultado(resultado)
        if not ok:
            shutil.rmtree(session_path, ignore_errors=True)
            return _json_error(f"Salida inválida: {msg}", 500)

        # Armar respuesta + registrar pendientes
        if isinstance(resultado, (list, tuple)) and len(resultado) >= 3:
            # Caso 3+ salidas: video, texto, audio
            video_out, txt_out, aud_out = resultado[0], resultado[1], resultado[2]
            txt_ok = bool(txt_out and os.path.exists(txt_out))
            aud_ok = bool(aud_out and os.path.exists(aud_out))
            
            # Registrar descargas
            registrar_descargas(session_id, video_out, aud_out if aud_ok else None, txt_out if txt_ok else None)
            
            # Construir respuesta
            resp = {
                "success": True,
                "session_id": session_id,
                "video_url": _url(video_out),
                "info": {
                    "video_original": video_file.filename,
                    "video_size": size,
                    "threshold_used": thr,
                    "formato_salida": "720x1280 (vertical)",
                    "has_audio": aud_ok,
                    "has_text": txt_ok,
                    "tts_mixed": False,  # No disponible en la respuesta
                }
            }
            if aud_ok: resp["audio_url"] = _url(aud_out)
            if txt_ok: resp["texto_url"] = _url(txt_out)
            print("✅ OK")
            return jsonify(resp)
        else:
            # Caso 1 salida o string: solo video
            out_video = resultado[0] if isinstance(resultado, (list, tuple)) else str(resultado)
            registrar_descargas(session_id, out_video, None, None)
            
            return jsonify({
                "success": True,
                "session_id": session_id,
                "video_url": _url(out_video),
                "info": {
                    "video_original": video_file.filename,
                    "video_size": size,
                    "threshold_used": thr,
                    "formato_salida": "720x1280 (vertical)",
                    "has_audio": False,
                    "has_text": False,
                    "tts_mixed": False,
                }
            })

    except Exception as e:
        import traceback
        print(f"❌ Error en /upload_video: {e}")
        print("📋 Stack trace completo:")
        traceback.print_exc()
        shutil.rmtree(session_path, ignore_errors=True)
        return _json_error(f"Error procesando video: {e}", 500)

@app.route('/descargar')
def descargar_archivo():
    path = request.args.get("path")
    if not path:
        return jsonify({"error": "Ruta no proporcionada"}), 400

    path = urllib.parse.unquote(path).replace("\\", "/")
    if not os.path.isabs(path):
        path = os.path.join(BASE_DIR, path)

    print(f"📤 Solicitud de descarga: {path}")

    # Chequeo doble por posibles carreras con limpieza
    if not os.path.exists(path):
        print(f"📛 Archivo no encontrado (pre-open): {path}")
        return jsonify({"error": "Archivo no encontrado"}), 404

    try:
        carpeta = os.path.dirname(path)
        session_id = os.path.basename(carpeta)

        archivo_tipo = "desconocido"
        if "_VERTICAL.mp4" in path or "_FINAL.mp4" in path or (path.endswith(".mp4") and os.path.dirname(path).endswith(session_id)):
            archivo_tipo = "video"
        elif path.endswith("audio_completo.mp3"):
            archivo_tipo = "audio"
        elif path.endswith("transcripcion.txt") or path.endswith("_transcripcion.txt"):
            archivo_tipo = "texto"

        # Abrir con manejo de error por race (TOCTOU)
        try:
            with open(path, "rb") as f:
                file_data = BytesIO(f.read())
        except FileNotFoundError:
            print(f"📛 Archivo no encontrado (open): {path}")
            return jsonify({"error": "Archivo no encontrado"}), 404

        # Marcar descargado y limpiar sesión si ya se bajó todo
        info = descargas_pendientes.get(session_id)
        if info:
            if archivo_tipo in info["descargados"]:
                info["descargados"][archivo_tipo] = True
                print(f"📤 Archivo descargado: {archivo_tipo} de {session_id} → {info['descargados']}")
            else:
                print(f"ℹ️ '{archivo_tipo}' no está trackeado para {session_id}, no se marca")

            # Limpia SOLO cuando todo lo existente esté descargado
            try:
                if all(info["descargados"].values()):
                    print(f"🧹 Todos descargados → eliminando sesión: {session_id}")
                    shutil.rmtree(carpeta, ignore_errors=True)
                    del descargas_pendientes[session_id]
            except Exception as cleanup_error:
                print(f"⚠️ Error limpiando sesión: {cleanup_error}")
        else:
            print(f"⚠️ Sesión {session_id} no encontrada en tracking")

        mime = "application/octet-stream"
        if path.endswith(".mp4"): mime = "video/mp4"
        elif path.endswith(".mp3"): mime = "audio/mpeg"
        elif path.endswith(".txt"): mime = "text/plain"

        file_data.seek(0)
        return send_file(file_data, as_attachment=True, download_name=os.path.basename(path), mimetype=mime)

    except Exception as e:
        import traceback
        print("❌ Error enviando archivo:\n" + traceback.format_exc())
        return jsonify({"error": f"No se pudo enviar archivo: {e}"}), 500

@app.route('/status')
def status():
    return jsonify({
        "status": "OK",
        "procesamiento_disponible": PROCESAMIENTO_DISPONIBLE,
        "verificador_disponible": VERIFICADOR_DISPONIBLE,
        "static_dir": STATIC_DIR,
        "sesiones": len(descargas_pendientes)
    })

if __name__ == "__main__":
    print("🚀 Server ON")
    app.run(host="0.0.0.0", port=5000, debug=True)
