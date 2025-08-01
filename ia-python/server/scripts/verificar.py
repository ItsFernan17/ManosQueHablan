import os
import json
import cv2
import numpy as np
from keras.models import load_model
from keras.layers import LSTM, Dense, Dropout
from keras.models import Sequential
from keras.regularizers import l2
from mediapipe.python.solutions.holistic import Holistic
from scripts.constants import (
    MODEL_PATH, WORDS_JSON_PATH, MODEL_FRAMES,
    LENGTH_KEYPOINTS, MIN_LENGTH_FRAMES,
)

# -------------------- CARGA DE CLASES --------------------
with open(WORDS_JSON_PATH, "r") as f:
    WORDS = json.load(f)["word_ids"]  # ✅ CORREGIDO

# -------------------- HELPERS --------------------
def mediapipe_detection(image, model):
    image_rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
    image_rgb.flags.writeable = False
    return model.process(image_rgb)

def there_hand(results):
    return results.left_hand_landmarks or results.right_hand_landmarks

def extract_keypoints(results):
    lh = np.array([[res.x, res.y, res.z] for res in results.left_hand_landmarks.landmark]) if results.left_hand_landmarks else np.zeros((21, 3))
    rh = np.array([[res.x, res.y, res.z] for res in results.right_hand_landmarks.landmark]) if results.right_hand_landmarks else np.zeros((21, 3))
    return np.concatenate([lh, rh]).flatten()

def normalize_keypoints(keypoints, target_length=MODEL_FRAMES):
    current_length = len(keypoints)
    if current_length == target_length:
        return keypoints
    elif current_length > target_length:
        step = current_length / target_length
        indices = np.arange(0, current_length, step).astype(int)[:target_length]
        return [keypoints[i] for i in indices]
    else:
        indices = np.linspace(0, current_length - 1, target_length)
        interpolated = []
        for i in indices:
            lower = int(np.floor(i))
            upper = int(np.ceil(i))
            weight = i - lower
            if lower == upper:
                interpolated.append(keypoints[lower])
            else:
                interpolated.append(((1 - weight) * np.array(keypoints[lower]) + weight * np.array(keypoints[upper])).tolist())
        return interpolated

# -------------------- EVALUACIÓN --------------------
def evaluate_model_with_frames(src, threshold=0.8, margin_frame=1, delay_frames=3, debug=True):
    print("🟡 Cargando modelo...")
    model = load_model(MODEL_PATH)

    print(f"🎥 Analizando video: {src}")
    cap = cv2.VideoCapture(src)
    if not cap.isOpened():
        print("❌ No se pudo abrir el video.")
        return []

    resultados = []
    kp_seq, count_frame, fix_frames = [], 0, 0
    recording = False
    frame_index = 0

    with Holistic(min_detection_confidence=0.5, min_tracking_confidence=0.5) as holistic:
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
                        continue

                    kp_seq = kp_seq[: -(margin_frame + delay_frames)]
                    kp_normalized = normalize_keypoints(kp_seq, target_length=MODEL_FRAMES)
                    input_data = np.expand_dims(np.array(kp_normalized), axis=0)

                    prediction = model.predict(input_data, verbose=0)[0]
                    max_idx = np.argmax(prediction)
                    max_prob = prediction[max_idx]

                    if max_prob > threshold:
                        if 0 <= max_idx < len(WORDS):
                            label = WORDS[max_idx]
                        else:
                            print(f"⚠️ Índice inválido: {max_idx}")
                            label = "desconocido"
                        resultados.append((frame_index, label))
                        if debug:
                            print(f"✅ {label} ({max_prob*100:.1f}%) en frame {frame_index}")

                kp_seq = []
                count_frame = 0
                fix_frames = 0
                recording = False

            frame_index += 1

        cap.release()

    return resultados

# -------------------- PRUEBA --------------------
if __name__ == "__main__":
    resultados = evaluate_model_with_frames("video_android.mp4", threshold=0.75)
    for frame, palabra in resultados:
        print(f"🧠 Palabra detectada: {palabra} en frame {frame}")
