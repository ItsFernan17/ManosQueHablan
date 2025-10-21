# app/evaluator.py
import os
import cv2
import numpy as np
import mediapipe as mp
from typing import List, Dict, Any, Tuple
from tensorflow.keras.models import load_model
import unicodedata

# --- CONFIG ---
LENGTH_KEYPOINTS = 126
MODEL_FRAMES = 30
MIN_LENGTH_FRAMES = 10

FONT = cv2.FONT_HERSHEY_SIMPLEX
FONT_POS = (10, 30)
FONT_SIZE = 1

# Rutas relativas al proyecto
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_PATH = os.path.join(BASE_DIR, "app", "models", "MargaritaLSG1.h5")
LABELS_PATH = os.path.join(BASE_DIR, "app", "models", "labels.txt")

# --- WORDS desde labels.txt ---
def load_words_from_labels(path: str) -> List[str]:
    if not os.path.isfile(path):
        raise FileNotFoundError(f"No existe labels.txt en {path}")
    words = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            w = line.strip()
            if w:
                words.append(unicodedata.normalize("NFC", w))
    return words

WORDS = load_words_from_labels(LABELS_PATH)

# (opcional) imprime el mapeo índice->label una sola vez
PRINT_LABELS_MAPPING = False
if PRINT_LABELS_MAPPING:
    print({i: w for i, w in enumerate(WORDS)})

# --- Carga de modelo en caliente (singleton en memoria) ---
_MODEL = None
def get_model():
    global _MODEL
    if _MODEL is None:
        _MODEL = load_model(MODEL_PATH)
    return _MODEL

# --- Helpers de MediaPipe ---
def mediapipe_detection(image_bgr, holistic):
    image = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2RGB)
    image.flags.writeable = False
    return holistic.process(image)

def there_hand(results):
    return (results.left_hand_landmarks is not None) or (results.right_hand_landmarks is not None)

def extract_keypoints(results):
    lh = np.array([[res.x, res.y, res.z] for res in results.left_hand_landmarks.landmark]) if results.left_hand_landmarks else np.zeros((21, 3))
    rh = np.array([[res.x, res.y, res.z] for res in results.right_hand_landmarks.landmark]) if results.right_hand_landmarks else np.zeros((21, 3))
    return np.concatenate([lh, rh]).flatten()

def normalize_keypoints(seq: List[np.ndarray], target_length: int = MODEL_FRAMES):
    current_length = len(seq)
    if current_length == target_length:
        return seq
    elif current_length > target_length:
        step = current_length / target_length
        indices = np.arange(0, current_length, step).astype(int)[:target_length]
        return [seq[i] for i in indices]
    else:
        indices = np.linspace(0, current_length - 1, target_length)
        interpolated = []
        for i in indices:
            lower = int(np.floor(i))
            upper = int(np.ceil(i))
            weight = i - lower
            if lower == upper:
                interpolated.append(seq[lower])
            else:
                interpolated.append(((1 - weight) * np.array(seq[lower]) + weight * np.array(seq[upper])))
        return interpolated

# --- Suavizado ligero de keypoints (reducción de jitter) ---
def smooth_seq(seq: List[np.ndarray], k: int = 3) -> List[np.ndarray]:
    """Promedio móvil ventana k sobre la secuencia de keypoints (T, 126)."""
    if len(seq) < 3 or k <= 1:
        return seq
    arr = np.stack(seq, axis=0)  # (T, 126)
    pad = k // 2
    arr_pad = np.pad(arr, ((pad, pad), (0, 0)), mode="edge")
    out = []
    for i in range(len(seq)):
        window = arr_pad[i:i+k]
        out.append(window.mean(axis=0))
    return out

def draw_keypoints(image, results):
    mp_drawing = mp.solutions.drawing_utils
    if results.left_hand_landmarks:
        mp_drawing.draw_landmarks(image, results.left_hand_landmarks, mp.solutions.holistic.HAND_CONNECTIONS)
    if results.right_hand_landmarks:
        mp_drawing.draw_landmarks(image, results.right_hand_landmarks, mp.solutions.holistic.HAND_CONNECTIONS)

