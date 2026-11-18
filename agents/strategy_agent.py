"""
Strategy Agent – creates simple weekly content plans for a Hinglish faceless channel.
"""

import json
import urllib.request
from typing import Any, Dict, Optional
from datetime import datetime, timedelta

from .base import BaseAgent


class StrategyAgent(BaseAgent):
    name = "strategy"
    description = "Creates weekly content strategy and posting plans"

    MODEL = "gemini-2.0-flash"

    SYSTEM = """You are a YouTube content strategist for faceless Hinglish channels.
Create practical, realistic weekly plans.

Return pure JSON only:
{
  "week_focus": "...",
  "posting_days": ["Monday", "Wednesday", "Friday"],
  "videos": [
    {
      "day": "Monday",
      "title_idea": "...",
      "type": "educational|story|list|motivational",
      "priority": "high|medium|low",
      "notes": "short note"
    }
  ],
  "tips": ["tip1", "tip2"]
}
"""

    def run(self, task: str, context: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        if not self.gemini_key:
            return self.fail("Gemini API key not provided")

        context = context or {}
        niche = context.get("niche") or task or "general Hinglish"
        videos_per_week = context.get("videos_per_week", 3)

        prompt = f"""Niche: {niche}
Videos per week: {videos_per_week}
Today: {datetime.now().strftime('%A, %d %B %Y')}

Create a realistic 1-week content plan for a faceless Hinglish YouTube channel.
Return ONLY the JSON object."""

        try:
            answer = self._call_gemini(prompt)
            start = answer.find("{")
            end = answer.rfind("}")
            if start >= 0 and end > start:
                plan = json.loads(answer[start:end + 1])
                return self.ok(plan, niche=niche)
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
