from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from google import genai
from google.genai import types
import uvicorn
import os
import json

app = FastAPI()

api_key = os.getenv("GEMINI_API_KEY")
client = genai.Client(api_key=api_key)

class ScamRequest(BaseModel):
    text: str

@app.get("/")
async def root():
    return {"status": "Online", "engine": "Google Gemini AI Platform"}

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

    system_instruction = """
    You are an expert Cybersecurity Incident Response analyzer specializing in Mobile Social Engineering, Smishing, and Phishing triage.
    Analyze the incoming message text for scams, lottery fraud, pressure tactics, fake urgent requests, or verification traps.
    
    You MUST respond with a valid JSON object matching this exact format, with no extra markdown formatting or conversational filler:
    {
        "verdict": "🚨 Potential Scam Message 🚨" or "⚠️ Suspicious Risk Pattern Detected ⚠️" or "✅ Looks Safe",
        "safety_score": a float value between 0.0 (perfectly safe) and 1.0 (confirmed high-risk scam),
        "threat_tags": ["list", "of", "found", "keywords", "or", "tactics"]
    }
    """

    try:
        response = client.models.generate_content(
            model='gemini-2.5-flash',
            contents=text,
            config=types.GenerateContentConfig(
                system_instruction=system_instruction,
                response_mime_type="application/json"
            )
        )
        
        ai_result = json.loads(response.text)
        
        return {
            "text": text,
            "safety_score": round(float(ai_result.get("safety_score", 0.10)), 2),
            "verdict": ai_result.get("verdict", "✅ Looks Safe"),
            "threat_tags": ai_result.get("threat_tags", [])
        }

    except Exception as e:
        lowercase_text = text.lower()
        keywords = ["urgent", "verify", "password", "bank", "click here", "free money", "won", "prize", "lottery", "claim", "clam"]
        found_threats = [word for word in keywords if word in lowercase_text]
        
        verdict = "✅ Looks Safe"
        safety_score = 0.10
        if len(found_threats) >= 3 or "clam" in lowercase_text or "prize" in lowercase_text:
            verdict = "🚨 Potential Scam Message 🚨"
            safety_score = 0.85
        elif len(found_threats) > 0:
            verdict = "⚠️ Suspicious Risk Pattern Detected ⚠️"
            safety_score = 0.45

        return {
            "text": text,
            "safety_score": safety_score,
            "verdict": verdict,
            "threat_tags": found_threats
        }

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)