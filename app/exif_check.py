"""
Day 2: EXIF metadata check.

Real camera photos carry EXIF metadata (device model, timestamp, GPS,
exposure settings). Images that are AI-generated, screenshotted, or have
been re-saved/stripped by social platforms usually lack it.

This is a WEAK signal on its own (many legit re-shared photos also lack
EXIF, e.g. anything downloaded from WhatsApp/Twitter) - it should only
ever contribute a small weight in the final trust_score, not decide it
alone. Treat it as corroborating evidence, not proof.
"""

from PIL import Image
from PIL.ExifTags import TAGS


def check_exif(image: Image.Image) -> dict:
    """
    Returns:
        {
            "has_exif": bool,
            "exif_summary": dict  # only populated if present, e.g. camera make/model
        }
    """
    exif_data = image.getexif()

    if not exif_data or len(exif_data) == 0:
        return {"has_exif": False, "exif_summary": {}}

    summary = {}
    for tag_id, value in exif_data.items():
        tag_name = TAGS.get(tag_id, tag_id)
        # Only keep human-readable, small values - skip binary blobs (e.g. thumbnails)
        if isinstance(value, (str, int, float)):
            summary[tag_name] = value

    return {"has_exif": True, "exif_summary": summary}


if __name__ == "__main__":
    import sys

    if len(sys.argv) != 2:
        print("Usage: python exif_check.py <image_path>")
        sys.exit(1)

    img = Image.open(sys.argv[1])
    print(check_exif(img))
