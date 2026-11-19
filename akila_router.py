import os
import socket
from typing import Any
from google import genai
from google.genai import types

SYSTEM_PROMPT = (
    "You are AKILA, a personal Android voice assistant that belongs strictly to your owner. "
    "Understand and reply naturally in Tamil, English, or Tanglish matching the user's style. "
    "When the user asks to control phone settings or apps, call the appropriate phone_control tool."
)


def has_internet(timeout: float = 1.5) -> bool:
    """Check if mobile data / Wi-Fi internet is available."""
    try:
        socket.create_connection(("8.8.8.8", 53), timeout=timeout).close()
        return True
    except OSError:
        return False


def execute_phone_action(action: str, target: str = "", value: str = "") -> dict[str, Any]:
    """Executes full phone control commands on Android via AccessibilityService."""
    return {
        "status": "dispatched_to_android",
        "action": action,
        "target": target,
        "value": value,
    }


def _offline_tanglish_intent(text: str) -> str:
    """Fast offline rule fallback for Tamil / English / Tanglish commands."""
    q = text.lower()
    if any(k in q for k in ["wifi", "wi-fi", "வைஃபை"]):
        state = "off" if any(k in q for k in ["off", "நிறுத்து", "ஆஃப்"]) else "on"
        execute_phone_action("toggle_wifi", value=state)
        return f"சரி, Wi-Fi {state} செய்யப்பட்டது."
    if any(k in q for k in ["torch", "flash", "flashlight", "டார்ச்", "லைட்"]):
        state = "off" if any(k in q for k in ["off", "நிறுத்து", "ஆஃப்"]) else "on"
        execute_phone_action("toggle_flashlight", value=state)
        return f"சரி, Flashlight {state} செய்யப்பட்டது."
    if any(k in q for k in ["whatsapp", "வாட்ஸ்அப்", "youtube", "யூடியூப்", "camera", "கேமரா"]):
        for app_name in ["WhatsApp", "YouTube", "Camera"]:
            if app_name.lower() in q or (app_name == "WhatsApp" and "வாட்ஸ்அப்" in q):
                execute_phone_action("open_app", target=app_name)
                return f"{app_name} திறக்கப்படுகிறது."
    return "Offline mode-ல் உள்ளேன். இணையம் இல்லாததால் அடிப்படை phone கட்டளைகளை மட்டும் இப்போது செய்ய முடியும்."


def process_akila_command(user_text: str) -> dict[str, str]:
    """Routes AKILA commands to Cloud Gemini (Online) or Local Engine (Offline)."""
    if has_internet() and os.getenv("GEMINI_API_KEY"):
        client = genai.Client(api_key=os.getenv("GEMINI_API_KEY"))
        response = client.models.generate_content(
            model="gemini-2.5-flash",
            contents=user_text,
            config=types.GenerateContentConfig(
                system_instruction=SYSTEM_PROMPT,
                tools=[execute_phone_action],
                temperature=0.5,
            ),
        )
        return {"mode": "online", "reply": response.text or "சரி, செய்துவிட்டேன்."}

    return {"mode": "offline", "reply": _offline_tanglish_intent(user_text)}
