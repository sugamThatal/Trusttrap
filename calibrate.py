

import csv
import sys
from pathlib import Path

import requests

API_URL = "http://localhost:8000/analyze-image"
TEST_DIR = Path("test_images")
IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}


def analyze_one(image_path: Path, claimed_caption: str | None) -> dict:
    """Sends one image (and optional caption) to the running API, returns the JSON result."""
    with open(image_path, "rb") as f:
        files = {"image": (image_path.name, f, "image/jpeg")}
        data = {}
        if claimed_caption:
            data["claimed_caption"] = claimed_caption

        response = requests.post(API_URL, files=files, data=data, timeout=120)

    response.raise_for_status()
    return response.json()


def find_caption(image_path: Path) -> str | None:
    """Looks for a sibling .txt file with the same base name as the image."""
    caption_path = image_path.with_suffix(".txt")
    if caption_path.exists():
        return caption_path.read_text().strip()
    return None


def main():
    if not TEST_DIR.exists():
        print(f"ERROR: {TEST_DIR}/ doesn't exist yet.")
        print("Create it with subfolders: genuine/, ai_generated/, mismatched/")
        print("See the docstring at the top of this file for the expected layout.")
        sys.exit(1)

    rows = []
    category_counts = {}  # category -> {"Low": n, "Medium": n, "High": n}

    for category_dir in sorted(TEST_DIR.iterdir()):
        if not category_dir.is_dir():
            continue

        category = category_dir.name
        category_counts[category] = {"Low": 0, "Medium": 0, "High": 0}

        image_files = [
            p for p in sorted(category_dir.iterdir())
            if p.suffix.lower() in IMAGE_EXTENSIONS
        ]

        for image_path in image_files:
            caption = find_caption(image_path)
            print(f"Analyzing {category}/{image_path.name}...", end=" ", flush=True)

            try:
                result = analyze_one(image_path, caption)
            except requests.exceptions.RequestException as e:
                print(f"FAILED: {e}")
                continue

            print(f"score={result['trust_score']} risk={result['risk']}")

            category_counts[category][result["risk"]] += 1
            rows.append({
                "category": category,
                "filename": image_path.name,
                "claimed_caption": caption or "",
                "trust_score": result["trust_score"],
                "risk": result["risk"],
                "reason": "; ".join(result["reason"]),
                "accessible_description": result["accessible_description"],
            })

    if not rows:
        print("No images found. Check your test_images/ folder structure.")
        return

    # Write CSV for later review
    csv_path = Path("calibration_results.csv")
    with open(csv_path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)

    # Print summary
    print("\n" + "=" * 50)
    print("SUMMARY (risk distribution per category)")
    print("=" * 50)
    for category, counts in category_counts.items():
        total = sum(counts.values())
        print(f"{category:15s} Low={counts['Low']:2d}  Medium={counts['Medium']:2d}  High={counts['High']:2d}  (n={total})")

    print(f"\nFull results saved to {csv_path.resolve()}")
    print("\nWhat to look for:")
    print("  - genuine/       should be mostly Low")
    print("  - ai_generated/  should be mostly Medium/High")
    print("  - mismatched/    should be mostly Medium/High")
    print("  If a category doesn't match expectations, that's your signal to")
    print("  adjust the weights/thresholds in app/scoring.py")


if __name__ == "__main__":
    main()
