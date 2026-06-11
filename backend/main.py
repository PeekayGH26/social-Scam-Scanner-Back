from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from transformers import pipeline
import uvicorn
import os

app = FastAPI()

# Read the environment variable right after creating the app instance
allow_emulator = os.getenv("ALLOW_EMULATOR_TRAFFIC", "false")

class ScamRequest(BaseModel):
    text: str

# Initialize the Hugging Face AI pipeline using a mobile-optimized classification model
# Render will automatically download this model into memory during deployment
ai_classifier = pipeline("text-classification", model="mrm8488/distilroberta-finetuned-financial-news-sentiment-analysis")

@app.get("/")
async def root():
    return {"message": "Social Scam Scanner Backend is running live via Hugging Face AI!"}

@app.post("/analyze")
async def analyze_text(request: ScamRequest):
    text = request.text.strip()

    if not text:
        return {
            "text": "",
            "safety_score": 0.0,
            "verdict": "No text detected",
            "threat_tags": []
        }

    # 1. Run the image text through the NLP Transformer model
    model_prediction = ai_classifier(text)[0]
    label = model_prediction["label"]
    confidence_score = model_prediction["score"]

    # 2. Extract specific threat keywords for the UI report chip mapping
    keywords = ["urgent", "verify", "password", "bank", "suspicious", "click here", "free money"]
    found_threats = [word for word in keywords if word in text.lower()]

    # 3. Dynamic Warning Verdict Engine based on AI classification layers
    if label == "negative" and confidence_score > 0.60:
        verdict = "🚨 Potential Scam Message 🚨"
        safety_score = confidence_score
    elif label == "neutral" or len(found_threats) > 0:
        verdict = "⚠️ Suspicious Risk Pattern Detected ⚠️"
        safety_score = 0.45
    else:
        verdict = "✅ Looks Safe"
        safety_score = 0.10

    return {
        "text": text,
        "safety_score": round(float(safety_score), 3),
        "verdict": verdict,
        "threat_tags": found_threats
    }

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
