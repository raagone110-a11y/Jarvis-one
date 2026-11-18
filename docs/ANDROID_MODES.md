# JARVIS Android Modes

## Design Principle

JARVIS supports **two operating modes** so it remains useful on normal phones and can optionally unlock more power when the user chooses.

### 1. STANDARD MODE (Default)

Works with normal Android permissions and official APIs only.

**Currently supported (no Developer Options needed):**
- Launch any installed app
- Flashlight on/off
- Volume up/down
- Make calls (with CALL_PHONE permission)
- Open WhatsApp chat with pre-filled message
- Set alarm / timer
- Read contacts (for name → number lookup)
- Battery status
- Vibrate
- Speech-to-Text + Text-to-Speech (Hindi / Hinglish)
- Gemini conversation
- Foreground service + persistent notification
- Basic notifications

**Permissions used:**
- RECORD_AUDIO
- READ_CONTACTS
- CALL_PHONE
- INTERNET
- VIBRATE
- POST_NOTIFICATIONS (Android 13+)
- FOREGROUND_SERVICE + FOREGROUND_SERVICE_MICROPHONE

This mode is the default and must always remain fully functional.

### 2. ADVANCED MODE (Optional)

Extra capabilities that may require the user to manually enable Developer Options / USB debugging / ADB.

**Examples of future advanced features (not forced):**
- Deeper system automation via ADB
- Advanced diagnostics
- Extended UI automation (where Accessibility alone is insufficient)
- Custom shell-level helpers (Termux + ADB bridge)

**Strict rules:**
- Never enable Developer Options or USB debugging automatically
- Always explain *why* a setting is needed and the security implications
- Give clear steps to enable **and** to disable later
- User must explicitly opt-in
- Standard Mode must continue working even if Advanced Mode is off
- Do not interfere with third-party work apps that require Developer Options to stay OFF

## Current Status

| Feature                    | Mode     | Status      |
|---------------------------|----------|-------------|
| Chat + Gemini             | Standard | Working     |
| Voice (STT/TTS)           | Standard | Working     |
| Open App                  | Standard | Working     |
| Flashlight / Volume       | Standard | Working     |
| Call / WhatsApp           | Standard | Working     |
| Alarm / Timer             | Standard | Working     |
| Battery / Vibrate         | Standard | Working     |
| Foreground Service        | Standard | Working     |
| Accessibility-based taps  | Standard | Planned    |
| ADB / shell automation    | Advanced | Future      |

## Development Rule

Prefer Standard Mode solutions first.  
Only introduce Advanced Mode paths when there is a genuine technical need that cannot be solved with normal Android APIs + Accessibility.
