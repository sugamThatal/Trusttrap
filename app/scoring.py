"""
Day 4: Combine all signals into trust_score, risk, and reason[].

Deliberately a plain weighted rule-set, NOT another ML model - this needs
to be explainable in front of judges ("here's exactly why this image got
flagged"), and it's fast to tune without retraining anything.

IMPORTANT: the weights and thresholds below are PLACEHOLDERS. On Day 6,
run ~15-20 real test images (mix of genuine photos, AI-generated images,
and caption-mismatched examples) through this and adjust the numbers
based on what you actually observe - don't trust these as-is.
"""

# --- Tunable weights (placeholders - calibrate on Day 6) ---
EXIF_MISSING_PENALTY = 20
AI_GENERATED_PENALTY = 35
AI_GENERATED_THRESHOLD = 0.7       # probability above which we flag "possible AI-generated"
CAPTION_MISMATCH_PENALTY = 30
CAPTION_MISMATCH_THRESHOLD = 0.22  # similarity below which we flag mismatch (see clip_match.py notes)

RISK_HIGH_CEILING = 40   # score below this -> High risk
RISK_MEDIUM_CEILING = 70  # score below this -> Medium risk, else Low


def compute_trust_score(exif_result: dict, ai_result: dict, clip_result: dict) -> dict:
    """
    Takes the raw outputs of exif_check, ai_detector, and clip_match,
    returns the final {trust_score, risk, reason[]}.

    Signals that aren't implemented yet (None values) are skipped rather
    than penalized - so the pipeline degrades gracefully while you're
    still building Day 3's modules.
    """
    score = 100
    reasons = []

    if not exif_result.get("has_exif", True):
        score -= EXIF_MISSING_PENALTY
        reasons.append("Metadata missing")

    ai_prob = ai_result.get("ai_generated_probability")
    if ai_prob is not None and ai_prob > AI_GENERATED_THRESHOLD:
        score -= AI_GENERATED_PENALTY
        reasons.append("Possible AI-generated image")

    similarity = clip_result.get("similarity")
    if similarity is not None and similarity < CAPTION_MISMATCH_THRESHOLD:
        score -= CAPTION_MISMATCH_PENALTY
        reasons.append("Caption does not match image")

    score = max(0, min(100, score))

    if score < RISK_HIGH_CEILING:
        risk = "High"
    elif score < RISK_MEDIUM_CEILING:
        risk = "Medium"
    else:
        risk = "Low"

    return {"trust_score": score, "risk": risk, "reason": reasons}


def build_accessible_description(caption: str, risk: str) -> str:
    """
    Combines the BLIP caption with a risk-appropriate warning prefix,
    so a screen reader user hears both WHAT the image shows and
    whether to be cautious about it - in one sentence, front-loaded
    with the warning since that's the most time-sensitive part.
    """
    if risk == "High":
        prefix = "Warning. This image may have been manipulated or miscaptioned. "
    elif risk == "Medium":
        prefix = "Caution. Some inconsistencies detected in this image. "
    else:
        prefix = ""

    return f"{prefix}Image shows: {caption}"
