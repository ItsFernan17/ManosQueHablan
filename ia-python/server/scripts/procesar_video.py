import os
import sys
import subprocess
import shutil
import glob
import time
import numpy as np
from gtts import gTTS
from moviepy.editor import VideoFileClip, AudioFileClip, CompositeAudioClip, TextClip, CompositeVideoClip, ColorClip
import moviepy.config as mp_config
import imageio
from PIL import Image, ImageDraw, ImageFont # Importar Pillow
try:
    import ffmpeg
    FFMPEG_PYTHON_AVAILABLE = True
except ImportError:
    FFMPEG_PYTHON_AVAILABLE = False
from .evaluate import evaluate_model_with_frames

# DIMENSIONES ESTÁNDAR PARA TELÉFONO VERTICAL
PHONE_WIDTH = 720   # Ancho estándar
PHONE_HEIGHT = 1280 # Alto estándar (proporción 9:16)
PHONE_ASPECT = PHONE_HEIGHT / PHONE_WIDTH  # 1.778

def cerrar_clips_seguro(*clips):
    """Cierra clips de manera segura, manejando errores"""
    for clip in clips:
        if clip is not None:
            try:
                clip.close()
            except Exception as e:
                print(f"⚠️ Error cerrando clip: {e}")

def limpiar_archivos_seguro(*archivos):
    """Limpia archivos de manera segura, con reintentos"""
    for archivo in archivos:
        if archivo and os.path.exists(archivo):
            for intento in range(3):
                try:
                    os.remove(archivo)
                    print(f"✅ Archivo eliminado: {os.path.basename(archivo)}")
                    break
                except PermissionError:
                    if intento < 2:
                        print(f"🔄 Intento {intento + 1}: Esperando liberar archivo...")
                        time.sleep(2 ** intento)  # Espera exponencial: 1s, 2s, 4s
                    else:
                        print(f"❌ No se pudo eliminar después de 3 intentos: {os.path.basename(archivo)}")
                except Exception as e:
                    print(f"❌ Error eliminando {os.path.basename(archivo)}: {e}")
                    break

def configurar_ffmpeg():
    """Configura FFmpeg para MoviePy usando imageio-ffmpeg"""
    try:
        # Usar imageio-ffmpeg en lugar del método deprecado
        import imageio_ffmpeg
        
        # Obtener la ruta del ejecutable de ffmpeg
        ffmpeg_exe = imageio_ffmpeg.get_ffmpeg_exe()
        
        # Configurar MoviePy para usar este ffmpeg
        mp_config.change_settings({"FFMPEG_BINARY": ffmpeg_exe})
        
        print(f"✅ FFmpeg configurado desde imageio-ffmpeg: {ffmpeg_exe}")
        return ffmpeg_exe
    except ImportError:
        print("⚠️ imageio-ffmpeg no está instalado. Instalando...")
        try:
            import subprocess
            subprocess.check_call([sys.executable, "-m", "pip", "install", "imageio-ffmpeg"])
            import imageio_ffmpeg
            ffmpeg_exe = imageio_ffmpeg.get_ffmpeg_exe()
            mp_config.change_settings({"FFMPEG_BINARY": ffmpeg_exe})
            print(f"✅ FFmpeg instalado y configurado: {ffmpeg_exe}")
            return ffmpeg_exe
        except Exception as install_error:
            print(f"❌ Error instalando imageio-ffmpeg: {install_error}")
            return None
    except Exception as e:
        print(f"⚠️ Error configurando FFmpeg desde imageio-ffmpeg: {e}")
        return None

def obtener_ffmpeg_path():
    """Obtiene la ruta del ejecutable FFmpeg disponible"""
    # Intentar configurar desde imageio primero
    ffmpeg_path = configurar_ffmpeg()
    if ffmpeg_path:
        return ffmpeg_path
    
    # Si imageio falla, intentar encontrar ffmpeg en el sistema
    try:
        result = subprocess.run(['ffmpeg', '-version'], capture_output=True, text=True)
        if result.returncode == 0:
            return 'ffmpeg'  # Está en PATH
    except Exception:
        pass
    
    # Intentar rutas comunes en Windows
    rutas_comunes = [
        r"C:\ffmpeg\bin\ffmpeg.exe",
        r"C:\Program Files\ffmpeg\bin\ffmpeg.exe",
        r"C:\Program Files (x86)\ffmpeg\bin\ffmpeg.exe"
    ]
    
    for ruta in rutas_comunes:
        if os.path.exists(ruta):
            return ruta
    
    return None

# Configurar FFmpeg al importar el módulo
print("🔧 Configurando FFmpeg...")
try:
    configurar_ffmpeg()
except Exception as e:
    print(f"⚠️ No se pudo configurar FFmpeg automáticamente: {e}")
    print("🔄 Se intentará detectar FFmpeg manualmente cuando sea necesario")

def generar_audio(texto, ruta_output):
    """Genera audio TTS para el texto"""
    try:
        print(f"🎤 Generando audio para: '{texto}'")
        tts = gTTS(text=texto, lang='es', slow=False)
        tts.save(ruta_output)
        print(f"✅ Audio generado: {ruta_output}")
        return True
    except Exception as e:
        print(f"❌ Error generando audio: {e}")
        return False

