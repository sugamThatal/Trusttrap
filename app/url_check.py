"""Local, privacy-preserving URL inspection.

This module never opens a link or sends its contents to a third party. It
looks at the URL string itself and reports signals that deserve caution.
"""

from __future__ import annotations

import re
from urllib.parse import urlparse


_URL_PATTERN = re.compile(r"(?i)(?:https?://|www\.)[^\s<>]+")
_SHORTENERS = {"bit.ly", "tinyurl.com", "t.co", "is.gd", "ow.ly", "shorturl.at", "rb.gy"}


def extract_urls(text: str) -> list[str]:
    found: list[str] = []
    for raw in _URL_PATTERN.findall(text or ""):
        cleaned = raw.rstrip(".,!?);]\"'")
        if cleaned and cleaned not in found:
            found.append(cleaned)
    return found


def _host_for(raw_url: str) -> tuple[str, str]:
    normalized = raw_url if raw_url.casefold().startswith(("http://", "https://")) else f"https://{raw_url}"
    parsed = urlparse(normalized)
    return normalized, (parsed.hostname or "").casefold()


def inspect_urls(text: str) -> list[dict]:
    findings: list[dict] = []
    for raw_url in extract_urls(text):
        normalized, host = _host_for(raw_url)
        parsed = urlparse(normalized)
        signals: list[str] = []
        points = 0

        def add(signal: str, penalty: int) -> None:
            nonlocal points
            if signal not in signals:
                signals.append(signal)
                points += penalty

        if host in _SHORTENERS:
            add("Shortened link hides the final destination", 25)
        if host.startswith("xn--") or ".xn--" in host:
            add("Internationalized domain needs extra care", 30)
        if re.fullmatch(r"\d{1,3}(?:\.\d{1,3}){3}", host):
            add("Link uses a raw IP address instead of a domain", 30)
        if parsed.username or parsed.password:
            add("Link contains embedded sign-in information", 30)
        if parsed.port and parsed.port not in {80, 443}:
            add("Link uses an unusual port", 18)
        if parsed.scheme == "http":
            add("Link is not encrypted with HTTPS", 12)
        if len(parsed.query) > 180:
            add("Link has an unusually long tracking/query section", 8)

        points = min(points, 100)
        score = max(0, 100 - points)
        if score < 45:
            risk = "High"
            action = "Do not open it. Use the official app or type the known website yourself."
        elif score <= 75:
            risk = "Medium"
            action = "Do not trust the link alone. Verify the destination through an official channel."
        else:
            risk = "Low"
            action = "Still check the domain before entering information."

        if not signals:
            signals = ["No obvious string-level warning signal"]
        findings.append({
            "url": raw_url,
            "host": host or "unknown host",
            "risk": risk,
            "signals": signals,
            "recommended_action": action,
        })
    return findings


def url_summary(findings: list[dict]) -> tuple[list[str], list[str], int]:
    reasons: list[str] = []
    actions: list[str] = []
    score = 100
    for finding in findings:
        if finding["risk"] != "Low":
            for signal in finding["signals"]:
                if signal not in reasons:
                    reasons.append(signal)
        if finding["recommended_action"] not in actions:
            actions.append(finding["recommended_action"])
        score = min(score, {"High": 35, "Medium": 65, "Low": 100}[finding["risk"]])
    return reasons, actions, score
