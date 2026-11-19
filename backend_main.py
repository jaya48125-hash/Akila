import os
from datetime import datetime
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from google import genai
from google.genai import types

load_dotenv()

app = FastAPI(title="AKILA Voice Assistant API")
client = genai.Client(api_key=os.getenv("GEMINI_API_KEY"))

REMINDERS: list[dict[str, str]] = []


def get_current_time() -> str:
    """Returns the current local date and time."""
    return datetime.now().strftime("%Y-%m-%d %I:%M %p")


def add_reminder(task: str, time_str: str) -> str:
    """Saves a reminder for the user."""
    REMINDERS.append({"task": task, "time": time_str})
    return f"Reminder saved: '{task}' at {time_str}"


def list_reminders() -> list[dict[str, str]]:
    """Returns all saved reminders."""
    return REMINDERS


class VoiceQuery(BaseModel):
    text: str
    language: str = "ta"


class AssistantResponse(BaseModel):
    reply: str


SYSTEM_PROMPT = (
    "You are AKILA, a fast, helpful, warm personal voice assistant like Google Assistant and Siri. "
    "Respond concisely in the same language the user speaks (Tamil, English, or Tanglish). "
    "Use the available tools whenever the user asks for the time or wants to manage reminders."
)


@app.get("/health")
def health_check():
    return {"status": "ok", "assistant": "AKILA"}


@app.post("/ask", response_model=AssistantResponse)
def ask_assistant(query: VoiceQuery):
    if not os.getenv("GEMINI_API_KEY"):
        raise HTTPException(status_code=500, detail="GEMINI_API_KEY is not configured.")

    try:
        response = client.models.generate_content(
            model="gemini-2.5-flash",
            contents=query.text,
            config=types.GenerateContentConfig(
                system_instruction=SYSTEM_PROMPT,
                tools=[get_current_time, add_reminder, list_reminders],
                temperature=0.6,
            ),
        )
        reply_text = response.text or "மன்னிக்கவும், என்னால் இப்போது பதில் அளிக்க முடியவில்லை."
        return AssistantResponse(reply=reply_text)
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc
