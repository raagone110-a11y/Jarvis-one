# JARVIS-ONE Architecture

## High Level

```
JARVIS-ONE
├── android-companion/     # Kotlin Android app (phone control + chat UI)
├── core/                  # Low-level device actions (Termux)
├── agents/                # Modular AI agents (YouTube CEO vision)
│   ├── base.py
│   ├── script_agent.py
│   └── (research, strategy, analytics, production, publishing, learning)
├── jarvis.py              # Main Python conversational loop
└── docs/ + ROADMAP.md
```

## Agent Design

Every agent inherits from `BaseAgent` and implements `run(task, context) -> result_dict`.

Result format is standardized so an orchestrator can chain agents safely.

## Safety & Permissions

- Coding & internal improvements → autonomous
- Publishing / uploading / paid services → explicit owner approval required
- API keys never committed

## Next Building Blocks

1. Simple orchestrator that can call ScriptAgent
2. ResearchAgent (free search + Gemini analysis)
3. Shared memory / state store
