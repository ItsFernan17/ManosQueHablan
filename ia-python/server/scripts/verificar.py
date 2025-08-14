import os
import json
import numpy as np
from collections import deque

# === CONFIGURACIÓN GLOBAL ===
# Umbrales
TH_HIGH = 0.85
TH_LOW = 0.60
TH_RELEASE = 0.40
TH_SWITCH = 0.88
TH_NEUTRAL = 0.50
DELTA_SWITCH = 0.20

# Frames
PERSISTENCE_FRAMES = 6
COOLDOWN_FRAMES = 20
M_SWITCH = 6
Z_NEUTRAL = 5
Q_STILL = 4
MIN_GAP_BETWEEN_EVENTS = 10

# Suavizado
SMOOTHING = "ema"
EMA_ALPHA = 0.2
SMOOTH_WINDOW = 5

# Físico
VEL_MIN = 0.01
HEIGHT_EPS = 0.003
OPEN_THRESH = 0.35
FPS_DEFAULT = 30.0

# Configuración por clase (extensible)
PER_CLASS_TH = {}
CONFUSABLE_PAIRS = set()
PAIR_DELTA_SWITCH = {}
CLASS_GATES = {}

# Compatibilidad
MIN_HAND_RATIO = 0.30
DEFAULT_MIN_DISTANCE = 15

# Importaciones con manejo de errores
try:
    import cv2
    import mediapipe as mp
    from tensorflow.keras.models import load_model
    DEPENDENCIES_AVAILABLE = True
except ImportError as e:
    print(f"⚠️ Dependencias no disponibles: {e}")
    DEPENDENCIES_AVAILABLE = False
    cv2 = None
    mp = None
    load_model = None

try:
    from .helpers import mediapipe_detection, extract_keypoints, there_hand
    from .constants import MODEL_PATH, WORDS_JSON_PATH, MODEL_FRAMES, LENGTH_KEYPOINTS, validate_model_files
    LOCAL_IMPORTS_AVAILABLE = True
except ImportError as e:
    print(f"⚠️ Importaciones locales no disponibles: {e}")
    LOCAL_IMPORTS_AVAILABLE = False
    MODEL_PATH = "models/action.h5"
    WORDS_JSON_PATH = "models/words.json"
    MODEL_FRAMES = 30
    LENGTH_KEYPOINTS = 126

mp_holistic = mp.solutions.holistic if mp else None

def extract_keypoints_simplified(results):
    """Extrae 126 keypoints (solo manos) para el modelo"""
    if not DEPENDENCIES_AVAILABLE:
        return [0] * 126
    
    lh = np.array([[res.x, res.y, res.z] for res in results.left_hand_landmarks.landmark]).flatten() if results.left_hand_landmarks else np.zeros(21*3)
    rh = np.array([[res.x, res.y, res.z] for res in results.right_hand_landmarks.landmark]).flatten() if results.right_hand_landmarks else np.zeros(21*3)
    keypoints = np.concatenate([lh, rh])
    
    if len(keypoints) < 126:
        keypoints = np.pad(keypoints, (0, 126-len(keypoints)), 'constant')
    elif len(keypoints) > 126:
        keypoints = keypoints[:126]
    
    return keypoints

def calculate_hand_velocity(current_hands, previous_hands):
    """Calcula velocidad de movimiento de manos"""
    if previous_hands is None or current_hands is None:
        return 0.0
    
    try:
        curr = np.array(current_hands).reshape(-1, 3)
        prev = np.array(previous_hands).reshape(-1, 3)
        diff = curr - prev
        velocities = np.sqrt(np.sum(diff**2, axis=1))
        return float(np.mean(velocities))
    except:
        return 0.0

def calculate_hand_height(keypoints):
    """Calcula altura Y de muñeca más alta"""
    if keypoints is None:
        return None
    
    try:
        hands_data = np.array(keypoints[-126:]).reshape(-1, 3)
        left_hand = hands_data[:21]
        right_hand = hands_data[21:]
        
        heights = []
        if not np.all(left_hand[0] == 0):
            heights.append(left_hand[0][1])
        if not np.all(right_hand[0] == 0):
            heights.append(right_hand[0][1])
        
        return min(heights) if heights else None
    except:
        return None

