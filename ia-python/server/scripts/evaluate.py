import os, cv2, argparse, numpy as np
from typing import List, Tuple, NamedTuple
from tensorflow.keras.models import load_model
from mediapipe.python.solutions.holistic import Holistic
from .helpers import (
    mediapipe_detection, there_hand, extract_keypoints, draw_keypoints, 
    disambiguate_signs_by_segment
)
from .constants import (
    MODEL_PATH, CLASSES, MODEL_FRAMES, THRESHOLD, MIN_LENGTH_FRAMES,
    LENGTH_KEYPOINTS, MARGIN_FRAME, DELAY_FRAMES, MAX_SEGMENT_FRAMES,
    FONT, FONT_SIZE, FONT_POS, words_text,
    # --- Importar constantes anti-duplicado ---
    COOLDOWN_SECONDS, HISTERESIS_ON_THRESHOLD, HISTERESIS_OFF_THRESHOLD,
    SILENCE_FRAMES_BETWEEN_SAME_CLASS, NMS_TEMPORAL_MS, MIN_CLEAN_FRAMES_FOR_RESET,
    # --- Importar constantes de desambiguación ---
    SIMILAR_SIGNS_GROUPS, APEX_HEIGHT_THRESHOLD, MOUTH_DWELL_THRESHOLD, APEX_K_FRAMES
)
from .text_to_speech import text_to_speech
import logging
import time

# Configuración básica de logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')

def _interpolate(seq: List[np.ndarray], target_len: int) -> List[np.ndarray]:
    """Interpolación lineal para asegurar que la secuencia tenga exactamente target_len frames."""
    seq_len = len(seq)
    
    if seq_len == target_len:
        return seq

    if seq_len == 1:
        return [seq[0] for _ in range(target_len)]

    # Generar índices para la nueva secuencia
    new_indices = np.linspace(0, seq_len - 1, target_len)
    
    result = []
    for idx in new_indices:
        idx_low = int(np.floor(idx))
        idx_high = int(np.ceil(idx))
        
        if idx_low == idx_high:
            result.append(seq[idx_low])
        else:
            # Interpolar
            alpha = idx - idx_low
            interpolated = seq[idx_low] * (1 - alpha) + seq[idx_high] * alpha
            result.append(interpolated)
            
    return result

def _downsample(seq: List[np.ndarray], target_len: int) -> List[np.ndarray]:
    """Submuestreo uniforme."""
    if len(seq) <= target_len:
        return seq
    
    indices = np.linspace(0, len(seq) - 1, target_len, dtype=int)
    return [seq[i] for i in indices]

def normalize_keypoints(seq: List[np.ndarray], target_len: int = MODEL_FRAMES) -> np.ndarray:
    """Interpola/corta/apila a (T,D), garantizando la forma final."""
    if len(seq) == 0:
        return np.zeros((target_len, LENGTH_KEYPOINTS), dtype=np.float32)
    
    if len(seq) < target_len:
        processed_seq = _interpolate(seq, target_len)
    elif len(seq) > target_len:
        processed_seq = _downsample(seq, target_len)
    else: # len(seq) == target_len
        processed_seq = seq
    
    result = np.array(processed_seq, dtype=np.float32)
    
    # Garantizar la forma antes de retornar
    assert result.shape == (target_len, LENGTH_KEYPOINTS), \
        f"Error de forma en normalize_keypoints: se esperaba ({target_len}, {LENGTH_KEYPOINTS}), se obtuvo {result.shape}"
        
    return result

def predict_sequence(model, kp_seq: List[np.ndarray]) -> (list, list):
    """Normaliza, predice y retorna los 2 mejores resultados (índice y confianza)."""
    if len(kp_seq) == 0:
        return [], []
    
    normalized = normalize_keypoints(kp_seq, MODEL_FRAMES)
    logging.info(f"Verificación de forma: len_seq={len(kp_seq)}, normalized.shape={normalized.shape}")

    prediction = model.predict(normalized.reshape(1, MODEL_FRAMES, LENGTH_KEYPOINTS), verbose=0)[0]
    
    # Obtener los 2 mejores índices
    top2_indices = prediction.argsort()[-2:][::-1]
    top2_confidences = [prediction[i] for i in top2_indices]
    
    return top2_indices, top2_confidences