def limpiar_carpeta_static(carpeta_static="static"):
    """
    Limpia automáticamente la carpeta static para liberar espacio
    Mantiene solo los archivos de la sesión más reciente
    """
    try:
        if not os.path.exists(carpeta_static):
            print(f"📁 Carpeta {carpeta_static} no existe, creándola...")
            os.makedirs(carpeta_static)
            return
        
        print(f"🧹 Limpiando carpeta {carpeta_static}...")
        
        # Obtener todas las subcarpetas de sesión
        sesion_folders = []
        for item in os.listdir(carpeta_static):
            item_path = os.path.join(carpeta_static, item)
            if os.path.isdir(item_path) and item.startswith('sesion_'):
                # Obtener tiempo de modificación
                mod_time = os.path.getmtime(item_path)
                sesion_folders.append((mod_time, item_path))
        
        # Ordenar por tiempo de modificación (más reciente primero)
        sesion_folders.sort(reverse=True)
        
        # Mantener solo la sesión más reciente, eliminar el resto
        folders_deleted = 0
        for i, (mod_time, folder_path) in enumerate(sesion_folders):
            if i > 0:  # Mantener solo la primera (más reciente)
                try:
                    shutil.rmtree(folder_path)
                    folders_deleted += 1
                    print(f"🗑️ Eliminada sesión antigua: {os.path.basename(folder_path)}")
                except Exception as e:
                    print(f"⚠️ Error eliminando {folder_path}: {e}")
        
        # Limpiar archivos sueltos en static
        files_deleted = 0
        for file_path in glob.glob(os.path.join(carpeta_static, "*")):
            if os.path.isfile(file_path):
                try:
                    os.remove(file_path)
                    files_deleted += 1
                    print(f"🗑️ Eliminado archivo suelto: {os.path.basename(file_path)}")
                except Exception as e:
                    print(f"⚠️ Error eliminando {file_path}: {e}")
        
        if folders_deleted > 0 or files_deleted > 0:
            print(f"✅ Limpieza completada: {folders_deleted} carpetas y {files_deleted} archivos eliminados")
        else:
            print(f"✅ Carpeta {carpeta_static} ya está limpia")
            
    except Exception as e:
        print(f"❌ Error en limpieza de {carpeta_static}: {e}")

def calcular_duracion_subtitulo(texto):
    """Calcula duración apropiada del subtítulo basada en la longitud del texto"""
    # Duración base de 1.5 segundos + tiempo de lectura
    duracion_base = 1.5
    # Aproximadamente 200ms por carácter para lectura cómoda
    tiempo_lectura = len(texto) * 0.2
    # Mínimo 2 segundos, máximo 4 segundos
    duracion_total = max(2.0, min(4.0, duracion_base + tiempo_lectura))
    return duracion_total

def crear_subtitulo_minimalista(texto, duracion):
    """Crea subtítulos minimalistas y elegantes usando Pillow para mejor soporte de texto."""
    try:
        print(f"Creando subtítulo minimalista para: '{texto}'")
        
        from moviepy.editor import ImageClip
        
        # Configuración de la fuente (asegúrate de que esta fuente exista en el sistema)
        # Puedes probar con "arial.ttf", "DejaVuSans.ttf", o una ruta absoluta a una fuente.
        try:
            font_path = "C:/Windows/Fonts/arial.ttf" # Ruta común para Arial en Windows
            font_size = 40
            font = ImageFont.truetype(font_path, font_size)
        except IOError:
            print("⚠️ Fuente Arial no encontrada, usando fuente por defecto. Los acentos podrían no mostrarse correctamente.")
            font_size = 30 # Reducir tamaño si la fuente por defecto es más grande
            font = ImageFont.load_default()

        # Calcular dimensiones del texto usando Pillow
        # getbbox() es más preciso para el tamaño real del texto
        bbox = font.getbbox(texto)
        text_width = bbox[2] - bbox[0]
        text_height = bbox[3] - bbox[1]
        
        # Dimensiones del subtítulo con padding
        padding_x = 30
        padding_y = 20
        subtitle_width = text_width + padding_x * 2
        subtitle_height = text_height + padding_y * 2
        
        # Asegurar que el ancho no exceda el del teléfono
        if subtitle_width > PHONE_WIDTH - 40:
            # Si es demasiado ancho, ajustar el tamaño de la fuente o el padding
            # Por simplicidad, aquí solo se ajusta el ancho del cuadro
            subtitle_width = PHONE_WIDTH - 40
            # Considerar ajustar font_size o envolver texto si esto es un problema recurrente
            
        # Crear imagen RGBA con Pillow para transparencia
        img = Image.new("RGBA", (subtitle_width, subtitle_height), (0, 0, 0, 0)) # Fondo transparente
        draw = ImageDraw.Draw(img)
        
        # Fondo negro semi-transparente (elegante)
        # Dibujar un rectángulo para el fondo
        draw.rectangle([(0, 0), (subtitle_width, subtitle_height)], fill=(0, 0, 0, 180)) # Negro con 70% de opacidad
        
        # Posición del texto centrado dentro del cuadro del subtítulo
        # Ajustar la posición para que el texto esté centrado vertical y horizontalmente
        text_x = (subtitle_width - text_width) / 2 - bbox[0]
        text_y = (subtitle_height - text_height) / 2 - bbox[1]
        
        # Dibujar texto blanco
        draw.text((text_x, text_y), texto, font=font, fill=(255, 255, 255, 255))
        
        # Convertir imagen de Pillow a array de NumPy para MoviePy
        img_np = np.array(img)
        
        # Crear clip
        img_clip = ImageClip(img_np, duration=duracion)
        
        # Posición en la parte inferior centrada
        margin_bottom = 80
        img_clip = img_clip.set_position(('center', PHONE_HEIGHT - margin_bottom - subtitle_height))
        
        # La transparencia ya está manejada por el canal alfa de la imagen,
        # pero set_opacity puede ser útil para un control adicional si se desea.
        # img_clip = img_clip.set_opacity(0.9) # Opcional: ajustar opacidad general del clip
        
        print(f"Subtítulo minimalista creado para '{texto}'")
        return img_clip
        
    except Exception as e:
        print(f"Error creando subtítulo minimalista: {e}")
        print("🔄 Usando fallback para subtítulos.")
        return crear_subtitulo_simple_fallback(texto, duracion)

