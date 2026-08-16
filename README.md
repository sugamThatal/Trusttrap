# TrustTap

TrustTap checks whether a photo or video is consistent with what it claims to show. The Android app sends the media to the FastAPI backend, then presents and speaks the result. The app is designed for TalkBack and Share Sheet use, with local history and an editable backend address.

## What is included in this update

- Check, History, and Settings screens using Navigation Compose.
- Local Room history with thumbnails, timestamps, media type, and the full analysis response.
- DataStore settings for the backend URL; no Kotlin rebuild is needed when the address changes.
- First-launch explainer, clear loading/error/empty states, connection test, TTS replay, and TalkBack live-region announcements.
- Image/video Photo Picker and image/video/text Share Sheet handling are included.
- A large, TalkBack-friendly Plain text check is available by paste/type or Android Share.
- Text results are saved in local History; no SMS-reading permission is requested.
- Media reports can extract visible text with optional Tesseract OCR, inspect links locally, and suggest safer next actions.
- The result card includes a bounded follow-up assistant: ask why it was flagged, ask what to do, or ask it to read extracted text.
- Follow-up questions can be typed or sent to the phone's installed voice-recognition app; TrustTap does not silently record audio.
- No changes to the existing detection modules: captioning, EXIF, AI detector, CLIP match, evidence, scoring, or video sampling.
- See `PRIVACY.md` for the data-flow/permission boundary and `EVALUATION.md` for the blind-user evaluation plan.

## 1. Start the backend on the computer

Open PowerShell in the outer `Trusttrap-main` folder:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Leave this terminal open. The first real analysis can be slow because the ML models may download and initialize.

Confirm the backend on the computer by opening `http://localhost:8000/`. It should return a small JSON health response.
Open `http://localhost:8000/capabilities` to see whether OCR and an optional trained text model are available.

### Optional OCR setup

The Python package `pytesseract` is included. On Windows, install the official
Tesseract OCR engine separately and ensure `tesseract.exe` is on PATH. If it is installed elsewhere, set the
environment variable before starting Uvicorn:

```powershell
$env:TRUSTTAP_TESSERACT_CMD = "C:\Program Files\Tesseract-OCR\tesseract.exe"
```

TrustTap still works without OCR; it will state that text inside the image was
not read rather than pretending it saw it.

### Windows firewall and real phone

For a real phone, the computer and phone must be on the same Wi-Fi network. If Windows asks whether Python may communicate on a network, allow it on the Private network. If it does not ask, allow Python/Uvicorn or TCP port 8000 through Windows Defender Firewall for Private networks.

Find the computer's Wi-Fi IPv4 address:

```powershell
ipconfig
```

Use the Wi-Fi adapter's IPv4 address. The address in the current setup brief is `192.168.1.70`, so the phone URL is:

```text
http://192.168.1.70:8000/
```

On the phone's browser, open `http://192.168.1.70:8000/`. If it does not load, fix Wi-Fi/firewall/IP first; Android Studio cannot fix a computer that is not reachable on the LAN.

## 2. Open and build the Android app

1. Open Android Studio.
2. Choose **Open** and select the `TrustTapAndroid` folder specifically.
3. Wait for Gradle Sync to finish.
4. For a real phone, enable Developer Options and USB debugging, connect the phone, accept the debugging prompt, and choose the phone from Android Studio's device dropdown. Do not select the “Medium Phone” emulator when testing the real-phone flow.
5. Press Run.

If Gradle reports a Java/Kotlin version error, set **Android Studio > Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK** to JDK 17. This project uses Kotlin 1.9.24 and the Android Studio 8.5.2-compatible Gradle 8.7 wrapper.

The first launch shows a short explainer. Tap **Get started**.

## 3. Set the backend URL in the app

Open **Settings** in TrustTap.

- Real phone: use `http://192.168.1.70:8000/` (replace the IP if `ipconfig` shows a different one). There must be no stray space, and the phone and computer must be on the same Wi-Fi.
- Android Emulator on the same computer as the backend: use `http://10.0.2.2:8000/`.

Tap **Save address**, then **Test connection**. The app should say “Backend is reachable.” This URL is saved on the device with DataStore.

