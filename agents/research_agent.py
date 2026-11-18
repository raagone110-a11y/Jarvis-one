"""
Research Agent – discovers content ideas and trends using free methods + Gemini.

Zero-budget: uses only Gemini reasoning + public knowledge.
Later can be extended with free web search if available.
"""

import json
import urllib.request
from typing import Any, Dict, Optional

from .base import BaseAgent


class ResearchAgent(BaseAgent):
    name = "research"
    description = "Researches trending topics and generates original video ideas for Hinglish faceless channels"

    MODEL = "gemini-2.0-flash"

    SYSTEM = """You are a YouTube research specialist for faceless Hinglish channels.
Your job is to find high-potential, original video ideas.

Focus on:
- Evergreen + trending topics that Indian audience likes
- Low competition or unique angles
- Topics that work well without showing face
- Educational, motivational, tech, money, productivity, stories, AI tools etc.

Always return pure JSON array of ideas. Each idea object must have:
{
  "title_idea": "...",
  "angle": "unique angle explanation",
  "why_it_can_work": "short reason",
  "estimated_difficulty": "easy|medium|hard",
  "suggested_length": "short|medium|long"
}

Return 5-8 ideas. No extra text outside JSON.
"""

    def run(self, task: str, context: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        if not self.gemini_key:
            return self.fail("Gemini API key not provided")

        context = context or {}
        niche = context.get("niche") or task or "general Hinglish faceless"

        prompt = f"""Niche / focus: {niche}

Generate 6-8 original, high-potential YouTube video ideas suitable for a faceless Hinglish channel.
Return ONLY the JSON array."""

        try:
            answer = self._call_gemini(prompt)
            start = answer.find("[")
            end = answer.rfind("]")
            if start >= 0 and end > start:
                ideas = json.loads(answer[start:end + 1])
                return self.ok(ideas, niche=niche, count=len(ideas))
            return self.ok({"raw": answer}, niche=niche)
        except Exception as e:
            return self.fail(str(e))

    def _call_gemini(self, user_text: str) -> str:
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{self.MODEL}:generateContent"
        payload = {
            "systemInstruction": {"parts": [{"text": self.SYSTEM}]},
            "contents": [{"role": "user", "parts": [{"text": user_text}]}]
        }
        req = urllib.request.Request(
            url,
            data=json.dumps(payload).encode("utf-8"),
            headers={
                "Content-Type": "application/json",
                "x-goog-api-key": self.gemini_key
            },
            method="POST"
        )
        with urllib.request.urlopen(req, timeout=90) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        parts = data["candidates"][0]["content"]["parts"]
        return "".join(p.get("text", "") for p in parts)
