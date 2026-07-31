"""
Day 3 TODO: AI-generated image detection.

Search Hugging Face Hub for a pretrained detector before writing anything -
several exist already (search "ai generated image detection" or
"deepfake detection" on huggingface.co/models). Look for one with:
  - reasonable download count / recent updates (avoid abandoned repos)
  - a model card that states what it was trained on (helps you explain
    its limitations honestly during Q&A)

Once you've picked one, it's almost always a Hugging Face `pipeline`
one-liner, e.g.:

    from transformers import pipeline

    @lru_cache(maxsize=1)
    def _load_model():
        return pipeline("image-classification", model="<chosen-model-name>")

    def check_ai_generated(image):
        pipe = _load_model()
        results = pipe(image)  # returns [{"label": "fake", "score": 0.87}, ...]
        # map whatever label scheme the model uses (fake/real, ai/human, etc.)
        # to a single probability that THIS image is AI-generated
        ...

Come back to me once you've found a candidate model and I'll help you
wire it in correctly (label mapping is the part that usually needs
adjusting per-model).
"""

from PIL import Image


def check_ai_generated(image: Image.Image) -> dict:
    """
    Returns:
        {
            "ai_generated_probability": float | None  # 0-1, None until implemented
        }

    NOT YET IMPLEMENTED - returns a neutral placeholder so the rest of
    the pipeline runs end-to-end while this is being built.
    """
    # TODO(Day 3): replace with real model inference per the docstring above
    return {"ai_generated_probability": None}
