"""
Day 1: Accessible image description using BLIP.

Given an image, produces a natural-language caption that becomes the
base of the `accessible_description` field for visually impaired users.

Model: Salesforce/blip-image-captioning-base (pretrained, no training needed)
First run will download ~1GB of model weights from Hugging Face - this
is normal and only happens once (cached to ~/.cache/huggingface).
"""

from functools import lru_cache
from PIL import Image
from transformers import BlipProcessor, BlipForConditionalGeneration
import torch

MODEL_NAME = "Salesforce/blip-image-captioning-base"

# Use GPU if available (Apple Silicon MPS backend), otherwise CPU
DEVICE = "mps" if torch.backends.mps.is_available() else "cpu"


@lru_cache(maxsize=1)
def _load_model():
    """
    Loads the BLIP processor + model once and caches it in memory.
    lru_cache means this only runs on the FIRST call - subsequent
    calls reuse the already-loaded model instead of reloading from disk.
    """
    print(f"[captioning] Loading BLIP model on device: {DEVICE}")
    processor = BlipProcessor.from_pretrained(MODEL_NAME)
    model = BlipForConditionalGeneration.from_pretrained(MODEL_NAME).to(DEVICE)
    return processor, model


def generate_caption(image: Image.Image) -> str:
    """
    Takes a PIL Image, returns a one-sentence caption.

    Example: "a group of people holding signs at a protest"
    """
    processor, model = _load_model()

    image = image.convert("RGB")  # BLIP expects RGB, some uploads are RGBA/grayscale
    inputs = processor(images=image, return_tensors="pt").to(DEVICE)

    with torch.no_grad():
        output_ids = model.generate(**inputs, max_new_tokens=40)

    caption = processor.decode(output_ids[0], skip_special_tokens=True)
    return caption.strip()


if __name__ == "__main__":
    # Quick manual test: python captioning.py path/to/image.jpg
    import sys

    if len(sys.argv) != 2:
        print("Usage: python captioning.py <image_path>")
        sys.exit(1)

    img = Image.open(sys.argv[1])
    print("Caption:", generate_caption(img))