def crear_subtitulo_simple_fallback(texto, duracion):
    """Subtítulo de respaldo minimalista"""
    try:
        from moviepy.editor import ColorClip
        
        # Crear rectángulo negro semi-transparente simple
        text_length = len(texto)
        subtitle_width = max(200, text_length * 12 + 40)  # Ancho dinámico mínimo
        subtitle_width = min(subtitle_width, PHONE_WIDTH - 40)  # Máximo ancho
        subtitle_height = 50
        
        # Clip de fondo negro
        background = ColorClip(size=(subtitle_width, subtitle_height), 
                              color=(0, 0, 0), duration=duracion)
        background = background.set_opacity(0.6)  # Semi-transparente
        background = background.set_position(('center', PHONE_HEIGHT - 100))
        
        print(f"Subtítulo fallback minimalista creado para '{texto}'")
        return background
        
    except Exception as e:
        print(f"Error en fallback: {e}")
        return None

# Alias para mantener compatibilidad
def crear_subtitulo_vertical(texto, duracion):
    """Wrapper para la función de subtítulos minimalistas"""
    return crear_subtitulo_minimalista(texto, duracion)

def detectar_ffmpeg():
    """Detecta si FFmpeg está disponible en el sistema"""
    ffmpeg_path = obtener_ffmpeg_path()
    if ffmpeg_path:
        try:
            # Probar el ejecutable encontrado
            result = subprocess.run([ffmpeg_path, '-version'], capture_output=True, text=True)
            if result.returncode == 0:
                print(f"✅ FFmpeg detectado: {ffmpeg_path}")
                return ffmpeg_path
        except Exception as e:
            print(f"⚠️ Error probando FFmpeg en {ffmpeg_path}: {e}")
    
    print("⚠️ FFmpeg no está disponible")
    return False

def convertir_a_vertical_opencv(input_path, output_path):
    """
    Convierte video usando OpenCV cuando FFmpeg no está disponible
    """
    print("🔧 Convirtiendo con OpenCV (sin FFmpeg)...")
    
    try:
        import cv2
        
        # Abrir video de entrada
        cap = cv2.VideoCapture(input_path)
        if not cap.isOpened():
            print("❌ No se pudo abrir el video")
            return False
        
        # Obtener propiedades del video original
        orig_w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        orig_h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
        fps = cap.get(cv2.CAP_PROP_FPS)
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        
        print(f"📐 Video original: {orig_w}x{orig_h}, FPS: {fps}")
        
        # Calcular nuevas dimensiones SIN estirar
        orig_aspect = orig_w / orig_h
        target_aspect = PHONE_WIDTH / PHONE_HEIGHT
        
        if orig_aspect > target_aspect:
            # Video horizontal - ajustar por altura
            new_h = PHONE_HEIGHT
            new_w = int(new_h * orig_aspect)
            offset_x = (new_w - PHONE_WIDTH) // 2
            offset_y = 0
        else:
            # Video vertical - ajustar por ancho
            new_w = PHONE_WIDTH
            new_h = int(new_w / orig_aspect)
            offset_x = 0
            offset_y = (PHONE_HEIGHT - new_h) // 2
        
        print(f"📐 Redimensionando a: {new_w}x{new_h}")
        print(f"📱 Canvas final: {PHONE_WIDTH}x{PHONE_HEIGHT}")
        
        # Configurar escritor de video
        fourcc = cv2.VideoWriter_fourcc(*'mp4v')
        out = cv2.VideoWriter(output_path, fourcc, fps, (PHONE_WIDTH, PHONE_HEIGHT))
        
        frame_count = 0
        while True:
            ret, frame = cap.read()
            if not ret:
                break
            
            # Redimensionar frame manteniendo proporción
            frame_resized = cv2.resize(frame, (new_w, new_h))
            
            # Crear canvas negro del tamaño objetivo
            canvas = np.zeros((PHONE_HEIGHT, PHONE_WIDTH, 3), dtype=np.uint8)
            
            # Colocar frame redimensionado en el centro del canvas
            if orig_aspect > target_aspect:
                # Video horizontal - centrar verticalmente
                y_start = (PHONE_HEIGHT - new_h) // 2
                y_end = y_start + new_h
                x_start = max(0, offset_x)
                x_end = min(PHONE_WIDTH, offset_x + PHONE_WIDTH)
                
                frame_crop = frame_resized[:, x_start-offset_x:x_end-offset_x] if offset_x > 0 else frame_resized
                canvas[y_start:y_end, :frame_crop.shape[1]] = frame_crop
            else:
                # Video vertical - centrar horizontalmente
                x_start = (PHONE_WIDTH - new_w) // 2
                x_end = x_start + new_w
                y_start = max(0, offset_y)
                y_end = min(PHONE_HEIGHT, offset_y + PHONE_HEIGHT)
                
                frame_crop = frame_resized[y_start-offset_y:y_end-offset_y, :] if offset_y > 0 else frame_resized
                canvas[:frame_crop.shape[0], x_start:x_end] = frame_crop
            
            out.write(canvas)
            frame_count += 1
            
            if frame_count % 30 == 0:
                print(f"📊 Procesados {frame_count}/{total_frames} frames")
        
        cap.release()
        out.release()
        
        print("✅ Conversión OpenCV exitosa")
        return True
        
    except Exception as e:
        print(f"❌ Error con OpenCV: {e}")
        return False