def _temporal_nms(detections, fps, nms_ms):
    """Aplica Non-Maximum Suppression temporal a las detecciones."""
    if not detections:
        return []

    # Ordenar por confianza (mayor a menor)
    detections.sort(key=lambda x: x[2], reverse=True)
    
    nms_seconds = nms_ms / 1000.0
    final_detections = []
    
    for det in detections:
        is_duplicate = False
        for final_det in final_detections:
            # Comprobar si es la misma clase
            if det[1] == final_det[1]:
                # Calcular diferencia de tiempo
                time_diff = abs(det[0] - final_det[0]) / fps
                if time_diff < nms_seconds:
                    is_duplicate = True
                    break
        
        if not is_duplicate:
            final_detections.append(det)
            
    # Re-ordenar por frame
    final_detections.sort(key=lambda x: x[0])
    return final_detections

def evaluate_model_with_frames(video_path: str, threshold: float = THRESHOLD) -> dict:
    """
    Procesa un video con lógica anti-duplicado y retorna detecciones y metadatos.
    """
    # --- BANDERAS DE VERIFICACIÓN DE FLIP ---
    flip_aplicado_entrada = False
    flip_aplicado_deteccion = False
    flip_aplicado_overlay = False
    flip_aplicado_export = False

    logging.info(
        f"Banderas de Flip: entrada={flip_aplicado_entrada}, deteccion={flip_aplicado_deteccion}, "
        f"overlay={flip_aplicado_overlay}, export={flip_aplicado_export}"
    )
    # -----------------------------------------

    if not os.path.exists(video_path):
        raise FileNotFoundError(f"Video no encontrado: {video_path}")
    
    model = load_model(MODEL_PATH)
    holistic = Holistic(min_detection_confidence=0.5, min_tracking_confidence=0.5)
    
    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        raise RuntimeError("No se pudo abrir el video")
    
    original_fps = cap.get(cv2.CAP_PROP_FPS)
    original_frame_count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    
    # --- Variables de estado para anti-duplicado ---
    all_detections = []
    
    # Estado del segmento actual
    in_segment = False
    segment_kp = []
    segment_results = []
    segment_start_frame = 0
    best_in_segment = {"top1_word": None, "top2_word": None, "conf": 0.0, "frame": 0}
    
    # Estado de histéresis y cooldown
    hysteresis_armed = {word: True for word in CLASSES}
    last_detection_time = {word: 0 for word in CLASSES}
    
    # Contador de frames sin manos para "silencio"
    clean_frames_count = 0
    
    absolute_frame_count = 0
    
    try:
        while True:
            ret, frame = cap.read()
            if not ret:
                break
            absolute_frame_count += 1

            # --- PRUEBA DE HUMO (SMOKE TEST) ---
            if absolute_frame_count == 1:
                h, w, _ = frame.shape
                if (w // 3) > 10:
                    logging.info("PRUEBA DE HUMO (ENTRADA): OK")
                else:
                    logging.error("PRUEBA DE HUMO (ENTRADA): FALLO - Posible flip.")
                    raise RuntimeError("Video de entrada parece estar espejado.")
            # ------------------------------------

            image, results = mediapipe_detection(frame, holistic)
            
            # --- LÓGICA DE SEGMENTACIÓN Y DETECCIÓN ---
            hand_present = there_hand(results)
            
            if hand_present:
                clean_frames_count = 0 # Resetear contador de silencio
                
                if not in_segment:
                    # Iniciar nuevo segmento
                    in_segment = True
                    segment_start_frame = absolute_frame_count
                    segment_kp = []
                    segment_results = []
                    best_in_segment = {"top1_word": None, "top2_word": None, "conf": 0.0, "frame": 0}
                
                # Acumular datos del segmento
                segment_kp.append(extract_keypoints(results))
                segment_results.append(results)
                
                # Predecir en cada frame del segmento para encontrar el de mayor confianza
                if len(segment_kp) >= MIN_LENGTH_FRAMES:
                    top_indices, top_confs = predict_sequence(model, segment_kp)
                    
                    if top_confs and top_confs[0] > best_in_segment["conf"]:
                        top1_word = CLASSES[top_indices[0]]
                        top2_word = CLASSES[top_indices[1]] if len(top_indices) > 1 else None
                        
                        best_in_segment = {
                            "top1_word": top1_word,
                            "top2_word": top2_word,
                            "conf": top_confs[0], 
                            "frame": absolute_frame_count
                        }
                        
                    # Lógica de histéresis para re-armar
                    word = CLASSES[top_indices[0]]
                    conf = top_confs[0]
                    for w in CLASSES:
                        if w != word or conf < HISTERESIS_OFF_THRESHOLD:
                            hysteresis_armed[w] = True
                            
            else: # No hay mano
                clean_frames_count += 1
                
                if in_segment:
                    # Finalizar segmento y evaluar la mejor detección
                    if best_in_segment["top1_word"] is not None:
                        top1_word = best_in_segment["top1_word"]
                        conf = best_in_segment["conf"]
                        frame_anchor = best_in_segment["frame"]
                        
                        final_word = top1_word
                        
                        # Log de depuración al final del segmento
                        passed_threshold = conf >= max(THRESHOLD, HISTERESIS_ON_THRESHOLD)
                        logging.info(f"Segmento finalizado: best={top1_word} conf={conf:.2f} frame={frame_anchor} passed? {passed_threshold}")

                        # 1. Comprobar histéresis
                        if passed_threshold and hysteresis_armed[final_word]:
                            # 2. Comprobar cooldown
                            current_time = time.time()
                            if (current_time - last_detection_time[final_word]) >= COOLDOWN_SECONDS:
                                # Detección válida
                                all_detections.append((frame_anchor, final_word, conf))
                                
                                # Actualizar estado
                                last_detection_time[final_word] = current_time
                                hysteresis_armed[final_word] = False # Desarmar hasta que baje la confianza
                    
                    # Resetear estado del segmento
                    in_segment = False
                    segment_kp = []
                    segment_results = []
                    
                # Resetear completamente si hay suficientes frames limpios
                if clean_frames_count >= MIN_CLEAN_FRAMES_FOR_RESET:
                    for w in CLASSES:
                        hysteresis_armed[w] = True

    finally:
        cap.release()
        holistic.close()
    
    # --- POST-PROCESIAMENTO: NMS TEMPORAL ---
    final_detections = _temporal_nms(all_detections, original_fps, NMS_TEMPORAL_MS)
    
    logging.info(f"Detecciones crudas: {len(all_detections)}, Detecciones finales (post-NMS): {len(final_detections)}")
    
    return {
        "detections": final_detections,
        "original_fps": original_fps,
        "original_frame_count": original_frame_count
    }

def evaluate_model(model_path: str, src: str = None, threshold: float = THRESHOLD, show: bool = True):
    """Función principal de evaluación."""
    # Cargar modelo
    if not os.path.exists(model_path):
        raise FileNotFoundError(f"Modelo no encontrado: {model_path}")
    
    model = load_model(model_path)
    print(f"✅ Modelo cargado: {model_path}")
    
    # Inicializar MediaPipe
    holistic = Holistic(min_detection_confidence=0.5, min_tracking_confidence=0.5)
    
    # Abrir video/webcam
    if src is None:
        cap = cv2.VideoCapture(0)
        print("📹 Usando webcam")
    else:
        cap = cv2.VideoCapture(src)
        print(f"🎬 Usando video: {src}")
    
    if not cap.isOpened():
        raise RuntimeError("No se pudo abrir la fuente de video")
    
    # Variables de segmentación
    recording = False
    count_frame = 0
    fix_frames = 0
    kp_seq = []
    sentence = []
    
    print("🎯 Sistema listo para reconocer señas")
    print("Presiona 'q' para salir")
    
    try:
        while True:
            ret, frame = cap.read()
            if not ret:
                break
            
            # Detección MediaPipe
            image, results = mediapipe_detection(frame, holistic)
            
            # Lógica de segmentación
            if there_hand(results) or recording:
                recording = True
                count_frame += 1
                
                if count_frame > MARGIN_FRAME:
                    kp_seq.append(extract_keypoints(results))
                
                if count_frame >= MAX_SEGMENT_FRAMES:
                    # Forzar predicción
                    if len(kp_seq) >= MIN_LENGTH_FRAMES:
                        top_indices, top_confs = predict_sequence(model, kp_seq)
                        if top_confs and top_confs[0] >= threshold:
                            word = CLASSES[top_indices[0]]
                            if not sentence or sentence[0] != word:
                                sentence.insert(0, word)
                                text_to_speech(words_text.get(word, word.upper()))
                            print(f"{top_indices[0]} ({top_confs[0]*100:.2f}%)")
                    # Reset
                    recording = False
                    count_frame = 0
                    fix_frames = 0
                    kp_seq = []
            else:
                if count_frame >= MIN_LENGTH_FRAMES + MARGIN_FRAME:
                    fix_frames += 1
                    
                    if fix_frames < DELAY_FRAMES:
                        recording = True
                    else:
                        # Recortar cola si aplica
                        if len(kp_seq) > MARGIN_FRAME + DELAY_FRAMES:
                            kp_seq = kp_seq[:-MARGIN_FRAME - DELAY_FRAMES]
                        
                        if len(kp_seq) >= MIN_LENGTH_FRAMES:
                            top_indices, top_confs = predict_sequence(model, kp_seq)
                            if top_confs and top_confs[0] >= threshold:
                                word = CLASSES[top_indices[0]]
                                if not sentence or sentence[0] != word:
                                    sentence.insert(0, word)
                                    text_to_speech(words_text.get(word, word.upper()))
                                print(f"{top_indices[0]} ({top_confs[0]*100:.2f}%)")
                        
                        # Reset completo
                        recording = False
                        count_frame = 0
                        fix_frames = 0
                        kp_seq = []
                else:
                    # Reset directo
                    recording = False
                    count_frame = 0
                    fix_frames = 0
                    kp_seq = []
            
            # Overlay
            if sentence:
                cv2.putText(image, ' | '.join(sentence).upper(), FONT_POS, FONT, FONT_SIZE, (255, 255, 255))
            
            # Dibujar keypoints
            draw_keypoints(image, results)
            
            # Mostrar ventana
            if show:
                cv2.imshow('Reconocimiento de Señas', image)
            
            # Salir con 'q'
            if cv2.waitKey(1) & 0xFF == ord('q'):
                break
                
    finally:
        cap.release()
        if show:
            cv2.destroyAllWindows()
        holistic.close()
    
    print(f"Detectado (última primero): {sentence}")

def main():
    """CLI principal."""
    parser = argparse.ArgumentParser(description='Reconocimiento de señas con MediaPipe + Keras')
    parser.add_argument('--src', type=str, help='Ruta del video (vacío para webcam)')
    parser.add_argument('--threshold', type=float, default=THRESHOLD, help=f'Umbral de confianza (default: {THRESHOLD})')
    parser.add_argument('--noshow', action='store_true', help='No mostrar ventana')
    
    args = parser.parse_args()
    
    try:
        evaluate_model(
            model_path=MODEL_PATH,
            src=args.src,
            threshold=args.threshold,
            show=not args.noshow
        )
    except Exception as e:
        print(f"❌ Error: {e}")
        return 1
    
    return 0

if __name__ == "__main__":
    exit(main())
