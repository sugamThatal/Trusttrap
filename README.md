# TrustTap – Image Analysis Backend

TrustTap is an accessibility-first misinformation detection backend with an Android frontend. The backend analyzes an image using multiple verification modules and returns an explainable trust score for the mobile application.

---

# Backend Setup

## macOS (Anaconda)

```bash
cd trusttap

/opt/anaconda3/bin/python -m pip install -r requirements.txt
```

The first installation may take a few minutes because PyTorch is large. On Apple Silicon, PyTorch will automatically use the MPS backend if available, otherwise it falls back to CPU.

## Windows (Python Virtual Environment)

```bash
python -m venv venv

venv\Scripts\activate

pip install -r requirements.txt
```

---

# Run the Backend

### macOS

```bash
/opt/anaconda3/bin/python -m uvicorn app.main:app --reload --port 8000
```

### Windows

```bash
uvicorn app.main:app --reload --port 8000
```

The first request downloads BLIP's pretrained weights (~1 GB) from Hugging Face. This only happens once and is cached afterwards.

---

# Android Frontend

The Android application is located in:

```text
TrustTapAndroid/
```

Implemented features:

- Image picker
- Retrofit networking
- FastAPI backend communication
- Trust score display
- Accessible result screen
- Works with Android Emulator (`10.0.2.2`)

---

# Backend URL

### Android Emulator

```
http://10.0.2.2:8000/
```

### Physical Android Device

Replace `YOUR_PC_IP` with your computer's local IP address.

```
http://YOUR_PC_IP:8000/
```

---

# API Test

```bash
curl -X POST http://localhost:8000/analyze-image \
  -F "image=@/path/to/test.jpg" \
  -F "claimed_caption=A protest in downtown yesterday"
```

`claimed_caption` is optional and can be omitted until the CLIP caption-image verification module is implemented.

---

# Expected Response

```json
{
  "trust_score": 80,
  "risk": "Low",
  "reason": [
    "Metadata missing"
  ],
  "accessible_description": "Image shows: a group of people holding signs at a protest"
}
```

---

# Development Status

- ✅ Day 1: BLIP caption generation (`app/captioning.py`)
- ✅ Day 2: EXIF metadata analysis (`app/exif_check.py`)
- ⏳ Day 3: CLIP caption-image mismatch (`app/clip_match.py`)
- ⏳ Day 3: AI-generated image detection (`app/ai_detector.py`)
- ✅ Day 4: Scoring logic (`app/scoring.py`)
- ✅ Day 5: FastAPI backend (`app/main.py`)
- ⏳ Day 6: Calibrate scoring against real-world test images
- ✅ Day 7: Android frontend integration

---

# Notes

- `app/clip_match.py` and `app/ai_detector.py` currently contain implementation plans and code skeletons.
- The scoring weights in `scoring.py` are placeholder values and should be calibrated using real-world datasets before deployment.
- The Android application communicates with the backend through Retrofit and currently targets `http://10.0.2.2:8000/` when running on the Android Emulator.
