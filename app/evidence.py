"""
Evidence: fact-check + reverse image search.

Combines two lookups into a single "evidence" list added to the response:
  1. Fact Check Tools API - checks the claimed_caption text against known
     fact-checked claims (e.g. Reuters, AFP, Snopes verdicts)
  2. Google Cloud Vision Web Detection - reverse image search, finds if
     this exact/similar image appears elsewhere online (catches recycled
     old photos re-shared with a new false caption)

DELIBERATE DESIGN CHOICE: evidence does NOT affect trust_score/risk.
It's returned as a separate "evidence" field. Your 4 core signals
(EXIF, BLIP, CLIP, AI-detector) are already calibrated - wiring a
same-day feature into that scoring math this close to deadline risks
breaking something that already works. If you have time after
everything else is solid, we can integrate it into scoring properly.

SETUP REQUIRED (you must do this before this module will work):
  1. Go to console.cloud.google.com, create a project (or use an existing one)
  2. Enable "Cloud Vision API" AND "Fact Check Tools API" for that project
     (Vision API requires a billing account attached - the free tier
     covers 1,000 units/month, plenty for a hackathon demo, but Google
     requires a card on file to enable it. Fact Check Tools API is free,
     no billing needed, just needs to be enabled)
  3. Create an API key: APIs & Services -> Credentials -> Create Credentials -> API Key
  4. Set it as an environment variable before running the server:
         export GOOGLE_API_KEY="your-key-here"
     (do NOT hardcode it here, do NOT commit it to git)

Both lookups are wrapped in try/except - if either API call fails (bad
key, quota exceeded, no network), this returns an empty list rather
than crashing the whole /analyze-image request. A fact-check failure
should never take down the core pipeline that's already tested.
"""

import base64
import os
import requests

GOOGLE_API_KEY = os.environ.get("GOOGLE_API_KEY", "")

FACT_CHECK_URL = "https://factchecktools.googleapis.com/v1alpha1/claims:search"
VISION_API_URL = "https://vision.googleapis.com/v1/images:annotate"


def search_fact_check(claim_text: str | None) -> list[dict]:
    """
    Queries Google Fact Check Tools API for the claimed caption text.
    Returns matched claims with rating + publisher + url.
    Empty list if no caption given, no key set, no matches, or the call fails.
    """
    if not claim_text or not GOOGLE_API_KEY:
        return []

    try:
        response = requests.get(
            FACT_CHECK_URL,
            params={"query": claim_text, "key": GOOGLE_API_KEY},
            timeout=10,
        )
        response.raise_for_status()
        data = response.json()
    except requests.exceptions.RequestException as e:
        print(f"[evidence] Fact check API call failed: {e}")
        return []

    matches = []
    for claim in data.get("claims", [])[:3]:  # cap at top 3
        reviews = claim.get("claimReview", [])
        if not reviews:
            continue
        review = reviews[0]
        matches.append({
            "type": "fact_check",
            "claim": claim.get("text", ""),
            "rating": review.get("textualRating", "Unknown"),
            "publisher": review.get("publisher", {}).get("name", "Unknown"),
            "url": review.get("url", ""),
        })

    return matches


def reverse_image_search(image_bytes: bytes) -> list[dict]:
    """
    Uses Google Cloud Vision's Web Detection to find if this image (or a
    near-duplicate) appears elsewhere online. Returns matching pages.
    Empty list if no key set, the call fails, or nothing is found.
    """
    if not GOOGLE_API_KEY:
        return []

    encoded_image = base64.b64encode(image_bytes).decode("utf-8")

    request_body = {
        "requests": [{
            "image": {"content": encoded_image},
            "features": [{"type": "WEB_DETECTION", "maxResults": 5}],
        }]
    }

    try:
        response = requests.post(
            f"{VISION_API_URL}?key={GOOGLE_API_KEY}",
            json=request_body,
            timeout=15,
        )
        response.raise_for_status()
        data = response.json()
    except requests.exceptions.RequestException as e:
        print(f"[evidence] Reverse image search API call failed: {e}")
        return []

    web_detection = data.get("responses", [{}])[0].get("webDetection", {})
    pages = web_detection.get("pagesWithMatchingImages", [])

    matches = []
    for page in pages[:3]:  # cap at top 3
        matches.append({
            "type": "reverse_image",
            "url": page.get("url", ""),
            "page_title": page.get("pageTitle", ""),
        })

    return matches


def gather_evidence(image_bytes: bytes, claimed_caption: str | None) -> list[dict]:
    """Combines both lookups into one evidence list for the API response."""
    evidence = []
    evidence.extend(search_fact_check(claimed_caption))
    evidence.extend(reverse_image_search(image_bytes))
    return evidence


if __name__ == "__main__":
    # Quick manual test: python evidence.py path/to/image.jpg "claimed caption"
    import sys

    if len(sys.argv) < 2:
        print('Usage: python evidence.py <image_path> ["claimed caption"]')
        sys.exit(1)

    with open(sys.argv[1], "rb") as f:
        img_bytes = f.read()

    caption = sys.argv[2] if len(sys.argv) > 2 else None
    print(gather_evidence(img_bytes, caption))
