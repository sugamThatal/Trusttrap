# Accessibility and model evaluation plan

The project should be evaluated with consented, de-identified examples from
blind and low-vision users, not only generic image benchmarks.

Track:

- task completion rate for “understand this,” “should I act,” and “read the
  important text” tasks;
- time to a safe decision;
- false reassurance rate, especially for payment/code requests;
- unnecessary alarm rate on ordinary messages;
- spoken-summary length and whether users can repeat the recommended action;
- OCR reading order and missed critical fields such as dates, amounts, URLs,
  and phone numbers;
- follow-up question success rate;
- model calibration by risk group, language, image quality, and source app.

The supplied deterministic tests cover text and URL report assembly. They do not
claim that the existing media models are accurate for every population. Add a
held-out evaluation set before changing weights or training a model.
