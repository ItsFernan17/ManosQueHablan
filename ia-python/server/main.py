import os
import sys
import shutil
import uuid
import urllib.parse
import time
from io import BytesIO
from flask import Flask, request, jsonify, send_file
from werkzeug.utils import secure_filename

# Agregar el directorio actual al path para importar scripts
sys.path.append(os.path.abspath(os.path.dirname(__file__)))

# IMPORT CORREGIDO - usar función FFmpeg mejorada
try:
    # Importar función FFmpeg que no estira videos
    from scripts.procesar_video import procesar_video_vertical_estandar_ffmpeg as procesar_video_con_voz
    print("✅ Importado procesar_video_vertical_estandar_ffmpeg (FFmpeg directo)")
    PROCESAMIENTO_DISPONIBLE = True
except ImportError as e:
    print(f"❌ Error importando funciones de procesamiento: {e}")
    print("📁 Archivos en directorio actual:")
    try:
        current_files = os.listdir('.')
        for f in current_files:
            if f.endswith('.py'):
                print(f"  📄 {f}")
        
        if os.path.exists('scripts'):
            print("📁 Archivos en scripts/:")
            scripts_files = os.listdir('scripts')
            for f in scripts_files:
                if f.endswith('.py'):
                    print(f"  📄 scripts/{f}")
    except Exception as dir_error:
        print(f"Error listando archivos: {dir_error}")
    
    # Crear función dummy para evitar crash
    def procesar_video_con_voz(video_path, carpeta="."):
        print("❌ Función de procesamiento no disponible")
        return None
    PROCESAMIENTO_DISPONIBLE = False

app = Flask(__name__)
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
STATIC_DIR = os.path.join(BASE_DIR, 'static')
os.makedirs(STATIC_DIR, exist_ok=True)

# Diccionario temporal para rastrear descargas
descargas_pendientes = {}

def verificar_estructura_resultado(resultado):
    """Verifica que el resultado tenga la estructura correcta"""
    if not resultado:
        return False, "Resultado es None"
    
    if not isinstance(resultado, (list, tuple)):
        return False, f"Resultado no es lista/tupla: {type(resultado)}"
    
    # La función puede devolver 3 elementos (video, transcript, audio) o solo 1 (video)
    if len(resultado) == 1:
        # Solo video
        output_video = resultado[0]
        if not os.path.exists(output_video):
            return False, f"Video no existe: {output_video}"
        return True, "OK - solo video"
    elif len(resultado) == 3:
        # Video, transcripción y audio
        output_video, texto_final, audio_final = resultado
        
        # Verificar que los archivos existan (algunos pueden ser None)
        archivos_faltantes = []
        if not os.path.exists(output_video):
            archivos_faltantes.append(f"Video: {output_video}")
        if texto_final and not os.path.exists(texto_final):
            archivos_faltantes.append(f"Texto: {texto_final}")
        if audio_final and not os.path.exists(audio_final):
            archivos_faltantes.append(f"Audio: {audio_final}")
        
        if archivos_faltantes:
            return False, f"Archivos faltantes: {', '.join(archivos_faltantes)}"
        
        return True, "OK - completo"
    else:
        return False, f"Resultado tiene {len(resultado)} elementos, esperado 1 o 3"

def limpiar_sesiones_antiguas():
    """Limpia sesiones antiguas para evitar acumulación"""
    try:
        # Usar la función de limpieza integrada si está disponible
        if PROCESAMIENTO_DISPONIBLE:
            try:
                from scripts.procesar_video import limpiar_carpeta_static
                limpiar_carpeta_static(STATIC_DIR)
                print("✅ Limpieza automática completada")
                return
            except ImportError:
                print("⚠️ Función de limpieza automática no disponible, usando método manual")
        
        # Método manual de respaldo
        if not os.path.exists(STATIC_DIR):
            return
        
        current_time = time.time()
        
        for item in os.listdir(STATIC_DIR):
            item_path = os.path.join(STATIC_DIR, item)
            if os.path.isdir(item_path) and item.startswith('sesion_'):
                # Eliminar carpetas más antiguas de 1 hora
                if current_time - os.path.getctime(item_path) > 3600:
                    print(f"🧹 Eliminando sesión antigua: {item}")
                    shutil.rmtree(item_path, ignore_errors=True)
                    if item in descargas_pendientes:
                        del descargas_pendientes[item]
    except Exception as e:
        print(f"⚠️ Error limpiando sesiones: {e}")

@app.route('/upload_video', methods=['POST'])
def upload_video():
    """Endpoint para subir y procesar videos"""
    
    # Verificar que el procesamiento esté disponible
    if not PROCESAMIENTO_DISPONIBLE:
        return jsonify({
            "success": False,
            "error": "Función de procesamiento no disponible. Revisar imports en el servidor."
        }), 500
    
    # Limpiar sesiones antiguas
    limpiar_sesiones_antiguas()
    
    if 'video' not in request.files:
        return jsonify({"error": "No se proporcionó un archivo de video"}), 400

    video_file = request.files['video']
    if video_file.filename == '':
        return jsonify({"error": "Nombre de archivo vacío"}), 400

    # Crear sesión única
    session_id = f"sesion_{uuid.uuid4().hex}"
    session_path = os.path.join(STATIC_DIR, session_id)
    os.makedirs(session_path, exist_ok=True)

    try:
        # Guardar video subido con nombre temporal único
        import tempfile
        temp_filename = f"temp_video{int(time.time() * 1000000)}.mp4"
        video_path = os.path.join(session_path, temp_filename)
        video_file.save(video_path)
        print(f"📥 Video guardado en: {video_path}")
        
        # Verificar que el archivo se guardó correctamente
        if not os.path.exists(video_path):
            raise ValueError(f"Error guardando video en {video_path}")
        
        file_size = os.path.getsize(video_path)
        print(f"📊 Tamaño del archivo: {file_size} bytes")
        
        if file_size == 0:
            raise ValueError("El archivo guardado está vacío")

        # Procesar el video
        print(f"🔄 Iniciando procesamiento del video...")
        resultado = procesar_video_con_voz(video_path, carpeta=session_path)
        print(f"📦 Resultado del procesamiento: {resultado}")

        # Verificar si no se detectaron señas
        if resultado is None:
            # Limpiar la sesión ya que no hay archivos que descargar
            if os.path.exists(session_path):
                shutil.rmtree(session_path, ignore_errors=True)
            
            return jsonify({
                "success": False,
                "error": "No se detectaron señas en el video",
                "message": "No se generaron archivos de salida porque no se detectaron señas de lenguaje de señas en el video.",
                "info": {
                    "video_original": video_file.filename,
                    "video_size": file_size,
                    "threshold_used": "0.8 (alta precisión)"
                }
            }), 422  # 422 Unprocessable Entity

        # Verificar estructura del resultado
        es_valido, mensaje = verificar_estructura_resultado(resultado)
        if not es_valido:
            raise ValueError(f"Error en resultado de procesamiento: {mensaje}")

        # Manejar diferentes tipos de resultado
        if isinstance(resultado, (list, tuple)) and len(resultado) == 3:
            output_video, texto_final, audio_final = resultado
            
            # Verificar tamaños de archivos generados
            print(f"📊 Archivos generados:")
            print(f"  📹 Video: {os.path.getsize(output_video)} bytes")
            if texto_final and os.path.exists(texto_final):
                print(f"  📄 Texto: {os.path.getsize(texto_final)} bytes")
            if audio_final and os.path.exists(audio_final):
                print(f"  🔊 Audio: {os.path.getsize(audio_final)} bytes")

            # Registrar sesión y archivos pendientes
            descargas_pendientes[session_id] = {
                "video": output_video,
                "audio": audio_final if audio_final and os.path.exists(audio_final) else None,
                "texto": texto_final if texto_final and os.path.exists(texto_final) else None,
                "descargados": {"video": False, "audio": False, "texto": False},
                "timestamp": time.time()
            }

            # Crear URLs de descarga
            response = {
                "success": True,
                "session_id": session_id,
                "video_url": f"/descargar?path={urllib.parse.quote(output_video.replace(os.sep, '/'))}",
                "info": {
                    "video_original": video_file.filename,
                    "video_size": file_size,
                    "formato_salida": "720x1280 (vertical)",
                    "archivos_generados": 1
                }
            }
            
            # Agregar URLs de audio y texto si existen
            if audio_final and os.path.exists(audio_final):
                response["audio_url"] = f"/descargar?path={urllib.parse.quote(audio_final.replace(os.sep, '/'))}"
                response["info"]["archivos_generados"] += 1
                
            if texto_final and os.path.exists(texto_final):
                response["texto_url"] = f"/descargar?path={urllib.parse.quote(texto_final.replace(os.sep, '/'))}"
                response["info"]["archivos_generados"] += 1

        elif isinstance(resultado, (list, tuple)) and len(resultado) == 1:
            # Solo video
            output_video = resultado[0]
            
            print(f"📊 Archivo generado:")
            print(f"  📹 Video: {os.path.getsize(output_video)} bytes")

            descargas_pendientes[session_id] = {
                "video": output_video,
                "audio": None,
                "texto": None,
                "descargados": {"video": False},
                "timestamp": time.time()
            }

            response = {
                "success": True,
                "session_id": session_id,
                "video_url": f"/descargar?path={urllib.parse.quote(output_video.replace(os.sep, '/'))}",
                "info": {
                    "video_original": video_file.filename,
                    "video_size": file_size,
                    "formato_salida": "720x1280 (vertical)",
                    "archivos_generados": 1
                }
            }
        else:
            # Resultado directo (string con ruta del video)
            output_video = str(resultado)
            
            if not os.path.exists(output_video):
                raise ValueError(f"Video procesado no existe: {output_video}")
            
            print(f"📊 Archivo generado:")
            print(f"  📹 Video: {os.path.getsize(output_video)} bytes")

            descargas_pendientes[session_id] = {
                "video": output_video,
                "audio": None,
                "texto": None,
                "descargados": {"video": False},
                "timestamp": time.time()
            }

            response = {
                "success": True,
                "session_id": session_id,
                "video_url": f"/descargar?path={urllib.parse.quote(output_video.replace(os.sep, '/'))}",
                "info": {
                    "video_original": video_file.filename,
                    "video_size": file_size,
                    "formato_salida": "720x1280 (vertical)",
                    "archivos_generados": 1
                }
            }

        print(f"✅ Procesamiento completado exitosamente")
        return jsonify(response)

    except Exception as e:
        import traceback
        error_traceback = traceback.format_exc()
        print(f"❌ Error durante procesamiento:")
        print(error_traceback)
        
        # Limpiar en caso de error
        if os.path.exists(session_path):
            shutil.rmtree(session_path, ignore_errors=True)
        
        if session_id in descargas_pendientes:
            del descargas_pendientes[session_id]
        
        return jsonify({
            "success": False,
            "error": f"Error procesando video: {str(e)}",
            "details": error_traceback if app.debug else None
        }), 500

