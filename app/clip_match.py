"""
Day 3: Caption-image mismatch detection using CLIP.

Embeds the CLAIMED caption (the text the user shared alongside the
image) and the image itself into the same vector space with CLIP, then
computes cosine similarity. Low similarity = the caption probably
doesn't describe this image = classic "real photo, false context"
misinformation pattern.

Model: openai/clip-vit-base-patch32 (pretrained, no training needed)

IMPORTANT calibration note: CLIP similarity scores for genuinely matching
image/caption pairs are usually NOT close to 1.0. Expect matching pairs
around 0.25-0.35 and mismatched pairs lower, around 0.1-0.2. The actual
threshold decision lives in scoring.py (CAPTION_MISMATCH_THRESHOLD) so
it's tunable in one place - this module just reports the raw similarity.
Test with ~10 known-matching and ~10 known-mismatched pairs on Day 6
before trusting the default threshold.
"""

from functools import lru_cache
from PIL import Image
from transformers import CLIPProcessor, CLIPModel
import torch

MODEL_NAME = "openai/clip-vit-base-patch32"

DEVICE = "mps" if torch.backends.mps.is_available() else "cpu"


@lru_cache(maxsize=1)
def _load_model():
    """
    Loads CLIP once and caches it in memory - same lru_cache pattern as
    captioning.py, so repeated requests don't reload the model from disk.
    """
    print(f"[clip_match] Loading CLIP model on device: {DEVICE}")
    processor = CLIPProcessor.from_pretrained(MODEL_NAME)
    model = CLIPModel.from_pretrained(MODEL_NAME).to(DEVICE)
    return processor, model


def check_caption_match(image: Image.Image, claimed_caption: str | None) -> dict:
    """
    Returns:
        {
            "similarity": float | None,  # None if no caption was provided to check against
        }

    Higher similarity = caption matches the image content well.
    Lower similarity = possible mismatch (real photo, false context).
    """
    if not claimed_caption:
        # No caption was shared alongside the image - nothing to check against
        return {"similarity": None}

    processor, model = _load_model()
    image = image.convert("RGB")

    inputs = processor(
        text=[claimed_caption],
        images=image,
        return_tensors="pt",
        padding=True,
        truncation=True,  # CLIP's text encoder has a 77-token limit, truncate longer captions
    ).to(DEVICE)

    with torch.no_grad():
        outputs = model(**inputs)

    similarity = torch.cosine_similarity(
        outputs.image_embeds, outputs.text_embeds
    ).item()

    return {"similarity": similarity}


if __name__ == "__main__":
    # Quick manual test: python clip_match.py path/to/image.jpg "claimed caption text"
    import sys

    if len(sys.argv) != 3:
        print('Usage: python clip_match.py <image_path> "claimed caption"')
        sys.exit(1)

    img = Image.open(sys.argv[1])
    result = check_caption_match(img, sys.argv[2])
    print(result)
