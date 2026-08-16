from app.ocr import ocr_status
from app.text_check import text_model_status


def get_capabilities() -> dict:
    return {
        "ocr": ocr_status(),
        "text_model": text_model_status(),
        "tee": {
            "available": False,
            "message": "No confidential-computing backend is active. See TEE-PLAN.md.",
        },
        "url_inspection": {
            "available": True,
            "message": "Local string-level inspection; links are never opened automatically.",
        },
        "privacy": [
            "No SMS-reading permission is required.",
            "No link is opened automatically.",
            "External evidence lookups remain best-effort and require the existing backend configuration.",
        ],
    }
