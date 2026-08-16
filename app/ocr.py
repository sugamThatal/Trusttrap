"""Optional OCR adapter with an explicit capability status.

The Python package is lightweight, but Windows also needs the Tesseract
executable. Set TRUSTTAP_TESSERACT_CMD if it is not on PATH. Text checking
continues to work when OCR is unavailable.
"""

from __future__ import annotations

import os


def ocr_status() -> dict:
    try:
        import pytesseract

        command = os.getenv("TRUSTTAP_TESSERACT_CMD")
        if command:
            pytesseract.pytesseract.tesseract_cmd = command
        version = str(pytesseract.get_tesseract_version()).splitlines()[0]
        return {"available": True, "message": f"Tesseract OCR available ({version})"}
    except Exception as error:
        return {
            "available": False,
            "message": "OCR unavailable; install Tesseract and pytesseract to read text inside images.",
            "detail": str(error),
        }


def extract_text(image) -> dict:
    status = ocr_status()
    if not status["available"]:
        return {"text": "", "status": status}
    try:
        import pytesseract

        text = pytesseract.image_to_string(image).strip()
        return {"text": text, "status": status}
    except Exception as error:
        return {
            "text": "",
            "status": {"available": False, "message": "OCR failed for this image.", "detail": str(error)},
        }