def convertir_a_vertical_ffmpeg(input_path, output_path):
    """
    Convierte video a formato vertical usando FFmpeg directamente
    SIN estirar - usa pad para agregar barras negras
    """
    print("🔧 Convirtiendo a vertical con FFmpeg directo...")
    
    # Obtener la ruta correcta de FFmpeg
    ffmpeg_path = detectar_ffmpeg()
    if not ffmpeg_path:
        print("❌ FFmpeg no disponible")
        return False
    
    try:
        # Comando FFmpeg que NUNCA estira y siempre mantiene proporción
        cmd = [
            ffmpeg_path, '-y',  # -y para sobrescribir
            '-i', input_path,
            '-vf', f'scale=720:1280:force_original_aspect_ratio=decrease,pad=720:1280:(ow-iw)/2:(oh-ih)/2:black',
            '-c:a', 'aac',
            '-c:v', 'libx264',
            '-preset', 'fast',
            '-crf', '23',
            output_path
        ]
        
        print(f"🎬 Ejecutando: {' '.join(cmd)}")
        
        # Ejecutar FFmpeg
        result = subprocess.run(cmd, capture_output=True, text=True)
        
        if result.returncode == 0:
            print("✅ Conversión FFmpeg exitosa")
            return True
        else:
            print(f"❌ Error FFmpeg: {result.stderr}")
            return False
            
    except Exception as e:
        print(f"❌ Error ejecutando FFmpeg: {e}")
        return False

def convertir_a_vertical_inteligente(input_path, output_path):
    """
    Convierte video usando el mejor método disponible
    """
    # Intentar FFmpeg primero
    ffmpeg_path = detectar_ffmpeg()
    if ffmpeg_path:
        if convertir_a_vertical_ffmpeg(input_path, output_path):
            return True
        else:
            print("🔄 FFmpeg falló, intentando con OpenCV...")
    
    # Usar OpenCV como respaldo
    return convertir_a_vertical_opencv(input_path, output_path)

def convertir_a_vertical(video_clip):
    """
    Convierte video a formato vertical SIN ESTIRAR usando letterbox/pillarbox
    Mantiene la proporción original y agrega barras negras donde sea necesario
    """
    print("📱 Convirtiendo a formato vertical SIN estirar...")
    
    # Obtener dimensiones originales
    orig_w, orig_h = video_clip.size
    orig_aspect = orig_w / orig_h
    target_aspect = PHONE_WIDTH / PHONE_HEIGHT
    
    print(f"📐 Original: {orig_w}x{orig_h} (aspect: {orig_aspect:.3f})")
    print(f"📐 Target: {PHONE_WIDTH}x{PHONE_HEIGHT} (aspect: {target_aspect:.3f})")
    
    try:
        # MÉTODO LETTERBOX/PILLARBOX: NUNCA estirar, solo escalar y centrar
        
        # Crear fondo negro de destino
        fondo = ColorClip(size=(PHONE_WIDTH, PHONE_HEIGHT), color=(0, 0, 0))
        fondo = fondo.set_duration(video_clip.duration)
        
        # Calcular escala para que quepa SIN estirar
        scale_w = PHONE_WIDTH / orig_w
        scale_h = PHONE_HEIGHT / orig_h
        
        # Usar la escala MENOR para que todo quepa sin estirar
        scale = min(scale_w, scale_h)
        
        new_w = int(orig_w * scale)
        new_h = int(orig_h * scale)
        
        print(f"🔧 Escalando por factor {scale:.3f}")
        print(f"📐 Nuevo tamaño (sin estirar): {new_w}x{new_h}")
        
        # Redimensionar video manteniendo proporción
        video_escalado = video_clip.resize((new_w, new_h))
        
        # Centrar el video escalado en el fondo negro
        video_centrado = video_escalado.set_position('center')
        
        # Combinar fondo + video centrado
        video_final = CompositeVideoClip([fondo, video_centrado])
    
        # Verificar dimensiones finales
        final_w, final_h = video_final.size
        print(f"📊 Dimensiones finales del composite: {final_w}x{final_h}")
        
        if orig_aspect > target_aspect:
            print(f"📱 Video horizontal: agregadas barras negras arriba/abajo (pillarbox)")
            print(f"   📏 Barras verticales: {(PHONE_HEIGHT - new_h) // 2}px arriba y abajo")
        else:
            print(f"📱 Video vertical: agregadas barras negras a los lados (letterbox)")
            print(f"   📏 Barras horizontales: {(PHONE_WIDTH - new_w) // 2}px a cada lado")
        
        print(f"✅ Video convertido SIN estirar: {PHONE_WIDTH}x{PHONE_HEIGHT}")
        return video_final
        
    except Exception as e:
        print(f"❌ Error en conversión letterbox: {e}")
        print("🔄 Aplicando método de respaldo...")
        
        # Método de respaldo simple con escala uniforme
        try:
            scale = min(PHONE_WIDTH / orig_w, PHONE_HEIGHT / orig_h)
            new_w = int(orig_w * scale)
            new_h = int(orig_h * scale)
            
            video_escalado = video_clip.resize((new_w, new_h))
            
            # Crear composite con fondo negro
            fondo = ColorClip(size=(PHONE_WIDTH, PHONE_HEIGHT), color=(0, 0, 0))
            fondo = fondo.set_duration(video_clip.duration)
            
            video_centrado = video_escalado.set_position('center')
            return CompositeVideoClip([fondo, video_centrado])
            
        except Exception as e2:
            print(f"❌ Error en método de respaldo: {e2}")
            # Último recurso: resize directo (puede estirar)
            print("⚠️ Usando resize directo como último recurso")
            return video_clip.resize((PHONE_WIDTH, PHONE_HEIGHT))

