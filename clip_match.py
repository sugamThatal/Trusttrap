"""
Day 3 TODO: Caption-image mismatch detection using CLIP.

Idea: embed the CLAIMED caption (the text the user shared alongside the
image) and the image itself into the same vector space with CLIP, then
compute cosine similarity. Low similarity = the caption probably
doesn't describe this image = classic "real photo, false context"
misinformation pattern.

Model to use: openai/clip-vit-base-patch32 (pretrained, no training)

Fill in `check_caption_match()` below. Suggested implementation:

    from transformers import CLIPProcessor, CLIPModel
    import torch

    MODEL_NAME = "openai/clip-vit-base-patch32"

    @lru_cache(maxsize=1)
    def _load_model():
        processor = CLIPProcessor.from_pretrained(MODEL_NAME)
        model = CLIPModel.from_pretrained(MODEL_NAME)
        return processor, model

    def check_caption_match(image, claimed_caption):
        processor, model = _load_model()
        inputs = processor(text=[claimed_caption], images=image,
                            return_tensors="pt", padding=True)
        outputs = model(**inputs)
        # logits_per_image is already a similarity score - normalize with softmax
        # or just use cosine similarity between image_embeds and text_embeds directly
        similarity = torch.cosine_similarity(outputs.image_embeds, outputs.text_embeds).item()
        return similarity  # roughly -1 to 1, in practice usually 0.15-0.35 range

Calibration note: CLIP similarity scores for genuinely matching image/caption
pairs are usually NOT close to 1.0 (unlike what you'd expect) - expect
matching pairs around 0.25-0.35 and mismatched pairs lower, closer to 0.1-0.2.
Test with ~10 known-matching and ~10 known-mismatched pairs on Day 6 to
find your actual threshold instead of trusting a guessed number.
"""

from PIL import Image


def check_caption_match(image: Image.Image, claimed_caption: str | None) -> dict:
    """
    Returns:
        {
            "similarity": float | None,  # None if no caption was provided to check against
            "mismatch": bool
        }

    NOT YET IMPLEMENTED - currently returns a neutral placeholder so the
    rest of the pipeline runs end-to-end while this is being built.
    """
    if not claimed_caption:
        return {"similarity": None, "mismatch": False}

    # TODO(Day 3): replace with real CLIP similarity per the docstring above
    return {"similarity": None, "mismatch": False}
