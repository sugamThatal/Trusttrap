"""
Video support: extract sample frames from an uploaded video so the
EXISTING image-based pipeline (BLIP captioning, SDXL AI-detector, CLIP
match) can run on them - no separate video-specific model needed.

WHY THIS APPROACH (be ready to explain this honestly if asked):
There isn't a single well-established "pip install and go" pretrained
video deepfake/AI-generation detector the way there is for images.
Real deepfake-video detection systems are ALSO frame-based under the
hood in practice (extract frames/faces, classify each with an image
model, aggregate) - so sampling frames and reusing the same SDXL
AI-image-detector on each one is a legitimate, honest approximation of
that approach, not a shortcut hack. It also means zero new heavyweight
ML dependencies - just OpenCV for decoding frames.

KNOWN LIMITATION (say this out loud, don't let it be discovered later):
This checks whether individual FRAMES look AI-generated or manipulated.
It will NOT catch:
  - Temporal-only artifacts (flicker, unnatural motion) that only show
    up ACROSS consecutive frames, not within a single one
  - Fake/cloned AUDIO - this never looks at the audio track at all
If those matter for your use case, that's a genuinely separate, bigger
model - don't quietly claim this covers it.
"""

import cv2
from PIL import Image

# Sanity cap so someone can't upload a 3-hour video and hang the server -
# adjust if you have a real use case for longer clips
MAX_VIDEO_DURATION_SECONDS = 120

DEFAULT_SAMPLE_FRAMES = 6


def get_video_duration_seconds(video_path: str) -> float:
    """Returns 0.0 if the file can't be read as a video at all."""
    cap = cv2.VideoCapture(video_path)
    try:
        fps = cap.get(cv2.CAP_PROP_FPS) or 0
        frame_count = cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0
        if fps <= 0 or frame_count <= 0:
            return 0.0
        return frame_count / fps
    finally:
        cap.release()


def extract_sample_frames(video_path: str, num_frames: int = DEFAULT_SAMPLE_FRAMES) -> list[Image.Image]:
    """
    Pulls `num_frames` frames evenly spaced across the video (including
    the first and last frame), as PIL Images ready for the existing
    BLIP/CLIP/AI-detector functions. Silently skips any individual frame
    that fails to decode rather than failing the whole request over one
    corrupt frame.

    Returns an empty list if the video can't be read at all.
    """
    cap = cv2.VideoCapture(video_path)
    try:
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
        if total_frames <= 0:
            return []

        num_frames = max(1, min(num_frames, total_frames))
        if num_frames == 1:
            indices = [total_frames // 2]
        else:
            # Evenly spaced, including both the first and last frame
            indices = sorted({
                round(i * (total_frames - 1) / (num_frames - 1))
                for i in range(num_frames)
            })

        frames = []
        for idx in indices:
            cap.set(cv2.CAP_PROP_POS_FRAMES, idx)
            success, frame_bgr = cap.read()
            if not success:
                continue
            frame_rgb = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
            frames.append(Image.fromarray(frame_rgb))

        return frames
    finally:
        cap.release()


if __name__ == "__main__":
    # Quick manual test: python video_utils.py path/to/video.mp4
    import sys

    if len(sys.argv) != 2:
        print("Usage: python video_utils.py <video_path>")
        sys.exit(1)

    duration = get_video_duration_seconds(sys.argv[1])
    print(f"Duration: {duration:.1f}s")
    frames = extract_sample_frames(sys.argv[1])
    print(f"Extracted {len(frames)} frames, sizes: {[f.size for f in frames]}")