def optimizar_para_movil(video_clip):
    """
    Aplica optimizaciones específicas para reproducción móvil
    """
    print("📱 Aplicando optimizaciones para móvil...")
    
    # Ajustar FPS si es muy alto (máximo 30 fps para móvil)
    if video_clip.fps > 30:
        print(f"⚡ Reduciendo FPS de {video_clip.fps} a 30")
        video_clip = video_clip.set_fps(30)
    
    # Asegurar que la duración sea válida
    if video_clip.duration < 1:
        print("⚠️ Video muy corto, extendiendo duración mínima")
        video_clip = video_clip.loop(duration=max(1, video_clip.duration))
    
    return video_clip

def procesar_video_vertical_estandar_ffmpeg(input_path, carpeta=".", detection_data=None):
    """
    Procesa video usando FFmpeg directo, con sincronización de alta precisión.
    Acepta un diccionario de detección para re-escalar tiempos.
    """
    print(f"🚀 Procesamiento con FFmpeg directo: {input_path}")
    
    # Limpiar carpeta static al inicio
    if "static" in carpeta:
        limpiar_carpeta_static()
    
    # Verificar archivo de entrada
    if not os.path.exists(input_path):
        print(f"❌ Archivo no encontrado: {input_path}")
        return None
    
    # Rutas de salida
    nombre_base = os.path.splitext(os.path.basename(input_path))[0]
    output_video = os.path.join(carpeta, f"{nombre_base}_VERTICAL.mp4")
    transcript_path = os.path.join(carpeta, f"{nombre_base}_transcripcion.txt")
    audio_path = os.path.join(carpeta, f"{nombre_base}_audio_completo.mp3")
    
    # 1. EXTRAER DATOS DE DETECCIÓN
    detecciones = []
    original_fps = 30.0 # Default
    if detection_data and 'detections' in detection_data:
        detecciones = detection_data['detections']
        original_fps = detection_data.get('original_fps', original_fps)
        print(f"✅ Usando {len(detecciones)} detecciones pre-calculadas con FPS original de {original_fps:.2f}")
    else:
        print("⚠️ No se proveyeron datos de detección, el resultado puede no tener subtítulos/audio.")
    
    # 2. CONVERTIR A VERTICAL CON MÉTODO INTELIGENTE
    print("🔧 Convirtiendo a formato vertical...")
    video_temp = os.path.join(carpeta, f"{nombre_base}_temp_vertical.mp4")
    
    if not convertir_a_vertical_inteligente(input_path, video_temp):
        print("❌ Error en conversión de formato")
        return None
    
    # 3. AGREGAR SUBTÍTULOS Y AUDIO A VIDEO CONVERTIDO (SINCRONIZADOS AL FINAL DE CADA SEÑA)
    if detecciones:
        print("🎬 Agregando subtítulos y audio sincronizados AL FINALIZAR cada seña...")
        video_clip = None
        video_con_subtitulos = None
        video_final = None
        
        try:
            # Cargar video convertido con MoviePy
            video_clip = VideoFileClip(video_temp)
            final_fps = video_clip.fps
            
            # --- LOGGING DE VALIDACIÓN ---
            print("\n--- VALIDACIÓN DE SINCRONIZACIÓN ---")
            print(f"FPS Original: {original_fps:.2f} | FPS Final: {final_fps:.2f}")
            time_rescale_ratio = final_fps / original_fps if original_fps > 0 else 1.0
            print(f"Ratio de re-escala de tiempo: {time_rescale_ratio:.4f}")
            
            final_audio_clips = []
            final_subtitle_clips = []

            for i, (frame_absoluto, palabra, _) in enumerate(detecciones):
                # Calcular tiempo de anclaje en la línea de tiempo ORIGINAL
                t_original = frame_absoluto / original_fps
                
                # Re-escalar a la línea de tiempo FINAL
                t_ancla_final = t_original * time_rescale_ratio

                if i == 0: # Log del primer item como ejemplo
                    print(f"Ejemplo Mapeo: Frame Absoluto {frame_absoluto} -> t_original {t_original:.2f}s -> t_final {t_ancla_final:.2f}s")
                
                # Generar audio y subtítulo
                temp_audio = os.path.join(carpeta, f"temp_audio_{frame_absoluto}.mp3")
                if generar_audio(palabra, temp_audio):
                    try:
                        audio_clip = AudioFileClip(temp_audio).set_start(t_ancla_final)
                        final_audio_clips.append(audio_clip)
                    except Exception as e:
                        print(f"⚠️ Error cargando audio {temp_audio}: {e}")

                subtitle_duration = calcular_duracion_subtitulo(palabra)
                subtitulo_clip = crear_subtitulo_vertical(palabra, subtitle_duration)
                if subtitulo_clip:
                    subtitulo_clip = subtitulo_clip.set_start(t_ancla_final)
                    final_subtitle_clips.append(subtitulo_clip)
            
            print("-------------------------------------\n")

            # Combinar todo
            video_con_subtitulos = CompositeVideoClip([video_clip] + final_subtitle_clips) if final_subtitle_clips else video_clip
            video_final = video_con_subtitulos.set_audio(CompositeAudioClip(final_audio_clips)) if final_audio_clips else video_con_subtitulos
            
            # Exportar video final con subtítulos y audio
            print("💾 Exportando video con subtítulos y audio...")
            video_final.write_videofile(
                output_video,
                codec="libx264",
                audio_codec="aac",
                verbose=False,
                logger=None,
                temp_audiofile=None,
                remove_temp=True,
                ffmpeg_params=["-pix_fmt", "yuv420p"]
            )
            
            # Cerrar clips PRIMERO para liberar archivos
            cerrar_clips_seguro(video_clip, video_con_subtitulos, video_final)
            print("✅ Clips cerrados correctamente")
            
            # Limpiar archivos temporales de manera segura
            archivos_temp = []
            for detection_data in detecciones:
                frame_idx = detection_data[0]  # Primer elemento siempre es frame_idx
                temp_audio = os.path.join(carpeta, f"temp_audio_{frame_idx}.mp3")
                if os.path.exists(temp_audio):
                    archivos_temp.append(temp_audio)
            
            # Agregar video temporal a la lista de limpieza
            if os.path.exists(video_temp):
                archivos_temp.append(video_temp)
            
            # Limpiar todos los archivos temporales
            limpiar_archivos_seguro(*archivos_temp)
            
            print("✅ Video con subtítulos y audio creado exitosamente")
            
        except Exception as e:
            print(f"❌ Error agregando subtítulos/audio: {e}")
            # Si falla, usar video convertido sin subtítulos
            try:
                # Cerrar todos los clips antes de intentar operaciones de archivo
                cerrar_clips_seguro(
                    locals().get('video_clip'),
                    locals().get('video_con_subtitulos'),
                    locals().get('video_final')
                )
                
                # Esperar un momento para que Windows libere los archivos
                time.sleep(1)
                
                if os.path.exists(video_temp):
                    try:
                        os.rename(video_temp, output_video)
                        print("⚠️ Usando video convertido sin subtítulos")
                    except PermissionError:
                        # Si no se puede renombrar, copiar en su lugar
                        try:
                            shutil.copy2(video_temp, output_video)
                            limpiar_archivos_seguro(video_temp)
                            print("⚠️ Video copiado sin subtítulos (renombrar falló)")
                        except Exception as copy_error:
                            print(f"❌ No se pudo copiar video: {copy_error}")
            except Exception as cleanup_error:
                print(f"⚠️ Error en limpieza de emergencia: {cleanup_error}")
        finally:
            # Asegurar que los clips se cierren siempre
            cerrar_clips_seguro(video_clip, video_con_subtitulos, video_final)
    else:
        # Sin detecciones, solo renombrar video convertido
        print("⚠️ Sin señas detectadas, usando video convertido simple")
        if os.path.exists(video_temp):
            os.rename(video_temp, output_video)
    
    # 4. CREAR TRANSCRIPCIÓN Y AUDIO COMPLETO
    print("📝 Guardando transcripción...")
    # Extraer solo las palabras, independiente del formato (con/sin confianza)
    transcripcion_palabras = []
    for detection_data in detecciones:
        if len(detection_data) >= 2:
            palabra = detection_data[1]  # Segundo elemento siempre es la palabra
            transcripcion_palabras.append(palabra)
    
    try:
        if transcripcion_palabras:
            transcripcion = ". ".join([p.capitalize() for p in transcripcion_palabras])
        else:
            transcripcion = "No se detectaron señas en el video."
        
        with open(transcript_path, "w", encoding="utf-8") as f:
            f.write(transcripcion)
        
        if os.path.exists(transcript_path):
            print(f"✅ Transcripción guardada: {transcript_path}")
        else:
            transcript_path = None
            
    except Exception as e:
        print(f"❌ Error guardando transcripción: {e}")
        transcript_path = None
    
    # 4. GENERAR AUDIO COMPLETO
    print("🔊 Generando audio completo...")
    try:
        if transcripcion_palabras:
            texto_completo = " ".join(transcripcion_palabras)
        else:
            texto_completo = "No se detectaron señas"
        
        if generar_audio(texto_completo, audio_path):
            if os.path.exists(audio_path):
                print(f"✅ Audio completo guardado: {audio_path}")
            else:
                audio_path = None
        else:
            audio_path = None
            
    except Exception as e:
        print(f"❌ Error generando audio completo: {e}")
        audio_path = None
    
    # 5. VERIFICAR DIMENSIONES FINALES
    verificar_dimensiones_finales(output_video)
    
    print("🎉 ¡Procesamiento completado!")
    print(f"📱 Video vertical: {output_video}")
    print(f"📄 Transcripción: {transcript_path}")
    print(f"🔊 Audio: {audio_path}")
    print(f"📐 Formato final: {PHONE_WIDTH}x{PHONE_HEIGHT} (vertical estándar)")
    
    return output_video, transcript_path, audio_path

