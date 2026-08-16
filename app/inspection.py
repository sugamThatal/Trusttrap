"""Shared report assembly for text, links, images, and videos."""

from __future__ import annotations

from app.text_check import check_text, text_model_status
from app.url_check import inspect_urls, url_summary


def _risk_for_score(score: int) -> str:
    if score < 40:
        return "High"
    if score < 70:
        return "Medium"
    return "Low"


def _dedupe(items: list[str]) -> list[str]:
    output: list[str] = []
    for item in items:
        if item and item not in output:
            output.append(item)
    return output


def _next_actions(risk: str, reasons: list[str], url_actions: list[str]) -> list[str]:
    actions: list[str] = []
    lower_reasons = " ".join(reasons).casefold()
    if risk == "High":
        actions.append("Pause. Do not open links, send money, or share codes yet.")
    elif risk == "Medium":
        actions.append("Pause and verify the claim through a trusted source before acting.")
    else:
        actions.append("No common warning pattern was found, but verify important claims independently.")
    if any(word in lower_reasons for word in ("password", "code", "secret", "payment", "money")):
        actions.append("Never share a password, one-time code, PIN, or payment details from an unexpected message.")
    if url_actions:
        actions.extend(url_actions)
    actions.append("You can ask TrustTap: why was this flagged, read the text, or what should I do next?")
    return _dedupe(actions)


def _follow_ups(has_text: bool, has_urls: bool) -> list[str]:
    prompts = ["What should I do next?", "Why was this flagged?"]
    if has_text:
        prompts.insert(0, "Read the text")
    if has_urls:
        prompts.append("Explain the link warning")
    return prompts


def inspect_text_report(text: str, input_type: str = "text") -> dict:
    result = check_text(text)
    url_findings = inspect_urls(text)
    url_reasons, url_actions, url_score = url_summary(url_findings)
    score = min(result["trust_score"], url_score)
    reasons = _dedupe(result["reason"] + url_reasons)
    risk = _risk_for_score(score)
    return {
        **result,
        "trust_score": score,
        "risk": risk,
        "reason": reasons,
        "extracted_text": text.strip(),
        "url_findings": url_findings,
        "next_actions": _next_actions(risk, reasons, url_actions),
        "follow_up_prompts": _follow_ups(True, bool(url_findings)),
        "limitations": ["This is a safety triage, not proof that the message is true or false."],
        "analysis_method": f"{result['analysis_method']}; local URL inspection",
        "input_type": input_type,
    }


def enrich_media_report(
    base_response: dict,
    caption: str,
    claimed_caption: str | None,
    extracted_text: str,
    input_type: str,
    ocr_status: dict,
) -> dict:
    searchable_text = " ".join(part for part in (extracted_text, claimed_caption or "") if part).strip()
    url_findings = inspect_urls(searchable_text)
    url_reasons, url_actions, url_score = url_summary(url_findings)
    reasons = list(base_response.get("reason", []))
    score = int(base_response.get("trust_score", 0))
    method = "existing media ensemble + local URL inspection"
    text_result = None
    if extracted_text.strip():
        text_result = check_text(extracted_text)
        if text_result["reason"] != ["No common pressure patterns detected"]:
            reasons.extend(f"Readable text: {reason}" for reason in text_result["reason"])
            score = min(score, int(text_result["trust_score"]))
            method += " + OCR text safety review"
    if url_reasons:
        reasons.extend(url_reasons)
    score = min(score, url_score)
    reasons = _dedupe(reasons)
    risk = _risk_for_score(score)
    description = base_response.get("accessible_description", "")
    if extracted_text.strip():
        excerpt = " ".join(extracted_text.split())[:240]
        description += f" Readable text: {excerpt}."
    if url_findings:
        description += f" Found {len(url_findings)} link{'s' if len(url_findings) != 1 else ''}; check before opening."
    if not ocr_status.get("available", False):
        description += " Text inside the image could not be read because OCR is unavailable."
    response = {
        **base_response,
        "trust_score": score,
        "risk": risk,
        "reason": reasons,
        "accessible_description": description,
        "extracted_text": extracted_text or None,
        "url_findings": url_findings,
        "next_actions": _next_actions(risk, reasons, url_actions),
        "follow_up_prompts": _follow_ups(bool(extracted_text.strip()), bool(url_findings)),
        "limitations": [
            "Image and video results are signals, not proof of authenticity.",
            "Video analysis samples frames and does not verify audio or every moment.",
        ] + ([] if ocr_status.get("available", False) else ["OCR was not available for this check."]),
        "analysis_method": method,
        "input_type": input_type,
        "ocr_available": bool(ocr_status.get("available", False)),
        "text_model_available": text_model_status()["available"],
    }
    return response
