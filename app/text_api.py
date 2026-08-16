from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from app.inspection import inspect_text_report


router = APIRouter()


class TextAnalysisRequest(BaseModel):
    text: str


@router.post("/analyze-text")
async def analyze_text(request: TextAnalysisRequest):
    try:
        return inspect_text_report(request.text)
    except ValueError as error:
        raise HTTPException(status_code=400, detail=str(error)) from error
