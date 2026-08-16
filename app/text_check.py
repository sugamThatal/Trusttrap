"""Explainable text-safety triage for messages shared into TrustTap.

This is deliberately not a fact-checker. It highlights common pressure and
scam patterns so a person can slow down before clicking, paying, or replying.
If a locally trained model exists at ``models/text_model.joblib`` it is used
as an additional signal; otherwise the rules remain fully usable.
"""

from __future__ import annotations

import re
from pathlib import Path
from urllib.parse import urlparse


_MODEL_PATH = Path(__file__).resolve().parent.parent / "models" / "text_model.joblib"
_MODEL = None
_MODEL_LOAD_ATTEMPTED = False

_URL_PATTERN = re.compile(r"(?i)(?:https?://|www\.)[^\s<>]+")
_IP_URL_PATTERN = re.compile(r"https?://(?:\d{1,3}\.){3}\d{1,3}(?::\d+)?")


def text_model_status() -> dict:
    if not _MODEL_PATH.exists():
        return {"available": False, "message": "No trained text model artifact loaded; using explainable rules."}
    _load_optional_model()
    if _MODEL is None:
        return {"available": False, "message": "A text model artifact exists but could not be loaded."}
    return {"available": True, "message": "Trained text model loaded."}


def _load_optional_model():
    """Load the optional artifact without making it a runtime dependency."""
    global _MODEL, _MODEL_LOAD_ATTEMPTED
    if _MODEL_LOAD_ATTEMPTED:
        return _MODEL
    _MODEL_LOAD_ATTEMPTED = True
    if not _MODEL_PATH.exists():
        return None
    try:
        import joblib

        _MODEL = joblib.load(_MODEL_PATH)
    except Exception as error:
        print(f"[text_check] optional model unavailable: {error}")
        _MODEL = None
    return _MODEL


def _model_probability(text: str) -> float | None:
    model = _load_optional_model()
    if model is None or not hasattr(model, "predict_proba"):
        return None
    try:
        probabilities = model.predict_proba([text])[0]
        classes = list(getattr(model, "classes_", range(len(probabilities))))
        suspicious_index = classes.index(1) if 1 in classes else len(probabilities) - 1
        return float(probabilities[suspicious_index])
    except Exception as error:
        print(f"[text_check] optional model prediction failed: {error}")
        return None


def _heuristic_flags(text: str) -> tuple[list[str], int]:
    lowered = text.casefold()
    flags: list[str] = []
    points = 0

    def add(reason: str, penalty: int) -> None:
        nonlocal points
        if reason not in flags:
            flags.append(reason)
            points += penalty

    urls = _URL_PATTERN.findall(text)
    if urls:
        for raw_url in urls:
            cleaned = raw_url.rstrip(".,!?);]")
            host = (urlparse(cleaned if cleaned.startswith("http") else f"https://{cleaned}").hostname or "").casefold()
            if host in {"bit.ly", "tinyurl.com", "t.co", "is.gd", "ow.ly", "shorturl.at"}:
                add("Shortened link present", 22)
            elif _IP_URL_PATTERN.search(cleaned):
                add("Link uses a raw IP address", 25)
            else:
                add("Link present; check the destination before opening", 8)

    if re.search(r"\b(send|pay|wire|transfer|gift card|bitcoin|crypto|fee|deposit)\b", lowered):
        add("Money or payment request", 28)
    if re.search(r"\b(password|passcode|one[- ]time|otp|verification code|security code|seed phrase|pin)\b", lowered):
        add("Request for a password, code, or secret", 32)
    if re.search(r"\b(urgent|immediately|right now|act now|within \d+|last chance|expires today)\b", lowered):
        add("Urgent pressure language", 20)
    if re.search(r"\b(account|bank|wallet|profile).{0,35}\b(suspend|blocked|locked|close|verify)\b", lowered):
        add("Account-threat language", 24)
    if re.search(r"\b(forward|share|send this)\b.{0,45}\b(everyone|contacts|10 people|friends)\b", lowered):
        add("Forwarding or chain-message request", 16)
    if re.search(r"\b(arrest|police|lawsuit|legal action|fine|deport)\b", lowered):
        add("Fear or threat language", 22)
    if len(text) >= 20:
        letters = [char for char in text if char.isalpha()]
        if letters and sum(char.isupper() for char in letters) / len(letters) >= 0.70:
            add("Unusually high use of capital letters", 10)
    if text.count("!") >= 3 or text.count("$") >= 3:
        add("Excessive urgency or money punctuation", 8)

    return flags, points


def check_text(text: str) -> dict:
    clean_text = " ".join(text.split())
    if not clean_text:
        raise ValueError("Text is empty")

    reasons, points = _heuristic_flags(clean_text)
    model_probability = _model_probability(clean_text)
    if model_probability is not None:
        if model_probability >= 0.80:
            reasons.append("Trained text model also sees a strong suspicious pattern")
            points += 30
        elif model_probability >= 0.60:
            reasons.append("Trained text model sees a possible suspicious pattern")
            points += 18

    points = min(100, points)
    trust_score = max(0, 100 - points)
    if trust_score < 40:
        risk = "High"
    elif trust_score < 70:
        risk = "Medium"
    else:
        risk = "Low"

    if not reasons:
        reasons = ["No common pressure patterns detected"]
    if risk == "High":
        prefix = "Warning. "
    elif risk == "Medium":
        prefix = "Caution. "
    else:
        prefix = ""
    description = (
        "This is a safety review, not proof that the message is true or false. "
        f"{prefix}{reasons[0]}. "
        "Pause and verify through a trusted contact or official website before acting."
    )

    return {
        "trust_score": trust_score,
        "risk": risk,
        "reason": reasons,
        "accessible_description": description,
        "evidence": [],
        "analysis_method": "trained text model plus explainable rules" if model_probability is not None else "explainable rules; no trained model artifact loaded",
    }