def procesar_video_vertical_estandar(input_path, carpeta="."):
    """
    Procesa video convirtiéndolo SIEMPRE a formato vertical estándar de teléfono
    """
    print(f"🚀 Iniciando procesamiento para formato vertical: {input_path}")
    
    # Limpiar carpeta static al inicio
    if "static" in carpeta:
        limpiar_carpeta_static()
    
    # Verificar archivo de entrada
    if not os.path.exists(input_path):
        print(f"❌ Archivo no encontrado: {input_path}")
        return None
    
    # Rutas de salida
    nombre_base = os.path.splitext(os.path.basename(input_path))[0]
    output_video = os.path.join(carpeta, f"{nombre_base}_VERTICAL.mp4")
    transcript_path = os.path.join(carpeta, f"{nombre_base}_transcripcion.txt")
    audio_path = os.path.join(carpeta, f"{nombre_base}_audio_completo.mp3")
    
    # 1. DETECTAR SEÑAS
    print("🔍 Detectando señas en el video...")
    try:
        detecciones = evaluate_model_with_frames(input_path, threshold=0.5)
        
        if not detecciones:
            print("⚠️ No se detectaron señas, intentando con threshold más bajo...")
            detecciones = evaluate_model_with_frames(input_path, threshold=0.3)
        
        if not detecciones:
            print("❌ No se detectaron señas en el video")
            print("🚫 Sin señas detectadas - no se generarán archivos de salida")
            return None  # No procesar si no hay señas
            
        print(f"✅ Detectadas {len(detecciones)} señas")
        
    except Exception as e:
        print(f"❌ Error en detección de señas: {e}")
        print("🚫 Error en detección - no se generarán archivos de salida")
        return None
    
    # 2. CARGAR VIDEO
    print("🎬 Cargando video original...")
    try:
        video_clip = VideoFileClip(input_path)
        print(f"📐 Video original: {video_clip.size[0]}x{video_clip.size[1]}")
        print(f"🎞️ Duración: {video_clip.duration:.2f}s")
        print(f"📊 FPS: {video_clip.fps}")
        
    except Exception as e:
        print(f"❌ Error cargando video: {e}")
        return None
    
    # 3. OPTIMIZAR PARA MÓVIL
    video_optimizado = optimizar_para_movil(video_clip)
    
    # 4. CONVERTIR A FORMATO VERTICAL ESTÁNDAR
    video_vertical = convertir_a_vertical(video_optimizado)
    
    # 5. CREAR SUBTÍTULOS PARA FORMATO VERTICAL
    print("🔤 Creando subtítulos para formato vertical...")
    subtitulos = []
    transcripcion_palabras = []
    
    for detection_data in detecciones:
        # Extraer datos de la detección (manejar formato con/sin confianza)
        if len(detection_data) == 3:
            frame_idx, palabra, confidence = detection_data
            confidence_text = f" ({confidence:.1f}%)"
        else:
            frame_idx, palabra = detection_data
            confidence_text = ""
        
        detection_time = frame_idx / video_vertical.fps
        duracion = calcular_duracion_subtitulo(palabra)
        
        subtitulo = crear_subtitulo_vertical(palabra, duracion)
        if subtitulo:
            subtitulo = subtitulo.set_start(detection_time)
            subtitulos.append(subtitulo)
            transcripcion_palabras.append(palabra)
            print(f"📝 Subtítulo '{palabra}'{confidence_text} al detectar en {detection_time:.2f}s (duración: {duracion:.1f}s)")
    
    # 6. COMBINAR VIDEO CON SUBTÍTULOS
    print("🎬 Combinando video vertical con subtítulos...")
    try:
        if subtitulos:
            video_con_subtitulos = CompositeVideoClip([video_vertical] + subtitulos)
        else:
            video_con_subtitulos = video_vertical
        print("✅ Subtítulos agregados exitosamente")
    except Exception as e:
        print(f"❌ Error combinando subtítulos: {e}")
        video_con_subtitulos = video_vertical
    
    # 7. GENERAR AUDIO SINCRONIZADO
    print("🎧 Generando audio sincronizado...")
    audio_clips = []
    
    for detection_data in detecciones:
        # Extraer datos de la detección (manejar formato con/sin confianza)
        if len(detection_data) == 3:
            frame_idx, palabra, confidence = detection_data
            confidence_text = f" ({confidence:.1f}%)"
        else:
            frame_idx, palabra = detection_data
            confidence_text = ""
        
        detection_time = frame_idx / video_con_subtitulos.fps
        temp_audio = os.path.join(carpeta, f"temp_audio_{frame_idx}.mp3")
        
        if generar_audio(palabra, temp_audio):
            try:
                clip = AudioFileClip(temp_audio).set_start(detection_time)
                audio_clips.append(clip)
                print(f"🎵 Audio para '{palabra}'{confidence_text} al detectar en {detection_time:.2f}s")
            except Exception as e:
                print(f"⚠️ Error con audio {temp_audio}: {e}")
    
    # 8. COMBINAR AUDIO CON VIDEO
    print("🔊 Combinando audio con video vertical...")
    try:
        if audio_clips:
            audio_final = CompositeAudioClip(audio_clips)
            video_final = video_con_subtitulos.set_audio(audio_final)
        else:
            video_final = video_con_subtitulos
        print("✅ Audio combinado exitosamente")
    except Exception as e:
        print(f"❌ Error combinando audio: {e}")
        video_final = video_con_subtitulos
    
    # 9. VERIFICAR DIMENSIONES ANTES DE EXPORTAR (SIN CORREGIR)
    pre_export_w, pre_export_h = video_final.size
    print(f"🔍 PRE-EXPORTACIÓN - Dimensiones del video final: {pre_export_w}x{pre_export_h}")
    
    if pre_export_w != PHONE_WIDTH or pre_export_h != PHONE_HEIGHT:
        print(f"⚠️ ADVERTENCIA: Dimensiones inesperadas antes de exportar")
        print(f"   Actual: {pre_export_w}x{pre_export_h}")
        print(f"   Esperado: {PHONE_WIDTH}x{PHONE_HEIGHT}")
        print(f"   📱 Esto es normal con el método letterbox - el composite mantiene proporciones")
    else:
        print(f"✅ Dimensiones correctas antes de exportar: {pre_export_w}x{pre_export_h}")
    
    # 10. EXPORTAR EN FORMATO VERTICAL OPTIMIZADO
    print("💾 Exportando video vertical final...")
    try:
        # Parámetros simplificados para evitar errores de compatibilidad
        video_final.write_videofile(
            output_video,
            codec="libx264",
            audio_codec="aac",
            verbose=False,
            logger=None,
            temp_audiofile=None,  # Evitar conflictos con temp_audiofile
            remove_temp=True,
            ffmpeg_params=[
                "-pix_fmt", "yuv420p"  # Solo formato compatible, SIN forzar escala
            ]
        )
        
        print("✅ Video vertical exportado exitosamente")
        
        # Verificar dimensiones finales
        verificar_dimensiones_finales(output_video)
        
    except Exception as e:
        print(f"❌ Error exportando video: {e}")
        return None
    
    # 10. GUARDAR TRANSCRIPCIÓN (MEJORADO)
    print("📝 Guardando transcripción...")
    try:
        if transcripcion_palabras:
            transcripcion = ". ".join([p.capitalize() for p in transcripcion_palabras])
        else:
            transcripcion = "No se detectaron señas en el video."
        
        with open(transcript_path, "w", encoding="utf-8") as f:
            f.write(transcripcion)
        
        # Verificar que el archivo se creó
        if os.path.exists(transcript_path):
            print(f"✅ Transcripción guardada: {transcript_path}")
        else:
            print(f"⚠️ Archivo de transcripción no se pudo verificar: {transcript_path}")
            transcript_path = None
            
    except Exception as e:
        print(f"❌ Error guardando transcripción: {e}")
        transcript_path = None
    
    # 11. GENERAR AUDIO COMPLETO (MEJORADO)
    print("🔊 Generando audio completo...")
    try:
        if transcripcion_palabras:
            texto_completo = " ".join(transcripcion_palabras)
        else:
            texto_completo = "No se detectaron señas"
        
        if generar_audio(texto_completo, audio_path):
            # Verificar que el archivo se creó
            if os.path.exists(audio_path):
                print(f"✅ Audio completo guardado: {audio_path}")
            else:
                print(f"⚠️ Archivo de audio no se pudo verificar: {audio_path}")
                audio_path = None
        else:
            print("❌ No se pudo generar audio completo")
            audio_path = None
            
    except Exception as e:
        print(f"❌ Error generando audio completo: {e}")
        audio_path = None
    
    # 12. LIMPIEZA
    print("🧹 Limpiando archivos temporales...")
    try:
        video_clip.close()
        if 'video_vertical' in locals():
            video_vertical.close()
        if 'video_con_subtitulos' in locals():
            video_con_subtitulos.close()
        if 'video_final' in locals():
            video_final.close()
        
        # Limpiar archivos temporales
        for detection_data in detecciones:
            frame_idx = detection_data[0]  # Primer elemento siempre es frame_idx
            temp_audio = os.path.join(carpeta, f"temp_audio_{frame_idx}.mp3")
            if os.path.exists(temp_audio):
                os.remove(temp_audio)
        
        # Limpiar archivo de audio temporal
        temp_audio_main = os.path.join(carpeta, "temp_audio.mp3")
        if os.path.exists(temp_audio_main):
            os.remove(temp_audio_main)
            
    except Exception as e:
        print(f"⚠️ Error en limpieza: {e}")
    
    print("🎉 ¡Procesamiento completado!")
    print(f"📱 Video vertical: {output_video}")
    print(f"📄 Transcripción: {transcript_path}")
    print(f"🔊 Audio: {audio_path}")
    print(f"📐 Formato final: {PHONE_WIDTH}x{PHONE_HEIGHT} (vertical estándar)")
    
    return output_video, transcript_path, audio_path

