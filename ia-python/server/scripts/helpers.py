import json
import os
import cv2
import numpy as np
import pandas as pd
from typing import NamedTuple
from mediapipe.python.solutions.holistic import FACEMESH_CONTOURS, POSE_CONNECTIONS, HAND_CONNECTIONS
from mediapipe.python.solutions.drawing_utils import draw_landmarks, DrawingSpec

from scripts.constants import *

def mediapipe_detection(image, model):
    '''
    Procesa una imagen con el modelo de MediaPipe Holistic.
    Retorna la imagen procesada y los resultados.
    '''
    image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
    image.flags.writeable = False
    results = model.process(image)
    image.flags.writeable = True
    image = cv2.cvtColor(image, cv2.COLOR_RGB2BGR)
    return image, results

def create_folder(path):
    '''
    Crea una carpeta si no existe.
    '''
    if not os.path.exists(path):
        os.makedirs(path)

def there_hand(results: NamedTuple) -> bool:
    '''
    Verifica si hay manos detectadas en los resultados de MediaPipe.
    '''
    return results.left_hand_landmarks or results.right_hand_landmarks

def get_word_ids(path):
    '''
    Carga los IDs de palabras desde un archivo JSON.
    '''
    with open(path, 'r') as json_file:
        data = json.load(json_file)
        return data.get('word_ids')

# ---------------------
# CAPTURA Y DIBUJO
# ---------------------

def draw_keypoints(image, results):
    '''
    Dibuja los keypoints detectados en la imagen.
    '''
    draw_landmarks(
        image,
        results.face_landmarks,
        FACEMESH_CONTOURS,
        DrawingSpec(color=(80, 110, 10), thickness=1, circle_radius=1),
        DrawingSpec(color=(80, 256, 121), thickness=1, circle_radius=1),
    )
    draw_landmarks(
        image,
        results.pose_landmarks,
        POSE_CONNECTIONS,
        DrawingSpec(color=(80, 22, 10), thickness=2, circle_radius=4),
        DrawingSpec(color=(80, 44, 121), thickness=2, circle_radius=2),
    )
    draw_landmarks(
        image,
        results.left_hand_landmarks,
        HAND_CONNECTIONS,
        DrawingSpec(color=(121, 22, 76), thickness=2, circle_radius=4),
        DrawingSpec(color=(121, 44, 250), thickness=2, circle_radius=2),
    )
    draw_landmarks(
        image,
        results.right_hand_landmarks,
        HAND_CONNECTIONS,
        DrawingSpec(color=(245, 117, 66), thickness=2, circle_radius=4),
        DrawingSpec(color=(245, 66, 230), thickness=2, circle_radius=2),
    )

def save_frames(frames, output_folder):
    '''
    Guarda una secuencia de frames como imágenes en una carpeta.
    '''
    for num_frame, frame in enumerate(frames):
        frame_path = os.path.join(output_folder, f"{num_frame + 1}.jpg")
        cv2.imwrite(frame_path, cv2.cvtColor(frame, cv2.COLOR_BGR2BGRA))

# ---------------------
# KEYPOINTS
# ---------------------

def extract_keypoints(results):
    '''
    Extrae los keypoints de rostro, cuerpo y manos de los resultados.
    '''
    pose = np.array([[res.x, res.y, res.z, res.visibility] for res in results.pose_landmarks.landmark]).flatten() if results.pose_landmarks else np.zeros(33*4)
    face = np.array([[res.x, res.y, res.z] for res in results.face_landmarks.landmark]).flatten() if results.face_landmarks else np.zeros(468*3)
    lh = np.array([[res.x, res.y, res.z] for res in results.left_hand_landmarks.landmark]).flatten() if results.left_hand_landmarks else np.zeros(21*3)
    rh = np.array([[res.x, res.y, res.z] for res in results.right_hand_landmarks.landmark]).flatten() if results.right_hand_landmarks else np.zeros(21*3)
    return np.concatenate([pose, face, lh, rh])

def get_keypoints(model, sample_path):
    '''
    Retorna la secuencia de keypoints extraídos de cada imagen en una carpeta.
    '''
    kp_seq = np.array([])
    for img_name in sorted(os.listdir(sample_path)):
        img_path = os.path.join(sample_path, img_name)
        frame = cv2.imread(img_path)
        _, results = mediapipe_detection(frame, model)
        kp_frame = extract_keypoints(results)
        kp_seq = np.concatenate([kp_seq, [kp_frame]] if kp_seq.size > 0 else [[kp_frame]])
    return kp_seq

def insert_keypoints_sequence(df, n_sample: int, kp_seq):
    '''
    Inserta la secuencia de keypoints de una muestra al DataFrame.
    '''
    for frame, keypoints in enumerate(kp_seq):
        data = {'sample': n_sample, 'frame': frame + 1, 'keypoints': [keypoints]}
        df_keypoints = pd.DataFrame(data)
        df = pd.concat([df, df_keypoints])
    return df

# ---------------------
# CARGA DE DATOS PARA ENTRENAMIENTO
# ---------------------

def get_sequences_and_labels(words_id):
    '''
    Carga todas las secuencias de entrenamiento y etiquetas desde los archivos HDF5.
    '''
    sequences, labels = [], []
    for word_index, word_id in enumerate(words_id):
        hdf_path = os.path.join(KEYPOINTS_PATH, f"{word_id}.h5")
        data = pd.read_hdf(hdf_path, key='data')
        for _, df_sample in data.groupby('sample'):
            seq_keypoints = [fila['keypoints'] for _, fila in df_sample.iterrows()]
            sequences.append(seq_keypoints)
            labels.append(word_index)
    return sequences, labels
