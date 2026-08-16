# Optional text-model training

TrustTap does not ship a pretend-trained model. Put real, consented examples
in a CSV with exactly these columns:

```csv
text,label
"Your account is blocked, send the code now",1
"Our appointment is at 10 tomorrow",0
```

Use `1` for suspicious/scam and `0` for ordinary/benign. Aim for a balanced,
representative dataset and keep private messages out of source control.

From the outer `Trusttrap-main` folder:

```powershell
pip install -r training/requirements.txt
python training/train_text_model.py --input C:\path\to\your\text_labels.csv --output models\text_model.joblib
```

Restart Uvicorn after training. The `/analyze-text` endpoint will then combine
the trained classifier with the explainable rules. If the artifact is absent,
the transparent rules still work and the API says so in `analysis_method`.