## 4. Run a photo, video, or text check

1. Open **Check**.
2. For media, tap **Photo or video**, choose a file, optionally enter the claim, and tap **Check photo or video**.
3. For a message, tap **Plain text**, paste/type the message, and tap **Check plain text**.
4. Read the result card, open any evidence links, or tap **Replay result** to hear it again.

Text checking is a safety triage for pressure/scam patterns. It is not proof
that a message is true or false. Do not paste passwords, one-time codes, or
other private secrets.

Every completed result is saved locally in **History**. Tap a history row to reopen the full result later. History is on-device; it is not uploaded or synced.

## 5. Test sharing from social apps or a message

On the actual phone, open Facebook, Chrome, or Gallery, find an image, tap
**Share**, tap **More** if necessary, choose **TrustTap**, and the app should
open and start analyzing automatically. For a text message, select/copy the
text, use Android's **Share** action, and choose **TrustTap**; the app receives
only the text included in that share.

Facebook, Chrome, and Gallery are the most reliable image demos because they
usually share an actual image file. Instagram and X may only offer **Copy
link** or in-app sending instead of exposing an image file. That is a
restriction of the source app. A shared post link is still useful as Plain
text, but it is not an image analysis.

If TrustTap does not appear in the Share list:

1. Make sure you installed this updated ZIP's APK/project; an older installed
   build cannot gain the new Share Sheet filters.
2. Tap **Share > More** and scroll/search for TrustTap.
3. If it still does not appear, uninstall the old TrustTap app, run the new
   build from Android Studio onto the actual phone, then try again.
4. Use Facebook, Chrome, or Gallery for the first test. Some social apps do
   not expose a downloadable image to Android at all.

The app accepts `image/*`, `video/*`, `text/plain`, and broad Android file-share
types. It reads the shared Uri/text and requests no storage or SMS permission.

## Honest project status

- Text review: implemented as explainable safety triage and works by paste/type or Share.
- Unified report: media, OCR text, shared text, and links now return readable text, local link signals, limitations, and safer next actions.
- Agent behavior: bounded follow-up questions are supported; TrustTap never autonomously opens links, sends messages, pays money, or reads SMS.
- Model training/calibration: not run in this ZIP because no real labeled dataset was supplied. The optional training command is below; it refuses to fabricate data.
- TEE/confidential computing: not active. A real attested confidential backend target is required; see `TEE-PLAN.md`.
- SMS permission: deliberately not requested. The safe flow is to share or paste selected text rather than silently reading the inbox.
- Audio deepfake detection and true temporal video analysis are also not implemented; video currently samples frames through the existing backend pipeline.

## Backend modules kept unchanged

The following existing detection files are intentionally preserved: `app/captioning.py`, `app/exif_check.py`, `app/ai_detector.py`, `app/clip_match.py`, `app/evidence.py`, and `app/scoring.py`.

## 7. Optional model training with your labeled data

From the outer `Trusttrap-main` folder, install the optional training tools and
run the command below after creating a consented CSV with `text,label` headers.
Use `1` for suspicious/scam and `0` for ordinary/benign examples.

```powershell
pip install -r training/requirements.txt
python training/train_text_model.py --input C:\path\to\text_labels.csv --output models\text_model.joblib
```

Restart Uvicorn after training. The text endpoint will combine that model with
the explainable rules. Read `training/README.md` before using real messages.

## macOS setup (for Mac users)

Windows steps above use PowerShell; here's the Mac equivalent for the same steps.

### 1. Start the backend

Open Terminal in the outer `Trusttrap-main` folder:

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

### 2. Optional OCR setup

```bash
brew install tesseract
```

No extra environment variable needed if installed via Homebrew — it's found on PATH automatically.

### 3. Find your Mac's Wi-Fi IP (for testing on a real phone)

- System Settings → Wi-Fi → click the (i) next to your network → note the IP address
- Or in Terminal: `ipconfig getifaddr en0` (try `en1` if that's empty)
- macOS will show a firewall popup the first time the backend starts — click **Allow**

Everything from "Open and build the Android app" onward is identical on both platforms.
