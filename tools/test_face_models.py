#!/usr/bin/env python3
"""Real inference smoke test using public OpenCV sample photographs."""
import pathlib,urllib.request,cv2,numpy as np
ROOT=pathlib.Path(__file__).resolve().parents[1]
assets=ROOT/'terminal/src/main/assets'
det=cv2.FaceDetectorYN.create(str(assets/'yunet.onnx'),'',(640,480),.9,.3,5000)
rec=cv2.FaceRecognizerSF.create(str(assets/'sface.onnx'),'')
def feature(name):
 path=ROOT/'build'/name
 path.parent.mkdir(exist_ok=True)
 urllib.request.urlretrieve('https://raw.githubusercontent.com/opencv/opencv/4.10.0/samples/data/'+name,path)
 image=cv2.imread(str(path))
 assert image is not None
 det.setInputSize((image.shape[1],image.shape[0]))
 _,faces=det.detect(image)
 assert faces is not None and len(faces)==1,(name,faces)
 return rec.feature(rec.alignCrop(image,faces[0]))
a=feature('lena.jpg'); b=feature('messi5.jpg')
same=rec.match(a,a,cv2.FaceRecognizerSF_FR_COSINE)
different=rec.match(a,b,cv2.FaceRecognizerSF_FR_COSINE)
assert a.size==128 and np.isfinite(a).all()
assert same>=.99,(same,different)
assert different<.5,(same,different)
print('Real face-model inference passed; same face',same,'different face',different)
