# TrustTap - Image Analysis Backend

## Setup (run on your Mac, Anaconda env)

```bash
cd trusttap
/opt/anaconda3/bin/python -m pip install -r requirements.txt
```

First install will take a few minutes (torch is large). No GPU-specific
setup needed - it'll auto-use Apple Silicon's MPS backend if available,
otherwise falls back to CPU.

## Run the server

```bash
/opt/anaconda3/bin/python -m uvicorn app.main:app --reload --port 8000
```

First request will download BLIP's weights (~1GB) from Hugging Face -
one-time, cached after that. Give it a minute on the first call.

## Test it

```bash
curl -X POST http://localhost:8000/analyze-image \
  -F "image=@/path/to/some/test.jpg" \
  -F "claimed_caption=A protest in downtown yesterday"
```

`claimed_caption` is optional - you can omit `-F "claimed_caption=..."`
entirely while testing before Day 3's CLIP module is filled in.

Expected response shape:
```json
{
  "trust_score": 80,
  "risk": "Low",
  "reason": ["Metadata missing"],
  "accessible_description": "Image shows: a group of people holding signs at a protest"
}
```

## Status (update as you go)

- [x] Day 1: BLIP captioning (`app/captioning.py`) - DONE (fixed repetition-loop bug)
- [x] Day 2: EXIF check (`app/exif_check.py`) - DONE
- [x] Day 3: CLIP caption-image mismatch (`app/clip_match.py`) - DONE
- [x] Day 3: AI-generated image detector (`app/ai_detector.py`) - DONE (Organika/sdxl-detector)
- [x] Day 4: Scoring logic (`app/scoring.py`) - DONE, but weights are placeholders, calibrate Day 6
- [x] Day 5: FastAPI wiring (`app/main.py`) - DONE, contract matches your spec
- [ ] Day 6: Calibrate against real test images - NEXT UP
- [ ] Day 7: Integration with the mobile app

## Notes

- `app/clip_match.py` and `app/ai_detector.py` have detailed docstrings
  with the implementation plan and code sketch - come back to me when
  you're ready to fill either one in, or if you hit an error.
- The scoring weights in `scoring.py` are guesses. Don't defend them to
  judges as "tuned" until you've actually run real test images through
  on Day 6.