def calculate_openness(keypoints):
    """Calcula apertura máxima de ambas manos"""
    if keypoints is None:
        return 0.0
    
    try:
        hands_data = np.array(keypoints[-126:]).reshape(-1, 3)
        left_hand = hands_data[:21]
        right_hand = hands_data[21:]
        
        def hand_openness(hand_points):
            if np.all(hand_points == 0):
                return 0.0
            fingertips = [4, 8, 12, 16, 20]
            palm_center = hand_points[0]
            distances = [np.linalg.norm(hand_points[tip_idx] - palm_center) 
                        for tip_idx in fingertips if tip_idx < len(hand_points)]
            return np.mean(distances) if distances else 0.0
        
        return max(hand_openness(left_hand), hand_openness(right_hand))
    except:
        return 0.0

def load_sign_model():
    """Carga modelo de señas"""
    try:
        model = load_model(MODEL_PATH)
        print(f"✅ Modelo cargado: {MODEL_PATH}")
        return model
    except Exception as e:
        print(f"❌ Error cargando modelo: {e}")
        return None

def load_word_labels():
    """Carga etiquetas de palabras"""
    try:
        with open(WORDS_JSON_PATH, 'r', encoding='utf-8') as f:
            data = json.load(f)
        words = data.get('word_ids', [])
        print(f"✅ Palabras cargadas: {words}")
        return words
    except Exception as e:
        print(f"❌ Error cargando palabras: {e}")
        return []

def scale_frame_params(fps):
    """Escala parámetros por FPS"""
    if fps <= 0:
        fps = FPS_DEFAULT
    
    scale_factor = fps / FPS_DEFAULT
    return {
        'persistence_frames': max(1, round(PERSISTENCE_FRAMES * scale_factor)),
        'm_switch': max(1, round(M_SWITCH * scale_factor)),
        'q_still': max(1, round(Q_STILL * scale_factor)),
        'z_neutral': max(1, round(Z_NEUTRAL * scale_factor)),
        'min_gap_between_events': max(1, round(MIN_GAP_BETWEEN_EVENTS * scale_factor)),
        'cooldown_frames': max(1, round(COOLDOWN_FRAMES * scale_factor))
    }

def process_video_frames(video_path):
    """Procesa frames del video extrayendo keypoints"""
    print(f"🎬 Procesando video: {video_path}")
    
    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        print(f"❌ No se pudo abrir el video")
        return []
    
    frames_keypoints = []
    frame_count = 0
    fps = cap.get(cv2.CAP_PROP_FPS)
    
    with mp_holistic.Holistic(min_detection_confidence=0.5, min_tracking_confidence=0.5) as holistic:
        while True:
            ret, frame = cap.read()
            if not ret:
                break
            
            image, results = mediapipe_detection(frame, holistic)
            keypoints = extract_keypoints_simplified(results)
            has_hands = there_hand(results)
            
            hands_raw = None
            if has_hands:
                lh = np.array([[res.x, res.y, res.z] for res in results.left_hand_landmarks.landmark]).flatten() if results.left_hand_landmarks else np.zeros(21*3)
                rh = np.array([[res.x, res.y, res.z] for res in results.right_hand_landmarks.landmark]).flatten() if results.right_hand_landmarks else np.zeros(21*3)
                hands_raw = np.concatenate([lh, rh])
            
            frames_keypoints.append({
                'frame_idx': frame_count,
                'keypoints': keypoints,
                'has_hands': has_hands,
                'hands_raw': hands_raw,
                'timestamp': frame_count / fps if fps > 0 else frame_count / 30.0
            })
            
            frame_count += 1
    
    cap.release()
    print(f"✅ Procesados {len(frames_keypoints)} frames")
    return frames_keypoints

def create_sequences_for_prediction(frames_keypoints, sequence_length=MODEL_FRAMES):
    """Crea secuencias deslizantes para predicción"""
    sequences = []
    frame_indices = []
    
    for i in range(len(frames_keypoints) - sequence_length + 1):
        hands_detected = sum(1 for j in range(i, i + sequence_length) 
                           if frames_keypoints[j]['has_hands'])
        
        if hands_detected >= sequence_length * MIN_HAND_RATIO:
            sequence = [frames_keypoints[j]['keypoints'] for j in range(i, i + sequence_length)]
            sequences.append(np.array(sequence))
            frame_indices.append(i)
    
    return np.array(sequences), frame_indices

