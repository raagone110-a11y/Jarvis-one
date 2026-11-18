"""
Simple Orchestrator – routes tasks to the right agent(s).
"""

from typing import Any, Dict, Optional

from .script_agent import ScriptAgent
from .research_agent import ResearchAgent
from .strategy_agent import StrategyAgent


class Orchestrator:
    def __init__(self, gemini_key: str):
        self.gemini_key = gemini_key
        self.agents = {
            "script": ScriptAgent(gemini_key),
            "research": ResearchAgent(gemini_key),
            "strategy": StrategyAgent(gemini_key),
        }

    def run(self, agent_name: str, task: str, context: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        agent = self.agents.get(agent_name)
        if not agent:
            return {
                "success": False,
                "error": f"Unknown agent: {agent_name}. Available: {list(self.agents.keys())}"
            }
        return agent.run(task, context)

    def research_then_script(self, niche: str) -> Dict[str, Any]:
        research = self.run("research", niche, {"niche": niche})
        if not research.get("success"):
            return research

        ideas = research.get("output") or []
        if not ideas:
            return {"success": False, "error": "No ideas returned from research"}

        first = ideas[0] if isinstance(ideas, list) else ideas
        title = first.get("title_idea") if isinstance(first, dict) else str(first)

        script = self.run("script", title, {"topic": title, "style": "engaging Hinglish"})
        return {
            "success": True,
            "research": research,
            "selected_idea": first,
            "script_package": script
        }