@app.route('/descargar')
def descargar_archivo():
    """Endpoint para descargar archivos procesados"""
    path = request.args.get("path")
    if not path:
        return jsonify({"error": "Ruta no proporcionada"}), 400

    # Decodificar y normalizar la ruta
    path = urllib.parse.unquote(path).replace("\\", "/")
    
    # Convertir a ruta absoluta si es necesario
    if not os.path.isabs(path):
        path = os.path.join(BASE_DIR, path)

    print(f"📤 Solicitud de descarga: {path}")

    if not os.path.exists(path):
        print(f"📛 Archivo no encontrado: {path}")
        return jsonify({"error": "Archivo no encontrado"}), 404

    try:
        # Obtener información de la sesión
        carpeta = os.path.dirname(path)
        session_id = os.path.basename(carpeta)

        # Detectar tipo de archivo descargado
        archivo_tipo = "desconocido"
        if "_VERTICAL.mp4" in path or "_FINAL.mp4" in path:
            archivo_tipo = "video"
        elif "_transcripcion.txt" in path:
            archivo_tipo = "texto"
        elif "_audio_completo.mp3" in path:
            archivo_tipo = "audio"

        # Marcar como descargado
        if session_id in descargas_pendientes and archivo_tipo in descargas_pendientes[session_id]["descargados"]:
            descargas_pendientes[session_id]["descargados"][archivo_tipo] = True
            print(f"📤 Archivo descargado: {archivo_tipo} de {session_id}")

        # Leer el archivo en memoria para envío
        with open(path, "rb") as f:
            file_data = BytesIO(f.read())

        # Verificar si todos los archivos ya fueron descargados
        if (
            session_id in descargas_pendientes and
            all(descargas_pendientes[session_id]["descargados"].values())
        ):
            print(f"🧹 Todos los archivos descargados, eliminando sesión: {session_id}")
            try:
                shutil.rmtree(carpeta, ignore_errors=True)
                del descargas_pendientes[session_id]
            except Exception as cleanup_error:
                print(f"⚠️ Error limpiando sesión: {cleanup_error}")

        # Determinar MIME type
        mime_type = "application/octet-stream"
        if path.endswith(".mp4"):
            mime_type = "video/mp4"
        elif path.endswith(".mp3"):
            mime_type = "audio/mpeg"
        elif path.endswith(".txt"):
            mime_type = "text/plain"

        file_data.seek(0)
        return send_file(
            file_data,
            as_attachment=True,
            download_name=os.path.basename(path),
            mimetype=mime_type
        )

    except Exception as e:
        import traceback
        error_traceback = traceback.format_exc()
        print(f"❌ Error enviando archivo:")
        print(error_traceback)
        return jsonify({
            "error": f"No se pudo enviar archivo: {str(e)}",
            "details": error_traceback if app.debug else None
        }), 500

