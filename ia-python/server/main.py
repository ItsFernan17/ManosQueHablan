import os
import sys
import shutil
import uuid
import urllib.parse
from io import BytesIO
from flask import Flask, request, jsonify, send_file
from werkzeug.utils import secure_filename
from scripts.procesar_video import procesar_video_con_voz

sys.path.append(os.path.abspath(os.path.dirname(__file__)))

app = Flask(__name__)
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
STATIC_DIR = os.path.join(BASE_DIR, 'static')
os.makedirs(STATIC_DIR, exist_ok=True)

# Diccionario temporal para rastrear descargas
descargas_pendientes = {}

@app.route('/upload_video', methods=['POST'])
def upload_video():
    if 'video' not in request.files:
        return jsonify({"error": "No se proporcionó un archivo de video"}), 400

    video_file = request.files['video']
    if video_file.filename == '':
        return jsonify({"error": "Nombre de archivo vacío"}), 400

    session_id = f"sesion_{uuid.uuid4().hex}"
    session_path = os.path.join(STATIC_DIR, session_id)
    os.makedirs(session_path, exist_ok=True)

    try:
        filename = secure_filename(video_file.filename)
        video_path = os.path.join(session_path, filename)
        video_file.save(video_path)
        print(f"📥 Video guardado en: {video_path}")

        resultado = procesar_video_con_voz(video_path, carpeta=session_path)
        print(f"📦 Resultado completo de procesamiento: {resultado}")

        if not resultado or len(resultado) != 3:
            raise ValueError("⚠️ Error: procesar_video_con_voz no retornó los 3 archivos esperados")

        output_video, texto_final, audio_final = resultado

        # Registrar sesión y archivos pendientes
        descargas_pendientes[session_id] = {
            "video": output_video,
            "audio": audio_final,
            "texto": texto_final,
            "descargados": {"video": False, "audio": False, "texto": False}
        }

        response = {
            "video_url": f"/descargar?path={urllib.parse.quote(output_video.replace(os.sep, '/'))}",
            "audio_url": f"/descargar?path={urllib.parse.quote(audio_final.replace(os.sep, '/'))}",
            "texto_url": f"/descargar?path={urllib.parse.quote(texto_final.replace(os.sep, '/'))}"
        }

        return jsonify(response)

    except Exception as e:
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500

@app.route('/descargar')
def descargar_archivo():
    path = request.args.get("path")
    if not path:
        return jsonify({"error": "Ruta no proporcionada"}), 400

    path = urllib.parse.unquote(path).replace("\\", "/")

    if not os.path.exists(path):
        print(f"📛 Archivo no encontrado: {path}")
        return jsonify({"error": "Archivo no encontrado"}), 404

    try:
        carpeta = os.path.dirname(path)
        session_id = os.path.basename(carpeta)

        # Detectar tipo de archivo descargado
        archivo_tipo = "desconocido"
        if path.endswith("_FINAL.mp4"):
            archivo_tipo = "video"
        elif path.endswith("_transcripcion.txt"):
            archivo_tipo = "texto"
        elif path.endswith("_audio_completo.mp3"):
            archivo_tipo = "audio"

        if session_id in descargas_pendientes and archivo_tipo in descargas_pendientes[session_id]["descargados"]:
            descargas_pendientes[session_id]["descargados"][archivo_tipo] = True
            print(f"📤 Archivo descargado: {archivo_tipo} de {session_id}")

        # Leer el archivo en memoria
        with open(path, "rb") as f:
            file_data = BytesIO(f.read())

        # Verificar si todos los archivos ya fueron descargados
        if (
            session_id in descargas_pendientes and
            all(descargas_pendientes[session_id]["descargados"].values())
        ):
            print(f"🧹 Eliminando carpeta completa de sesión: {carpeta}")
            shutil.rmtree(carpeta, ignore_errors=True)
            del descargas_pendientes[session_id]

        file_data.seek(0)
        return send_file(
            file_data,
            as_attachment=True,
            download_name=os.path.basename(path),
            mimetype="application/octet-stream"
        )

    except Exception as e:
        import traceback
        traceback.print_exc()
        return jsonify({"error": f"No se pudo enviar archivo: {e}"}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
