"""Train the optional text classifier from a user-supplied labeled CSV.

The CSV must contain ``text,label``. Label 1 means suspicious/scam and label
0 means ordinary/benign. This script intentionally refuses to invent data.
"""

from __future__ import annotations

import argparse
import csv
from collections import Counter
from pathlib import Path


def parse_label(raw: str) -> int:
    value = raw.strip().casefold()
    if value in {"1", "true", "yes", "suspicious", "scam", "fraud"}:
        return 1
    if value in {"0", "false", "no", "ordinary", "benign", "safe"}:
        return 0
    raise ValueError(f"Unsupported label {raw!r}; use 0/1 or benign/suspicious")


def main() -> None:
    parser = argparse.ArgumentParser(description="Train TrustTap's optional text-safety model")
    parser.add_argument("--input", type=Path, required=True, help="CSV with text,label columns")
    parser.add_argument("--output", type=Path, default=Path("models/text_model.joblib"))
    args = parser.parse_args()

    if not args.input.exists():
        raise SystemExit(f"No dataset found at {args.input}. Provide your real labeled CSV first.")

    texts: list[str] = []
    labels: list[int] = []
    with args.input.open("r", newline="", encoding="utf-8-sig") as handle:
        reader = csv.DictReader(handle)
        if not reader.fieldnames or not {"text", "label"}.issubset(set(reader.fieldnames)):
            raise SystemExit("CSV must have headers: text,label")
        for row in reader:
            text = (row.get("text") or "").strip()
            if text:
                texts.append(text)
                labels.append(parse_label(row.get("label") or ""))

    counts = Counter(labels)
    if len(texts) < 12 or set(counts) != {0, 1} or min(counts.values()) < 2:
        raise SystemExit(
            "Need at least 12 labeled messages, with at least 2 ordinary and 2 suspicious examples; "
            "the script will not fabricate training data."
        )

    from joblib import dump
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.linear_model import LogisticRegression
    from sklearn.pipeline import Pipeline
    from sklearn.model_selection import train_test_split
    from sklearn.metrics import classification_report

    model = Pipeline([
        ("tfidf", TfidfVectorizer(lowercase=True, ngram_range=(1, 2), min_df=1, max_features=12000)),
        ("classifier", LogisticRegression(max_iter=1000, class_weight="balanced")),
    ])

    test_size = max(2, round(len(texts) * 0.25))
    train_texts, test_texts, train_labels, test_labels = train_test_split(
        texts, labels, test_size=test_size, random_state=42, stratify=labels
    )
    model.fit(train_texts, train_labels)
    print(classification_report(test_labels, model.predict(test_texts), zero_division=0))

    model.fit(texts, labels)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    dump(model, args.output)
    print(f"Saved trained model to {args.output.resolve()}")


if __name__ == "__main__":
    main()
