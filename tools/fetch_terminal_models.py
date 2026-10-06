#!/usr/bin/env python3
"""Download immutable upstream models and verify Git LFS SHA-256 values."""
import hashlib,pathlib,urllib.request
ROOT=pathlib.Path(__file__).resolve().parents[1]
COMMIT="47534e27c9851bb1128ccc0102f1145e27f23f98"
MODELS=[
 ("models/face_detection_yunet/face_detection_yunet_2023mar.onnx","yunet.onnx","8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4"),
 ("models/face_recognition_sface/face_recognition_sface_2021dec.onnx","sface.onnx","0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79"),
]
target=ROOT/"terminal/src/main/assets"
target.mkdir(parents=True,exist_ok=True)
for upstream,name,digest in MODELS:
 out=target/name
 if not out.exists() or hashlib.sha256(out.read_bytes()).hexdigest()!=digest:
  urllib.request.urlretrieve(f"https://media.githubusercontent.com/media/opencv/opencv_zoo/{COMMIT}/{upstream}",out)
 assert hashlib.sha256(out.read_bytes()).hexdigest()==digest,f"Model integrity mismatch: {name}"
 print(name,out.stat().st_size,"SHA256 verified")
