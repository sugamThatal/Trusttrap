"""
TrustTap image + video analysis backend.

Run with:
    /opt/anaconda3/bin/python -m uvicorn app.main:app --reload --port 8000

Then test with:
    curl -X POST http://localhost:8000/analyze-image \
      -F "image=@/path/to/test.jpg" \
      -F "claimed_caption=A protest in downtown yesterday"

    curl -X POST http://localhost:8000/analyze-video \
      -F "video=@/path/to/test.mp4" \
      -F "claimed_caption=Footage from the protest yesterday"

(claimed_caption is optional on both endpoints)

Text can be checked without SMS permissions:
    curl -X POST http://localhost:8000/analyze-text \
      -H "Content-Type: application/json" \
      -d '{"text":"Your account is blocked. Send the code now."}'
"""

from fastapi import FastAPI, File, UploadFile, Form
from fastapi.responses import JSONResponse
from PIL import Image
import io
import os
import tempfile

from app.captioning import generate_caption
from app.exif_check import check_exif
from app.ai_detector import check_ai_generated
from app.clip_match import check_caption_match
from app.scoring import compute_trust_score, build_accessible_description
from app.evidence import gather_evidence
from app.video_utils import (
    extract_sample_frames,
    get_video_duration_seconds,
    MAX_VIDEO_DURATION_SECONDS,
)
from app.text_api import router as text_router
from app.capabilities import get_capabilities
from app.inspection import enrich_media_report
from app.ocr import extract_text

app = FastAPI(title="TrustTap Analysis API")
app.include_router(text_router)


@app.get("/")
def health_check():
    return {"status": "ok", "service": "trusttap-analysis-api"}


@app.get("/capabilities")
def capabilities():
    return get_capabilities()


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
    ocr_result = extract_text(pil_image)

    response = enrich_media_report({
        "trust_score": verdict["trust_score"],
        "risk": verdict["risk"],
        "reason": verdict["reason"],
        "accessible_description": accessible_description,
        "evidence": evidence,
    }, caption, claimed_caption, ocr_result["text"], "image", ocr_result["status"])

    return JSONResponse(content=response)


@app.post("/analyze-video")
async def analyze_video(
    video: UploadFile = File(...),
    claimed_caption: str | None = Form(None),
):
    """
    Same idea as /analyze-image, adapted for video: sample several
    frames spread across the clip and run the existing image pipeline
    on them (see video_utils.py docstring for why this approach, and
    its known limitations - it won't catch temporal-only artifacts or
    fake audio).

    Video has to hit disk temporarily - unlike PIL for images, OpenCV's
    frame-accurate seeking needs an actual file path, not an in-memory
    buffer. The temp file is always cleaned up before returning.
    """
    video_bytes = await video.read()
    suffix = os.path.splitext(video.filename or "")[1] or ".mp4"

    tmp_path = None
    try:
        with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as tmp_file:
            tmp_file.write(video_bytes)
            tmp_path = tmp_file.name

        duration = get_video_duration_seconds(tmp_path)
        if duration <= 0:
            return JSONResponse(
                status_code=400,
                content={"detail": "Couldn't read this as a video file - is it a valid, non-corrupt video?"},
            )
        if duration > MAX_VIDEO_DURATION_SECONDS:
            return JSONResponse(
                status_code=400,
                content={
                    "detail": f"Video is {duration:.0f}s long - the current limit is "
                              f"{MAX_VIDEO_DURATION_SECONDS}s. Trim it and try again."
                },
            )

        frames = extract_sample_frames(tmp_path)
        if not frames:
            return JSONResponse(
                status_code=400,
                content={"detail": "Couldn't extract any frames from this video."},
            )

        # The middle sampled frame stands in for "the image" for the two
        # checks that only need one representative frame - running BLIP
        # captioning and CLIP matching on every sampled frame would be
        # much slower for little extra benefit on those two specifically
        representative_frame = frames[len(frames) // 2]

        caption = generate_caption(representative_frame)
        clip_result = check_caption_match(representative_frame, claimed_caption)

        # AI-detector DOES run on every sampled frame - a manipulated or
        # AI-generated video often only looks "off" in some frames, so
        # we take the worst (highest) probability across the sample
        # rather than averaging it away
        frame_ai_probs = [
            check_ai_generated(frame)["ai_generated_probability"] for frame in frames
        ]
        ai_result = {"ai_generated_probability": max(frame_ai_probs)}

        # No EXIF-equivalent metadata check for video yet. has_exif=True
        # means "don't penalize for this" (see scoring.py's default) -
        # we say so explicitly below instead of silently skipping it
        exif_result = {"has_exif": True}

        verdict = compute_trust_score(exif_result, ai_result, clip_result)
        accessible_description = (
            build_accessible_description(caption, verdict["risk"])
            + " (Frame-based video check - metadata verification isn't available for video yet.)"
        )

        # Reuse the same evidence lookups as images: fact-check on the
        # caption text, reverse-image search on the representative frame
        # (catches an old video's footage being recycled with a new claim)
        frame_bytes_io = io.BytesIO()
        representative_frame.save(frame_bytes_io, format="JPEG")
        evidence = gather_evidence(frame_bytes_io.getvalue(), claimed_caption)

        ocr_result = extract_text(representative_frame)
        response = enrich_media_report({
            "trust_score": verdict["trust_score"],
            "risk": verdict["risk"],
            "reason": verdict["reason"],
            "accessible_description": accessible_description,
            "evidence": evidence,
            "frames_analyzed": len(frames),
        }, caption, claimed_caption, ocr_result["text"], "video", ocr_result["status"])
        return JSONResponse(content=response)
    finally:
        if tmp_path and os.path.exists(tmp_path):
            os.unlink(tmp_path)