class TemporalSmoother:
    """Suavizado temporal de probabilidades"""
    def __init__(self, num_classes, smoothing_type=SMOOTHING, alpha=EMA_ALPHA, window=SMOOTH_WINDOW):
        self.num_classes = num_classes
        self.smoothing_type = smoothing_type
        self.alpha = alpha
        
        if smoothing_type == "ema":
            self.smoothed_probs = None
        else:
            self.prob_history = [deque(maxlen=window) for _ in range(num_classes)]
        
    def update(self, probs):
        probs = np.array(probs)
        
        if self.smoothing_type == "ema":
            if self.smoothed_probs is None:
                self.smoothed_probs = probs.copy()
            else:
                self.smoothed_probs = self.alpha * probs + (1 - self.alpha) * self.smoothed_probs
            return self.smoothed_probs.copy()
        else:
            for i, prob in enumerate(probs):
                self.prob_history[i].append(prob)
            return np.array([np.mean(self.prob_history[i]) if len(self.prob_history[i]) > 0 else 0.0 
                           for i in range(self.num_classes)])

class StrictDetector:
    """Detector con verificación estricta multinivel"""
    def __init__(self, word_labels, frames_data, fps=30.0):
        self.word_labels = word_labels
        self.num_classes = len(word_labels)
        self.frames_data = frames_data
        self.scaled_params = scale_frame_params(fps)
        
        # Estado
        self.active_class = None
        self.active_since = None
        self.persistence_count = {}
        self.switch_count = {}
        self.neutral_count = 0
        self.still_count = 0
        
        # Físico
        self.previous_hands = None
        self.velocity_history = deque(maxlen=5)
        self.previous_height = None
        self.height_derivative = 0.0
        self.height_stable_count = 0
        self.current_openness = 0.0
        
        # Control
        self.cooldown_timers = {}
        self.last_emitted_frame_by_class = {}
        self.confirmed_detections = []
        self.completed_detections = []  # Lista para señas completadas (inicio, fin, palabra, confianza)
        self.detection_confidences = {}  # Almacena confianza por frame de detección
        self.smoother = TemporalSmoother(self.num_classes)
        
    def is_confusable_pair(self, class_a, class_b):
        word_a = self.word_labels[class_a]
        word_b = self.word_labels[class_b]
        return (word_a, word_b) in CONFUSABLE_PAIRS
        
    def get_class_threshold(self, class_idx):
        word = self.word_labels[class_idx]
        return PER_CLASS_TH.get(word, TH_HIGH)
    
    def get_switch_threshold(self, class_idx):
        word = self.word_labels[class_idx]
        return PER_CLASS_TH.get(word, TH_SWITCH)
    
    def get_pair_delta(self, class_a, class_b):
        word_a = self.word_labels[class_a]
        word_b = self.word_labels[class_b]
        delta = PAIR_DELTA_SWITCH.get((word_a, word_b)) or PAIR_DELTA_SWITCH.get((word_b, word_a))
        return delta if delta is not None else DELTA_SWITCH
    
    def check_geometric_gates(self, class_idx):
        word = self.word_labels[class_idx]
        if word not in CLASS_GATES:
            return True, "No gates"
        
        gates = CLASS_GATES[word]
        
        # Altura
        if self.previous_height is not None:
            if gates.get('min_y') is not None and self.previous_height < gates['min_y']:
                return False, f"Altura baja: {self.previous_height:.3f}"
            if gates.get('max_y') is not None and self.previous_height > gates['max_y']:
                return False, f"Altura alta: {self.previous_height:.3f}"
        
        # Dirección
        direction = gates.get('dir')
        if direction == "down" and self.height_derivative <= 0:
            return False, "Requiere bajar"
        elif direction == "up" and self.height_derivative >= 0:
            return False, "Requiere subir"
        
        # Apertura
        if gates.get('open_min') is not None and self.current_openness < gates['open_min']:
            return False, f"Apertura baja: {self.current_openness:.3f}"
        if gates.get('open_max') is not None and self.current_openness > gates['open_max']:
            return False, f"Apertura alta: {self.current_openness:.3f}"
        
        return True, "Gates OK"
    
    def check_nms_temporal(self, class_idx, frame_idx):
        if class_idx not in self.last_emitted_frame_by_class:
            return True, "Primera"
        
        last_frame = self.last_emitted_frame_by_class[class_idx]
        gap = frame_idx - last_frame
        min_gap = self.scaled_params['min_gap_between_events']
        
        if gap < min_gap:
            return False, f"Gap {gap} < {min_gap}"
        return True, f"Gap OK"
    
    def update_motion_state(self, frame_idx):
        if frame_idx >= len(self.frames_data):
            return False, True, True
            
        current_hands = self.frames_data[frame_idx].get('hands_raw')
        keypoints = self.frames_data[frame_idx].get('keypoints')
        
        # Velocidad
        velocity = calculate_hand_velocity(current_hands, self.previous_hands)
        self.velocity_history.append(velocity)
        self.previous_hands = current_hands
        is_moving = np.mean(self.velocity_history) > VEL_MIN if self.velocity_history else False
        
        # Altura
        current_height = calculate_hand_height(keypoints)
        if current_height is not None and self.previous_height is not None:
            self.height_derivative = current_height - self.previous_height
        else:
            self.height_derivative = 0.0
        self.previous_height = current_height
        
        # Apertura
        self.current_openness = calculate_openness(keypoints)
        
        # Estabilidad
        height_stable = abs(self.height_derivative) < HEIGHT_EPS
        if height_stable:
            self.height_stable_count += 1
        else:
            self.height_stable_count = 0
            
        overall_still = not is_moving and height_stable
        if overall_still:
            self.still_count += 1
        else:
            self.still_count = 0
            
        return is_moving, self.still_count >= self.scaled_params['q_still'], self.height_stable_count >= self.scaled_params['q_still']
    
    def is_neutral_state(self, frame_idx, smoothed_probs, is_moving):
        all_low_prob = np.all(smoothed_probs < TH_NEUTRAL)
        
        if frame_idx < len(self.frames_data):
            has_hands = self.frames_data[frame_idx].get('has_hands', False)
            openness = calculate_openness(self.frames_data[frame_idx].get('keypoints'))
            is_neutral_hand = not has_hands or openness < OPEN_THRESH
        else:
            is_neutral_hand = True
            
        return all_low_prob or is_neutral_hand or is_moving
    
    def process_frame(self, frame_idx, raw_probs):
        """Procesa frame con verificación estricta"""
        smoothed_probs = self.smoother.update(raw_probs)
        
        # Actualizar cooldowns
        for class_idx in list(self.cooldown_timers.keys()):
            self.cooldown_timers[class_idx] -= 1
            if self.cooldown_timers[class_idx] <= 0:
                del self.cooldown_timers[class_idx]
        
        # Estado físico
        is_moving, is_still, height_stable = self.update_motion_state(frame_idx)
        is_neutral = self.is_neutral_state(frame_idx, smoothed_probs, is_moving)
        
        # Contador neutral
        if is_neutral:
            self.neutral_count += 1
        else:
            self.neutral_count = 0
        
        candidate_class = np.argmax(smoothed_probs)
        candidate_prob = smoothed_probs[candidate_class]
        candidate_word = self.word_labels[candidate_class]
        
        if self.active_class is None:
            # Activación inicial
            if (candidate_class not in self.cooldown_timers and
                self.neutral_count >= self.scaled_params['z_neutral'] and
                is_still and
                candidate_prob >= self.get_class_threshold(candidate_class)):
                
                gates_ok, _ = self.check_geometric_gates(candidate_class)
                nms_ok, _ = self.check_nms_temporal(candidate_class, frame_idx)
                
                if gates_ok and nms_ok:
                    self.persistence_count[candidate_class] = self.persistence_count.get(candidate_class, 0) + 1
                    
                    if self.persistence_count[candidate_class] >= self.scaled_params['persistence_frames']:
                        # ACTIVAR
                        self.active_class = candidate_class
                        self.active_since = frame_idx
                        confidence_percent = candidate_prob * 100
                        self.confirmed_detections.append((frame_idx, candidate_word, confidence_percent))
                        self.detection_confidences[frame_idx] = confidence_percent
                        self.cooldown_timers[candidate_class] = self.scaled_params['cooldown_frames']
                        self.last_emitted_frame_by_class[candidate_class] = frame_idx
                        self.persistence_count[candidate_class] = 0
                        self.neutral_count = 0
                        print(f"🔒 Activado: '{candidate_word}' frame {frame_idx} (confianza: {confidence_percent:.1f}%)")
        else:
            # Cambio o mantenimiento
            current_prob = smoothed_probs[self.active_class]
            current_word = self.word_labels[self.active_class]
            
            if current_prob >= TH_LOW:
                self.switch_count = {}
            elif current_prob < TH_RELEASE:
                # Evaluar cambio
                neutrality_ok = self.neutral_count >= self.scaled_params['z_neutral']
                stillness_ok = is_still and height_stable
                threshold_ok = candidate_prob >= self.get_switch_threshold(candidate_class)
                margin_ok = (candidate_prob - current_prob) >= self.get_pair_delta(self.active_class, candidate_class)
                gates_ok, _ = self.check_geometric_gates(candidate_class)
                nms_ok, _ = self.check_nms_temporal(candidate_class, frame_idx)
                
                basic_ok = (neutrality_ok and stillness_ok and threshold_ok and 
                           margin_ok and gates_ok and nms_ok and
                           candidate_class != self.active_class and
                           candidate_class not in self.cooldown_timers)
                
                if basic_ok:
                    self.switch_count[candidate_class] = self.switch_count.get(candidate_class, 0) + 1
                    
                    if self.switch_count[candidate_class] >= self.scaled_params['m_switch']:
                        # CAMBIAR - completar seña anterior y empezar nueva
                        confidence_percent = candidate_prob * 100
                        current_confidence = self.detection_confidences.get(self.active_since, 0)
                        print(f"✅ Cambio: '{current_word}' ({current_confidence:.1f}%) → '{candidate_word}' ({confidence_percent:.1f}%) frame {frame_idx}")
                        # Registrar seña anterior como completada
                        self.completed_detections.append((self.active_since, frame_idx, current_word, current_confidence))
                        # Activar nueva seña
                        self.active_class = candidate_class
                        self.active_since = frame_idx
                        self.confirmed_detections.append((frame_idx, candidate_word, confidence_percent))
                        self.detection_confidences[frame_idx] = confidence_percent
                        self.cooldown_timers[candidate_class] = self.scaled_params['cooldown_frames']
                        self.last_emitted_frame_by_class[candidate_class] = frame_idx
                        self.switch_count = {}
                        self.neutral_count = 0
                else:
                    self.switch_count = {}
            
            # Liberar clase
            if current_prob < TH_RELEASE and self.neutral_count >= self.scaled_params['z_neutral']:
                current_confidence = self.detection_confidences.get(self.active_since, 0)
                print(f"🔓 Liberado: '{current_word}' ({current_confidence:.1f}%) - Seña completada")
                # Registrar seña completada con inicio y fin
                self.completed_detections.append((self.active_since, frame_idx, current_word, current_confidence))
                self.active_class = None
                self.switch_count = {}
    
    def get_detections(self):
        return self.confirmed_detections
    
    def get_completed_detections(self):
        """Retorna señas completadas con tiempo de inicio y fin"""
        return self.completed_detections
    
    def finalize(self, final_frame_idx):
        """Finaliza detección - completa señas activas al final del video"""
        if self.active_class is not None:
            current_word = self.word_labels[self.active_class]
            current_confidence = self.detection_confidences.get(self.active_since, 0)
            print(f"🔓 Finalizando seña activa: '{current_word}' ({current_confidence:.1f}%) al final del video")
            self.completed_detections.append((self.active_since, final_frame_idx, current_word, current_confidence))
            self.active_class = None