def verificar_dimensiones_finales(video_path):
    """Verifica las dimensiones del video final y reporta problemas"""
    try:
        import cv2
        cap = cv2.VideoCapture(video_path)
        if cap.isOpened():
            width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
            height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
            cap.release()
            
            print(f"🔍 VERIFICACIÓN FINAL DE DIMENSIONES:")
            print(f"   📐 Video exportado: {width}x{height}")
            print(f"   📐 Esperado: {PHONE_WIDTH}x{PHONE_HEIGHT}")
            
            if width == PHONE_WIDTH and height == PHONE_HEIGHT:
                print(f"✅ DIMENSIONES CORRECTAS: {width}x{height} (formato vertical estándar)")
                return True
            else:
                print(f"❌ DIMENSIONES INCORRECTAS!")
                print(f"   🔧 El video se exportó en {width}x{height} en lugar de {PHONE_WIDTH}x{PHONE_HEIGHT}")
                
                # Calcular aspect ratio
                aspect_actual = width / height
                aspect_esperado = PHONE_WIDTH / PHONE_HEIGHT
                print(f"   📊 Aspect ratio actual: {aspect_actual:.3f}")
                print(f"   📊 Aspect ratio esperado: {aspect_esperado:.3f}")
                
                return False
        else:
            print("❌ No se pudo abrir el video para verificar dimensiones")
            return False
    except Exception as e:
        print(f"❌ Error verificando dimensiones: {e}")
        return False