def evaluate_video_to_file(
    video_path: str,
    out_mp4_path: str,
    threshold: float = 0.75,
    margin_frame: int = 1,
    delay_frames: int = 3,
    draw=True,
    gracias_threshold: float = 0.60,
    cantar_threshold: float = 0.65,
    dolor_threshold: float = 0.60,
    donde_threshold: float = 0.70,
    desconectar_threshold: float = 0.75,
    gracias_gap_min: float = 0.08,
    cantar_gap_min: float = 0.065,
    dolor_gap_min: float = 0.06,
    donde_gap_min: float = 0.08,
    desconectar_gap_min: float = 0.08,
) -> Tuple[List[Dict[str, Any]], Dict[str, Any]]:
    if not os.path.isfile(video_path):
        raise FileNotFoundError(f"No existe el archivo: {video_path}")

    model = get_model()

    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        raise RuntimeError("No se pudo abrir el video.")

    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    width  = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH)  or 640)
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT) or 480)

    fourcc = cv2.VideoWriter_fourcc(*"mp4v")
    writer = cv2.VideoWriter(out_mp4_path, fourcc, fps, (width, height))

    detections = []
    kp_seq, count_frame, fix_frames = [], 0, 0
    recording = False
    pred_label = ""
    pred_prob = 0.0
    overlay_cooldown = 0

    # Sensibilidad un poco mayor para entornos normales/difusos
    with mp.solutions.holistic.Holistic(
        min_detection_confidence=0.6,   # antes 0.5
        min_tracking_confidence=0.5
    ) as holistic:

        frame_idx = 0
        while True:
            ret, frame = cap.read()
            if not ret:
                break

            results = mediapipe_detection(frame, holistic)

            if there_hand(results) or recording:
                recording = False
                count_frame += 1
                if count_frame > margin_frame:
                    kp_frame = extract_keypoints(results)
                    kp_seq.append(kp_frame)
            else:
                if count_frame >= MIN_LENGTH_FRAMES + margin_frame:
                    fix_frames += 1
                    if fix_frames < delay_frames:
                        recording = True
                    else:
                        # --- Suaviza la secuencia cruda antes de evaluar ---
                        kp_seq = smooth_seq(kp_seq, k=3)

                        # --- Dual-pass: con recorte de cola y sin recorte ---
                        cut = margin_frame + delay_frames
                        seq_trim = kp_seq[: -cut] if (cut > 0 and len(kp_seq) > cut) else kp_seq
                        seq_full = kp_seq  # sin recorte final

                        def _predict_seq(seq):
                            kp_norm = normalize_keypoints(seq, target_length=MODEL_FRAMES)
                            x = np.expand_dims(np.array(kp_norm, dtype=np.float32), axis=0)
                            return model.predict(x, verbose=0)[0]

                        pred_trim = _predict_seq(seq_trim)
                        pred_full = _predict_seq(seq_full)

                        # Elige la predicción más confiable
                        prediction = pred_full if float(pred_full.max()) > float(pred_trim.max()) else pred_trim

                        max_idx = int(np.argmax(prediction))
                        max_prob = float(prediction[max_idx])

                        if 0 <= max_idx < len(WORDS):
                            max_label = WORDS[max_idx]
                            lbl = max_label.lower()

                            # --- Umbral por clase ---
                            if lbl == "gracias":
                                th = gracias_threshold
                            elif lbl == "cantar":
                                th = cantar_threshold
                            elif lbl == "dolor":
                                th = dolor_threshold
                            elif lbl == "donde":
                                th = donde_threshold
                            elif lbl == "desconectar":
                                th = desconectar_threshold
                            else:
                                th = threshold

                            # --- Gap por clase (diferencia con la segunda mejor prob.) ---
                            accept = False
                            msg_gap = ""
                            if lbl in ("gracias", "cantar", "dolor", "donde", "desconectar"):
                                top2 = float(np.partition(prediction, -2)[-2])
                                gap = max_prob - top2
                                if lbl == "gracias":
                                    gap_min = gracias_gap_min
                                elif lbl == "cantar":
                                    gap_min = cantar_gap_min
                                elif lbl == "dolor":
                                    gap_min = dolor_gap_min
                                elif lbl == "donde":
                                    gap_min = donde_gap_min
                                else:  # "desconectar"
                                    gap_min = desconectar_gap_min
                                accept = (max_prob >= th) and (gap >= gap_min)
                                msg_gap = f" gap={gap:.2f}"
                            else:
                                accept = (max_prob >= th)

                            if accept:
                                pred_label = max_label
                                pred_prob = max_prob * 100.0
                                overlay_cooldown = int(fps * 0.7)
                                print(f"✅ ACEPTADO: {pred_label} ({pred_prob:.2f}%) con th={th:.2f}{msg_gap}")

                                end_frame = frame_idx
                                dur_frames = len(kp_seq)
                                start_frame = max(0, end_frame - dur_frames)
                                detections.append({
                                    "label": pred_label,
                                    "prob": round(pred_prob, 2),
                                    "start_frame": start_frame,
                                    "end_frame": end_frame,
                                    "start_time": round(start_frame / fps, 3),
                                    "end_time": round(end_frame / fps, 3),
                                })
                            else:
                                if lbl in ("gracias", "cantar", "dolor", "donde", "desconectar"):
                                    top2 = float(np.partition(prediction, -2)[-2])
                                    gap = max_prob - top2
                                    if max_prob < th:
                                        print(f"❌ Rechazado (máx {max_label}={max_prob:.2f}<th={th:.2f}, gap={gap:.2f}≥{gap_min:.2f})")
                                    else:
                                        print(f"❌ Rechazado (máx {max_label}={max_prob:.2f}≥th={th:.2f}, gap={gap:.2f}<{gap_min:.2f})")
                                else:
                                    print(f"❌ Rechazado (máx {max_label}={max_prob:.2f}, th={th:.2f})")

                        # reset de acumuladores
                        kp_seq, count_frame, fix_frames, recording = [], 0, 0, False
                else:
                    kp_seq, count_frame, fix_frames, recording = [], 0, 0, False

            if draw and overlay_cooldown > 0:
                cv2.rectangle(frame, (0, 0), (max(300, width // 2), 60), (245, 117, 16), -1)
                cv2.putText(frame, f'{pred_label.upper()} ({pred_prob:.1f}%)',
                            (20, 40), FONT, FONT_SIZE, (255, 255, 255), 2)
                overlay_cooldown -= 1

            if draw:
                try:
                    draw_keypoints(frame, results)
                except Exception:
                    pass

            writer.write(frame)
            frame_idx += 1

    cap.release()
    writer.release()

    meta = {"fps": fps, "width": width, "height": height}
    return detections, meta