def predict_signs_strict(model, sequences, frame_indices, word_labels, frames_data, fps=30.0):
    """Predice con verificación estricta"""
    if len(sequences) == 0:
        return [], []
    
    print(f"🔮 Prediciendo {len(sequences)} secuencias...")
    predictions = model.predict(sequences)
    detector = StrictDetector(word_labels, frames_data, fps)
    
    for i, prediction in enumerate(predictions):
        frame_idx = frame_indices[i] + MODEL_FRAMES // 2
        detector.process_frame(frame_idx, prediction)
    
    # Finalizar detección para completar señas activas
    if frames_data:
        final_frame = len(frames_data) - 1
        detector.finalize(final_frame)
    
    detections = detector.get_detections()
    completed_detections = detector.get_completed_detections()
    print(f"🎉 {len(detections)} señas detectadas, {len(completed_detections)} completadas")
    return detections, completed_detections

def evaluate_model_with_frames(input_path, threshold=0.8):
    """
    Función principal - detecta señas con verificación estricta multinivel
    """
    print(f"🚀 Evaluación estricta: {input_path}")
    
    global TH_HIGH
    if threshold != 0.8:
        TH_HIGH = threshold
    
    if not DEPENDENCIES_AVAILABLE or not LOCAL_IMPORTS_AVAILABLE:
        print("❌ Dependencias no disponibles")
        return []
    
    # Cargar modelo y palabras
    model = load_sign_model()
    if model is None:
        return []
    
    word_labels = load_word_labels()
    if not word_labels:
        return []
    
    # Procesar video
    frames_keypoints = process_video_frames(input_path)
    if not frames_keypoints:
        return []
    
    sequences, frame_indices = create_sequences_for_prediction(frames_keypoints)
    if len(sequences) == 0:
        print("⚠️ No hay secuencias válidas")
        return []
    
    # Obtener FPS
    try:
        cap = cv2.VideoCapture(input_path)
        fps = cap.get(cv2.CAP_PROP_FPS)
        cap.release()
        if fps <= 0:
            fps = FPS_DEFAULT
    except:
        fps = FPS_DEFAULT
    
    # Predecir con verificación estricta
    detections, completed_detections = predict_signs_strict(model, sequences, frame_indices, word_labels, frames_keypoints, fps)
    
    # Formatear salida - usar detecciones completadas para sincronización
    final_detections = []
    
    print("📊 Detecciones completadas (audio/subtítulo se reproducirá AL DETECTAR cada seña):")
    for detection_data in completed_detections:
        if len(detection_data) == 4:
            start_frame, end_frame, word, confidence = detection_data
        else:
            # Compatibilidad con formato anterior
            start_frame, end_frame, word = detection_data
            confidence = 0.0
        
        # Usar el frame de inicio para sincronizar audio/subtítulo cuando la seña SE DETECTA
        final_detections.append((start_frame, word, confidence))
        start_time = start_frame / fps
        end_time = end_frame / fps
        duration = end_time - start_time
        print(f"✅ '{word}' ({confidence:.1f}%): DETECTADA en {start_time:.2f}s → termina {end_time:.2f}s (duración: {duration:.2f}s) 🎵📺")
    
    print(f"🎉 Completado: {len(final_detections)} señas sincronizadas")
    return final_detections

