"""
Day 3: AI-generated image detection.

Model: Organika/sdxl-detector (pretrained, no training needed)
Fine-tuned from the umm-maybe AI-image-detector specifically to catch
modern diffusion-model output (SDXL and similar). Validation metrics:
~98% accuracy, ~97.3% F1. License: CC-BY-NC-3.0 (non-commercial use -
fine for a hackathon, flag it if this ever goes commercial).

Known limitation from the model card: it may underperform on images
from OLDER generators (e.g. VQGAN+CLIP) since it was tuned toward SDXL-era
output. Worth mentioning honestly if judges ask about edge cases.
"""

from functools import lru_cache
from PIL import Image
from transformers import pipeline

MODEL_NAME = "Organika/sdxl-detector"

# Label keywords that indicate the "AI-generated" class - checked
# case-insensitively since exact label strings vary slightly by model
GENERATED_LABEL_KEYWORDS = ("artificial", "ai", "fake", "generated", "synthetic")


@lru_cache(maxsize=1)
def _load_model():
    """
    Loads the classifier once and caches it in memory - same pattern as
    captioning.py and clip_match.py.
    """
    print(f"[ai_detector] Loading AI-image detector: {MODEL_NAME}")
    return pipeline("image-classification", model=MODEL_NAME)


def check_ai_generated(image: Image.Image) -> dict:
    """
    Returns:
        {
            "ai_generated_probability": float  # 0-1, probability the image is AI-generated
        }
    """
    classifier = _load_model()
    image = image.convert("RGB")

    # top_k=None returns scores for ALL labels, not just the top one -
    # we need this because we're matching by keyword, not just taking argmax
    results = classifier(image, top_k=None)

    ai_probability = 0.0
    for result in results:
        label_lower = result["label"].lower()
        if any(keyword in label_lower for keyword in GENERATED_LABEL_KEYWORDS):
            ai_probability = result["score"]
            break

    return {"ai_generated_probability": ai_probability}


if __name__ == "__main__":
    # Quick manual test: python ai_detector.py path/to/image.jpg
    # First run: print the raw labels to confirm the keyword match worked -
    # if ai_generated_probability looks wrong (e.g. always 0.0), print
    # `results` directly to see the actual label strings this model uses.
    import sys

    if len(sys.argv) != 2:
        print("Usage: python ai_detector.py <image_path>")
        sys.exit(1)

    img = Image.open(sys.argv[1])
    print(check_ai_generated(img))
