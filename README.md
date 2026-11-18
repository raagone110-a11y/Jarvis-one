# JARVIS-ONE

Personal autonomous AI assistant with Android companion + modular agent system.

**Long-term vision**: JARVIS as YouTube CEO for a faceless Hinglish channel.

## Features

### Android Companion (Standard Mode)
- Modern dark UI
- Gemini chat + Hindi/Hinglish voice
- Phone control: open apps, flashlight, volume, calls, WhatsApp, alarm, timer, battery
- Foreground service with notification
- **No Developer Options required**

### Python Core
- Conversational JARVIS with memory
- ActionEngine for device actions (Termux)
- Modular Agents:
  - **ResearchAgent** → video ideas
  - **ScriptAgent** → full Hinglish scripts + titles + tags
  - **StrategyAgent** → weekly content plans
  - **Orchestrator** → chains agents (research → script)

## Quick Start (Python)

```bash
# Needs config.py with GEMINI_API_KEY and owner.json
python jarvis.py
```

Special commands (examples):
- `research: AI tools` → idea generation
- `script: How to earn online` → full script package
- `strategy: productivity` → weekly plan
- `pipeline: money mindset` → research + script together

## Safety
- Standard Mode is default
- Advanced Mode (ADB etc.) is optional and never forced
- Publishing / paid services require explicit approval
- API keys never committed

See `ROADMAP.md` and `docs/` for full architecture.
