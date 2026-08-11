"""
TrustTap image analysis backend.

Run with:
    /opt/anaconda3/bin/python -m uvicorn app.main:app --reload --port 8000

Then test with:
    curl -X POST http://localhost:8000/analyze-image \
      -F "image=@/path/to/test.jpg" \
      -F "claimed_caption=A protest in downtown yesterday"

(claimed_caption is optional - omit it if you're just testing the
description/EXIF/AI-detection parts before Day 3's CLIP module is done)
"""

from fastapi import FastAPI, File, UploadFile, Form
from fastapi.responses import JSONResponse
from PIL import Image
import io

from app.captioning import generate_caption
from app.exif_check import check_exif
from app.ai_detector import check_ai_generated
from app.clip_match import check_caption_match
from app.scoring import compute_trust_score, build_accessible_description
from app.evidence import gather_evidence

app = FastAPI(title="TrustTap Image Analysis API")


@app.get("/")
def health_check():
    return {"status": "ok", "service": "trusttap-image-analysis"}


@app.post("/analyze-image")
async def analyze_image(
    image: UploadFile = File(...),
    claimed_caption: str | None = Form(None),
):
    # Read the uploaded file into a PIL Image
    image_bytes = await image.read()
    pil_image = Image.open(io.BytesIO(image_bytes))

    # Run EXIF check on the ORIGINAL bytes' image object before any
    # processing (some libraries strip EXIF on re-encode/convert)
    exif_result = check_exif(pil_image)

    # Run each analysis module
    caption = generate_caption(pil_image)
    ai_result = check_ai_generated(pil_image)
    clip_result = check_caption_match(pil_image, claimed_caption)

    # Combine into final verdict (evidence is NOT part of this - see
    # evidence.py docstring for why it's kept separate)
    verdict = compute_trust_score(exif_result, ai_result, clip_result)
    accessible_description = build_accessible_description(caption, verdict["risk"])

    # Evidence is best-effort - if GOOGLE_API_KEY isn't set or the calls
    # fail, this just returns an empty list rather than breaking the request
    evidence = gather_evidence(image_bytes, claimed_caption)

    response = {
        "trust_score": verdict["trust_score"],
        "risk": verdict["risk"],
        "reason": verdict["reason"],
        "accessible_description": accessible_description,
        "evidence": evidence,
    }

    return JSONResponse(content=response)
