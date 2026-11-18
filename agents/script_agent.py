"""
Script Agent – generates original Hinglish YouTube scripts,
hooks, titles, descriptions and tags using Gemini.

Zero-budget: reuses the same Gemini key already used by JARVIS.
"""

import json
import urllib.request
import urllib.error
from typing import Any, Dict, Optional

from .base import BaseAgent


class ScriptAgent(BaseAgent):
    name = "script"
    description = "Creates original Hinglish YouTube scripts, titles, hooks and descriptions"

    MODEL = "gemini-2.0-flash"

    SYSTEM = """You are an expert Hinglish YouTube script writer for faceless channels.
Write natural, conversational Hinglish (Hindi + English mix) that sounds like a real Indian creator speaking to camera.

Rules:
- Strong hook in first 8-12 seconds
- Clear storytelling + value
- Short sentences, spoken style
- End with soft CTA (subscribe / comment)
- Never copy existing videos
- Keep language clean and energetic

When asked for full package, return valid JSON only with these keys:
{
  "title": "...",
  "hook": "...",
  "script": "...",
  "description": "...",
  "tags": ["tag1", "tag2", ...],
  "thumbnail_text": "short bold text for thumbnail"
}
"""

    def run(self, task: str, context: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        if not self.gemini_key:
            return self.fail("Gemini API key not provided")

        context = context or {}
        topic = context.get("topic") or task
        style = context.get("style", "informative + engaging")
        length = context.get("length", "medium")  # short / medium / long

        prompt = f"""Topic: {topic}
Style: {style}
Length: {length}

Create a complete YouTube video package in Hinglish.
Return ONLY the JSON object, no extra text."""

        try:
            answer = self._call_gemini(prompt)
            # try parse JSON
            start = answer.find("{")
            end = answer.rfind("}")
            if start >= 0 and end > start:
                data = json.loads(answer[start:end + 1])
                return self.ok(data, topic=topic, raw=answer)
            return self.ok({"raw_script": answer}, topic=topic)
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
