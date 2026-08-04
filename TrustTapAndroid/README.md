# TrustTap - Android app (basic UI pass)

A minimal Jetpack Compose app that talks to the FastAPI backend's
`POST /analyze-image` endpoint. This pass is just "pick or share an
image → see the verdict" - TTS voice output and deeper TalkBack
accessibility work come in the next pass.

## 1. run the backend


```bash
cd Trusttrap-main
mkdir app
mv main.py captioning.py exif_check.py ai_detector.py clip_match.py scoring.py app/
touch app/__init__.py
python -m uvicorn app.main:app --reload --port 8000
```

Leave this running in a terminal while you test the app.

## 2. Open the Android project

Open the `TrustTap` folder (the one this README is in) in Android
Studio. If it asks to create a Gradle wrapper, say yes - that's
normal for a project that wasn't zipped from inside Android Studio.
Let it sync, then run on an emulator.

## 3. Point the app at your backend

`app/src/main/java/com/trusttap/app/network/RetrofitClient.kt` has the
base URL. It's currently set to `http://10.0.2.2:8000/`, which is the
Android **emulator's** special alias for your laptop's localhost - use
this as-is if you're running the emulator on the same machine as the
FastAPI server.

Testing on a real phone instead? Put your phone and laptop on the same
Wi-Fi, find your laptop's LAN IP (`ipconfig getifaddr en0` on Mac,
`ipconfig` on Windows), and change the constant to
`http://<that-ip>:8000/`.

## 4. Try it

- Tap "Pick an image to check", choose any photo, hit Analyze.
- Or: share an image into TrustTap from Photos/WhatsApp/a browser via
  the system Share Sheet - it'll open TrustTap with that image already
  loaded.
- Right now only the EXIF-missing check actually affects the score
  (`ai_detector.py` / `clip_match.py` are still stubs on the backend),
  so don't expect AI-generated or mismatched-caption test images to
  get flagged yet - that's backend work, not a frontend bug.

## Known rough edges (expected at this stage)

- No TTS yet - results are shown as text only.
- Compose's default accessibility semantics are OK but not tuned -
  icon-only elements and the dynamic result card need explicit
  `contentDescription`/`semantics {}` work, coming next.
- No retry/offline handling beyond a plain error message.
