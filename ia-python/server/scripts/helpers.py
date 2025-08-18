import json, os, cv2, numpy as np, pandas as pd
from typing import NamedTuple, List
from mediapipe.python.solutions.holistic import FACEMESH_CONTOURS, POSE_CONNECTIONS, HAND_CONNECTIONS
from mediapipe.python.solutions.drawing_utils import draw_landmarks, DrawingSpec
from .constants import *

def mediapipe_detection(image, model):
    """Convierte BGR a RGB y procesa con MediaPipe Holistic."""
    image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
    image.flags.writeable = False
    results = model.process(image)
    image.flags.writeable = True
    image = cv2.cvtColor(image, cv2.COLOR_RGB2BGR)
    return image, results

def create_folder(path):
    """Crea carpeta si no existe."""
    if not os.path.exists(path):
        os.makedirs(path)

def there_hand(results: NamedTuple) -> bool:
    """Verifica si hay manos detectadas."""
    return results.left_hand_landmarks is not None or results.right_hand_landmarks is not None

def draw_keypoints(image, results):
    """Dibuja keypoints de face, pose y manos."""
    # Face
    if results.face_landmarks:
        draw_landmarks(image, results.face_landmarks, FACEMESH_CONTOURS, 
                      DrawingSpec(color=(80, 110, 10), thickness=1, circle_radius=1),
                      DrawingSpec(color=(80, 256, 121), thickness=1, circle_radius=1))
    
    # Pose
    if results.pose_landmarks:
        draw_landmarks(image, results.pose_landmarks, POSE_CONNECTIONS,
                      DrawingSpec(color=(80, 22, 10), thickness=2, circle_radius=4),
                      DrawingSpec(color=(80, 44, 121), thickness=2, circle_radius=2))
    
    # Left hand
    if results.left_hand_landmarks:
        draw_landmarks(image, results.left_hand_landmarks, HAND_CONNECTIONS,
                      DrawingSpec(color=(121, 22, 76), thickness=2, circle_radius=4),
                      DrawingSpec(color=(121, 44, 250), thickness=2, circle_radius=2))
    
    # Right hand
    if results.right_hand_landmarks:
        draw_landmarks(image, results.right_hand_landmarks, HAND_CONNECTIONS,
                      DrawingSpec(color=(245, 117, 66), thickness=2, circle_radius=4),
                      DrawingSpec(color=(245, 66, 230), thickness=2, circle_radius=2))

def save_frames(frames, output_folder):
    """Guarda frames como JPGs."""
    create_folder(output_folder)
    for i, frame in enumerate(frames):
        cv2.imwrite(os.path.join(output_folder, f"frame_{i:04d}.jpg"), frame)

def extract_keypoints(results):
    """Extrae y concatena keypoints de left_hand y right_hand para el modelo."""
    lh = np.array([[res.x, res.y, res.z] for res in results.left_hand_landmarks.landmark]).flatten() if results.left_hand_landmarks else np.zeros(21*3)
    rh = np.array([[res.x, res.y, res.z] for res in results.right_hand_landmarks.landmark]).flatten() if results.right_hand_landmarks else np.zeros(21*3)
    
    return np.concatenate([lh, rh]).astype(np.float32)

def disambiguate_signs_by_segment(top1_word: str, segment_results: List[NamedTuple]) -> (str, dict):
    """
    Desambigua entre "hola" y "papá" analizando todo el segmento de frames.
    Retorna la palabra decidida y un diccionario con las métricas calculadas.
    """
    # --- 1. Cálculo de Features Geométricas ---
    face_height, face_width = -1, -1
    heights_above_eyes, mouth_distances, final_phase_mouth_distances = [], [], []

    # Normalización (se busca el primer frame con cara para obtener dimensiones)
    for r in segment_results:
        if r.face_landmarks:
            p_top = r.face_landmarks.landmark[10]  # Frente
            p_bottom = r.face_landmarks.landmark[152] # Barbilla
            p_left = r.face_landmarks.landmark[127] # Mejilla izq
            p_right = r.face_landmarks.landmark[356] # Mejilla der
            face_height = np.linalg.norm([p_top.x - p_bottom.x, p_top.y - p_bottom.y])
            face_width = np.linalg.norm([p_left.x - p_right.x, p_left.y - p_right.y])
            break
    
    if face_height <= 0 or face_width <= 0:
        return top1_word, {"error": "No face landmarks for normalization"}

    # Procesar cada frame del segmento
    segment_len = len(segment_results)
    last_third_start_index = int(segment_len * 2 / 3)

    for i, r in enumerate(segment_results):
        if not (r.left_hand_landmarks or r.right_hand_landmarks) or not r.face_landmarks:
            continue
        
        hand = r.right_hand_landmarks or r.left_hand_landmarks
        wrist = hand.landmark[0]
        eyebrow = r.face_landmarks.landmark[105] # Ceja izq
        lip = r.face_landmarks.landmark[13] # Labio superior

        # Altura del ápice (normalizada)
        heights_above_eyes.append((eyebrow.y - wrist.y) / face_height)
        
        # Distancia a la boca (normalizada)
        dist = np.linalg.norm([wrist.x - lip.x, wrist.y - lip.y]) / face_width
        mouth_distances.append(dist)
        
        # Distancia en la fase final
        if i >= last_third_start_index:
            final_phase_mouth_distances.append(dist)

    # --- 2. Calcular Scores ---
    # Apex score: media de los K frames más altos
    k = min(APEX_K_FRAMES, len(heights_above_eyes))
    apex_score = np.mean(sorted(heights_above_eyes, reverse=True)[:k]) if k > 0 else 0
    
    # Dwell score: % de frames cerca de la boca
    dwell_score = np.mean([1 for d in mouth_distances if d < 0.2]) if mouth_distances else 0
    
    # Fase final: ¿termina cerca de la boca?
    final_phase_score = np.mean(final_phase_mouth_distances) if final_phase_mouth_distances else 1.0

    metrics = {"apex_score": apex_score, "dwell_score": dwell_score, "final_phase_dist": final_phase_score}

    # --- 3. Política de Decisión ---
    passes_apex = apex_score >= APEX_HEIGHT_THRESHOLD
    passes_dwell = dwell_score >= MOUTH_DWELL_THRESHOLD

    decision = top1_word
    if passes_apex and not passes_dwell:
        decision = "hola"
    elif passes_dwell and not passes_apex:
        decision = "papá"
    elif passes_apex and passes_dwell:
        # Desempate: si la fase final está lejos de la boca, es más probable que sea "hola"
        decision = "hola" if final_phase_score > 0.25 else "papá"
    
    # Log final
    logging.info(
        f"Desambiguación: apex={apex_score:.2f} (th={APEX_HEIGHT_THRESHOLD}), "
        f"dwell={dwell_score:.2f} (th={MOUTH_DWELL_THRESHOLD}), "
        f"final_phase_dist={final_phase_score:.2f}, decision={decision}"
    )
    
    return decision, metrics
