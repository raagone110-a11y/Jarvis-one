import subprocess

def listen():
    try:
        r = subprocess.run(["termux-speech-to-text"], capture_output=True, text=True, timeout=30)
        return r.stdout.strip()
    except Exception:
        return ""