def procesar_solo_formato(input_path, carpeta="."):
    """
    Función para solo convertir formato sin procesamiento de señas
    """
    print(f"🔄 Convirtiendo solo formato a vertical: {input_path}")
    
    try:
        video_clip = VideoFileClip(input_path)
        video_optimizado = optimizar_para_movil(video_clip)
        video_vertical = convertir_a_vertical(video_optimizado)
        
        nombre_base = os.path.splitext(os.path.basename(input_path))[0]
        output_video = os.path.join(carpeta, f"{nombre_base}_VERTICAL_ONLY.mp4")
        
        video_vertical.write_videofile(
            output_video,
            codec="libx264",
            audio_codec="aac",
            bitrate="3000k",
            preset="fast",
            verbose=False,
            logger=None
        )
        
        video_clip.close()
        video_vertical.close()
        
        print(f"✅ Video convertido a formato vertical: {output_video}")
        return output_video
        
    except Exception as e:
        print(f"❌ Error convirtiendo formato: {e}")
        return None

if __name__ == "__main__":
    # Prueba con limpieza automática
    print("🧪 Ejecutando prueba con limpieza automática...")
    
    # Limpiar carpeta static antes de la prueba
    limpiar_carpeta_static()
    
    resultado = procesar_video_vertical_estandar("video_prueba.mp4")
    if resultado:
        print("✅ Éxito! Archivos generados:")
        if isinstance(resultado, (list, tuple)):
            for i, archivo in enumerate(resultado):
                if archivo and os.path.exists(archivo):
                    print(f"  {i+1}. {archivo}")
        else:
            print(f"  1. {resultado}")
    else:
        print("❌ Sin señas detectadas - no se generaron archivos")
