import json
import datetime
import time
import subprocess
import urllib.request
import urllib.error
from config import GEMINI_API_KEY
from listen import listen

MODEL = "gemini-3.5-flash-lite"
URL = f"https://generativelanguage.googleapis.com/v1beta/models/{MODEL}:generateContent"

SYSTEM_PROMPT = """
You are JARVIS, a personal AI assistant created by your owner.
Your AI engine is powered by Google's Gemini.

IDENTITY:
- Your name is JARVIS.
- Never introduce yourself as Gemini or a generic language model.
- You are a helpful, intelligent and professional personal assistant.

COMMUNICATION:
- Speak naturally in Hindi or Hinglish by default.
- Understand both Hindi and English.
- Be polite, friendly and concise.
- Explain technical steps in simple language.

BEHAVIOUR:
- Understand the user's instructions carefully.
- Use previous conversation context when available.
- Never pretend to perform actions you cannot actually perform.
- Never claim to control Android or access files unless the required tools exist.
- Help with coding, learning, planning and problem-solving.

If a message contains [DEVICE BATTERY: ...] data, that is real live data from the phone. Use it to answer the battery level and charging status.\nYour purpose is to make your owner's daily tasks easier.
"""

with open("owner.json", "r", encoding="utf-8") as f:
    OWNER = json.load(f)

SYSTEM_PROMPT = SYSTEM_PROMPT + f"""

OWNER PROFILE:
- Owner name: {OWNER["owner_name"]}
- Your name: {OWNER["assistant_name"]}
- Preferred language: {OWNER["preferred_language"]}
- Your role: {OWNER["role"]}

Always address the owner using their saved name.
Never claim that you know personal information unless it is in the profile or conversation.
"""

MEMORY_FILE = "jarvis_memory.json"

try:
    with open(MEMORY_FILE, "r", encoding="utf-8") as f:
        history = json.load(f)
    if not isinstance(history, list):
        history = []
except (FileNotFoundError, json.JSONDecodeError):
    history = []

print(f"JARVIS Memory: {len(history)} previous messages loaded.")

print("\n🤖 JARVIS-ONE ONLINE")
print("Namaste! Main JARVIS hoon.")
print("Ab Gemini AI se baat kar sakte ho.")
print("Band karne ke liye bye likho.\n")

while True:
    user_input = input("You (Enter=bolo, ya type karo): ").strip()
    if user_input == "":
        print("🎤 Bolo...")
        user_input = listen()
        print("You (voice):", user_input)

    if user_input.lower() in ["bye", "exit", "quit"]:
        print("JARVIS: Goodbye!")
        break

    if not user_input:
        continue

    extra = ""
    low = user_input.lower()
    if "battery" in low or "charge" in low:
        try:
            out = subprocess.run(["termux-battery-status"], capture_output=True, text=True, timeout=10).stdout.strip()
            extra = "\n[DEVICE BATTERY: " + out + "]"
        except Exception:
            pass
    history.append({
        "role": "user",
        "parts": [{"text": user_input + extra}]
    })

    payload = {
        "systemInstruction": {
            "parts": [{"text": SYSTEM_PROMPT + "\nCURRENT DATE/TIME: " + datetime.datetime.now().strftime("%A, %d %B %Y, %I:%M %p")}]
        },
        "contents": history
    }

    request = urllib.request.Request(
        URL,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "x-goog-api-key": GEMINI_API_KEY
        },
        method="POST"
    )

    try:
        result = None

        for attempt in range(3):
            try:
                with urllib.request.urlopen(request, timeout=120) as response:
                    result = json.loads(response.read().decode("utf-8"))
                break
            except urllib.error.HTTPError as e:
                if e.code == 503 and attempt < 2:
                    print(f"Gemini busy. Retrying in {3 * (attempt + 1)} seconds...")
                    time.sleep(3 * (attempt + 1))
                    continue
                raise

        answer = "".join(
            part.get("text", "")
            for part in result["candidates"][0]["content"]["parts"]
        )

        print("\nJARVIS:", answer, "\n")

        subprocess.run(
            ["termux-tts-speak", "-l", "hi-IN", "-p", "0.75", "-r", "0.90", answer],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=False
        )

        history.append({
            "role": "model",
            "parts": [{"text": answer}]
        })

        history = history[-30:]

        with open(MEMORY_FILE, "w", encoding="utf-8") as f:
            json.dump(history, f, ensure_ascii=False, indent=2)

    except urllib.error.HTTPError as e:
        print("\nAPI Error:", e.code)
        print(e.read().decode("utf-8", errors="replace"))
        history.pop()

    except Exception as e:
        print("\nError:", str(e))
        history.pop()
