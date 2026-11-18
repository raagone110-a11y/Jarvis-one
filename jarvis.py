import json
import datetime
import time
import subprocess
import urllib.request
import urllib.error
from config import GEMINI_API_KEY
from listen import listen
from core.action_engine import ActionEngine

try:
    from agents.orchestrator import Orchestrator
    orchestrator = Orchestrator(GEMINI_API_KEY)
except Exception:
    orchestrator = None

MODEL = "gemini-2.0-flash"
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

If a message contains [DEVICE BATTERY: ...] data, that is real live data from the phone. Use it to answer the battery level and charging status.
Your purpose is to make your owner's daily tasks easier.
"""

try:
    with open("owner.json", "r", encoding="utf-8") as f:
        OWNER = json.load(f)
    SYSTEM_PROMPT = SYSTEM_PROMPT + f"""

OWNER PROFILE:
- Owner name: {OWNER.get("owner_name", "Owner")}
- Your name: {OWNER.get("assistant_name", "JARVIS")}
- Preferred language: {OWNER.get("preferred_language", "Hinglish")}
- Your role: {OWNER.get("role", "Personal Assistant")}

Always address the owner using their saved name.
Never claim that you know personal information unless it is in the profile or conversation.
"""
except Exception:
    OWNER = {"owner_name": "Owner"}

MEMORY_FILE = "jarvis_memory.json"
engine = ActionEngine()

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
print("Commands: research: <niche> | script: <topic> | strategy: <niche> | pipeline: <niche>")
print("Band karne ke liye bye likho.\n")

def format_result(plan, result):
    if not result.get("success"):
        return "Kaam nahi ho paya: " + str(result.get("error", "unknown error"))
    action = plan["action"]
    if action == "get_battery":
        b = result["output"]
        charger = "charger nahi laga hai" if b.get("plugged") == "UNPLUGGED" else "charger laga hai"
        return f"Battery {b.get('percentage')} percent hai, {charger}. Temperature {b.get('temperature')} degree hai."
    if action == "vibrate":
        return "Phone vibrate kar diya."
    if action == "show_notification":
        return "Notification bhej diya."
    return "Ho gaya."

def handle_agent_command(user_input: str) -> bool:
    if orchestrator is None:
        return False

    low = user_input.lower().strip()

    if low.startswith("research:"):
        niche = user_input[9:].strip() or "general Hinglish"
        print("JARVIS: Research kar raha hoon...")
        result = orchestrator.run("research", niche, {"niche": niche})
        if result.get("success"):
            ideas = result.get("output", [])
            print("\n📌 Research Ideas:")
            for i, idea in enumerate(ideas[:8], 1):
                if isinstance(idea, dict):
                    print(f"{i}. {idea.get('title_idea', idea)}")
                    print(f"   Angle: {idea.get('angle', '')}")
                else:
                    print(f"{i}. {idea}")
            print()
        else:
            print("JARVIS: Research fail →", result.get("error"))
        return True

    if low.startswith("script:"):
        topic = user_input[7:].strip()
        if not topic:
            print("JARVIS: Topic batao. Example → script: How to focus better")
            return True
        print("JARVIS: Script bana raha hoon...")
        result = orchestrator.run("script", topic, {"topic": topic})
        if result.get("success"):
            out = result.get("output", {})
            if isinstance(out, dict) and "title" in out:
                print("\n🎬 Script Package")
                print("Title:", out.get("title"))
                print("Hook:", out.get("hook"))
                print("\nScript:\n", out.get("script", "")[:1200], "...\n" if len(out.get("script", "")) > 1200 else "\n")
                print("Tags:", ", ".join(out.get("tags", [])[:8]))
            else:
                print(out)
        else:
            print("JARVIS: Script fail →", result.get("error"))
        return True

    if low.startswith("strategy:"):
        niche = user_input[9:].strip() or "general"
        print("JARVIS: Weekly strategy bana raha hoon...")
        result = orchestrator.run("strategy", niche, {"niche": niche})
        if result.get("success"):
            plan = result.get("output", {})
            print("\n📅 Weekly Strategy")
            print("Focus:", plan.get("week_focus", ""))
            for v in plan.get("videos", []):
                print(f"- {v.get('day')}: {v.get('title_idea')} ({v.get('type')})")
            print()
        else:
            print("JARVIS: Strategy fail →", result.get("error"))
        return True

    if low.startswith("pipeline:"):
        niche = user_input[9:].strip() or "general"
        print("JARVIS: Full pipeline (research → script) chala raha hoon...")
        result = orchestrator.research_then_script(niche)
        if result.get("success"):
            idea = result.get("selected_idea", {})
            script = result.get("script_package", {}).get("output", {})
            print("\n✅ Pipeline Complete")
            print("Selected Idea:", idea.get("title_idea") if isinstance(idea, dict) else idea)
            if isinstance(script, dict):
                print("Title:", script.get("title"))
                print("Hook:", script.get("hook"))
            print()
        else:
            print("JARVIS: Pipeline fail →", result.get("error"))
        return True

    return False


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

    if handle_agent_command(user_input):
        continue

    plan = engine.plan(user_input)
    if plan:
        if "error" in plan:
            print("JARVIS:", plan["error"])
            continue
        result = engine.execute(plan)
        if result.get("requires_confirmation"):
            if input(f"Allow {plan['action']}? (y/n): ").strip().lower() == "y":
                result = engine.execute(plan, approved=True)
            else:
                print("JARVIS: Cancel kiya.")
                continue
        message = format_result(plan, result)
        print("JARVIS:", message)
        subprocess.run(
            ["termux-tts-speak", "-l", "hi-IN", "-p", "0.75", "-r", "0.90", message],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=False
        )
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
        if history:
            history.pop()

    except Exception as e:
        print("\nError:", str(e))
        if history:
            history.pop()