@app.route('/status')
def status():
    """Endpoint para verificar el estado del servidor"""
    return jsonify({
        "status": "OK",
        "message": "Servidor de procesamiento de videos funcionando",
        "sesiones_activas": len(descargas_pendientes),
        "directorio_static": STATIC_DIR,
        "procesamiento_disponible": PROCESAMIENTO_DISPONIBLE,
        "funcion_procesamiento": "procesar_video_con_voz" if PROCESAMIENTO_DISPONIBLE else "No disponible"
    })

@app.route('/debug')
def debug_info():
    """Endpoint para información de debug"""
    try:
        archivos_python = []
        try:
            for f in os.listdir('.'):
                if f.endswith('.py'):
                    archivos_python.append(f)
            
            scripts_dir = os.path.join('.', 'scripts')
            if os.path.exists(scripts_dir):
                for f in os.listdir(scripts_dir):
                    if f.endswith('.py'):
                        archivos_python.append(f"scripts/{f}")
        except:
            pass
        
        return jsonify({
            "procesamiento_disponible": PROCESAMIENTO_DISPONIBLE,
            "archivos_python": archivos_python,
            "directorio_actual": os.getcwd(),
            "python_path": sys.path[:5],  # Primeros 5 elementos
            "sesiones_activas": len(descargas_pendientes)
        })
    except Exception as e:
        return jsonify({"error": str(e)})

@app.route('/cleanup', methods=['POST'])
def cleanup_manual():
    """Endpoint para limpiar manualmente sesiones antiguas"""
    try:
        limpiar_sesiones_antiguas()
        return jsonify({
            "success": True,
            "message": "Limpieza completada",
            "sesiones_restantes": len(descargas_pendientes)
        })
    except Exception as e:
        return jsonify({
            "success": False,
            "error": str(e)
        }), 500

if __name__ == '__main__':
    print("🚀 Iniciando servidor de procesamiento de videos...")
    print(f"📁 Directorio base: {BASE_DIR}")
    print(f"📁 Directorio static: {STATIC_DIR}")
    print(f"🔧 Procesamiento disponible: {'✅ Sí' if PROCESAMIENTO_DISPONIBLE else '❌ No'}")
    
    # Mostrar archivos Python disponibles para debug
    try:
        print("📄 Archivos Python encontrados:")
        for f in os.listdir('.'):
            if f.endswith('.py'):
                print(f"  - {f}")
        
        scripts_dir = 'scripts'
        if os.path.exists(scripts_dir):
            print(f"📂 Archivos en {scripts_dir}/:")
            for f in os.listdir(scripts_dir):
                if f.endswith('.py'):
                    print(f"  - {f}")
    except:
        pass
    
    app.run(host='0.0.0.0', port=5000, debug=True)