# JARVIS-ONE Architecture

## High Level

```
JARVIS-ONE
├── android-companion/     # Kotlin Android app (phone control + chat UI)
│   ├── Standard Mode      # Normal permissions only (default)
│   └── Advanced Mode      # Optional ADB / deeper automation (user opt-in)
├── core/                  # Low-level device actions (Termux)
├── agents/                # Modular AI agents (YouTube CEO vision)
│   ├── base.py
│   ├── script_agent.py
│   └── (research, strategy, analytics, production, publishing, learning)
├── jarvis.py              # Main Python conversational loop
└── docs/
```

## Android Modes

See `docs/ANDROID_MODES.md` for full details.

- **Standard Mode** is the default and must always work.
- **Advanced Mode** is optional and never forced.

## Agent Design

Every agent inherits from `BaseAgent` and implements `run(task, context) -> result_dict`.

Result format is standardized so an orchestrator can chain agents safely.

## Safety & Permissions

- Coding & internal improvements → autonomous
- Publishing / uploading / paid services → explicit owner approval required
- API keys never committed
- Never auto-enable Developer Options or USB debugging
- Clear distinction between supported / restricted / unavailable actions

## Next Building Blocks

1. Simple orchestrator that can call ScriptAgent
2. ResearchAgent (free search + Gemini analysis)
3. Shared memory / state store
4. Accessibility-based navigation (still Standard Mode)