def test_model_detection(video_path="test_video.mp4"):
    """Función de prueba"""
    if not os.path.exists(video_path):
        print(f"⚠️ Video no encontrado: {video_path}")
        return
    
    detections = evaluate_model_with_frames(video_path, threshold=0.8)
    print(f"\n📊 Total: {len(detections)} detecciones")
    for detection_data in detections:
        if len(detection_data) == 3:
            frame_idx, word, confidence = detection_data
            timestamp = frame_idx / 30.0
            print(f"  • Frame {frame_idx} ({timestamp:.2f}s): '{word}' ({confidence:.1f}%)")
        else:
            # Compatibilidad con formato anterior
            frame_idx, word = detection_data
            timestamp = frame_idx / 30.0
            print(f"  • Frame {frame_idx} ({timestamp:.2f}s): '{word}'")

if __name__ == "__main__":
    print("🔍 Verificando archivos...")
    if os.path.exists(MODEL_PATH):
        print(f"✅ Modelo: {MODEL_PATH}")
    else:
        print(f"❌ Modelo no encontrado: {MODEL_PATH}")
    
    if os.path.exists(WORDS_JSON_PATH):
        print(f"✅ Palabras: {WORDS_JSON_PATH}")
    else:
        print(f"❌ Palabras no encontradas: {WORDS_JSON_PATH}")
    
    print("🎯 Sistema estricto listo")